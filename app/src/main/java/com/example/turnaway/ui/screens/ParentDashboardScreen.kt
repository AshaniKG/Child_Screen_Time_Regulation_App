package com.example.turnaway.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
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
import com.example.turnaway.ui.components.ProfileConfigurationCard
import com.example.turnaway.ui.components.SchedulerCard
import com.example.turnaway.ui.components.SetupGuideCard
import com.example.turnaway.ui.components.TargetAppsCard
import com.example.turnaway.ui.theme.SuccessColor
import com.example.turnaway.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: DashboardViewModel,
    onRequestBiometricAuth: () -> Unit,
    onRequestScreenCaptureConsent: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
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
                Spacer(modifier = Modifier.height(4.dp))

                when (selectedTab) {
                    // ─── HOME TAB ───
                    0 -> {
                        // Engine status hero card
                        EngineStatusCard(
                            engineState = uiState.currentEngineState,
                            timeRemainingMs = uiState.timeRemainingInPhaseMs,
                            currentSaturation = uiState.currentSaturation,
                            currentBlurRadius = uiState.currentBlurRadius,
                            currentTouchDelayMs = uiState.currentTouchDelayMs,
                            currentVolumePercent = uiState.currentVolumePercent,
                            onTriggerManualLanding = { viewModel.triggerImmediateSoftLanding() },
                            onEmergencyAbort = { viewModel.abortCurrentTransition() }
                        )

                        // Setup guide (only shows if permissions are missing)
                        SetupGuideCard(
                            hasOverlayPermission = uiState.hasOverlayPermission,
                            hasAccessibilityPermission = uiState.hasAccessibilityPermission,
                            hasScreenCapturePermission = uiState.hasScreenCapturePermission,
                            onRequestScreenCapture = onRequestScreenCaptureConsent
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
                            onProfileUpdated = { updated -> viewModel.saveProfile(updated) }
                        )

                        // Selective regulated applications
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

                        // Permissions status
                        PermissionsCard(
                            hasOverlay = uiState.hasOverlayPermission,
                            hasAccessibility = uiState.hasAccessibilityPermission,
                            hasScreenCapture = uiState.hasScreenCapturePermission,
                            onRequestScreenCapture = onRequestScreenCaptureConsent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ─── Permissions Card (Settings tab) ───

@Composable
private fun PermissionsCard(
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    hasScreenCapture: Boolean,
    onRequestScreenCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "App Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Overlay permission
            PermissionRow(
                title = "Screen Overlay",
                description = if (hasOverlay) "Granted" else "Allows the wind-down effect over other apps",
                isGranted = hasOverlay,
                onAction = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // Accessibility permission
            PermissionRow(
                title = "Wind-Down Service",
                description = if (hasAccessibility) "Granted" else "Enables gradual touch slowdown and app monitoring",
                isGranted = hasAccessibility,
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // Screen Capture permission
            PermissionRow(
                title = "Screen Frame Capture",
                description = if (hasScreenCapture) "Granted" else "Enables hardware frame capture for smooth flow lag pacing",
                isGranted = hasScreenCapture,
                onAction = onRequestScreenCapture
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Outlined.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) SuccessColor else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isGranted) {
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("Set up", style = MaterialTheme.typography.labelMedium)
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
