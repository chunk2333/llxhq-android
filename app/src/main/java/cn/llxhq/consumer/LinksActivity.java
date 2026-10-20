package cn.llxhq.consumer;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * 下载链接列表：内置节点（按运营商 / 厂商分组）+ 自定义节点
 * 支持搜索过滤、选中、添加、删除，全部持久化。
 */
public class LinksActivity extends ThemedActivity {

    private Prefs prefs;
    private LinearLayout llNodes;
    private ScrollView svNodes;
    private EditText etName;
    private EditText etUrl;
    private EditText etSearch;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private String keyword = "";
    private Runnable pendingFilter;
    private boolean firstRender = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(R.layout.activity_links);

        llNodes = findViewById(R.id.llNodes);
        svNodes = findViewById(R.id.svNodes);
        etName = findViewById(R.id.etName);
        etUrl = findViewById(R.id.etUrl);
        etSearch = findViewById(R.id.etSearch);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.btnAdd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addCustom();
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                keyword = s == null ? "" : s.toString();
                if (pendingFilter != null) {
                    ui.removeCallbacks(pendingFilter);
                }
                pendingFilter = new Runnable() {
                    @Override
                    public void run() {
                        render(false);
                    }
                };
                ui.postDelayed(pendingFilter, 160);
            }
        });

        render(true);
    }

    @Override
    protected void onDestroy() {
        if (pendingFilter != null) {
            ui.removeCallbacks(pendingFilter);
        }
        super.onDestroy();
    }

    private void render(boolean autoScroll) {
        final String selected = prefs.getSelectedUrl();
        final String q = keyword.trim().toLowerCase(Locale.US);
        LayoutInflater inf = getLayoutInflater();
        List<LinkStore.Node> nodes = LinkStore.all(this);

        llNodes.removeAllViews();
        String lastGroup = null;
        int shown = 0;
        View selectedRow = null;

        for (final LinkStore.Node n : nodes) {
            if (q.length() > 0 && !matches(n, q)) {
                continue;
            }
            if (!n.group.equals(lastGroup)) {
                if (shown > 0) {
                    addDivider();
                }
                addHeader(n.group);
                lastGroup = n.group;
            }
            View row = inf.inflate(R.layout.item_link, llNodes, false);
            TextView name = row.findViewById(R.id.tvName);
            TextView url = row.findViewById(R.id.tvUrl);
            View dot = row.findViewById(R.id.viewDot);
            View delete = row.findViewById(R.id.btnDelete);

            name.setText(n.name);
            url.setText(n.url);
            boolean on = n.url.equals(selected);
            dot.setBackground(dotDrawable(on));
            if (on) {
                selectedRow = row;
            }

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.setSelectedUrl(n.url);
                    toast("已选用：" + n.name);
                    finish();
                }
            });

            if (n.custom) {
                delete.setVisibility(View.VISIBLE);
                delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmDelete(n);
                    }
                });
            } else {
                delete.setVisibility(View.GONE);
            }
            llNodes.addView(row);
            shown++;
        }

        if (shown == 0) {
            addEmpty();
            return;
        }

        // 首次进入时把当前选中项滚到可见位置
        if (autoScroll && firstRender && selectedRow != null) {
            final View target = selectedRow;
            svNodes.post(new Runnable() {
                @Override
                public void run() {
                    int y = 0;
                    View v = target;
                    while (v != null && v != svNodes) {
                        y += v.getTop();
                        if (!(v.getParent() instanceof View)) {
                            break;
                        }
                        v = (View) v.getParent();
                    }
                    svNodes.smoothScrollTo(0, Math.max(0, y - (int) Utils.dp(LinksActivity.this, 24f)));
                }
            });
        }
        firstRender = false;
    }

    private boolean matches(LinkStore.Node n, String q) {
        return n.name.toLowerCase(Locale.US).contains(q)
                || n.url.toLowerCase(Locale.US).contains(q)
                || n.group.toLowerCase(Locale.US).contains(q);
    }

    private void addHeader(String title) {
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(11f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setLetterSpacing(0.05f);
        h.setTextColor(getRes(R.color.text2));
        h.setPadding((int) Utils.dp(this, 14f), (int) Utils.dp(this, 12f),
                (int) Utils.dp(this, 14f), (int) Utils.dp(this, 6f));
        llNodes.addView(h);
    }

    private void addDivider() {
        View line = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) Utils.dp(this, 1f));
        lp.setMarginStart((int) Utils.dp(this, 14f));
        lp.setMarginEnd((int) Utils.dp(this, 14f));
        lp.topMargin = (int) Utils.dp(this, 4f);
        line.setLayoutParams(lp);
        line.setBackgroundColor(getRes(R.color.stroke));
        llNodes.addView(line);
    }

    private void addEmpty() {
        TextView t = new TextView(this);
        t.setText("没有匹配的节点，换个关键词试试");
        t.setTextSize(12f);
        t.setTextColor(getRes(R.color.text2));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, (int) Utils.dp(this, 32f), 0, (int) Utils.dp(this, 32f));
        llNodes.addView(t);
    }

    private Drawable dotDrawable(boolean selected) {
        GradientDrawable outer = new GradientDrawable();
        outer.setShape(GradientDrawable.OVAL);
        if (selected) {
            outer.setColor(getRes(R.color.accent));
            GradientDrawable inner = new GradientDrawable();
            inner.setShape(GradientDrawable.OVAL);
            inner.setColor(getRes(R.color.card));
            LayerDrawable ld = new LayerDrawable(new Drawable[]{outer, inner});
            int inset = (int) Utils.dp(this, 5f);
            ld.setLayerInset(1, inset, inset, inset, inset);
            return ld;
        }
        outer.setColor(Color.TRANSPARENT);
        outer.setStroke((int) Utils.dp(this, 1.6f), getRes(R.color.stroke));
        return outer;
    }

    private void addCustom() {
        String name = etName.getText() == null ? "" : etName.getText().toString().trim();
        String url = etUrl.getText() == null ? "" : etUrl.getText().toString().trim();
        if (url.length() == 0) {
            toast("请填写下载直链");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        if (!LinkStore.addCustom(this, name, url)) {
            toast("该链接已存在");
            return;
        }
        prefs.setSelectedUrl(url);
        toast("已添加并选用");
        finish();
    }

    private void confirmDelete(final LinkStore.Node n) {
        new AlertDialog.Builder(this)
                .setTitle("删除链接")
                .setMessage("确定删除「" + n.name + "」？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        LinkStore.removeCustom(LinksActivity.this, n.url);
                        render(false);
                        toast("已删除");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int getRes(int colorRes) {
        return getResources().getColor(colorRes, getTheme());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
