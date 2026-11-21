package com.adhdkiller.app.enforcement.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * The only privileged surface the app still uses: a plain **device administrator**.
 *
 * Device Owner is gone. It could only be granted on a device without accounts, it made the app
 * impossible to uninstall, and every API it unlocked (package suspension, lock task packages,
 * persistent HOME preference, user restrictions, password policy) is replaced by something in
 * the `enforcement.guard` package.
 *
 * What a plain device administrator still buys us, and the reason it stays:
 *
 *  * While the administrator is active, **Android refuses to uninstall this app**. That is the
 *    single most valuable property for a lockdown tool, and it needs no factory reset, no
 *    account-free device and no `adb`.
 *
 * Everything else in the app works with the administrator switched off. It is a convenience, not
 * a requirement — which is why the Settings screen labels it "optional".
 */
class AdminController(private val context: Context) {

    private val dpm: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    val admin: ComponentName = LockdownDeviceAdminReceiver.adminComponent(context)

    fun isAvailable(): Boolean = dpm != null

    /** True while the user has activated this app as a device administrator. */
    fun isAdminActive(): Boolean = runCatching { dpm?.isAdminActive(admin) == true }.getOrDefault(false)

    /**
     * Legacy check. The app no longer needs Device Owner for anything, but an installation that
     * was provisioned as Device Owner cannot be uninstalled and keeps an unnecessary privilege.
     * The Settings screen uses this to offer a one-tap revoke.
     */
    fun isDeviceOwner(): Boolean = runCatching {
        dpm?.isDeviceOwnerApp(admin.packageName) == true
    }.getOrDefault(false)
}
