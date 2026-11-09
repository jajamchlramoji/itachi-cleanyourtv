package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Closes apps for the top bar's brush button by pressing Force stop on each app's system page, because Android 14
 * lets no ordinary app stop another. It only acts on a page showing that exact package name, and the only things it
 * ever presses are a row labelled exactly "Force stop" and the "OK" under a screen titled "Force stop".
 */
public class CleanerService extends AccessibilityService {
    private static final String TAG = "CleanerService";
    private static final String SETTINGS = "com.android.tv.settings";
    private static final String FORCE_STOP = "Force stop", OK = "OK";
    private static final String GUIDANCE_TITLE = SETTINGS + ":id/guidance_title";
    private static final long POLL_MS = 120, STEP_TIMEOUT_MS = 5000;
    private static final int MAX_FAILURES = 2;
    private enum Step { FIND_ROW, CONFIRM, LEAVE_CONFIRM }

    private static CleanerService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::poll;
    private final Deque<String> queue = new ArrayDeque<>();
    private Step step;
    private String current;
    private long stepStarted;
    private int total, cleared, failures;
    private boolean running;
    private TextView cover;

    static boolean available() {
        return instance != null;
    }

    static void clear(List<String> packages) {
        if (instance != null && !instance.running && !packages.isEmpty()) instance.begin(packages);
    }

    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent intent) {
        finish(false);
        instance = null;
        return super.onUnbind(intent);
    }

    private void begin(List<String> packages) {
        running = true;
        queue.clear();
        queue.addAll(packages);
        total = packages.size();
        cleared = 0;
        failures = 0;
        showCover();
        next();
    }

    private void next() {
        current = queue.poll();
        if (current == null) {
            finish(true);
            return;
        }
        updateCover();
        step = Step.FIND_ROW;
        stepStarted = SystemClock.uptimeMillis();
        Intent page = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + current));
        page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(page);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not open the page for " + current);
            skip();
            return;
        }
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, POLL_MS);
    }

    private void skip() {
        Log.w(TAG, "skipped " + current + " at " + step);
        if (++failures >= MAX_FAILURES) finish(true);
        else next();
    }

    private void poll() {
        if (!running) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean inSettings = root != null && root.getPackageName() != null
                && SETTINGS.contentEquals(root.getPackageName());
        if (inSettings) {
            if (step == Step.FIND_ROW) findRow(root);
            else if (step == Step.CONFIRM) confirm(root);
            else if (root.findAccessibilityNodeInfosByViewId(GUIDANCE_TITLE).isEmpty()) {
                cleared++;
                failures = 0;
                next();
                return;
            }
        }
        if (!running) return;
        if (SystemClock.uptimeMillis() - stepStarted > STEP_TIMEOUT_MS) {
            skip();
            if (!running) return;
        }
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, POLL_MS);
    }

    private void moveTo(Step next) {
        step = next;
        stepStarted = SystemClock.uptimeMillis();
    }

    private static AccessibilityNodeInfo exact(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo node : found) {
            if (node.getText() != null && text.contentEquals(node.getText())) return node;
        }
        return null;
    }

    private static AccessibilityNodeInfo clickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo walk = node;
        for (int i = 0; walk != null && i < 4; i++) {
            if (walk.isClickable()) return walk;
            walk = walk.getParent();
        }
        return null;
    }

    private void findRow(AccessibilityNodeInfo root) {
        if (exact(root, current) == null) return;   // not this app's page yet
        if (!root.findAccessibilityNodeInfosByViewId(GUIDANCE_TITLE).isEmpty()) return;
        AccessibilityNodeInfo label = exact(root, FORCE_STOP);
        AccessibilityNodeInfo row = label != null ? clickableParent(label) : null;
        if (row == null) return;
        if (!row.isEnabled() || !label.isEnabled()) {       // Android says it is already stopped
            cleared++;
            failures = 0;
            next();
            return;
        }
        if (row.performAction(AccessibilityNodeInfo.ACTION_CLICK)) moveTo(Step.CONFIRM);
    }

    private void confirm(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> titles = root.findAccessibilityNodeInfosByViewId(GUIDANCE_TITLE);
        if (titles.isEmpty() || titles.get(0).getText() == null
                || !FORCE_STOP.contentEquals(titles.get(0).getText())) return;
        AccessibilityNodeInfo label = exact(root, OK);
        AccessibilityNodeInfo button = label != null ? clickableParent(label) : null;
        if (button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) moveTo(Step.LEAVE_CONFIRM);
    }

    private void showCover() {
        cover = new TextView(this);
        cover.setBackgroundColor(Color.BLACK);
        cover.setTextColor(Color.WHITE);
        cover.setTextSize(30f);
        cover.setGravity(Gravity.CENTER);
        cover.setTypeface(UiFont.get(this, R.string.font_light));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(cover, lp);
        } catch (RuntimeException e) {
            cover = null;       // the job still runs, the Settings pages just show
        }
    }

    private void updateCover() {
        if (cover != null) cover.setText("Clearing apps  " + (total - queue.size()) + " of " + total);
    }

    private void finish(boolean goHome) {
        if (!running) return;
        running = false;
        handler.removeCallbacks(poll);
        queue.clear();
        if (cover != null) {
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover);
            } catch (RuntimeException ignored) {
                // already gone
            }
            cover = null;
        }
        Log.i(TAG, "done: cleared " + cleared + " of " + total);
        if (goHome) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            Toast.makeText(this, cleared == total ? "Background apps cleared"
                    : "Cleared " + cleared + " of " + total + " apps", Toast.LENGTH_SHORT).show();
        }
    }
}
