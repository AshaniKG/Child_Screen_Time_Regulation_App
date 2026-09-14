package com.example.turnaway.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turnaway.service.EngineState
import com.example.turnaway.ui.theme.PrimaryColor
import com.example.turnaway.ui.theme.SuccessColor
import com.example.turnaway.ui.theme.ErrorColor
import com.example.turnaway.ui.theme.WarningColor

@Composable
fun EngineStatusCard(
    engineState: EngineState,
    timeRemainingMs: Long,
    currentSaturation: Float,
    currentFps: Int,
    currentTouchDelayMs: Long,
    currentVolumePercent: Float,
    onTriggerManualLanding: () -> Unit,
    onEmergencyAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTransitioning = engineState == EngineState.SOFT_LANDING_TRANSITION
    val isLocked = engineState == EngineState.LOCKED_OUT

    // Pulsing animation for active transition
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaPulse"
    )

    val stateColor = when (engineState) {
        EngineState.MONITORING -> SuccessColor
        EngineState.SOFT_LANDING_TRANSITION -> WarningColor
        EngineState.LOCKED_OUT -> ErrorColor
    }

    val stateText = when (engineState) {
        EngineState.MONITORING -> "System Monitoring (Idle)"
        EngineState.SOFT_LANDING_TRANSITION -> "Soft-Landing Degradation Active"
        EngineState.LOCKED_OUT -> "Transition Complete (Lockout)"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(stateColor.copy(alpha = if (isTransitioning) alphaPulse else 1.0f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = stateColor,
                    modifier = Modifier.weight(1f)
                )

                if (isTransitioning && timeRemainingMs > 0) {
                    val seconds = (timeRemainingMs / 1000) % 60
                    val minutes = (timeRemainingMs / 1000) / 60
                    Text(
                        text = String.format("%02d:%02d remaining", minutes, seconds),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = WarningColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dynamic Sensory Gauges Row (3 Core Features Status)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Gauge 1: Saturation / Grayscale
                SensoryGaugeTile(
                    title = "Color Saturation",
                    valueText = "${(currentSaturation * 100).toInt()}%",
                    icon = Icons.Default.Palette,
                    accentColor = PrimaryColor,
                    progress = currentSaturation,
                    modifier = Modifier.weight(1f)
                )

                // Gauge 3: Touch Latency Queue
                SensoryGaugeTile(
                    title = "Touch Lag",
                    valueText = "${currentTouchDelayMs}ms",
                    icon = Icons.Default.TouchApp,
                    accentColor = if (currentTouchDelayMs > 200) ErrorColor else SuccessColor,
                    progress = (currentTouchDelayMs / 800f).coerceIn(0f, 1f),
                    modifier = Modifier.weight(1f)
                )

                // Gauge 4: Audio Volume Fading
                SensoryGaugeTile(
                    title = "Media Vol",
                    valueText = "${(currentVolumePercent * 100).toInt()}%",
                    icon = Icons.Default.Lock,
                    accentColor = PrimaryColor,
                    progress = currentVolumePercent,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isTransitioning || isLocked) {
                    Button(
                        onClick = onEmergencyAbort,
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Emergency Abort", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onTriggerManualLanding,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Soft-Landing Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SensoryGaugeTile(
    title: String,
    valueText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
            )
        }
    }
}
