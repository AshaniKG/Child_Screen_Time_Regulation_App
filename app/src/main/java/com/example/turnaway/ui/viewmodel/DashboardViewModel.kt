package com.example.turnaway.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.data.entity.ScheduleConfigEntity
import com.example.turnaway.data.entity.SessionLogEntity
import com.example.turnaway.data.entity.TargetAppEntity
import com.example.turnaway.data.repository.SoftLandingRepository
import com.example.turnaway.service.EngineBridge
import com.example.turnaway.service.EngineStatusData
import com.example.turnaway.service.ScreenCaptureForegroundService
import com.example.turnaway.ui.state.DashboardUiState
import com.example.turnaway.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: SoftLandingRepository) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow(false)
    private val _hasOverlayPermission = MutableStateFlow(false)
    private val _hasAccessibilityPermission = MutableStateFlow(false)
    private val _hasScreenCapturePermission = MutableStateFlow(false)

    private val dataFlow = combine(
        repository.getAllProfiles().map { it.firstOrNull() ?: RestrictionProfileEntity(profileName = "Standard") },
        repository.getAllSchedules(),
        repository.getAllTargetApps(),
        repository.getRecentSessionLogs()
    ) { profile, schedules, targetApps, logs ->
        DataTuple(profile, schedules, targetApps, logs)
    }

    private val permissionsFlow = combine(
        _hasOverlayPermission,
        _hasAccessibilityPermission,
        _hasScreenCapturePermission
    ) { overlay, acc, capture ->
        PermissionsTuple(overlay, acc, capture)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _isAuthenticated,
        dataFlow,
        EngineBridge.engineStatus,
        permissionsFlow
    ) { auth: Boolean, dataTuple: DataTuple, status: EngineStatusData, perms: PermissionsTuple ->
        DashboardUiState(
            isAuthenticated = auth,
            activeProfile = dataTuple.profile,
            activeSchedules = dataTuple.schedules,
            targetApps = dataTuple.targetApps,
            currentEngineState = status.state,
            timeRemainingInPhaseMs = status.timeRemainingMs,
            currentSaturation = status.currentSaturation,
            currentBlurRadius = status.currentBlurRadius,
            currentTouchDelayMs = status.currentTouchDelayMs,
            currentVolumePercent = status.currentVolumePercent,
            recentSessionLogs = dataTuple.sessionLogs,
            hasOverlayPermission = perms.hasOverlay,
            hasAccessibilityPermission = perms.hasAccessibility,
            hasScreenCapturePermission = perms.hasScreenCapture
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun updateScreenCapturePermission(isGranted: Boolean) {
        _hasScreenCapturePermission.value = isGranted
    }

    fun checkPermissions(context: Context) {
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasAccessibility = isAccessibilityServiceEnabled(context)

        _hasOverlayPermission.value = hasOverlay
        _hasAccessibilityPermission.value = hasAccessibility
        _hasScreenCapturePermission.value = ScreenCaptureForegroundService.isProjectionActive.value

        scanAndSyncInstalledApps(context)
    }

    fun scanAndSyncInstalledApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val currentApps = repository.getAllTargetApps().firstOrNull() ?: emptyList()
                val currentMap = currentApps.associateBy { it.packageName }

                val excludedPackages = setOf(
                    context.packageName,
                    "com.android.settings",
                    "com.android.systemui",
                    "android"
                )

                // 1. Discover all apps with Launcher activities
                val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val launcherActivities = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(launcherIntent, 0)
                }

                val discoveredMap = mutableMapOf<String, String>() // packageName -> appName

                for (resolveInfo in launcherActivities) {
                    val pkg = resolveInfo.activityInfo.packageName
                    if (!excludedPackages.contains(pkg) && !pkg.contains("launcher", ignoreCase = true)) {
                        val name = resolveInfo.loadLabel(pm).toString()
                        discoveredMap[pkg] = name
                    }
                }

                // 2. Discover user-installed apps (non-system apps) that may launch via other intents
                val installedApps = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(0)
                }

                for (appInfo in installedApps) {
                    val pkg = appInfo.packageName
                    if (!excludedPackages.contains(pkg) && !discoveredMap.containsKey(pkg)) {
                        val isUserApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                        val isUpdatedSysApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                        val hasLaunchIntent = pm.getLaunchIntentForPackage(pkg) != null

                        if ((isUserApp || isUpdatedSysApp) && hasLaunchIntent) {
                            val name = pm.getApplicationLabel(appInfo).toString()
                            discoveredMap[pkg] = name
                        }
                    }
                }

                val targetEntities = discoveredMap.map { (pkg, name) ->
                    val isTargeted = currentMap[pkg]?.isTargeted
                        ?: (pkg.contains("youtube", ignoreCase = true) ||
                            pkg.contains("video", ignoreCase = true) ||
                            pkg.contains("chrome", ignoreCase = true) ||
                            pkg.contains("game", ignoreCase = true) ||
                            pkg.contains("media", ignoreCase = true) ||
                            pkg.contains("tiktok", ignoreCase = true))
                    TargetAppEntity(packageName = pkg, appName = name, isTargeted = isTargeted)
                }.sortedBy { it.appName.lowercase() }

                if (targetEntities.isNotEmpty()) {
                    repository.saveTargetApps(targetEntities)
                    AppLogger.d("TargetApps", "Synced ${targetEntities.size} installed apps into Room database")
                }
            } catch (e: Exception) {
                AppLogger.e("TargetApps", "Error syncing installed apps", e)
            }
        }
    }

    fun toggleAppTarget(packageName: String, isTargeted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAppTargetStatus(packageName, isTargeted)
            AppLogger.i("TargetApps", "Updated target status: $packageName -> $isTargeted")
        }
    }

    fun setAllAppsTargeted(isTargeted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAllTargetStatus(isTargeted)
            AppLogger.i("TargetApps", "Updated all apps target status: $isTargeted")
        }
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
        AppLogger.i("Engine", "Parent manually triggered wind-down")
    }

    fun abortCurrentTransition() {
        EngineBridge.abortTransition()
        AppLogger.w("Engine", "Parent stopped current wind-down")
    }
}

private data class DataTuple(
    val profile: RestrictionProfileEntity,
    val schedules: List<ScheduleConfigEntity>,
    val targetApps: List<TargetAppEntity>,
    val sessionLogs: List<SessionLogEntity>
)

private data class PermissionsTuple(
    val hasOverlay: Boolean,
    val hasAccessibility: Boolean,
    val hasScreenCapture: Boolean
)
