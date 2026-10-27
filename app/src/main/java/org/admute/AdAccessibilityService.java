package org.admute;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Path;
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
import org.admute.core.AdControlDetector;
import org.admute.core.AdControlGuard;
import org.admute.core.AdSession;
import org.admute.core.VolumeLease;
import org.admute.core.VisualAdDetector;
import org.admute.core.VisualAdSession;
import org.admute.core.EnhancedAudioController;
import android.util.SparseArray;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AdAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService adControlExecutor = Executors.newSingleThreadExecutor();
    private final AdDetector detector = new AdDetector();
    private final AdControlDetector adControlDetector = new AdControlDetector();
    private final AdSession session = new AdSession();
    private final VisualAdDetector visualDetector = new VisualAdDetector();
    private final VisualAdSession visualSession = new VisualAdSession();
    private long visualGeneration, lastVisualScan, nextVisualScanAt;
    private boolean visualScanInFlight;
    private android.graphics.Rect[] visualWindows = new android.graphics.Rect[0];
    private String visualResult = "NOT_SCANNED";
    private boolean foregroundNotice;
    private String reliabilityIssue = "";
    private ScreenCover cover;
    private VolumeLease volume;
    private EnhancedAudioController enhancedAudio;
    private SharedPreferences prefs;
    private long lastBack = -10000;
    private long pausedUntil;
    private boolean running;
    private boolean recoveryNeedsAttention;
    private boolean coverNeedsAttention;
    private final ArrayDeque<String> debugMarkerEvents = new ArrayDeque<>();
    private long debugComfortUntil;
    private String debugComfortOwner = "";
    private TextRecognizer textRecognizer;
    private boolean adControlScanInFlight;
    private long lastAdControlScan;
    private long nextAdControlScanAt;
    private String adControlIssue = "";
    private final AdControlGuard adControlGuard = new AdControlGuard();
    private String foregroundOwner = "";
    private String adControlResult = "NONE";
    private ProcessGuard processGuard;
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
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (preferences, key) -> {
        if ("enabled".equals(key) || "autoSkip".equals(key) || "mute".equals(key)
                || "cover".equals(key) || "visualProtection".equals(key) || AdDetector.PACKAGES.contains(key)) {
            resetVisual();
            nextVisualScanAt = 0;
            adControlGuard.reset();
            nextAdControlScanAt = 0;
            adControlIssue = "";
            if (running) { handler.removeCallbacks(tick); handler.post(tick); }
        }
    };
    // The recovery baseline must be durable before media volume is changed.
    @SuppressLint("ApplySharedPref")
    @Override protected void onServiceConnected() {
        release();
        adControlGuard.reset();
        prefs = Preferences.get(this);
        prefs.registerOnSharedPreferenceChangeListener(settingsListener);
        cover = new ScreenCover(this);
        processGuard = new ProcessGuard(this);
        processGuard.enable();
        enhancedAudio = new EnhancedAudioController(this);
        AudioManager audio = getSystemService(AudioManager.class);
        volume = new VolumeLease(new VolumeLease.Port() {
            public int volume() { return audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
            public boolean fixed() { return audio.isVolumeFixed(); }
            public boolean muted() { return audio.isStreamMute(AudioManager.STREAM_MUSIC); }
            public void setMuted(boolean muted) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
            }
            public void setVolume(int value) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0); }
            public void saveMute() {
                if (!prefs.edit().putBoolean("savedMute", true).commit())
                    throw new IllegalStateException("Cannot save mute state");
            }
            public boolean ownsMute() { return prefs.getBoolean("savedMute", false); }
            public void clearMute() {
                if (!prefs.edit().remove("savedMute").commit())
                    throw new IllegalStateException("Cannot clear mute state");
            }
            public void save(int value) {
                if (!prefs.edit().putInt("savedVolume", value).commit()) throw new IllegalStateException("Cannot save volume");
            }
            public int saved() { return prefs.getInt("savedVolume", -1); }
            public void clear() {
                if (!prefs.edit().remove("savedVolume").commit())
                    throw new IllegalStateException("Cannot clear volume state");
            }
        });
        try { volume.recover(); } catch (RuntimeException e) { status("Volume recovery needs attention"); }
        running = true; handler.removeCallbacks(tick); handler.post(tick);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!running || event == null) return;
        rememberMarkerEvent(event);
        // Polls read fresh trees. Events only expedite a foreground change, never serve as evidence.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            visualGeneration++;
            adControlGuard.invalidate();
            handler.removeCallbacks(tick); handler.post(tick);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Setup starts this explicitly while visible; some TV firmwares reject a
        // foreground promotion initiated solely from the accessibility binding.
        if (prefs == null) prefs = Preferences.get(this);
        foregroundNotice = false; reliabilityIssue = "";
        updateForegroundNotice();
        return prefs.getBoolean("enabled", true) ? START_STICKY : START_NOT_STICKY;
    }
    private void evaluate() {
        long now = SystemClock.elapsedRealtime();
        if (processGuard != null) processGuard.recordHeartbeat();
        updateForegroundNotice();
        boolean enabled = prefs.getBoolean("enabled", true) && now >= pausedUntil;
        if (!enabled) { resetVisual(); debugComfortUntil = 0; adControlGuard.reset(); session.reset(); release(); status("Paused"); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = "";
        AdDetector.Evidence evidence = AdDetector.Evidence.UNKNOWN;
        if (root != null) {
            try {
                pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
                if (!pkg.equals(foregroundOwner)) {
                    resetVisual();
                    foregroundOwner = pkg;
                    adControlGuard.reset();
                }
                if (AdDetector.PACKAGES.contains(pkg) && prefs.getBoolean(pkg, true)) {
                    NodeSnapshot snapshot = NodeSnapshot.read(root);
                    evidence = detector.detect(pkg, snapshot.labels, snapshot.complete);
                    if (prefs.getBoolean("autoSkip", false)) {
                        activateAccessibleAdControl(root, pkg, snapshot, evidence);
                    }
                    String sample = pkg + "\nVisible labels: " + snapshot.labels.size()
                            + " · complete read: " + snapshot.complete + "\nEmpty labels ignored: " + snapshot.emptyLabels
                            + "\nEvidence: " + evidence;
                    if (!sample.equals(prefs.getString("sample", ""))) prefs.edit().putString("sample", sample).apply();
                }
            } finally { root.recycle(); }
        }
        if (pkg.isEmpty() && !foregroundOwner.isEmpty()) {
            resetVisual();
            foregroundOwner = "";
            adControlGuard.reset();
        }
        // A missing root releases immediately: we cannot safely establish app ownership.
        boolean protect = session.update(pkg, evidence, now, prefs.getBoolean(pkg, true));
        if (!visualEnabled() || !AdDetector.YOUTUBE.equals(pkg) || !prefs.getBoolean(pkg, true)) resetVisual();
        else requestVisualScan(pkg, now);
        protect |= visualSession.active();
        boolean testActive = now < debugComfortUntil && pkg.equals(debugComfortOwner) && prefs.getBoolean(pkg, true);
        if (!pkg.equals(debugComfortOwner)) debugComfortUntil = 0;
        protect |= testActive;
        if (protect) {
            cover.setLabelWindows(visualSession.active() ? visualWindows : new android.graphics.Rect[0]);
            if (prefs.getBoolean("mute", true)) volume.acquire(); else volume.release();
            if (prefs.getBoolean("cover", true)) cover.show(); else cover.hide();
            AudioManager audio = getSystemService(AudioManager.class);
            boolean soundNotMuted = prefs.getBoolean("mute", true)
                    && !audio.isStreamMute(AudioManager.STREAM_MUSIC)
                    && (audio.isVolumeFixed() || audio.getStreamVolume(AudioManager.STREAM_MUSIC) != 0);
            status(testActive ? "Device test · releases automatically after 10 seconds"
                    : visualSession.active() ? "Visual ad detected · protection active · " + visualResult
                    : soundNotMuted ? "Ad detected · media audio is not muted" : "Ad detected · comfort protection active");
        } else {
            release();
            if (!visualEnabled() && AdDetector.YOUTUBE.equals(pkg) && prefs.getBoolean(pkg, true))
                requestAdControlScan(pkg, now);
            status(session.state() == AdSession.State.COOLDOWN ? "Safety release · waiting for ad labels to clear"
                    : visualEnabled() && AdDetector.YOUTUBE.equals(pkg) ? "Visual YouTube check · " + visualResult
                    : AdDetector.PACKAGES.contains(pkg) ? evidence == AdDetector.Evidence.UNKNOWN
                    ? "Player labels unavailable or incomplete" : "Watching for ad labels" : "Waiting for a selected streaming app");
        }
    }
    private void release() {
        recoveryNeedsAttention = false;
        coverNeedsAttention = false;
        try { if (cover != null) cover.hide(); }
        catch (RuntimeException ignored) { coverNeedsAttention = true; }
        try {
            if (volume != null) { volume.release(); recoveryNeedsAttention = volume.recoveryPending(); }
        } catch (RuntimeException ignored) { recoveryNeedsAttention = true; }
    }
    private void status(String value) {
        if (!reliabilityIssue.isEmpty()) value += " · " + reliabilityIssue;
        if (!visualEnabled() && !adControlIssue.isEmpty() && prefs != null && prefs.getBoolean("autoSkip", false))
            value += " · " + adControlIssue;
        if (coverNeedsAttention) value = "Cover cleanup pending · disable the service · " + value;
        if (recoveryNeedsAttention) value = "Audio recovery pending · adjust volume or reconnect · " + value;
        if (prefs != null && !value.equals(prefs.getString("status", ""))) prefs.edit().putString("status", value).apply();
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (prefs == null) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_UP
                && event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN
                && event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_MUTE) { adControlGuard.invalidate(); visualGeneration++; }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastBack <= 800) {
                pausedUntil = now + 60000;
                resetVisual();
                adControlGuard.reset();
                debugComfortUntil = 0;
                session.reset(); release(); status("Paused for one minute · double Back");
                lastBack = -10000;
            } else lastBack = now;
        }
        // Preserve the remote's original behavior, including navigation and volume keys.
        return false;
    }
    @Override public void onInterrupt() {
        resetVisual();
        adControlGuard.reset();
        pausedUntil = SystemClock.elapsedRealtime() + 60000;
        debugComfortUntil = 0;
        session.reset(); release();
    }
    private void stop() {
        resetVisual();
        adControlGuard.reset();
        if (processGuard != null) processGuard.disable();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(settingsListener);
        running = false; debugComfortUntil = 0; handler.removeCallbacksAndMessages(null); session.reset(); release(); status("Service stopped");
        stopForeground(STOP_FOREGROUND_REMOVE); foregroundNotice = false;
        stopSelf();
    }
    @Override public boolean onUnbind(Intent intent) { stop(); return super.onUnbind(intent); }
    @Override public void onDestroy() {
        stop();
        // Creation, use, and release all stay on the same worker, including late initialization.
        adControlExecutor.execute(() -> {
            if (textRecognizer != null) { textRecognizer.release(); textRecognizer = null; }
        });
        adControlExecutor.shutdown();
        super.onDestroy();
    }
    private boolean debugBuild() { return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0; }
    private void rememberMarkerEvent(AccessibilityEvent event) {
        if (!debugBuild() || event.getPackageName() == null
                || !AdDetector.PACKAGES.contains(event.getPackageName().toString())
                || prefs == null || !prefs.getBoolean(event.getPackageName().toString(), true)) return;
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
        writer.println("visualProtection=" + visualEnabled() + " active=" + visualSession.active()
                + " result=" + visualResult + " scanPending=" + visualScanInFlight);
        writer.println("autoAdControls=" + (prefs != null && prefs.getBoolean("autoSkip", false))
                + " lastResult=" + adControlResult);
        if (!adControlIssue.isEmpty()) writer.println("adControlIssue=" + adControlIssue);
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) writer.println("No active accessibility root.");
        else try {
            String pkg = String.valueOf(root.getPackageName());
            if (!AdDetector.PACKAGES.contains(pkg) || prefs == null || !prefs.getBoolean(pkg, true))
                writer.println("No selected streaming app in foreground.");
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
        if (args != null && java.util.Arrays.asList(args).contains("idle-restart-probe")) {
            if (running && cover != null && !cover.isShowing() && !visualSession.active()
                    && !session.protectedNow() && volume != null && !volume.recoveryPending()
                    && SystemClock.elapsedRealtime() >= debugComfortUntil) {
                writer.println("Idle debug process restart armed; no protection or audio ownership is active.");
                handler.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 1000);
            } else writer.println("Restart probe refused: protection or audio recovery is active.");
        }
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
    private void activateAccessibleAdControl(AccessibilityNodeInfo root, String pkg, NodeSnapshot snapshot,
            AdDetector.Evidence evidence) {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        AdControlDetector.Target target = adControlDetector.findTarget(pkg, snapshot.controls, width, height);
        if (snapshot.complete) adControlGuard.observe(target != null);
        if (target == null || evidence != AdDetector.Evidence.AD) return;
        String key = pkg + ":" + target.action().name();
        if (!adControlGuard.canActivate(key)) return;
        if (performAccessibleClick(root, pkg, target.candidate(), 0, new int[]{0})) {
            adControlGuard.activated(key);
            adControlResult = target.action().name();
            status("Activated visible ad control: " + target.action().name().toLowerCase(Locale.ROOT));
        }
    }
    private boolean performAccessibleClick(AccessibilityNodeInfo node, String owner,
            AdControlDetector.Candidate target, int depth, int[] visited) {
        if (node == null || depth > 40 || ++visited[0] > 1000) return false;
        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        if (owner.equals(String.valueOf(node.getPackageName())) && node.isVisibleToUser()
                && node.isEnabled() && node.isClickable() && matchesLabel(node, target.text())
                && bounds.left == target.left() && bounds.top == target.top()
                && bounds.right == target.right() && bounds.bottom == target.bottom()
                && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { if (performAccessibleClick(child, owner, target, depth + 1, visited)) return true; }
            finally { child.recycle(); }
        }
        return false;
    }
    private boolean matchesLabel(AccessibilityNodeInfo node, String label) {
        String expected = AdDetector.normalize(label);
        CharSequence text = node.getText(), description = node.getContentDescription();
        return (text != null && expected.equals(AdDetector.normalize(text.toString())))
                || (description != null && expected.equals(AdDetector.normalize(description.toString())));
    }
    private boolean visualEnabled() {
        return Build.VERSION.SDK_INT >= 30 && prefs != null && prefs.getBoolean("visualProtection", false);
    }
    private void updateForegroundNotice() {
        boolean enabled = prefs.getBoolean("enabled", true);
        if (!enabled) {
            if (foregroundNotice) stopForeground(STOP_FOREGROUND_REMOVE);
            foregroundNotice = false; return;
        }
        if (foregroundNotice || !reliabilityIssue.isEmpty()) return;
        try {
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            manager.createNotificationChannel(new android.app.NotificationChannel("protection", "Sensory comfort protection",
                    android.app.NotificationManager.IMPORTANCE_LOW));
            android.app.PendingIntent setup = android.app.PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class), android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            android.app.Notification notice = new android.app.Notification.Builder(this, "protection")
                    .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                    .setContentTitle("ADMute protection enabled")
                    .setContentText("Open settings to pause. Double Back releases protection for one minute.")
                    .setContentIntent(setup).setOngoing(true).setCategory(android.app.Notification.CATEGORY_SERVICE).build();
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(101, notice, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(101, notice);
            foregroundNotice = true;
        } catch (RuntimeException error) { reliabilityIssue = "Foreground reliability notice unavailable"; }
    }
    private void resetVisual() {
        visualGeneration++;
        visualSession.reset();
        visualWindows = new android.graphics.Rect[0];
    }
    private boolean visualScanAllowed(String owner, long generation, long started) {
        long now = SystemClock.elapsedRealtime();
        return running && visualEnabled() && generation == visualGeneration && now >= started
                && now - started <= 1500 && prefs.getBoolean("enabled", true)
                && prefs.getBoolean(owner, true) && now >= pausedUntil && foregroundMatches(owner)
                && now >= debugComfortUntil;
    }
    private void visualScanFailed(long generation, String reason) {
        visualScanInFlight = false;
        if (!running || generation != visualGeneration) return;
        visualSession.failed();
        nextVisualScanAt = SystemClock.elapsedRealtime() + 5000;
        visualResult = reason + (visualSession.active() ? " · holding protection; double Back to release" : "");
    }
    private static android.graphics.Rect labelWindow(VisualAdDetector.Box box) {
        return new android.graphics.Rect(box.left - 2, box.top - 2, box.right + 2, box.bottom + 2);
    }
    private void requestVisualScan(String owner, long now) {
        // This Android 11 device enforces a one-second screenshot interval.
        if (Build.VERSION.SDK_INT < 30 || !visualEnabled() || visualScanInFlight || now < nextVisualScanAt || now - lastVisualScan < 1100
                || now < debugComfortUntil || (cover.isShowing() && !visualSession.active())) return;
        final long generation = visualGeneration;
        lastVisualScan = now;
        visualScanInFlight = true;
        try { takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                Bitmap hardware = null, cropped = null, pixels = null;
                try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                    if (!visualScanAllowed(owner, generation, now)) { visualScanInFlight = false; return; }
                    hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                    if (hardware == null) { visualScanFailed(generation, "Capture unavailable"); return; }
                    if (hardware.getWidth() != 1920 || hardware.getHeight() != 1080) {
                        visualScanFailed(generation, "Visual profile requires 1920×1080"); return;
                    }
                    // Retain only the player footer on the worker, reducing bitmap/GC pressure.
                    cropped = Bitmap.createBitmap(hardware, 0, 900, 1920, 180);
                    pixels = cropped.copy(Bitmap.Config.ARGB_8888, false);
                    if (pixels == null) { visualScanFailed(generation, "Pixels unavailable"); return; }
                } catch (RuntimeException error) {
                    if (pixels != null) pixels.recycle();
                    visualScanFailed(generation, "Capture unavailable"); return;
                } finally {
                    if (cropped != null && cropped != hardware) cropped.recycle();
                    if (hardware != null) hardware.recycle();
                }
                final Bitmap frame = pixels;
                final boolean protectedFrame = visualSession.active() && cover.isShowing();
                try { adControlExecutor.execute(() -> {
                    try {
                        int width = frame.getWidth(), height = frame.getHeight();
                        int[] colors = new int[width * height];
                        frame.getPixels(colors, 0, width, 0, 0, width, height);
                        // A black/redacted capture of our own cover cannot prove an ad ended.
                        boolean coverVisible = !protectedFrame || (width == 1920 && height == 180
                                && (colors[10 * width + 900] & 0xffffff) != 0
                                && (colors[20 * width + 1100] & 0xffffff) != 0);
                        VisualAdDetector.Result match = visualDetector.detectFooter(colors, width, height);
                        handler.post(() -> {
                            visualScanInFlight = false;
                            if (!visualScanAllowed(owner, generation, now)) return;
                            if (!match.supported || !coverVisible) {
                                visualScanFailed(generation, !match.supported
                                        ? "Visual profile requires 1920×1080" : "Capture redacted"); return;
                            }
                            boolean wasActive = visualSession.active();
                            nextVisualScanAt = 0;
                            boolean active = visualSession.observe(match.ad(), match.sponsored != null, now);
                            if (!wasActive && active) visualWindows = new android.graphics.Rect[] {
                                    labelWindow(match.sponsored), labelWindow(match.phone) };
                            visualResult = match.ad() ? "AD_PAIR" : match.sponsored != null ? "SPONSORED" : "NO_MARKERS";
                            if (debugBuild()) android.util.Log.i("ADMuteVisual", "capture=" + now
                                    + " result=" + visualResult + " active=" + active
                                    + " latencyMs=" + (SystemClock.elapsedRealtime() - now));
                            evaluate();
                        });
                    } catch (RuntimeException error) {
                        handler.post(() -> visualScanFailed(generation, "Visual processing failed"));
                    } finally { frame.recycle(); }
                }); } catch (RuntimeException error) {
                    frame.recycle(); visualScanFailed(generation, "Visual worker unavailable");
                }
            }
            @Override public void onFailure(int code) { visualScanFailed(generation, "Capture error " + code); }
        }); } catch (RuntimeException error) { visualScanFailed(generation, "Capture request failed"); }
    }
    private void requestAdControlScan(String owner, long now) {
        if (Build.VERSION.SDK_INT < 30 || !prefs.getBoolean("autoSkip", false)
                || !prefs.getBoolean("enabled", true) || now < pausedUntil
                || now < nextAdControlScanAt || adControlScanInFlight || now - lastAdControlScan < 1500) return;
        lastAdControlScan = now;
        long generation = adControlGuard.generation();
        adControlScanInFlight = true;
        try { takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                Bitmap hardware = null, pixels = null;
                try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                    if (!adControlScanAllowed(owner, generation, now)) { adControlScanInFlight = false; return; }
                    hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                    if (hardware == null) { adControlScanFailed(); return; }
                    pixels = hardware.copy(Bitmap.Config.ARGB_8888, false);
                    if (pixels == null) { adControlScanFailed(); return; }
                } catch (RuntimeException error) {
                    adControlScanFailed();
                    return;
                } finally {
                    if (hardware != null) hardware.recycle();
                }
                Bitmap frame = pixels;
                try { adControlExecutor.execute(() -> recognizeAdControls(owner, frame, generation, now)); }
                catch (RuntimeException error) { frame.recycle(); adControlScanFailed(); }
            }
            @Override public void onFailure(int errorCode) { adControlScanFailed(); }
        }); } catch (RuntimeException error) { adControlScanFailed(); }
    }
    private void recognizeAdControls(String owner, Bitmap frame, long generation, long startedAt) {
        try {
            if (textRecognizer == null)
                textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
            if (!textRecognizer.isOperational()) {
                handler.post(() -> adControlScanFailed("Auto controls unavailable: OCR model missing on this TV", 60000));
                return;
            }
            SparseArray<TextBlock> blocks = textRecognizer.detect(new Frame.Builder().setBitmap(frame).build());
            List<AdControlDetector.Candidate> candidates = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                TextBlock block = blocks.valueAt(i);
                for (Text line : block.getComponents()) {
                    android.graphics.Rect bounds = line.getBoundingBox();
                    if (bounds != null) candidates.add(new AdControlDetector.Candidate(
                            line.getValue(), bounds.left, bounds.top, bounds.right, bounds.bottom, true));
                }
            }
            int width = frame.getWidth(), height = frame.getHeight();
            handler.post(() -> {
                try {
                    if (adControlScanAllowed(owner, generation, startedAt)) {
                        adControlIssue = "";
                        nextAdControlScanAt = 0;
                        handleAdControlScan(owner, candidates, width, height);
                    }
                } catch (RuntimeException error) { adControlScanFailed(); }
                finally { adControlScanInFlight = false; }
            });
        } catch (RuntimeException error) {
            handler.post(() -> adControlScanFailed("On-device text recognition failed"));
        } finally {
            frame.recycle();
        }
    }
    private boolean adControlScanAllowed(String owner, long generation, long startedAt) {
        return running && !visualEnabled() && prefs != null && prefs.getBoolean("autoSkip", false)
                && adControlGuard.current(generation, startedAt, SystemClock.elapsedRealtime())
                && prefs.getBoolean("enabled", true) && prefs.getBoolean(owner, true)
                && SystemClock.elapsedRealtime() >= pausedUntil
                && !(SystemClock.elapsedRealtime() < debugComfortUntil && debugComfortOwner.equals(owner))
                && cover != null && !cover.isShowing()
                && foregroundMatches(owner);
    }
    private void adControlScanFailed() { adControlScanFailed("Ad-control scan failed"); }
    private void adControlScanFailed(String message) {
        adControlScanFailed(message, 10000);
    }
    private void adControlScanFailed(String message, long retryDelay) {
        adControlScanInFlight = false;
        adControlResult = "SCAN_FAILED";
        if (running) {
            adControlIssue = message;
            nextAdControlScanAt = SystemClock.elapsedRealtime() + retryDelay;
            status("No automatic control activated");
        }
    }
    private void handleAdControlScan(String owner, List<AdControlDetector.Candidate> candidates,
            int width, int height) {
        AdControlDetector.Target target = adControlDetector.findTarget(owner, candidates, width, height);
        if (target == null) {
            adControlResult = "NO_CONTROL";
            adControlGuard.observe(false, AdControlGuard.Source.OCR);
            return;
        }
        adControlGuard.observe(true, AdControlGuard.Source.OCR);
        String key = owner + ":" + target.action().name();
        if (!adControlGuard.canActivate(key)) return;
        AdControlDetector.Candidate candidate = target.candidate();
        float x = candidate.left() + (candidate.right() - candidate.left()) / 2f;
        float y = candidate.top() + (candidate.bottom() - candidate.top()) / 2f;
        Path path = new Path();
        path.moveTo(x, y);
        android.accessibilityservice.GestureDescription gesture =
                new android.accessibilityservice.GestureDescription.Builder()
                        .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100))
                        .build();
        adControlGuard.activated(key, AdControlGuard.Source.OCR);
        adControlResult = "PENDING_" + target.action().name();
        if (!dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(android.accessibilityservice.GestureDescription completedGesture) {
                adControlResult = target.action().name();
                if (running) status("Activated visible ad control: " + target.action().name().toLowerCase(Locale.ROOT));
            }
            @Override public void onCancelled(android.accessibilityservice.GestureDescription cancelledGesture) {
                // Keep the attempt latched until the control disappears; avoid repeated taps.
                adControlResult = "CANCELLED";
                if (running) status("Could not activate visible ad control");
            }
        }, handler)) {
            // A rejected gesture is also an attempt; do not loop on a persistent control.
            adControlResult = "CANCELLED";
            status("Could not activate visible ad control");
        }
    }
    private void startScreenshotProbe() {
        if (!debugBuild()) return;
        if (Build.VERSION.SDK_INT < 30) { probeResult("Screenshot API requires Android 11 or newer."); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { probeResult("No active root; no screenshot taken."); return; }
        String owner;
        try { owner = String.valueOf(root.getPackageName()); }
        finally { root.recycle(); }
        if (!AdDetector.PACKAGES.contains(owner) || prefs == null || !prefs.getBoolean(owner, true)) {
            probeResult("Open a selected supported app before the probe."); return;
        }
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
