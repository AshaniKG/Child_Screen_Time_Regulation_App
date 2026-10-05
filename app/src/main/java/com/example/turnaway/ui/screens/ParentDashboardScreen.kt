package com.example.turnaway.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.turnaway.engine.SessionState
import com.example.turnaway.engine.ThrottleSessionManager
import com.example.turnaway.ui.components.BiometricLockOverlay
import com.example.turnaway.ui.components.GrayscalePermissionDialog
import com.example.turnaway.ui.components.TurnAwayBottomNavigation
import com.example.turnaway.ui.viewmodel.DashboardViewModel

@Composable
fun ParentDashboardScreen(
    viewModel: DashboardViewModel,
    onRequestBiometricAuth: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showRulesSubScreen by remember { mutableStateOf(false) }
    var appSelectionMode by remember { mutableStateOf(AppSelectionMode.RESTRICTED) }
    var showGrayscaleDialog by remember { mutableStateOf(false) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (uiState.currentSessionState != SessionState.IDLE) {
                ThrottleSessionManager.getInstance(context).startSession()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }

    if (showGrayscaleDialog) {
        GrayscalePermissionDialog(
            onDismissRequest = { showGrayscaleDialog = false },
            onPermissionGranted = {
                viewModel.checkPermissions(context)
            }
        )
    }

    if (!uiState.isAuthenticated) {
        BiometricLockOverlay(onAuthenticate = onRequestBiometricAuth)
    } else {
        if (showRulesSubScreen) {
            BackHandler {
                showRulesSubScreen = false
            }
            RegulatedAppsScreen(
                targetApps = uiState.targetApps,
                mode = appSelectionMode,
                onToggleAppTarget = { pkg, isTargeted ->
                    viewModel.toggleAppTarget(pkg, isTargeted)
                },
                onSelectAllTargets = { selectAll ->
                    viewModel.setAllAppsTargeted(selectAll)
                },
                onToggleAppBlocked = { pkg, isBlocked ->
                    viewModel.toggleAppBlocked(pkg, isBlocked)
                },
                onSelectAllBlocked = { selectAll ->
                    viewModel.setAllAppsBlocked(selectAll)
                },
                onBack = {
                    showRulesSubScreen = false
                }
            )
        } else {
            Scaffold(
                bottomBar = {
                    TurnAwayBottomNavigation(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding())
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tabTransition"
                    ) { tab ->
                        when (tab) {
                            0 -> {
                                SessionDashboardScreen(
                                    sessionState = uiState.currentSessionState,
                                    engineState = uiState.currentEngineState,
                                    timeRemainingMs = uiState.timeRemainingInPhaseMs,
                                    normalTimeRemainingMs = uiState.normalTimeRemainingMs,
                                    transitionTimeRemainingMs = uiState.transitionTimeRemainingMs,
                                    totalUsageMinutes = uiState.totalUsageMinutes,
                                    transitionMinutes = uiState.transitionMinutes,
                                    targetApps = uiState.targetApps,
                                    onStartSession = { totalUsage, transition ->
                                        if (uiState.activeProfile.enableNetworkThrottling) {
                                            val vpnIntent = ThrottleSessionManager.getInstance(context).checkVpnPermissionNeeded()
                                            if (vpnIntent != null) {
                                                vpnLauncher.launch(vpnIntent)
                                            }
                                        }
                                        viewModel.startSession(context, totalUsage, transition)
                                        if (!uiState.hasWriteSecureSettingsPermission && uiState.activeProfile.enableColorDesaturation) {
                                            showGrayscaleDialog = true
                                        }
                                    },
                                    onStopSession = {
                                        viewModel.stopSession(context)
                                    },
                                    onLockDashboard = {
                                        viewModel.lockDashboard()
                                    },
                                    onNavigateToBlockedApps = {
                                        appSelectionMode = AppSelectionMode.FULLY_BLOCKED
                                        showRulesSubScreen = true
                                    },
                                    onNavigateToRestrictedApps = {
                                        appSelectionMode = AppSelectionMode.RESTRICTED
                                        showRulesSubScreen = true
                                    }
                                )
                            }
                            1 -> {
                                WindDownSettingsScreen(
                                    profile = uiState.activeProfile,
                                    regulatedAppsCount = uiState.targetApps.count { it.isTargeted },
                                    blockedAppsCount = uiState.targetApps.count { it.isBlocked },
                                    onProfileUpdated = { updated ->
                                        viewModel.saveProfile(updated)
                                        if (updated.enableNetworkThrottling) {
                                            val vpnIntent = ThrottleSessionManager.getInstance(context).checkVpnPermissionNeeded()
                                            if (vpnIntent != null) {
                                                vpnLauncher.launch(vpnIntent)
                                            }
                                        }
                                    },
                                    onLockDashboard = {
                                        viewModel.lockDashboard()
                                    },
                                    onNavigateToBlockedApps = {
                                        appSelectionMode = AppSelectionMode.FULLY_BLOCKED
                                        showRulesSubScreen = true
                                    },
                                    onNavigateToRestrictedApps = {
                                        appSelectionMode = AppSelectionMode.RESTRICTED
                                        showRulesSubScreen = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
