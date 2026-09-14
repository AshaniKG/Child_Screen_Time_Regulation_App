package com.example.turnaway.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "restriction_profiles")
data class RestrictionProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val profileName: String,
    val transitionDurationMinutes: Int = 2,
    val curveType: String = "LINEAR", // LINEAR, EXPONENTIAL, SIGMOIDAL
    val enableColorDesaturation: Boolean = true,
    val enableFrameThrottling: Boolean = true,
    val enableTouchDelay: Boolean = true,
    val minFpsFloor: Int = 5,
    val maxTouchDelayMs: Long = 800L,
    val enableAudioFade: Boolean = true
)
