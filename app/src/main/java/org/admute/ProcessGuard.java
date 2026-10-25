package org.admute;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * Protects against OEM process eviction by scheduling periodic heartbeat checks.
 * If the service is killed by OEM's tguard-kill or similar, this guard attempts restart.
 */
public final class ProcessGuard {
    private static final String TAG = "ADMuteProcessGuard";
    private static final String PREF_LAST_HEARTBEAT = "last_heartbeat";
    private static final String PREF_GUARD_ENABLED = "process_guard_enabled";
    private static final long HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds
    private static final long SERVICE_TIMEOUT_MS = 60000; // 60 seconds

    private final Context context;
    private final SharedPreferences prefs;
    private final AlarmManager alarmManager;

    public ProcessGuard(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("process_guard", Context.MODE_PRIVATE);
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public void enable() {
        prefs.edit().putBoolean(PREF_GUARD_ENABLED, true).apply();
        scheduleHeartbeat();
        Log.i(TAG, "Process guard enabled");
    }

    public void disable() {
        prefs.edit().putBoolean(PREF_GUARD_ENABLED, false).apply();
        cancelHeartbeat();
        Log.i(TAG, "Process guard disabled");
    }

    public boolean isEnabled() {
        return prefs.getBoolean(PREF_GUARD_ENABLED, false);
    }

    public void recordHeartbeat() {
        prefs.edit().putLong(PREF_LAST_HEARTBEAT, SystemClock.elapsedRealtime()).apply();
    }

    public long getLastHeartbeat() {
        return prefs.getLong(PREF_LAST_HEARTBEAT, 0);
    }

    public boolean isServiceAlive() {
        long last = getLastHeartbeat();
        if (last == 0) return false;
        return SystemClock.elapsedRealtime() - last < SERVICE_TIMEOUT_MS;
    }

    private void scheduleHeartbeat() {
        if (!isEnabled()) return;

        Intent intent = new Intent(context, HeartbeatReceiver.class);
        intent.setAction("org.admute.HEARTBEAT_CHECK");

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        long triggerAt = SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MS;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                );
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                );
            }
            Log.d(TAG, "Scheduled heartbeat check in " + HEARTBEAT_INTERVAL_MS + "ms");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to schedule heartbeat: " + e.getMessage());
        }
    }

    private void cancelHeartbeat() {
        Intent intent = new Intent(context, HeartbeatReceiver.class);
        intent.setAction("org.admute.HEARTBEAT_CHECK");

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        try {
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "Heartbeat cancelled");
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel heartbeat: " + e.getMessage());
        }
    }

    public static class HeartbeatReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!"org.admute.HEARTBEAT_CHECK".equals(intent.getAction())) return;

            ProcessGuard guard = new ProcessGuard(context);
            if (!guard.isEnabled()) return;

            if (!guard.isServiceAlive()) {
                Log.w(TAG, "Service appears dead, attempting restart");
                // Try to restart the service
                try {
                    Intent serviceIntent = new Intent(context, AdAccessibilityService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    Log.i(TAG, "Service restart initiated");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to restart service: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "Service heartbeat OK");
            }

            // Reschedule next check
            guard.scheduleHeartbeat();
        }
    }
}
