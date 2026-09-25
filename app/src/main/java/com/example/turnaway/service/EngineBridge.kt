package com.example.turnaway.service

import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.engine.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EngineState {
    MONITORING, SOFT_LANDING_TRANSITION, LOCKED_OUT
}

data class SessionStartParams(
    val totalUsageMinutes: Int = 30,
    val transitionMinutes: Int = 5,
    val timestamp: Long = System.currentTimeMillis()
)

data class EngineStatusData(
    val state: EngineState = EngineState.MONITORING,
    val sessionState: SessionState = SessionState.IDLE,
    val totalUsageMinutes: Int = 30,
    val transitionMinutes: Int = 5,
    val timeRemainingMs: Long = 0L,
    val normalTimeRemainingMs: Long = 0L,
    val transitionTimeRemainingMs: Long = 0L,
    val currentSaturation: Float = 1.0f,
    val currentTouchDelayMs: Long = 0L,
    val currentVolumePercent: Float = 1.0f,
    val isNetworkThrottled: Boolean = false,
    val activeProfile: RestrictionProfileEntity = RestrictionProfileEntity(profileName = "Standard Soft-Landing")
)

object EngineBridge {
    private val _engineStatus = MutableStateFlow(EngineStatusData())
    val engineStatus: StateFlow<EngineStatusData> = _engineStatus.asStateFlow()

    private val _manualTriggerEvent = MutableStateFlow<SessionStartParams?>(null)
    val manualTriggerEvent: StateFlow<SessionStartParams?> = _manualTriggerEvent.asStateFlow()

    private val _abortEvent = MutableStateFlow(false)
    val abortEvent: StateFlow<Boolean> = _abortEvent.asStateFlow()

    fun updateStatus(status: EngineStatusData) {
        _engineStatus.value = status
    }

    fun triggerStartSession(totalUsageMinutes: Int = 30, transitionMinutes: Int = 5) {
        _manualTriggerEvent.value = SessionStartParams(
            totalUsageMinutes = totalUsageMinutes,
            transitionMinutes = transitionMinutes,
            timestamp = System.currentTimeMillis()
        )
        _abortEvent.value = false
    }

    fun triggerManualSoftLanding() {
        triggerStartSession(30, 5)
    }

    fun abortTransition() {
        _abortEvent.value = true
    }

    fun resetAbort() {
        _abortEvent.value = false
    }
}
