package com.adhdkiller.app.data.repo

import com.adhdkiller.app.data.db.dao.DistractionDao
import com.adhdkiller.app.data.db.dao.FocusDao
import com.adhdkiller.app.data.db.dao.GoalDao
import com.adhdkiller.app.data.db.dao.HabitDao
import com.adhdkiller.app.data.db.dao.ScheduleDao
import com.adhdkiller.app.data.db.dao.TaskDao
import com.adhdkiller.app.data.db.dao.XpDao
import com.adhdkiller.app.data.db.entity.DistractionEventEntity
import com.adhdkiller.app.data.db.entity.FocusSessionEntity
import com.adhdkiller.app.data.db.entity.GoalEntity
import com.adhdkiller.app.data.db.entity.HabitEntity
import com.adhdkiller.app.data.db.entity.HabitLogEntity
import com.adhdkiller.app.data.db.entity.ScheduleBlockEntity
import com.adhdkiller.app.data.db.entity.TaskEntity
import com.adhdkiller.app.data.db.entity.XpEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.floor
import kotlin.math.sqrt

/** XP awarded for each kind of discipline action. */
object Xp {
    const val TASK = 10
    const val HABIT = 5
    const val HABIT_STREAK_BONUS = 15
    const val GOAL_MILESTONE = 50
    const val FOCUS_MINUTE = 1
    const val LOCKDOWN_COMPLETED = 25
    const val LOCKDOWN_EARLY_EXIT = -20

    /** Level curve: level = floor(sqrt(xp / 100)) + 1. */
    fun levelFor(xp: Int): Int = floor(sqrt(xp.coerceAtLeast(0) / 100.0)).toInt() + 1

    /** XP required to reach [level]. */
    fun xpForLevel(level: Int): Int = 100 * (level - 1) * (level - 1)

    /** Progress inside the current level, 0f..1f. */
    fun levelProgress(xp: Int): Float {
        val level = levelFor(xp)
        val floorXp = xpForLevel(level)
        val nextXp = xpForLevel(level + 1)
        if (nextXp <= floorXp) return 1f
        return ((xp - floorXp).toFloat() / (nextXp - floorXp)).coerceIn(0f, 1f)
    }
}

class GamificationRepository(private val xpDao: XpDao) {
    val totalXp: Flow<Int> = xpDao.observeTotal()
    val recentEvents: Flow<List<XpEventEntity>> = xpDao.observeAll()

    fun xpSince(since: Long): Flow<Int> = xpDao.observeTotalSince(since)

    suspend fun award(amount: Int, reason: String) {
        if (amount == 0) return
        xpDao.insert(XpEventEntity(amount = amount, reason = reason))
    }
}

class TaskRepository(
    private val taskDao: TaskDao,
    private val gamification: GamificationRepository,
    private val xpEnabled: () -> Boolean = { true },
) {
    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val openTasks: Flow<List<TaskEntity>> = taskDao.observeOpen()
    val completedCount: Flow<Int> = taskDao.observeCompletedCount()

    fun completedSince(since: Long): Flow<Int> = taskDao.observeCompletedSince(since)

    suspend fun add(task: TaskEntity): Long = taskDao.insert(task)

    suspend fun update(task: TaskEntity) = taskDao.update(task)

    suspend fun delete(task: TaskEntity) = taskDao.delete(task)

    /** Toggles completion and keeps the XP ledger in sync in both directions. */
    suspend fun setCompleted(task: TaskEntity, completed: Boolean) {
        val now = System.currentTimeMillis()
        taskDao.update(task.copy(completedAt = if (completed) now else null))
        if (xpEnabled()) {
            if (completed) gamification.award(Xp.TASK, "Task completed")
            else gamification.award(-Xp.TASK, "Task reopened")
        }
    }
}

class HabitRepository(
    private val habitDao: HabitDao,
    private val gamification: GamificationRepository,
    private val xpEnabled: () -> Boolean = { true },
) {
    val habits: Flow<List<HabitEntity>> = habitDao.observeActive()
    val allHabits: Flow<List<HabitEntity>> = habitDao.observeAll()

    fun logsSince(from: Long): Flow<List<HabitLogEntity>> = habitDao.observeLogsSince(from)

    fun logsFor(habitId: Long): Flow<List<HabitLogEntity>> = habitDao.observeLogsFor(habitId)

    suspend fun add(habit: HabitEntity): Long = habitDao.insert(habit)

    suspend fun update(habit: HabitEntity) = habitDao.update(habit)

    suspend fun delete(habit: HabitEntity) = habitDao.delete(habit)

    /** Logs one occurrence for [epochDay]; awards XP the first time the day is logged. */
    suspend fun log(habit: HabitEntity, epochDay: Long) {
        val alreadyLogged = habitDao.findLog(habit.id, epochDay) != null
        habitDao.upsertLog(HabitLogEntity(habitId = habit.id, epochDay = epochDay, count = 1))
        if (!alreadyLogged && xpEnabled()) {
            val streak = streakOf(habit.id, habitDao.getLogsFor(habit.id))
            gamification.award(Xp.HABIT + (streak.coerceAtMost(3) * 5), "Habit: ${habit.name}")
        }
    }

    suspend fun unlog(habitId: Long, epochDay: Long) = habitDao.deleteLog(habitId, epochDay)
}

/** Current streak of consecutive logged days for a habit. */
fun streakOf(habitId: Long, logs: List<HabitLogEntity>): Int {
    val days = logs.map { it.epochDay }.toSortedSet().reversed()
    if (days.isEmpty()) return 0
    val today = java.time.LocalDate.now().toEpochDay()
    var expected = if (days.contains(today)) today else today - 1
    var streak = 0
    for (day in days) {
        if (day == expected) {
            streak++
            expected--
        } else if (day < expected) {
            break
        }
    }
    return streak
}

class GoalRepository(
    private val goalDao: GoalDao,
    private val gamification: GamificationRepository,
    private val xpEnabled: () -> Boolean = { true },
) {
    val goals: Flow<List<GoalEntity>> = goalDao.observeAll()
    val completedCount: Flow<Int> = goalDao.observeCompletedCount()

    suspend fun add(goal: GoalEntity): Long = goalDao.insert(goal)
    suspend fun update(goal: GoalEntity) = goalDao.update(goal)
    suspend fun delete(goal: GoalEntity) = goalDao.delete(goal)

    suspend fun increment(goal: GoalEntity, delta: Double) {
        val newValue = (goal.currentValue + delta).coerceAtLeast(0.0)
        val reached = goal.currentValue < goal.targetValue && newValue >= goal.targetValue
        goalDao.update(
            goal.copy(
                currentValue = newValue,
                completedAt = if (reached) System.currentTimeMillis() else goal.completedAt,
            )
        )
        if (reached && xpEnabled()) {
            gamification.award(Xp.GOAL_MILESTONE, "Goal reached: ${goal.title}")
        }
    }
}

class ScheduleRepository(private val scheduleDao: ScheduleDao) {
    val blocks: Flow<List<ScheduleBlockEntity>> = scheduleDao.observeAll()
    fun blocksForDay(day: Int): Flow<List<ScheduleBlockEntity>> = scheduleDao.observeForDay(day)
    suspend fun add(block: ScheduleBlockEntity): Long = scheduleDao.insert(block)
    suspend fun update(block: ScheduleBlockEntity) = scheduleDao.update(block)
    suspend fun delete(block: ScheduleBlockEntity) = scheduleDao.delete(block)
}

class FocusRepository(
    private val focusDao: FocusDao,
    private val gamification: GamificationRepository,
    private val xpEnabled: () -> Boolean = { true },
) {
    val sessions: Flow<List<FocusSessionEntity>> = focusDao.observeAll()
    fun minutesSince(since: Long): Flow<Int> = focusDao.observeMinutesSince(since)
    fun sessionsSince(since: Long): Flow<List<FocusSessionEntity>> = focusDao.observeSince(since)

    suspend fun record(startedAt: Long, endedAt: Long, label: String) {
        val minutes = ((endedAt - startedAt) / 60_000L).toInt().coerceAtLeast(0)
        if (minutes == 0) return
        focusDao.insert(FocusSessionEntity(startedAt = startedAt, endedAt = endedAt, minutes = minutes, label = label))
        if (xpEnabled()) gamification.award(minutes * Xp.FOCUS_MINUTE, "Focus session")
    }
}

class DistractionRepository(private val distractionDao: DistractionDao) {
    val recent: Flow<List<DistractionEventEntity>> = distractionDao.observeRecent()
    fun countSince(since: Long): Flow<Int> = distractionDao.observeCountSince(since)
    fun eventsSince(since: Long): Flow<List<DistractionEventEntity>> = distractionDao.observeSince(since)
    suspend fun log(packageName: String, appLabel: String, surface: String) {
        distractionDao.insert(DistractionEventEntity(packageName = packageName, appLabel = appLabel, surface = surface))
    }
}
