package com.adhdkiller.app

import android.content.Context
import com.adhdkiller.app.core.ScheduleMirror
import com.adhdkiller.app.data.db.ADHDDatabase
import com.adhdkiller.app.data.prefs.SettingsRepository
import com.adhdkiller.app.data.repo.BlockRuleRepository
import com.adhdkiller.app.data.repo.BlockedPackageRepository
import com.adhdkiller.app.data.repo.DistractionRepository
import com.adhdkiller.app.data.repo.FocusRepository
import com.adhdkiller.app.data.repo.GoalRepository
import com.adhdkiller.app.data.repo.GamificationRepository
import com.adhdkiller.app.data.repo.HabitRepository
import com.adhdkiller.app.data.repo.LockdownScheduleRepository
import com.adhdkiller.app.data.repo.ScheduleRepository
import com.adhdkiller.app.data.repo.TaskRepository
import com.adhdkiller.app.data.repo.Xp
import com.adhdkiller.app.enforcement.admin.AdminController
import com.adhdkiller.app.enforcement.app.AppInventory
import com.adhdkiller.app.enforcement.guard.GuardStateHolder
import com.adhdkiller.app.enforcement.lockdown.LauncherController
import com.adhdkiller.app.enforcement.lockdown.LockdownEnforcer
import com.adhdkiller.app.enforcement.lockdown.LockdownScheduler
import com.adhdkiller.app.enforcement.lockdown.LockdownStateHolder
import com.adhdkiller.app.enforcement.lockdown.LockdownWindow
import com.adhdkiller.app.enforcement.shortvideo.ShortFormEnforcer
import com.adhdkiller.app.ui.theme.Accent
import com.adhdkiller.app.ui.theme.AccentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency container. Small enough that a DI framework would be overhead, and
 * explicit enough that another developer (or another AI session) can follow the wiring.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- core ---------------------------------------------------------------------------
    val database: ADHDDatabase = ADHDDatabase.get(appContext)
    val settings: SettingsRepository = SettingsRepository.create(appContext)
    val mirror: ScheduleMirror = ScheduleMirror(appContext)

    private val xpEnabledFlow = MutableStateFlow(true)

    init {
        scope.launch {
            settings.xpEnabled.collect { xpEnabledFlow.value = it }
        }
    }

    private fun xpEnabled(): Boolean = xpEnabledFlow.value

    // --- enforcement --------------------------------------------------------------------
    val admin: AdminController = AdminController(appContext)
    val inventory: AppInventory = AppInventory(appContext)
    val lockdownState: LockdownStateHolder = LockdownStateHolder()
    val guardState: GuardStateHolder = GuardStateHolder()
    val launcher: LauncherController = LauncherController(appContext)

    val blockedPackageRepository = BlockedPackageRepository(database.suspendedPackageDao())
    val lockdownScheduleRepository = LockdownScheduleRepository(database.lockdownScheduleDao())
    val blockRuleRepository = BlockRuleRepository(database.blockRuleDao())

    val lockdownEnforcer = LockdownEnforcer(
        context = appContext,
        admin = admin,
        inventory = inventory,
        blockedRepo = blockedPackageRepository,
        settings = settings,
        state = lockdownState,
        guard = guardState,
        mirror = mirror,
        launcher = launcher,
    )

    val lockdownScheduler = LockdownScheduler(appContext, lockdownState)

    val shortFormEnforcer = ShortFormEnforcer(
        context = appContext,
        inventory = inventory,
        ruleRepository = blockRuleRepository,
        settings = settings,
        guard = guardState,
        mirror = mirror,
    )

    // --- productivity -------------------------------------------------------------------
    val gamification = GamificationRepository(database.xpDao())
    val taskRepository = TaskRepository(database.taskDao(), gamification) { xpEnabled() }
    val habitRepository = HabitRepository(database.habitDao(), gamification) { xpEnabled() }
    val goalRepository = GoalRepository(database.goalDao(), gamification) { xpEnabled() }
    val scheduleRepository = ScheduleRepository(database.scheduleDao())
    val focusRepository = FocusRepository(database.focusDao(), gamification) { xpEnabled() }
    val distractionRepository = DistractionRepository(database.distractionDao())

    val accentState: AccentState = AccentState(gamification, scope)

    /**
     * Called once when the application starts: mirrors the schedule into device-protected
     * storage, re-arms the alarms and re-asserts the current lockdown state.
     */
    fun bootstrap() {
        scope.launch {
            runCatching {
                val schedules = lockdownScheduleRepository.enabled()
                mirror.write(schedules)
                lockdownScheduler.schedule(schedules.map {
                    LockdownWindow(it.id, it.label, it.daysMask, it.startMinute, it.endMinute, it.strict)
                })
                lockdownEnforcer.refreshCapabilities()
                lockdownEnforcer.mirrorAllowedPackages()
                lockdownEnforcer.reconcile()
                shortFormEnforcer.seedDefaultsIfEmpty()
                shortFormEnforcer.reconcile()
                lockdownEnforcer.refreshCapabilities()
            }
        }
    }

    /** Ends the current lockdown on the user's own initiative — with an XP penalty. */
    suspend fun endLockdownEarly() {
        lockdownEnforcer.release("early-exit")
        if (xpEnabled()) {
            gamification.award(Xp.LOCKDOWN_EARLY_EXIT, "Lockdown ended early")
        }
    }
}

/** The level-driven accent colour, exposed as a [StateFlow] for the theme. */
class AccentState(
    gamification: GamificationRepository,
    scope: CoroutineScope,
) {
    val accent: StateFlow<Accent> = gamification.totalXp
        .map { xp -> AccentList[(Xp.levelFor(xp) - 1).coerceIn(0, AccentList.size - 1)] }
        .stateIn(scope, SharingStarted.Eagerly, Accent.MINT)
}
