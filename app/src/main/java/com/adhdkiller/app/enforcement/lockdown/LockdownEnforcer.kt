package com.adhdkiller.app.enforcement.lockdown

import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import com.adhdkiller.app.core.MirrorGuard
import com.adhdkiller.app.core.MirrorSchedule
import com.adhdkiller.app.core.ScheduleMirror
import com.adhdkiller.app.core.Time
import com.adhdkiller.app.data.db.entity.LockdownScheduleEntity
import com.adhdkiller.app.data.db.entity.SOURCE_LOCKDOWN
import com.adhdkiller.app.data.prefs.SettingsRepository
import com.adhdkiller.app.data.repo.BlockedPackageRepository
import com.adhdkiller.app.enforcement.admin.AdminController
import com.adhdkiller.app.enforcement.app.AppInventory
import com.adhdkiller.app.enforcement.guard.GuardRules
import com.adhdkiller.app.enforcement.guard.GuardStateHolder
import kotlinx.coroutines.flow.first

/**
 * Applies and releases the lockdown. Every operation is idempotent: calling [enforce] twice
 * has the same effect as calling it once, which matters because the enforcer is triggered from
 * alarms, broadcasts, the app UI and the boot receiver.
 *
 * The enforcement itself lives in the guard (see `enforcement.guard`): this class decides *what*
 * is blocked and publishes it. It no longer talks to `DevicePolicyManager` at all.
 */
class LockdownEnforcer(
    private val context: Context,
    private val admin: AdminController,
    private val inventory: AppInventory,
    private val blockedRepo: BlockedPackageRepository,
    private val settings: SettingsRepository,
    private val state: LockdownStateHolder,
    private val guard: GuardStateHolder,
    private val mirror: ScheduleMirror,
    private val launcher: LauncherController,
) {

    companion object {
        const val TAG = "LockdownEnforcer"
    }

    /**
     * When the running test lockdown expires. A scheduled window does not need this: it is
     * derived from the schedule every time.
     */
    private var testLockdownUntil: Long = 0L

    /**
     * Brings the device into the locked state for [window].
     * Returns the number of packages that are now blocked.
     */
    suspend fun enforce(window: LockdownWindow, strict: Boolean = window.strict): Int {
        val allowed = allowedPackages()
        val candidates = inventory.installedApps(includeSystem = true).map { it.packageName }
        // An advisory window shows the screen but lets every app through.
        val blocked = if (strict) GuardRules.blockedPackages(candidates, allowed) else emptySet()

        // Remember exactly what we block so the release can clean up after itself. The ledger
        // lives in the encrypted database, which is not readable before the first unlock — the
        // enforcement itself does not depend on it, so a failure here is not fatal.
        runCatching { blocked.forEach { blockedRepo.record(it) } }

        val endsAt = LockdownWindows.endOf(window)
        publishLockdown(active = true, blocked = blocked, label = window.label, endsAt = endsAt)
        mirror.updateGuard {
            it.copy(
                active = true,
                label = window.label,
                endsAtMillis = endsAt,
                allowedPackages = allowed.toList(),
                blockedPackages = blocked.toList(),
            )
        }

        val guardianActive = launcher.isGuardianActive()
        val screenPinning = settings.screenPinningEnabled.first()
        val accessibility = com.adhdkiller.app.enforcement.guard.AppGuardService.instance != null

        val warning = when {
            !accessibility && !guardianActive && !screenPinning ->
                "No enforcement layer is active. Turn on the accessibility guard or make this app " +
                    "your home app, otherwise the lockdown only shows a screen."
            else -> null
        }

        state.update {
            it.copy(
                adminActive = admin.isAdminActive(),
                enforced = true,
                activeWindowId = window.id,
                activeWindowLabel = window.label,
                endsAtMillis = endsAt,
                blockedPackages = blocked.size,
                guardianLauncherActive = guardianActive,
                screenPinningEnabled = screenPinning,
                accessibilityGuardActive = accessibility,
                lastEnforcedAt = System.currentTimeMillis(),
                message = warning,
            )
        }
        Log.i(TAG, "Lockdown enforced: ${blocked.size} packages blocked (window '${window.label}')")
        return blocked.size
    }

    /** Lifts the lockdown and lets every blocked app back in. */
    suspend fun release(reason: String = "manual") {
        publishLockdown(active = false, blocked = emptySet())
        runCatching { blockedRepo.clear(SOURCE_LOCKDOWN) }
        // The allowlist stays mirrored: the pre-unlock path needs it even when no window runs.
        mirror.updateGuard { it.copy(active = false, blockedPackages = emptyList()) }

        state.update {
            it.copy(
                enforced = false,
                activeWindowId = null,
                activeWindowLabel = null,
                endsAtMillis = null,
                blockedPackages = 0,
                lastReleasedAt = System.currentTimeMillis(),
                message = if (reason == "manual") "Lockdown ended." else null,
            )
        }
        Log.i(TAG, "Lockdown released ($reason)")
    }

    /**
     * Starts the five minute test lockdown.
     *
     * A test lockdown is not a scheduled window, so it needs its own lifetime: without it the
     * next [reconcile] would see "no active window" and release the lockdown immediately —
     * which is exactly what used to happen.
     */
    suspend fun startTestLockdown(minutes: Int = 5): Int {
        val now = Time.now()
        val start = Time.minuteOfDay(now.toLocalTime())
        val window = LockdownWindow(
            id = -1,
            label = "Test lockdown",
            daysMask = 0b1111111,
            startMinute = start,
            endMinute = (start + minutes) % 1440,
            strict = true,
        )
        testLockdownUntil = System.currentTimeMillis() + minutes * 60_000L
        return enforce(window)
    }

    /** True while the five minute test lockdown is still running. */
    fun isTestLockdownActive(): Boolean = testLockdownUntil > System.currentTimeMillis()

    /**
     * Brings the device in line with the schedule: enforces when a window is active and
     * releases when it is not.
     */
    suspend fun reconcile(): Boolean {
        val schedules = activeSchedules()
        val entity = LockdownWindows.activeEntity(schedules)
        val window = entity?.let {
            LockdownWindow(it.id, it.label, it.daysMask, it.startMinute, it.endMinute, it.strict)
        }
        return if (window != null) {
            enforce(window)
            true
        } else if (isTestLockdownActive()) {
            // The test lockdown is not on the schedule — keep it running until it expires.
            true
        } else {
            testLockdownUntil = 0L
            val stillBlocking = runCatching {
                blockedRepo.bySource(SOURCE_LOCKDOWN).isNotEmpty()
            }.getOrDefault(false)
            if (state.status.value.enforced || stillBlocking) {
                release("window-ended")
            }
            false
        }
    }

    // -----------------------------------------------------------------------------------
    // Direct-boot path
    // -----------------------------------------------------------------------------------

    /**
     * Pre-unlock enforcement. Only device-protected storage is readable at this point, so the
     * schedule and the block list come from the mirror and no user settings are consulted.
     */
    suspend fun enforcePreUnlock(window: MirrorSchedule): Int {
        val mirrored = mirror.readGuard()
        val allowed = mirrored.allowedPackages.toSet()
        val candidates = inventory.installedApps(includeSystem = true).map { it.packageName }
        val blocked = if (allowed.isEmpty()) {
            emptySet()
        } else {
            GuardRules.blockedPackages(candidates, allowed)
        }

        publishLockdown(
            active = true,
            blocked = blocked,
            label = window.label,
            endsAt = mirrored.endsAtMillis,
        )
        // The strict short-form rules are mirrored too, so they keep working before the first
        // unlock as well.
        val strict = mirrored.strictPackages.toSet()
        if (strict.isNotEmpty()) {
            guard.update { it.copy(strictPackages = strict) }
        }
        Log.i(TAG, "Pre-unlock lockdown enforced: ${blocked.size} packages blocked")
        return blocked.size
    }

    /** Pre-unlock reconcile driven purely by the mirrored schedule. */
    suspend fun reconcilePreUnlock(): Boolean {
        val now = Time.now()
        val window = mirror.read().firstOrNull {
            LockdownWindows.isActive(
                LockdownWindow(0, it.label, it.daysMask, it.startMinute, it.endMinute, it.strict),
                now,
            )
        } ?: return false
        enforcePreUnlock(window)
        return true
    }

    suspend fun activeSchedules(): List<LockdownScheduleEntity> =
        mirror.read().map { it.toEntity() }

    /** Packages that stay usable during a lockdown. */
    suspend fun allowedPackages(): Set<String> {
        val allowed = inventory.protectedPackages().toMutableSet()

        if (isUserUnlocked()) {
            allowed.addAll(settings.allowedPackages.first())
            if (settings.allowLauncherInLockdown.first()) {
                inventory.launcherPackage()?.let { allowed.add(it) }
            }
        } else {
            // Before the first unlock the settings live in credential-encrypted storage and
            // cannot be read — they would silently come back empty. The mirror holds the last
            // known allowlist, so the lockdown does not fall back to "nothing is allowed".
            allowed.addAll(mirror.readGuard().allowedPackages)
        }

        return allowed
    }

    /** True once the user has unlocked the device after a boot. */
    private fun isUserUnlocked(): Boolean = runCatching {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        userManager?.isUserUnlocked ?: true
    }.getOrDefault(true)

    /**
     * Refreshes the capability flags the UI shows (guard running, guardian launcher in force,
     * screen pinning on) without touching the lockdown itself.
     */
    suspend fun refreshCapabilities() {
        val guardianActive = launcher.isGuardianActive()
        val screenPinning = settings.screenPinningEnabled.first()
        val accessibility = com.adhdkiller.app.enforcement.guard.AppGuardService.instance != null

        state.update {
            it.copy(
                adminActive = admin.isAdminActive(),
                guardianLauncherActive = guardianActive,
                screenPinningEnabled = screenPinning,
                accessibilityGuardActive = accessibility,
            )
        }
    }

    /**
     * Mirrors the allowlist into device-protected storage.
     *
     * The pre-unlock boot path cannot read the database (it lives in credential-encrypted
     * storage), so it needs the allowlist to know which packages to block. The mirror is
     * therefore written whenever the app starts, not only while a lockdown is running.
     */
    suspend fun mirrorAllowedPackages() {
        val allowed = allowedPackages()
        val current = mirror.readGuard()
        val blocked = if (current.active) {
            GuardRules.blockedPackages(
                inventory.installedApps(includeSystem = true).map { it.packageName },
                allowed,
            )
        } else {
            emptySet()
        }
        mirror.writeGuard(
            current.copy(
                allowedPackages = allowed.toList(),
                blockedPackages = blocked.toList(),
            )
        )
    }

    /** Publishes the lockdown state to the guard. */
    private fun publishLockdown(
        active: Boolean,
        blocked: Set<String>,
        label: String = "",
        endsAt: Long? = null,
    ) {
        guard.update {
            it.copy(
                lockdownActive = active,
                blockedPackages = if (active) blocked else emptySet(),
                windowLabel = if (active) label else "",
                endsAtMillis = if (active) endsAt else null,
            )
        }
    }

    private fun MirrorSchedule.toEntity(): LockdownScheduleEntity = LockdownScheduleEntity(
        id = 0,
        label = label,
        daysMask = daysMask,
        startMinute = startMinute,
        endMinute = endMinute,
        enabled = true,
        strict = strict,
    )

    /** Intent used by the boot path to open the lockdown screen. */
    fun lockdownIntent(): Intent = Intent(context, LockdownActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
