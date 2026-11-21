package com.adhdkiller.app.enforcement.lockdown

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the UI needs to know about the lockdown, in one immutable snapshot.
 */
data class LockdownStatus(
    /** True while this app is activated as a device administrator (uninstall protection). */
    val adminActive: Boolean = false,
    /** True while a lockdown window is running. */
    val enforced: Boolean = false,
    val activeWindowId: Long? = null,
    val activeWindowLabel: String? = null,
    val endsAtMillis: Long? = null,
    val nextStartMillis: Long? = null,
    /** How many apps the current lockdown blocks. */
    val blockedPackages: Int = 0,
    val exactAlarmsAllowed: Boolean = true,
    /** True when this app is the device's HOME app (the guardian launcher). */
    val guardianLauncherActive: Boolean = false,
    /** True when screen pinning is used as an extra layer. */
    val screenPinningEnabled: Boolean = false,
    /** True when the accessibility guard is running. */
    val accessibilityGuardActive: Boolean = false,
    val lastEnforcedAt: Long = 0,
    val lastReleasedAt: Long = 0,
    val message: String? = null,
) {
    /**
     * A lockdown can only be enforced when at least one enforcement layer is standing:
     * the accessibility guard, the guardian launcher or screen pinning.
     */
    val canEnforce: Boolean
        get() = accessibilityGuardActive || guardianLauncherActive || screenPinningEnabled
}

class LockdownStateHolder {
    private val _status = MutableStateFlow(LockdownStatus())
    val status: StateFlow<LockdownStatus> = _status.asStateFlow()

    fun update(transform: (LockdownStatus) -> LockdownStatus) {
        _status.value = transform(_status.value)
    }
}
