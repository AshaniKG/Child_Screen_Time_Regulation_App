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
import com.example.turnaway.service.EngineState
import com.example.turnaway.ui.theme.ErrorColor
import com.example.turnaway.ui.theme.SuccessColor
import com.example.turnaway.ui.theme.WarningColor
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EngineStatusCard(
    engineState: EngineState,
    timeRemainingMs: Long,
    activeProfile: RestrictionProfileEntity,
    currentTouchDelayMs: Long,
    currentVolumePercent: Float,
    transitionDurationMinutes: Int = 2,
    onDurationChange: (Int) -> Unit = {},
    onTriggerManualLanding: () -> Unit,
    onEmergencyAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDuration by remember(transitionDurationMinutes) {
        mutableIntStateOf(transitionDurationMinutes)
    }

    // Build list of active features from profile
    val activeFeatures = remember(activeProfile) {
        buildList {
            if (activeProfile.enableAudioFade) add("🔊 Volume Fade")
            if (activeProfile.enableTouchDelay) add("👆 Touch Delay")
            if (activeProfile.enableColorDesaturation) add("🎨 System Grayscale")
            if (activeProfile.enableOverlayGraying) add("🎭 Overlay Veil (${(activeProfile.overlayMaxAlpha * 100).toInt()}%)")
            if (activeProfile.enableFrameThrottling) add("⚡ Screen Lag")
            if (activeProfile.maxBlurRadius > 0) add("🌫 Blur (${activeProfile.maxBlurRadius}px)")
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (engineState == EngineState.LOCKED_OUT) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
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
                StatusIndicator(engineState = engineState)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = when (engineState) {
                            EngineState.MONITORING -> "Ready"
                            EngineState.SOFT_LANDING_TRANSITION -> "Degrading…"
                            EngineState.LOCKED_OUT -> "Time Limit Exceeded!"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (engineState == EngineState.LOCKED_OUT) ErrorColor else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (engineState) {
                            EngineState.MONITORING -> "Sensory wind-down configured & ready"
                            EngineState.SOFT_LANDING_TRANSITION -> "Gradually reducing sensory stimulation"
                            EngineState.LOCKED_OUT -> "All enabled limits enforced at maximum"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── READY STATE ───
            AnimatedVisibility(
                visible = engineState == EngineState.MONITORING,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    // Customizable Time Limit Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Time Limit: $selectedDuration minute${if (selectedDuration > 1) "s" else ""}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset chips
                    val presets = listOf(1, 2, 5, 10, 15, 30)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presets.forEach { minutes ->
                            FilterChip(
                                selected = selectedDuration == minutes,
                                onClick = {
                                    selectedDuration = minutes
                                    onDurationChange(minutes)
                                },
                                label = { Text("${minutes}m") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Duration Slider
                    Slider(
                        value = selectedDuration.toFloat(),
                        onValueChange = {
                            selectedDuration = it.toInt()
                            onDurationChange(it.toInt())
                        },
                        valueRange = 1f..60f,
                        steps = 58
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Active Features Checklist (dynamic)
                    if (activeFeatures.isNotEmpty()) {
                        Text(
                            text = "Active Gradual Features:",
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
                    } else {
                        Text(
                            text = "No wind-down features enabled. Enable features in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onTriggerManualLanding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = activeFeatures.isNotEmpty()
                    ) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Start Wind-Down ($selectedDuration min)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // ─── TRANSITION IN PROGRESS ───
            AnimatedVisibility(
                visible = engineState == EngineState.SOFT_LANDING_TRANSITION,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemainingMs)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemainingMs) % 60
                    val timeString = String.format("%02d:%02d", minutes, seconds)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time Remaining:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar based on time elapsed
                    val totalDurationMs = activeProfile.transitionDurationMinutes * 60 * 1000L
                    val elapsedFraction = if (totalDurationMs > 0) {
                        (1.0f - (timeRemainingMs.toFloat() / totalDurationMs.toFloat())).coerceIn(0f, 1f)
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Simplified real-time metrics (only active features)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
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
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onEmergencyAbort,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorColor,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Stop Degradation",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // ─── TIME LIMIT EXCEEDED (LOCKED OUT) ───
            AnimatedVisibility(
                visible = engineState == EngineState.LOCKED_OUT,
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
                                    text = "Screen-Time Limit Reached",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorColor
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "All enabled regulation features are enforced at maximum intensity. Tap the button below to stop restrictions and restore normal device behavior.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            // Dynamic enforced badges based on profile
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (activeProfile.enableAudioFade) EnforcedLimitBadge("Audio: Muted")
                                if (activeProfile.enableTouchDelay) EnforcedLimitBadge("Touch: Delayed")
                                if (activeProfile.enableColorDesaturation) EnforcedLimitBadge("Grayscale: 100%")
                                if (activeProfile.enableOverlayGraying) EnforcedLimitBadge("Overlay: ${(activeProfile.overlayMaxAlpha * 100).toInt()}%")
                                if (activeProfile.maxBlurRadius > 0) EnforcedLimitBadge("Blur: ${activeProfile.maxBlurRadius}px")
                                if (activeProfile.enableFrameThrottling) EnforcedLimitBadge("Lag: ${activeProfile.minFpsFloor} FPS")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // PROMINENT STOP BUTTON
                    Button(
                        onClick = onEmergencyAbort,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorColor,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "STOP / RESTORE DEVICE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
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
fun StatusIndicator(engineState: EngineState) {
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

    val color = when (engineState) {
        EngineState.MONITORING -> SuccessColor
        EngineState.SOFT_LANDING_TRANSITION -> WarningColor
        EngineState.LOCKED_OUT -> ErrorColor
    }

    val currentAlpha = if (engineState == EngineState.SOFT_LANDING_TRANSITION || engineState == EngineState.LOCKED_OUT) alpha else 1.0f

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = currentAlpha))
    )
}
