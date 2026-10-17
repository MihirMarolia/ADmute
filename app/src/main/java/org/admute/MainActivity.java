package org.admute;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import org.admute.core.AdDetector;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() {
            AccessibilityManager manager = getSystemService(AccessibilityManager.class);
            boolean connected = false;
            for (AccessibilityServiceInfo service : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (getPackageName().equals(service.getResolveInfo().serviceInfo.packageName)) connected = true;
            }
            status.setText(connected ? prefs.getString("status", "Starting service…") : "Setup needed · enable ADMute in Accessibility");
            handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = Preferences.get(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(48), dp(24), dp(48), dp(32)); scroll.addView(panel); setContentView(scroll);
        text(panel, "ADMute", 32);
        text(panel, "A quieter, gentler ad break", 22);
        status = text(panel, "Checking setup…", 18);
        text(panel, "Recognizes English ad labels in selected apps. Ads still play. Detection can miss ads; it cannot guarantee protection from flashes.", 17);
        toggle(panel, "Comfort protection", "enabled");
        toggle(panel, "Mute ad sound", "mute");
        toggle(panel, "Cover ads with a dark, still texture", "cover");
        text(panel, "Visual YouTube protection: locally matches the English Sponsored + Send to phone player labels on the inspected 1920×1080 layout. No OCR model or download is needed. Two tiny label areas remain visible through the cover so it can detect the ad ending. These areas can show picture content. Recognition starts after labels appear and can miss ads or layout changes; it cannot prevent every initial flash or sound. Capture failures hold existing protection; double Back releases it.", 17);
        toggle(panel, "Visual YouTube protection · small label windows", "visualProtection");
        text(panel, "Optional YouTube ad controls: on Android 11 or newer, ADMute scans selected YouTube screens on-device for a lower-right Skip ad, or Skip/Dismiss paired with Sponsored. This older OCR control mode is suspended while visual protection is enabled. Captured pixels and recognized text are discarded and never uploaded.", 17);
        toggle(panel, "Automatically activate visible Dismiss/Skip controls", "autoSkip");
        text(panel, "Selected apps", 20);
        toggle(panel, "YouTube", AdDetector.YOUTUBE);
        toggle(panel, "Tubi", AdDetector.TUBI);
        toggle(panel, "Plex", AdDetector.PLEX);
        button(panel, "Enable accessibility service", view -> new AlertDialog.Builder(this)
                .setTitle("Enable ad comfort")
                .setMessage("ADMute reads on-screen accessibility labels to recognize ad breaks in your selected apps. Optional visual YouTube protection matches two ad labels in local screenshots and leaves tiny label areas visible through its cover to detect the ad ending. Optional OCR controls scan for Dismiss/Skip. Captured pixels and recognized text are discarded; nothing is uploaded. It can activate those controls, control media volume, and show a dark cover. "
                        + "In the next screen, select ADMute and enable it.")
                .setPositiveButton("Open settings", (dialog, which) -> {
                    try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                    catch (RuntimeException e) { new AlertDialog.Builder(this).setMessage("Open TV Settings → Accessibility → ADMute manually.").setPositiveButton("OK", null).show(); }
                }).setNegativeButton("Cancel", null).show());
        button(panel, "Pause protection and restore sound", view -> prefs.edit().putBoolean("enabled", false).apply());
        button(panel, "Preview dark texture", view -> {
            DarkTextureView preview = new DarkTextureView(this);
            preview.setMinimumHeight(dp(220));
            new AlertDialog.Builder(this).setTitle("Still, dark weave").setView(preview)
                    .setPositiveButton("Close", null).show().getWindow().setLayout(dp(640), dp(360));
        });
        button(panel, "Detection check", view -> new AlertDialog.Builder(this)
                .setTitle("Last selected-app sample")
                .setMessage(prefs.getString("sample", "No sample yet. Enable accessibility and open a selected app first.")
                        + "\n\nOnly app name, label count, and detection result are saved here. Screen text is never saved. This sample may be from earlier playback.")
                .setPositiveButton("Close", null).show());
        text(panel, "Recovery: press Back twice within 0.8 seconds to release protection for one minute. Home or leaving the app also releases it. Remote keys keep working under the cover. Some TV/soundbar setups do not support media-volume control.", 17);
        text(panel, "Before relying on this, test a full ad break in each app on your TV. The first fraction of a second may remain visible and audible.", 17);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout panel, String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(Color.rgb(205, 218, 215)); view.setPadding(0, dp(6), 0, dp(6)); panel.addView(view); return view;
    }
    private void toggle(LinearLayout panel, String label, String key) {
        Switch control = new Switch(this); control.setText(label); control.setTextSize(19);
        control.setPadding(dp(12), dp(8), dp(12), dp(8)); control.setMinHeight(dp(52));
        control.setChecked(prefs.getBoolean(key, !"autoSkip".equals(key) && !"visualProtection".equals(key)));
        if (("autoSkip".equals(key) || "visualProtection".equals(key)) && android.os.Build.VERSION.SDK_INT < 30) {
            control.setEnabled(false);
            control.setText(getString(R.string.ad_controls_requires_android_11, label));
        }
        control.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply());
        control.setOnFocusChangeListener((view, focused) -> view.setBackgroundColor(focused ? 0xFF263C34 : Color.TRANSPARENT));
        panel.addView(control);
        if (key.equals("enabled")) prefs.registerOnSharedPreferenceChangeListener(prefListener = (preferences, changed) -> {
            if ("enabled".equals(changed)) control.setChecked(preferences.getBoolean("enabled", true));
        });
    }
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private void button(LinearLayout panel, String label, View.OnClickListener listener) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setOnClickListener(listener);
        panel.addView(button, new LinearLayout.LayoutParams(-1, dp(52)));
    }
    @Override protected void onResume() {
        super.onResume(); handler.post(refresh);
        if (prefs.getBoolean("enabled", true)) {
            AccessibilityManager manager = getSystemService(AccessibilityManager.class);
            for (AccessibilityServiceInfo service : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (getPackageName().equals(service.getResolveInfo().serviceInfo.packageName)) {
                    try { startForegroundService(new Intent(this, AdAccessibilityService.class)); }
                    catch (RuntimeException ignored) { /* Accessibility binding remains the functional owner. */ }
                    break;
                }
            }
        }
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { if (prefListener != null) prefs.unregisterOnSharedPreferenceChangeListener(prefListener); super.onDestroy(); }
}
