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
    onStartDriving: () -> Unit,
    onBack: () -> Unit
) {
    var isMapExpanded by remember { mutableStateOf(false) }

    val activeRoute = if (isUsingAlternate && alternateRoute != null) alternateRoute else recommendedRoute
    val recDistText = recommendedRoute?.let { GeoUtils.formatDistance(it.totalDistanceMeters, useKilometers) } ?: "18.4 km"
    val recTimeText = recommendedRoute?.let { "${it.durationMinutes} min" } ?: "32 min"
    val altDistText = alternateRoute?.let { GeoUtils.formatDistance(it.totalDistanceMeters, useKilometers) } ?: "21.7 km"
    val altTimeText = alternateRoute?.let { "${it.durationMinutes} min" } ?: "36 min"

    val selectedDistText = if (isUsingAlternate) altDistText else recDistText
    val selectedTimeText = if (isUsingAlternate) altTimeText else recTimeText
    val roadConditionText = activeRoute?.roadConditionSummary ?: stringResource(R.string.condition_mostly_good)

    val avoidedOrNearbyHazard = activeHazards.firstOrNull { hazard ->
        hazard.isEffectiveHazard && (activeRoute?.avoidedHazardIds?.contains(hazard.id) == true)
    } ?: activeHazards.firstOrNull { it.isEffectiveHazard }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
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

        // If an active hazard from Admin Panel / Backend is present on the corridor, show compact Type & Severity Banner
        if (avoidedOrNearbyHazard != null && !isMapExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            val isCritical = avoidedOrNearbyHazard.severity == HazardSeverity.CRITICAL ||
                avoidedOrNearbyHazard.severity == HazardSeverity.HIGH
            val bannerBg = if (isCritical) Color(0xFFFFEBEE) else Color(0xFFFFF3E0)
            val accentColor = if (isCritical) HazardRed else HazardOrange
            val statusLabel = when (avoidedOrNearbyHazard.status) {
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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${avoidedOrNearbyHazard.name} • ${avoidedOrNearbyHazard.type.displayName}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${avoidedOrNearbyHazard.severity.name} — $statusLabel (Safer route highlighted)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = Color(0xFF334155)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Large Expansive Google Map Preview Box (Takes up ~70-80% of the screen!)
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
                    secondaryRoute = if (isUsingAlternate) recommendedRoute else alternateRoute,
                    hazards = activeHazards,
                    isNavigationMode = false,
                    isMapsApiKeyConfigured = isMapsApiKeyConfigured,
                    hasLocationPermission = hasLocationPermission,
                    showNavigationControls = false,
                    onSelectAlternateRoute = { onSelectRouteOption(!isUsingAlternate) },
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
            // Side-by-Side Recommended vs Alternate Route Cards (Compact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteOptionCard(
                    label = stringResource(R.string.label_recommended_route),
                    distanceText = recDistText,
                    durationText = recTimeText,
                    isSelected = !isUsingAlternate,
                    onClick = { onSelectRouteOption(false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("route_option_recommended")
                )

                RouteOptionCard(
                    label = stringResource(R.string.label_alternate_route),
                    distanceText = altDistText,
                    durationText = altTimeText,
                    isSelected = isUsingAlternate,
                    onClick = { onSelectRouteOption(true) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("route_option_alternate")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            tint = RoutePilotBlue,
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
                            tint = RoutePilotBlue,
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
}

@Composable
private fun RouteOptionCard(
    label: String,
    distanceText: String,
    durationText: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) RoutePilotBlueLight else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) RoutePilotBlue else Color(0xFFD8E0EC)
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = if (isSelected) RoutePilotBlue else Color(0xFF64748B),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                )
                Text(
                    text = "• $durationText",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}
