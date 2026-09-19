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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.turnaway.service.EngineState
import com.example.turnaway.ui.theme.ErrorColor
import com.example.turnaway.ui.theme.SuccessColor
import com.example.turnaway.ui.theme.WarningColor
import java.util.concurrent.TimeUnit

@Composable
fun EngineStatusCard(
    engineState: EngineState,
    timeRemainingMs: Long,
    currentSaturation: Float,
    currentBlurRadius: Int,
    currentTouchDelayMs: Long,
    currentVolumePercent: Float,
    onTriggerManualLanding: () -> Unit,
    onEmergencyAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusIndicator(engineState = engineState)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (engineState) {
                        EngineState.MONITORING -> "Ready"
                        EngineState.SOFT_LANDING_TRANSITION -> "Winding Down…"
                        EngineState.LOCKED_OUT -> "Wind-Down Complete"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body
            AnimatedVisibility(
                visible = engineState == EngineState.MONITORING,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Text(
                        text = "Device is running normally. You can start the wind-down sequence at any time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onTriggerManualLanding,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Wind-Down")
                    }
                }
            }

            AnimatedVisibility(
                visible = engineState == EngineState.SOFT_LANDING_TRANSITION,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemainingMs)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemainingMs) % 60
                    val timeString = String.format("%02d:%02d", minutes, seconds)
                    
                    Text(
                        text = "Time Remaining: $timeString",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Simple progress bar based on time remaining.
                    // Assuming a standard 5-minute (300000ms) wind down for progress calculation
                    val totalTimeMs = 300000f
                    val progress = 1f - (timeRemainingMs.toFloat() / totalTimeMs).coerceIn(0f, 1f)

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (currentBlurRadius > 0) "Screen blur active (${currentBlurRadius} px) & FPS lag throttling" else "Screen FPS lag throttling active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onEmergencyAbort,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorColor,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Stop Wind-Down")
                    }
                }
            }

            AnimatedVisibility(
                visible = engineState == EngineState.LOCKED_OUT,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Text(
                        text = "Wind-down transition is complete. Screen is dimmed and locked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onEmergencyAbort,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorColor,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Stop Wind-Down")
                    }
                }
            }
        }
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
        EngineState.LOCKED_OUT -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val currentAlpha = if (engineState == EngineState.SOFT_LANDING_TRANSITION) alpha else 1.0f

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = currentAlpha))
    )
}
