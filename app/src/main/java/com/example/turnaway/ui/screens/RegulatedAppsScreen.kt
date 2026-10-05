package com.example.turnaway.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turnaway.data.entity.TargetAppEntity
import com.example.turnaway.ui.components.AppIconImage
import com.example.turnaway.ui.components.TurnAwayBrandLogo
import com.example.turnaway.ui.components.TurnAwaySwitch
import com.example.turnaway.ui.theme.*

enum class AppSelectionMode {
    FULLY_BLOCKED,
    RESTRICTED
}

@Composable
fun RegulatedAppsScreen(
    targetApps: List<TargetAppEntity>,
    mode: AppSelectionMode = AppSelectionMode.RESTRICTED,
    onToggleAppTarget: (packageName: String, isTargeted: Boolean) -> Unit,
    onSelectAllTargets: (isTargeted: Boolean) -> Unit,
    onToggleAppBlocked: (packageName: String, isBlocked: Boolean) -> Unit,
    onSelectAllBlocked: (isBlocked: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Social", "Games", "Productivity", "Entertainment")

    fun inferCategory(packageName: String, appName: String): String {
        val lower = "$packageName $appName".lowercase()
        return when {
            lower.contains("game") || lower.contains("puzzle") || lower.contains("makeup") || lower.contains("play") -> "Games"
            lower.contains("tiktok") || lower.contains("instagram") || lower.contains("facebook") || lower.contains("whatsapp") || lower.contains("snapchat") || lower.contains("twitter") || lower.contains("social") -> "Social"
            lower.contains("youtube") || lower.contains("netflix") || lower.contains("music") || lower.contains("video") || lower.contains("spotify") || lower.contains("tv") -> "Entertainment"
            lower.contains("reader") || lower.contains("pdf") || lower.contains("book") || lower.contains("note") || lower.contains("doc") || lower.contains("office") -> "Productivity"
            else -> "Productivity"
        }
    }

    val isBlockedMode = mode == AppSelectionMode.FULLY_BLOCKED
    val activeCount = remember(targetApps, isBlockedMode) {
        if (isBlockedMode) targetApps.count { it.isBlocked }
        else targetApps.count { it.isTargeted }
    }
    val totalCount = targetApps.size

    val filteredApps = remember(targetApps, searchQuery, selectedCategory) {
        targetApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            val matchesCat = if (selectedCategory == "All") true
            else inferCategory(app.packageName, app.appName) == selectedCategory

            matchesSearch && matchesCat
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TurnawayBackground)
    ) {
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
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go Back",
                                tint = TurnawayPrimary
                            )
                        }
                        TurnAwayBrandLogo(size = 28.dp, cornerRadius = 8.dp)
                        Text(
                            text = if (isBlockedMode) "Fully Blocked Apps" else "Restricted Apps",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TurnawayPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ─── APP LIST & FILTERS ───
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Status Pill Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(9999.dp),
                            color = if (isBlockedMode) TurnawayError.copy(alpha = 0.12f) else TurnawaySecondaryContainer,
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBlockedMode) Icons.Default.Block else Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = if (isBlockedMode) TurnawayError else TurnawayOnSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isBlockedMode) "$activeCount of $totalCount Blocked" else "$activeCount of $totalCount Regulated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBlockedMode) TurnawayError else TurnawayOnSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Educational Help Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = TurnawaySurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isBlockedMode) TurnawayError.copy(alpha = 0.12f) else TurnawayPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isBlockedMode) Icons.Default.Block else Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = if (isBlockedMode) TurnawayError else TurnawayOnPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isBlockedMode) "Hard Lockout Policy" else "App Restriction Rules",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TurnawayPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBlockedMode) {
                                        "Selected apps are immediately locked with zero access granted as soon as a session is active."
                                    } else {
                                        "Selected apps will experience progressive latency, screen graying, and volume suppression during transition."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TurnawayOnSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Search Input Bar
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TurnawaySurfaceContainerLowest,
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TurnawayOutline,
                                modifier = Modifier.size(20.dp)
                            )
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "Search $totalCount installed apps...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TurnawayOutline
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TurnawayOnSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Category Chips Carousel & Bulk Actions
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                val label = if (cat == "All") "All ($totalCount)" else cat
                                Surface(
                                    onClick = { selectedCategory = cat },
                                    shape = RoundedCornerShape(9999.dp),
                                    color = if (isSelected) TurnawayPrimary else TurnawaySurfaceContainer,
                                    shadowElevation = if (isSelected) 1.dp else 0.dp
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) TurnawayOnPrimary else TurnawayOnSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // Bulk Actions Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Showing ${filteredApps.size} apps",
                                style = MaterialTheme.typography.labelSmall,
                                color = TurnawayOnSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    onClick = {
                                        if (isBlockedMode) onSelectAllBlocked(true)
                                        else onSelectAllTargets(true)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = TurnawaySurfaceContainerHigh
                                ) {
                                    Text(
                                        text = "Select All",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TurnawayPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                Surface(
                                    onClick = {
                                        if (isBlockedMode) onSelectAllBlocked(false)
                                        else onSelectAllTargets(false)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = TurnawaySurfaceContainerHigh
                                ) {
                                    Text(
                                        text = "Deselect All",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TurnawayOnSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Empty State
                if (filteredApps.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(TurnawaySurfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterListOff,
                                    contentDescription = null,
                                    tint = TurnawayOutline,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No matching apps found",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TurnawayPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try adjusting your search query or reset category filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TurnawayOnSurfaceVariant
                            )
                        }
                    }
                } else {
                    // App Rows
                    items(filteredApps, key = { it.packageName }) { app ->
                        val cat = inferCategory(app.packageName, app.appName)
                        val isChecked = if (isBlockedMode) app.isBlocked else app.isTargeted

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = TurnawaySurfaceContainerLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
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
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(TurnawaySurfaceContainer)
                                            .border(1.dp, TurnawayOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppIconImage(
                                            packageName = app.packageName,
                                            size = 32.dp
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = TurnawayOnSurface,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(9999.dp),
                                                color = TurnawaySurfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = cat,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TurnawayOnSurfaceVariant,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                TurnAwaySwitch(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (isBlockedMode) {
                                            onToggleAppBlocked(app.packageName, checked)
                                        } else {
                                            onToggleAppTarget(app.packageName, checked)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sticky Bottom Summary Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(elevation = 16.dp, ambientColor = TurnawayPrimary.copy(alpha = 0.08f)),
            color = TurnawaySurface.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
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
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Apply Changes",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
