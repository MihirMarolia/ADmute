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
        resumeDelayMs = preferences.getLong(KEY_RESUME_DELAY_MS, DEFAULT_RESUME_DELAY_MS),
        showOverlay = preferences.getBoolean(KEY_SHOW_OVERLAY, true),
        scanInterval = preferences.getFloat(KEY_SCAN_INTERVAL, DEFAULT_SCAN_INTERVAL).toDouble(),
        verboseDiagnostics = preferences.getBoolean(KEY_VERBOSE_DIAGNOSTICS, false),
        idMarkers = splitLines(preferences.getString(KEY_ID_MARKERS, "").orEmpty()),
        confidenceThreshold = preferences.getFloat(KEY_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD).toDouble()
    )

    fun save(settings: AppSettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putBoolean(KEY_WATCH_ALL_APPS, settings.watchAllApps)
            .putString(KEY_PACKAGE_NAMES, settings.packageNames.joinToString("\n"))
            .putString(KEY_MARKERS, settings.markers.joinToString("\n"))
            .putLong(KEY_RESUME_DELAY_MS, settings.resumeDelayMs)
            .putBoolean(KEY_SHOW_OVERLAY, settings.showOverlay)
            .putFloat(KEY_SCAN_INTERVAL, settings.scanInterval.toFloat())
            .putBoolean(KEY_VERBOSE_DIAGNOSTICS, settings.verboseDiagnostics)
            .putString(KEY_ID_MARKERS, settings.idMarkers.joinToString("\n"))
            .putFloat(KEY_CONFIDENCE_THRESHOLD, settings.confidenceThreshold.toFloat())
            .apply()
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
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
        val resumeDelayMs: Long,
        val showOverlay: Boolean,
        val scanInterval: Double = DEFAULT_SCAN_INTERVAL,
        val verboseDiagnostics: Boolean = false,
        val idMarkers: Set<String> = emptySet(),
        val confidenceThreshold: Double = DEFAULT_CONFIDENCE_THRESHOLD
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
        private const val KEY_RESUME_DELAY_MS = "resume_delay_ms"
        private const val KEY_SHOW_OVERLAY = "show_overlay"
        private const val KEY_SCAN_INTERVAL = "scan_interval"
        private const val KEY_VERBOSE_DIAGNOSTICS = "verbose_diagnostics"
        private const val KEY_ID_MARKERS = "id_markers"
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"

        const val DEFAULT_RESUME_DELAY_MS = 1_800L
        const val DEFAULT_MARKERS = "ad\nadvertisement\nsponsored\nad 1 of\nskip ad"
        val DEFAULT_MARKER_SET: Set<String> = DEFAULT_MARKERS.lineSequence().toSet()
        const val DEFAULT_SCAN_INTERVAL = 2.0
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.5f
    }
}
