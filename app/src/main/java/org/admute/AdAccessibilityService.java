package org.admute;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.admute.core.AdDetector;
import org.admute.core.AdSession;
import org.admute.core.VolumeLease;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class AdAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AdDetector detector = new AdDetector();
    private final AdSession session = new AdSession();
    private ScreenCover cover;
    private VolumeLease volume;
    private SharedPreferences prefs;
    private long lastBack = -10000;
    private long pausedUntil;
    private boolean running;
    private final ArrayDeque<String> debugMarkerEvents = new ArrayDeque<>();
    private long debugComfortUntil;
    private String debugComfortOwner = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { evaluate(); }
            catch (RuntimeException error) {
                pausedUntil = SystemClock.elapsedRealtime() + 60000;
                session.reset(); release(); status("Paused after a device access error");
            } finally { if (running) handler.postDelayed(this, 350); }
        }
    };
    // The recovery baseline must be durable before media volume is changed.
    @SuppressLint("ApplySharedPref")
    @Override protected void onServiceConnected() {
        prefs = Preferences.get(this);
        cover = new ScreenCover(this);
        AudioManager audio = getSystemService(AudioManager.class);
        volume = new VolumeLease(new VolumeLease.Port() {
            public int volume() { return audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
            public boolean fixed() { return audio.isVolumeFixed(); }
            public void setVolume(int value) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0); }
            public void save(int value) {
                if (!prefs.edit().putInt("savedVolume", value).commit()) throw new IllegalStateException("Cannot save volume");
            }
            public int saved() { return prefs.getInt("savedVolume", -1); }
            public void clear() { prefs.edit().remove("savedVolume").commit(); }
        });
        try { volume.recover(); } catch (RuntimeException e) { status("Volume recovery needs attention"); }
        running = true; handler.removeCallbacks(tick); handler.post(tick);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!running || event == null) return;
        rememberMarkerEvent(event);
        // Polls read fresh trees. Events only expedite a foreground change, never serve as evidence.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handler.removeCallbacks(tick); handler.post(tick);
        }
    }
    private void evaluate() {
        long now = SystemClock.elapsedRealtime();
        boolean enabled = prefs.getBoolean("enabled", true) && now >= pausedUntil;
        if (!enabled) { debugComfortUntil = 0; session.reset(); release(); status("Paused"); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = "";
        AdDetector.Evidence evidence = AdDetector.Evidence.UNKNOWN;
        if (root != null) {
            try {
                pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
                if (AdDetector.PACKAGES.contains(pkg) && prefs.getBoolean(pkg, true)) {
                    NodeSnapshot snapshot = NodeSnapshot.read(root);
                    evidence = detector.detect(pkg, snapshot.labels, snapshot.complete);
                    String sample = pkg + "\nVisible labels: " + snapshot.labels.size()
                            + " · complete read: " + snapshot.complete + "\nEmpty labels ignored: " + snapshot.emptyLabels
                            + "\nEvidence: " + evidence;
                    if (!sample.equals(prefs.getString("sample", ""))) prefs.edit().putString("sample", sample).apply();
                }
            } finally { root.recycle(); }
        }
        // A missing root releases immediately: we cannot safely establish app ownership.
        boolean protect = session.update(pkg, evidence, now, prefs.getBoolean(pkg, true));
        boolean testActive = now < debugComfortUntil && pkg.equals(debugComfortOwner) && prefs.getBoolean(pkg, true);
        if (!pkg.equals(debugComfortOwner)) debugComfortUntil = 0;
        protect |= testActive;
        if (protect) {
            if (prefs.getBoolean("mute", true)) volume.acquire(); else volume.release();
            if (prefs.getBoolean("cover", true)) cover.show(); else cover.hide();
            AudioManager audio = getSystemService(AudioManager.class);
            boolean soundNotMuted = prefs.getBoolean("mute", true)
                    && (audio.isVolumeFixed() || audio.getStreamVolume(AudioManager.STREAM_MUSIC) != 0);
            status(testActive ? "Device test · releases automatically after 10 seconds"
                    : soundNotMuted ? "Ad detected · media audio is not muted" : "Ad detected · comfort protection active");
        } else {
            release();
            status(session.state() == AdSession.State.COOLDOWN ? "Safety release · waiting for ad labels to clear"
                    : AdDetector.PACKAGES.contains(pkg) ? evidence == AdDetector.Evidence.UNKNOWN
                    ? "Player labels unavailable or incomplete" : "Watching for ad labels" : "Waiting for a selected streaming app");
        }
    }
    private void release() {
        try { if (cover != null) cover.hide(); }
        catch (RuntimeException ignored) { }
        try { if (volume != null) volume.release(); }
        catch (RuntimeException ignored) { /* durable volume remains available for reconnect recovery */ }
    }
    private void status(String value) {
        if (prefs != null && !value.equals(prefs.getString("status", ""))) prefs.edit().putString("status", value).apply();
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (prefs == null) return false;
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastBack <= 800) {
                pausedUntil = now + 60000;
                debugComfortUntil = 0;
                session.reset(); release(); status("Paused for one minute · double Back");
                lastBack = -10000;
            } else lastBack = now;
        }
        // Preserve the remote's original behavior, including navigation and volume keys.
        return false;
    }
    @Override public void onInterrupt() {
        pausedUntil = SystemClock.elapsedRealtime() + 60000;
        debugComfortUntil = 0;
        session.reset(); release();
    }
    private void stop() {
        running = false; debugComfortUntil = 0; handler.removeCallbacksAndMessages(null); session.reset(); release(); status("Service stopped");
    }
    @Override public boolean onUnbind(Intent intent) { stop(); return super.onUnbind(intent); }
    @Override public void onDestroy() { stop(); super.onDestroy(); }
    private boolean debugBuild() { return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0; }
    private void rememberMarkerEvent(AccessibilityEvent event) {
        if (!debugBuild() || event.getPackageName() == null
                || !AdDetector.PACKAGES.contains(event.getPackageName().toString())) return;
        List<String> markers = new ArrayList<>();
        for (CharSequence value : event.getText()) {
            String text = value == null ? "" : value.toString();
            String marker = AdDetector.marker(text);
            if (!marker.isEmpty()) markers.add(marker);
            else {
                String normalized = AdDetector.normalize(text);
                if (normalized.contains("sponsored")) markers.add("MENTIONS_SPONSORED");
                if (normalized.contains("send to phone")) markers.add("MENTIONS_SEND_TO_PHONE");
            }
        }
        String descriptionMarker = AdDetector.marker(event.getContentDescription() == null
                ? null : event.getContentDescription().toString());
        if (!descriptionMarker.isEmpty()) markers.add(descriptionMarker);
        synchronized (debugMarkerEvents) {
            debugMarkerEvents.addLast(SystemClock.elapsedRealtime() + " " + event.getPackageName()
                    + " " + AccessibilityEvent.eventTypeToString(event.getEventType())
                    + " textItems=" + event.getText().size() + " markers=" + markers);
            while (debugMarkerEvents.size() > 20) debugMarkerEvents.removeFirst();
        }
    }
    /** Explicit ADB diagnostics in debug builds: marker enums/counts, no screenshots or arbitrary text. */
    @Override protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (!debugBuild()) { writer.println("Diagnostics are available in debug builds only."); return; }
        writer.println("ADMute state=" + session.state() + " running=" + running);
        writer.println("paused=" + (SystemClock.elapsedRealtime() < pausedUntil));
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) writer.println("No active accessibility root.");
        else try {
            String pkg = String.valueOf(root.getPackageName());
            if (!AdDetector.PACKAGES.contains(pkg)) writer.println("No selected streaming app in foreground.");
            else {
                NodeSnapshot snapshot = NodeSnapshot.read(root);
                writer.println("package=" + pkg + " nodes=" + snapshot.visited + " rawLabels=" + snapshot.rawLabels
                        + " emptyLabels=" + snapshot.emptyLabels + " meaningfulLabels=" + snapshot.labels.size());
                for (AdDetector.Label label : snapshot.labels) {
                    String marker = AdDetector.marker(label.text());
                    if (!marker.isEmpty()) writer.println("rootMarker=" + marker + " control=" + label.playerControl()
                            + " eligible=" + label.eligible());
                }
                writer.println("rootEvidence=" + detector.detect(pkg, snapshot.labels, snapshot.complete));
            }
        } finally { root.recycle(); }
        writer.println("Recent selected-app event categories (in memory only):");
        synchronized (debugMarkerEvents) { for (String item : debugMarkerEvents) writer.println(item); }
        if (args != null && java.util.Arrays.asList(args).contains("screenshot-probe")) {
            startScreenshotProbe();
            writer.println("One-shot screenshot probe requested; read probeSummary in debug preferences for the result.");
        }
        if (args != null && java.util.Arrays.asList(args).contains("comfort-test")) {
            AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active == null) writer.println("No active app; comfort test not started.");
            else try {
                String owner = String.valueOf(active.getPackageName());
                if (AdDetector.PACKAGES.contains(owner) && prefs.getBoolean(owner, true)
                        && prefs.getBoolean("enabled", true) && SystemClock.elapsedRealtime() >= pausedUntil) {
                    debugComfortOwner = owner;
                    debugComfortUntil = SystemClock.elapsedRealtime() + 10000;
                    writer.println("Explicit 10-second device test armed for " + owner + "; this is not ad detection.");
                } else writer.println("Open an enabled selected app and unpause protection before testing.");
            } finally { active.recycle(); }
        }
    }
    private void probeResult(String message) {
        if (prefs != null) prefs.edit().putString("probeSummary", message).apply();
    }
    private boolean foregroundMatches(String expected) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try { return expected.equals(String.valueOf(root.getPackageName())); }
        finally { root.recycle(); }
    }
    private void startScreenshotProbe() {
        if (!debugBuild()) return;
        if (Build.VERSION.SDK_INT < 30) { probeResult("Screenshot API requires Android 11 or newer."); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { probeResult("No active root; no screenshot taken."); return; }
        String owner;
        try { owner = String.valueOf(root.getPackageName()); }
        finally { root.recycle(); }
        if (!AdDetector.PACKAGES.contains(owner)) { probeResult("Open a supported app before the probe."); return; }
        probeResult("Capture pending for " + owner);
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                Bitmap hardware = null, pixels = null;
                try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                    if (!running || !foregroundMatches(owner)) { probeResult("Foreground changed; frame discarded."); return; }
                    hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                    if (hardware == null) { probeResult("Screenshot bitmap unavailable."); return; }
                    pixels = hardware.copy(Bitmap.Config.ARGB_8888, false);
                    if (pixels == null) { probeResult("Screenshot pixels unavailable."); return; }
                    int visible = 0, samples = 0;
                    long brightness = 0;
                    int stepX = Math.max(1, pixels.getWidth() / 320);
                    int stepY = Math.max(1, pixels.getHeight() / 180);
                    for (int y = 0; y < pixels.getHeight(); y += stepY) {
                        for (int x = 0; x < pixels.getWidth(); x += stepX) {
                            int color = pixels.getPixel(x, y);
                            int level = Math.max((color >> 16) & 255, Math.max((color >> 8) & 255, color & 255));
                            if (level > 20) visible++;
                            brightness += level; samples++;
                        }
                    }
                    probeResult(owner + " · visible samples=" + visible + "/" + samples
                            + " · mean brightness=" + (brightness / Math.max(1, samples))
                            + " · frame discarded; no OCR has run");
                } catch (RuntimeException error) { probeResult("Screenshot processing failed; frame discarded."); }
                finally {
                    if (pixels != null) pixels.recycle();
                    if (hardware != null) hardware.recycle();
                }
            }
            @Override public void onFailure(int errorCode) { probeResult("Screenshot API failed, code=" + errorCode); }
        });
    }
}
