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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.LocationPoint
import com.example.domain.model.Route
import com.example.domain.routing.GeoUtils
import com.example.ui.components.RoutePilotMapView
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.RoutePilotBlueLight
import com.example.ui.theme.SafeRouteGreen
import com.example.ui.theme.SurfaceBackground

/**
 * SCREEN 5 — ROUTE PREVIEW SCREEN
 * Matches Screen 5 of the reference design:
 * - Origin (Your Location / Current Location) & Destination (District Hospital / Jhansi, UP)
 * - Side-by-side Route cards: Recommended (18.4 km, 32 min) | Alternate (21.7 km, 36 min)
 * - Map preview with route polyline
 * - Details list: Distance, Estimated Time, Road Condition (Mostly Good)
 * - "Start Driving" primary action button
 * - Does NOT expose internal algorithm details ("A*", "Dijkstra", "Graph Nodes")
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
    val activeRoute = if (isUsingAlternate && alternateRoute != null) alternateRoute else recommendedRoute
    val recDistText = recommendedRoute?.let { GeoUtils.formatDistance(it.totalDistanceMeters, useKilometers) } ?: "18.4 km"
    val recTimeText = recommendedRoute?.let { "${it.durationMinutes} min" } ?: "32 min"
    val altDistText = alternateRoute?.let { GeoUtils.formatDistance(it.totalDistanceMeters, useKilometers) } ?: "21.7 km"
    val altTimeText = alternateRoute?.let { "${it.durationMinutes} min" } ?: "36 min"

    val selectedDistText = if (isUsingAlternate) altDistText else recDistText
    val selectedTimeText = if (isUsingAlternate) altTimeText else recTimeText
    val roadConditionText = activeRoute?.roadConditionSummary ?: stringResource(R.string.condition_mostly_good)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("route_preview_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF0F172A)
                )
            }
            Text(
                text = stringResource(R.string.title_route_preview),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                ),
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Origin & Destination Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(SafeRouteGreen)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.label_your_location),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = stringResource(R.string.label_current_location),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = HazardRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = destination.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = destination.address,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = Color(0xFF64748B),
                                maxLines = 1
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Route Direction",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Side-by-Side Recommended vs Alternate Route Cards (matching Screen 5)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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

        Spacer(modifier = Modifier.height(10.dp))

        // Map Preview Box
        Card(
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            RoutePilotMapView(
                currentLocation = currentLocation,
                destination = destination,
                primaryRoute = activeRoute,
                secondaryRoute = if (isUsingAlternate) recommendedRoute else alternateRoute,
                hazards = activeHazards,
                isNavigationMode = false,
                isMapsApiKeyConfigured = isMapsApiKeyConfigured,
                hasLocationPermission = hasLocationPermission,
                showNavigationControls = false
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Route Summary Metrics Card (Distance, Estimated Time, Road Condition)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteDetailMetricRow(
                    icon = Icons.Default.Route,
                    title = stringResource(R.string.label_distance),
                    value = selectedDistText
                )
                RouteDetailMetricRow(
                    icon = Icons.Default.AccessTime,
                    title = stringResource(R.string.label_estimated_time),
                    value = selectedTimeText
                )
                RouteDetailMetricRow(
                    icon = Icons.Default.VerifiedUser,
                    title = stringResource(R.string.label_road_condition),
                    value = roadConditionText
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onStartDriving,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("start_driving_button")
        ) {
            Text(
                text = stringResource(R.string.action_start_driving),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
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
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) RoutePilotBlue else Color(0xFF64748B),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = distanceText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
            )
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF475569),
                    fontSize = 13.sp
                )
            )
        }
    }
}

@Composable
private fun RouteDetailMetricRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF475569),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = Color(0xFF64748B)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            )
        }
    }
}
