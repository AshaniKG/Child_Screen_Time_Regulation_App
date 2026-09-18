package com.example.turnaway.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.turnaway.data.entity.SessionLogEntity

@Composable
fun DisengagementAnalyticsCard(
    sessionLogs: List<SessionLogEntity>,
    modifier: Modifier = Modifier
) {
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
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Session Insights",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "How your child responded to recent wind-downs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            if (sessionLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No sessions yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Wind-down session insights will appear here after the first use",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // KPIs
                val avgMs = sessionLogs.map { it.timeToDisengageMs }.average().toLong()
                val avgMin = (avgMs / 1000) / 60
                val avgSec = (avgMs / 1000) % 60
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Avg. Wind-Down",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${avgMin}m ${avgSec}s",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Sessions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${sessionLogs.size} total",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Chart
                val chartData = sessionLogs.takeLast(10).map { it.timeToDisengageMs }
                // Avoid division by zero
                val maxVal = chartData.maxOrNull()?.let { if (it == 0L) 1L else it } ?: 1L
                
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .drawWithCache {
                            onDrawBehind {
                                // Draw horizontal grid lines (4 lines)
                                val gridLineCount = 4
                                val spacingY = size.height / (gridLineCount - 1).coerceAtLeast(1)
                                for (i in 0 until gridLineCount) {
                                    val y = i * spacingY
                                    drawLine(
                                        color = gridColor.copy(alpha = 0.3f),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                                
                                if (chartData.size > 1) {
                                    val spacingX = size.width / (chartData.size - 1)
                                    
                                    val path = Path()
                                    val fillPath = Path()
                                    
                                    val points = chartData.mapIndexed { index, value ->
                                        val x = index * spacingX
                                        val y = size.height - (value.toFloat() / maxVal.toFloat() * size.height)
                                        Offset(x, y)
                                    }
                                    
                                    path.moveTo(points[0].x, points[0].y)
                                    fillPath.moveTo(points[0].x, points[0].y)
                                    
                                    for (i in 0 until points.size - 1) {
                                        val p0 = points[i]
                                        val p1 = points[i + 1]
                                        
                                        val controlX = p0.x + (p1.x - p0.x) / 2f
                                        
                                        path.cubicTo(
                                            controlX, p0.y,
                                            controlX, p1.y,
                                            p1.x, p1.y
                                        )
                                        
                                        fillPath.cubicTo(
                                            controlX, p0.y,
                                            controlX, p1.y,
                                            p1.x, p1.y
                                        )
                                    }
                                    
                                    // Complete the fill path
                                    fillPath.lineTo(points.last().x, size.height)
                                    fillPath.lineTo(points.first().x, size.height)
                                    fillPath.close()
                                    
                                    // Draw the gradient fill
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                lineColor.copy(alpha = 0.3f),
                                                Color.Transparent
                                            ),
                                            startY = 0f,
                                            endY = size.height
                                        ),
                                        style = Fill
                                    )
                                    
                                    // Draw the path line
                                    drawPath(
                                        path = path,
                                        color = lineColor,
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                    
                                    // Draw points
                                    points.forEach { point ->
                                        drawCircle(
                                            color = lineColor,
                                            radius = 4.dp.toPx(),
                                            center = point
                                        )
                                    }
                                } else if (chartData.size == 1) {
                                    val y = size.height - (chartData[0].toFloat() / maxVal.toFloat() * size.height)
                                    val point = Offset(size.width / 2f, y)
                                    drawCircle(
                                        color = lineColor,
                                        radius = 4.dp.toPx(),
                                        center = point
                                    )
                                }
                            }
                        }
                ) {}
            }
        }
    }
}
