package com.adhdkiller.app.core

import android.content.Context
import android.util.Log

/**
 * Detects Android 17's "Advanced Protection" mode.
 *
 * When the user turns Advanced Protection on, the platform restricts the AccessibilityService
 * API to verified accessibility tools — which disables the precise layer of the short-form
 * blocker. The app detects the mode and tells the user, so the degradation is never silent.
 *
 * The API is looked up reflectively because it only exists on Android 17 (API 37).
 */
object AdvancedProtection {

    private const val CLASS_NAME = "android.security.advancedprotection.AdvancedProtectionManager"

    fun isEnabled(context: Context): Boolean = runCatching {
        val clazz = Class.forName(CLASS_NAME)
        val manager = context.getSystemService(clazz) ?: return false
        val method = clazz.getMethod("isAdvancedProtectionEnabled")
        method.invoke(manager) as? Boolean ?: false
    }.getOrElse {
        Log.d("AdvancedProtection", "Advanced Protection API unavailable: ${it.message}")
        false
    }
}
