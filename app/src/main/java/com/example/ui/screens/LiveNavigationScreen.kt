package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.HazardStatus
import com.example.domain.model.LocationPoint
import com.example.domain.model.Route
import com.example.domain.routing.GeoUtils
import com.example.ui.components.RoutePilotMapView
import com.example.ui.theme.HazardOrange
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.RoutePilotBlueLight
import com.example.ui.theme.RoutePilotNavy
import com.example.ui.theme.SafeRouteGreen
import com.example.ui.viewmodel.NavigationWorkflowState

/**
 * Covers SCREENS 6, 7, 8, and 9 in pure Live Mode:
 * - Screen 6: Live Driving / Navigation (Dark Navy Turn Banner + Map + Remaining/ETA/End Card)
 * - Screen 7: Hazard Alert During Driving (Red Hazard Banner + Hazard Card showing Name, Hazard Type, Severity, Distance Ahead, and Road Status)
 * - Screen 8: Recalculating Route (Clean full-screen Car/Route Refresh + Progress Bar + Cancel)
 * - Screen 9: New Safer Route Shown (Green "Route Updated" Banner + Green Polyline + Remaining/ETA/End Card)
 */
@Composable
fun LiveNavigationScreen(
    workflowState: NavigationWorkflowState,
    currentLocation: LocationPoint,
    destination: Destination,
    activeRoute: Route?,
    previousRouteBeforeDiversion: Route?,
    activeHazards: List<Hazard>,
    primaryAffectingHazard: Hazard?,
    remainingDistanceMeters: Double,
    remainingEtaMinutes: Int,
    currentTurnDistanceMeters: Double,
    currentTurnInstruction: String,
    currentTurnManeuver: String,
    recalculationProgress: Float,
    recalculationErrorMessage: String?,
    isMapsApiKeyConfigured: Boolean,
    hasLocationPermission: Boolean,
    useKilometers: Boolean,
    isVoiceMuted: Boolean,
    onToggleVoiceMute: () -> Unit,
    onDismissHazardAlert: () -> Unit,
    onTriggerRerouteNow: () -> Unit,
    onCancelRecalculation: () -> Unit,
    onEndNavigation: () -> Unit
) {
    AnimatedContent(
        targetState = workflowState == NavigationWorkflowState.RECALCULATING,
        label = "nav_recalculating_transition"
    ) { isRecalculatingScreen ->
        if (isRecalculatingScreen) {
            RecalculatingRouteView(
                progress = recalculationProgress,
                errorMessage = recalculationErrorMessage,
                onCancel = onCancelRecalculation,
                onTryAgain = onTriggerRerouteNow,
                onEndNavigation = onEndNavigation
            )
        } else {
            val isHazardDetected = workflowState == NavigationWorkflowState.HAZARD_DETECTED
            val isSaferRouteUpdated = workflowState == NavigationWorkflowState.ROUTE_UPDATED

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("live_navigation_screen")
            ) {
                RoutePilotMapView(
                    currentLocation = currentLocation,
                    destination = destination,
                    primaryRoute = activeRoute,
                    secondaryRoute = if (isSaferRouteUpdated) previousRouteBeforeDiversion else null,
                    hazards = activeHazards,
                    isNavigationMode = true,
                    isSaferGreenRoute = isSaferRouteUpdated,
                    isMapsApiKeyConfigured = isMapsApiKeyConfigured,
                    hasLocationPermission = hasLocationPermission,
                    isVoiceMuted = isVoiceMuted,
                    showNavigationControls = true,
                    onToggleVoiceMute = onToggleVoiceMute,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Banner Overlay (Compact so the live driving map remains maximally visible)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    when {
                        isHazardDetected -> {
                            val hzName = primaryAffectingHazard?.name ?: "Road / Bridge Hazard"
                            val hzType = primaryAffectingHazard?.type?.displayName ?: "Critical Hazard"
                            HazardDetectedTopBanner(
                                subtitle = "$hzName • $hzType"
                            )
                        }

                        isSaferRouteUpdated -> {
                            RouteUpdatedTopBanner()
                        }

                        else -> {
                            TurnInstructionTopBanner(
                                distanceText = GeoUtils.formatDistance(currentTurnDistanceMeters, useKilometers),
                                instructionText = currentTurnInstruction,
                                maneuver = currentTurnManeuver
                            )
                        }
                    }
                }

                // Bottom Card Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (isHazardDetected) {
                        HazardAlertBottomCard(
                            hazard = primaryAffectingHazard,
                            useKilometers = useKilometers,
                            onDismiss = onDismissHazardAlert,
                            onFindSaferRoute = onTriggerRerouteNow
                        )
                    } else {
                        NavigationBottomSummaryCard(
                            remainingDistanceText = GeoUtils.formatDistance(remainingDistanceMeters, useKilometers),
                            etaText = "$remainingEtaMinutes min",
                            onEndNavigation = onEndNavigation
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnInstructionTopBanner(
    distanceText: String,
    instructionText: String,
    maneuver: String
) {
    val turnIcon = when (maneuver.uppercase()) {
        "LEFT" -> Icons.Default.TurnLeft
        "RIGHT" -> Icons.Default.TurnRight
        else -> Icons.Default.Straight
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RoutePilotNavy.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("turn_instruction_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = turnIcon,
                contentDescription = maneuver,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    text = instructionText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFE2E8F0),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HazardDetectedTopBanner(
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = HazardRed),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hazard_detected_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(R.string.hazard_detected_ahead),
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.hazard_detected_ahead),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White.copy(alpha = 0.94f),
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun RouteUpdatedTopBanner() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SafeRouteGreen),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("route_updated_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.banner_route_updated_title),
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.banner_route_updated_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    text = stringResource(R.string.banner_route_updated_subtitle),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White.copy(alpha = 0.94f),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

/**
 * SCREEN 7 BOTTOM CARD — Displays Hazard Name, Hazard Type badge, Severity badge,
 * Distance Ahead, Road Status, and optional Admin description.
 */
@Composable
private fun HazardAlertBottomCard(
    hazard: Hazard?,
    useKilometers: Boolean,
    onDismiss: () -> Unit,
    onFindSaferRoute: () -> Unit
) {
    val hazardName = hazard?.name ?: "Bridge B1"
    val hazardTypeDisplay = hazard?.type?.displayName ?: "Bridge Structural Damage"
    val severityLabel = when (hazard?.severity) {
        HazardSeverity.CRITICAL, null -> stringResource(R.string.severity_critical)
        HazardSeverity.HIGH -> stringResource(R.string.severity_high)
        HazardSeverity.MEDIUM -> stringResource(R.string.severity_medium)
        HazardSeverity.LOW -> stringResource(R.string.severity_low)
    }
    val severityBadgeColor = when (hazard?.severity) {
        HazardSeverity.CRITICAL, HazardSeverity.HIGH, null -> HazardRed
        HazardSeverity.MEDIUM -> HazardOrange
        HazardSeverity.LOW -> Color(0xFFFBC02D)
    }
    val distMeters = hazard?.distanceAheadMeters ?: 2100.0
    val distanceAheadText = stringResource(
        R.string.distance_ahead_format,
        GeoUtils.formatDistance(distMeters, useKilometers)
    )
    val statusText = when (hazard?.status) {
        HazardStatus.BLOCKED, null -> stringResource(R.string.status_road_blocked)
        HazardStatus.PARTIALLY_BLOCKED -> stringResource(R.string.status_partially_blocked)
        HazardStatus.WARNING -> stringResource(R.string.status_warning)
        HazardStatus.CLEARED -> stringResource(R.string.status_cleared)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFindSaferRoute() }
            .testTag("hazard_alert_bottom_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_bridge_hazard),
                    contentDescription = hazardName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = hazardName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_dismiss),
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Severity Badge + Hazard Type Badge
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = severityBadgeColor
                        ) {
                            Text(
                                text = severityLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RoutePilotBlueLight
                        ) {
                            Text(
                                text = hazardTypeDisplay,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = RoutePilotBlue,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = distanceAheadText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF334155)
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                    )

                    if (!hazard?.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = hazard?.description.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF64748B),
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationBottomSummaryCard(
    remainingDistanceText: String,
    etaText: String,
    onEndNavigation: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("navigation_bottom_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = remainingDistanceText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                )
                Text(
                    text = stringResource(R.string.label_remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64748B)
                )
            }

            Column {
                Text(
                    text = etaText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                )
                Text(
                    text = stringResource(R.string.label_eta),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64748B)
                )
            }

            Button(
                onClick = onEndNavigation,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HazardRed),
                modifier = Modifier
                    .height(42.dp)
                    .width(86.dp)
                    .testTag("end_navigation_button")
            ) {
                Text(
                    text = stringResource(R.string.action_end_navigation),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun RecalculatingRouteView(
    progress: Float,
    errorMessage: String?,
    onCancel: () -> Unit,
    onTryAgain: () -> Unit,
    onEndNavigation: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recalculating_route_screen"),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(164.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = RoutePilotBlueLight,
                            radius = size.minDimension * 0.46f
                        )
                        drawArc(
                            color = RoutePilotBlue.copy(alpha = 0.55f),
                            startAngle = -35f,
                            sweepAngle = 140f,
                            useCenter = false,
                            style = Stroke(width = 10f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = RoutePilotBlue,
                            startAngle = 145f,
                            sweepAngle = 140f,
                            useCenter = false,
                            style = Stroke(width = 10f, cap = StrokeCap.Round)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Recalculating Route",
                        tint = RoutePilotNavy,
                        modifier = Modifier.size(68.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = errorMessage ?: stringResource(R.string.title_recalculating_route),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.subtitle_recalculating_route),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (errorMessage == null) {
                    if (progress > 0f) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0.1f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = RoutePilotBlue,
                            trackColor = Color(0xFFE2E8F0)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = RoutePilotBlue,
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onTryAgain,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_try_again),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onEndNavigation,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_end_navigation),
                            color = HazardRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Button(
                    onClick = onCancel,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFE4E6),
                        contentColor = HazardRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("cancel_recalculation_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
