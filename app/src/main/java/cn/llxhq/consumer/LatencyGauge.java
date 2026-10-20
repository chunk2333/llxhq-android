package cn.llxhq.consumer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** 延迟仪表盘：270° 圆弧 + 中心读数 + 彩色指示 */
public class LatencyGauge extends View {

    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;
    private static final int MAX_SCALE_MS = 300;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float strokeW;
    private final float glowW;

    private int colorGood;
    private int colorMid;
    private int colorBad;

    private int value = -1;
    private float progress = 0f;
    private int arcColor;
    private ValueAnimator animator;

    public LatencyGauge(Context context) {
        this(context, null);
    }

    public LatencyGauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources r = getResources();
        colorGood = r.getColor(R.color.good, null);
        colorMid = r.getColor(R.color.mid, null);
        colorBad = r.getColor(R.color.bad, null);
        int colorTrack = r.getColor(R.color.gauge_track, null);
        int colorText = r.getColor(R.color.text, null);
        int colorText2 = r.getColor(R.color.text2, null);
        arcColor = colorMid;

        strokeW = Utils.dp(context, 9f);
        glowW = Utils.dp(context, 17f);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeW);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(colorTrack);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(glowW);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeW);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);

        valuePaint.setColor(colorText);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(Utils.dp(context, 48f));
        valuePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        unitPaint.setColor(colorText2);
        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setTextSize(Utils.dp(context, 12f));

        hintPaint.setColor(colorText2);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTextSize(Utils.dp(context, 10.5f));
    }

    /** 传入延迟毫秒，-1 表示超时 */
    public void setValue(int ms) {
        this.value = ms;
        int color;
        float target;
        if (ms < 0) {
            color = colorBad;
            target = 1f;
        } else {
            color = ms <= 60 ? colorGood : (ms <= 150 ? colorMid : colorBad);
            target = Math.min(1f, Math.max(0.05f, ms / (float) MAX_SCALE_MS));
        }
        this.arcColor = color;

        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(320);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                progress = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
        invalidate();
    }

    public void reset() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        value = -1;
        progress = 0f;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float cx = w / 2f;
        float cy = h / 2f + Utils.dp(getContext(), 2f);
        float radius = Math.min(w, h) / 2f - glowW / 2f - Utils.dp(getContext(), 3f);
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

        canvas.drawArc(oval, START_ANGLE, SWEEP_ANGLE, false, trackPaint);

        float sweep = SWEEP_ANGLE * progress;
        if (sweep > 0.5f) {
            glowPaint.setColor(arcColor);
            glowPaint.setAlpha(46);
            canvas.drawArc(oval, START_ANGLE, sweep, false, glowPaint);

            arcPaint.setColor(arcColor);
            canvas.drawArc(oval, START_ANGLE, sweep, false, arcPaint);

            double rad = Math.toRadians(START_ANGLE + sweep);
            float dx = (float) (cx + radius * Math.cos(rad));
            float dy = (float) (cy + radius * Math.sin(rad));
            dotPaint.setColor(arcColor);
            dotPaint.setAlpha(90);
            canvas.drawCircle(dx, dy, Utils.dp(getContext(), 7f), dotPaint);
            dotPaint.setAlpha(255);
            canvas.drawCircle(dx, dy, Utils.dp(getContext(), 3.4f), dotPaint);
        }

        String text = value < 0 ? "--" : String.valueOf(value);
        Paint.FontMetrics fm = valuePaint.getFontMetrics();
        float baseY = cy - (fm.ascent + fm.descent) / 2f - Utils.dp(getContext(), 4f);
        canvas.drawText(text, cx, baseY, valuePaint);
        canvas.drawText("ms", cx, baseY + Utils.dp(getContext(), 20f), unitPaint);
        canvas.drawText(value < 0 ? "TCP 延迟" : "TCP 握手延迟", cx,
                cy + radius - Utils.dp(getContext(), 18f), hintPaint);
    }
}
