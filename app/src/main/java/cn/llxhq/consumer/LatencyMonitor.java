package cn.llxhq.consumer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 每秒一次的延迟采样器 */
public class LatencyMonitor {

    public interface Listener {
        void onSample(int ms);
    }

    private volatile boolean running = false;
    private volatile int generation = 0;
    private ScheduledExecutorService exec;
    private Listener listener;
    private String host = "qq.com";

    public String getHost() {
        return host;
    }

    public void start(String target, Listener l) {
        stop();
        this.host = (target == null || target.trim().length() == 0) ? "qq.com" : target.trim();
        this.listener = l;
        this.running = true;
        final int gen = ++generation;
        exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                if (!running || gen != generation) {
                    return;
                }
                int ms = Net.tcpPing(host, 900);
                Listener l2 = listener;
                if (running && gen == generation && l2 != null) {
                    l2.onSample(ms);
                }
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        generation++;
        ScheduledExecutorService e = exec;
        exec = null;
        if (e != null) {
            e.shutdownNow();
        }
    }
}
