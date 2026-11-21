package com.adhdkiller.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.adhdkiller.app.data.db.entity.BlockRuleEntity
import com.adhdkiller.app.data.db.entity.DistractionEventEntity
import com.adhdkiller.app.data.db.entity.FocusSessionEntity
import com.adhdkiller.app.data.db.entity.GoalEntity
import com.adhdkiller.app.data.db.entity.HabitEntity
import com.adhdkiller.app.data.db.entity.HabitLogEntity
import com.adhdkiller.app.data.db.entity.LockdownScheduleEntity
import com.adhdkiller.app.data.db.entity.ScheduleBlockEntity
import com.adhdkiller.app.data.db.entity.SuspendedPackageEntity
import com.adhdkiller.app.data.db.entity.TaskEntity
import com.adhdkiller.app.data.db.entity.XpEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY completedAt IS NOT NULL, completedAt DESC, priority DESC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE completedAt IS NULL")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: Long): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks WHERE completedAt IS NOT NULL")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE completedAt IS NOT NULL AND completedAt >= :since")
    fun observeCompletedSince(since: Long): Flow<Int>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY archived ASC, createdAt ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun findById(id: Long): HabitEntity?

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("SELECT * FROM habit_logs WHERE epochDay >= :from ORDER BY epochDay DESC")
    fun observeLogsSince(from: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY epochDay DESC")
    fun observeLogsFor(habitId: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY epochDay DESC")
    suspend fun getLogsFor(habitId: Long): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun findLog(habitId: Long, epochDay: Long): HabitLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteLog(habitId: Long, epochDay: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY completedAt IS NOT NULL, completedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT COUNT(*) FROM goals WHERE completedAt IS NOT NULL")
    fun observeCompletedCount(): Flow<Int>

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_blocks ORDER BY dayOfWeek ASC, startMinute ASC")
    fun observeAll(): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE dayOfWeek = :day ORDER BY startMinute ASC")
    fun observeForDay(day: Int): Flow<List<ScheduleBlockEntity>>

    @Insert
    suspend fun insert(block: ScheduleBlockEntity): Long

    @Update
    suspend fun update(block: ScheduleBlockEntity)

    @Delete
    suspend fun delete(block: ScheduleBlockEntity)
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun observeSince(since: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(minutes), 0) FROM focus_sessions WHERE startedAt >= :since")
    fun observeMinutesSince(since: Long): Flow<Int>

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Delete
    suspend fun delete(session: FocusSessionEntity)
}

@Dao
interface XpDao {
    @Query("SELECT * FROM xp_events ORDER BY at DESC")
    fun observeAll(): Flow<List<XpEventEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events")
    fun observeTotal(): Flow<Int>

    @Query("SELECT * FROM xp_events WHERE at >= :since ORDER BY at DESC")
    fun observeSince(since: Long): Flow<List<XpEventEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE at >= :since")
    fun observeTotalSince(since: Long): Flow<Int>

    @Insert
    suspend fun insert(event: XpEventEntity): Long
}

@Dao
interface DistractionDao {
    @Query("SELECT * FROM distraction_events ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<DistractionEventEntity>>

    @Query("SELECT COUNT(*) FROM distraction_events WHERE at >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT * FROM distraction_events WHERE at >= :since ORDER BY at DESC")
    fun observeSince(since: Long): Flow<List<DistractionEventEntity>>

    @Insert
    suspend fun insert(event: DistractionEventEntity): Long
}

@Dao
interface LockdownScheduleDao {
    @Query("SELECT * FROM lockdown_schedules ORDER BY startMinute ASC")
    fun observeAll(): Flow<List<LockdownScheduleEntity>>

    @Query("SELECT * FROM lockdown_schedules WHERE enabled = 1 ORDER BY startMinute ASC")
    suspend fun getEnabled(): List<LockdownScheduleEntity>

    @Insert
    suspend fun insert(schedule: LockdownScheduleEntity): Long

    @Update
    suspend fun update(schedule: LockdownScheduleEntity)

    @Delete
    suspend fun delete(schedule: LockdownScheduleEntity)
}

@Dao
interface SuspendedPackageDao {
    @Query("SELECT * FROM suspended_packages")
    suspend fun getAll(): List<SuspendedPackageEntity>

    @Query("SELECT * FROM suspended_packages WHERE source = :source")
    suspend fun getBySource(source: String): List<SuspendedPackageEntity>

    @Query("SELECT * FROM suspended_packages")
    fun observeAll(): Flow<List<SuspendedPackageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SuspendedPackageEntity)

    @Query("DELETE FROM suspended_packages WHERE packageName = :packageName AND source = :source")
    suspend fun delete(packageName: String, source: String)

    @Query("DELETE FROM suspended_packages WHERE source = :source")
    suspend fun clear(source: String)
}

@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM block_rules ORDER BY appLabel ASC")
    fun observeAll(): Flow<List<BlockRuleEntity>>

    @Query("SELECT COUNT(*) FROM block_rules")
    suspend fun count(): Int

    @Query("SELECT * FROM block_rules")
    suspend fun getAll(): List<BlockRuleEntity>

    @Query("SELECT * FROM block_rules WHERE enabled = 1")
    suspend fun getEnabled(): List<BlockRuleEntity>

    @Query("SELECT * FROM block_rules WHERE packageName = :packageName")
    suspend fun findByPackage(packageName: String): BlockRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: BlockRuleEntity)

    @Delete
    suspend fun delete(rule: BlockRuleEntity)
}
