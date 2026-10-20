package cn.llxhq.consumer;

import android.content.Context;
import android.content.res.Configuration;

/**
 * 主题（跟随系统 / 浅色 / 深色）应用器。
 *
 * <p>注意：这里**不能**在 onCreate 里用 {@code Activity.applyOverrideConfiguration()}。
 * 该 API 要求此前从未访问过 resources，而取基准配置必然要先调用
 * {@code getResources()}，于是必然抛 IllegalStateException
 * （"getResources() or getAssets() has already been called"），
 * 且被 catch 吞掉后表现为「切换浅色无效」。
 *
 * <p>正确做法是在 {@link ThemedActivity#attachBaseContext(Context)} 里
 * 用 {@code createConfigurationContext()} 生成一个 uiMode 被强制过的 Context，
 * 这样 Activity 后续的 getResources() / getTheme() 都基于正确的限定符。
 */
public final class ThemeHelper {

    private ThemeHelper() {
    }

    /** 按偏好强制 uiMode 后返回包装 Context；跟随系统时原样返回 */
    public static Context wrap(Context base) {
        if (base == null) {
            return null;
        }
        try {
            int mode = new Prefs(base).getThemeMode();
            if (mode == Prefs.THEME_AUTO) {
                return base;
            }
            Configuration src = base.getResources().getConfiguration();
            Configuration cfg = new Configuration(src);
            int want = (mode == Prefs.THEME_DARK)
                    ? Configuration.UI_MODE_NIGHT_YES
                    : Configuration.UI_MODE_NIGHT_NO;
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | want;
            Context wrapped = base.createConfigurationContext(cfg);
            return wrapped == null ? base : wrapped;
        } catch (Throwable t) {
            // 极端机型上退回跟随系统，不影响功能
            return base;
        }
    }
}
