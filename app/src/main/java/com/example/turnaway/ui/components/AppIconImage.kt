package com.example.turnaway.ui.components

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.turnaway.ui.theme.TurnawayPrimary

@Composable
fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val context = LocalContext.current
    val imageBitmap = remember(packageName) {
        try {
            val pm: PackageManager = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val px = (size.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
            drawable.toBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = modifier.size(size)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = null,
            tint = TurnawayPrimary,
            modifier = modifier.size(size)
        )
    }
}
