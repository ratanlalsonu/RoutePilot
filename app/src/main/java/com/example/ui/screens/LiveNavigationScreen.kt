package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Path
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
import kotlin.math.max

/**
 * Covers SCREENS 6, 7, 8, and 9 matching the reference UI:
 * - Screen 6: Live Driving / Navigation (Dark Navy Turn Banner + Map + Remaining/ETA/End Card)
 * - Screen 7: Hazard Alert (During Driving) ("Hazard Detected Ahead!", "Bridge B1 - Critical Hazard",
 *   Red road segment & Red warning triangle on map, and bottom Bridge B1 card with thumbnail, Critical pill,
 *   2.1 km ahead, Road Blocked)
 * - Screen 8: Recalculating Route ("Recalculating Route...", "Finding a safer and optimal path for you",
 *   Blue Car with circular arrows, progress bar, and Cancel button)
 * - Screen 9: New Route Shown ("Route Updated", "A safer route has been selected.", Green safer route on map
 *   with avoided hazard branch, and Remaining / ETA / End summary card)
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
    remainingEtaSeconds: Int = remainingEtaMinutes * 60,
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
                    remainingDistanceMeters = remainingDistanceMeters,
                    remainingEtaSeconds = remainingEtaSeconds,
                    isNavigationMode = true,
                    isSaferGreenRoute = isSaferRouteUpdated || (activeRoute?.isDivertedForSafety == true),
                    isMapsApiKeyConfigured = isMapsApiKeyConfigured,
                    hasLocationPermission = hasLocationPermission,
                    isVoiceMuted = isVoiceMuted,
                    showNavigationControls = true,
                    onToggleVoiceMute = onToggleVoiceMute,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Banner Overlay (Screen 7 Red Hazard Banner / Screen 9 Green Route Updated Banner / Screen 6 Turn Banner)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    when {
                        isHazardDetected -> {
                            val hzName = primaryAffectingHazard?.name ?: "Bridge B1"
                            val severityWord = when (primaryAffectingHazard?.severity) {
                                HazardSeverity.CRITICAL, null -> "Critical"
                                HazardSeverity.HIGH -> "High"
                                HazardSeverity.MEDIUM -> "Moderate"
                                HazardSeverity.LOW -> "Low"
                            }
                            HazardDetectedTopBanner(
                                subtitle = "$hzName - $severityWord Hazard",
                                onClick = onTriggerRerouteNow
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

                // Bottom Card Overlay (Screen 7 Bridge B1 Hazard Card / Screen 9 Remaining & ETA Card)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (isHazardDetected) {
                        HazardAlertBottomCard(
                            hazard = primaryAffectingHazard,
                            useKilometers = useKilometers,
                            onDismiss = onDismissHazardAlert,
                            onFindSaferRoute = onTriggerRerouteNow
                        )
                    } else {
                        val displayEtaMinutes = max(1, (remainingEtaSeconds + 30) / 60)
                        NavigationBottomSummaryCard(
                            remainingDistanceText = GeoUtils.formatDistance(remainingDistanceMeters, useKilometers),
                            remainingSubtitleText = stringResource(R.string.label_remaining),
                            etaText = "$displayEtaMinutes min",
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RoutePilotNavy.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("turn_instruction_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = turnIcon,
                contentDescription = maneuver,
                tint = Color.White,
                modifier = Modifier.size(34.dp)
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

/**
 * SCREEN 7 TOP RED BANNER:
 * Matches "Hazard Detected Ahead!" / "Bridge B1 - Critical Hazard" with white warning triangle icon.
 */
@Composable
private fun HazardDetectedTopBanner(
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEA2828)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("hazard_detected_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(R.string.hazard_detected_ahead),
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.hazard_detected_ahead),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White.copy(alpha = 0.96f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                )
            }
        }
    }
}

/**
 * SCREEN 9 TOP GREEN BANNER:
 * Matches "Route Updated" / "A safer route has been selected." with white circle & green checkmark.
 */
@Composable
private fun RouteUpdatedTopBanner() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E8E3E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("route_updated_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.banner_route_updated_title),
                    tint = Color(0xFF1E8E3E),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.banner_route_updated_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.banner_route_updated_subtitle),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White.copy(alpha = 0.96f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                )
            }
        }
    }
}

/**
 * SCREEN 7 BOTTOM CARD — Matches the reference screenshot:
 * - Left: Rounded square Bridge B1 photo (`R.drawable.img_bridge_hazard`)
 * - Middle:
 *   - Bold title: "Bridge B1"
 *   - Red pill badge: "Critical"
 *   - Distance text: "2.1 km ahead"
 *   - Bold status text: "Road Blocked"
 * - Top-right: `X` close icon
 * - Tapping the card or the action button triggers Screen 8 (Recalculating Route).
 */
@Composable
private fun HazardAlertBottomCard(
    hazard: Hazard?,
    useKilometers: Boolean,
    onDismiss: () -> Unit,
    onFindSaferRoute: () -> Unit
) {
    val hazardName = hazard?.name ?: "Bridge B1"
    val severityLabel = when (hazard?.severity) {
        HazardSeverity.CRITICAL, null -> stringResource(R.string.severity_critical)
        HazardSeverity.HIGH -> stringResource(R.string.severity_high)
        HazardSeverity.MEDIUM -> stringResource(R.string.severity_medium)
        HazardSeverity.LOW -> stringResource(R.string.severity_low)
    }
    val severityBadgeColor = when (hazard?.severity) {
        HazardSeverity.CRITICAL, HazardSeverity.HIGH, null -> Color(0xFFEA2828)
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
                        .size(94.dp)
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
                                color = Color(0xFF0F172A),
                                fontSize = 19.sp
                            )
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("dismiss_hazard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_dismiss),
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = severityBadgeColor
                    ) {
                        Text(
                            text = severityLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = distanceAheadText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * SCREEN 9 BOTTOM SUMMARY CARD — Matches "17.6 km Remaining | 30 min ETA | End"
 */
@Composable
private fun NavigationBottomSummaryCard(
    remainingDistanceText: String,
    remainingSubtitleText: String,
    etaText: String,
    onEndNavigation: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("navigation_bottom_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = remainingDistanceText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        fontSize = 20.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = remainingSubtitleText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF475569),
                        fontSize = 13.sp
                    )
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = etaText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        fontSize = 20.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.label_eta),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF475569),
                        fontSize = 13.sp
                    )
                )
            }

            Button(
                onClick = onEndNavigation,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA2828)),
                modifier = Modifier
                    .height(48.dp)
                    .width(92.dp)
                    .testTag("end_navigation_button")
            ) {
                Text(
                    text = stringResource(R.string.action_end_navigation),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/**
 * SCREEN 8 — RECALCULATING ROUTE VIEW
 * Matches "8. Recalculating Route" in the reference design:
 * - Soft blue circle with circular refresh arrows around a dark blue car icon
 * - "Recalculating Route..."
 * - "Finding a safer and optimal path for you"
 * - Blue progress bar
 * - Soft light-red "Cancel" button
 */
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
        color = Color(0xFFF8FAFC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(172.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension * 0.44f
                        drawCircle(
                            color = Color(0xFFE0ECFF),
                            radius = r
                        )
                        drawArc(
                            color = Color(0xFF60A5FA),
                            startAngle = -145f,
                            sweepAngle = 135f,
                            useCenter = false,
                            style = Stroke(width = 14f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color(0xFF60A5FA),
                            startAngle = 35f,
                            sweepAngle = 135f,
                            useCenter = false,
                            style = Stroke(width = 14f, cap = StrokeCap.Round)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Recalculating Route",
                        tint = Color(0xFF0A2E5C),
                        modifier = Modifier.size(76.dp)
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = errorMessage ?: stringResource(R.string.title_recalculating_route),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        fontSize = 24.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.subtitle_recalculating_route),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color(0xFF334155),
                        fontSize = 16.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(30.dp))

                if (errorMessage == null) {
                    LinearProgressIndicator(
                        progress = { if (progress > 0f) progress.coerceIn(0.15f, 1f) else 0.58f },
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Color(0xFF1A73E8),
                        trackColor = Color(0xFFE2E8F0)
                    )
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
                        containerColor = Color(0xFFFDE2E2),
                        contentColor = Color(0xFFDC2626)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("cancel_recalculation_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }
        }
    }
}
