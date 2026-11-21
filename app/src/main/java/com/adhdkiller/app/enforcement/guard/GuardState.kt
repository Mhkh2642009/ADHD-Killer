package com.adhdkiller.app.enforcement.guard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the guard is currently supposed to block.
 *
 * This is the single source of truth that replaces the Device Owner APIs. The old design asked
 * the operating system to suspend packages for us; the new design asks the *guard* (the
 * accessibility service, plus the guardian launcher) to refuse to let a blocked app stay in the
 * foreground.
 *
 * Both the phone lockdown and the short-form blocker publish into this snapshot, and both are
 * independent of each other:
 *
 *  * [lockdownActive] + [blockedPackages] — the scheduled phone lockdown. Every package that is
 *    not on the allowlist is blocked while a window is running.
 *  * [strictPackages] — the short-form blocker's "strict" rules. These apps are blocked entirely,
 *    whether or not a lockdown is running.
 */
data class GuardSnapshot(
    /** True while a scheduled lockdown window is running. */
    val lockdownActive: Boolean = false,
    /** Packages the current lockdown refuses to let in. */
    val blockedPackages: Set<String> = emptySet(),
    /** Packages the short-form blocker refuses to let in at all. */
    val strictPackages: Set<String> = emptySet(),
    /** Label of the running lockdown window. */
    val windowLabel: String = "",
    /** When the running lockdown window ends (epoch millis). */
    val endsAtMillis: Long? = null,
    /** Set when the guard could not be armed properly, so the UI can say why. */
    val warning: String? = null,
) {
    val blockedCount: Int get() = blockedPackages.size + strictPackages.size
}

/**
 * The decision logic of the guard, kept free of Android dependencies so it can be unit tested.
 */
object GuardRules {

    /**
     * May [packageName] stay in the foreground?
     *
     * A package is refused when the short-form blocker marked it strict, or when a lockdown is
     * running and the package is not on the allowlist.
     */
    fun isBlocked(snapshot: GuardSnapshot, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName in snapshot.strictPackages) return true
        return snapshot.lockdownActive && packageName in snapshot.blockedPackages
    }

    /**
     * Computes the packages a lockdown must block: everything installed that is not allowed.
     *
     * @param installed every launchable package on the device.
     * @param allowed the packages that stay usable (the app itself, the dialer, the IME, the
     *   user's allowlist …).
     */
    fun blockedPackages(installed: List<String>, allowed: Set<String>): Set<String> =
        installed.asSequence()
            .filter { it !in allowed }
            .filter { it.isNotBlank() }
            .toSet()
}

/**
 * Holds the live [GuardSnapshot]. The enforcers write to it, the accessibility service reads
 * from it, and the UI observes it. It lives in the app process, which is also the process the
 * accessibility service runs in.
 */
class GuardStateHolder {

    private val _snapshot = MutableStateFlow(GuardSnapshot())
    val snapshot: StateFlow<GuardSnapshot> = _snapshot.asStateFlow()

    fun update(transform: (GuardSnapshot) -> GuardSnapshot) {
        _snapshot.value = transform(_snapshot.value)
    }
}
