package com.example.turnaway.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.turnaway.ui.theme.SuccessColor

@Composable
fun SetupGuideCard(
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    modifier: Modifier = Modifier
) {
    // Only show when at least one permission is missing
    if (hasOverlayPermission && hasAccessibilityPermission) return

    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Complete Setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "A couple of quick steps to get TurnAway ready",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 34.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Step 1: Screen Overlay
            PermissionStep(
                stepNumber = "1",
                title = "Allow screen overlay",
                description = "Lets the wind-down effect appear over other apps",
                isGranted = hasOverlayPermission,
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Accessibility Service
            PermissionStep(
                stepNumber = "2",
                title = "Enable wind-down service",
                description = "Allows gradual touch slowdown during wind-down",
                isGranted = hasAccessibilityPermission,
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun PermissionStep(
    stepNumber: String,
    title: String,
    description: String,
    isGranted: Boolean,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Numbered circle
        Surface(
            shape = CircleShape,
            color = if (isGranted) {
                SuccessColor.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f)
            },
            modifier = Modifier.size(32.dp)
        ) {
            if (isGranted) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Completed",
                    tint = SuccessColor,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(24.dp)
                )
            } else {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Step content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (isGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = SuccessColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelMedium,
                        color = SuccessColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                FilledTonalButton(onClick = onOpenSettings) {
                    Text(text = "Open Settings")
                }
            }
        }
    }
}
