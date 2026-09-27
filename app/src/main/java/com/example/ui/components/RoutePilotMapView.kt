package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.LocationPoint
import com.example.domain.model.Route
import com.example.domain.routing.GeoUtils
import com.example.ui.theme.HazardOrange
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Directional arrow placement along the active road segment.
 */
private data class RoadGuidanceArrowSpec(
    val position: LocationPoint,
    val bearingDegrees: Float
)

/**
 * 100% Google Maps SDK Engine (`com.google.maps.android.compose.GoogleMap`) for RoutePilot:
 * - Highlights Best Route (Primary Blue / Safer Green) and Alternate Route directly on Google Maps roads
 * - Interactive Alternate Route selection (tap alternate route polyline or badge on map)
 * - Bespoke 3D Swept-Wing Navigation Arrow inside a frosted white puck
 * - White-with-navy-outline directional road guidance arrows (`══════►`) along the active road
 * - Route corridor pacing dots (`• • •`) and dotted approach connector with road anchor node
 * - Red-needle "N" Compass control
 */
private val sharedSatelliteModeState = mutableStateOf(false)

@Composable
fun RoutePilotMapView(
    currentLocation: LocationPoint,
    destination: Destination?,
    primaryRoute: Route?,
    secondaryRoute: Route? = null,
    hazards: List<Hazard> = emptyList(),
    isNavigationMode: Boolean = false,
    isSaferGreenRoute: Boolean = false,
    isMapsApiKeyConfigured: Boolean = true,
    hasLocationPermission: Boolean = false,
    isVoiceMuted: Boolean = false,
    showNavigationControls: Boolean = false,
    onToggleVoiceMute: (() -> Unit)? = null,
    onSelectAlternateRoute: (() -> Unit)? = null,
    onMapClick: ((Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var zoomLevel by remember(isNavigationMode) {
        mutableFloatStateOf(if (isNavigationMode) 16.5f else 13.8f)
    }
    var recenterTrigger by remember { mutableIntStateOf(0) }
    var isSatelliteMode by remember { sharedSatelliteModeState }

    val isGreenTheme = isSaferGreenRoute || (primaryRoute?.isDivertedForSafety == true)

    // Compute route heading bearing and road entry anchor point
    val nearestRouteAnchor = remember(currentLocation.latitude, currentLocation.longitude, primaryRoute) {
        findNearestRouteEntryPoint(currentLocation, primaryRoute)
    }

    val headingBearingDegrees = remember(
        currentLocation.latitude,
        currentLocation.longitude,
        primaryRoute,
        destination
    ) {
        computeRouteHeadingBearing(currentLocation, primaryRoute, destination)
    }

    // Compute interpolated points along the route for pacing dots and road direction arrows
    val routePacingPoints = remember(primaryRoute) {
        computeInterpolatedRoutePoints(primaryRoute, stepMeters = 320.0)
    }
    val roadGuidanceArrows = remember(currentLocation.latitude, currentLocation.longitude, primaryRoute) {
        computeRoadGuidanceArrows(currentLocation, primaryRoute)
    }
    val dottedApproachPoints = remember(currentLocation, nearestRouteAnchor) {
        computeDottedApproachCurve(currentLocation, nearestRouteAnchor)
    }

    // Route midpoint callout positions for Best & Alternate Routes
    val primaryMidpoint = remember(primaryRoute) {
        findRouteMidpoint(primaryRoute, fraction = 0.48)
    }
    val secondaryMidpoint = remember(secondaryRoute) {
        findRouteMidpoint(secondaryRoute, fraction = 0.56)
    }

    val driverMarkerBitmap = remember(isNavigationMode) {
        createProfessionalDriverMarkerBitmap(isNavigationMode)
    }
    val roadDirectionArrowBitmap = remember(isGreenTheme) {
        createRoadDirectionGuidanceArrowBitmap(isGreenTheme)
    }
    val pacingDotBitmap = remember(isGreenTheme) {
        createRoutePacingDotBitmap(isGreenTheme)
    }
    val roadAnchorNodeBitmap = remember {
        createRoadEntryAnchorBitmap()
    }
    val bestRouteCalloutBitmap = remember(primaryRoute?.id, primaryRoute?.durationMinutes, isGreenTheme) {
        primaryRoute?.let {
            createRouteCalloutBadgeBitmap(
                title = if (isGreenTheme) "Safer Route • ${it.durationMinutes} min" else "Best Route • ${it.durationMinutes} min",
                subtitle = String.format(java.util.Locale.US, "%.1f km", it.distanceKm),
                isPrimary = true,
                isGreenTheme = isGreenTheme
            )
        }
    }
    val altRouteCalloutBitmap = remember(secondaryRoute?.id, secondaryRoute?.durationMinutes) {
        secondaryRoute?.let {
            createRouteCalloutBadgeBitmap(
                title = "Alt • ${it.durationMinutes} min",
                subtitle = String.format(java.util.Locale.US, "%.1f km", it.distanceKm),
                isPrimary = false,
                isGreenTheme = false
            )
        }
    }
    val criticalHazardBitmap = remember {
        createHazardTriangleBitmap(HazardRed.toArgb())
    }
    val warningHazardBitmap = remember {
        createHazardTriangleBitmap(HazardOrange.toArgb())
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.Builder()
            .target(LatLng(currentLocation.latitude, currentLocation.longitude))
            .zoom(zoomLevel)
            .bearing(if (isNavigationMode) headingBearingDegrees else 0f)
            .tilt(if (isNavigationMode) 45f else 0f)
            .build()
    }

    LaunchedEffect(
        currentLocation.latitude,
        currentLocation.longitude,
        destination?.id,
        zoomLevel,
        recenterTrigger,
        isNavigationMode,
        headingBearingDegrees
    ) {
        runCatching {
            val targetLatLng = if (!isNavigationMode && destination != null && primaryRoute != null) {
                LatLng(
                    (currentLocation.latitude + destination.latitude) / 2.0,
                    (currentLocation.longitude + destination.longitude) / 2.0
                )
            } else {
                LatLng(currentLocation.latitude, currentLocation.longitude)
            }
            val camPos = CameraPosition.Builder()
                .target(targetLatLng)
                .zoom(zoomLevel)
                .bearing(if (isNavigationMode) headingBearingDegrees else 0f)
                .tilt(if (isNavigationMode) 45f else 0f)
                .build()
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(camPos)
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .testTag("google_map_sdk_view"),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = if (isSatelliteMode) MapType.HYBRID else MapType.NORMAL,
                isMyLocationEnabled = hasLocationPermission && !isNavigationMode,
                isTrafficEnabled = false,
                isBuildingEnabled = true
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false
            ),
            onMapClick = { latLng ->
                onMapClick?.invoke(latLng.latitude, latLng.longitude)
            }
        ) {
            // 1. Highlighted Alternate Route on Map Road (Outer Casing + Inner Slate-Blue Highlight)
            if (secondaryRoute != null && secondaryRoute.points.size >= 2) {
                val altPoints = secondaryRoute.points.map { LatLng(it.latitude, it.longitude) }
                Polyline(
                    points = altPoints,
                    color = Color(0xFF475569),
                    width = 22f,
                    zIndex = 1.0f,
                    clickable = onSelectAlternateRoute != null,
                    onClick = { onSelectAlternateRoute?.invoke() }
                )
                Polyline(
                    points = altPoints,
                    color = Color(0xFF94A3B8),
                    width = 14f,
                    zIndex = 1.2f,
                    clickable = onSelectAlternateRoute != null,
                    onClick = { onSelectAlternateRoute?.invoke() }
                )

                if (!isNavigationMode && secondaryMidpoint != null && altRouteCalloutBitmap != null) {
                    Marker(
                        state = MarkerState(
                            position = LatLng(secondaryMidpoint.latitude, secondaryMidpoint.longitude)
                        ),
                        anchor = Offset(0.5f, 1.0f),
                        zIndex = 5.5f,
                        icon = BitmapDescriptorFactory.fromBitmap(altRouteCalloutBitmap),
                        onClick = {
                            onSelectAlternateRoute?.invoke()
                            true
                        }
                    )
                }
            }

            // 2. Highlighted Best / Safer Active Route on Map Road (Glow + Casing + Core Highlight)
            if (primaryRoute != null && primaryRoute.points.size >= 2) {
                val routePoints = primaryRoute.points.map { LatLng(it.latitude, it.longitude) }
                val outerGlowColor = if (isGreenTheme) {
                    Color(0xFF22C55E).copy(alpha = 0.28f)
                } else {
                    Color(0xFF3B82F6).copy(alpha = 0.28f)
                }
                val outerCasingColor = if (isGreenTheme) Color(0xFF0F5132) else Color(0xFF1E3A8A)
                val innerBandColor = if (isGreenTheme) Color(0xFF22C55E) else Color(0xFF2563EB)

                // Soft road highlight glow
                Polyline(
                    points = routePoints,
                    color = outerGlowColor,
                    width = 34f,
                    zIndex = 2.0f
                )
                // Crisp dark road edge casing
                Polyline(
                    points = routePoints,
                    color = outerCasingColor,
                    width = 24f,
                    zIndex = 2.5f
                )
                // Bright highlighted road core
                Polyline(
                    points = routePoints,
                    color = innerBandColor,
                    width = 16f,
                    zIndex = 3.0f
                )

                // Best Route Callout Pill on Route Preview
                if (!isNavigationMode && primaryMidpoint != null && bestRouteCalloutBitmap != null) {
                    Marker(
                        state = MarkerState(
                            position = LatLng(primaryMidpoint.latitude, primaryMidpoint.longitude)
                        ),
                        anchor = Offset(0.5f, 1.0f),
                        zIndex = 6.5f,
                        icon = BitmapDescriptorFactory.fromBitmap(bestRouteCalloutBitmap)
                    )
                }

                // Route Pacing Dots (white-ringed blue/green dots along the corridor)
                routePacingPoints.forEach { pt ->
                    Marker(
                        state = MarkerState(position = LatLng(pt.latitude, pt.longitude)),
                        anchor = Offset(0.5f, 0.5f),
                        flat = true,
                        zIndex = 4.0f,
                        icon = BitmapDescriptorFactory.fromBitmap(pacingDotBitmap)
                    )
                }

                // Dotted Approach Connector + Gray Anchor Node when starting/joining route
                if (dottedApproachPoints.size >= 2 && nearestRouteAnchor != null) {
                    Polyline(
                        points = dottedApproachPoints.map { LatLng(it.latitude, it.longitude) },
                        color = Color(0xFF64748B),
                        width = 10f,
                        pattern = listOf(Dot(), Gap(14f)),
                        zIndex = 4.5f
                    )
                    Marker(
                        state = MarkerState(
                            position = LatLng(nearestRouteAnchor.latitude, nearestRouteAnchor.longitude)
                        ),
                        anchor = Offset(0.5f, 0.5f),
                        flat = true,
                        zIndex = 5.0f,
                        icon = BitmapDescriptorFactory.fromBitmap(roadAnchorNodeBitmap)
                    )
                }

                // Directional Guidance Arrows (White shaft arrow with dark navy/green border) on road
                roadGuidanceArrows.forEach { arrowSpec ->
                    Marker(
                        state = MarkerState(
                            position = LatLng(arrowSpec.position.latitude, arrowSpec.position.longitude)
                        ),
                        anchor = Offset(0.5f, 0.5f),
                        flat = true,
                        rotation = arrowSpec.bearingDegrees,
                        zIndex = 6.0f,
                        icon = BitmapDescriptorFactory.fromBitmap(roadDirectionArrowBitmap)
                    )
                }
            }

            // 3. Active Hazards (Warning Triangles + Radius Circles)
            hazards.filter { it.isEffectiveHazard }.forEach { hazard ->
                val isCrit = hazard.severity == HazardSeverity.CRITICAL ||
                    hazard.severity == HazardSeverity.HIGH
                val circleColor = when (hazard.severity) {
                    HazardSeverity.CRITICAL, HazardSeverity.HIGH -> HazardRed
                    HazardSeverity.MEDIUM -> HazardOrange
                    HazardSeverity.LOW -> Color(0xFFFBC02D)
                }
                Marker(
                    state = MarkerState(position = LatLng(hazard.latitude, hazard.longitude)),
                    title = "${hazard.name} • ${hazard.type.displayName}",
                    snippet = "${hazard.severity.name} - ${hazard.description}",
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = 8.0f,
                    icon = BitmapDescriptorFactory.fromBitmap(
                        if (isCrit) criticalHazardBitmap else warningHazardBitmap
                    )
                )
                Circle(
                    center = LatLng(hazard.latitude, hazard.longitude),
                    radius = hazard.radiusMeters,
                    fillColor = circleColor.copy(alpha = 0.22f),
                    strokeColor = circleColor,
                    strokeWidth = 4f
                )
            }

            // 4. Destination Red Pin
            if (destination != null) {
                Marker(
                    state = MarkerState(
                        position = LatLng(destination.latitude, destination.longitude)
                    ),
                    title = destination.name,
                    snippet = destination.address,
                    zIndex = 7.0f,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            }

            // 5. Professional RoutePilot 3D Navigation Arrow Puck
            Marker(
                state = MarkerState(
                    position = LatLng(currentLocation.latitude, currentLocation.longitude)
                ),
                title = if (isNavigationMode) "Navigating" else "Your Location",
                anchor = Offset(0.5f, 0.5f),
                flat = true,
                rotation = headingBearingDegrees,
                zIndex = 10.0f,
                icon = BitmapDescriptorFactory.fromBitmap(driverMarkerBitmap)
            )
        }

        // Floating Map Controls on the Right (including Satellite Mode, Red-Needle "N" Compass, and Zoom)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Satellite Mode Toggle Button with clear SAT / MAP label
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSatelliteMode) RoutePilotBlue else Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clickable { isSatelliteMode = !isSatelliteMode }
                    .testTag("map_btn_satellite_toggle")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.SatelliteAlt,
                        contentDescription = if (isSatelliteMode) "Switch to Standard Map" else "Switch to Satellite Mode",
                        tint = if (isSatelliteMode) Color.White else RoutePilotBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isSatelliteMode) "SAT ON" else "Satellite",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isSatelliteMode) Color.White else Color(0xFF0F172A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            if (showNavigationControls) {
                MapControlFloatingButton(
                    onClick = { onToggleVoiceMute?.invoke() },
                    contentDescription = "Toggle Navigation Voice",
                    testTag = "map_btn_speaker"
                ) {
                    Icon(
                        imageVector = if (isVoiceMuted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = "Voice Guidance",
                        tint = if (isVoiceMuted) Color(0xFF64748B) else Color(0xFF0F172A)
                    )
                }

                MapControlFloatingButton(
                    onClick = { recenterTrigger++ },
                    contentDescription = "Compass & Recenter",
                    testTag = "map_btn_compass"
                ) {
                    CompassNorthIcon()
                }
            } else {
                MapControlFloatingButton(
                    onClick = { recenterTrigger++ },
                    contentDescription = "Compass North",
                    testTag = "map_btn_compass"
                ) {
                    CompassNorthIcon()
                }

                MapControlFloatingButton(
                    onClick = { recenterTrigger++ },
                    contentDescription = "Center on My Location",
                    testTag = "map_btn_my_location"
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        tint = RoutePilotBlue
                    )
                }
            }

            // Zoom In / Zoom Out Pill Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 6.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { zoomLevel = min(19f, zoomLevel + 1.0f) },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("map_btn_zoom_in")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = Color(0xFF1E293B)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(1.dp)
                            .background(Color(0xFFE2E8F0))
                    )
                    IconButton(
                        onClick = { zoomLevel = max(5f, zoomLevel - 1.0f) },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("map_btn_zoom_out")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = Color(0xFF1E293B)
                        )
                    }
                }
            }
        }

        // Unobstructed Satellite / Map Mode toggle pill at Top-Start (below top turn banner during navigation)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSatelliteMode) RoutePilotBlue else Color.White.copy(alpha = 0.96f),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = 12.dp,
                    top = if (isNavigationMode) 104.dp else 12.dp
                )
                .clickable { isSatelliteMode = !isSatelliteMode }
                .testTag("map_mode_pill_toggle")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSatelliteMode) Icons.Default.SatelliteAlt else Icons.Default.Layers,
                    contentDescription = null,
                    tint = if (isSatelliteMode) Color.White else RoutePilotBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSatelliteMode) "Satellite Mode ON" else "Satellite Mode",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isSatelliteMode) Color.White else Color(0xFF0F172A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

/**
 * Red North Pointer + Bold "N" Compass Icon matching the reference navigation screenshot.
 */
@Composable
private fun CompassNorthIcon() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(width = 12.dp, height = 11.dp)) {
            val path = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path = path, color = Color(0xFFD93025))
        }
        Text(
            text = "N",
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color(0xFF3C4043),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                lineHeight = 13.sp
            )
        )
    }
}

@Composable
private fun MapControlFloatingButton(
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
    ) {
        IconButton(onClick = onClick) {
            content()
        }
    }
}

private fun findRouteMidpoint(route: Route?, fraction: Double): LocationPoint? {
    val pts = route?.points.orEmpty()
    if (pts.isEmpty()) return null
    val idx = ((pts.size - 1) * fraction).toInt().coerceIn(0, pts.lastIndex)
    return pts[idx]
}

/**
 * Finds the nearest point on the route polyline to anchor the dotted entry curve.
 */
private fun findNearestRouteEntryPoint(
    currentLocation: LocationPoint,
    route: Route?
): LocationPoint? {
    val pts = route?.points.orEmpty()
    if (pts.isEmpty()) return null
    var nearest = pts.first()
    var minDist = Double.MAX_VALUE
    for (pt in pts) {
        val d = GeoUtils.haversineMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            pt.latitude,
            pt.longitude
        )
        if (d < minDist) {
            minDist = d
            nearest = pt
        }
    }
    return nearest
}

/**
 * Builds a smooth curved dotted connector between the driver's navigation puck and the road entry anchor.
 */
private fun computeDottedApproachCurve(
    currentLocation: LocationPoint,
    anchorPoint: LocationPoint?
): List<LocationPoint> {
    if (anchorPoint == null) return emptyList()
    val distMeters = GeoUtils.haversineMeters(
        currentLocation.latitude,
        currentLocation.longitude,
        anchorPoint.latitude,
        anchorPoint.longitude
    )
    val targetAnchor = if (distMeters < 18.0) {
        LocationPoint(
            latitude = currentLocation.latitude - 0.00038,
            longitude = currentLocation.longitude - 0.00028
        )
    } else {
        anchorPoint
    }

    val controlLat = (currentLocation.latitude + targetAnchor.latitude) / 2.0 + 0.00018
    val controlLng = (currentLocation.longitude + targetAnchor.longitude) / 2.0 - 0.00016

    val curve = mutableListOf<LocationPoint>()
    val steps = 8
    for (i in 0..steps) {
        val t = i.toDouble() / steps
        val oneMinusT = 1.0 - t
        val lat = oneMinusT * oneMinusT * targetAnchor.latitude +
            2 * oneMinusT * t * controlLat +
            t * t * currentLocation.latitude
        val lng = oneMinusT * oneMinusT * targetAnchor.longitude +
            2 * oneMinusT * t * controlLng +
            t * t * currentLocation.longitude
        curve.add(LocationPoint(lat, lng))
    }
    return curve
}

/**
 * Computes evenly spaced pacing dot coordinates along the active route polyline.
 */
private fun computeInterpolatedRoutePoints(
    route: Route?,
    stepMeters: Double
): List<LocationPoint> {
    val pts = route?.points.orEmpty()
    if (pts.size < 2) return emptyList()
    val result = mutableListOf<LocationPoint>()
    var accumulated = 0.0
    for (i in 0 until pts.lastIndex) {
        val a = pts[i]
        val b = pts[i + 1]
        val segDist = GeoUtils.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        if (segDist <= 1.0) continue
        var cursor = stepMeters - accumulated
        while (cursor <= segDist) {
            val fraction = cursor / segDist
            result.add(
                LocationPoint(
                    latitude = a.latitude + (b.latitude - a.latitude) * fraction,
                    longitude = a.longitude + (b.longitude - a.longitude) * fraction
                )
            )
            cursor += stepMeters
        }
        accumulated = (accumulated + segDist) % stepMeters
    }
    return result.take(40)
}

/**
 * Computes road guidance arrows (`══════►`) positioned directly along the road segments ahead of the driver.
 */
private fun computeRoadGuidanceArrows(
    currentLocation: LocationPoint,
    route: Route?
): List<RoadGuidanceArrowSpec> {
    val pts = route?.points.orEmpty()
    if (pts.size < 2) return emptyList()

    var startIdx = 0
    var minDist = Double.MAX_VALUE
    for (i in 0 until pts.lastIndex) {
        val d = GeoUtils.haversineMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            pts[i].latitude,
            pts[i].longitude
        )
        if (d < minDist) {
            minDist = d
            startIdx = i
        }
    }

    val specs = mutableListOf<RoadGuidanceArrowSpec>()
    val stride = max(1, (pts.size - startIdx) / 5)
    var idx = startIdx
    while (idx < pts.lastIndex && specs.size < 5) {
        val a = pts[idx]
        val b = pts[idx + 1]
        val bearing = calculateBearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)
        val fraction = if (idx == startIdx) 0.35 else 0.50
        val arrowPos = LocationPoint(
            latitude = a.latitude + (b.latitude - a.latitude) * fraction,
            longitude = a.longitude + (b.longitude - a.longitude) * fraction
        )
        specs.add(RoadGuidanceArrowSpec(position = arrowPos, bearingDegrees = bearing))
        idx += stride
    }
    return specs
}

/**
 * Computes the compass bearing (0..360 degrees clockwise from North) from the driver's
 * current location toward the next ahead waypoint on the active route.
 */
private fun computeRouteHeadingBearing(
    currentLocation: LocationPoint,
    route: Route?,
    destination: Destination?
): Float {
    val pts = route?.points.orEmpty()
    val targetPoint: LocationPoint? = if (pts.size >= 2) {
        var nearestIdx = 0
        var minDist = Double.MAX_VALUE
        for (i in pts.indices) {
            val d = GeoUtils.haversineMeters(
                currentLocation.latitude,
                currentLocation.longitude,
                pts[i].latitude,
                pts[i].longitude
            )
            if (d < minDist) {
                minDist = d
                nearestIdx = i
            }
        }
        val nextIdx = (nearestIdx + 1).coerceAtMost(pts.lastIndex)
        if (nextIdx != nearestIdx) pts[nextIdx] else pts.last()
    } else if (destination != null) {
        LocationPoint(destination.latitude, destination.longitude)
    } else {
        null
    }

    if (targetPoint == null) return 0f
    return calculateBearingDegrees(
        currentLocation.latitude,
        currentLocation.longitude,
        targetPoint.latitude,
        targetPoint.longitude
    )
}

private fun calculateBearingDegrees(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Float {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLon = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLon)
    val theta = Math.toDegrees(atan2(y, x))
    return ((theta + 360.0) % 360.0).toFloat()
}

/**
 * Creates a floating route callout pill (`Best Route • 18 min` or `Alt • 22 min`) directly on the map road.
 */
private fun createRouteCalloutBadgeBitmap(
    title: String,
    subtitle: String,
    isPrimary: Boolean,
    isGreenTheme: Boolean
): Bitmap {
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isPrimary) android.graphics.Color.WHITE else Color(0xFF1E293B).toArgb()
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isPrimary) Color(0xFFE2E8F0).toArgb() else Color(0xFF475569).toArgb()
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    val textWidth = max(titlePaint.measureText(title), subPaint.measureText(subtitle))
    val padX = 28f
    val w = (textWidth + padX * 2).toInt().coerceAtLeast(180)
    val h = 92
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)

    val bgColor = when {
        isPrimary && isGreenTheme -> Color(0xFF15803D).toArgb()
        isPrimary -> Color(0xFF1D4ED8).toArgb()
        else -> android.graphics.Color.WHITE
    }
    val strokeColor = when {
        isPrimary && isGreenTheme -> android.graphics.Color.WHITE
        isPrimary -> android.graphics.Color.WHITE
        else -> Color(0xFF64748B).toArgb()
    }

    val rect = RectF(6f, 6f, w - 6f, h - 16f)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    canvas.drawRoundRect(rect, 22f, 22f, fillPaint)
    canvas.drawRoundRect(rect, 22f, 22f, borderPaint)

    canvas.drawText(title, padX, 38f, titlePaint)
    canvas.drawText(subtitle, padX, 64f, subPaint)

    return bmp
}

/**
 * Creates a bespoke, professional 3D RoutePilot Navigation Puck & Swept-Wing Fin Arrow.
 */
private fun createProfessionalDriverMarkerBitmap(isNavigationMode: Boolean): Bitmap {
    val sizePx = 164
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    // 1. Soft outer drop shadow under the white navigation puck
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(42, 15, 23, 42)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 4f, sizePx * 0.39f, shadowPaint)

    // 2. Frosted White Circular Puck
    val puckFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(242, 255, 255, 255)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.38f, puckFillPaint)

    val puckBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNavigationMode) {
            Color(0xFFBFDBFE).toArgb()
        } else {
            Color(0xFFE2E8F0).toArgb()
        }
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    canvas.drawCircle(cx, cy, sizePx * 0.38f, puckBorderPaint)

    // 3. Subtle forward directional beam cone inside the top of the puck
    val beamPath = android.graphics.Path().apply {
        moveTo(cx, cy)
        lineTo(cx - sizePx * 0.22f, cy - sizePx * 0.31f)
        quadTo(cx, cy - sizePx * 0.38f, cx + sizePx * 0.22f, cy - sizePx * 0.31f)
        close()
    }
    val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            cx,
            cy,
            cx,
            cy - sizePx * 0.36f,
            android.graphics.Color.argb(65, 26, 115, 232),
            android.graphics.Color.argb(0, 26, 115, 232),
            Shader.TileMode.CLAMP
        )
        style = Paint.Style.FILL
    }
    canvas.drawPath(beamPath, beamPaint)

    // 4. Bespoke Professional 3D Swept-Wing Navigation Fin (pointing North = 0 deg)
    val noseY = cy - sizePx * 0.25f
    val outerWingTipY = cy + sizePx * 0.16f
    val innerWingRootY = cy + sizePx * 0.19f
    val tailNotchY = cy + sizePx * 0.08f
    val wingSpanX = sizePx * 0.22f
    val innerRootX = sizePx * 0.09f

    val fullArrowOutlinePath = android.graphics.Path().apply {
        moveTo(cx, noseY)
        lineTo(cx - wingSpanX, outerWingTipY)
        lineTo(cx - innerRootX, innerWingRootY)
        lineTo(cx, tailNotchY)
        lineTo(cx + innerRootX, innerWingRootY)
        lineTo(cx + wingSpanX, outerWingTipY)
        close()
    }
    val arrowHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(fullArrowOutlinePath, arrowHaloPaint)

    val leftWingPath = android.graphics.Path().apply {
        moveTo(cx, noseY)
        lineTo(cx - wingSpanX, outerWingTipY)
        lineTo(cx - innerRootX, innerWingRootY)
        lineTo(cx, tailNotchY)
        close()
    }
    val leftWingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            cx - wingSpanX,
            noseY,
            cx,
            innerWingRootY,
            Color(0xFF2563EB).toArgb(),
            Color(0xFF1D4ED8).toArgb(),
            Shader.TileMode.CLAMP
        )
        style = Paint.Style.FILL
    }
    canvas.drawPath(leftWingPath, leftWingPaint)

    val rightWingPath = android.graphics.Path().apply {
        moveTo(cx, noseY)
        lineTo(cx + wingSpanX, outerWingTipY)
        lineTo(cx + innerRootX, innerWingRootY)
        lineTo(cx, tailNotchY)
        close()
    }
    val rightWingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            cx,
            noseY,
            cx + wingSpanX,
            innerWingRootY,
            Color(0xFF1E40AF).toArgb(),
            Color(0xFF0F2976).toArgb(),
            Shader.TileMode.CLAMP
        )
        style = Paint.Style.FILL
    }
    canvas.drawPath(rightWingPath, rightWingPaint)

    val spinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color(0xFF93C5FD).toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(cx, noseY + 4f, cx, tailNotchY - 2f, spinePaint)

    return bmp
}

/**
 * Creates the White Directional Road Guidance Arrow with a Dark Navy/Green Border (`══════►`)
 * that lies flat on the road polyline to indicate the direction of movement.
 */
private fun createRoadDirectionGuidanceArrowBitmap(isGreenTheme: Boolean): Bitmap {
    val w = 84
    val h = 132
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = w / 2f

    val tipY = 10f
    val headBaseY = 44f
    val headHalfWidth = 24f
    val shaftHalfWidth = 9.5f
    val tailY = h - 14f

    val arrowPath = android.graphics.Path().apply {
        moveTo(cx, tipY)
        lineTo(cx - headHalfWidth, headBaseY)
        lineTo(cx - shaftHalfWidth, headBaseY)
        lineTo(cx - shaftHalfWidth, tailY)
        lineTo(cx + shaftHalfWidth, tailY)
        lineTo(cx + shaftHalfWidth, headBaseY)
        lineTo(cx + headHalfWidth, headBaseY)
        close()
    }

    val outlineColor = if (isGreenTheme) {
        Color(0xFF064E3B).toArgb()
    } else {
        Color(0xFF0F1E64).toArgb()
    }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = outlineColor
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    canvas.drawPath(arrowPath, borderPaint)
    canvas.drawPath(arrowPath, fillPaint)

    return bmp
}

/**
 * Creates the white-ringed blue/green pacing dot (`•`) placed periodically along the route corridor.
 */
private fun createRoutePacingDotBitmap(isGreenTheme: Boolean): Bitmap {
    val sizePx = 30
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val c = sizePx / 2f

    val outerWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val innerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isGreenTheme) {
            Color(0xFF16A34A).toArgb()
        } else {
            Color(0xFF1D4ED8).toArgb()
        }
        style = Paint.Style.FILL
    }

    canvas.drawCircle(c, c, 11f, outerWhite)
    canvas.drawCircle(c, c, 7.5f, innerFill)
    return bmp
}

/**
 * Creates the gray-bordered circular road anchor node where the dotted approach curve meets the road.
 */
private fun createRoadEntryAnchorBitmap(): Bitmap {
    val sizePx = 48
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val c = sizePx / 2f

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color(0xFFCBD5E1).toArgb()
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color(0xFF64748B).toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    canvas.drawCircle(c, c, 17f, fillPaint)
    canvas.drawCircle(c, c, 17f, strokePaint)
    return bmp
}

private fun createHazardTriangleBitmap(colorArgb: Int): Bitmap {
    val sizePx = 92
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = sizePx / 2f

    val triPath = android.graphics.Path().apply {
        moveTo(cx, 12f)
        lineTo(12f, sizePx - 14f)
        lineTo(sizePx - 12f, sizePx - 14f)
        close()
    }

    val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeJoin = Paint.Join.ROUND
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }

    canvas.drawPath(triPath, whiteStroke)
    canvas.drawPath(triPath, fillPaint)

    val exclPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6.5f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(cx, 34f, cx, 56f, exclPaint)

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, 67f, 4f, dotPaint)

    return bmp
}
