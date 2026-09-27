package com.example.ui.theme

import android.app.Activity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            WindowCompat.getInsetsController(window, view).apply {
                show(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = RoutePilotLightColorScheme,
        typography = Typography,
        content = content
    )
}
