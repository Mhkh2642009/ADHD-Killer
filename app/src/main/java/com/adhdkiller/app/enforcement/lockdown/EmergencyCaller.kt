package com.adhdkiller.app.enforcement.lockdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log

/**
 * Places an emergency call without leaving the lockdown screen.
 *
 * The lockdown must never be the reason someone cannot get help, so this path is deliberately
 * short: dial the number through the platform, and if that fails fall back to the system dialer
 * with the number already filled in.
 */
object EmergencyCaller {

    private const val TAG = "EmergencyCaller"

    /** True when the platform accepted the call. */
    fun place(context: Context, number: String): Boolean {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val placed = runCatching {
            telecom?.placeCall(Uri.parse("tel:$number"), Bundle())
            true
        }.getOrDefault(false)

        if (!placed) {
            Log.w(TAG, "placeCall was refused — falling back to the dialer")
            openDialer(context, number)
        }
        return placed
    }

    /** Fallback: opens the system dialer with the number pre-filled (no permission needed). */
    fun openDialer(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        val launched = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)

        if (!launched) {
            runCatching {
                val dialer = context.packageManager.resolveActivity(intent, 0)
                dialer?.activityInfo?.packageName?.let {
                    context.startActivity(
                        Intent(intent).setPackage(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}
