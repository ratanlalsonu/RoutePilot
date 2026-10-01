package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
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
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.abs
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
 * - Highlights the active route (Primary Blue / Safer Green after "Choose Other Path") AND exact-path
 *   dotted highlight (`Dot(), Gap(16f)`) strictly on the exact road polyline (`routePoints`)
 * - Rotates and moves the 3D Swept-Wing Navigation Arrow in real time as the user rotates or moves
 *   their mobile phone (via Android Rotation Vector / Compass / Accelerometer+Magnetometer sensors + GPS)
 * - Preserves user zoom-in and pan position during active navigation without snapping back to source
 */
private val sharedSatelliteModeState = mutableStateOf(false)

@Composable
fun RoutePilotMapView(
    currentLocation: LocationPoint,
    destination: Destination?,
    primaryRoute: Route?,
    secondaryRoute: Route? = null,
    hazards: List<Hazard> = emptyList(),
    nearbyPlaces: List<Destination> = emptyList(),
    searchCenterLocation: LocationPoint? = null,
    isMultiMarkerCategoryView: Boolean = false,
    fitAllMarkersTrigger: Int = 0,
    remainingDistanceMeters: Double? = null,
    remainingEtaSeconds: Int? = null,
    isNavigationMode: Boolean = false,
    isSaferGreenRoute: Boolean = false,
    isMapsApiKeyConfigured: Boolean = true,
    hasLocationPermission: Boolean = false,
    isVoiceMuted: Boolean = false,
    showNavigationControls: Boolean = false,
    onToggleVoiceMute: (() -> Unit)? = null,
    onSelectAlternateRoute: (() -> Unit)? = null,
    onSelectNearbyPlace: ((Destination) -> Unit)? = null,
    onMapClick: ((Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var zoomLevel by remember(isNavigationMode) {
        mutableFloatStateOf(if (isNavigationMode) 17.0f else 14.5f)
    }
    var manualZoomTrigger by remember { mutableIntStateOf(0) }
    var recenterTrigger by remember { mutableIntStateOf(0) }
    var isSatelliteMode by remember { sharedSatelliteModeState }

    val isGreenTheme = isSaferGreenRoute || (primaryRoute?.isDivertedForSafety == true)

    // Exact nearest point on the active route polyline
    val nearestRouteAnchor = remember(currentLocation.latitude, currentLocation.longitude, primaryRoute) {
        findNearestPointOnRoutePolyline(currentLocation, primaryRoute)
    }

    // Allow free driver arrow movement when the user moves their mobile phone; only snap to road centerline
    // when strictly progressing along an interior road segment within 12m
    val displayDriverLocation = remember(currentLocation, nearestRouteAnchor, isNavigationMode) {
        if (nearestRouteAnchor != null) {
            val distToRoad = GeoUtils.haversineMeters(
                currentLocation.latitude,
                currentLocation.longitude,
                nearestRouteAnchor.latitude,
                nearestRouteAnchor.longitude
            )
            val roadStart = primaryRoute?.points?.firstOrNull()
            val distFromStart = if (roadStart != null) {
                GeoUtils.haversineMeters(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    roadStart.latitude,
                    roadStart.longitude
                )
            } else {
                0.0
            }
            val snapThreshold = if (isNavigationMode) 12.0 else 28.0
            if (distToRoad <= snapThreshold && (!isNavigationMode || distFromStart > 18.0)) {
                nearestRouteAnchor
            } else {
                currentLocation
            }
        } else {
            currentLocation
        }
    }

    val routeHeadingBearingDegrees = remember(
        displayDriverLocation.latitude,
        displayDriverLocation.longitude,
        primaryRoute,
        destination
    ) {
        computeRouteHeadingBearing(displayDriverLocation, primaryRoute, destination)
    }

    // Real-time live device orientation / compass sensor heading so rotating or moving the mobile
    // immediately rotates the navigation arrow just like Google Maps
    val liveArrowBearingDegrees = rememberLiveDeviceHeadingDegrees(
        fallbackBearing = routeHeadingBearingDegrees,
        gpsBearing = currentLocation.bearing,
        gpsSpeedMps = currentLocation.speedMps
    )

    val roadGuidanceArrows = remember(displayDriverLocation.latitude, displayDriverLocation.longitude, primaryRoute) {
        computeRoadGuidanceArrows(displayDriverLocation, primaryRoute)
    }

    // Straight, exact approach connector only if origin or destination pin is slightly off the street (6m..220m)
    val originApproachPoints = remember(currentLocation, primaryRoute) {
        val roadStart = primaryRoute?.points?.firstOrNull()
        if (roadStart != null) {
            val d = GeoUtils.haversineMeters(
                currentLocation.latitude,
                currentLocation.longitude,
                roadStart.latitude,
                roadStart.longitude
            )
            if (d in 6.0..220.0) listOf(currentLocation, roadStart) else emptyList()
        } else {
            emptyList()
        }
    }

    val destinationApproachPoints = remember(destination, primaryRoute) {
        val roadEnd = primaryRoute?.points?.lastOrNull()
        if (destination != null && roadEnd != null) {
            val destPt = LocationPoint(destination.latitude, destination.longitude)
            val d = GeoUtils.haversineMeters(
                destPt.latitude,
                destPt.longitude,
                roadEnd.latitude,
                roadEnd.longitude
            )
            if (d in 6.0..220.0) listOf(roadEnd, destPt) else emptyList()
        } else {
            emptyList()
        }
    }

    // Route midpoint callout position for active route
    val primaryMidpoint = remember(primaryRoute) {
        findRouteMidpoint(primaryRoute, fraction = 0.48)
    }

    val driverMarkerBitmap = remember(isNavigationMode) {
        createProfessionalDriverMarkerBitmap(isNavigationMode)
    }
    val roadDirectionArrowBitmap = remember(isGreenTheme) {
        createRoadDirectionGuidanceArrowBitmap(isGreenTheme)
    }
    val roadAnchorNodeBitmap = remember {
        createRoadEntryAnchorBitmap()
    }
    val liveCalloutDistText = remember(remainingDistanceMeters, primaryRoute?.totalDistanceMeters) {
        val distM = remainingDistanceMeters ?: primaryRoute?.totalDistanceMeters ?: 0.0
        GeoUtils.formatDistance(distM, useKilometers = true)
    }
    val liveCalloutTimeText = remember(remainingEtaSeconds, primaryRoute?.durationMinutes) {
        if (remainingEtaSeconds != null) {
            GeoUtils.formatLiveRemainingTime(remainingEtaSeconds)
        } else {
            "${primaryRoute?.durationMinutes ?: 0} min"
        }
    }
    val bestRouteCalloutBitmap = remember(primaryRoute?.id, liveCalloutDistText, liveCalloutTimeText, isGreenTheme) {
        primaryRoute?.let {
            createRouteCalloutBadgeBitmap(
                title = if (isGreenTheme) "Safer Route • $liveCalloutTimeText" else "Best Route • $liveCalloutTimeText",
                subtitle = liveCalloutDistText,
                isPrimary = true,
                isGreenTheme = isGreenTheme
            )
        }
    }
    val criticalHazardBitmap = remember {
        createHazardTriangleBitmap(HazardRed.toArgb())
    }
    val warningHazardBitmap = remember {
        createHazardTriangleBitmap(HazardOrange.toArgb())
    }

    // Remembered MarkerState so the Google Maps Marker updates its position & rotation in real time
    val driverMarkerState = remember {
        MarkerState(position = LatLng(displayDriverLocation.latitude, displayDriverLocation.longitude))
    }
    driverMarkerState.position = LatLng(displayDriverLocation.latitude, displayDriverLocation.longitude)

    val destinationMarkerState = remember {
        MarkerState(
            position = LatLng(
                destination?.latitude ?: displayDriverLocation.latitude,
                destination?.longitude ?: displayDriverLocation.longitude
            )
        )
    }
    if (destination != null) {
        destinationMarkerState.position = LatLng(destination.latitude, destination.longitude)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.Builder()
            .target(LatLng(displayDriverLocation.latitude, displayDriverLocation.longitude))
            .zoom(zoomLevel)
            .bearing(if (isNavigationMode) routeHeadingBearingDegrees else 0f)
            .tilt(if (isNavigationMode) 45f else 0f)
            .build()
    }

    val nearbyIdsSignature = remember(nearbyPlaces) {
        nearbyPlaces.joinToString("|") { "${it.id}:${it.latitude}:${it.longitude}" }
    }

    // Unified Camera Controller:
    // 1. Multi-Marker Category / Location Search -> frames ALL Red Location Markers in LatLngBounds
    // 2. Initial SelectDestinationScreen Open -> centers on User's Current Location
    // 3. Tapped Single Marker -> centers on that selected marker while keeping all red markers visible
    // 4. Route Preview / Active Navigation -> frames route or tracks 3D navigation camera
    LaunchedEffect(
        nearbyIdsSignature,
        searchCenterLocation?.latitude,
        searchCenterLocation?.longitude,
        isMultiMarkerCategoryView,
        fitAllMarkersTrigger,
        destination?.id,
        destination?.latitude,
        destination?.longitude,
        primaryRoute?.id,
        isNavigationMode
    ) {
        if (!isNavigationMode && primaryRoute == null) {
            val shouldFrameAllRedMarkers = nearbyPlaces.isNotEmpty() && (
                isMultiMarkerCategoryView ||
                    (nearbyPlaces.size > 1 && destination?.id == nearbyPlaces.firstOrNull()?.id)
                )
            if (shouldFrameAllRedMarkers) {
                val anchor = searchCenterLocation
                    ?: LocationPoint(nearbyPlaces.first().latitude, nearbyPlaces.first().longitude)
                val clusterPlaces = nearbyPlaces.filter { place ->
                    GeoUtils.haversineMeters(
                        anchor.latitude,
                        anchor.longitude,
                        place.latitude,
                        place.longitude
                    ) <= 35_000.0
                }.ifEmpty { nearbyPlaces }

                val boundsBuilder = LatLngBounds.builder()
                var includedCount = 0
                clusterPlaces.forEach { place ->
                    boundsBuilder.include(LatLng(place.latitude, place.longitude))
                    includedCount++
                }
                // Only include currentLocation if searching around current location (within 12 km)
                val distDriverToAnchor = GeoUtils.haversineMeters(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    anchor.latitude,
                    anchor.longitude
                )
                if (searchCenterLocation == null && distDriverToAnchor <= 12_000.0) {
                    boundsBuilder.include(LatLng(currentLocation.latitude, currentLocation.longitude))
                    includedCount++
                }

                if (includedCount >= 2) {
                    val bounds = boundsBuilder.build()
                    val animated = runCatching {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngBounds(bounds, 135)
                        )
                    }.isSuccess
                    if (!animated) {
                        runCatching {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngBounds(bounds, 1000, 1100, 140)
                            )
                        }
                    }
                } else {
                    val first = clusterPlaces.first()
                    val camPos = CameraPosition.Builder()
                        .target(LatLng(first.latitude, first.longitude))
                        .zoom(14.5f)
                        .bearing(0f)
                        .tilt(0f)
                        .build()
                    runCatching {
                        cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(camPos))
                    }
                }
            } else {
                val isCurrentUserLoc = destination == null || destination.id == "current_user_location"
                val targetLat = if (isCurrentUserLoc) displayDriverLocation.latitude else destination!!.latitude
                val targetLng = if (isCurrentUserLoc) displayDriverLocation.longitude else destination!!.longitude
                val targetZoom = if (isCurrentUserLoc) {
                    15.8f
                } else {
                    max(cameraPositionState.position.zoom, 15.2f).coerceAtMost(16.8f)
                }
                val camPos = CameraPosition.Builder()
                    .target(LatLng(targetLat, targetLng))
                    .zoom(targetZoom)
                    .bearing(0f)
                    .tilt(0f)
                    .build()
                runCatching {
                    cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(camPos))
                }
            }
        } else if ((!isNavigationMode || isGreenTheme) && primaryRoute != null && primaryRoute.points.size >= 2) {
            runCatching {
                val boundsBuilder = LatLngBounds.builder()
                primaryRoute.points.forEach { pt ->
                    boundsBuilder.include(LatLng(pt.latitude, pt.longitude))
                }
                secondaryRoute?.points?.forEach { pt ->
                    boundsBuilder.include(LatLng(pt.latitude, pt.longitude))
                }
                hazards.filter { it.isEffectiveHazard }.forEach { hz ->
                    boundsBuilder.include(LatLng(hz.latitude, hz.longitude))
                }
                boundsBuilder.include(LatLng(currentLocation.latitude, currentLocation.longitude))
                destination?.let {
                    boundsBuilder.include(LatLng(it.latitude, it.longitude))
                }
                val bounds = boundsBuilder.build()
                val animated = runCatching {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(bounds, 140)
                    )
                }.isSuccess
                if (!animated) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(bounds, 1000, 1100, 140)
                    )
                }
            }
        } else if (isNavigationMode) {
            runCatching {
                val firstHazard = hazards.firstOrNull { it.isEffectiveHazard }
                if (firstHazard != null && primaryRoute != null && primaryRoute.points.size >= 2) {
                    val boundsBuilder = LatLngBounds.builder()
                    boundsBuilder.include(LatLng(displayDriverLocation.latitude, displayDriverLocation.longitude))
                    boundsBuilder.include(LatLng(firstHazard.latitude, firstHazard.longitude))
                    val midIdx = (primaryRoute.points.size / 2).coerceIn(0, primaryRoute.points.lastIndex)
                    for (i in 0..minOf(primaryRoute.points.lastIndex, midIdx + 3)) {
                        val pt = primaryRoute.points[i]
                        boundsBuilder.include(LatLng(pt.latitude, pt.longitude))
                    }
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 160)
                    )
                } else {
                    val camPos = CameraPosition.Builder()
                        .target(LatLng(displayDriverLocation.latitude, displayDriverLocation.longitude))
                        .zoom(17.0f)
                        .bearing(routeHeadingBearingDegrees)
                        .tilt(45f)
                        .build()
                    cameraPositionState.animate(
                        CameraUpdateFactory.newCameraPosition(camPos)
                    )
                }
            }
        }
    }

    // Handle explicit Recenter / Compass button press
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            runCatching {
                val targetZoom = if (isNavigationMode) max(cameraPositionState.position.zoom, 17.0f) else 15.0f
                val camPos = CameraPosition.Builder()
                    .target(LatLng(displayDriverLocation.latitude, displayDriverLocation.longitude))
                    .zoom(targetZoom)
                    .bearing(if (isNavigationMode) liveArrowBearingDegrees else 0f)
                    .tilt(if (isNavigationMode) 45f else 0f)
                    .build()
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(camPos)
                )
            }
        }
    }

    // Handle explicit + / - zoom button presses at whatever location the user is currently viewing
    LaunchedEffect(manualZoomTrigger) {
        if (manualZoomTrigger > 0) {
            runCatching {
                cameraPositionState.animate(CameraUpdateFactory.zoomTo(zoomLevel))
            }
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
                isMyLocationEnabled = false,
                isTrafficEnabled = false,
                isBuildingEnabled = true
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true,
                rotationGesturesEnabled = true,
                tiltGesturesEnabled = true
            ),
            onMapClick = { latLng ->
                onMapClick?.invoke(latLng.latitude, latLng.longitude)
            }
        ) {
            // 0. Avoided Old Route Branch (shown during Screen 9: Route Updated alongside the new Green route)
            if (secondaryRoute != null && secondaryRoute.points.size >= 2) {
                val secondaryPoints = secondaryRoute.points.map { LatLng(it.latitude, it.longitude) }
                Polyline(
                    points = secondaryPoints,
                    color = Color(0xFF93C5FD),
                    width = 16f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    geodesic = true,
                    zIndex = 1.8f
                )
                // Amber/Red caution segment near the avoided hazard on secondaryRoute
                val secMid = secondaryPoints.size / 2
                val secStart = max(0, secMid - max(1, secondaryPoints.size / 6))
                val secEnd = min(secondaryPoints.lastIndex, secMid + max(1, secondaryPoints.size / 6))
                if (secEnd > secStart) {
                    Polyline(
                        points = secondaryPoints.subList(secStart, secEnd + 1),
                        color = Color(0xFFF59E0B).copy(alpha = 0.75f),
                        width = 16f,
                        jointType = JointType.ROUND,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        geodesic = true,
                        zIndex = 2.0f
                    )
                }
            }

            // 1. Highlighted Active Route on Exact Map Road (Casing + Core Highlight + Red Hazard Segment in Screen 7)
            if (primaryRoute != null && primaryRoute.points.size >= 2) {
                val routePoints = primaryRoute.points.map { LatLng(it.latitude, it.longitude) }
                val outerCasingColor = if (isGreenTheme) Color(0xFF15803D) else Color(0xFF174EA6)
                val innerBandColor = if (isGreenTheme) Color(0xFF22C55E) else Color(0xFF2563EB)
                val exactPathDotColor = if (isGreenTheme) Color(0xFFDCFCE7) else Color(0xFFE8F0FE)

                // Layer A: Crisp dark royal-blue / forest-green road edge casing on the exact street path
                Polyline(
                    points = routePoints,
                    color = outerCasingColor,
                    width = 26f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    geodesic = true,
                    zIndex = 2.5f
                )
                // Layer B: Vibrant Google Maps blue / safer green road highlight on the exact street path
                Polyline(
                    points = routePoints,
                    color = innerBandColor,
                    width = 18f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    geodesic = true,
                    zIndex = 3.0f
                )
                // Layer C: Exact-path dotted highlight (continuous circular dots locked 100% onto the road centerline)
                Polyline(
                    points = routePoints,
                    color = exactPathDotColor,
                    width = 10f,
                    pattern = listOf(Dot(), Gap(18f)),
                    jointType = JointType.ROUND,
                    geodesic = true,
                    zIndex = 3.6f
                )

                // Layer D: Red Blocked Road Segment on the active blue route leading into the Hazard Triangle (Screen 7)
                val activeHazardOnPrimary = if (!isGreenTheme) {
                    hazards.firstOrNull { it.isEffectiveHazard }
                } else {
                    null
                }
                if (activeHazardOnPrimary != null && routePoints.size >= 3) {
                    val hzIdx = primaryRoute.points.indices.minByOrNull { idx ->
                        GeoUtils.haversineMeters(
                            primaryRoute.points[idx].latitude,
                            primaryRoute.points[idx].longitude,
                            activeHazardOnPrimary.latitude,
                            activeHazardOnPrimary.longitude
                        )
                    } ?: (routePoints.size / 2)
                    val span = max(1, routePoints.size / 6)
                    val redStart = (hzIdx - span).coerceAtLeast(0)
                    val redEnd = hzIdx.coerceIn(redStart + 1, routePoints.lastIndex)
                    val redSegmentPoints = routePoints.subList(redStart, redEnd + 1)
                    if (redSegmentPoints.size >= 2) {
                        Polyline(
                            points = redSegmentPoints,
                            color = Color(0xFFB91C1C),
                            width = 26f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            geodesic = true,
                            zIndex = 3.8f
                        )
                        Polyline(
                            points = redSegmentPoints,
                            color = HazardRed,
                            width = 19f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            geodesic = true,
                            zIndex = 4.0f
                        )
                    }
                }

                // Anchor nodes at road start & road end (matching Google Maps endpoint circles)
                val firstRoadPt = routePoints.first()
                val lastRoadPt = routePoints.last()
                Marker(
                    state = MarkerState(position = firstRoadPt),
                    anchor = Offset(0.5f, 0.5f),
                    flat = true,
                    zIndex = 4.2f,
                    icon = BitmapDescriptorFactory.fromBitmap(roadAnchorNodeBitmap)
                )
                Marker(
                    state = MarkerState(position = lastRoadPt),
                    anchor = Offset(0.5f, 0.5f),
                    flat = true,
                    zIndex = 4.2f,
                    icon = BitmapDescriptorFactory.fromBitmap(roadAnchorNodeBitmap)
                )

                // Straight dotted approach connector ONLY if start/destination pin is slightly off the street
                if (originApproachPoints.size == 2) {
                    Polyline(
                        points = originApproachPoints.map { LatLng(it.latitude, it.longitude) },
                        color = Color(0xFF1A73E8),
                        width = 12f,
                        pattern = listOf(Dot(), Gap(14f)),
                        zIndex = 4.5f
                    )
                }
                if (destinationApproachPoints.size == 2) {
                    Polyline(
                        points = destinationApproachPoints.map { LatLng(it.latitude, it.longitude) },
                        color = Color(0xFF1A73E8),
                        width = 12f,
                        pattern = listOf(Dot(), Gap(14f)),
                        zIndex = 4.5f
                    )
                }

                // Route Callout Pill on Route Preview
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

                // Directional Guidance Arrows (White shaft arrow with dark navy/green border) flat on the road
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

            // 2. Active Hazards (Warning Triangles + Radius Circles indicated directly on the path)
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

            // 3. Search Center Area Halo (when searching around a specified location)
            if (!isNavigationMode && primaryRoute == null && searchCenterLocation != null) {
                val distFromDriver = GeoUtils.haversineMeters(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    searchCenterLocation.latitude,
                    searchCenterLocation.longitude
                )
                if (distFromDriver > 350.0) {
                    Circle(
                        center = LatLng(searchCenterLocation.latitude, searchCenterLocation.longitude),
                        radius = 2200.0,
                        fillColor = RoutePilotBlue.copy(alpha = 0.07f),
                        strokeColor = RoutePilotBlue.copy(alpha = 0.35f),
                        strokeWidth = 3f
                    )
                }
            }

            // 4. Nearby Places Red Location Markers (Google Maps-style multi-marker display with Red Pin + Place Label)
            if (!isNavigationMode && primaryRoute == null && nearbyPlaces.isNotEmpty()) {
                nearbyPlaces.forEach { place ->
                    val isSelectedPlace = destination?.id == place.id ||
                        (destination != null &&
                            abs(destination.latitude - place.latitude) < 1e-5 &&
                            abs(destination.longitude - place.longitude) < 1e-5)

                    if (isSelectedPlace) {
                        Circle(
                            center = LatLng(place.latitude, place.longitude),
                            radius = 120.0,
                            fillColor = HazardRed.copy(alpha = 0.18f),
                            strokeColor = HazardRed,
                            strokeWidth = 4f
                        )
                    }

                    val (redPinBitmap, pinAnchor) = remember(place.id, place.name, isSelectedPlace) {
                        createGoogleMapsRedPlacePinBitmap(
                            title = place.name,
                            isSelected = isSelectedPlace
                        )
                    }

                    Marker(
                        state = remember(place.id, place.latitude, place.longitude) {
                            MarkerState(position = LatLng(place.latitude, place.longitude))
                        },
                        title = place.name,
                        snippet = "${place.category} • ${place.address}",
                        anchor = pinAnchor,
                        zIndex = if (isSelectedPlace) 9.4f else 7.8f,
                        icon = BitmapDescriptorFactory.fromBitmap(redPinBitmap),
                        onClick = { marker ->
                            onSelectNearbyPlace?.invoke(place)
                            marker.showInfoWindow()
                            true
                        }
                    )
                }
            }

            // 5. Primary Destination Red Pin (if not already rendered in nearbyPlaces or during Route Preview / Navigation)
            val isDestinationInNearbyList = !isNavigationMode &&
                primaryRoute == null &&
                destination != null &&
                nearbyPlaces.any {
                    it.id == destination.id ||
                        (abs(it.latitude - destination.latitude) < 1e-5 &&
                            abs(it.longitude - destination.longitude) < 1e-5)
                }

            if (destination != null && destination.id != "current_user_location" && !isDestinationInNearbyList) {
                if (!isNavigationMode && primaryRoute == null) {
                    Circle(
                        center = LatLng(destination.latitude, destination.longitude),
                        radius = 120.0,
                        fillColor = HazardRed.copy(alpha = 0.18f),
                        strokeColor = HazardRed,
                        strokeWidth = 4f
                    )
                }
                Marker(
                    state = destinationMarkerState,
                    title = destination.name,
                    snippet = "${destination.category} • ${destination.address}",
                    zIndex = 9.2f,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                    onClick = { marker ->
                        onSelectNearbyPlace?.invoke(destination)
                        marker.showInfoWindow()
                        true
                    }
                )
            }

            // 4. Professional RoutePilot 3D Navigation Arrow Puck (rotates & moves with mobile sensor + GPS)
            Marker(
                state = driverMarkerState,
                title = if (isNavigationMode) "Navigating" else "Your Location",
                anchor = Offset(0.5f, 0.5f),
                flat = true,
                rotation = liveArrowBearingDegrees,
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
                        onClick = {
                            val currentZoom = cameraPositionState.position.zoom
                            zoomLevel = min(21f, currentZoom + 1.0f)
                            manualZoomTrigger++
                        },
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
                        onClick = {
                            val currentZoom = cameraPositionState.position.zoom
                            zoomLevel = max(5f, currentZoom - 1.0f)
                            manualZoomTrigger++
                        },
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
    }
}

/**
 * Tracks real-time mobile orientation/rotation via Android's `SensorManager`
 * (`TYPE_ROTATION_VECTOR`, `TYPE_GAME_ROTATION_VECTOR`, and `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD`)
 * so that rotating or moving the mobile phone smoothly rotates the 3D Navigation Arrow just like Google Maps.
 */
@Composable
private fun rememberLiveDeviceHeadingDegrees(
    fallbackBearing: Float,
    gpsBearing: Float,
    gpsSpeedMps: Float
): Float {
    val context = LocalContext.current
    var sensorAzimuth by remember { mutableFloatStateOf(Float.NaN) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            onDispose { }
        } else {
            val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val gameRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            val rotationMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)
            val gravity = FloatArray(3)
            val geomagnetic = FloatArray(3)
            var hasGravity = false
            var hasGeomagnetic = false

            var lastSensorEmitMillis = 0L

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val now = System.currentTimeMillis()
                    if (now - lastSensorEmitMillis < 120L && !sensorAzimuth.isNaN()) {
                        return
                    }
                    var computedMatrix = false
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR,
                        Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            computedMatrix = true
                        }
                        Sensor.TYPE_ACCELEROMETER -> {
                            System.arraycopy(event.values, 0, gravity, 0, min(3, event.values.size))
                            hasGravity = true
                            if (hasGeomagnetic) {
                                computedMatrix = SensorManager.getRotationMatrix(
                                    rotationMatrix,
                                    null,
                                    gravity,
                                    geomagnetic
                                )
                            }
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            System.arraycopy(event.values, 0, geomagnetic, 0, min(3, event.values.size))
                            hasGeomagnetic = true
                            if (hasGravity) {
                                computedMatrix = SensorManager.getRotationMatrix(
                                    rotationMatrix,
                                    null,
                                    gravity,
                                    geomagnetic
                                )
                            }
                        }
                    }

                    if (computedMatrix) {
                        // Remap axes if phone is held upright (> 55 degrees tilt) to prevent gimbal lock
                        val isUpright = abs(rotationMatrix[8]) < 0.55f
                        if (isUpright) {
                            SensorManager.remapCoordinateSystem(
                                rotationMatrix,
                                SensorManager.AXIS_X,
                                SensorManager.AXIS_Z,
                                remappedMatrix
                            )
                        } else {
                            System.arraycopy(rotationMatrix, 0, remappedMatrix, 0, 9)
                        }

                        SensorManager.getOrientation(remappedMatrix, orientationAngles)
                        val rawDeg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()

                        if (sensorAzimuth.isNaN()) {
                            lastSensorEmitMillis = now
                            sensorAzimuth = rawDeg
                        } else {
                            var delta = (rawDeg - sensorAzimuth) % 360f
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            if (abs(delta) >= 2.5f) {
                                lastSensorEmitMillis = now
                                sensorAzimuth = ((sensorAzimuth + delta * 0.30f) + 360f) % 360f
                            }
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            runCatching {
                when {
                    rotationVectorSensor != null -> {
                        sensorManager.registerListener(
                            listener,
                            rotationVectorSensor,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }
                    accelerometer != null && magnetometer != null -> {
                        sensorManager.registerListener(
                            listener,
                            accelerometer,
                            SensorManager.SENSOR_DELAY_UI
                        )
                        sensorManager.registerListener(
                            listener,
                            magnetometer,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }
                    gameRotationSensor != null -> {
                        sensorManager.registerListener(
                            listener,
                            gameRotationSensor,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }
                }
            }

            onDispose {
                runCatching { sensorManager.unregisterListener(listener) }
            }
        }
    }

    return when {
        gpsSpeedMps > 3.5f && gpsBearing != 0f -> gpsBearing
        !sensorAzimuth.isNaN() -> sensorAzimuth
        gpsBearing != 0f -> gpsBearing
        else -> fallbackBearing
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
 * Projects [currentLocation] onto the nearest segment of [route] so the driver arrow puck
 * sits cleanly on the exact road centerline.
 */
private fun findNearestPointOnRoutePolyline(
    currentLocation: LocationPoint,
    route: Route?
): LocationPoint? {
    val pts = route?.points.orEmpty()
    if (pts.isEmpty()) return null
    if (pts.size == 1) return pts.first()

    var bestPoint = pts.first()
    var minDist = Double.MAX_VALUE

    for (i in 0 until pts.lastIndex) {
        val a = pts[i]
        val b = pts[i + 1]
        val projected = projectPointOntoSegment(currentLocation, a, b)
        val d = GeoUtils.haversineMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            projected.latitude,
            projected.longitude
        )
        if (d < minDist) {
            minDist = d
            bestPoint = projected
        }
    }
    return bestPoint
}

private fun projectPointOntoSegment(
    p: LocationPoint,
    a: LocationPoint,
    b: LocationPoint
): LocationPoint {
    val dx = b.longitude - a.longitude
    val dy = b.latitude - a.latitude
    val lenSq = dx * dx + dy * dy
    if (lenSq < 1e-12) return a
    val t = (((p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy) / lenSq)
        .coerceIn(0.0, 1.0)
    return LocationPoint(
        latitude = a.latitude + t * dy,
        longitude = a.longitude + t * dx
    )
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

    // Collect candidate segments of sufficient length (>= 22m) so arrows lie cleanly on straight road sections
    val candidateIndices = (startIdx until pts.lastIndex).filter { idx ->
        val a = pts[idx]
        val b = pts[idx + 1]
        GeoUtils.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) >= 22.0
    }.ifEmpty {
        (startIdx until pts.lastIndex).toList()
    }

    if (candidateIndices.isEmpty()) return emptyList()

    val specs = mutableListOf<RoadGuidanceArrowSpec>()
    val stride = max(1, candidateIndices.size / 4)
    var cIdx = 0
    while (cIdx < candidateIndices.size && specs.size < 4) {
        val segIdx = candidateIndices[cIdx]
        val a = pts[segIdx]
        val b = pts[segIdx + 1]
        val bearing = calculateBearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)
        val arrowPos = LocationPoint(
            latitude = a.latitude + (b.latitude - a.latitude) * 0.5,
            longitude = a.longitude + (b.longitude - a.longitude) * 0.5
        )
        specs.add(RoadGuidanceArrowSpec(position = arrowPos, bearingDegrees = bearing))
        cIdx += stride
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
    if (pts.size >= 2) {
        var nearestIdx = 0
        var minDist = Double.MAX_VALUE
        for (i in 0 until pts.lastIndex) {
            val proj = projectPointOntoSegment(currentLocation, pts[i], pts[i + 1])
            val d = GeoUtils.haversineMeters(
                currentLocation.latitude,
                currentLocation.longitude,
                proj.latitude,
                proj.longitude
            )
            if (d < minDist) {
                minDist = d
                nearestIdx = i
            }
        }
        val a = pts[nearestIdx]
        val b = pts[(nearestIdx + 1).coerceAtMost(pts.lastIndex)]
        if (GeoUtils.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) > 1.0) {
            return calculateBearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)
        }
    }
    if (destination != null) {
        return calculateBearingDegrees(
            currentLocation.latitude,
            currentLocation.longitude,
            destination.latitude,
            destination.longitude
        )
    }
    return 0f
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
    val sizePx = 148
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    // 1. Soft drop shadow under the circular navigation puck
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(55, 15, 23, 42)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 5f, sizePx * 0.39f, shadowPaint)

    // 2. Crisp White Outer Ring (matching Screens 7 & 9 in the reference design)
    val whiteRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.39f, whiteRingPaint)

    // 3. Vibrant Google Maps Blue Inner Circle (#1A73E8 / #2563EB)
    val blueCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            cx,
            cy - sizePx * 0.33f,
            cx,
            cy + sizePx * 0.33f,
            Color(0xFF2563EB).toArgb(),
            Color(0xFF1D4ED8).toArgb(),
            Shader.TileMode.CLAMP
        )
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.32f, blueCirclePaint)

    // 4. Crisp White Navigation Arrow inside the Blue Circle (pointing North = 0 deg)
    val noseY = cy - sizePx * 0.20f
    val wingTipY = cy + sizePx * 0.14f
    val tailNotchY = cy + sizePx * 0.06f
    val wingSpanX = sizePx * 0.16f

    val whiteArrowPath = android.graphics.Path().apply {
        moveTo(cx, noseY)
        lineTo(cx - wingSpanX, wingTipY)
        lineTo(cx, tailNotchY)
        lineTo(cx + wingSpanX, wingTipY)
        close()
    }
    val whiteArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawPath(whiteArrowPath, whiteArrowPaint)

    return bmp
}

/**
 * Creates the White Directional Road Guidance Arrow with a Dark Navy/Green Border (`══════►`)
 * that lies flat on the road polyline to indicate the direction of movement.
 */
private fun createRoadDirectionGuidanceArrowBitmap(isGreenTheme: Boolean): Bitmap {
    val w = 68
    val h = 104
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = w / 2f

    val tipY = 8f
    val headBaseY = 36f
    val headHalfWidth = 19f
    val shaftHalfWidth = 7.5f
    val tailY = h - 12f

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
        strokeWidth = 6.5f
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
 * Creates the white-centered circular road endpoint anchor node matching Google Maps.
 */
private fun createRoadEntryAnchorBitmap(): Bitmap {
    val sizePx = 40
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val c = sizePx / 2f

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color(0xFF1E293B).toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    canvas.drawCircle(c, c, 13f, fillPaint)
    canvas.drawCircle(c, c, 13f, strokePaint)
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

/**
 * Creates a Google Maps-style Red Teardrop Location Pin (`#EA4335`) with a crisp white-haloed
 * place name label beside it so all matching category locations stand out in red on the map.
 */
private fun createGoogleMapsRedPlacePinBitmap(
    title: String,
    isSelected: Boolean
): Pair<Bitmap, Offset> {
    val cleanTitle = title.trim().let {
        if (it.length > 22) it.take(20).trimEnd() + "…" else it
    }

    val textSizePx = if (isSelected) 25f else 22f
    val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isSelected) Color(0xFF991B1B).toArgb() else Color(0xFFB31412).toArgb()
        textSize = textSizePx
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = textSizePx
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = 7.5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    val pinRadius = if (isSelected) 21f else 17f
    val pinHeight = if (isSelected) 54f else 44f
    val leftPad = 8f
    val topPad = 6f
    val pinCenterX = leftPad + pinRadius
    val pinHeadCenterY = topPad + pinRadius
    val pinTipY = topPad + pinHeight

    val labelStartX = pinCenterX + pinRadius + 8f
    val measuredTextW = textFillPaint.measureText(cleanTitle)
    val totalW = (labelStartX + measuredTextW + 14f).toInt().coerceAtLeast(80)
    val totalH = (pinTipY + 8f).toInt().coerceAtLeast(60)

    val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)

    // 1. Soft ground shadow at pin tip
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(65, 15, 23, 42)
        style = Paint.Style.FILL
    }
    canvas.drawOval(
        RectF(pinCenterX - 8f, pinTipY - 3f, pinCenterX + 8f, pinTipY + 4f),
        shadowPaint
    )

    // 2. Teardrop Pin Path
    val pinPath = android.graphics.Path().apply {
        moveTo(pinCenterX, pinTipY)
        cubicTo(
            pinCenterX - pinRadius * 0.55f,
            pinTipY - pinHeight * 0.28f,
            pinCenterX - pinRadius,
            pinHeadCenterY + pinRadius * 0.45f,
            pinCenterX - pinRadius,
            pinHeadCenterY
        )
        arcTo(
            RectF(
                pinCenterX - pinRadius,
                pinHeadCenterY - pinRadius,
                pinCenterX + pinRadius,
                pinHeadCenterY + pinRadius
            ),
            180f,
            180f,
            false
        )
        cubicTo(
            pinCenterX + pinRadius,
            pinHeadCenterY + pinRadius * 0.45f,
            pinCenterX + pinRadius * 0.55f,
            pinTipY - pinHeight * 0.28f,
            pinCenterX,
            pinTipY
        )
        close()
    }

    val whiteOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = if (isSelected) 5.5f else 4f
        strokeJoin = Paint.Join.ROUND
    }
    val redPinFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isSelected) Color(0xFFD93025).toArgb() else Color(0xFFEA4335).toArgb()
        style = Paint.Style.FILL
    }

    canvas.drawPath(pinPath, whiteOutlinePaint)
    canvas.drawPath(pinPath, redPinFillPaint)

    // 3. White inner circle inside the red pin head (classic Google Maps POI marker dot)
    val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(pinCenterX, pinHeadCenterY, if (isSelected) 8.2f else 6.5f, innerDotPaint)

    // 4. Crisp Google Maps-style Place Name Label beside the red pin
    val textBaselineY = pinHeadCenterY + (textSizePx * 0.36f)
    canvas.drawText(cleanTitle, labelStartX, textBaselineY, textStrokePaint)
    canvas.drawText(cleanTitle, labelStartX, textBaselineY, textFillPaint)

    val anchorOffset = Offset(
        x = (pinCenterX / totalW.toFloat()).coerceIn(0f, 1f),
        y = (pinTipY / totalH.toFloat()).coerceIn(0f, 1f)
    )
    return bmp to anchorOffset
}
