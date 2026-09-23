package com.example.turnaway.service

import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.data.entity.ScheduleConfigEntity
import com.example.turnaway.data.entity.SessionLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EngineState {
    MONITORING, SOFT_LANDING_TRANSITION, LOCKED_OUT
}

data class EngineStatusData(
    val state: EngineState = EngineState.MONITORING,
    val timeRemainingMs: Long = 0L,
    val currentSaturation: Float = 1.0f,
    val currentTouchDelayMs: Long = 0L,
    val currentVolumePercent: Float = 1.0f,
    val isNetworkThrottled: Boolean = false,
    val activeProfile: RestrictionProfileEntity = RestrictionProfileEntity(profileName = "Standard Soft-Landing")
)

object EngineBridge {
    private val _engineStatus = MutableStateFlow(EngineStatusData())
    val engineStatus: StateFlow<EngineStatusData> = _engineStatus.asStateFlow()

    private val _manualTriggerEvent = MutableStateFlow<Long?>(null)
    val manualTriggerEvent: StateFlow<Long?> = _manualTriggerEvent.asStateFlow()

    private val _abortEvent = MutableStateFlow(false)
    val abortEvent: StateFlow<Boolean> = _abortEvent.asStateFlow()

    fun updateStatus(status: EngineStatusData) {
        _engineStatus.value = status
    }

    fun triggerManualSoftLanding() {
        _manualTriggerEvent.value = System.currentTimeMillis()
        _abortEvent.value = false
    }

    fun abortTransition() {
        _abortEvent.value = true
    }

    fun resetAbort() {
        _abortEvent.value = false
    }
}
