package com.example.turnaway.data.repository

import com.example.turnaway.data.dao.SoftLandingDao
import com.example.turnaway.data.entity.BlockedAppEntity
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.data.entity.ScheduleConfigEntity
import com.example.turnaway.data.entity.SessionLogEntity
import com.example.turnaway.data.entity.TargetAppEntity
import kotlinx.coroutines.flow.Flow

class SoftLandingRepository(private val dao: SoftLandingDao) {

    fun getProfileById(id: String): Flow<RestrictionProfileEntity?> = dao.getProfileById(id)

    fun getAllProfiles(): Flow<List<RestrictionProfileEntity>> = dao.getAllProfiles()

    suspend fun saveProfile(profile: RestrictionProfileEntity) {
        dao.insertProfile(profile)
    }

    fun getActiveSchedules(): Flow<List<ScheduleConfigEntity>> = dao.getActiveSchedules()

    fun getAllSchedules(): Flow<List<ScheduleConfigEntity>> = dao.getAllSchedules()

    suspend fun saveSchedule(schedule: ScheduleConfigEntity) {
        dao.insertSchedule(schedule)
    }

    suspend fun saveSessionLog(log: SessionLogEntity) {
        dao.insertSessionLog(log)
    }

    fun getRecentSessionLogs(): Flow<List<SessionLogEntity>> = dao.getRecentSessionLogs()

    fun getAllTargetApps(): Flow<List<TargetAppEntity>> = dao.getAllTargetApps()

    fun getTargetedApps(): Flow<List<TargetAppEntity>> = dao.getTargetedApps()

    suspend fun getTargetedPackageNamesList(): List<String> = dao.getTargetedPackageNamesList()

    suspend fun saveTargetApp(app: TargetAppEntity) {
        dao.insertTargetApp(app)
    }

    suspend fun saveTargetApps(apps: List<TargetAppEntity>) {
        dao.insertTargetApps(apps)
    }

    suspend fun updateAppTargetStatus(packageName: String, isTargeted: Boolean) {
        dao.updateTargetStatus(packageName, isTargeted)
    }

    suspend fun updateAllTargetStatus(isTargeted: Boolean) {
        dao.updateAllTargetStatus(isTargeted)
    }

    suspend fun deleteTargetApp(packageName: String) {
        dao.deleteTargetApp(packageName)
    }

    // ─── UPFRONT BLOCKED APPS METHODS ───
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>> = dao.getAllBlockedApps()

    fun getBlockedApps(): Flow<List<BlockedAppEntity>> = dao.getBlockedApps()

    suspend fun getBlockedPackageNamesList(): List<String> = dao.getBlockedPackageNamesList()

    suspend fun saveBlockedApp(app: BlockedAppEntity) {
        dao.insertBlockedApp(app)
    }

    suspend fun saveBlockedApps(apps: List<BlockedAppEntity>) {
        dao.insertBlockedApps(apps)
    }

    suspend fun updateAppBlockedStatus(packageName: String, isBlocked: Boolean) {
        dao.updateBlockedStatus(packageName, isBlocked)
    }

    suspend fun updateAllBlockedStatus(isBlocked: Boolean) {
        dao.updateAllBlockedStatus(isBlocked)
    }

    suspend fun deleteBlockedApp(packageName: String) {
        dao.deleteBlockedApp(packageName)
    }
}
