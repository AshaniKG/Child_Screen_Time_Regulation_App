package com.example.turnaway.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.engine.DecayCurveType
import com.example.turnaway.ui.theme.SuccessColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Curated overlay color options
data class OverlayColorOption(val name: String, val hex: String, val color: Color)

val OVERLAY_COLOR_PRESETS = listOf(
    OverlayColorOption("Neutral Gray", "#808080", Color(0xFF808080)),
    OverlayColorOption("Dark Charcoal", "#222222", Color(0xFF222222)),
    OverlayColorOption("Warm Amber", "#8C5523", Color(0xFF8C5523)),
    OverlayColorOption("Soft Sepia", "#664422", Color(0xFF664422)),
    OverlayColorOption("Midnight Blue", "#1B263B", Color(0xFF1B263B)),
    OverlayColorOption("Dim Crimson", "#4A1515", Color(0xFF4A1515))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileConfigurationCard(
    profile: RestrictionProfileEntity,
    onProfileUpdated: (RestrictionProfileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var transitionDuration by remember { mutableFloatStateOf(profile.transitionDurationMinutes.toFloat()) }
    var enableGrayscale by remember { mutableStateOf(profile.enableColorDesaturation) }
    var enableOverlay by remember { mutableStateOf(profile.enableOverlayGraying) }
    var selectedOverlayHex by remember { mutableStateOf(profile.overlayColorHex) }
    var overlayAlpha by remember { mutableFloatStateOf(profile.overlayMaxAlpha) }
    var enableBlur by remember { mutableStateOf(profile.maxBlurRadius > 0) }
    var blurIntensity by remember { mutableFloatStateOf(if (profile.maxBlurRadius > 0) profile.maxBlurRadius.toFloat() else 20f) }
    var enableFrameThrottling by remember { mutableStateOf(profile.enableFrameThrottling) }
    var minFpsFloor by remember { mutableFloatStateOf(profile.minFpsFloor.toFloat()) }
    var enableTouchSlowdown by remember { mutableStateOf(profile.enableTouchDelay) }
    var touchDelayMs by remember { mutableFloatStateOf(profile.maxTouchDelayMs.toFloat()) }
    var enableAudioFade by remember { mutableStateOf(profile.enableAudioFade) }
    var enableNetworkThrottling by remember { mutableStateOf(profile.enableNetworkThrottling) }
    var curveTypeStr by remember { mutableStateOf(profile.curveType) }

    var isSavedRecently by remember { mutableStateOf(false) }

    LaunchedEffect(profile.id) {
        transitionDuration = profile.transitionDurationMinutes.toFloat()
        enableGrayscale = profile.enableColorDesaturation
        enableOverlay = profile.enableOverlayGraying
        selectedOverlayHex = profile.overlayColorHex
        overlayAlpha = profile.overlayMaxAlpha
        enableBlur = profile.maxBlurRadius > 0
        blurIntensity = if (profile.maxBlurRadius > 0) profile.maxBlurRadius.toFloat() else 20f
        enableFrameThrottling = profile.enableFrameThrottling
        minFpsFloor = profile.minFpsFloor.toFloat()
        enableTouchSlowdown = profile.enableTouchDelay
        touchDelayMs = profile.maxTouchDelayMs.toFloat()
        enableAudioFade = profile.enableAudioFade
        enableNetworkThrottling = profile.enableNetworkThrottling
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
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
                        text = "Customize gradual degradation when time expires",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // 1. Wind-Down Duration
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wind-Down Duration",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${transitionDuration.toInt()} min",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = transitionDuration,
                    onValueChange = { transitionDuration = it },
                    valueRange = 1f..60f,
                    steps = 58
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // 2. Screen Graying (Native System Grayscale)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterBAndW,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screen Graying (System Grayscale)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Native black & white display (transforms system colors without an overlay)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableGrayscale,
                        onCheckedChange = { enableGrayscale = it }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // 3. Screen Overlay Veil (Customizable Color & Visibility)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screen Overlay Veil",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Draws a tinted visual overlay veil over the screen during wind-down",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableOverlay,
                        onCheckedChange = { enableOverlay = it }
                    )
                }

                AnimatedVisibility(visible = enableOverlay) {
                    Column(
                        modifier = Modifier
                            .padding(start = 36.dp, top = 10.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Overlay Color Presets
                        Text(
                            text = "Overlay Tint Color",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OVERLAY_COLOR_PRESETS.forEach { option ->
                                val isSelected = selectedOverlayHex.equals(option.hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(option.color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedOverlayHex = option.hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Overlay Visibility / Max Opacity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Overlay Visibility / Opacity",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "${(overlayAlpha * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = overlayAlpha,
                            onValueChange = { overlayAlpha = it },
                            valueRange = 0.10f..1.00f,
                            steps = 17
                        )

                        // Live Preview Box
                        val activeColor = remember(selectedOverlayHex, overlayAlpha) {
                            try {
                                val base = android.graphics.Color.parseColor(selectedOverlayHex)
                                Color(base).copy(alpha = overlayAlpha)
                            } catch (e: Exception) {
                                Color.Gray.copy(alpha = overlayAlpha)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 48.dp, height = 28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(activeColor)
                                )
                            }
                            Text(
                                text = "Preview: ${OVERLAY_COLOR_PRESETS.find { it.hex.equals(selectedOverlayHex, ignoreCase = true) }?.name ?: selectedOverlayHex} at ${(overlayAlpha * 100).toInt()}% max veil",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // 4. Screen Blur & Softening (Up to 80 px)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BlurOn,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screen Blur & Softening",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gradually blur and soften the screen (up to 80 px)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableBlur,
                        onCheckedChange = { enableBlur = it }
                    )
                }
                AnimatedVisibility(visible = enableBlur) {
                    Column(modifier = Modifier.padding(start = 36.dp, top = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Blur Intensity (up to 80 px)",
                                style = MaterialTheme.typography.labelMedium
                            )
                            val intensityLabel = when (blurIntensity.toInt()) {
                                in 1..20 -> "Mild (${blurIntensity.toInt()} px)"
                                in 21..50 -> "Medium (${blurIntensity.toInt()} px)"
                                in 51..79 -> "Heavy (${blurIntensity.toInt()} px)"
                                else -> "Maximum (80 px)"
                            }
                            Text(
                                text = intensityLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = blurIntensity,
                            onValueChange = { blurIntensity = it },
                            valueRange = 1f..80f,
                            steps = 78
                        )
                        Text(
                            text = "Highest blur reaches ${blurIntensity.toInt()} px (maximum 80 px) at the end of wind-down.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // 5. Screen Stutter & Frame Slowdown
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Framerate Floor", style = MaterialTheme.typography.labelMedium)
                            val fpsLabel = when {
                                minFpsFloor <= 8f -> "Heavy Stutter (${minFpsFloor.toInt()} FPS)"
                                minFpsFloor <= 15f -> "Moderate Lag (${minFpsFloor.toInt()} FPS)"
                                else -> "Mild Jitter (${minFpsFloor.toInt()} FPS)"
                            }
                            Text(text = fpsLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = minFpsFloor,
                            onValueChange = { minFpsFloor = it },
                            valueRange = 5f..30f,
                            steps = 4
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // 6. Gentle Touch Slowdown
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Maximum Delay", style = MaterialTheme.typography.labelMedium)
                            Text(text = "${touchDelayMs.toInt()} ms", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = touchDelayMs,
                            onValueChange = { touchDelayMs = it },
                            valueRange = 100f..1000f,
                            steps = 8
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // 7. Volume Fade-Out
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // 8. Network Throttling
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.NetworkCheck,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Network Throttling", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Throttle network for 5s once every 1–2 minutes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = enableNetworkThrottling,
                    onCheckedChange = { enableNetworkThrottling = it }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // 8. Wind-Down Style
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Wind-Down Curve Style",
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

            Spacer(modifier = Modifier.height(8.dp))

            // 9. HIGH VISIBILITY PROMINENT SAVE BUTTON
            val buttonColor by animateColorAsState(
                targetValue = if (isSavedRecently) SuccessColor else MaterialTheme.colorScheme.primary,
                label = "SaveButtonColor"
            )

            Button(
                onClick = {
                    val updated = profile.copy(
                        transitionDurationMinutes = transitionDuration.toInt(),
                        curveType = curveTypeStr,
                        enableColorDesaturation = enableGrayscale,
                        enableOverlayGraying = enableOverlay,
                        overlayColorHex = selectedOverlayHex,
                        overlayMaxAlpha = overlayAlpha,
                        maxBlurRadius = if (enableBlur) blurIntensity.toInt().coerceIn(1, 80) else 0,
                        enableFrameThrottling = enableFrameThrottling,
                        minFpsFloor = minFpsFloor.toInt(),
                        enableTouchDelay = enableTouchSlowdown,
                        maxTouchDelayMs = touchDelayMs.toLong(),
                        enableAudioFade = enableAudioFade,
                        enableNetworkThrottling = enableNetworkThrottling
                    )
                    onProfileUpdated(updated)

                    coroutineScope.launch {
                        isSavedRecently = true
                        Toast.makeText(context, "Wind-Down Settings Saved!", Toast.LENGTH_SHORT).show()
                        delay(2500)
                        isSavedRecently = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isSavedRecently) Icons.Default.Check else Icons.Default.Save,
                        contentDescription = "Save",
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (isSavedRecently) "SETTINGS SAVED!" else "SAVE ALL SETTINGS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
