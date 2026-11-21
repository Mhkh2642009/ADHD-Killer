package com.adhdkiller.app.enforcement.lockdown

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Controls the **guardian launcher** — this app acting as the device's HOME app.
 *
 * Device Owner used to give us `addPersistentPreferredActivity`, which made the lockdown screen
 * the HOME app automatically. Without Device Owner the platform will not let an app take over
 * HOME by itself, but the *user* can: the system shows a "choose home app" dialog, and from then
 * on the guardian launcher is the HOME app. The user can undo it in the system settings at any
 * time, which is exactly the balance a self-discipline tool should have — the enforcement is
 * strong, but it is never taken away silently.
 *
 * Being HOME buys two things:
 *
 *  * **Reboot survival.** The system launches HOME at boot. If HOME is the guardian launcher,
 *    the launcher sees the mirrored schedule (device-protected storage, readable before the first
 *    unlock) and puts the lockdown screen up instead of an app drawer.
 *  * **A real door.** The launcher only offers the allowed apps while a lockdown is running, so
 *    the blocked apps are not one tap away.
 */
class LauncherController(private val context: Context) {

    companion object {
        const val TAG = "LauncherController"
    }

    /** True when this app is the device's current HOME app. */
    fun isGuardianActive(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            val held = runCatching { roles?.isRoleHeld(RoleManager.ROLE_HOME) == true }.getOrDefault(false)
            // Some OEM builds do not implement the HOME role properly, so fall back to resolving
            // the HOME intent as well.
            if (held) return true
        }
        return currentHomePackage() == context.packageName
    }

    /** The package that currently handles HOME, if it can be resolved. */
    fun currentHomePackage(): String? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }.getOrNull()

    /**
     * The intent that asks the user to make this app the HOME app.
     *
     * On Android 10+ this is the role request dialog. Below that it is the classic "choose home
     * app" intent, which most ROMs show as a picker.
     */
    fun requestIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            val intent = runCatching { roles?.createRequestRoleIntent(RoleManager.ROLE_HOME) }.getOrNull()
            if (intent != null) return intent
        }
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The system screen where the user changes the HOME app. */
    fun homeSettingsIntent(): Intent =
        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** True when the guardian launcher can be used as a lockdown layer right now. */
    fun isUsable(): Boolean = runCatching { isGuardianActive() }.getOrElse {
        Log.w(TAG, "Could not read the HOME role: ${it.message}")
        false
    }
}
