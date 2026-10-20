package cn.llxhq.consumer;

import android.app.Activity;
import android.content.Context;

/**
 * 所有页面的基类：在 attachBaseContext 阶段就套用深浅色偏好，
 * 保证 getTheme() / getResources() 拿到的是正确的 -night 限定符资源。
 */
public class ThemedActivity extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.wrap(newBase));
    }
}
