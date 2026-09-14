package com.example.turnaway.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.turnaway.ui.components.AdbGrayscaleCard
import com.example.turnaway.ui.components.DiagnosticsLogCard
import com.example.turnaway.ui.components.DisengagementAnalyticsCard
import com.example.turnaway.ui.components.EngineStatusCard
import com.example.turnaway.ui.components.ProfileConfigurationCard
import com.example.turnaway.ui.components.SchedulerCard
import com.example.turnaway.ui.theme.SuccessColor
import com.example.turnaway.ui.theme.ErrorColor
import com.example.turnaway.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: DashboardViewModel,
    onRequestBiometricAuth: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Local tab state for instantaneous 0ms tab switching
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Soft-Landing Engine", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.lockDashboard() }) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock Portal", tint = ErrorColor)
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
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Dashboard") },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Settings & Rules") },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("Diagnostics & Logs") },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.logEntries.any { it.level == com.example.turnaway.util.LogLevel.ERROR }) {
                                        Badge(containerColor = ErrorColor)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                            }
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

                // Permission Warning Card if required system permissions are missing
                if (!uiState.hasOverlayPermission || !uiState.hasAccessibilityPermission) {
                    PermissionWarningCard(
                        hasOverlay = uiState.hasOverlayPermission,
                        hasAccessibility = uiState.hasAccessibilityPermission
                    )
                }

                // Simplified 3-Tab Content Switching
                when (selectedTab) {
                    0 -> { // Dashboard Tab
                        EngineStatusCard(
                            engineState = uiState.currentEngineState,
                            timeRemainingMs = uiState.timeRemainingInPhaseMs,
                            currentSaturation = uiState.currentSaturation,
                            currentFps = uiState.currentFps,
                            currentTouchDelayMs = uiState.currentTouchDelayMs,
                            currentVolumePercent = uiState.currentVolumePercent,
                            onTriggerManualLanding = { viewModel.triggerImmediateSoftLanding() },
                            onEmergencyAbort = { viewModel.abortCurrentTransition() }
                        )

                        DisengagementAnalyticsCard(sessionLogs = uiState.recentSessionLogs)
                    }

                    1 -> { // Settings & Rules Tab
                        ProfileConfigurationCard(
                            profile = uiState.activeProfile,
                            onProfileUpdated = { updated -> viewModel.saveProfile(updated) }
                        )

                        SchedulerCard(
                            schedules = uiState.activeSchedules,
                            onAddSchedule = { newSched -> viewModel.addSchedule(newSched) }
                        )

                        SystemPermissionsCard(
                            hasOverlay = uiState.hasOverlayPermission,
                            hasAccessibility = uiState.hasAccessibilityPermission
                        )
                    }

                    2 -> { // Diagnostics & Logs Tab
                        AdbGrayscaleCard(
                            hasAdbPermission = uiState.hasAdbPermission,
                            isTestGrayscaleActive = uiState.isTestGrayscaleActive,
                            onToggleTestGrayscale = { viewModel.toggleTestGrayscale(context) }
                        )

                        DiagnosticsLogCard(
                            logEntries = uiState.logEntries,
                            onClearLogs = { viewModel.clearLogs() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SystemPermissionsCard(
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("System Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))

            /* ENABLE_OVERLAY_FEATURES_LATER
            // Use over other apps (Overlay)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasOverlay) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (hasOverlay) SuccessColor else ErrorColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use over other apps", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        if (hasOverlay) "Permission Granted" else "Required for UI filters",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (hasOverlay) "Manage" else "Turn On", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))
            */

            // Accessibility Service
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasAccessibility) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (hasAccessibility) SuccessColor else ErrorColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Accessibility Service", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        if (hasAccessibility) "Permission Granted" else "Required for touch & keys",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (hasAccessibility) "Manage" else "Turn On", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun PermissionWarningCard(
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Grant Required Permissions Below",
                    fontWeight = FontWeight.Bold,
                    color = ErrorColor,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!hasOverlay) {
                Text(
                    text = "• Display over other apps: Required to apply screen grayscale & frame-rate filters.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!hasAccessibility) {
                Text(
                    text = "• Soft-Landing Accessibility Service: Required to intercept touch gestures & apply touch lag.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hasAccessibility) {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Turn on Accessibility", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }

                if (!hasOverlay) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Turn on 'Use over other apps'", fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Soft-Landing Engine",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Biometric authentication or device PIN required to unlock parental portal.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onAuthenticate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Unlock Portal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% Offline Local Security",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
