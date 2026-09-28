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
import com.example.turnaway.service.EngineState
import com.example.turnaway.service.EngineStatusData
import com.example.turnaway.ui.state.DashboardUiState
import com.example.turnaway.util.AppLogger
import com.example.turnaway.util.GrayscaleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: SoftLandingRepository) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow(false)
    private val _hasOverlayPermission = MutableStateFlow(false)
    private val _hasAccessibilityPermission = MutableStateFlow(false)
    private val _hasWriteSecureSettingsPermission = MutableStateFlow(false)
    private val _isGrayscaleActive = MutableStateFlow(false)

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
        _hasWriteSecureSettingsPermission,
        _isGrayscaleActive
    ) { overlay, acc, secureSettings, grayscaleActive ->
        PermissionsTuple(overlay, acc, secureSettings, grayscaleActive)
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
            currentSessionState = status.sessionState,
            totalUsageMinutes = status.totalUsageMinutes,
            transitionMinutes = status.transitionMinutes,
            timeRemainingInPhaseMs = status.timeRemainingMs,
            normalTimeRemainingMs = status.normalTimeRemainingMs,
            transitionTimeRemainingMs = status.transitionTimeRemainingMs,
            currentSaturation = status.currentSaturation,
            currentTouchDelayMs = status.currentTouchDelayMs,
            currentVolumePercent = status.currentVolumePercent,
            isNetworkThrottled = status.isNetworkThrottled,
            recentSessionLogs = dataTuple.sessionLogs,
            hasOverlayPermission = perms.hasOverlay,
            hasAccessibilityPermission = perms.hasAccessibility,
            hasWriteSecureSettingsPermission = perms.hasWriteSecureSettings,
            isGrayscaleActive = perms.isGrayscaleActive
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun checkPermissions(context: Context) {
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasAccessibility = isAccessibilityServiceEnabled(context)

        _hasOverlayPermission.value = hasOverlay
        _hasAccessibilityPermission.value = hasAccessibility
        _hasWriteSecureSettingsPermission.value = GrayscaleManager.isPermissionGranted(context)
        _isGrayscaleActive.value = GrayscaleManager.isGrayscaleActive(context)

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

                val isLegacyAllTargeted = currentApps.isNotEmpty() && currentApps.all { it.isTargeted }

                val targetEntities = discoveredMap.map { (pkg, name) ->
                    val isTargeted = if (isLegacyAllTargeted) false else (currentMap[pkg]?.isTargeted ?: false)
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

    fun setCustomTransitionDuration(durationMinutes: Int) {
        val current = uiState.value.activeProfile
        val updated = current.copy(transitionDurationMinutes = durationMinutes.coerceIn(1, 30))
        saveProfile(updated)
        AppLogger.i("Configuration", "Updated transition duration to ${updated.transitionDurationMinutes} minutes")
    }

    fun startSession(context: Context, totalUsageMinutes: Int, transitionMinutes: Int) {
        val total = totalUsageMinutes.coerceIn(1, 30)
        val trans = transitionMinutes.coerceIn(1, total).coerceAtMost(total)

        val currentProfile = uiState.value.activeProfile
        saveProfile(currentProfile.copy(transitionDurationMinutes = trans))

        val targets = uiState.value.targetApps.filter { it.isTargeted }.map { it.packageName }.toSet()
        if (targets.isNotEmpty()) {
            com.example.turnaway.engine.ThrottleSessionManager.getInstance(context).setTargetPackageNames(targets)
        }

        com.example.turnaway.engine.SessionStateManager.startSession(context, total, trans)
        EngineBridge.triggerStartSession(total, trans)
        AppLogger.i("Engine", "Parent started two-stage session with totalUsage=${total}m, transition=${trans}m")
    }

    fun stopSession(context: Context) {
        abortCurrentTransition(context)
    }

    fun triggerImmediateSoftLanding(context: Context) {
        startSession(context, 30, 5)
    }

    fun triggerImmediateSoftLanding() {
        EngineBridge.triggerStartSession(30, 5)
    }

    fun abortCurrentTransition(context: Context) {
        com.example.turnaway.engine.SessionStateManager.stopSession(context)
        EngineBridge.abortTransition()
        if (GrayscaleManager.isPermissionGranted(context)) {
            GrayscaleManager.setSaturationLevel(context, 100)
            GrayscaleManager.setGrayscaleEnabled(context, false)
        }

        EngineBridge.updateStatus(
            EngineStatusData(
                state = EngineState.MONITORING,
                sessionState = com.example.turnaway.engine.SessionState.IDLE,
                timeRemainingMs = 0L,
                normalTimeRemainingMs = 0L,
                transitionTimeRemainingMs = 0L,
                currentSaturation = 1.0f,
                currentTouchDelayMs = 0L,
                currentVolumePercent = 1.0f,
                activeProfile = uiState.value.activeProfile
            )
        )
        _isGrayscaleActive.value = false
        AppLogger.w("Engine", "Parent stopped session. Restored full settings.")
    }

    fun abortCurrentTransition() {
        EngineBridge.abortTransition()
    }

    fun toggleGrayscale(context: Context, enable: Boolean, onPermissionRequired: () -> Unit) {
        if (enable) {
            if (!GrayscaleManager.isPermissionGranted(context)) {
                onPermissionRequired()
                return
            }
            GrayscaleManager.setGrayscaleEnabled(context, true)
            checkPermissions(context)
        } else {
            GrayscaleManager.setGrayscaleEnabled(context, false)
            checkPermissions(context)
        }
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
    val hasWriteSecureSettings: Boolean,
    val isGrayscaleActive: Boolean
)
