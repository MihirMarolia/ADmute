package com.example.admutetv

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class AdMuteAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val detector = AdDetector()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var volumeController: MediaVolumeController
    private lateinit var statusOverlay: StatusOverlay
    private lateinit var diagnosticsLog: DiagnosticsLog

    private var lastLikelyAdAtMs = 0L
    private var lastPackageName: String? = null
    private var pendingRestore: Runnable? = null
    private var commandReceiverRegistered = false
    private var pollRunnable: Runnable? = null
    private var isPolling = false

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == StatusContract.ACTION_RESTORE_NOW && ::volumeController.isInitialized) {
                cancelPendingRestore()
                restoreAndReport("Manual restore requested")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(this)
        volumeController = MediaVolumeController(this)
        statusOverlay = StatusOverlay(this)
        diagnosticsLog = DiagnosticsLog(this)
        ContextCompat.registerReceiver(
            this,
            commandReceiver,
            IntentFilter(StatusContract.ACTION_RESTORE_NOW),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        commandReceiverRegistered = true
        diagnosticsLog.logService("connected")
        startPolling()
        publishStatus("Ready — watching for configured ad labels")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::volumeController.isInitialized) return

        val settings = settingsRepository.load()
        if (!settings.enabled) {
            cancelPendingRestore()
            restoreAndReport("Automation is turned off")
            return
        }

        val foregroundPackage = event.packageName?.toString()
        lastPackageName = foregroundPackage ?: lastPackageName
        if (foregroundPackage == packageName || !settings.appliesTo(foregroundPackage)) {
            scheduleRestore(settings.resumeDelayMs)
            return
        }

        val evidence = detector.inspect(
            root = rootInActiveWindow,
            eventTexts = event.text.toList(),
            markers = settings.markers
        )

        if (evidence.isAdLikely) {
            lastLikelyAdAtMs = System.currentTimeMillis()
            cancelPendingRestore()
            when (volumeController.mute()) {
                is MediaVolumeController.Result.Muted -> {
                    publishStatus(
                        "Muted media volume — label: ${evidence.matchedLabels.firstOrNull() ?: "ad"}",
                        isMuted = true,
                        overlayEnabled = settings.showOverlay
                    )
                }
                MediaVolumeController.Result.AlreadyMutedByUs -> {
                    publishStatus("Muted media volume — ad label still visible", true, settings.showOverlay)
                }
                MediaVolumeController.Result.AlreadyMutedExternally -> {
                    publishStatus("Possible ad label found — media was already muted", false, settings.showOverlay)
                }
                MediaVolumeController.Result.FixedVolumeDevice -> {
                    publishStatus("Possible ad label found — this TV has fixed media volume", false, settings.showOverlay)
                }
                else -> Unit
            }
        } else {
            scheduleRestore(settings.resumeDelayMs)
        }
    }

    override fun onInterrupt() {
        cancelPendingRestore()
        restoreAndReport("Accessibility service interrupted")
    }

    override fun onDestroy() {
        stopPolling()
        cancelPendingRestore()
        if (::volumeController.isInitialized) restoreAndReport("Accessibility service stopped")
        if (commandReceiverRegistered) unregisterReceiver(commandReceiver)
        if (::statusOverlay.isInitialized) statusOverlay.dismiss()
        super.onDestroy()
    }

    private fun scheduleRestore(delayMs: Long) {
        if (!volumeController.isMutedByUs || pendingRestore != null) return

        val elapsed = System.currentTimeMillis() - lastLikelyAdAtMs
        val waitMs = (delayMs - elapsed).coerceAtLeast(0L)
        val action = Runnable {
            pendingRestore = null
            restoreAndReport("Ad label no longer visible")
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
                diagnosticsLog.logRestore(lastPackageName, reason)
                publishStatus("Restored media volume to ${result.restoredVolume} — $reason")
            }
            MediaVolumeController.Result.UserOrSystemChangedVolume ->
                publishStatus("Left current media volume unchanged — $reason")
            else -> publishStatus("Ready — $reason")
        }
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

    private fun startPolling() {
        if (isPolling) return
        isPolling = true
        
        pollRunnable = object : Runnable {
            override fun run() {
                if (!isPolling) return
                
                evaluatePoll()
                
                val settings = settingsRepository.load()
                val pollIntervalMs = (settings.scanInterval * 1000).toLong().coerceAtLeast(500)
                handler.postDelayed(this, pollIntervalMs)
            }
        }
        
        val settings = settingsRepository.load()
        val initialDelayMs = (settings.scanInterval * 1000).toLong().coerceAtLeast(500)
        handler.postDelayed(pollRunnable!!, initialDelayMs)
    }

    private fun stopPolling() {
        isPolling = false
        pollRunnable?.let(handler::removeCallbacks)
        pollRunnable = null
    }

    private fun evaluatePoll() {
        if (!::volumeController.isInitialized) return

        val settings = settingsRepository.load()
        if (!settings.enabled) {
            cancelPendingRestore()
            restoreAndReport("Automation is turned off")
            return
        }

        val windowRoots = collectWindowRoots()
        if (windowRoots.isEmpty()) {
            scheduleRestore(settings.resumeDelayMs)
            return
        }

        var foundAd = false
        var matchedLabels = emptyList<String>()
        var totalNodes = 0
        var detectedPackage: String? = null
        var sampleText = ""

        for ((packageName, root) in windowRoots) {
            if (packageName == this.packageName || !settings.appliesTo(packageName)) {
                continue
            }

            val evidence = detector.inspect(
                root = root,
                eventTexts = emptyList(),
                markers = settings.markers
            )

            totalNodes += evidence.inspectedNodeCount
            if (sampleText.isEmpty() && evidence.sampleText.isNotEmpty()) {
                sampleText = evidence.sampleText
            }

            if (evidence.isAdLikely) {
                foundAd = true
                matchedLabels = evidence.matchedLabels
                detectedPackage = packageName
                lastPackageName = packageName
                break
            }
        }
        
        // Log scan results if verbose diagnostics enabled
        if (settings.verboseDiagnostics && windowRoots.isNotEmpty()) {
            val primaryPackage = windowRoots.first().first
            diagnosticsLog.logScan(primaryPackage, totalNodes, false, sampleText)
        }

        if (foundAd) {
            lastLikelyAdAtMs = System.currentTimeMillis()
            cancelPendingRestore()
            when (volumeController.mute()) {
                is MediaVolumeController.Result.Muted -> {
                    diagnosticsLog.logMute(detectedPackage, matchedLabels.firstOrNull() ?: "ad")
                    publishStatus(
                        "Muted media volume — label: ${matchedLabels.firstOrNull() ?: "ad"}",
                        isMuted = true,
                        overlayEnabled = settings.showOverlay
                    )
                }
                MediaVolumeController.Result.AlreadyMutedByUs -> {
                    publishStatus("Muted media volume — ad label still visible", true, settings.showOverlay)
                }
                MediaVolumeController.Result.AlreadyMutedExternally -> {
                    publishStatus("Possible ad label found — media was already muted", false, settings.showOverlay)
                }
                MediaVolumeController.Result.FixedVolumeDevice -> {
                    publishStatus("Possible ad label found — this TV has fixed media volume", false, settings.showOverlay)
                }
                else -> Unit
            }
        } else {
            scheduleRestore(settings.resumeDelayMs)
        }
    }

    private fun collectWindowRoots(): List<Pair<String, android.view.accessibility.AccessibilityNodeInfo>> {
        val roots = mutableListOf<Pair<String, android.view.accessibility.AccessibilityNodeInfo>>()
        
        // Try to get the active window root first
        rootInActiveWindow?.let { activeRoot ->
            windows?.forEach { window ->
                window.root?.let { root ->
                    val packageName = window.packageName?.toString()
                    if (packageName != null) {
                        roots.add(Pair(packageName, root))
                    }
                }
            }
        }
        
        return roots
    }
}
