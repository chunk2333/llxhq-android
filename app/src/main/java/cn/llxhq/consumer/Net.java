package cn.llxhq.consumer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/** 网络探测：TCP 握手延迟（无需 root，兼容所有安卓版本） */
public final class Net {

    private static final int[] PORTS = new int[]{443, 80, 53};

    private Net() {
    }

    private static long nowMs() {
        return System.nanoTime() / 1000000L;
    }

    /**
     * 依次尝试 443 / 80 / 53 端口，返回 TCP 握手耗时（毫秒）。
     * 失败返回 -1。总耗时不会超过 timeoutMs。
     */
    public static int tcpPing(String host, int timeoutMs) {
        if (host == null) {
            return -1;
        }
        host = host.trim();
        if (host.length() == 0) {
            return -1;
        }
        long deadline = nowMs() + Math.max(100, timeoutMs);
        for (int port : PORTS) {
            long remain = deadline - nowMs();
            if (remain <= 0) {
                break;
            }
            Socket s = new Socket();
            try {
                InetSocketAddress addr = new InetSocketAddress(host, port);
                long t0 = nowMs();
                s.connect(addr, (int) remain);
                int cost = (int) (nowMs() - t0);
                if (cost < 0) {
                    cost = 0;
                }
                return cost;
            } catch (Throwable ignored) {
                // 尝试下一个端口
            } finally {
                try {
                    s.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return -1;
    }

    /** 解析域名对应的 IP，失败返回 null */
    public static String resolve(String host) {
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            if (addrs != null && addrs.length > 0) {
                return addrs[0].getHostAddress();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
