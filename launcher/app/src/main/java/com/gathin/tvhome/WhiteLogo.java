package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns an app's banner into a white logo on nothing (theme key white_logos). The banner's background is modelled
 * as a smooth colour surface, fitted to its border and then to everything that agrees with it, so gradients come
 * out as cleanly as flat colours; whatever differs from that surface is the logo. Artwork with no such background
 * (a full-bleed picture) has no logo to lift out, so it gets the app's name in white instead.
 */
final class WhiteLogo {
    private WhiteLogo() {}

    private static final int W = 320, H = 180;
    private static final int INSET = 4, BAND = 24, STRIDE = 4, ROUNDS = 5;
    /** Bump when the method changes, so logos saved by an older build are not reused. */
    private static final String DISK_VERSION = "v1";
    private static final float NEAR = 30f, FAR = 85f;

    /** Keyed by the APK path, which changes when the app updates. */
    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    static Bitmap forApp(File cacheDir, String key, Drawable banner, String label, Typeface typeface) {
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        File file = new File(cacheDir, "white_logo_" + DISK_VERSION + "_" + Integer.toHexString(key.hashCode()) + ".png");
        Bitmap made = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
        if (made == null) {
            made = banner != null ? lift(banner) : null;
            if (made == null) made = text(label, typeface);
            try (FileOutputStream stream = new FileOutputStream(file)) {
                made.compress(Bitmap.CompressFormat.PNG, 100, stream);
            } catch (IOException ignored) {
                // Only a cache: the logo is made again at the next start.
            }
        }
        CACHE.put(key, made);
        return made;
    }

    private static Bitmap lift(Drawable banner) {
        Bitmap src = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        banner.setBounds(0, 0, W, H);
        banner.draw(new Canvas(src));
        int[] px = new int[W * H];
        src.getPixels(px, 0, W, 0, 0, W, H);
        src.recycle();

        int clear = 0;
        for (int p : px) if (Color.alpha(p) < 128) clear++;
        int[] out = new int[W * H];
        if (clear > W * H * 3 / 10) {        // the app already ships its logo on transparency
            int solid = 0;
            for (int i = 0; i < px.length; i++) {
                int a = Color.alpha(px[i]);
                out[i] = (a << 24) | 0xFFFFFF;
                if (a >= 128) solid++;
            }
            return plausible(solid, W * H) ? bitmap(out) : null;
        }

        boolean[] keep = new boolean[W * H], use = new boolean[W * H];
        for (int y = INSET; y < H - INSET; y++) {
            for (int x = INSET; x < W - INSET; x++) {
                int i = y * W + x;
                keep[i] = Color.alpha(px[i]) >= 128;
                use[i] = keep[i] && (x < BAND || x >= W - BAND || y < BAND || y >= H - BAND);
            }
        }
        double[][] coef = null;
        float[] dist = new float[W * H];
        for (int round = 0; round < ROUNDS; round++) {
            coef = fit(px, use);
            if (coef == null) return null;
            for (int y = 0; y < H; y += STRIDE) {
                for (int x = 0; x < W; x += STRIDE) {
                    int i = y * W + x;
                    use[i] = keep[i] && distance(px[i], coef, x, y) < NEAR;
                }
            }
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                dist[i] = keep[i] ? distance(px[i], coef, x, y) : 0;
            }
        }
        // The border has to be background: if most of it disagrees with the surface, this is a picture.
        int border = 0, agree = 0, kept = 0, solid = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (!keep[i]) continue;
                kept++;
                if (x < BAND || x >= W - BAND || y < BAND || y >= H - BAND) {
                    border++;
                    if (dist[i] < NEAR) agree++;
                }
                float a = (dist[i] - NEAR) / (FAR - NEAR);
                int alpha = a <= 0 ? 0 : a >= 1 ? 255 : Math.round(a * 255);
                if (alpha >= 128) solid++;
                out[i] = (alpha << 24) | 0xFFFFFF;
            }
        }
        if (border == 0 || agree < border * 3 / 4 || !plausible(solid, kept)) return null;
        clearEdgeBleed(out, solid);
        return bitmap(out);
    }

    /** A glow in the artwork that runs off the banner's edge is not logo: drop what is connected to the rim. */
    private static void clearEdgeBleed(int[] out, int solid) {
        final int rim = 12, faint = 10 << 24;
        boolean[] hit = new boolean[W * H];
        int[] stack = new int[W * H];
        int top = 0, removed = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x >= rim && x < W - rim && y >= rim && y < H - rim) continue;
                int i = y * W + x;
                if (Integer.compareUnsigned(out[i], faint) >= 0) { hit[i] = true; stack[top++] = i; }
            }
        }
        while (top > 0) {
            int i = stack[--top];
            if (out[i] >>> 24 >= 128) removed++;
            int x = i % W, y = i / W;
            for (int k = 0; k < 4; k++) {
                int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
                int j = ny * W + nx;
                if (!hit[j] && Integer.compareUnsigned(out[j], faint) >= 0) { hit[j] = true; stack[top++] = j; }
            }
        }
        if (removed > solid / 2) return;      // that was the logo itself, reaching the edge
        for (int i = 0; i < out.length; i++) if (hit[i]) out[i] = 0x00FFFFFF;
    }

    private static boolean plausible(int solid, int total) {
        return solid >= total * 0.004f && solid <= total * 0.55f;
    }

    private static Bitmap bitmap(int[] pixels) {
        return Bitmap.createBitmap(pixels, W, H, Bitmap.Config.ARGB_8888);
    }

    private static void basis(int x, int y, double[] b) {
        double u = (double) x / W - 0.5, v = (double) y / H - 0.5;
        b[0] = 1; b[1] = u; b[2] = v; b[3] = u * u; b[4] = v * v; b[5] = u * v;
    }

    private static float distance(int p, double[][] c, int x, int y) {
        double u = (double) x / W - 0.5, v = (double) y / H - 0.5, uu = u * u, vv = v * v, uv = u * v, sum = 0;
        for (int ch = 0; ch < 3; ch++) {
            double[] k = c[ch];
            double d = channel(p, ch) - (k[0] + k[1] * u + k[2] * v + k[3] * uu + k[4] * vv + k[5] * uv);
            sum += d * d;
        }
        return (float) Math.sqrt(sum);
    }

    private static int channel(int p, int ch) {
        return ch == 0 ? Color.red(p) : ch == 1 ? Color.green(p) : Color.blue(p);
    }

    /** Least squares for the six surface terms, one solution per colour channel; null when too little agrees. */
    private static double[][] fit(int[] px, boolean[] use) {
        double[][] m = new double[6][9];
        double[] b = new double[6];
        int n = 0;
        for (int y = 0; y < H; y += STRIDE) {
            for (int x = 0; x < W; x += STRIDE) {
                int i = y * W + x;
                if (!use[i]) continue;
                n++;
                basis(x, y, b);
                for (int r = 0; r < 6; r++) {
                    for (int c = 0; c < 6; c++) m[r][c] += b[r] * b[c];
                    for (int ch = 0; ch < 3; ch++) m[r][6 + ch] += b[r] * channel(px[i], ch);
                }
            }
        }
        if (n < 24) return null;
        for (int col = 0; col < 6; col++) {
            int pivot = col;
            for (int r = col + 1; r < 6; r++) if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) pivot = r;
            if (Math.abs(m[pivot][col]) < 1e-9) return null;
            double[] swap = m[col]; m[col] = m[pivot]; m[pivot] = swap;
            for (int r = 0; r < 6; r++) {
                if (r == col) continue;
                double f = m[r][col] / m[col][col];
                for (int c = col; c < 9; c++) m[r][c] -= f * m[col][c];
            }
        }
        double[][] out = new double[3][6];
        for (int ch = 0; ch < 3; ch++) for (int r = 0; r < 6; r++) out[ch][r] = m[r][6 + ch] / m[r][r];
        return out;
    }

    private static Bitmap text(String label, Typeface typeface) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(typeface);
        paint.setTextAlign(Paint.Align.CENTER);
        float size = 60f, room = W * 0.86f;
        paint.setTextSize(size);
        while (size > 30f && paint.measureText(label) > room) paint.setTextSize(size -= 2f);
        CharSequence fitted = TextUtils.ellipsize(label, paint, room, TextUtils.TruncateAt.END);
        Paint.FontMetrics fm = paint.getFontMetrics();
        new Canvas(out).drawText(fitted, 0, fitted.length(), W / 2f, H / 2f - (fm.ascent + fm.descent) / 2f, paint);
        return out;
    }
}
