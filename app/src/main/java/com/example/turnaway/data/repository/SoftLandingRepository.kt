package com.example.turnaway.data.repository

import com.example.turnaway.data.dao.SoftLandingDao
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.data.entity.ScheduleConfigEntity
import com.example.turnaway.data.entity.SessionLogEntity
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
}
