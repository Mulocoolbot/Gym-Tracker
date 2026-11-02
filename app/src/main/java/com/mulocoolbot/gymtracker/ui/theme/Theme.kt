package com.mulocoolbot.gymtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = VoltAccent,
    onPrimary = OnVolt,
    primaryContainer = CardSurface,
    onPrimaryContainer = VoltAccent,
    secondary = VoltAccent,
    onSecondary = OnVolt,
    secondaryContainer = InputSurface,
    onSecondaryContainer = TextPrimary,
    tertiary = SuccessGreen,
    onTertiary = OnVolt,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = InputSurface,
    onSurfaceVariant = TextMuted,
    outline = CardBorder,
    error = DangerRed,
    onError = TextPrimary
)

@Composable
fun GymTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = GymTypography,
        content = content
    )
}

