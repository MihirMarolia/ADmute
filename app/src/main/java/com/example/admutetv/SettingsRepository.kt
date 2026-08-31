package com.example.admutetv

import android.content.Context
import android.content.SharedPreferences

internal class SettingsRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        watchAllApps = preferences.getBoolean(KEY_WATCH_ALL_APPS, true),
        packageNames = splitLines(preferences.getString(KEY_PACKAGE_NAMES, "").orEmpty()),
        markers = splitLines(
            preferences.getString(KEY_MARKERS, DEFAULT_MARKERS).orEmpty()
        ).ifEmpty { DEFAULT_MARKER_SET },
        idMarkers = splitLines(
            preferences.getString(KEY_ID_MARKERS, DEFAULT_ID_MARKERS).orEmpty()
        ),
        resumeDelayMs = preferences.getLong(KEY_RESUME_DELAY_MS, DEFAULT_RESUME_DELAY_MS),
        scanIntervalMs = preferences.getLong(KEY_SCAN_INTERVAL_MS, DEFAULT_SCAN_INTERVAL_MS),
        showOverlay = preferences.getBoolean(KEY_SHOW_OVERLAY, true),
        onlyWhileMediaPlays = preferences.getBoolean(KEY_ONLY_WHILE_PLAYING, true),
        useNetworkDetection = preferences.getBoolean(KEY_USE_NETWORK, false),
        networkHoldMs = preferences.getLong(KEY_NETWORK_HOLD_MS, DEFAULT_NETWORK_HOLD_MS),
        verboseDiagnostics = preferences.getBoolean(KEY_VERBOSE, false)
    )

    fun save(settings: AppSettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putBoolean(KEY_WATCH_ALL_APPS, settings.watchAllApps)
            .putString(KEY_PACKAGE_NAMES, settings.packageNames.joinToString("\n"))
            .putString(KEY_MARKERS, settings.markers.joinToString("\n"))
            .putString(KEY_ID_MARKERS, settings.idMarkers.joinToString("\n"))
            .putLong(KEY_RESUME_DELAY_MS, settings.resumeDelayMs)
            .putLong(KEY_SCAN_INTERVAL_MS, settings.scanIntervalMs)
            .putBoolean(KEY_SHOW_OVERLAY, settings.showOverlay)
            .putBoolean(KEY_ONLY_WHILE_PLAYING, settings.onlyWhileMediaPlays)
            .putBoolean(KEY_USE_NETWORK, settings.useNetworkDetection)
            .putLong(KEY_NETWORK_HOLD_MS, settings.networkHoldMs)
            .putBoolean(KEY_VERBOSE, settings.verboseDiagnostics)
            .apply()
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setNetworkDetection(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_USE_NETWORK, enabled).apply()
    }

    private fun splitLines(value: String): Set<String> =
        value.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    data class AppSettings(
        val enabled: Boolean,
        val watchAllApps: Boolean,
        val packageNames: Set<String>,
        val markers: Set<String>,
        val idMarkers: Set<String>,
        val resumeDelayMs: Long,
        val scanIntervalMs: Long,
        val showOverlay: Boolean,
        val onlyWhileMediaPlays: Boolean,
        val useNetworkDetection: Boolean,
        val networkHoldMs: Long,
        val verboseDiagnostics: Boolean
    ) {
        fun appliesTo(packageName: String?): Boolean =
            watchAllApps || (packageName != null && packageName in packageNames)
    }

    companion object {
        private const val PREFERENCES_NAME = "ad_mute_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WATCH_ALL_APPS = "watch_all_apps"
        private const val KEY_PACKAGE_NAMES = "package_names"
        private const val KEY_MARKERS = "markers"
        private const val KEY_ID_MARKERS = "id_markers"
        private const val KEY_RESUME_DELAY_MS = "resume_delay_ms"
        private const val KEY_SCAN_INTERVAL_MS = "scan_interval_ms"
        private const val KEY_SHOW_OVERLAY = "show_overlay"
        private const val KEY_ONLY_WHILE_PLAYING = "only_while_playing"
        private const val KEY_USE_NETWORK = "use_network_detection"
        private const val KEY_NETWORK_HOLD_MS = "network_hold_ms"
        private const val KEY_VERBOSE = "verbose_diagnostics"

        const val DEFAULT_RESUME_DELAY_MS = 1_800L
        const val DEFAULT_SCAN_INTERVAL_MS = 700L
        const val DEFAULT_NETWORK_HOLD_MS = 8_000L
        const val DEFAULT_MARKERS =
            "ad\nads\nadvertisement\nsponsored\nad 1 of\nad 2 of\nskip ad\nskip ads\n" +
                "your video will resume\nvideo will resume after\nvisit advertiser\nad will end in"
        const val DEFAULT_ID_MARKERS =
            "ad_countdown\nad_badge\nad_progress\nskip_ad\nad_overlay\nad_text"
        val DEFAULT_MARKER_SET: Set<String> = DEFAULT_MARKERS.lineSequence().toSet()
    }
}
