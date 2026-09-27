package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RoutePilotLightColorScheme = lightColorScheme(
    primary = RoutePilotBlue,
    onPrimary = Color.White,
    primaryContainer = RoutePilotBlueLight,
    onPrimaryContainer = RoutePilotNavy,
    secondary = SafeRouteGreen,
    onSecondary = Color.White,
    secondaryContainer = SafeRouteGreenLight,
    onSecondaryContainer = SafeRouteGreen,
    tertiary = HazardOrange,
    error = HazardRed,
    onError = Color.White,
    errorContainer = HazardRedLight,
    onErrorContainer = HazardRed,
    background = SurfaceBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEEF2F8),
    onSurfaceVariant = TextSecondary,
    outline = DividerSubtle
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RoutePilotLightColorScheme,
        typography = Typography,
        content = content
    )
}
