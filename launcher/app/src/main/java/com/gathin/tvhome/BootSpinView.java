package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * A short full-screen animation the first time Home opens after each boot (theme key boot_spin); any remote key skips
 * it. Theme key boot_spin_every_start plays it on every start of the launcher, which is handy while working on it.
 *
 * To make your own animation, replace drawAnimation(). It is handed a canvas that has been cleared to black, the
 * screen's centre, and the animation's progress from 0 to 1. Change DURATION_MS for a longer or shorter one.
 */
final class BootSpinView extends View {
    private static final long DURATION_MS = 2400;
    private static final long FADE_OUT_MS = 260;
    private static final String PREFS = "boot_spin", LAST_BOOT = "last_boot_v2", TAG = "BootSpin";
    /** The most one frame may advance the animation, so a stall while Home loads pauses it instead of using it up. */
    private static final long MAX_STEP_MS = 40;

    // ---- The placeholder animation: a red eye with a black ring that spins like a fan. --------------------------
    private static final int VANES = 7;
    private static final float EYE_RADIUS = 300f, RING_RADIUS = 214f, RING_WIDTH = 30f, PUPIL_RADIUS = 62f;
    private final Paint redPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path vane = new Path();

    private void setUpAnimation() {
        redPaint.setShader(new RadialGradient(0f, 0f, EYE_RADIUS, new int[]{0xFFFF2A22, 0xFFD40F18, 0xFF5A040A},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        blackPaint.setColor(0xFF050103);
        ringPaint.setColor(0xFF050103);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(RING_WIDTH);
        // One blade of the fan, reaching in from the ring and swept in the direction of the spin.
        vane.moveTo(RING_RADIUS, -30f);
        vane.quadTo(RING_RADIUS - 58f, -26f, RING_RADIUS - 92f, 34f);
        vane.quadTo(RING_RADIUS - 46f, 22f, RING_RADIUS, 34f);
        vane.close();
    }

    /** Draw one frame here. progress runs from 0 to 1 over DURATION_MS. */
    private void drawAnimation(Canvas canvas, float centreX, float centreY, float progress) {
        float appear = Math.min(1f, progress / 0.18f);
        float scale = 0.72f + 0.28f * appear;
        // The fan winds up to speed, the way a real one does: the angle is the integral of a rising speed.
        float turns = 4.2f * progress * progress;
        canvas.save();
        canvas.translate(centreX, centreY);
        canvas.scale(scale, scale);
        canvas.drawCircle(0f, 0f, EYE_RADIUS, redPaint);
        canvas.rotate(turns * 360f);
        canvas.drawCircle(0f, 0f, RING_RADIUS, ringPaint);
        for (int i = 0; i < VANES; i++) {
            canvas.drawPath(vane, blackPaint);
            canvas.rotate(360f / VANES);
        }
        canvas.drawCircle(0f, 0f, PUPIL_RADIUS, blackPaint);
        canvas.restore();
    }
    // ---- End of the placeholder. --------------------------------------------------------------------------------

    private float progress;
    private long elapsed, lastFrame, startedAt;
    private int frames;
    private boolean started;
    private final Runnable step = this::advance;
    private Runnable onDone;
    private boolean closing;

    private BootSpinView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setUpAnimation();
    }

    /** Puts the animation over Home and starts it, or returns null when it has already played since this boot. */
    static BootSpinView showIfDue(Context context, ViewGroup root, boolean everyStart, Runnable onDone) {
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean due = everyStart || prefs.getInt(LAST_BOOT, Integer.MIN_VALUE) != boot;
        Log.i(TAG, "boot " + boot + " due=" + due);
        if (!due) return null;
        prefs.edit().putInt(LAST_BOOT, boot).apply();
        BootSpinView view = new BootSpinView(context);
        view.onDone = onDone;
        root.addView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return view;
    }

    private void advance() {
        long now = SystemClock.uptimeMillis();
        elapsed += Math.min(now - lastFrame, MAX_STEP_MS);
        lastFrame = now;
        frames++;
        progress = Math.min(1f, (float) elapsed / DURATION_MS);
        invalidate();
        if (progress >= 1f) close();
        else postOnAnimation(step);
    }

    /** Fades out and leaves Home showing. Safe to call more than once. */
    void close() {
        if (closing) return;
        closing = true;
        removeCallbacks(step);
        Log.i(TAG, "closing after " + frames + " frames, " + (SystemClock.uptimeMillis() - startedAt) + " ms");
        animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction(() -> {
            if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
            if (onDone != null) onDone.run();
        }).start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        drawAnimation(canvas, getWidth() / 2f, getHeight() / 2f, progress);
        if (!started) {          // the clock starts once there is something on screen
            started = true;
            lastFrame = startedAt = SystemClock.uptimeMillis();
            postOnAnimation(step);
        }
    }
}
