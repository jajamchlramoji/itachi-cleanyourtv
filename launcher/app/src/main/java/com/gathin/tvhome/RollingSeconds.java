package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The seconds beside Home's clock, each digit rolling upward when it changes (theme key rolling_seconds). The width
 * is fixed to the widest digit so the date does not shift. It only ticks while Home's window is visible.
 */
final class RollingSeconds extends View {
    private static final long ROLL_MS = 340;

    private final View clock;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float digitWidth, baseline;
    private final int rowHeight;
    private final Runnable tick = this::onTick;
    private final ValueAnimator roll = ValueAnimator.ofFloat(0f, 1f).setDuration(ROLL_MS);
    private int previous, current;
    private float progress = 1f;

    RollingSeconds(Context context, View clock) {
        super(context);
        this.clock = clock;
        float density = getResources().getDisplayMetrics().density;
        paint.setColor(0xFFFFFFFF);
        paint.setTypeface(UiFont.get(context, R.string.font_light));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 29f, getResources().getDisplayMetrics()));
        float widest = 0f;
        for (char c = '0'; c <= '9'; c++) widest = Math.max(widest, paint.measureText(String.valueOf(c)));
        digitWidth = widest - 1f * density;
        rowHeight = Math.round(35f * density);
        baseline = rowHeight - 6.5f * density;
        roll.setInterpolator(new LinearInterpolator());
        roll.addUpdateListener(a -> {
            progress = a.getAnimatedFraction();
            invalidate();
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private static int second() {
        return (int) ((System.currentTimeMillis() / 1000L) % 60L);
    }

    private void scheduleTick() {
        removeCallbacks(tick);
        postDelayed(tick, 1000L - System.currentTimeMillis() % 1000L + 4L);
    }

    private void onTick() {
        setVisibility(clock.getVisibility());
        int now = second();
        if (now != current) {
            previous = current;
            current = now;
            progress = 0f;
            roll.cancel();
            roll.start();
        }
        scheduleTick();
    }

    private void halt() {
        removeCallbacks(tick);
        roll.cancel();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            previous = current = second();
            progress = 1f;
            invalidate();
            scheduleTick();
        } else {
            halt();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        halt();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round(digitWidth * 2f), rowHeight);
    }

    @Override
    public int getBaseline() {
        return Math.round(baseline);
    }

    /** Fast in, a touch past the mark, then settles. */
    private static float ease(float t) {
        final float c = 1.70158f;
        float u = t - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }

    private void digit(Canvas canvas, int value, float x, float y, float alpha) {
        paint.setAlpha(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
        canvas.drawText(String.valueOf((char) ('0' + value)), x, y, paint);
    }

    private void column(Canvas canvas, int from, int to, float x) {
        if (from == to || progress >= 1f) {
            digit(canvas, to, x, baseline, 1f);
            return;
        }
        float off = rowHeight * ease(progress);
        float share = off / rowHeight;
        digit(canvas, from, x, baseline - off, 1f - share);
        digit(canvas, to, x, baseline + rowHeight - off, share);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.clipRect(0, 0, getWidth(), getHeight());
        column(canvas, previous / 10, current / 10, digitWidth * 0.5f);
        column(canvas, previous % 10, current % 10, digitWidth * 1.5f);
    }
}
