package org.admute;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.admute.core.AdDetector;
import org.admute.core.AdSession;
import org.admute.core.VolumeLease;

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
        // Polls read fresh trees. Events only expedite a foreground change, never serve as evidence.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handler.removeCallbacks(tick); handler.post(tick);
        }
    }
    private void evaluate() {
        long now = SystemClock.elapsedRealtime();
        boolean enabled = prefs.getBoolean("enabled", true) && now >= pausedUntil;
        if (!enabled) { session.reset(); release(); status("Paused"); return; }
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
                            + " · complete read: " + snapshot.complete + "\nEvidence: " + evidence;
                    if (!sample.equals(prefs.getString("sample", ""))) prefs.edit().putString("sample", sample).apply();
                }
            } finally { root.recycle(); }
        }
        // A missing root releases immediately: we cannot safely establish app ownership.
        boolean protect = session.update(pkg, evidence, now, prefs.getBoolean(pkg, true));
        if (protect) {
            if (prefs.getBoolean("mute", true)) volume.acquire(); else volume.release();
            if (prefs.getBoolean("cover", true)) cover.show(); else cover.hide();
            AudioManager audio = getSystemService(AudioManager.class);
            boolean soundNotMuted = prefs.getBoolean("mute", true)
                    && (audio.isVolumeFixed() || audio.getStreamVolume(AudioManager.STREAM_MUSIC) != 0);
            status(soundNotMuted ? "Ad detected · media audio is not muted" : "Ad detected · comfort protection active");
        } else {
            release();
            status(session.state() == AdSession.State.COOLDOWN ? "Safety release · waiting for ad labels to clear"
                    : AdDetector.PACKAGES.contains(pkg) ? "Watching for ad labels" : "Waiting for a selected streaming app");
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
                session.reset(); release(); status("Paused for one minute · double Back");
                lastBack = -10000;
            } else lastBack = now;
        }
        // Preserve the remote's original behavior, including navigation and volume keys.
        return false;
    }
    @Override public void onInterrupt() {
        pausedUntil = SystemClock.elapsedRealtime() + 60000;
        session.reset(); release();
    }
    private void stop() {
        running = false; handler.removeCallbacksAndMessages(null); session.reset(); release(); status("Service stopped");
    }
    @Override public boolean onUnbind(Intent intent) { stop(); return super.onUnbind(intent); }
    @Override public void onDestroy() { stop(); super.onDestroy(); }
}
