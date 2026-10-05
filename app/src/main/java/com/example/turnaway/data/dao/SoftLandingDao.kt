package com.example.turnaway.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.turnaway.data.entity.BlockedAppEntity
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.data.entity.ScheduleConfigEntity
import com.example.turnaway.data.entity.SessionLogEntity
import com.example.turnaway.data.entity.TargetAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SoftLandingDao {
    @Query("SELECT * FROM restriction_profiles WHERE id = :id")
    fun getProfileById(id: String): Flow<RestrictionProfileEntity?>

    @Query("SELECT * FROM restriction_profiles")
    fun getAllProfiles(): Flow<List<RestrictionProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: RestrictionProfileEntity)

    @Query("SELECT * FROM schedule_configs WHERE isActive = 1")
    fun getActiveSchedules(): Flow<List<ScheduleConfigEntity>>

    @Query("SELECT * FROM schedule_configs")
    fun getAllSchedules(): Flow<List<ScheduleConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionLog(log: SessionLogEntity)

    @Query("SELECT * FROM session_logs ORDER BY timestampStart DESC LIMIT 30")
    fun getRecentSessionLogs(): Flow<List<SessionLogEntity>>

    @Query("SELECT * FROM target_apps ORDER BY appName ASC")
    fun getAllTargetApps(): Flow<List<TargetAppEntity>>

    @Query("SELECT * FROM target_apps WHERE isTargeted = 1")
    fun getTargetedApps(): Flow<List<TargetAppEntity>>

    @Query("SELECT packageName FROM target_apps WHERE isTargeted = 1")
    suspend fun getTargetedPackageNamesList(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargetApp(app: TargetAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargetApps(apps: List<TargetAppEntity>)

    @Query("UPDATE target_apps SET isTargeted = :isTargeted WHERE packageName = :packageName")
    suspend fun updateTargetStatus(packageName: String, isTargeted: Boolean)

    @Query("UPDATE target_apps SET isTargeted = :isTargeted")
    suspend fun updateAllTargetStatus(isTargeted: Boolean)

    @Query("UPDATE target_apps SET isBlocked = :isBlocked WHERE packageName = :packageName")
    suspend fun updateTargetAppBlockedStatus(packageName: String, isBlocked: Boolean)

    @Query("UPDATE target_apps SET isBlocked = :isBlocked")
    suspend fun updateAllTargetAppsBlockedStatus(isBlocked: Boolean)

    @Query("DELETE FROM target_apps WHERE packageName = :packageName")
    suspend fun deleteTargetApp(packageName: String)

    // ─── UPFRONT BLOCKED APPS QUERIES ───
    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE isBlocked = 1")
    fun getBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT packageName FROM blocked_apps WHERE isBlocked = 1")
    suspend fun getBlockedPackageNamesList(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApp(app: BlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApps(apps: List<BlockedAppEntity>)

    @Query("UPDATE blocked_apps SET isBlocked = :isBlocked WHERE packageName = :packageName")
    suspend fun updateBlockedAppStatus(packageName: String, isBlocked: Boolean)

    @Query("UPDATE blocked_apps SET isBlocked = :isBlocked")
    suspend fun updateAllBlockedAppsStatus(isBlocked: Boolean)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteBlockedApp(packageName: String)
}
