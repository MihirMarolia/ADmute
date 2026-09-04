package com.example.admutetv

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat

/**
 * Runs for as long as the accessibility service is enabled, independently of
 * whether this app's own screen is open.
 *
 * Detection combines two sources: the accessibility text of every application
 * window the platform exposes (not only the focused one), and, when the user
 * opts in, ad-domain DNS lookups observed by [AdDnsVpnService]. Media players
 * emit very few accessibility events during full-screen playback, so window
 * content is polled on a timer instead of relying on events alone.
 */
class AdMuteAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val detector = AdDetector()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var volumeController: MediaVolumeController
    private lateinit var statusOverlay: StatusOverlay
    private lateinit var audioManager: AudioManager

    private var lastLikelyAdAtMs = 0L
    private var lastEvaluationAtMs = 0L
    private var lastPackageName: String? = null
    private var pendingRestore: Runnable? = null
    private var commandReceiverRegistered = false
    private var polling = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            val interval = runCatching { evaluate(EvaluationTrigger.POLL) }
                .getOrDefault(SettingsRepository.DEFAULT_SCAN_INTERVAL_MS)
            handler.postDelayed(this, interval)
        }
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!::volumeController.isInitialized) return
            when (intent.action) {
                StatusContract.ACTION_RESTORE_NOW -> {
                    cancelPendingRestore()
                    restoreAndReport("Manual restore requested")
                }
                StatusContract.ACTION_REQUEST_STATUS -> publishStatus(describeState())
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(this)
        volumeController = MediaVolumeController(this)
        statusOverlay = StatusOverlay(this)
        audioManager = getSystemService(AudioManager::class.java)

        applyServiceInfo()

        val filter = IntentFilter(StatusContract.ACTION_RESTORE_NOW).apply {
            addAction(StatusContract.ACTION_REQUEST_STATUS)
        }
        ContextCompat.registerReceiver(this, commandReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        commandReceiverRegistered = true

        polling = true
        handler.post(pollRunnable)

        DiagnosticsLog.add("service: connected, background scanning started")
        publishStatus("Ready — scanning every foreground app")
    }

    /**
     * Window content must be readable in every app, including views the app
     * marked as unimportant for accessibility, which is where television
     * players usually place their ad countdown labels.
     */
    private fun applyServiceInfo() {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        info.notificationTimeout = 100
        info.packageNames = null
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::volumeController.isInitialized) return
        event.packageName?.toString()?.let { lastPackageName = it }

        // Events only accelerate a scan; the poll loop is the primary source.
        if (SystemClock.elapsedRealtime() - lastEvaluationAtMs < MIN_EVENT_INTERVAL_MS) return
        evaluate(EvaluationTrigger.EVENT, event.text.toList())
    }

    private fun evaluate(
        trigger: EvaluationTrigger,
        eventTexts: List<CharSequence?> = emptyList()
    ): Long {
        lastEvaluationAtMs = SystemClock.elapsedRealtime()
        val settings = settingsRepository.load()

        if (!settings.enabled) {
            cancelPendingRestore()
            if (volumeController.isMutedByUs) restoreAndReport("Automation is turned off")
            return IDLE_INTERVAL_MS
        }

        val roots = collectWindowRoots(settings)
        val screen = detector.inspect(
            roots = roots,
            eventTexts = eventTexts,
            markers = settings.markers,
            idMarkers = settings.idMarkers,
            collectSamples = settings.verboseDiagnostics
        )

        val networkHit = settings.useNetworkDetection && AdSignals.networkAdActive(settings.networkHoldMs)
        val mediaPlaying = !settings.onlyWhileMediaPlays || isMediaPlaying()
        val adLikely = (screen.isAdLikely || networkHit) && mediaPlaying

        if (settings.verboseDiagnostics && trigger == EvaluationTrigger.POLL) {
            DiagnosticsLog.add(
                "scan: ${roots.size} window(s), ${screen.inspectedNodeCount} nodes, " +
                    "playing=$mediaPlaying, text=${screen.matchedLabels}, net=$networkHit" +
                    screen.sampledTexts.take(6).joinToString(prefix = "\n  seen: ", separator = " | ")
            )
        }

        if (adLikely) {
            lastLikelyAdAtMs = System.currentTimeMillis()
            cancelPendingRestore()
            val source = when {
                screen.isAdLikely && networkHit -> "label ${screen.matchedLabels.firstOrNull()} + ad host ${AdSignals.lastNetworkHost}"
                screen.isAdLikely -> "label ${screen.matchedLabels.firstOrNull()}"
                else -> "ad host ${AdSignals.lastNetworkHost}"
            }
            screen.packageName?.let { lastPackageName = it }

            when (val result = volumeController.mute()) {
                is MediaVolumeController.Result.Muted -> {
                    if (result.externalAudioRoute) {
                        DiagnosticsLog.add(
                            "mute: ${lastPackageName ?: "foreground app"} — $source " +
                                "(audio may be routed to an external HDMI/ARC device — not guaranteed to be heard)"
                        )
                        publishStatus(
                            "Muted (device volume) — $source. Audio is routed over HDMI; " +
                                "an external soundbar/receiver may not have muted.",
                            true,
                            settings.showOverlay
                        )
                    } else {
                        DiagnosticsLog.add("mute: ${lastPackageName ?: "foreground app"} — $source")
                        publishStatus("Muted media volume — $source", true, settings.showOverlay)
                    }
                }
                MediaVolumeController.Result.AlreadyMutedByUs ->
                    publishStatus("Muted media volume — $source", true, settings.showOverlay)
                MediaVolumeController.Result.AlreadyMutedExternally ->
                    publishStatus("Ad detected — media was already muted", false, settings.showOverlay)
                MediaVolumeController.Result.FixedVolumeDevice -> {
                    DiagnosticsLog.add("mute: refused, this device reports fixed media volume")
                    publishStatus("Ad detected — this TV reports fixed media volume", false, settings.showOverlay)
                }
                else -> Unit
            }
            return ACTIVE_INTERVAL_MS
        }

        scheduleRestore(settings.resumeDelayMs)
        return if (volumeController.isMutedByUs || mediaPlaying) {
            settings.scanIntervalMs.coerceIn(ACTIVE_INTERVAL_MS, IDLE_INTERVAL_MS)
        } else {
            IDLE_INTERVAL_MS
        }
    }

    /**
     * Returns the root node of every application window the platform will
     * expose, so playback in a background-launched or non-focused window is
     * still inspected.
     */
    private fun collectWindowRoots(settings: SettingsRepository.AppSettings): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val seen = mutableSetOf<Int>()

        val candidates = buildList {
            runCatching { windows }.getOrNull()?.let { addAll(it) }
        }
        for (window in candidates) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.type != AccessibilityWindowInfo.TYPE_SYSTEM
            ) {
                continue
            }
            val root = window.root ?: continue
            val rootPackage = root.packageName?.toString()
            if (rootPackage == packageName || !settings.appliesTo(rootPackage)) continue
            if (!seen.add(window.id)) continue
            roots.add(root)
        }

        if (roots.isEmpty()) {
            val active = rootInActiveWindow
            val activePackage = active?.packageName?.toString()
            if (active != null && activePackage != packageName && settings.appliesTo(activePackage)) {
                roots.add(active)
            }
        }
        return roots
    }

    private fun isMediaPlaying(): Boolean {
        if (audioManager.isMusicActive) return true
        return audioManager.activePlaybackConfigurations.isNotEmpty()
    }

    override fun onInterrupt() {
        cancelPendingRestore()
        restoreAndReport("Accessibility service interrupted")
    }

    override fun onDestroy() {
        polling = false
        handler.removeCallbacks(pollRunnable)
        cancelPendingRestore()
        if (::volumeController.isInitialized) restoreAndReport("Accessibility service stopped")
        if (commandReceiverRegistered) unregisterReceiver(commandReceiver)
        if (::statusOverlay.isInitialized) statusOverlay.dismiss()
        DiagnosticsLog.add("service: stopped")
        super.onDestroy()
    }

    private fun scheduleRestore(delayMs: Long) {
        if (!volumeController.isMutedByUs || pendingRestore != null) return

        val elapsed = System.currentTimeMillis() - lastLikelyAdAtMs
        val waitMs = (delayMs - elapsed).coerceAtLeast(0L)
        val action = Runnable {
            pendingRestore = null
            restoreAndReport("No ad evidence in any foreground app")
        }
        pendingRestore = action
        handler.postDelayed(action, waitMs)
    }

    private fun cancelPendingRestore() {
        pendingRestore?.let(handler::removeCallbacks)
        pendingRestore = null
    }

    private fun restoreAndReport(reason: String) {
        when (val result = volumeController.restore()) {
            is MediaVolumeController.Result.Restored -> {
                DiagnosticsLog.add("restore: media volume back to ${result.restoredVolume} — $reason")
                publishStatus("Restored media volume to ${result.restoredVolume} — $reason")
            }
            MediaVolumeController.Result.UserOrSystemChangedVolume ->
                publishStatus("Left current media volume unchanged — $reason")
            else -> publishStatus("Ready — $reason")
        }
    }

    private fun describeState(): String = when {
        volumeController.isMutedByUs -> "Muted by AdMute TV"
        AdSignals.networkDetectorRunning -> "Ready — screen text and ad-domain watch active"
        else -> "Ready — scanning every foreground app"
    }

    private fun publishStatus(
        text: String,
        isMuted: Boolean = false,
        overlayEnabled: Boolean = settingsRepository.load().showOverlay
    ) {
        if (overlayEnabled && isMuted) statusOverlay.showMuted(lastPackageName)
        else statusOverlay.dismiss()

        sendBroadcast(StatusContract.intent(this, text, isMuted, lastPackageName))
    }

    private enum class EvaluationTrigger { POLL, EVENT }

    private companion object {
        private const val ACTIVE_INTERVAL_MS = 400L
        private const val IDLE_INTERVAL_MS = 2_000L
        private const val MIN_EVENT_INTERVAL_MS = 250L
    }
}
