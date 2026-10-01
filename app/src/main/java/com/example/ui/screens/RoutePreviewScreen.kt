package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.theme.SafeRouteGreen
import com.example.ui.theme.SurfaceBackground

/**
 * SCREEN 5 — ROUTE PREVIEW SCREEN
 * Features a large, expansive Google Map preview (~70% of screen height + Full Map expand option)
 * with Satellite Mode toggle, highlighted Best & Alternate routes directly on actual map roads,
 * active backend Hazard Type & Severity indicator banner, and "Start Driving" action.
 */
@Composable
fun RoutePreviewScreen(
    currentLocation: LocationPoint,
    destination: Destination,
    recommendedRoute: Route?,
    alternateRoute: Route?,
    isUsingAlternate: Boolean,
    activeHazards: List<Hazard>,
    isMapsApiKeyConfigured: Boolean,
    hasLocationPermission: Boolean,
    useKilometers: Boolean,
    onSelectRouteOption: (useAlternate: Boolean) -> Unit,
    onChooseOtherPath: () -> Unit = { onSelectRouteOption(true) },
    onStartDriving: () -> Unit,
    onBack: () -> Unit
) {
    var isMapExpanded by remember { mutableStateOf(false) }
    var dismissedPreviewHazardKey by remember { mutableStateOf<String?>(null) }

    val activeRoute = recommendedRoute
    val selectedDistText = activeRoute?.let { GeoUtils.formatDistance(it.totalDistanceMeters, useKilometers) } ?: "18.4 km"
    val selectedTimeText = activeRoute?.let { "${it.durationMinutes} min" } ?: "32 min"
    val roadConditionText = activeRoute?.roadConditionSummary ?: stringResource(R.string.condition_mostly_good)

    val hazardOnCurrentPath = activeHazards.firstOrNull { it.isEffectiveHazard }
    val isAlreadyDiverted = activeRoute?.isDivertedForSafety == true
    val currentHazardKey = hazardOnCurrentPath?.let { "${it.id}_${it.status}_${it.severity}" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp)
        ) {
        // Compact Header + Origin -> Destination Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("route_preview_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF0F172A)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Compact Origin -> Destination Summary Card in Header
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(SafeRouteGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Your Location",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "  →  ",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = RoutePilotBlue,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = HazardRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = destination.name,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // If an active hazard from Admin Panel / Backend is present on the corridor, indicate hazard & offer "Choose Other Path"
        if (hazardOnCurrentPath != null && !isMapExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            val isCritical = hazardOnCurrentPath.severity == HazardSeverity.CRITICAL ||
                hazardOnCurrentPath.severity == HazardSeverity.HIGH
            val bannerBg = if (isAlreadyDiverted) Color(0xFFDCFCE7) else if (isCritical) Color(0xFFFFEBEE) else Color(0xFFFFF3E0)
            val accentColor = if (isAlreadyDiverted) SafeRouteGreen else if (isCritical) HazardRed else HazardOrange
            val statusLabel = when (hazardOnCurrentPath.status) {
                HazardStatus.BLOCKED -> stringResource(R.string.status_road_blocked)
                HazardStatus.PARTIALLY_BLOCKED -> stringResource(R.string.status_partially_blocked)
                else -> stringResource(R.string.status_warning)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bannerBg,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("route_preview_hazard_indicator")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAlreadyDiverted) Icons.Default.VerifiedUser else Icons.Default.Warning,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAlreadyDiverted) {
                                "New Path Highlighted (Hazard Avoided)"
                            } else {
                                "Hazard on Path: ${hazardOnCurrentPath.name} • ${hazardOnCurrentPath.type.displayName}"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isAlreadyDiverted) {
                                "Safer road route is now highlighted on map"
                            } else {
                                "${hazardOnCurrentPath.severity.name} — $statusLabel on current path"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = Color(0xFF334155)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isAlreadyDiverted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onChooseOtherPath,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("preview_choose_other_path_button")
                        ) {
                            Text(
                                text = stringResource(R.string.action_reroute_now),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Large Expansive Google Map Preview Box (Single active route highlighted on exact road)
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                RoutePilotMapView(
                    currentLocation = currentLocation,
                    destination = destination,
                    primaryRoute = activeRoute,
                    secondaryRoute = null,
                    hazards = activeHazards,
                    isNavigationMode = false,
                    isSaferGreenRoute = isAlreadyDiverted,
                    isMapsApiKeyConfigured = isMapsApiKeyConfigured,
                    hasLocationPermission = hasLocationPermission,
                    showNavigationControls = false,
                    onSelectAlternateRoute = null,
                    modifier = Modifier.fillMaxSize()
                )

                // Expand / Full Map Toggle Button on Top-End of Map
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.94f),
                    shadowElevation = 5.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .clickable { isMapExpanded = !isMapExpanded }
                        .testTag("toggle_expand_preview_map")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMapExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Full Map",
                            tint = RoutePilotBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isMapExpanded) "Compact" else "Full Map",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!isMapExpanded) {
            // Compact Single-Row Summary Bar (Distance • ETA • Road Condition)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            tint = if (isAlreadyDiverted) SafeRouteGreen else RoutePilotBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedDistText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = if (isAlreadyDiverted) SafeRouteGreen else RoutePilotBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedTimeText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = SafeRouteGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = roadConditionText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SafeRouteGreen
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onStartDriving,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("start_driving_button")
        ) {
            Text(
                text = stringResource(R.string.action_start_driving),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        }

        // Popup asking user whether to Find Other Route or Cancel when Admin generates a hazard
        if (hazardOnCurrentPath != null && !isAlreadyDiverted && currentHazardKey != dismissedPreviewHazardKey) {
            val statusLabel = when (hazardOnCurrentPath.status) {
                HazardStatus.BLOCKED -> stringResource(R.string.status_road_blocked)
                HazardStatus.PARTIALLY_BLOCKED -> stringResource(R.string.status_partially_blocked)
                else -> stringResource(R.string.status_warning)
            }
            val descText = hazardOnCurrentPath.description.ifBlank {
                "Hazard reported ahead on ${hazardOnCurrentPath.name} ($statusLabel). Continuing on this route may be unsafe."
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preview_hazard_popup_card")
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = HazardRed,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hazard Detected on Route!",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = HazardRed,
                                        fontSize = 18.sp
                                    )
                                )
                                Text(
                                    text = "${hazardOnCurrentPath.name} • ${hazardOnCurrentPath.type.displayName}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = HazardRed
                            ) {
                                Text(
                                    text = hazardOnCurrentPath.severity.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFFFEBEE)
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = HazardRed,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFFF5F5),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Hazard Description:",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF991B1B)
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = descText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF1E293B),
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Would you like to find another safer route to avoid this hazard?",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clickable { dismissedPreviewHazardKey = currentHazardKey }
                                    .testTag("preview_popup_cancel_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.action_cancel),
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    dismissedPreviewHazardKey = currentHazardKey
                                    onChooseOtherPath()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(46.dp)
                                    .testTag("preview_popup_find_route_button")
                            ) {
                                Text(
                                    text = "Find Other Route",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
