package com.example.turnaway.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.engine.DecayCurveType

@Composable
fun ProfileConfigurationCard(
    profile: RestrictionProfileEntity,
    onProfileUpdated: (RestrictionProfileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // Unbound remember so sliders move smoothly without flickering on recomposition
    var transitionMinutes by remember { mutableFloatStateOf(profile.transitionDurationMinutes.toFloat()) }
    var enableGrayscale by remember { mutableStateOf(profile.enableColorDesaturation) }
    var enableTouchDelay by remember { mutableStateOf(profile.enableTouchDelay) }
    var maxTouchDelayMs by remember { mutableFloatStateOf(profile.maxTouchDelayMs.toFloat()) }
    var curveType by remember { mutableStateOf(profile.curveType) }

    // Sync with external profile updates only when ID changes
    LaunchedEffect(profile.id) {
        transitionMinutes = profile.transitionDurationMinutes.toFloat()
        enableGrayscale = profile.enableColorDesaturation
        enableTouchDelay = profile.enableTouchDelay
        maxTouchDelayMs = profile.maxTouchDelayMs.toFloat()
        curveType = profile.curveType
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
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Restriction Profile Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure sensory transition curves & thresholds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Transition Duration Slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Transition Window: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(text = "${transitionMinutes.toInt()} min", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = transitionMinutes,
                onValueChange = { transitionMinutes = it },
                valueRange = 1f..60f,
                steps = 59
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Grayscale Desaturation Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "1. Color Desaturation Filter", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = "Fade screen colors to grayscale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableGrayscale, onCheckedChange = { enableGrayscale = it })
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Touch Input Latency Queue Toggle & Max Lag Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "2. Touch Latency Queue", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = "Maximum lag floor: ${maxTouchDelayMs.toInt()} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableTouchDelay, onCheckedChange = { enableTouchDelay = it })
            }
            if (enableTouchDelay) {
                Slider(
                    value = maxTouchDelayMs,
                    onValueChange = { maxTouchDelayMs = it },
                    valueRange = 100f..800f,
                    steps = 7
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Dynamic Audio Volume Fading Toggle
            var enableAudioFade by remember { mutableStateOf(profile.enableAudioFade) }
            LaunchedEffect(profile.id) {
                enableAudioFade = profile.enableAudioFade
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "3. Dynamic Audio Fading", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = "Fades volume & locks hardware keys", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableAudioFade, onCheckedChange = { enableAudioFade = it })
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Decay Curve Type Selection
            Text(text = "Decay Curve Model", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecayCurveType.values().forEach { curve ->
                    FilterChip(
                        selected = curveType == curve.name,
                        onClick = { curveType = curve.name },
                        label = { Text(curve.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Button
            Button(
                onClick = {
                    val updated = profile.copy(
                        transitionDurationMinutes = transitionMinutes.toInt(),
                        enableColorDesaturation = enableGrayscale,
                        enableTouchDelay = enableTouchDelay,
                        maxTouchDelayMs = maxTouchDelayMs.toLong(),
                        enableAudioFade = enableAudioFade,
                        curveType = curveType
                    )
                    onProfileUpdated(updated)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Profile Settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}
