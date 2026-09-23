package com.example.turnaway.ui.state

import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.data.entity.ScheduleConfigEntity
import com.example.turnaway.data.entity.SessionLogEntity
import com.example.turnaway.data.entity.TargetAppEntity
import com.example.turnaway.service.EngineState

data class DashboardUiState(
    val isAuthenticated: Boolean = false,
    val activeProfile: RestrictionProfileEntity = RestrictionProfileEntity(profileName = "Standard"),
    val activeSchedules: List<ScheduleConfigEntity> = emptyList(),
    val targetApps: List<TargetAppEntity> = emptyList(),
    val currentEngineState: EngineState = EngineState.MONITORING,
    val timeRemainingInPhaseMs: Long = 0L,
    val currentSaturation: Float = 1.0f,
    val currentTouchDelayMs: Long = 0L,
    val currentVolumePercent: Float = 1.0f,
    val isNetworkThrottled: Boolean = false,
    val recentSessionLogs: List<SessionLogEntity> = emptyList(),
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasWriteSecureSettingsPermission: Boolean = false,
    val isGrayscaleActive: Boolean = false
)
