package com.adhdkiller.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------------------
// Productivity
// ---------------------------------------------------------------------------------------

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** 0 = low, 1 = normal, 2 = high. */
    val priority: Int = 1,
    val dueAt: Long? = null,
    val estimateMinutes: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val tag: String? = null,
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "•",
    val cadence: HabitCadence = HabitCadence.DAILY,
    /** Used by [HabitCadence.TIMES_PER_WEEK]. */
    val timesPerWeek: Int = 7,
    /** Bit mask, bit 0 = Monday … bit 6 = Sunday. Used by [HabitCadence.SPECIFIC_DAYS]. */
    val daysMask: Int = ALL_DAYS,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

enum class HabitCadence { DAILY, TIMES_PER_WEEK, SPECIFIC_DAYS }

const val ALL_DAYS: Int = 0b1111111

@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "epochDay"])
data class HabitLogEntity(
    val habitId: Long,
    val epochDay: Long,
    val count: Int = 1,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val unit: String = "",
    val targetValue: Double = 1.0,
    val currentValue: Double = 0.0,
    val deadline: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Entity(tableName = "schedule_blocks")
data class ScheduleBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** ISO day of week: 1 = Monday … 7 = Sunday. */
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val kind: BlockKind = BlockKind.FOCUS,
    val note: String = "",
)

enum class BlockKind { FOCUS, DEEP_WORK, BREAK, PERSONAL, SOCIAL }

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val minutes: Int,
    val label: String = "",
)

@Entity(tableName = "xp_events")
data class XpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,
    val reason: String,
    val at: Long = System.currentTimeMillis(),
)

// ---------------------------------------------------------------------------------------
// Enforcement
// ---------------------------------------------------------------------------------------

@Entity(tableName = "distraction_events")
data class DistractionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val surface: String,
    val at: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lockdown_schedules")
data class LockdownScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    /** Bit mask, bit 0 = Monday … bit 6 = Sunday. */
    val daysMask: Int = ALL_DAYS,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    /** false = advisory (notification only), true = enforced (suspension + lock task). */
    val strict: Boolean = true,
)

/**
 * Packages that *this app* suspended. Lifting a lockdown restores exactly these packages and
 * nothing else, so a suspension created by the user or another admin is never touched.
 *
 * [source] distinguishes why the package was suspended: the lockdown and the strict
 * short-form blocker can both suspend the same app and must be able to lift their own
 * suspensions independently.
 */
@Entity(tableName = "suspended_packages", primaryKeys = ["packageName", "source"])
data class SuspendedPackageEntity(
    val packageName: String,
    val source: String = SOURCE_LOCKDOWN,
    val suspendedAt: Long = System.currentTimeMillis(),
)

const val SOURCE_LOCKDOWN = "LOCKDOWN"
const val SOURCE_SHORT_FORM = "SHORT_FORM"

@Entity(tableName = "block_rules")
data class BlockRuleEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val enabled: Boolean = true,
    val mode: BlockMode = BlockMode.ACTIVITY,
    /** Comma-separated activity class names or prefixes. */
    val activityPatterns: String = "",
    /** Comma-separated view-id / content-description keywords. */
    val keywords: String = "",
    /**
     * Comma-separated keywords that *veto* a match. Used to keep the YouTube "Shorts" tab in
     * the bottom navigation from triggering a block while the player itself does.
     */
    val excludeKeywords: String = "",
    /** Suspend the whole package instead of only its short-form surfaces. */
    val strict: Boolean = false,
)

enum class BlockMode { ACTIVITY, KEYWORD, WHOLE_APP }
