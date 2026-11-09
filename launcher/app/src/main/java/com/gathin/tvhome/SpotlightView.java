package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;

import java.nio.ByteBuffer;

/**
 * A beam from above the screen and a pool of light on the focused tile, in that app's colour (theme key spotlight).
 * It adds light to what is already on screen, so a white logo under it stays white. One small alpha map is drawn
 * scaled up in one pass, with no layer, so moving it costs no more than the glass highlight it replaces.
 */
final class SpotlightView extends View {
    static final int W = 1120, H = 1560;
    private static final int ANCHOR_Y = 1180, SCALE = 4;
    private static final float BEAM_LENGTH = 1135f;
    private static final int DEFAULT_COLOUR = 0xFFFFF6EB;
    /** Overall strength of the light; 1 is the first version, which read as too bright. */
    private static final float BRIGHTNESS = 0.70f;

    private static Bitmap map;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect target = new Rect(0, 0, W, H);
    private final ArgbEvaluator evaluator = new ArgbEvaluator();
    private ValueAnimator fade;
    private int colour = DEFAULT_COLOUR;

    SpotlightView(Context context) {
        super(context);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        paint.setColor(colour);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setAlpha(0f);
        if (map == null) map = buildMap();
    }

    private static Bitmap buildMap() {
        int w = W / SCALE, h = H / SCALE;
        byte[] a = new byte[w * h];
        float cx = W / 2f;
        for (int row = 0; row < h; row++) {
            float y = (row + 0.5f) * SCALE;
            float t = Math.max(0f, Math.min(1.15f, (y - (ANCHOR_Y - BEAM_LENGTH)) / BEAM_LENGTH));
            float half = 26f + 128f * t;
            float beamFade = (float) Math.pow(t, 1.5) * clamp((ANCHOR_Y + 46f - y) / 70f) * 0.17f;
            float dy = y - ANCHOR_Y;
            for (int col = 0; col < w; col++) {
                float dx = (col + 0.5f) * SCALE - cx;
                float edge = clamp(1f - Math.abs(dx) / half);
                float beam = (float) Math.pow(edge, 1.35) * beamFade;
                float pool = 0.50f * gauss(dx, 185f, dy, 78f);
                float halo = 0.15f * gauss(dx, 300f, dy, 190f);
                // A soft shoulder rather than a hard stop where the map ends.
                float light = clamp(beam + pool + halo) * BRIGHTNESS;
                a[row * w + col] = (byte) Math.round(light * 255f);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(a));
        return bitmap;
    }

    private static float gauss(float dx, float sx, float dy, float sy) {
        return (float) Math.exp(-(dx * dx / (sx * sx) + dy * dy / (sy * sy)));
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    /** Sits over the highlight's centre and takes on its fade, every frame the highlight moves. */
    void follow(View glass) {
        ViewTreeObserver.OnPreDrawListener copy = () -> {
            setTranslationX(glass.getX() + glass.getWidth() / 2f - W / 2f);
            setTranslationY(glass.getY() + glass.getHeight() / 2f - ANCHOR_Y);
            setAlpha(glass.getAlpha());
            return true;
        };
        glass.getViewTreeObserver().addOnPreDrawListener(copy);
    }

    /** Glides to the focused app's colour; 0 is a soft white. */
    void setColour(int argb) {
        int to = argb == 0 ? DEFAULT_COLOUR : lifted(argb);
        if (fade != null) fade.cancel();
        int from = colour;
        fade = ValueAnimator.ofFloat(0f, 1f).setDuration(180);
        fade.addUpdateListener(a -> {
            colour = (Integer) evaluator.evaluate(a.getAnimatedFraction(), from, to);
            paint.setColor(colour);
            invalidate();
        });
        fade.start();
    }

    /** A dark banner colour would cast no visible light, so it is raised until its strongest channel is bright. */
    private static int lifted(int argb) {
        int r = Color.red(argb), g = Color.green(argb), b = Color.blue(argb);
        int top = Math.max(r, Math.max(g, b));
        if (top == 0) return DEFAULT_COLOUR;
        if (top >= 215) return 0xFF000000 | (argb & 0xFFFFFF);
        float k = 215f / top;
        return Color.rgb(Math.round(r * k), Math.round(g * k), Math.round(b * k));
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(map, null, target, paint);
    }
}
