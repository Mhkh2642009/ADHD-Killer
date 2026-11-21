package com.adhdkiller.app.enforcement.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * Knows which apps exist on the device and which of them must never be suspended.
 *
 * The platform refuses to suspend a handful of packages (device admins, the active launcher,
 * the package installer/uninstaller/verifier, the default dialer, the permission controller).
 * On top of that we protect the current IME — a suspended keyboard makes the device unusable.
 */
class AppInventory(private val context: Context) {

    data class AppEntry(
        val packageName: String,
        val label: String,
        val isSystem: Boolean,
        val isProtected: Boolean,
    )

    private val pm: PackageManager = context.packageManager

    /** Packages that must stay usable during a lockdown. */
    fun protectedPackages(): Set<String> {
        val protected = mutableSetOf(context.packageName)

        launcherPackage()?.let { protected.add(it) }
        defaultDialerPackage()?.let { protected.add(it) }
        protected.addAll(imePackages())
        protected.addAll(alwaysInstalledSystemPackages())

        return protected
    }

    fun launcherPackage(): String? =
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName

    fun defaultDialerPackage(): String? {
        // TelecomManager is the reliable public source for the default dialer package.
        val telecom = context.getSystemService(TelecomManager::class.java)
        runCatching { telecom?.defaultDialerPackage }.getOrNull()?.let { if (it.isNotBlank()) return it }
        return pm.resolveActivity(Intent(Intent.ACTION_DIAL), 0)?.activityInfo?.packageName
    }

    fun imePackages(): Set<String> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return emptySet()
        return imm.enabledInputMethodList.mapNotNull { it.packageName }.toSet()
    }

    /**
     * Packages the platform treats as always-installed. Suspending them breaks core platform
     * behaviour (or is refused outright).
     */
    private fun alwaysInstalledSystemPackages(): Set<String> {
        val names = mutableSetOf(
            "com.android.settings",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.android.providers.settings",
            "com.google.android.permissioncontroller",
        )
        // Package verifier / device admin reconciler variants.
        names += listOf(
            "com.android.externalstorage",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
        )
        return names
    }

    /** All launchable user-visible apps on the device. */
    fun installedApps(includeSystem: Boolean = true): List<AppEntry> {
        val protected = protectedPackages()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        val entries = resolved.map { info ->
            val pkg = info.activityInfo.packageName
            AppEntry(
                packageName = pkg,
                label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg),
                isSystem = (info.activityInfo.applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                isProtected = pkg in protected,
            )
        }

        return entries
            .distinctBy { it.packageName }
            .filter { includeSystem || !it.isSystem }
            .sortedBy { it.label.lowercase() }
    }

    /** Label for a package (falls back to the package name). */
    fun labelOf(packageName: String): String = runCatching {
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    /** True when [packageName] is installed for the current user. */
    fun isInstalled(packageName: String): Boolean = runCatching {
        pm.getApplicationInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /** True when the package is a system app. */
    fun isSystemApp(packageName: String): Boolean = runCatching {
        val info = pm.getApplicationInfo(packageName, 0)
        (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    }.getOrDefault(false)

    /** True when the user has granted "display over other apps" to our package. */
    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)
}
