package com.adhdkiller.app.enforcement.shortvideo

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The full-screen "you hit a blocked app" overlay.
 *
 * It is drawn with plain Android views on purpose: this runs inside an accessibility callback
 * where starting a Compose tree would be wasteful, and the screen has to appear within
 * milliseconds of the feed showing up.
 */
class BlockOverlay(private val service: AccessibilityService) {

    private var root: View? = null
    private var windowManager: WindowManager? = null

    fun show(
        title: String,
        appLabel: String,
        reason: String,
        footnote: String,
        onGoBack: () -> Unit,
    ) {
        if (root != null) return
        val manager = windowManager ?: (service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.also { windowManager = it }
            ?: return

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        container.addView(
            TextView(service).apply {
                text = title
                setTextColor(Color.parseColor("#FF4D4D"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                letterSpacing = 0.18f
                gravity = Gravity.CENTER
            }
        )

        container.addView(space(dp(16)))

        container.addView(
            TextView(service).apply {
                text = appLabel
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                gravity = Gravity.CENTER
            }
        )

        container.addView(space(dp(10)))

        container.addView(
            TextView(service).apply {
                text = reason
                setTextColor(Color.parseColor("#A6A6A6"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
            }
        )

        container.addView(space(dp(28)))

        container.addView(
            Button(service).apply {
                text = "Go back"
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.WHITE)
                isAllCaps = false
                setOnClickListener {
                    dismiss()
                    onGoBack()
                }
            }
        )

        container.addView(space(dp(12)))

        container.addView(
            TextView(service).apply {
                text = footnote
                setTextColor(Color.parseColor("#6B6B6B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }

        runCatching {
            manager.addView(container, params)
            root = container
        }
    }

    fun dismiss() {
        val view = root ?: return
        root = null
        runCatching { windowManager?.removeView(view) }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

    private fun space(heightDp: Int): View = View(service).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp))
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    @Suppress("unused")
    private fun frame(context: Context): FrameLayout = FrameLayout(context)
}
