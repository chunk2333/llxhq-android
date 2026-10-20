package cn.llxhq.consumer;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends ThemedActivity {

    private static final int[] THREAD_OPTIONS = new int[]{1, 2, 4, 8, 16, 32, 64, 128, 256, 512};
    private static final int CHIPS_PER_ROW = 5;
    private static final long LIFETIME_FLUSH_MS = 3000L;

    private Prefs prefs;
    private final TrafficEngine engine = new TrafficEngine();
    private LatencyMonitor monitor;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LatencyGauge gauge;
    private LatencyTrendView trend;
    private TextView tvPingHost;
    private TextView tvPingState;
    private TextView tvPingMin;
    private TextView tvPingAvg;
    private TextView tvPingMax;
    private TextView tvTotal;
    private TextView tvSystem;
    private TextView tvSpeed;
    private TextView tvElapsed;
    private TextView tvRequests;
    private TextView tvLinkName;
    private TextView tvLinkUrl;
    private TextView tvTargetHint;
    private TextView tvLifetime;
    private TextView btnStart;
    private TextView btnManageLinks;
    private ProgressBar pbTarget;
    private EditText etTarget;
    private LinearLayout llThreads;

    private final List<TextView> threadChips = new ArrayList<>();
    private final List<Integer> history = new ArrayList<>();

    private int minMs = Integer.MAX_VALUE;
    private int maxMs = 0;
    private long sumMs = 0;
    private int validCount = 0;

    private long sysBaseRx = -1;
    private long sysBaseTx = -1;
    private boolean destroyed = false;

    // 累计消耗：lifeBase 为已落盘值，lifeDirty 为待落盘增量（避免每秒写磁盘）
    private final Object lifeLock = new Object();
    private long lifeBase = 0;
    private long lifeDirty = 0;
    private long lastSessionTotal = 0;
    private long lastFlushAt = 0;

    private final TrafficEngine.Listener engineListener = new TrafficEngine.Listener() {
        @Override
        public void onTick(final long total, final long speed, final long requests, final long elapsed) {
            accountLifetime(total);
            ui.post(new Runnable() {
                @Override
                public void run() {
                    if (destroyed) {
                        return;
                    }
                    renderStats(total, speed, requests, elapsed);
                }
            });
        }

        @Override
        public void onStopped(final long total, final boolean targetReached) {
            accountLifetime(total);
            flushLifetime();
            ui.post(new Runnable() {
                @Override
                public void run() {
                    if (destroyed) {
                        return;
                    }
                    setRunningUi(false);
                    renderStats(total, 0, engine.getRequestCount(), 0);
                    renderLifetime();
                    if (targetReached) {
                        toast("已达到目标流量，已自动停止");
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        lifeBase = prefs.getLifetimeBytes();
        setContentView(R.layout.activity_main);

        gauge = findViewById(R.id.gauge);
        trend = findViewById(R.id.trend);
        tvPingHost = findViewById(R.id.tvPingHost);
        tvPingState = findViewById(R.id.tvPingState);
        tvPingMin = findViewById(R.id.tvPingMin);
        tvPingAvg = findViewById(R.id.tvPingAvg);
        tvPingMax = findViewById(R.id.tvPingMax);
        tvTotal = findViewById(R.id.tvTotal);
        tvSystem = findViewById(R.id.tvSystem);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvElapsed = findViewById(R.id.tvElapsed);
        tvRequests = findViewById(R.id.tvRequests);
        tvLinkName = findViewById(R.id.tvLinkName);
        tvLinkUrl = findViewById(R.id.tvLinkUrl);
        tvTargetHint = findViewById(R.id.tvTargetHint);
        tvLifetime = findViewById(R.id.tvLifetime);
        btnStart = findViewById(R.id.btnStart);
        btnManageLinks = findViewById(R.id.btnManageLinks);
        pbTarget = findViewById(R.id.pbTarget);
        etTarget = findViewById(R.id.etTarget);
        llThreads = findViewById(R.id.llThreads);

        etTarget.setText(prefs.getTargetMb());
        etTarget.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        buildThreadChips();
        refreshLink();
        renderStats(0, 0, 0, 0);
        renderLifetime();
        setRunningUi(false);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRun();
            }
        });

        btnManageLinks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (engine.isRunning()) {
                    toast("正在消耗流量，请先停止再更换链接");
                    return;
                }
                startActivity(new Intent(MainActivity.this, LinksActivity.class));
            }
        });

        tvPingHost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPing();
            }
        });

        findViewById(R.id.btnPingEntry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPing();
            }
        });

        findViewById(R.id.btnTheme).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThemeDialog();
            }
        });

        findViewById(R.id.rowLifetime).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLifetimeDialog();
            }
        });
    }

    private void openPing() {
        startActivity(new Intent(MainActivity.this, PingActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLink();
        tvPingHost.setText(prefs.getPingHost());
        resetLatencyStats();
        startMonitor();
        updateSystemLine(engine.getTotalBytes());
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMonitor();
        flushLifetime();
    }

    @Override
    protected void onDestroy() {
        flushLifetime();
        destroyed = true;
        stopMonitor();
        engine.stop();
        super.onDestroy();
    }

    // ---------------- 累计消耗 ----------------

    /** 根据本次会话累计值计算增量，攒够 3 秒才落盘，避免高频写磁盘 */
    private void accountLifetime(long sessionTotal) {
        long delta;
        synchronized (lifeLock) {
            delta = sessionTotal - lastSessionTotal;
            if (delta <= 0) {
                return;
            }
            lastSessionTotal = sessionTotal;
            lifeDirty += delta;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastFlushAt >= LIFETIME_FLUSH_MS) {
            flushLifetime();
        }
    }

    private void flushLifetime() {
        long add;
        synchronized (lifeLock) {
            add = lifeDirty;
            lifeDirty = 0;
            lastFlushAt = SystemClock.elapsedRealtime();
        }
        if (add > 0) {
            prefs.addLifetimeBytes(add);
            synchronized (lifeLock) {
                lifeBase = prefs.getLifetimeBytes();
            }
        }
    }

    private void renderLifetime() {
        long v;
        synchronized (lifeLock) {
            v = lifeBase + lifeDirty;
        }
        if (tvLifetime != null) {
            tvLifetime.setText(Utils.bytes(v));
        }
    }

    private void showLifetimeDialog() {
        if (engine.isRunning()) {
            toast("正在消耗流量，请先停止再操作累计计数");
            return;
        }
        flushLifetime();
        final long v = prefs.getLifetimeBytes();
        new AlertDialog.Builder(this)
                .setTitle("累计消耗")
                .setMessage("自安装以来共消耗 " + Utils.bytes(v) + "。\n\n重置只清零累计计数，不影响「本次消耗」。")
                .setPositiveButton("重置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.resetLifetimeBytes();
                        synchronized (lifeLock) {
                            lifeBase = 0;
                            lifeDirty = 0;
                            lastSessionTotal = 0;
                        }
                        renderLifetime();
                        toast("累计计数已重置");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 延迟 ----------------

    private void startMonitor() {
        if (monitor == null) {
            monitor = new LatencyMonitor();
        }
        monitor.start(prefs.getPingHost(), new LatencyMonitor.Listener() {
            @Override
            public void onSample(final int ms) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) {
                            return;
                        }
                        applyLatency(ms);
                    }
                });
            }
        });
    }

    private void stopMonitor() {
        if (monitor != null) {
            monitor.stop();
        }
    }

    private void resetLatencyStats() {
        history.clear();
        minMs = Integer.MAX_VALUE;
        maxMs = 0;
        sumMs = 0;
        validCount = 0;
        trend.clear();
        gauge.reset();
        tvPingMin.setText("--");
        tvPingAvg.setText("--");
        tvPingMax.setText("--");
        tvPingState.setText("检测中");
        setPill(tvPingState, "检测中", getRes(R.color.text2));
    }

    private void applyLatency(int ms) {
        history.add(ms);
        if (history.size() > 30) {
            history.remove(0);
        }
        if (ms >= 0) {
            validCount++;
            sumMs += ms;
            if (ms < minMs) {
                minMs = ms;
            }
            if (ms > maxMs) {
                maxMs = ms;
            }
        }
        gauge.setValue(ms);
        trend.setData(history);

        tvPingMin.setText(minMs == Integer.MAX_VALUE ? "--" : (minMs + "ms"));
        tvPingAvg.setText(validCount == 0 ? "--" : ((int) (sumMs / validCount) + "ms"));
        tvPingMax.setText(maxMs == 0 ? "--" : (maxMs + "ms"));

        int color = getRes(Utils.latencyColorRes(ms));
        setPill(tvPingState, Utils.latencyLabel(ms), color);
    }

    private void setPill(TextView tv, String text, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(Utils.dp(this, 99f));
        d.setColor(Color.argb(38, Color.red(color), Color.green(color), Color.blue(color)));
        d.setStroke((int) Utils.dp(this, 1f), Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)));
        tv.setBackground(d);
        tv.setTextColor(color);
        tv.setText(text);
    }

    // ---------------- 流量 ----------------

    private void toggleRun() {
        if (engine.isRunning()) {
            btnStart.setEnabled(false);
            engine.stop();
            ui.post(new Runnable() {
                @Override
                public void run() {
                    btnStart.setEnabled(true);
                }
            });
            return;
        }
        final String url = prefs.getSelectedUrl();
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            toast("请先选择一个有效的下载链接");
            return;
        }
        hideKeyboard();
        long target = parseTargetBytes();
        prefs.setTargetMb(etTarget.getText().toString().trim().length() == 0
                ? "0" : etTarget.getText().toString().trim());
        int uid = Process.myUid();
        sysBaseRx = TrafficStats.getUidRxBytes(uid);
        sysBaseTx = TrafficStats.getUidTxBytes(uid);
        if (sysBaseRx < 0) {
            sysBaseRx = 0;
        }
        if (sysBaseTx < 0) {
            sysBaseTx = 0;
        }
        renderStats(0, 0, 0, 0);
        synchronized (lifeLock) {
            lastSessionTotal = 0;
        }
        final int wantThreads = prefs.getThreads();
        engine.start(url, wantThreads, target, engineListener);
        if (engine.getActiveThreads() <= 0) {
            setRunningUi(false);
            toast("系统拒绝创建线程，请降低并发数后重试");
            return;
        }
        setRunningUi(true);
        String extra = target > 0 ? ("，目标 " + Utils.bytes(target)) : "";
        toast("开始消耗 · " + engine.getActiveThreads() + " 线程" + extra);
    }

    private long parseTargetBytes() {
        String s = etTarget.getText() == null ? "" : etTarget.getText().toString().trim();
        if (s.length() == 0) {
            return 0;
        }
        try {
            double mb = Double.parseDouble(s);
            if (mb <= 0 || Double.isNaN(mb)) {
                return 0;
            }
            return (long) (mb * 1024d * 1024d);
        } catch (Throwable e) {
            return 0;
        }
    }

    private void renderStats(long total, long speed, long requests, long elapsed) {
        tvTotal.setText(Utils.bytes(total));
        tvSpeed.setText(Utils.speed(speed));
        tvRequests.setText(String.valueOf(requests));
        renderLifetime();
        if (elapsed > 0) {
            tvElapsed.setText(Utils.duration(elapsed));
        }
        updateSystemLine(total);

        long target = engine.getTargetBytes();
        if (target > 0) {
            int pct = (int) Math.min(100L, total * 100L / target);
            pbTarget.setVisibility(View.VISIBLE);
            pbTarget.setProgress(pct);
            tvTargetHint.setVisibility(View.VISIBLE);
            tvTargetHint.setText("目标 " + Utils.bytes(target) + " · 已完成 " + pct + "%");
        } else {
            pbTarget.setVisibility(View.GONE);
            tvTargetHint.setVisibility(View.GONE);
        }
    }

    private void updateSystemLine(long total) {
        int uid = Process.myUid();
        long rx = TrafficStats.getUidRxBytes(uid);
        long tx = TrafficStats.getUidTxBytes(uid);
        if (rx < 0 || tx < 0) {
            tvSystem.setText("系统统计不可用");
            return;
        }
        if (sysBaseRx < 0 || sysBaseTx < 0) {
            tvSystem.setText("系统累计 ↓" + Utils.bytes(rx) + " ↑" + Utils.bytes(tx));
            return;
        }
        long drx = Math.max(0, rx - sysBaseRx);
        long dtx = Math.max(0, tx - sysBaseTx);
        long sysTotal = drx + dtx;
        String tail;
        if (sysTotal > 128 * 1024 && total > 0) {
            double dev = (total - sysTotal) * 100.0 / sysTotal;
            if (Math.abs(dev) < 0.05) {
                dev = 0;
            }
            tail = String.format(Locale.US, " · 校核偏差 %+.1f%%", dev);
        } else {
            tail = "";
        }
        tvSystem.setText("系统实测 ↓" + Utils.bytes(drx) + " ↑" + Utils.bytes(dtx) + tail);
    }

    private void setRunningUi(boolean running) {
        btnStart.setEnabled(true);
        btnStart.setText(running ? "停止消耗" : "开始消耗");
        btnStart.setBackgroundResource(running ? R.drawable.btn_danger : R.drawable.btn_primary);
        etTarget.setEnabled(!running);
        etTarget.setAlpha(running ? 0.6f : 1f);
        btnManageLinks.setAlpha(running ? 0.45f : 1f);
        if (!running) {
            tvSpeed.setText("0 B/s");
        }
        // 消耗流量时保持屏幕常亮，避免息屏后网络被系统挂起
        if (running) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        styleThreadChips();
    }

    private void refreshLink() {
        LinkStore.Node node = LinkStore.findSelected(this);
        tvLinkName.setText(node.name);
        tvLinkUrl.setText(node.url);
    }

    // ---------------- 并发线程 ----------------

    private void buildThreadChips() {
        llThreads.removeAllViews();
        threadChips.clear();
        LinearLayout row = null;
        for (int i = 0; i < THREAD_OPTIONS.length; i++) {
            final int n = THREAD_OPTIONS[i];
            if (i % CHIPS_PER_ROW == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rlp.topMargin = (int) Utils.dp(this, 7f);
                }
                row.setLayoutParams(rlp);
                llThreads.addView(row);
            }
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    (int) Utils.dp(this, 38f), 1f);
            if (i % CHIPS_PER_ROW != 0) {
                lp.setMarginStart((int) Utils.dp(this, 7f));
            }
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setText(String.valueOf(n));
            tv.setTextSize(12.5f);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (engine.isRunning()) {
                        toast("运行中不可修改并发线程，请先停止");
                        return;
                    }
                    prefs.setThreads(n);
                    styleThreadChips();
                    if (n >= 128) {
                        toast(n + " 线程并发较高，实际受设备与节点限制");
                    }
                }
            });
            if (row != null) {
                row.addView(tv);
            }
            threadChips.add(tv);
        }
        styleThreadChips();
    }

    private void styleThreadChips() {
        int selected = prefs.getThreads();
        int accent = getRes(R.color.accent);
        int chip = getRes(R.color.chip);
        int stroke = getRes(R.color.stroke);
        int onAccent = getRes(R.color.on_accent);
        int text2 = getRes(R.color.text2);
        boolean running = engine.isRunning();
        for (int i = 0; i < threadChips.size(); i++) {
            TextView tv = threadChips.get(i);
            boolean on = THREAD_OPTIONS[i] == selected;
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.RECTANGLE);
            d.setCornerRadius(Utils.dp(this, 12f));
            d.setColor(on ? accent : chip);
            if (!on) {
                d.setStroke((int) Utils.dp(this, 1f), stroke);
            }
            tv.setBackground(d);
            tv.setTextColor(on ? onAccent : text2);
            tv.setAlpha(running ? 0.55f : 1f);
        }
    }

    // ---------------- 主题 ----------------

    private void showThemeDialog() {
        if (engine.isRunning()) {
            toast("正在消耗流量，请先停止再切换外观");
            return;
        }
        final String[] items = new String[]{"跟随系统", "浅色模式", "深色模式"};
        new AlertDialog.Builder(this)
                .setTitle("外观")
                .setSingleChoiceItems(items, prefs.getThemeMode(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (prefs.getThemeMode() == which) {
                            return;
                        }
                        prefs.setThemeMode(which);
                        recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 杂项 ----------------

    private int getRes(int colorRes) {
        return getResources().getColor(colorRes, getTheme());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                View focus = getCurrentFocus();
                if (focus != null) {
                    imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                    focus.clearFocus();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
