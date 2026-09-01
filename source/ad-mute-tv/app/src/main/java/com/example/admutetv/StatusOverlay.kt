package com.example.admutetv

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A deliberately non-focusable, non-touchable visual status indicator. It never
 * receives remote-control input or obscures the streaming application's UI.
 */
internal class StatusOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var overlayView: LinearLayout? = null
    private var attached = false

    fun showMuted(foregroundPackage: String?) {
        val view = overlayView ?: createView().also { overlayView = it }
        val title = view.getChildAt(0) as TextView
        val detail = view.getChildAt(1) as TextView
        title.text = "AdMute TV • MEDIA MUTED"
        detail.text = foregroundPackage?.let { "Possible ad label in $it" }
            ?: "Possible ad label in foreground app"

        if (attached) return
        try {
            windowManager.addView(view, layoutParams())
            attached = true
        } catch (_: WindowManager.BadTokenException) {
            // The service can still mute and restore safely if a device refuses
            // the accessibility overlay window.
        } catch (_: SecurityException) {
            // Surface a status in the main control screen instead of crashing.
        }
    }

    fun dismiss() {
        val view = overlayView ?: return
        if (!attached) return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The system already removed the service's window.
        } finally {
            attached = false
        }
    }

    private fun createView(): LinearLayout = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(14), dp(24), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.rgb(89, 26, 31))
            setStroke(dp(1), Color.rgb(255, 180, 171))
        }
        elevation = dp(8).toFloat()
        addView(TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(service).apply {
            setTextColor(Color.rgb(255, 218, 214))
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(32)
        y = dp(32)
        title = "AdMute status"
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()
}
