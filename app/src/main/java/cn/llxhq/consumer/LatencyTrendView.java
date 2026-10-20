package cn.llxhq.consumer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** 最近若干次延迟的迷你柱状趋势 */
public class LatencyTrendView extends View {

    private static final int SLOTS = 30;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Integer> data = new ArrayList<>();

    private int colorGood;
    private int colorMid;
    private int colorBad;

    public LatencyTrendView(Context context) {
        this(context, null);
    }

    public LatencyTrendView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources r = getResources();
        colorGood = r.getColor(R.color.good, null);
        colorMid = r.getColor(R.color.mid, null);
        colorBad = r.getColor(R.color.bad, null);
        barPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<Integer> samples) {
        data.clear();
        if (samples != null) {
            int from = Math.max(0, samples.size() - SLOTS);
            for (int i = from; i < samples.size(); i++) {
                data.add(samples.get(i));
            }
        }
        invalidate();
    }

    public void clear() {
        data.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || data.isEmpty()) {
            return;
        }
        float gap = Utils.dp(getContext(), 2.6f);
        float slot = (w - gap * (SLOTS - 1)) / SLOTS;
        if (slot < 1f) {
            slot = 1f;
            gap = 1f;
        }
        int maxVal = 220;
        for (int v : data) {
            if (v > maxVal) {
                maxVal = v;
            }
        }
        float radius = Math.min(slot / 2f, Utils.dp(getContext(), 3f));
        for (int i = 0; i < data.size(); i++) {
            int v = data.get(i);
            float left = i * (slot + gap);
            float height;
            int color;
            if (v < 0) {
                height = h * 0.30f;
                color = colorBad;
            } else {
                height = Math.max(h * 0.16f, h * (v / (float) maxVal));
                color = v <= 60 ? colorGood : (v <= 150 ? colorMid : colorBad);
            }
            float top = h - height;
            barPaint.setColor(color);
            barPaint.setAlpha(v < 0 ? 120 : 210);
            RectF rect = new RectF(left, top, left + slot, h);
            canvas.drawRoundRect(rect, radius, radius, barPaint);
        }
    }
}
