package com.example.turnaway.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = TurnawayPrimary,
    onPrimary = TurnawayOnPrimary,
    primaryContainer = TurnawayPrimaryContainer,
    onPrimaryContainer = TurnawayOnPrimaryContainer,
    inversePrimary = TurnawayInversePrimary,
    secondary = TurnawaySecondary,
    onSecondary = TurnawayOnSecondary,
    secondaryContainer = TurnawaySecondaryContainer,
    onSecondaryContainer = TurnawayOnSecondaryContainer,
    tertiary = TurnawayTertiary,
    onTertiary = TurnawayOnTertiary,
    tertiaryContainer = TurnawayTertiaryContainer,
    onTertiaryContainer = TurnawayOnTertiaryContainer,
    background = TurnawayBackground,
    onBackground = TurnawayOnBackground,
    surface = TurnawaySurface,
    onSurface = TurnawayOnSurface,
    surfaceVariant = TurnawaySurfaceVariant,
    onSurfaceVariant = TurnawayOnSurfaceVariant,
    surfaceContainerLowest = TurnawaySurfaceContainerLowest,
    surfaceContainerLow = TurnawaySurfaceContainerLow,
    surfaceContainer = TurnawaySurfaceContainer,
    surfaceContainerHigh = TurnawaySurfaceContainerHigh,
    surfaceContainerHighest = TurnawaySurfaceContainerHighest,
    outline = TurnawayOutline,
    outlineVariant = TurnawayOutlineVariant,
    error = TurnawayError,
    onError = TurnawayOnError,
    errorContainer = TurnawayErrorContainer,
    onErrorContainer = TurnawayOnErrorContainer,
    inverseSurface = TurnawayInverseSurface,
    inverseOnSurface = TurnawayInverseOnSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = TurnawayInversePrimary,
    onPrimary = TurnawayPrimary,
    primaryContainer = TurnawayPrimaryContainer,
    onPrimaryContainer = TurnawayPrimaryFixedDim,
    inversePrimary = TurnawayPrimary,
    secondary = TurnawaySecondaryFixedDim,
    onSecondary = TurnawayOnSecondaryFixed,
    secondaryContainer = Color(0xFF1E3A39),
    onSecondaryContainer = TurnawaySecondaryFixed,
    tertiary = TurnawayTertiaryFixedDim,
    onTertiary = TurnawayOnTertiaryFixed,
    tertiaryContainer = TurnawayTertiaryContainer,
    onTertiaryContainer = TurnawayTertiaryFixed,
    background = Color(0xFF0F141C),
    onBackground = Color(0xFFE2E6F0),
    surface = Color(0xFF0F141C),
    onSurface = Color(0xFFE2E6F0),
    surfaceVariant = Color(0xFF232A38),
    onSurfaceVariant = Color(0xFFA5ABC0),
    surfaceContainerLowest = Color(0xFF0A0E15),
    surfaceContainerLow = Color(0xFF141924),
    surfaceContainer = Color(0xFF1A212E),
    surfaceContainerHigh = Color(0xFF222938),
    surfaceContainerHighest = Color(0xFF2C3446),
    outline = Color(0xFF888E9E),
    outlineVariant = Color(0xFF3E4556),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE2E6F0),
    inverseOnSurface = Color(0xFF161C26)
)

@Composable
fun TurnAwayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve crafted brand palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}