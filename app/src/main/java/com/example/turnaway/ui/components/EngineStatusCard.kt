package com.example.turnaway.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.engine.SessionState
import com.example.turnaway.service.EngineState
import com.example.turnaway.ui.theme.ErrorColor
import com.example.turnaway.ui.theme.SuccessColor
import com.example.turnaway.ui.theme.WarningColor
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EngineStatusCard(
    sessionState: SessionState,
    engineState: EngineState,
    totalTimeRemainingMs: Long,
    normalTimeRemainingMs: Long,
    transitionTimeRemainingMs: Long,
    activeProfile: RestrictionProfileEntity,
    currentTouchDelayMs: Long,
    currentVolumePercent: Float,
    isNetworkThrottled: Boolean = false,
    initialTotalUsageMinutes: Int = 30,
    initialTransitionMinutes: Int = 5,
    onStartSession: (totalUsageMinutes: Int, transitionMinutes: Int) -> Unit,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTotalUsage by remember(initialTotalUsageMinutes) {
        mutableIntStateOf(initialTotalUsageMinutes.coerceIn(15, 120))
    }
    var selectedTransition by remember(initialTransitionMinutes) {
        mutableIntStateOf(initialTransitionMinutes.coerceIn(5, 15).coerceAtMost(selectedTotalUsage))
    }

    // Ensure transition duration never exceeds total usage time
    LaunchedEffect(selectedTotalUsage) {
        if (selectedTransition > selectedTotalUsage) {
            selectedTransition = selectedTotalUsage.coerceAtMost(15)
        }
    }

    val isSessionActive = sessionState != SessionState.IDLE

    // Active features badges
    val activeFeatures = remember(activeProfile) {
        buildList {
            if (activeProfile.enableAudioFade) add("🔊 Volume Fade")
            if (activeProfile.enableTouchDelay) add("👆 Touch Delay")
            if (activeProfile.enableNetworkThrottling) add("🌐 Network Throttling")
            if (activeProfile.enableColorDesaturation) add("🎨 System Grayscale")
            if (activeProfile.enableOverlayGraying) add("🎭 Overlay Veil (${(activeProfile.overlayMaxAlpha * 100).toInt()}%)")
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (sessionState) {
                SessionState.COMPLETED_LOCKED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                SessionState.IN_TRANSITION -> MaterialTheme.colorScheme.surfaceContainerHigh
                SessionState.NORMAL_USAGE -> MaterialTheme.colorScheme.surfaceContainerLow
                SessionState.IDLE -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusIndicator(sessionState = sessionState, engineState = engineState)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = when (sessionState) {
                            SessionState.IDLE -> "Ready to Start Session"
                            SessionState.NORMAL_USAGE -> "Phase 1: Normal Usage Active"
                            SessionState.IN_TRANSITION -> "Phase 2: Wind-Down In Progress"
                            SessionState.COMPLETED_LOCKED -> "Phase 3: Screen-Time Limit Reached!"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (sessionState == SessionState.COMPLETED_LOCKED) ErrorColor else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (sessionState) {
                            SessionState.IDLE -> "Configure total usage & wind-down duration"
                            SessionState.NORMAL_USAGE -> "Device operates with 100% normal performance"
                            SessionState.IN_TRANSITION -> "Sensory degradations active across transition duration"
                            SessionState.COMPLETED_LOCKED -> "All enabled limits enforced at maximum until stopped"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── TIME PICKERS / SLIDERS (Always visible, disabled when session active) ───
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // 1. Allowed Usage Time Selector (15m to 120m)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = if (isSessionActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Allowed Usage Time:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${selectedTotalUsage} minutes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val usagePresets = listOf(15, 30, 45, 60, 90, 120)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        usagePresets.forEach { minutes ->
                            FilterChip(
                                selected = selectedTotalUsage == minutes,
                                onClick = {
                                    if (!isSessionActive) selectedTotalUsage = minutes
                                },
                                enabled = !isSessionActive,
                                label = { Text("${minutes}m") }
                            )
                        }
                    }

                    Slider(
                        value = selectedTotalUsage.toFloat(),
                        onValueChange = { if (!isSessionActive) selectedTotalUsage = it.toInt() },
                        valueRange = 15f..120f,
                        steps = 104,
                        enabled = !isSessionActive,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Transition Duration Selector (5m to 15m, <= totalUsage)
                    val maxTransAllowed = selectedTotalUsage.coerceAtMost(15)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.HourglassTop,
                                contentDescription = null,
                                tint = if (isSessionActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Wind-Down Transition:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${selectedTransition} minutes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val transPresets = listOf(5, 8, 10, 12, 15).filter { it <= maxTransAllowed }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        transPresets.forEach { minutes ->
                            FilterChip(
                                selected = selectedTransition == minutes,
                                onClick = {
                                    if (!isSessionActive) selectedTransition = minutes
                                },
                                enabled = !isSessionActive,
                                label = { Text("${minutes}m") }
                            )
                        }
                    }

                    Slider(
                        value = selectedTransition.toFloat().coerceAtMost(maxTransAllowed.toFloat()),
                        onValueChange = { if (!isSessionActive) selectedTransition = it.toInt().coerceAtMost(maxTransAllowed) },
                        valueRange = 5f..(maxTransAllowed.toFloat().coerceAtLeast(5f)),
                        steps = (maxTransAllowed - 5).coerceAtLeast(0),
                        enabled = !isSessionActive && maxTransAllowed > 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─── PHASE 1: NORMAL USAGE DISPLAY ───
            AnimatedVisibility(
                visible = sessionState == SessionState.NORMAL_USAGE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    val totalMin = TimeUnit.MILLISECONDS.toMinutes(totalTimeRemainingMs)
                    val totalSec = TimeUnit.MILLISECONDS.toSeconds(totalTimeRemainingMs) % 60
                    val totalTimeString = String.format("%02d:%02d", totalMin, totalSec)

                    val normMin = TimeUnit.MILLISECONDS.toMinutes(normalTimeRemainingMs)
                    val normSec = TimeUnit.MILLISECONDS.toSeconds(normalTimeRemainingMs) % 60
                    val normTimeString = String.format("%02d:%02d", normMin, normSec)

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Session Remaining:",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = totalTimeString,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Wind-down starts in:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = normTimeString,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningColor
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // ─── PHASE 2: IN TRANSITION DISPLAY ───
            AnimatedVisibility(
                visible = sessionState == SessionState.IN_TRANSITION,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(transitionTimeRemainingMs)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(transitionTimeRemainingMs) % 60
                    val timeString = String.format("%02d:%02d", minutes, seconds)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transition Remaining:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = WarningColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val totalTransMs = selectedTransition * 60 * 1000L
                    val elapsedFraction = if (totalTransMs > 0) {
                        (1.0f - (transitionTimeRemainingMs.toFloat() / totalTransMs.toFloat())).coerceIn(0f, 1f)
                    } else 0f

                    LinearProgressIndicator(
                        progress = { elapsedFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = WarningColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            if (activeProfile.enableAudioFade) {
                                MetricColumn("Volume", "${(currentVolumePercent * 100).toInt()}%")
                            }
                            if (activeProfile.enableTouchDelay) {
                                MetricColumn("Touch Delay", "${currentTouchDelayMs}ms")
                            }
                            if (activeProfile.enableNetworkThrottling) {
                                MetricColumn("Network", if (isNetworkThrottled) "Throttled (5s)" else "Normal")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // ─── PHASE 3: COMPLETED LOCKOUT DISPLAY ───
            AnimatedVisibility(
                visible = sessionState == SessionState.COMPLETED_LOCKED,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = ErrorColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "00:00 - TIME LIMIT EXCEEDED",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorColor
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "All regulation limits are active at maximum intensity. Restricted apps are locked. Tap STOP to restore normal device operation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (activeProfile.enableAudioFade) EnforcedLimitBadge("Audio: Muted")
                                if (activeProfile.enableTouchDelay) EnforcedLimitBadge("Touch: Delayed")
                                if (activeProfile.enableColorDesaturation) EnforcedLimitBadge("Grayscale: 100%")
                                if (activeProfile.enableOverlayGraying) EnforcedLimitBadge("Overlay: ${(activeProfile.overlayMaxAlpha * 100).toInt()}%")
                                if (activeProfile.enableNetworkThrottling) EnforcedLimitBadge("Network: Throttled")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Active Features Badges Summary (in IDLE state)
            if (sessionState == SessionState.IDLE) {
                if (activeFeatures.isNotEmpty()) {
                    Text(
                        text = "Active Gradual Degradations:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        activeFeatures.forEach { feature ->
                            FeatureBadge(feature)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ─── DYNAMIC ACTION BUTTON (START / STOP) ───
            if (sessionState == SessionState.IDLE) {
                Button(
                    onClick = { onStartSession(selectedTotalUsage, selectedTransition) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = activeFeatures.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "START SESSION ($selectedTotalUsage min)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            } else {
                Button(
                    onClick = onStopSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorColor,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (sessionState == SessionState.COMPLETED_LOCKED) "STOP / RESTORE DEVICE" else "STOP SESSION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeatureBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun EnforcedLimitBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = ErrorColor.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = ErrorColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            fontSize = 10.sp
        )
    }
}

@Composable
fun StatusIndicator(sessionState: SessionState, engineState: EngineState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val color = when (sessionState) {
        SessionState.IDLE -> SuccessColor
        SessionState.NORMAL_USAGE -> SuccessColor
        SessionState.IN_TRANSITION -> WarningColor
        SessionState.COMPLETED_LOCKED -> ErrorColor
    }

    val currentAlpha = if (sessionState == SessionState.IN_TRANSITION || sessionState == SessionState.COMPLETED_LOCKED) alpha else 1.0f

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = currentAlpha))
    )
}
