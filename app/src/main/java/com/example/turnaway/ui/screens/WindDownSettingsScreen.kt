package com.example.turnaway.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turnaway.data.entity.RestrictionProfileEntity
import com.example.turnaway.ui.components.TurnAwayBrandLogo
import com.example.turnaway.ui.components.TurnAwaySwitch
import com.example.turnaway.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PaletteOption(
    val name: String,
    val hex: String,
    val color: Color
)

val VeilPalettes = listOf(
    PaletteOption("Charcoal", "#1E242B", VeilCharcoal),
    PaletteOption("Amber Night", "#7C4A03", VeilAmberNight),
    PaletteOption("Warm Sepia", "#5A3825", VeilWarmSepia),
    PaletteOption("Midnight Navy", "#0E1E38", VeilMidnightNavy),
    PaletteOption("Deep Crimson", "#4A121A", VeilDeepCrimson)
)

@Composable
fun WindDownSettingsScreen(
    profile: RestrictionProfileEntity,
    regulatedAppsCount: Int,
    blockedAppsCount: Int = 0,
    onProfileUpdated: (RestrictionProfileEntity) -> Unit,
    onLockDashboard: () -> Unit,
    onNavigateToBlockedApps: () -> Unit,
    onNavigateToRestrictedApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showToast by remember { mutableStateOf(false) }

    var enableGrayscale by remember(profile.enableColorDesaturation) {
        mutableStateOf(profile.enableColorDesaturation)
    }
    var enableVeil by remember(profile.enableOverlayGraying) {
        mutableStateOf(profile.enableOverlayGraying)
    }
    var selectedPaletteHex by remember(profile.overlayColorHex) {
        mutableStateOf(profile.overlayColorHex.ifEmpty { "#7C4A03" })
    }
    var veilOpacity by remember(profile.overlayMaxAlpha) {
        mutableFloatStateOf(profile.overlayMaxAlpha.coerceIn(0.30f, 0.95f))
    }
    var enableTouchSlowdown by remember(profile.enableTouchDelay) {
        mutableStateOf(profile.enableTouchDelay)
    }
    var touchLatencySeconds by remember(profile.maxTouchDelayMs) {
        mutableFloatStateOf((profile.maxTouchDelayMs / 1000f).coerceIn(0.1f, 3.0f))
    }
    var enableVolumeFade by remember(profile.enableAudioFade) {
        mutableStateOf(profile.enableAudioFade)
    }
    var enableNetworkThrottling by remember(profile.enableNetworkThrottling) {
        mutableStateOf(profile.enableNetworkThrottling)
    }

    fun applySave() {
        val updated = profile.copy(
            curveType = "LINEAR",
            enableColorDesaturation = enableGrayscale,
            enableOverlayGraying = enableVeil,
            overlayColorHex = selectedPaletteHex,
            overlayMaxAlpha = veilOpacity,
            enableTouchDelay = enableTouchSlowdown,
            maxTouchDelayMs = (touchLatencySeconds * 1000f).toLong(),
            enableAudioFade = enableVolumeFade,
            enableNetworkThrottling = enableNetworkThrottling
        )
        onProfileUpdated(updated)
        coroutineScope.launch {
            showToast = true
            delay(2400)
            showToast = false
        }
    }

    Box(modifier = modifier.fillMaxSize().background(TurnawayBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
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

                    // Lock button (icon only)
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

            // ─── SCROLLABLE SETTINGS CONTENT ───
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {



                // ─── SECTION 2: APP REGULATION RULES ───
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
                        Column {
                            Text(
                                text = "App Regulation Rules",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TurnawayPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Configure app restriction boundaries during sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = TurnawayOnSurfaceVariant
                            )
                        }

                        // Fully Blocked Apps Row
                        Surface(
                            onClick = onNavigateToBlockedApps,
                            shape = RoundedCornerShape(20.dp),
                            color = TurnawaySurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(TurnawayError.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = null,
                                                tint = TurnawayError,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Fully Blocked Apps",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = TurnawayPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Instant Hard Block",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TurnawayOnSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(9999.dp),
                                        color = TurnawayError.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "$blockedAppsCount apps blocked",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TurnawayError,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "Apps that are immediately locked upon session start with zero access granted.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TurnawayOnSurfaceVariant,
                                    lineHeight = 18.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Manage Blocked Apps",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TurnawaySecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = TurnawaySecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Restricted Apps Row
                        Surface(
                            onClick = onNavigateToRestrictedApps,
                            shape = RoundedCornerShape(20.dp),
                            color = TurnawaySurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(TurnawaySecondary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SlowMotionVideo,
                                                contentDescription = null,
                                                tint = TurnawaySecondary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Restricted Apps",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = TurnawayPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Sensory Throttling",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TurnawayOnSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(9999.dp),
                                        color = TurnawaySecondary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "$regulatedAppsCount apps regulated",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TurnawaySecondary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "Apps that remain accessible but undergo progressive latency, grayscale leaching, and audio muting.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TurnawayOnSurfaceVariant,
                                    lineHeight = 18.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Manage Regulated Apps",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TurnawaySecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = TurnawaySecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── SECTION 3: SENSORY DEGRADATION INTERVENTIONS ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sensory Degradation Interventions",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TurnawayPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Card 1: Screen Graying
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(28.dp), ambientColor = TurnawayPrimary.copy(alpha = 0.04f)),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = TurnawaySurfaceContainerLowest)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(TurnawaySurfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterBAndW,
                                    contentDescription = null,
                                    tint = TurnawayPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Screen Graying",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = TurnawayPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                Text(
                                    text = "Smoothly leaches saturated dopamine triggers into monochrome slate.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TurnawayOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        TurnAwaySwitch(
                            checked = enableGrayscale,
                            onCheckedChange = { enableGrayscale = it }
                        )
                    }
                }

                // Card 2: Ambient Screen Veil (With Palette Swatches & Opacity Slider)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(TurnawaySurfaceContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WbTwilight,
                                        contentDescription = null,
                                        tint = TurnawayPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ambient Screen Veil",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = TurnawayPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Projects a circadian tint mask overlay to curb blue luminescence.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TurnawayOnSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            TurnAwaySwitch(
                                checked = enableVeil,
                                onCheckedChange = { enableVeil = it }
                            )
                        }

                        // Sub-controls Drawer
                        AnimatedVisibility(
                            visible = enableVeil,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Palette Selector
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val currentPalette = VeilPalettes.find { it.hex.equals(selectedPaletteHex, ignoreCase = true) } ?: VeilPalettes[1]

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Atmospheric Tint Spectrum",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TurnawayOnSurfaceVariant
                                        )
                                        Text(
                                            text = currentPalette.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TurnawaySecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 5 Color Swatches
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        VeilPalettes.forEach { opt ->
                                            val isSelected = selectedPaletteHex.equals(opt.hex, ignoreCase = true)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(opt.color)
                                                    .then(
                                                        if (isSelected) Modifier.border(2.dp, TurnawayPrimary, RoundedCornerShape(16.dp))
                                                        else Modifier
                                                    )
                                                    .clickable { selectedPaletteHex = opt.hex },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Opacity Slider with live preview
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = TurnawaySurfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val activeColor = VeilPalettes.find { it.hex.equals(selectedPaletteHex, ignoreCase = true) }?.color ?: VeilAmberNight

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Terminal Veil Opacity",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TurnawayPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .background(activeColor.copy(alpha = veilOpacity))
                                                )
                                                Text(
                                                    text = "${(veilOpacity * 100).toInt()}% Max",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = TurnawaySecondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Slider(
                                            value = veilOpacity,
                                            onValueChange = { veilOpacity = it },
                                            valueRange = 0.30f..0.95f,
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
                                            Text("Soft 30%", style = MaterialTheme.typography.labelSmall, color = TurnawayOnSurfaceVariant, fontSize = 10.sp)
                                            Text("Balanced 60%", style = MaterialTheme.typography.labelSmall, color = TurnawayOnSurfaceVariant, fontSize = 10.sp)
                                            Text("Total Obscurity 95%", style = MaterialTheme.typography.labelSmall, color = TurnawayOnSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 3: Gentle Touch Slowdown with Latency Slider
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(TurnawaySurfaceContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = TurnawayPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Gentle Touch Slowdown",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = TurnawayPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Injects progressive latency into fling gestures to break doomscrolling.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TurnawayOnSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            TurnAwaySwitch(
                                checked = enableTouchSlowdown,
                                onCheckedChange = { enableTouchSlowdown = it }
                            )
                        }

                        // Touch latency adjustment slider (up to 3.0 seconds max)
                        AnimatedVisibility(visible = enableTouchSlowdown) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = TurnawaySurfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Latency Delay",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TurnawayPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = String.format("%.1f s delay", touchLatencySeconds),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TurnawaySecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Slider(
                                        value = touchLatencySeconds,
                                        onValueChange = { touchLatencySeconds = it },
                                        valueRange = 0.1f..3.0f,
                                        steps = 28,
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
                                        Text("0.1s", style = MaterialTheme.typography.labelSmall, color = TurnawayOutline, fontSize = 10.sp)
                                        Text("1.5s", style = MaterialTheme.typography.labelSmall, color = TurnawayOutline, fontSize = 10.sp)
                                        Text("3.0s max", style = MaterialTheme.typography.labelSmall, color = TurnawayOutline, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 4: Volume Fade-Out
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(28.dp), ambientColor = TurnawayPrimary.copy(alpha = 0.04f)),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = TurnawaySurfaceContainerLowest)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(TurnawaySurfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                                    contentDescription = null,
                                    tint = TurnawayPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Volume Fade-Out",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TurnawayPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Slowly suppresses background media playback, shorts, and notifications to 10% volume floor.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TurnawayOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        TurnAwaySwitch(
                            checked = enableVolumeFade,
                            onCheckedChange = { enableVolumeFade = it }
                        )
                    }
                }

                // Card 5: Pulsed Network Throttling
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(28.dp), ambientColor = TurnawayPrimary.copy(alpha = 0.04f)),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = TurnawaySurfaceContainerLowest)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(TurnawaySurfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiTetheringOff,
                                    contentDescription = null,
                                    tint = TurnawayPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pulsed Network Throttling",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TurnawayPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Alternates 1-min network blackout drops at 50% transition threshold to naturally pause feed loading.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TurnawayOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        TurnAwaySwitch(
                            checked = enableNetworkThrottling,
                            onCheckedChange = { enableNetworkThrottling = it }
                        )
                    }
                }



                // Save Settings Pill Button
                Button(
                    onClick = { applySave() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(9999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TurnawayPrimary,
                        contentColor = TurnawayOnPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Save Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Animated Mini Toast Notification Overlay
        AnimatedVisibility(
            visible = showToast,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TurnawayPrimary,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = TurnawaySecondaryFixed,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Parameters Updated",
                            style = MaterialTheme.typography.labelLarge,
                            color = TurnawayOnPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Engine ready for next scheduled bedtime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TurnawayPrimaryFixed
                        )
                    }
                }
            }
        }
    }
}
