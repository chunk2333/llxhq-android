package cn.llxhq.consumer;

import android.content.Context;
import android.util.TypedValue;

import java.util.Locale;

/** 通用格式化工具 */
public final class Utils {

    private Utils() {
    }

    public static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    /** 字节数 -> 人类可读 */
    public static String bytes(long b) {
        if (b < 0) {
            b = 0;
        }
        if (b < 1024) {
            return b + " B";
        }
        double kb = b / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.2f MB", mb);
        }
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    /** 速度 B/s -> 人类可读 */
    public static String speed(long bps) {
        if (bps <= 0) {
            return "0 B/s";
        }
        return bytes(bps) + "/s";
    }

    /** 毫秒时长 -> mm:ss / h:mm:ss */
    public static String duration(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long totalSec = ms / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    /** 延迟等级：0 优秀 / 1 一般 / 2 较差（超时为 2） */
    public static int latencyLevel(int ms) {
        if (ms < 0) {
            return 2;
        }
        if (ms <= 60) {
            return 0;
        }
        if (ms <= 150) {
            return 1;
        }
        return 2;
    }

    public static int latencyColorRes(int ms) {
        int lv = latencyLevel(ms);
        if (lv == 0) {
            return R.color.good;
        }
        if (lv == 1) {
            return R.color.mid;
        }
        return R.color.bad;
    }

    public static String latencyLabel(int ms) {
        if (ms < 0) {
            return "超时";
        }
        int lv = latencyLevel(ms);
        if (lv == 0) {
            return "流畅";
        }
        if (lv == 1) {
            return "一般";
        }
        return "较差";
    }
}
