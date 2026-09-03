package com.example.admutetv

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : android.app.Activity() {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var column: LinearLayout
    private lateinit var automationToggle: CheckBox
    private lateinit var allAppsToggle: CheckBox
    private lateinit var overlayToggle: CheckBox
    private lateinit var onlyWhilePlayingToggle: CheckBox
    private lateinit var networkToggle: CheckBox
    private lateinit var verboseToggle: CheckBox
    private lateinit var markersInput: EditText
    private lateinit var idMarkersInput: EditText
    private lateinit var packagesInput: EditText
    private lateinit var delayInput: EditText
    private lateinit var scanIntervalInput: EditText
    private lateinit var serviceState: TextView
    private lateinit var batteryState: TextView
    private lateinit var liveStatus: TextView
    private lateinit var diagnosticsView: TextView
    private var receiverRegistered = false
    private var renderingSettings = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != StatusContract.ACTION_STATUS_CHANGED) return
            val muted = intent.getBooleanExtra(StatusContract.EXTRA_MUTED, false)
            val message = intent.getStringExtra(StatusContract.EXTRA_MESSAGE).orEmpty()
            liveStatus.text = if (muted) "STATUS: MUTED\n$message" else "STATUS: READY\n$message"
            liveStatus.setTextColor(if (muted) MUTED_COLOR else READY_COLOR)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(this)
        buildLayout()
        populateFields()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_VPN_CONSENT) return
        if (resultCode == RESULT_OK) {
            startService(Intent(this, AdDnsVpnService::class.java))
            settingsRepository.setNetworkDetection(true)
        } else {
            settingsRepository.setNetworkDetection(false)
            renderingSettings = true
            networkToggle.isChecked = false
            renderingSettings = false
            liveStatus.text = "STATUS: NETWORK WATCH DECLINED\nAd-domain detection stays off."
        }
    }

    override fun onResume() {
        super.onResume()
        refreshServiceState()
        refreshDiagnostics()
        sendBroadcast(StatusContract.statusRequestIntent(this))
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                statusReceiver,
                IntentFilter(StatusContract.ACTION_STATUS_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    override fun onPause() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun buildLayout() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BACKGROUND_COLOR)
            isFillViewport = true
        }
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(52), dp(38), dp(52), dp(52))
        }
        scroll.addView(column, ViewGroup.LayoutParams(MATCH, WRAP))
        setContentView(scroll)

        addText("AdMute TV", 34f, Color.WHITE)
        addText("Personal, sideloaded accessibility utility", 17f, SECONDARY_COLOR, 4)
        addText(
            "Once the accessibility service is on, it keeps scanning every foreground app in the background — this screen does not need to stay open. It mutes STREAM_MUSIC when a configured ad label appears in any app window, or, if you enable it, when an ad-server hostname is looked up. It does not recognize screen pixels or audio, so it cannot detect every ad.",
            16f,
            SECONDARY_COLOR,
            18
        )

        serviceState = addText("", 18f, Color.WHITE, 28)
        addButton("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        batteryState = addText("", 15f, SECONDARY_COLOR, 4)
        addButton("Exempt from background restrictions") {
            requestIgnoreBatteryOptimizations()
        }
        addText(
            "Some TVs freeze or restart background services to save memory, which can pause detection for a few seconds at a time. This exemption keeps the service running steadily. It has nothing to do with battery — Android reuses the same setting for background-process management.",
            15f,
            SECONDARY_COLOR,
            4
        )

        automationToggle = addCheckBox("Enable automatic mute") {
            if (!renderingSettings) {
                val settings = currentSettings(enabled = it)
                settingsRepository.save(settings)
                if (!it) sendBroadcast(StatusContract.restoreIntent(this))
                refreshServiceState()
            }
        }
        allAppsToggle = addCheckBox("Watch all foreground apps") {
            if (!renderingSettings) packagesInput.visibility = if (it) View.GONE else View.VISIBLE
        }
        overlayToggle = addCheckBox("Show small MUTE status overlay") {}
        onlyWhilePlayingToggle = addCheckBox("Only mute while media is actually playing") {}
        networkToggle = addCheckBox("Also detect ad breaks from ad-server DNS lookups") { checked ->
            if (!renderingSettings) onNetworkDetectionToggled(checked)
        }
        addText(
            "The DNS watch routes only a private DNS address through a local tunnel to read hostnames. It forwards every lookup unchanged and blocks nothing. Android shows a VPN key icon while it runs.",
            15f,
            SECONDARY_COLOR,
            4
        )
        verboseToggle = addCheckBox("Verbose diagnostics (log the text each app exposes)") {}

        addText("Ad labels to match", 20f, Color.WHITE, 28)
        addText("One phrase per line. Keep phrases specific to avoid accidental mutes.", 15f, SECONDARY_COLOR, 4)
        markersInput = addMultilineInput("ad\nadvertisement\nsponsored\nad 1 of\nskip ad", 110)

        addText("View id fragments to match", 20f, Color.WHITE, 24)
        addText("Matched against resource ids such as ad_countdown. One fragment per line.", 15f, SECONDARY_COLOR, 4)
        idMarkersInput = addMultilineInput(SettingsRepository.DEFAULT_ID_MARKERS, 82)

        addText("Only watch these app packages", 20f, Color.WHITE, 24)
        addText("Used only when “Watch all foreground apps” is off. One Android package name per line.", 15f, SECONDARY_COLOR, 4)
        packagesInput = addMultilineInput("com.example.streamingapp", 82)

        addText("Resume delay", 20f, Color.WHITE, 24)
        addText("Seconds of no matching label before restoring volume. A small delay prevents rapid toggling.", 15f, SECONDARY_COLOR, 4)
        delayInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(SECONDARY_COLOR)
            hint = "1.8"
            textSize = 18f
            setSelectAllOnFocus(true)
            setSingleLine(true)
        }
        addView(delayInput, 6)

        addText("Scan interval", 20f, Color.WHITE, 24)
        addText("Seconds between background window scans. Players emit almost no accessibility events during playback, so this timer is what finds ads in other apps.", 15f, SECONDARY_COLOR, 4)
        scanIntervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(SECONDARY_COLOR)
            hint = "0.7"
            textSize = 18f
            setSelectAllOnFocus(true)
            setSingleLine(true)
        }
        addView(scanIntervalInput, 6)

        addButton("Save configuration") {
            settingsRepository.save(currentSettings(enabled = automationToggle.isChecked))
            liveStatus.text = "STATUS: SAVED\nSettings will be used for the next accessibility event."
            liveStatus.setTextColor(READY_COLOR)
            refreshServiceState()
        }
        addButton("Restore volume now") {
            sendBroadcast(StatusContract.restoreIntent(this))
            liveStatus.text = "STATUS: RESTORE REQUESTED\nThe service will restore only a volume level it muted."
            liveStatus.setTextColor(READY_COLOR)
        }

        liveStatus = addText("STATUS: WAITING\nOpen Accessibility settings to enable AdMute TV.", 17f, READY_COLOR, 28)

        addText("Recent activity", 20f, Color.WHITE, 28)
        addText("Use this to learn the exact labels your streaming apps expose, then add them above.", 15f, SECONDARY_COLOR, 4)
        addButton("Refresh activity log") { refreshDiagnostics() }
        addButton("Clear activity log") {
            DiagnosticsLog.clear()
            refreshDiagnostics()
        }
        diagnosticsView = addText("", 14f, SECONDARY_COLOR, 8)

        addText(
            "Safety behavior: this app records the media volume before it mutes. It restores that level only if it changed it; if media volume is non-zero at restore time, your newer volume choice is left intact.",
            15f,
            SECONDARY_COLOR,
            18
        )
    }

    private fun populateFields() {
        val settings = settingsRepository.load()
        renderingSettings = true
        automationToggle.isChecked = settings.enabled
        allAppsToggle.isChecked = settings.watchAllApps
        overlayToggle.isChecked = settings.showOverlay
        onlyWhilePlayingToggle.isChecked = settings.onlyWhileMediaPlays
        networkToggle.isChecked = settings.useNetworkDetection
        verboseToggle.isChecked = settings.verboseDiagnostics
        markersInput.setText(settings.markers.joinToString("\n"))
        idMarkersInput.setText(settings.idMarkers.joinToString("\n"))
        scanIntervalInput.setText((settings.scanIntervalMs / 1_000.0).toString())
        packagesInput.setText(settings.packageNames.joinToString("\n"))
        packagesInput.visibility = if (settings.watchAllApps) View.GONE else View.VISIBLE
        delayInput.setText((settings.resumeDelayMs / 1_000.0).toString())
        renderingSettings = false
    }

    private fun currentSettings(enabled: Boolean): SettingsRepository.AppSettings {
        val scanInterval = scanIntervalInput.text.toString().toDoubleOrNull()
            ?.times(1_000.0)
            ?.toLong()
            ?.coerceIn(300L, 5_000L)
            ?: SettingsRepository.DEFAULT_SCAN_INTERVAL_MS
        val delay = delayInput.text.toString().toDoubleOrNull()
            ?.times(1_000.0)
            ?.toLong()
            ?.coerceIn(0L, 15_000L)
            ?: SettingsRepository.DEFAULT_RESUME_DELAY_MS
        return SettingsRepository.AppSettings(
            enabled = enabled,
            watchAllApps = allAppsToggle.isChecked,
            packageNames = packagesInput.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            markers = markersInput.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            idMarkers = idMarkersInput.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            resumeDelayMs = delay,
            scanIntervalMs = scanInterval,
            showOverlay = overlayToggle.isChecked,
            onlyWhileMediaPlays = onlyWhilePlayingToggle.isChecked,
            useNetworkDetection = networkToggle.isChecked,
            networkHoldMs = SettingsRepository.DEFAULT_NETWORK_HOLD_MS,
            verboseDiagnostics = verboseToggle.isChecked
        )
    }

    private fun onNetworkDetectionToggled(enabled: Boolean) {
        if (!enabled) {
            settingsRepository.setNetworkDetection(false)
            startService(Intent(this, AdDnsVpnService::class.java).setAction(AdDnsVpnService.ACTION_STOP))
            return
        }
        val consent = VpnService.prepare(this)
        if (consent == null) {
            startService(Intent(this, AdDnsVpnService::class.java))
            settingsRepository.setNetworkDetection(true)
        } else {
            startActivityForResult(consent, REQUEST_VPN_CONSENT)
        }
    }

    private fun refreshDiagnostics() {
        val lines = DiagnosticsLog.snapshot()
        diagnosticsView.text = if (lines.isEmpty()) {
            "No activity recorded yet. Play an ad in a streaming app, then return here."
        } else {
            lines.take(40).joinToString("\n")
        }
    }

    private fun refreshServiceState() {
        val connected = getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                info.resolveInfo.serviceInfo.packageName == packageName &&
                    info.resolveInfo.serviceInfo.name == AdMuteAccessibilityService::class.java.name
            }
        serviceState.text = if (connected) {
            "Accessibility service: ON"
        } else {
            "Accessibility service: OFF — select “Open Accessibility settings”, then enable AdMute TV"
        }
        serviceState.setTextColor(if (connected) READY_COLOR else Color.rgb(255, 183, 77))

        val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        batteryState.text = if (exempt) {
            "Background restrictions: EXEMPT"
        } else {
            "Background restrictions: NOT EXEMPT — select “Exempt from background restrictions” for steadier detection"
        }
        batteryState.setTextColor(if (exempt) READY_COLOR else Color.rgb(255, 183, 77))
    }

    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            // Some OEM builds disable this screen too; fall back to the general list
            // where the user can find AdMute TV and exempt it manually.
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun addCheckBox(label: String, onChanged: (Boolean) -> Unit): CheckBox =
        CheckBox(this).apply {
            text = label
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, checked -> onChanged(checked) }
            addView(this, 16)
        }

    private fun addButton(label: String, onClick: () -> Unit) {
        Button(this).apply {
            text = label
            textSize = 17f
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
            addView(this, 14)
        }
    }

    private fun addMultilineInput(hint: String, minHeightDp: Int): EditText = EditText(this).apply {
        setTextColor(Color.WHITE)
        setHintTextColor(SECONDARY_COLOR)
        textSize = 17f
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        gravity = Gravity.TOP or Gravity.START
        minHeight = dp(minHeightDp)
        setSelectAllOnFocus(false)
    }.also { addView(it, 8) }

    private fun addText(text: String, sizeSp: Float, color: Int, topMarginDp: Int = 0): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            addView(this, topMarginDp)
        }

    private fun addView(view: View, topMarginDp: Int) {
        column.addView(view, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(topMarginDp)
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val BACKGROUND_COLOR = 0xFF101113.toInt()
        const val SECONDARY_COLOR = 0xFFBDC1C6.toInt()
        const val READY_COLOR = 0xFFB7F7C2.toInt()
        const val MUTED_COLOR = 0xFFFFB4AB.toInt()
        const val REQUEST_VPN_CONSENT = 4210
    }
}
