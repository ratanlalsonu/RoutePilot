package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.OperatingMode
import com.example.ui.theme.HazardOrange
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.RoutePilotBlueLight
import com.example.ui.theme.RoutePilotNavy
import com.example.ui.theme.SafeRouteGreen

/**
 * Renders the signature RoutePilot Location-Pin + S-Curve Road emblem from Screens 1 & 2.
 */
@Composable
fun RoutePilotBrandLogo(
    modifier: Modifier = Modifier,
    iconSize: Dp = 76.dp,
    showTagline: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val w = size.width
            val h = size.height

            // Outer blue location pin circle
            drawCircle(
                color = Color(0xFF1363DF),
                radius = w * 0.30f,
                center = Offset(w * 0.50f, h * 0.34f)
            )
            // Inner white hole of location pin
            drawCircle(
                color = Color.White,
                radius = w * 0.13f,
                center = Offset(w * 0.50f, h * 0.34f)
            )

            // Dark navy + blue sweeping S-road base
            val roadPath = Path().apply {
                moveTo(w * 0.22f, h * 0.88f)
                cubicTo(
                    w * 0.30f, h * 0.68f,
                    w * 0.72f, h * 0.72f,
                    w * 0.56f, h * 0.54f
                )
                lineTo(w * 0.74f, h * 0.56f)
                cubicTo(
                    w * 0.88f, h * 0.76f,
                    w * 0.52f, h * 0.78f,
                    w * 0.45f, h * 0.94f
                )
                close()
            }
            drawPath(path = roadPath, color = RoutePilotNavy)

            // Center white lane stripe
            val centerStripe = Path().apply {
                moveTo(w * 0.32f, h * 0.89f)
                cubicTo(
                    w * 0.42f, h * 0.73f,
                    w * 0.75f, h * 0.74f,
                    w * 0.63f, h * 0.56f
                )
            }
            drawPath(
                path = centerStripe,
                color = Color.White,
                style = Stroke(width = w * 0.035f, cap = StrokeCap.Round)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = RoutePilotNavy,
                letterSpacing = (-0.5).sp
            )
        )

        if (showTagline) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Safer Roads\nSmarter Journeys",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color(0xFF334155),
                    fontWeight = FontWeight.Medium
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Interactive pill badge showing whether the app is in LIVE MODE or DEMO MODE.
 */
@Composable
fun OperatingModeBadge(
    mode: OperatingMode,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDemo = mode == OperatingMode.DEMO
    val bgColor = if (isDemo) Color(0xFFFFF3E0) else Color(0xFFE8F8EE)
    val borderColor = if (isDemo) HazardOrange else SafeRouteGreen
    val textColor = if (isDemo) Color(0xFFD84315) else SafeRouteGreen
    val label = if (isDemo) stringResource(R.string.mode_demo) else stringResource(R.string.mode_live)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(50))
            .clickable(onClick = onToggleMode)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("operating_mode_badge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(borderColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
fun RoutePilotBottomBar(
    currentRoute: String,
    onNavigateTab: () -> Unit,
    onHistoryTab: () -> Unit,
    onSettingsTab: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = Color.White
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            containerColor = Color.White
        ) {
            NavigationBarItem(
                selected = currentRoute == "home",
                onClick = onNavigateTab,
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "home") Icons.Filled.Navigation else Icons.Outlined.Navigation,
                        contentDescription = stringResource(R.string.nav_navigate)
                    )
                },
                label = { Text(stringResource(R.string.nav_navigate)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RoutePilotBlue,
                    selectedTextColor = RoutePilotBlue,
                    indicatorColor = RoutePilotBlueLight
                ),
                modifier = Modifier.testTag("nav_tab_navigate")
            )

            NavigationBarItem(
                selected = currentRoute == "history",
                onClick = onHistoryTab,
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "history") Icons.Filled.History else Icons.Outlined.History,
                        contentDescription = stringResource(R.string.nav_history)
                    )
                },
                label = { Text(stringResource(R.string.nav_history)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RoutePilotBlue,
                    selectedTextColor = RoutePilotBlue,
                    indicatorColor = RoutePilotBlueLight
                ),
                modifier = Modifier.testTag("nav_tab_history")
            )

            NavigationBarItem(
                selected = currentRoute == "settings",
                onClick = onSettingsTab,
                icon = {
                    Icon(
                        imageVector = if (currentRoute == "settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.nav_settings)
                    )
                },
                label = { Text(stringResource(R.string.nav_settings)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RoutePilotBlue,
                    selectedTextColor = RoutePilotBlue,
                    indicatorColor = RoutePilotBlueLight
                ),
                modifier = Modifier.testTag("nav_tab_settings")
            )
        }
    }
}
