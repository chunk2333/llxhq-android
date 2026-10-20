package cn.llxhq.consumer;

import android.content.Context;
import android.content.SharedPreferences;

/** 本地配置存储，全部持久化，重启后自动恢复 */
public class Prefs {

    private static final String FILE = "llxhq_prefs";

    public static final int THEME_AUTO = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        Context app = ctx.getApplicationContext();
        if (app == null) {
            app = ctx;
        }
        this.sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public SharedPreferences raw() {
        return sp;
    }

    // ---------- 外观 ----------
    public int getThemeMode() {
        return sp.getInt("theme_mode", THEME_AUTO);
    }

    public void setThemeMode(int mode) {
        sp.edit().putInt("theme_mode", mode).apply();
    }

    // ---------- 下载链接 ----------
    public String getSelectedUrl() {
        return sp.getString("selected_url", LinkStore.PRESETS[0].url);
    }

    public void setSelectedUrl(String url) {
        sp.edit().putString("selected_url", url).apply();
    }

    public String getCustomLinks() {
        return sp.getString("custom_links", "");
    }

    public void setCustomLinks(String encoded) {
        sp.edit().putString("custom_links", encoded).apply();
    }

    // ---------- 并发 ----------
    public int getThreads() {
        int n = sp.getInt("threads", 4);
        if (n < 1) {
            n = 1;
        }
        if (n > 512) {
            n = 512;
        }
        return n;
    }

    public void setThreads(int n) {
        sp.edit().putInt("threads", n).apply();
    }

    // ---------- 目标 ----------
    public String getTargetMb() {
        return sp.getString("target_mb", "0");
    }

    public void setTargetMb(String v) {
        sp.edit().putString("target_mb", v == null ? "0" : v).apply();
    }

    // ---------- Ping ----------
    public String getPingHost() {
        return sp.getString("ping_host", "qq.com");
    }

    public void setPingHost(String host) {
        sp.edit().putString("ping_host", host).apply();
    }

    // ---------- 累计消耗（跨会话持久化） ----------
    public long getLifetimeBytes() {
        long v = sp.getLong("lifetime_bytes", 0L);
        return v < 0 ? 0L : v;
    }

    public void addLifetimeBytes(long n) {
        if (n <= 0) {
            return;
        }
        long cur = sp.getLong("lifetime_bytes", 0L);
        if (cur < 0) {
            cur = 0;
        }
        sp.edit().putLong("lifetime_bytes", cur + n).apply();
    }

    public void resetLifetimeBytes() {
        sp.edit().putLong("lifetime_bytes", 0L).apply();
    }
}
