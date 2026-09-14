package com.example.turnaway.ui.state

import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.data.entity.ScheduleConfigEntity
import com.example.turnaway.data.entity.SessionLogEntity
import com.example.turnaway.service.EngineState
import com.example.turnaway.util.LogEntry

data class DashboardUiState(
    val isAuthenticated: Boolean = false,
    val activeProfile: RestrictionProfileEntity = RestrictionProfileEntity(profileName = "Standard Soft-Landing"),
    val activeSchedules: List<ScheduleConfigEntity> = emptyList(),
    val currentEngineState: EngineState = EngineState.MONITORING,
    val timeRemainingInPhaseMs: Long = 0L,
    val currentSaturation: Float = 1.0f,
    val currentFps: Int = 60,
    val currentTouchDelayMs: Long = 0L,
    val currentVolumePercent: Float = 1.0f,
    val recentSessionLogs: List<SessionLogEntity> = emptyList(),
    val logEntries: List<LogEntry> = emptyList(),
    val hasAdbPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val isTestGrayscaleActive: Boolean = false
)
