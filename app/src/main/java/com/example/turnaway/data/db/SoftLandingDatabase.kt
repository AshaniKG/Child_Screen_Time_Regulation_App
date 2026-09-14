package com.example.turnaway.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.turnaway.data.dao.SoftLandingDao
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.data.entity.ScheduleConfigEntity
import com.example.turnaway.data.entity.SessionLogEntity

@Database(
    entities = [
        RestrictionProfileEntity::class,
        ScheduleConfigEntity::class,
        SessionLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SoftLandingDatabase : RoomDatabase() {
    abstract fun softLandingDao(): SoftLandingDao

    companion object {
        @Volatile
        private var INSTANCE: SoftLandingDatabase? = null

        fun getDatabase(context: Context): SoftLandingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SoftLandingDatabase::class.java,
                    "soft_landing_db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
