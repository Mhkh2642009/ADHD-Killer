package com.adhdkiller.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.adhdkiller.app.data.db.dao.BlockRuleDao
import com.adhdkiller.app.data.db.dao.DistractionDao
import com.adhdkiller.app.data.db.dao.FocusDao
import com.adhdkiller.app.data.db.dao.GoalDao
import com.adhdkiller.app.data.db.dao.HabitDao
import com.adhdkiller.app.data.db.dao.LockdownScheduleDao
import com.adhdkiller.app.data.db.dao.ScheduleDao
import com.adhdkiller.app.data.db.dao.SuspendedPackageDao
import com.adhdkiller.app.data.db.dao.TaskDao
import com.adhdkiller.app.data.db.dao.XpDao
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

@Database(
    entities = [
        TaskEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        GoalEntity::class,
        ScheduleBlockEntity::class,
        FocusSessionEntity::class,
        XpEventEntity::class,
        DistractionEventEntity::class,
        LockdownScheduleEntity::class,
        SuspendedPackageEntity::class,
        BlockRuleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ADHDDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun goalDao(): GoalDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun focusDao(): FocusDao
    abstract fun xpDao(): XpDao
    abstract fun distractionDao(): DistractionDao
    abstract fun lockdownScheduleDao(): LockdownScheduleDao
    abstract fun suspendedPackageDao(): SuspendedPackageDao
    abstract fun blockRuleDao(): BlockRuleDao

    companion object {
        @Volatile
        private var instance: ADHDDatabase? = null

        fun get(context: Context): ADHDDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ADHDDatabase::class.java,
                    "adhd_killer.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
