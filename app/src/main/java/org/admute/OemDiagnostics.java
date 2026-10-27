package org.admute;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostics and helper for OEM-specific restrictions and developer options.
 * Detects common Android TV OEM issues and guides users to enable required settings.
 */
public final class OemDiagnostics {
    private final Context context;
    private final List<String> issues = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public OemDiagnostics(Context context) {
        this.context = context.getApplicationContext();
    }

    public void checkAll() {
        issues.clear();
        warnings.clear();
        checkBatteryOptimization();
        checkBackgroundProcessLimits();
        checkManufacturerRestrictions();
        checkDeveloperOptions();
        checkOverlayPermission();
        checkForegroundServiceRestrictions();
    }

    private void checkBatteryOptimization() {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.getPackageName())) {
            issues.add("Battery optimization not disabled - app may be killed in background");
        }
    }

    private void checkBackgroundProcessLimits() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);
            if (memInfo.lowMemory) {
                warnings.add("Device is in low memory state - background processes may be terminated");
            }
        }
    }

    private void checkManufacturerRestrictions() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        String brand = Build.BRAND.toLowerCase();

        if (manufacturer.contains("tcl") || brand.contains("tcl")) {
            issues.add("TCL device detected - may require APP_AUTO_START permission in OEM settings");
            warnings.add("TCL's tguard-kill may terminate background services; add to device idle whitelist");
        }
        if (manufacturer.contains("sony") || brand.contains("sony")) {
            warnings.add("Sony TV may have additional background restrictions; check app settings");
        }
        if (manufacturer.contains("samsung") || brand.contains("samsung")) {
            warnings.add("Samsung TV may block accessibility services; verify developer options");
        }
        if (manufacturer.contains("lg") || brand.contains("lg")) {
            warnings.add("LG TV may have aggressive power management; check battery optimization");
        }
    }

    private void checkDeveloperOptions() {
        boolean devEnabled = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        if (!devEnabled) {
            issues.add("Developer options not enabled - required for full functionality");
        }
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                issues.add("System overlay permission not granted - required for screen cover");
            }
        }
    }

    private void checkForegroundServiceRestrictions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                if (!am.isLowRamDevice()) {
                    warnings.add("Foreground services may be restricted; ensure app is whitelisted");
                }
            }
        }
    }

    public List<String> getIssues() {
        return new ArrayList<>(issues);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public boolean hasCriticalIssues() {
        return !issues.isEmpty();
    }

    public String getSummary() {
        if (issues.isEmpty() && warnings.isEmpty()) {
            return "No OEM restrictions detected";
        }
        StringBuilder sb = new StringBuilder();
        if (!issues.isEmpty()) {
            sb.append("Critical issues:\n");
            for (String issue : issues) {
                sb.append("• ").append(issue).append("\n");
            }
        }
        if (!warnings.isEmpty()) {
            sb.append("\nWarnings:\n");
            for (String warning : warnings) {
                sb.append("• ").append(warning).append("\n");
            }
        }
        return sb.toString();
    }

    public Intent getBatteryOptimizationIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            return intent;
        }
        return null;
    }

    public Intent getDeveloperOptionsIntent() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public Intent getManufacturerSettingsIntent() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        if (manufacturer.contains("tcl")) {
            // TCL may have specific app auto-start settings
            try {
                Intent intent = new Intent();
                intent.setClassName("com.tcl.settings", "com.tcl.settings.app.AppDetailActivity");
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                return intent;
            } catch (Exception e) {
                // Fall through to generic
            }
        }
        // Generic app settings
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    public Intent getRunningServicesIntent() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    public String getDeviceModel() {
        return Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
    }

    public String getManufacturer() {
        return Build.MANUFACTURER;
    }

    public boolean isTclDevice() {
        return Build.MANUFACTURER.toLowerCase().contains("tcl")
                || Build.BRAND.toLowerCase().contains("tcl");
    }

    public boolean isSonyDevice() {
        return Build.MANUFACTURER.toLowerCase().contains("sony")
                || Build.BRAND.toLowerCase().contains("sony");
    }

    public boolean isSamsungDevice() {
        return Build.MANUFACTURER.toLowerCase().contains("samsung")
                || Build.BRAND.toLowerCase().contains("samsung");
    }

    public boolean isLgDevice() {
        return Build.MANUFACTURER.toLowerCase().contains("lg")
                || Build.BRAND.toLowerCase().contains("lg");
    }
}
