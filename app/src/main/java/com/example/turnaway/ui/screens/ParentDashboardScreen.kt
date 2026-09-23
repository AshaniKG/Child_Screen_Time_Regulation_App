package com.example.turnaway.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.turnaway.ui.components.DisengagementAnalyticsCard
import com.example.turnaway.ui.components.EngineStatusCard
import com.example.turnaway.ui.components.GrayscalePermissionDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.turnaway.ui.components.ProfileConfigurationCard
import com.example.turnaway.ui.components.SchedulerCard
import com.example.turnaway.ui.components.TargetAppsCard
import com.example.turnaway.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: DashboardViewModel,
    onRequestBiometricAuth: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showGrayscaleDialog by remember { mutableStateOf(false) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            com.example.turnaway.engine.ThrottleSessionManager.getInstance(context).startSession()
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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "TurnAway",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.lockDashboard() }) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Lock",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Home") },
                        icon = {
                            Icon(
                                if (selectedTab == 0) Icons.Filled.Home else Icons.Default.Home,
                                contentDescription = null
                            )
                        }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Settings") },
                        icon = {
                            Icon(
                                if (selectedTab == 1) Icons.Filled.Settings else Icons.Default.Settings,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Warning Banner when Time Limit Exceeded
                AnimatedVisibility(
                    visible = uiState.currentEngineState == com.example.turnaway.service.EngineState.LOCKED_OUT,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "TIME LIMIT EXCEEDED",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                    Text(
                                        text = "Wind-down restrictions active",
                                        fontSize = 11.sp,
                                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.abortCurrentTransition(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color.White,
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("STOP", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                when (selectedTab) {
                    // ─── HOME TAB ───
                    0 -> {
                        // Engine status hero card
                        EngineStatusCard(
                            engineState = uiState.currentEngineState,
                            timeRemainingMs = uiState.timeRemainingInPhaseMs,
                            activeProfile = uiState.activeProfile,
                            currentTouchDelayMs = uiState.currentTouchDelayMs,
                            currentVolumePercent = uiState.currentVolumePercent,
                            isNetworkThrottled = uiState.isNetworkThrottled,
                            transitionDurationMinutes = uiState.activeProfile.transitionDurationMinutes,
                            onDurationChange = { minutes -> viewModel.setCustomTransitionDuration(minutes) },
                            onTriggerManualLanding = {
                                viewModel.triggerImmediateSoftLanding(context)
                                if (!uiState.hasWriteSecureSettingsPermission && uiState.activeProfile.enableColorDesaturation) {
                                    showGrayscaleDialog = true
                                }
                            },
                            onEmergencyAbort = { viewModel.abortCurrentTransition(context) }
                        )

                        // Session insights
                        DisengagementAnalyticsCard(
                            sessionLogs = uiState.recentSessionLogs
                        )
                    }

                    // ─── SETTINGS TAB ───
                    1 -> {
                        // Wind-down profile configuration
                        ProfileConfigurationCard(
                            profile = uiState.activeProfile,
                            onProfileUpdated = { updated ->
                                viewModel.saveProfile(updated)
                                if (updated.enableNetworkThrottling) {
                                    val vpnIntent = com.example.turnaway.engine.ThrottleSessionManager.getInstance(context).checkVpnPermissionNeeded()
                                    if (vpnIntent != null) {
                                        vpnLauncher.launch(vpnIntent)
                                    }
                                }
                            }
                        )

                        // Selective regulated applications for network throttling
                        TargetAppsCard(
                            targetApps = uiState.targetApps,
                            onToggleAppTarget = { pkg, isTargeted ->
                                viewModel.toggleAppTarget(pkg, isTargeted)
                            },
                            onSelectAll = { selectAll ->
                                viewModel.setAllAppsTargeted(selectAll)
                            }
                        )

                        // Bedtime schedule
                        SchedulerCard(
                            schedules = uiState.activeSchedules,
                            onAddSchedule = { newSched -> viewModel.addSchedule(newSched) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ─── Biometric Lock Overlay ───

@Composable
fun BiometricLockOverlay(
    onAuthenticate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "TurnAway",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Parent Portal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Verify your identity to manage wind-down settings",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onAuthenticate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Unlock",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
