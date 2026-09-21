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
    val enableColorDesaturation: Boolean = true,  // System-wide native Grayscale
    val maxBlurRadius: Int = 0,                    // Disabled (0px)
    val enableFrameThrottling: Boolean = false,    // Disabled
    val enableTouchDelay: Boolean = false,         // Disabled
    val minFpsFloor: Int = 60,                     // 60 FPS normal
    val maxTouchDelayMs: Long = 0L,                // 0ms delay
    val enableAudioFade: Boolean = true            // Audio Volume Reduction
)
