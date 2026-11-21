package com.adhdkiller.app.enforcement.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The device administrator entry point.
 *
 * The app no longer asks for Device Owner. A plain device administrator is enough for the one
 * thing that still matters on the platform: while it is active, **Android refuses to uninstall
 * this app**. It needs no factory reset, no account-free device and no `adb`.
 *
 * The enforcement itself lives in `enforcement.guard` — the accessibility guard and the guardian
 * launcher — so everything in this app works with the administrator switched off.
 */
class LockdownDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled — uninstall protection is now active")
    }

    /**
     * Called when the admin is removed (user deactivation or `dpm remove-active-admin`).
     * Best-effort cleanup so a revoked admin does not leave the device in a lockdown that the
     * user can no longer escape.
     */
    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin disabled — releasing the lockdown")
        releaseOnDisable(context)
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        Log.i(TAG, "Device password changed")
    }

    private fun releaseOnDisable(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val container = (appContext as? com.adhdkiller.app.ADHDKillerApp)?.container ?: return@launch
                container.lockdownEnforcer.release("admin-revoked")
            }.onFailure { Log.w(TAG, "Could not release the lockdown: ${it.message}") }
        }
    }

    companion object {
        const val TAG = "LockdownDeviceAdmin"

        fun adminComponent(context: Context): ComponentName =
            ComponentName(context, LockdownDeviceAdminReceiver::class.java)

        /** Intent that opens the system "device admin" activation screen. */
        fun activationIntent(context: Context): Intent =
            Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    adminComponent(context),
                )
    }
}
