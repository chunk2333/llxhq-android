package cn.llxhq.consumer;

import android.os.SystemClock;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 流量消耗引擎。
 * 统计口径：实际从 socket 读出的字节数（HTTP 正文） + 每个请求的响应头字节数，
 * 而不是「文件大小 × 次数」，因此断点、限速、重连等情况下依然准确。
 */
public class TrafficEngine {

    public interface Listener {
        void onTick(long totalBytes, long speedBps, long requests, long elapsedMs);

        void onStopped(long totalBytes, boolean targetReached);
    }

    private static final int BUF_SIZE = 64 * 1024;

    private volatile boolean running = false;
    private volatile int generation = 0;
    private volatile String url = "";
    private volatile long targetBytes = 0;
    private volatile boolean targetReached = false;
    private volatile Listener listener;

    private long totalBytes = 0;
    private long requestCount = 0;
    private long startAt = 0;
    private volatile int bufSize = BUF_SIZE;

    private final List<Thread> workers = new ArrayList<>();
    private final List<HttpURLConnection> activeConnections =
            Collections.synchronizedList(new ArrayList<HttpURLConnection>());
    private Thread ticker;

    public boolean isRunning() {
        return running;
    }

    public synchronized long getTotalBytes() {
        return totalBytes;
    }

    public synchronized long getRequestCount() {
        return requestCount;
    }

    public long getTargetBytes() {
        return targetBytes;
    }

    /** 实际成功创建的线程数（高并发被系统拒绝时可能小于设定值） */
    public int getActiveThreads() {
        synchronized (workers) {
            return workers.size();
        }
    }

    public void start(String link, int threadCount, long target, Listener l) {
        stop();
        synchronized (this) {
            totalBytes = 0;
            requestCount = 0;
        }
        this.url = link;
        this.targetBytes = target;
        this.targetReached = false;
        this.listener = l;
        this.startAt = SystemClock.elapsedRealtime();
        final int gen = ++generation;
        running = true;

        final int n = Math.max(1, Math.min(threadCount, 512));
        // 线程越多，单线程缓冲区越小，避免 512 并发把堆撑爆
        bufSize = n >= 256 ? 8 * 1024 : (n >= 64 ? 16 * 1024 : BUF_SIZE);
        // 高并发时压小线程栈，降低虚拟内存与内核栈压力
        final long stackSize = n >= 32 ? 512L * 1024L : 0L;

        int created = 0;
        for (int i = 0; i < n; i++) {
            try {
                Thread t = new Thread(null, new Runnable() {
                    @Override
                    public void run() {
                        workerLoop(gen);
                    }
                }, "llxhq-worker-" + i, stackSize);
                t.setDaemon(true);
                t.start();
                synchronized (workers) {
                    workers.add(t);
                }
                created++;
            } catch (Throwable e) {
                // 达到系统线程上限 / 内存不足：用已创建的线程继续跑
                break;
            }
        }
        if (created == 0) {
            running = false;
            if (l != null) {
                l.onStopped(0, false);
            }
            return;
        }

        ticker = new Thread(new Runnable() {
            @Override
            public void run() {
                tickLoop(gen);
            }
        }, "llxhq-ticker");
        ticker.setDaemon(true);
        ticker.start();
    }

    public void stop() {
        if (!running && workers.isEmpty()) {
            return;
        }
        boolean wasRunning = running;
        running = false;
        disconnectAll();

        List<Thread> snapshot;
        synchronized (workers) {
            snapshot = new ArrayList<>(workers);
        }
        long deadline = System.currentTimeMillis() + 260;
        for (Thread t : snapshot) {
            if (t == Thread.currentThread()) {
                continue;
            }
            long remain = deadline - System.currentTimeMillis();
            if (remain <= 0) {
                break;
            }
            try {
                t.join(remain);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (workers) {
            workers.clear();
        }
        Thread tk = ticker;
        ticker = null;
        if (tk != null && tk != Thread.currentThread()) {
            try {
                tk.join(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (wasRunning) {
            Listener l = listener;
            if (l != null) {
                long total;
                synchronized (this) {
                    total = totalBytes;
                }
                l.onStopped(total, targetReached);
            }
        }
    }

    private void tickLoop(int gen) {
        long lastTotal = 0;
        long lastAt = SystemClock.elapsedRealtime();
        while (running && gen == generation) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }
            if (!running || gen != generation) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long total;
            long req;
            synchronized (this) {
                total = totalBytes;
                req = requestCount;
            }
            long dt = Math.max(1, now - lastAt);
            long speed = Math.max(0, (total - lastTotal) * 1000L / dt);
            lastTotal = total;
            lastAt = now;

            Listener l = listener;
            if (l != null) {
                l.onTick(total, speed, req, now - startAt);
            }
            if (targetBytes > 0 && total >= targetBytes) {
                targetReached = true;
                stop();
                return;
            }
        }
    }

    private void workerLoop(int gen) {
        byte[] buf = new byte[bufSize];
        while (running && gen == generation) {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(12000);
                conn.setUseCaches(false);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                conn.setRequestProperty("Pragma", "no-cache");
                conn.setRequestProperty("Accept-Encoding", "identity");
                conn.setRequestProperty("User-Agent", "llxhq-android/1.0");
                activeConnections.add(conn);

                int code = conn.getResponseCode();
                if (code >= 400) {
                    sleepQuiet(700);
                    continue;
                }
                addBytes(headerBytes(conn));

                InputStream in = conn.getInputStream();
                try {
                    while (running && gen == generation) {
                        int len = in.read(buf);
                        if (len < 0) {
                            break;
                        }
                        addBytes(len);
                    }
                } finally {
                    try {
                        in.close();
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                sleepQuiet(450);
            } finally {
                if (conn != null) {
                    activeConnections.remove(conn);
                    try {
                        conn.disconnect();
                    } catch (Throwable ignored) {
                    }
                    synchronized (this) {
                        requestCount++;
                    }
                }
            }
        }
    }

    private void addBytes(long n) {
        if (n <= 0) {
            return;
        }
        synchronized (this) {
            totalBytes += n;
        }
    }

    /** 统计响应头实际消耗的字节（近似值，附加状态行开销） */
    private long headerBytes(HttpURLConnection conn) {
        long n = 0;
        try {
            Map<String, List<String>> headers = conn.getHeaderFields();
            if (headers != null) {
                for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                    String k = e.getKey();
                    if (k != null) {
                        n += k.length() + 2;
                    }
                    List<String> vs = e.getValue();
                    if (vs != null) {
                        for (String v : vs) {
                            if (v != null) {
                                n += v.length() + 2;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return n + 40;
    }

    private void disconnectAll() {
        synchronized (activeConnections) {
            for (HttpURLConnection c : activeConnections) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
            activeConnections.clear();
        }
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
