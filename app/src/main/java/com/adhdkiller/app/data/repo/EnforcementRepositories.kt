package com.adhdkiller.app.data.repo

import com.adhdkiller.app.data.db.dao.BlockRuleDao
import com.adhdkiller.app.data.db.dao.LockdownScheduleDao
import com.adhdkiller.app.data.db.dao.SuspendedPackageDao
import com.adhdkiller.app.data.db.entity.BlockRuleEntity
import com.adhdkiller.app.data.db.entity.LockdownScheduleEntity
import com.adhdkiller.app.data.db.entity.SOURCE_LOCKDOWN
import com.adhdkiller.app.data.db.entity.SuspendedPackageEntity
import kotlinx.coroutines.flow.Flow

class LockdownScheduleRepository(private val dao: LockdownScheduleDao) {
    val schedules: Flow<List<LockdownScheduleEntity>> = dao.observeAll()
    suspend fun enabled(): List<LockdownScheduleEntity> = dao.getEnabled()
    suspend fun add(schedule: LockdownScheduleEntity): Long = dao.insert(schedule)
    suspend fun update(schedule: LockdownScheduleEntity) = dao.update(schedule)
    suspend fun delete(schedule: LockdownScheduleEntity) = dao.delete(schedule)
}

/**
 * Remembers exactly which packages this app is currently blocking.
 *
 * The table is the one that used to track *suspended* packages (Device Owner could suspend them
 * at the OS level). Without Device Owner nothing is suspended — the guard simply refuses the
 * packages — but the ledger is still the honest record of what the lockdown is holding back, and
 * it is what the release path cleans up.
 */
class BlockedPackageRepository(private val dao: SuspendedPackageDao) {
    suspend fun all(): List<SuspendedPackageEntity> = dao.getAll()
    suspend fun bySource(source: String): List<SuspendedPackageEntity> = dao.getBySource(source)
    suspend fun record(packageName: String, source: String = SOURCE_LOCKDOWN) =
        dao.insert(SuspendedPackageEntity(packageName = packageName, source = source))
    suspend fun forget(packageName: String, source: String = SOURCE_LOCKDOWN) =
        dao.delete(packageName, source)
    suspend fun clear(source: String = SOURCE_LOCKDOWN) = dao.clear(source)
}

class BlockRuleRepository(private val dao: BlockRuleDao) {
    val rules: Flow<List<BlockRuleEntity>> = dao.observeAll()
    suspend fun all(): List<BlockRuleEntity> = dao.getAll()
    suspend fun enabled(): List<BlockRuleEntity> = dao.getEnabled()
    suspend fun findByPackage(packageName: String): BlockRuleEntity? = dao.findByPackage(packageName)
    suspend fun count(): Int = dao.count()
    suspend fun upsert(rule: BlockRuleEntity) = dao.upsert(rule)
    suspend fun delete(rule: BlockRuleEntity) = dao.delete(rule)
}
