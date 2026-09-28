package com.example.turnaway.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "restriction_profiles")
data class RestrictionProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val profileName: String,
    val transitionDurationMinutes: Int = 5,
    val curveType: String = "LINEAR", // LINEAR, EXPONENTIAL, SIGMOIDAL
    val enableColorDesaturation: Boolean = true,  // System-wide native Grayscale (enabled by default)
    val enableOverlayGraying: Boolean = true,     // Visual screen overlay veil (enabled by default)
    val overlayColorHex: String = "#808080",       // Customizable overlay color (default: Neutral Gray)
    val overlayMaxAlpha: Float = 0.70f,            // Customizable overlay visibility/max opacity (0.10 to 1.00)
    val enableTouchDelay: Boolean = true,          // Gentle Touch Slowdown
    val maxTouchDelayMs: Long = 400L,              // 400ms maximum touch delay
    val enableAudioFade: Boolean = true,           // Audio Volume Reduction
    val enableNetworkThrottling: Boolean = true    // Network Throttling
)
