package com.example.turnaway.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turnaway.ui.theme.SuccessColor
import com.example.turnaway.ui.theme.ErrorColor
import com.example.turnaway.ui.theme.WarningColor
import com.example.turnaway.util.LogEntry
import com.example.turnaway.util.LogLevel

@Composable
fun DiagnosticsLogCard(
    logEntries: List<LogEntry>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var expandedLogId by remember { mutableStateOf<Long?>(null) }

    val filteredLogs = remember(logEntries, selectedLevelFilter) {
        if (selectedLevelFilter == null) logEntries
        else logEntries.filter { it.level == selectedLevelFilter }
    }

    val errorCount = logEntries.count { it.level == LogLevel.ERROR }
    val warnCount = logEntries.count { it.level == LogLevel.WARN }
    val hasErrors = errorCount > 0

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "System Diagnostics & Error Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${logEntries.size} events ($errorCount errors, $warnCount warnings)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Copy All Logs
                IconButton(
                    onClick = {
                        val text = logEntries.joinToString("\n") { "[${it.timestamp}] [${it.level}] [${it.tag}] ${it.message} ${it.details ?: ""}" }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("App Logs", text))
                        Toast.makeText(context, "Copied ${logEntries.size} logs to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Logs")
                }

                // Clear Logs
                IconButton(onClick = onClearLogs) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Logs", tint = ErrorColor)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Chips Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedLevelFilter == null,
                    onClick = { selectedLevelFilter = null },
                    label = { Text("All (${logEntries.size})") }
                )
                FilterChip(
                    selected = selectedLevelFilter == LogLevel.ERROR,
                    onClick = { selectedLevelFilter = LogLevel.ERROR },
                    label = { Text("Errors ($errorCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ErrorColor.copy(alpha = 0.2f),
                        selectedLabelColor = ErrorColor
                    )
                )
                FilterChip(
                    selected = selectedLevelFilter == LogLevel.WARN,
                    onClick = { selectedLevelFilter = LogLevel.WARN },
                    label = { Text("Warnings ($warnCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = WarningColor.copy(alpha = 0.2f),
                        selectedLabelColor = WarningColor
                    )
                )
                FilterChip(
                    selected = selectedLevelFilter == LogLevel.INFO,
                    onClick = { selectedLevelFilter = LogLevel.INFO },
                    label = { Text("Info") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SuccessColor.copy(alpha = 0.2f),
                        selectedLabelColor = SuccessColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log Items List Container
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "No log records found.",
                            color = if (hasErrors) ErrorColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            LogItemRow(
                                entry = log,
                                isExpanded = expandedLogId == log.id,
                                onToggleExpand = {
                                    expandedLogId = if (expandedLogId == log.id) null else log.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(
    entry: LogEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> ErrorColor
        LogLevel.WARN -> WarningColor
        LogLevel.INFO -> SuccessColor
        LogLevel.DEBUG -> MaterialTheme.colorScheme.primary
    }

    val icon = when (entry.level) {
        LogLevel.ERROR -> Icons.Default.Error
        LogLevel.WARN -> Icons.Default.Warning
        else -> Icons.Default.Info
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = levelColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = entry.timestamp,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = levelColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = entry.tag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = levelColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (entry.level == LogLevel.ERROR) FontWeight.SemiBold else FontWeight.Normal
            )

            if (isExpanded && !entry.details.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entry.details ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = ErrorColor.copy(alpha = 0.9f),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
