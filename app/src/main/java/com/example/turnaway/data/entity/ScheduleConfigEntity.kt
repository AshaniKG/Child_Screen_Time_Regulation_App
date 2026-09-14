package com.example.turnaway.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "schedule_configs",
    foreignKeys = [
        ForeignKey(
            entity = RestrictionProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScheduleConfigEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val startTimeOfDay: String, // "HH:mm" format
    val endTimeOfDay: String,   // "HH:mm" format
    val daysOfWeekBitmask: Int,  // Bitmask for Mon-Sun (e.g., 0b0111110)
    val isActive: Boolean = true
)
