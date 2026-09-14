package com.example.turnaway.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.data.entity.ScheduleConfigEntity
import com.example.turnaway.data.repository.SoftLandingRepository
import com.example.turnaway.service.EngineBridge
import com.example.turnaway.service.EngineStatusData
import com.example.turnaway.ui.state.DashboardUiState
import com.example.turnaway.util.AppLogger
import com.example.turnaway.util.LogEntry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: SoftLandingRepository) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow(false)
    private val _hasAdbPermission = MutableStateFlow(false)
    private val _hasOverlayPermission = MutableStateFlow(false)
    private val _hasAccessibilityPermission = MutableStateFlow(false)
    private val _isTestGrayscaleActive = MutableStateFlow(false)

    private val permissionsFlow = combine(
        _hasAdbPermission,
        _hasOverlayPermission,
        _hasAccessibilityPermission,
        _isTestGrayscaleActive
    ) { adb, overlay, acc, testGray ->
        PermissionsTuple(adb, overlay, acc, testGray)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _isAuthenticated,
        combine(
            repository.getAllProfiles().map { it.firstOrNull() ?: RestrictionProfileEntity(profileName = "Standard Soft-Landing") },
            repository.getAllSchedules(),
            repository.getRecentSessionLogs()
        ) { profile, schedules, logs -> DataTuple(profile, schedules, logs) },
        combine(EngineBridge.engineStatus, AppLogger.logs, permissionsFlow) { status, logs, perms -> EngineLogsPermissionsTuple(status, logs, perms) }
    ) { auth, dataTuple, engineLogsPerms ->
        DashboardUiState(
            isAuthenticated = auth,
            activeProfile = dataTuple.profile,
            activeSchedules = dataTuple.schedules,
            currentEngineState = engineLogsPerms.status.state,
            timeRemainingInPhaseMs = engineLogsPerms.status.timeRemainingMs,
            currentSaturation = engineLogsPerms.status.currentSaturation,
            currentFps = engineLogsPerms.status.currentFps,
            currentTouchDelayMs = engineLogsPerms.status.currentTouchDelayMs,
            currentVolumePercent = engineLogsPerms.status.currentVolumePercent,
            recentSessionLogs = dataTuple.sessionLogs,
            logEntries = engineLogsPerms.logs,
            hasAdbPermission = engineLogsPerms.permissions.hasAdb,
            hasOverlayPermission = engineLogsPerms.permissions.hasOverlay,
            hasAccessibilityPermission = engineLogsPerms.permissions.hasAccessibility,
            isTestGrayscaleActive = engineLogsPerms.permissions.isTestGrayscaleActive
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun checkPermissions(context: Context) {
        val hasOverlay = true // Settings.canDrawOverlays(context) // DISABLED FOR NOW
        val hasAdb = context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        val hasAccessibility = isAccessibilityServiceEnabled(context)

        _hasOverlayPermission.value = hasOverlay
        _hasAdbPermission.value = hasAdb
        _hasAccessibilityPermission.value = hasAccessibility
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedService = "${context.packageName}/com.example.turnaway.service.SoftLandingAccessibilityService"
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabledServices.contains(expectedService)
    }

    fun unlockDashboard() {
        _isAuthenticated.value = true
        AppLogger.i("Security", "Parent unlocked dashboard via Biometrics/Credential gate")
    }

    fun lockDashboard() {
        _isAuthenticated.value = false
        AppLogger.i("Security", "Dashboard locked by parent")
    }

    fun saveProfile(updatedProfile: RestrictionProfileEntity) {
        viewModelScope.launch {
            repository.saveProfile(updatedProfile)
            AppLogger.i("Configuration", "Saved restriction profile: ${updatedProfile.profileName}")
        }
    }

    fun addSchedule(schedule: ScheduleConfigEntity) {
        viewModelScope.launch {
            repository.saveSchedule(schedule)
            AppLogger.i("Scheduler", "Saved schedule time block: ${schedule.startTimeOfDay}")
        }
    }

    fun triggerImmediateSoftLanding() {
        EngineBridge.triggerManualSoftLanding()
        AppLogger.i("Engine", "Parent manually triggered Soft-Landing transition window")
    }

    fun abortCurrentTransition() {
        EngineBridge.abortTransition()
        AppLogger.w("Engine", "Parent aborted current Soft-Landing transition")
    }

    fun clearLogs() {
        AppLogger.clear()
    }

    fun toggleTestGrayscale(context: Context) {
        val newState = !_isTestGrayscaleActive.value
        _isTestGrayscaleActive.value = newState
        val hasAdb = _hasAdbPermission.value

        if (hasAdb) {
            try {
                Settings.Secure.putInt(context.contentResolver, "accessibility_display_daltonizer_enabled", if (newState) 1 else 0)
                Settings.Secure.putInt(context.contentResolver, "accessibility_display_daltonizer", 0)
                AppLogger.i("TestGrayscale", "Toggled ADB system grayscale test: $newState")
            } catch (e: Exception) {
                AppLogger.e("TestGrayscale", "Error toggling native ADB grayscale test", e)
            }
        } else {
            AppLogger.w("TestGrayscale", "Toggled on-device canvas grayscale filter test: $newState")
        }
    }
}

private data class PermissionsTuple(
    val hasAdb: Boolean,
    val hasOverlay: Boolean,
    val hasAccessibility: Boolean,
    val isTestGrayscaleActive: Boolean
)

private data class DataTuple(
    val profile: RestrictionProfileEntity,
    val schedules: List<ScheduleConfigEntity>,
    val sessionLogs: List<com.example.turnaway.data.entity.SessionLogEntity>
)

private data class EngineLogsPermissionsTuple(
    val status: EngineStatusData,
    val logs: List<LogEntry>,
    val permissions: PermissionsTuple
)
