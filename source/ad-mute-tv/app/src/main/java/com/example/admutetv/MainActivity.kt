package com.example.admutetv

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
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
    private lateinit var verboseToggle: CheckBox
    private lateinit var forceMuteToggle: CheckBox
    private lateinit var markersInput: EditText
    private lateinit var packagesInput: EditText
    private lateinit var idMarkersInput: EditText
    private lateinit var delayInput: EditText
    private lateinit var scanIntervalInput: EditText
    private lateinit var confidenceInput: EditText
    private lateinit var serviceState: TextView
    private lateinit var liveStatus: TextView
    private lateinit var activityLogText: TextView

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val BACKGROUND_COLOR = 0xFF101113.toInt()
        const val SECONDARY_COLOR = 0xFFBDC1C6.toInt()
        const val READY_COLOR = 0xFFB7F7C2.toInt()
        const val MUTED_COLOR = 0xFFFFB4AB.toInt()
    }
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

    override fun onResume() {
        super.onResume()
        refreshServiceState()
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
            "It mutes STREAM_MUSIC only when a foreground app exposes a configured ad label. It does not recognize screen pixels or audio, and it cannot detect every ad.",
            16f,
            SECONDARY_COLOR,
            18
        )

        serviceState = addText("", 18f, Color.WHITE, 28)
        addButton("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

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
        verboseToggle = addCheckBox("Verbose diagnostics") {}
        forceMuteToggle = addCheckBox("Force mute (bypass volume checks)") {
            if (!renderingSettings) {
                liveStatus.text = if (it) {
                    "STATUS: FORCE MUTE ENABLED\nWill mute even if volume appears already muted"
                } else {
                    "STATUS: FORCE MUTE DISABLED\nNormal volume checking enabled"
                }
                liveStatus.setTextColor(if (it) MUTED_COLOR else READY_COLOR)
            }
        }

        addText("Ad labels to match", 20f, Color.WHITE, 28)
        addText("Advanced patterns supported: /regex/ for regex, *wildcard*, package##pattern for app-specific rules.", 15f, SECONDARY_COLOR, 4)
        addText("Examples: /ad.*[0-9]+s/, *skip*, com.google.android.youtube##ad 1 of", 15f, SECONDARY_COLOR, 2)
        markersInput = addMultilineInput("""ad
advertisement
sponsored
ad 1 of
skip ad
/ad.*[0-9]+s/
*skip*
com.google.android.youtube##ad 1 of
com.google.android.youtube##skip ad
com.google.android.youtube##/advertisement.*[0-9]+s/
com.plexapp.android##advertisement
com.plexapp.android##sponsored
com.plexapp.android##/ad.*[0-9]+s/
com.tubi.tv##advertisement
com.tubi.tv##sponsored
com.tubi.tv##skip ad
com.tubi.tv##/ad.*[0-9]+s/""", 130)

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
        addText("Seconds between background scans. Lower values detect ads faster but use more CPU.", 15f, SECONDARY_COLOR, 4)
        scanIntervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(SECONDARY_COLOR)
            hint = "2.0"
            textSize = 18f
            setSelectAllOnFocus(true)
            setSingleLine(true)
        }
        addView(scanIntervalInput, 6)

        addText("View ID markers", 20f, Color.WHITE, 24)
        addText("Optional: Android view IDs that often contain ads (e.g., 'ad_banner', 'promo'). One per line.", 15f, SECONDARY_COLOR, 4)
        idMarkersInput = addMultilineInput("", 60)

        addText("Confidence threshold", 20f, Color.WHITE, 24)
        addText("Minimum confidence (0.0-1.0) required to mute. Higher values reduce false positives.", 15f, SECONDARY_COLOR, 4)
        confidenceInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(SECONDARY_COLOR)
            hint = "0.5"
            textSize = 18f
            setSelectAllOnFocus(true)
            setSingleLine(true)
        }
        addView(confidenceInput, 6)

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

        addText("Recent activity", 20f, Color.WHITE, 28)
        addText("Shows recent scan results and mute/restore actions when verbose diagnostics is enabled.", 15f, SECONDARY_COLOR, 4)
        activityLogText = addMultilineInput("No activity recorded yet...", 120)
        activityLogText.isFocusable = false
        activityLogText.isClickable = false
        
        val logButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        Button(this).apply {
            text = "Refresh activity log"
            textSize = 15f
            isAllCaps = false
            setOnClickListener { 
                refreshActivityLog()
            }
            logButtonsLayout.addView(this, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                rightMargin = dp(12)
            })
        }
        
        Button(this).apply {
            text = "Clear activity log"
            textSize = 15f
            isAllCaps = false
            setOnClickListener { 
                clearActivityLog()
            }
            logButtonsLayout.addView(this, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        
        addView(logButtonsLayout, 8)

        addText("Filter list management", 20f, Color.WHITE, 28)
        addText("Import/export filter lists in EasyList-compatible format.", 15f, SECONDARY_COLOR, 4)
        
        val filterListButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        Button(this).apply {
            text = "Export filter list"
            textSize = 15f
            isAllCaps = false
            setOnClickListener { 
                exportFilterList()
            }
            filterListButtonsLayout.addView(this, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                rightMargin = dp(12)
            })
        }
        
        Button(this).apply {
            text = "Import filter list"
            textSize = 15f
            isAllCaps = false
            setOnClickListener { 
                importFilterList()
            }
            filterListButtonsLayout.addView(this, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        
        addView(filterListButtonsLayout, 8)

        addText("Pattern test", 20f, Color.WHITE, 28)
        addText("Test your patterns against sample text to verify they work as expected.", 15f, SECONDARY_COLOR, 4)
        
        val testInput = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(SECONDARY_COLOR)
            hint = "Enter text to test (e.g., 'Skip ad in 5 seconds')"
            textSize = 16f
            setSingleLine(true)
        }
        addView(testInput, 4)
        
        val testResultView = addText("No test performed yet", 16f, SECONDARY_COLOR, 4)
        
        Button(this).apply {
            text = "Test patterns"
            textSize = 15f
            isAllCaps = false
            setOnClickListener {
                val testText = testInput.text.toString()
                if (testText.isNotBlank()) {
                    val rules = FilterRule.parseAll(markersInput.text.lineSequence().toList())
                    val matches = rules.filter { it.matches(testText) }
                    if (matches.isNotEmpty()) {
                        val matchDetails = matches.joinToString(", ") { 
                            "${it.originalPattern} (${(it.confidence * 100).toInt()}%)" 
                        }
                        testResultView.text = "MATCHED: $matchDetails"
                        testResultView.setTextColor(READY_COLOR)
                    } else {
                        testResultView.text = "No patterns matched"
                        testResultView.setTextColor(MUTED_COLOR)
                    }
                } else {
                    testResultView.text = "Enter text to test"
                    testResultView.setTextColor(SECONDARY_COLOR)
                }
            }
            addView(this, 8)
        }

        liveStatus = addText("STATUS: WAITING\nOpen Accessibility settings to enable AdMute TV.", 17f, READY_COLOR, 28)
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
        verboseToggle.isChecked = settings.verboseDiagnostics
        forceMuteToggle.isChecked = settings.forceMute
        markersInput.setText(settings.markers.joinToString("\n"))
        packagesInput.setText(settings.packageNames.joinToString("\n"))
        packagesInput.visibility = if (settings.watchAllApps) View.GONE else View.VISIBLE
        delayInput.setText((settings.resumeDelayMs / 1_000.0).toString())
        scanIntervalInput.setText(settings.scanInterval.toString())
        idMarkersInput.setText(settings.idMarkers.joinToString("\n"))
        confidenceInput.setText(settings.confidenceThreshold.toString())
        renderingSettings = false
    }

    private fun currentSettings(enabled: Boolean): SettingsRepository.AppSettings {
        val delay = delayInput.text.toString().toDoubleOrNull()
            ?.times(1_000.0)
            ?.toLong()
            ?.coerceIn(0L, 15_000L)
            ?: SettingsRepository.DEFAULT_RESUME_DELAY_MS
        val scanInterval = scanIntervalInput.text.toString().toDoubleOrNull()
            ?.coerceIn(0.5, 10.0)
            ?: SettingsRepository.DEFAULT_SCAN_INTERVAL
        val confidence = confidenceInput.text.toString().toDoubleOrNull()
            ?.coerceIn(0.0, 1.0)
            ?: SettingsRepository.DEFAULT_CONFIDENCE_THRESHOLD
        return SettingsRepository.AppSettings(
            enabled = enabled,
            watchAllApps = allAppsToggle.isChecked,
            packageNames = packagesInput.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            markers = markersInput.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            resumeDelayMs = delay,
            showOverlay = overlayToggle.isChecked,
            scanInterval = scanInterval,
            verboseDiagnostics = verboseToggle.isChecked,
            idMarkers = idMarkersInput.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            confidenceThreshold = confidence,
            forceMute = forceMuteToggle.isChecked
        )
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
        column.addView(view, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(topMarginDp)
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun refreshActivityLog() {
        val diagnosticsLog = DiagnosticsLog(this)
        activityLogText.setText(diagnosticsLog.formatForDisplay())
    }

    private fun clearActivityLog() {
        val diagnosticsLog = DiagnosticsLog(this)
        diagnosticsLog.clear()
        activityLogText.setText("No activity recorded yet...")
    }

    private fun exportFilterList() {
        val currentMarkers = markersInput.text.toString()
        val filterList = buildString {
            appendLine("# ADmute TV Filter List")
            appendLine("# Exported: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("# Format: Simple text, /regex/, *wildcard*, or package##pattern")
            appendLine()
            append(currentMarkers)
        }
        
        // Copy to clipboard
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ADmute Filter List", filterList)
        clipboard.setPrimaryClip(clip)
        
        liveStatus.text = "STATUS: FILTER LIST COPIED\nFilter list copied to clipboard. Paste to save or share."
        liveStatus.setTextColor(READY_COLOR)
    }

    private fun importFilterList() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString()
            if (text != null) {
                // Filter out comments and empty lines
                val filteredLines = text.lineSequence()
                    .filter { !it.trim().startsWith("#") }
                    .filter { it.trim().isNotEmpty() }
                    .joinToString("\n")
                
                markersInput.setText(filteredLines)
                liveStatus.text = "STATUS: FILTER LIST IMPORTED\nFilter list imported from clipboard. Review and save."
                liveStatus.setTextColor(READY_COLOR)
            } else {
                liveStatus.text = "STATUS: IMPORT FAILED\nClipboard is empty or contains no text."
                liveStatus.setTextColor(MUTED_COLOR)
            }
        } else {
            liveStatus.text = "STATUS: IMPORT FAILED\nClipboard is empty."
            liveStatus.setTextColor(MUTED_COLOR)
        }
    }
}
