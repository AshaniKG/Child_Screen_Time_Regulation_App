package com.example.turnaway.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.engine.DecayCurveType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileConfigurationCard(
    profile: RestrictionProfileEntity,
    onProfileUpdated: (RestrictionProfileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var transitionDuration by remember { mutableFloatStateOf(profile.transitionDurationMinutes.toFloat()) }
    var enableDimming by remember { mutableStateOf(profile.enableColorDesaturation) }
    var blurIntensity by remember { mutableFloatStateOf(profile.maxBlurRadius.toFloat()) }
    var enableFrameThrottling by remember { mutableStateOf(profile.enableFrameThrottling) }
    var minFpsFloor by remember { mutableFloatStateOf(profile.minFpsFloor.toFloat()) }
    var enableTouchSlowdown by remember { mutableStateOf(profile.enableTouchDelay) }
    var touchDelayMs by remember { mutableFloatStateOf(profile.maxTouchDelayMs.toFloat()) }
    var enableAudioFade by remember { mutableStateOf(profile.enableAudioFade) }
    var curveTypeStr by remember { mutableStateOf(profile.curveType) }

    LaunchedEffect(profile.id) {
        transitionDuration = profile.transitionDurationMinutes.toFloat()
        enableDimming = profile.enableColorDesaturation
        blurIntensity = profile.maxBlurRadius.toFloat()
        enableFrameThrottling = profile.enableFrameThrottling
        minFpsFloor = profile.minFpsFloor.toFloat()
        enableTouchSlowdown = profile.enableTouchDelay
        touchDelayMs = profile.maxTouchDelayMs.toFloat()
        enableAudioFade = profile.enableAudioFade
        curveTypeStr = profile.curveType
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Wind-Down Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Customize how the screen gradually winds down",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Wind-Down Duration
            Column {
                Text(
                    text = "Wind-Down Duration: ${transitionDuration.toInt()} min",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = transitionDuration,
                    onValueChange = { transitionDuration = it },
                    valueRange = 1f..60f,
                    steps = 58
                )
            }

            // Screen Dimming
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ColorLens,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Screen Dimming", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(text = "Gradually fade colors and blur the screen", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = enableDimming,
                        onCheckedChange = { enableDimming = it }
                    )
                }
                AnimatedVisibility(visible = enableDimming) {
                    Column(modifier = Modifier.padding(start = 36.dp, top = 8.dp)) {
                        Text(
                            text = "Screen Softening",
                            style = MaterialTheme.typography.labelMedium
                        )
                        val blurLabel = when {
                            blurIntensity <= 3f -> "Low"
                            blurIntensity <= 7f -> "Medium"
                            else -> "High"
                        }
                        Text(
                            text = blurLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = blurIntensity,
                            onValueChange = { blurIntensity = it },
                            valueRange = 1f..10f,
                            steps = 2
                        )
                    }
                }
            }

            // Screen Stutter & Frame Slowdown
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Screen Stutter & Frame Slowdown", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(text = "Gradually drops visual framerate to discourage gaming", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = enableFrameThrottling,
                        onCheckedChange = { enableFrameThrottling = it }
                    )
                }
                AnimatedVisibility(visible = enableFrameThrottling) {
                    Column(modifier = Modifier.padding(start = 36.dp, top = 8.dp)) {
                        Text(
                            text = "Framerate Floor",
                            style = MaterialTheme.typography.labelMedium
                        )
                        val fpsLabel = when {
                            minFpsFloor <= 8f -> "Heavy Stutter (5-8 FPS)"
                            minFpsFloor <= 15f -> "Moderate Lag (10-15 FPS)"
                            else -> "Mild Jitter (20-30 FPS)"
                        }
                        Text(
                            text = fpsLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = minFpsFloor,
                            onValueChange = { minFpsFloor = it },
                            valueRange = 5f..30f,
                            steps = 4
                        )
                    }
                }
            }

            // Gentle Touch Slowdown
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Gentle Touch Slowdown", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(text = "Slowly reduce touch responsiveness", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = enableTouchSlowdown,
                        onCheckedChange = { enableTouchSlowdown = it }
                    )
                }
                AnimatedVisibility(visible = enableTouchSlowdown) {
                    Column(modifier = Modifier.padding(start = 36.dp, top = 8.dp)) {
                        Text(
                            text = "Slowdown Intensity",
                            style = MaterialTheme.typography.labelMedium
                        )
                        val touchLabel = when {
                            touchDelayMs < 300 -> "Light"
                            touchDelayMs < 600 -> "Moderate"
                            else -> "Strong"
                        }
                        Text(
                            text = touchLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = touchDelayMs,
                            onValueChange = { touchDelayMs = it },
                            valueRange = 100f..800f,
                            steps = 6
                        )
                    }
                }
            }

            // Volume Fade-Out
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.VolumeOff,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Volume Fade-Out", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Gradually lower media volume", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = enableAudioFade,
                    onCheckedChange = { enableAudioFade = it }
                )
            }
            
            // Wind-Down Style
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Wind-Down Style",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                val options = listOf("Steady" to "LINEAR", "Gradual" to "EXPONENTIAL", "Smooth" to "SIGMOIDAL")
                val selectedIndex = options.indexOfFirst { it.second == curveTypeStr }.takeIf { it >= 0 } ?: 0
                
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            selected = index == selectedIndex,
                            onClick = { curveTypeStr = value },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
            
            // Save Button
            FilledTonalButton(
                onClick = {
                    val updated = profile.copy(
                        transitionDurationMinutes = transitionDuration.toInt(),
                        curveType = curveTypeStr,
                        enableColorDesaturation = enableDimming,
                        maxBlurRadius = blurIntensity.toInt(),
                        enableFrameThrottling = enableFrameThrottling,
                        minFpsFloor = minFpsFloor.toInt(),
                        enableTouchDelay = enableTouchSlowdown,
                        maxTouchDelayMs = touchDelayMs.toLong(),
                        enableAudioFade = enableAudioFade
                    )
                    onProfileUpdated(updated)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Save Settings")
            }
        }
    }
}
