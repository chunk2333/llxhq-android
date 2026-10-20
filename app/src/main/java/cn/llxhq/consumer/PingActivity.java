package cn.llxhq.consumer;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Ping 模块：连续 TCP 探测，统计丢包率与最小/平均/最大延迟 */
public class PingActivity extends ThemedActivity {

    private static final String[] PRESET_HOSTS = new String[]{
            "qq.com", "baidu.com", "taobao.com",
            "aliyun.com", "114.114.114.114", "github.com"
    };

    private static final int MAX_ROWS = 60;

    private Prefs prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private EditText etHost;
    private TextView btnPing;
    private TextView tvSent;
    private TextView tvRecv;
    private TextView tvLoss;
    private TextView tvMin;
    private TextView tvAvg;
    private TextView tvMax;
    private TextView tvResolved;
    private LinearLayout llResults;
    private LinearLayout llPresets;
    private ScrollView svResult;

    private ScheduledExecutorService exec;
    private volatile boolean running = false;
    private volatile String host = "qq.com";

    private int seq = 0;
    private int sent = 0;
    private int recv = 0;
    private int minMs = Integer.MAX_VALUE;
    private int maxMs = 0;
    private long sumMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(R.layout.activity_ping);

        etHost = findViewById(R.id.etHost);
        btnPing = findViewById(R.id.btnPing);
        tvSent = findViewById(R.id.tvSent);
        tvRecv = findViewById(R.id.tvRecv);
        tvLoss = findViewById(R.id.tvLoss);
        tvMin = findViewById(R.id.tvMin);
        tvAvg = findViewById(R.id.tvAvg);
        tvMax = findViewById(R.id.tvMax);
        tvResolved = findViewById(R.id.tvResolved);
        llResults = findViewById(R.id.llResults);
        llPresets = findViewById(R.id.llPresets);
        svResult = findViewById(R.id.svResult);

        host = prefs.getPingHost();
        etHost.setText(host);
        tvResolved.setText("目标：" + host);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnPing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) {
                    stopPing();
                } else {
                    startPing();
                }
            }
        });

        findViewById(R.id.btnClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearResults();
                resetStats();
                renderStats();
            }
        });

        buildPresets();
        renderStats();
    }

    @Override
    protected void onDestroy() {
        stopPing();
        super.onDestroy();
    }

    // ---------------- 预设节点 ----------------

    private void buildPresets() {
        llPresets.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < PRESET_HOSTS.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rlp.topMargin = (int) Utils.dp(this, 7f);
                }
                row.setLayoutParams(rlp);
                llPresets.addView(row);
            }
            final String h = PRESET_HOSTS[i];
            TextView chip = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) Utils.dp(this, 34f), 1f);
            if (i % 3 != 0) {
                lp.setMarginStart((int) Utils.dp(this, 7f));
            }
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER);
            chip.setText(h);
            chip.setTextSize(10.5f);
            chip.setSingleLine(true);
            chip.setBackgroundResource(R.drawable.pill_outline);
            chip.setTextColor(getRes(R.color.text2));
            chip.setClickable(true);
            chip.setFocusable(true);
            if (row != null) {
                row.addView(chip);
            }
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etHost.setText(h);
                    if (running) {
                        stopPing();
                    }
                    startPing();
                }
            });
        }
    }

    // ---------------- 探测 ----------------

    private void startPing() {
        String h = sanitize(etHost.getText() == null ? "" : etHost.getText().toString());
        if (h.length() == 0) {
            toast("请输入域名或 IP");
            return;
        }
        host = h;
        etHost.setText(h);
        prefs.setPingHost(h);
        clearResults();
        resetStats();
        renderStats();
        tvResolved.setText("目标：" + h);

        running = true;
        updateButton();
        resolveAsync(h);

        exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                final int ms = Net.tcpPing(host, 900);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!running) {
                            return;
                        }
                        addResult(ms);
                    }
                });
            }
        }, 0, 800, TimeUnit.MILLISECONDS);
    }

    private void stopPing() {
        running = false;
        ScheduledExecutorService e = exec;
        exec = null;
        if (e != null) {
            e.shutdownNow();
        }
        updateButton();
    }

    private void resolveAsync(final String h) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String ip = Net.resolve(h);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ip != null) {
                            tvResolved.setText("目标：" + h + "（" + ip + "）");
                        } else {
                            tvResolved.setText("目标：" + h + "（解析失败）");
                        }
                    }
                });
            }
        }, "llxhq-dns").start();
    }

    private void updateButton() {
        btnPing.setText(running ? "停止" : "开始");
        btnPing.setBackgroundResource(running ? R.drawable.btn_danger : R.drawable.btn_primary);
    }

    private void addResult(int ms) {
        sent++;
        seq++;
        if (ms >= 0) {
            recv++;
            sumMs += ms;
            if (ms < minMs) {
                minMs = ms;
            }
            if (ms > maxMs) {
                maxMs = ms;
            }
        }
        renderStats();

        LayoutInflater inf = getLayoutInflater();
        View row = inf.inflate(R.layout.item_ping, llResults, false);
        TextView tvSeq = row.findViewById(R.id.tvSeq);
        TextView tvTime = row.findViewById(R.id.tvTime);
        TextView tvNote = row.findViewById(R.id.tvNote);
        View dot = row.findViewById(R.id.viewDot);

        tvSeq.setText("#" + seq);
        if (ms < 0) {
            tvTime.setText("请求超时");
            tvTime.setTextColor(getRes(R.color.bad));
            tvNote.setText("无响应");
            dot.setBackground(dotDrawable(getRes(R.color.bad)));
        } else {
            tvTime.setText(ms + " ms");
            int color = getRes(Utils.latencyColorRes(ms));
            tvTime.setTextColor(color);
            tvNote.setText(Utils.latencyLabel(ms) + " · TCP");
            dot.setBackground(dotDrawable(color));
        }
        llResults.addView(row, 0);
        while (llResults.getChildCount() > MAX_ROWS) {
            llResults.removeViewAt(llResults.getChildCount() - 1);
        }
        svResult.scrollTo(0, 0);
    }

    /** 小圆点：用 7dp 的椭圆背景 */
    private GradientDrawable dotDrawable(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setSize((int) Utils.dp(this, 7f), (int) Utils.dp(this, 7f));
        return d;
    }

    private void clearResults() {
        llResults.removeAllViews();
    }

    private void resetStats() {
        seq = 0;
        sent = 0;
        recv = 0;
        minMs = Integer.MAX_VALUE;
        maxMs = 0;
        sumMs = 0;
    }

    private void renderStats() {
        tvSent.setText(String.valueOf(sent));
        tvRecv.setText(String.valueOf(recv));
        int loss = sent == 0 ? 0 : (int) Math.round((sent - recv) * 100.0 / sent);
        tvLoss.setText(loss + "%");
        tvLoss.setTextColor(getRes(loss == 0 ? R.color.good : (loss < 30 ? R.color.mid : R.color.bad)));
        tvMin.setText(minMs == Integer.MAX_VALUE ? "--" : String.valueOf(minMs));
        tvAvg.setText(recv == 0 ? "--" : String.valueOf(sumMs / recv));
        tvMax.setText(maxMs == 0 ? "--" : String.valueOf(maxMs));
    }

    private static String sanitize(String raw) {
        String h = raw == null ? "" : raw.trim();
        if (h.length() == 0) {
            return "";
        }
        int scheme = h.indexOf("://");
        if (scheme >= 0) {
            h = h.substring(scheme + 3);
        }
        int slash = h.indexOf('/');
        if (slash > 0) {
            h = h.substring(0, slash);
        }
        int colon = h.indexOf(':');
        if (colon > 0) {
            h = h.substring(0, colon);
        }
        return h.trim();
    }

    private int getRes(int colorRes) {
        return getResources().getColor(colorRes, getTheme());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
