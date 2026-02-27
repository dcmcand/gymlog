package com.gymlog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GymLogColorScheme = lightColorScheme(
    primary = GymGreen,
    onPrimary = Color.White,
    primaryContainer = GymGreenContainer,
    onPrimaryContainer = GymGreen,
    secondary = GymGreenLight,
    onSecondary = Color.White,
    secondaryContainer = GymGreenContainer,
    onSecondaryContainer = GymGreen,
    tertiary = SetHardColor,
    onTertiary = Color.White,
    error = SetFailedColor,
    onError = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF49454F)
)

@Composable
fun GymLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GymLogColorScheme,
        content = content
    )
}
