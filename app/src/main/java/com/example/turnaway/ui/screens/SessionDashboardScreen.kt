package com.example.turnaway.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turnaway.data.entity.TargetAppEntity
import com.example.turnaway.engine.SessionState
import com.example.turnaway.service.EngineState
import com.example.turnaway.ui.components.AppIconImage
import com.example.turnaway.ui.components.TurnAwayBrandLogo
import com.example.turnaway.ui.theme.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun SessionDashboardScreen(
    sessionState: SessionState,
    engineState: EngineState,
    timeRemainingMs: Long,
    normalTimeRemainingMs: Long,
    transitionTimeRemainingMs: Long,
    totalUsageMinutes: Int,
    transitionMinutes: Int,
    targetApps: List<TargetAppEntity>,
    onStartSession: (totalUsageMinutes: Int, transitionMinutes: Int) -> Unit,
    onStopSession: () -> Unit,
    onLockDashboard: () -> Unit,
    onNavigateToBlockedApps: () -> Unit,
    onNavigateToRestrictedApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    var allowedMinutes by remember(totalUsageMinutes) {
        mutableIntStateOf(if (totalUsageMinutes > 0) totalUsageMinutes else 30)
    }
    var transitionPeriodMinutes by remember(transitionMinutes) {
        mutableIntStateOf(if (transitionMinutes > 0) transitionMinutes else 5)
    }

    LaunchedEffect(allowedMinutes) {
        if (transitionPeriodMinutes > allowedMinutes) {
            transitionPeriodMinutes = allowedMinutes
        }
    }

    val isSessionActive = sessionState != SessionState.IDLE
    val targetedApps = remember(targetApps) { targetApps.filter { it.isTargeted } }
    val blockedApps = remember(targetApps) { targetApps.filter { it.isBlocked } }
    val targetedAppsCount = targetedApps.size
    val blockedAppsCount = blockedApps.size

    val targetedAppsPreview = remember(targetedApps) {
        if (targetedApps.isEmpty()) "Tap to select apps"
        else targetedApps.take(3).joinToString(", ") { it.appName } + if (targetedApps.size > 3) "..." else ""
    }

    val blockedAppsPreview = remember(blockedApps) {
        if (blockedApps.isEmpty()) "Tap to select apps"
        else blockedApps.take(3).joinToString(", ") { it.appName } + if (blockedApps.size > 3) "..." else ""
    }

    val totalSessionMs = (allowedMinutes * 60 * 1000L).coerceAtLeast(1L)
    val remainingLevelFraction = if (isSessionActive) {
        (timeRemainingMs.toFloat() / totalSessionMs.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TurnawayBackground)
    ) {
        // ─── TOP APP BAR ───
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, ambientColor = TurnawayPrimary.copy(alpha = 0.04f)),
            color = TurnawaySurface.copy(alpha = 0.92f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TurnAwayBrandLogo(size = 32.dp, cornerRadius = 8.dp)
                    Text(
                        text = "TurnAway",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TurnawayPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }

                // Lock icon only (no text)
                Surface(
                    onClick = onLockDashboard,
                    shape = CircleShape,
                    color = TurnawaySurfaceContainerHigh.copy(alpha = 0.7f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = TurnawaySecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ─── SCROLLABLE CONTENT ───
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ─── CENTRAL START/STOP BUTTON WITH DECREASING LEVEL RING ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                // Decreasing Level Progress Ring around the button
                Canvas(modifier = Modifier.size(164.dp)) {
                    val strokeWidth = 8.dp.toPx()
                    val arcSize = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    // Track background ring
                    drawArc(
                        color = TurnawaySurfaceVariant.copy(alpha = 0.45f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Decreasing level arc
                    val sweepAngle = 360f * remainingLevelFraction
                    if (sweepAngle > 0f) {
                        val levelColor = when {
                            sessionState == SessionState.IN_TRANSITION -> TurnawaySecondary
                            sessionState == SessionState.COMPLETED_LOCKED -> TurnawayError
                            else -> TurnawayPrimary
                        }
                        drawArc(
                            color = levelColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Central circular button
                Surface(
                    onClick = {
                        if (isSessionActive) {
                            onStopSession()
                        } else {
                            onStartSession(allowedMinutes, transitionPeriodMinutes)
                        }
                    },
                    modifier = Modifier
                        .size(132.dp)
                        .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = TurnawayPrimary.copy(alpha = 0.25f)),
                    shape = CircleShape,
                    color = if (isSessionActive) TurnawayPrimary else TurnawayPrimaryContainer
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isSessionActive) "Stop" else "Start",
                            tint = TurnawaySurfaceContainerLowest,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isSessionActive) "STOP" else "START",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TurnawaySurfaceContainerLowest,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isSessionActive) {
                                val mins = TimeUnit.MILLISECONDS.toMinutes(timeRemainingMs)
                                val secs = TimeUnit.MILLISECONDS.toSeconds(timeRemainingMs) % 60
                                String.format("%02d:%02d left", mins, secs)
                            } else {
                                "$allowedMinutes min"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TurnawayOnPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ─── SESSION TIMING CARD (Sliders Instead of Dropdowns) ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(28.dp), ambientColor = TurnawayPrimary.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = TurnawaySurfaceContainerLowest)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = TurnawayPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Session Timing",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TurnawayPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Status chip
                        Surface(
                            shape = RoundedCornerShape(9999.dp),
                            color = if (isSessionActive) TurnawaySecondaryContainer else TurnawaySurfaceContainerLow
                        ) {
                            Text(
                                text = when (sessionState) {
                                    SessionState.IDLE -> "Ready"
                                    SessionState.NORMAL_USAGE -> "Phase 1: Active"
                                    SessionState.IN_TRANSITION -> "Phase 2: Transition"
                                    SessionState.COMPLETED_LOCKED -> "Locked Out"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSessionActive) TurnawayOnSecondaryContainer else TurnawaySecondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Slider 1: Allowed Usage Time
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = TurnawaySecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Allowed Usage Time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TurnawayOnSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "$allowedMinutes minutes",
                                style = MaterialTheme.typography.labelSmall,
                                color = TurnawaySecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = TurnawaySurfaceContainerLow.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Slider(
                                    value = allowedMinutes.toFloat(),
                                    onValueChange = { allowedMinutes = it.roundToInt() },
                                    valueRange = 1f..60f,
                                    steps = 58,
                                    enabled = !isSessionActive,
                                    colors = SliderDefaults.colors(
                                        thumbColor = TurnawaySecondary,
                                        activeTrackColor = TurnawaySecondary,
                                        inactiveTrackColor = TurnawaySurfaceVariant
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("1 min", style = MaterialTheme.typography.labelSmall, color = TurnawayOutline, fontSize = 10.sp)
                                    Text("15 min", style = MaterialTheme.typography.labelSmall, color = TurnawayOutline, fontSize = 10.sp)
                                    Text("30 min", style = MaterialTheme.typography.labelSmall, color = TurnawayOutline, fontSize = 10.sp)
                                    Text("60 min", style = MaterialTheme.typography.labelSmall, color = TurnawayOutline, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Divider
                    HorizontalDivider(color = TurnawaySurfaceContainerHigh, thickness = 1.dp)

                    // Slider 2: Transition Period
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = TurnawayPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Transition Period",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TurnawayOnSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "$transitionPeriodMinutes minutes",
                                style = MaterialTheme.typography.labelSmall,
                                color = TurnawaySecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = TurnawaySurfaceContainerLow.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                val maxAllowedTransition = allowedMinutes.coerceAtMost(30)
                                Slider(
                                    value = transitionPeriodMinutes.coerceAtMost(maxAllowedTransition).toFloat(),
                                    onValueChange = { transitionPeriodMinutes = it.roundToInt() },
                                    valueRange = 1f..maxAllowedTransition.toFloat().coerceAtLeast(1f),
                                    enabled = !isSessionActive && maxAllowedTransition > 1,
                                    colors = SliderDefaults.colors(
                                        thumbColor = TurnawayPrimary,
                                        activeTrackColor = TurnawayPrimary,
                                        inactiveTrackColor = TurnawaySurfaceVariant
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("1 min", style = MaterialTheme.typography.labelSmall, color = TurnawayOutline, fontSize = 10.sp)
                                    Text("${maxAllowedTransition / 2} min", style = MaterialTheme.typography.labelSmall, color = TurnawayOutline, fontSize = 10.sp)
                                    Text("$maxAllowedTransition min", style = MaterialTheme.typography.labelSmall, color = TurnawayOutline, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Live Session Progress Display (if session active)
                    AnimatedVisibility(visible = isSessionActive) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(TurnawaySurfaceContainerLow)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val totalMs = (allowedMinutes * 60 * 1000L).coerceAtLeast(1L)
                            val elapsedMs = (totalMs - timeRemainingMs).coerceIn(0L, totalMs)
                            val progress = (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (sessionState) {
                                        SessionState.NORMAL_USAGE -> "Normal Usage Running"
                                        SessionState.IN_TRANSITION -> "Transition Period Active"
                                        SessionState.COMPLETED_LOCKED -> "Curfew Limit Enforced"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TurnawayPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}% elapsed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TurnawayOnSurfaceVariant
                                )
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (sessionState == SessionState.IN_TRANSITION) TurnawaySecondary else TurnawayPrimary,
                                trackColor = TurnawaySurfaceVariant
                            )
                        }
                    }
                }
            }

            // ─── PROTECTION SCOPE CARD (Click Categories Directly to Select Apps) ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(28.dp), ambientColor = TurnawayPrimary.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = TurnawaySurfaceContainerLowest)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = TurnawayPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Protection Scope",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TurnawayPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Item 1: Fully Blocked Apps (Clickable to open Blocked Apps selection)
                    Surface(
                        onClick = onNavigateToBlockedApps,
                        shape = RoundedCornerShape(16.dp),
                        color = TurnawaySurfaceContainerLow.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TurnawayError.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (blockedApps.isNotEmpty()) {
                                        AppIconImage(
                                            packageName = blockedApps.first().packageName,
                                            size = 28.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = null,
                                            tint = TurnawayError,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Fully Blocked Apps",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TurnawayPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (blockedAppsCount > 0) "$blockedAppsCount Apps: $blockedAppsPreview" else "No apps blocked. Tap to configure.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TurnawayOnSurfaceVariant,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(9999.dp),
                                    color = TurnawayError.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Hard Block",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TurnawayError,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = TurnawayOutline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Item 2: Restricted Apps (Clickable to open Restricted Apps selection)
                    Surface(
                        onClick = onNavigateToRestrictedApps,
                        shape = RoundedCornerShape(16.dp),
                        color = TurnawaySurfaceContainerLow.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TurnawaySecondary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (targetedApps.isNotEmpty()) {
                                        AppIconImage(
                                            packageName = targetedApps.first().packageName,
                                            size = 28.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.HourglassTop,
                                            contentDescription = null,
                                            tint = TurnawaySecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Restricted Apps",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TurnawayPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (targetedAppsCount > 0) "$targetedAppsCount Apps: $targetedAppsPreview" else "No apps selected. Tap to configure.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TurnawayOnSurfaceVariant,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(9999.dp),
                                    color = TurnawaySecondaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "Sensory Lag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TurnawayOnSecondaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = TurnawayOutline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
