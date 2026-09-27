package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.LocationPoint
import com.example.domain.model.Route
import com.example.ui.theme.HazardOrange
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.SafeRouteGreen
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polygon as OsmPolygon
import org.osmdroid.views.overlay.Polyline as OsmPolyline
import kotlin.math.max
import kotlin.math.min

/**
 * Real Map Engine for RoutePilot.
 *
 * - If a billed Google Cloud `MAPS_API_KEY` is configured in Secrets (.env), renders `GoogleMap`
 *   (Google Maps SDK for Android).
 * - When `MAPS_API_KEY` is not yet configured (or when toggled by the driver), automatically renders
 *   a **Real OpenStreetMap Live Tile Map (`org.osmdroid.views.MapView`)** with real streets, real
 *   buildings, real landmarks, real pan/zoom, real GPS marker, destination marker, hazard markers,
 *   and A* route polylines — working 100% live without requiring a Google Cloud billing account!
 */
@Composable
fun RoutePilotMapView(
    currentLocation: LocationPoint,
    destination: Destination?,
    primaryRoute: Route?,
    secondaryRoute: Route? = null,
    hazards: List<Hazard> = emptyList(),
    isNavigationMode: Boolean = false,
    isSaferGreenRoute: Boolean = false,
    isMapsApiKeyConfigured: Boolean = false,
    hasLocationPermission: Boolean = false,
    isVoiceMuted: Boolean = false,
    showNavigationControls: Boolean = false,
    onToggleVoiceMute: (() -> Unit)? = null,
    onMapClick: ((Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var zoomLevel by remember { mutableFloatStateOf(if (isNavigationMode) 15.0f else 13.8f) }
    var recenterTrigger by remember { mutableIntStateOf(0) }
    var preferGoogleMapsSdk by remember { mutableStateOf(isMapsApiKeyConfigured) }

    Box(modifier = modifier.fillMaxSize()) {
        if (preferGoogleMapsSdk && isMapsApiKeyConfigured) {
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(
                    LatLng(currentLocation.latitude, currentLocation.longitude),
                    zoomLevel
                )
            }

            LaunchedEffect(currentLocation.latitude, currentLocation.longitude, zoomLevel, recenterTrigger) {
                runCatching {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(currentLocation.latitude, currentLocation.longitude),
                            zoomLevel
                        )
                    )
                }
            }

            GoogleMap(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("google_map_sdk_view"),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                    isTrafficEnabled = true,
                    isBuildingEnabled = true
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = true
                ),
                onMapClick = { latLng ->
                    onMapClick?.invoke(latLng.latitude, latLng.longitude)
                }
            ) {
                Marker(
                    state = MarkerState(
                        position = LatLng(currentLocation.latitude, currentLocation.longitude)
                    ),
                    title = "Your Location",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )

                if (destination != null) {
                    Marker(
                        state = MarkerState(
                            position = LatLng(destination.latitude, destination.longitude)
                        ),
                        title = destination.name,
                        snippet = destination.address,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }

                if (secondaryRoute != null && secondaryRoute.points.size >= 2) {
                    Polyline(
                        points = secondaryRoute.points.map { LatLng(it.latitude, it.longitude) },
                        color = Color(0xFF94A3B8),
                        width = 12f
                    )
                }

                if (primaryRoute != null && primaryRoute.points.size >= 2) {
                    val routeColor = if (isSaferGreenRoute || primaryRoute.isDivertedForSafety) {
                        SafeRouteGreen
                    } else {
                        RoutePilotBlue
                    }
                    Polyline(
                        points = primaryRoute.points.map { LatLng(it.latitude, it.longitude) },
                        color = routeColor,
                        width = 16f
                    )
                }

                hazards.filter { it.isEffectiveHazard }.forEach { hazard ->
                    val hue = when (hazard.severity) {
                        HazardSeverity.CRITICAL, HazardSeverity.HIGH -> BitmapDescriptorFactory.HUE_RED
                        HazardSeverity.MEDIUM -> BitmapDescriptorFactory.HUE_ORANGE
                        HazardSeverity.LOW -> BitmapDescriptorFactory.HUE_YELLOW
                    }
                    val circleColor = when (hazard.severity) {
                        HazardSeverity.CRITICAL, HazardSeverity.HIGH -> HazardRed
                        HazardSeverity.MEDIUM -> HazardOrange
                        HazardSeverity.LOW -> Color(0xFFFBC02D)
                    }
                    Marker(
                        state = MarkerState(position = LatLng(hazard.latitude, hazard.longitude)),
                        title = "${hazard.name} (${hazard.severity.name})",
                        snippet = hazard.description,
                        icon = BitmapDescriptorFactory.defaultMarker(hue)
                    )
                    Circle(
                        center = LatLng(hazard.latitude, hazard.longitude),
                        radius = hazard.radiusMeters,
                        fillColor = circleColor.copy(alpha = 0.20f),
                        strokeColor = circleColor,
                        strokeWidth = 3f
                    )
                }
            }
        } else {
            // Zero-Key Real Street Tile Map View (OpenStreetMap Android SDK)
            OsmdroidRealTileMapView(
                currentLocation = currentLocation,
                destination = destination,
                primaryRoute = primaryRoute,
                secondaryRoute = secondaryRoute,
                hazards = hazards,
                isNavigationMode = isNavigationMode,
                isSaferGreenRoute = isSaferGreenRoute,
                zoomLevel = zoomLevel.toDouble(),
                recenterTrigger = recenterTrigger,
                onMapClick = onMapClick
            )
        }

        // Floating Map Controls on the Right (matching Screens 4, 6, 7, 9)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Compass",
                        tint = HazardRed
                    )
                }
            } else {
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
                            .size(44.dp)
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
                            .size(44.dp)
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

        // Map attribution badge at bottom-left
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 8.dp)
                .clickable {
                    if (isMapsApiKeyConfigured) {
                        preferGoogleMapsSdk = !preferGoogleMapsSdk
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = RoutePilotBlue,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (preferGoogleMapsSdk && isMapsApiKeyConfigured) {
                        "Google Maps Live"
                    } else {
                        "Live Street Map • OSM"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                )
            }
        }
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
            .size(46.dp)
            .testTag(testTag)
    ) {
        IconButton(onClick = onClick) {
            content()
        }
    }
}

/**
 * Real OpenStreetMap Android MapView (`org.osmdroid.views.MapView`) rendering actual street tiles,
 * roads, bridges, buildings, markers, and A* route polylines with zero API key setup required.
 */
@Composable
private fun OsmdroidRealTileMapView(
    currentLocation: LocationPoint,
    destination: Destination?,
    primaryRoute: Route?,
    secondaryRoute: Route?,
    hazards: List<Hazard>,
    isNavigationMode: Boolean,
    isSaferGreenRoute: Boolean,
    zoomLevel: Double,
    recenterTrigger: Int,
    onMapClick: ((Double, Double) -> Unit)?
) {
    val context = LocalContext.current

    remember(context) {
        Configuration.getInstance().userAgentValue = context.packageName.ifBlank { "com.aistudio.routepilot.drvnav" }
        true
    }

    val driverIconDrawable = remember(context, isNavigationMode) {
        createDriverMarkerDrawable(context, isNavigationMode)
    }
    val destPinDrawable = remember(context) {
        createDestinationPinDrawable(context)
    }
    val criticalHazardDrawable = remember(context) {
        createHazardTriangleDrawable(context, HazardRed.toArgb())
    }
    val warningHazardDrawable = remember(context) {
        createHazardTriangleDrawable(context, HazardOrange.toArgb())
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(zoomLevel)
            controller.setCenter(GeoPoint(currentLocation.latitude, currentLocation.longitude))
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
        }
    }

    LaunchedEffect(currentLocation.latitude, currentLocation.longitude, destination?.id, zoomLevel, recenterTrigger) {
        val targetCenter = if (!isNavigationMode && destination != null && primaryRoute != null) {
            GeoPoint(
                (currentLocation.latitude + destination.latitude) / 2.0,
                (currentLocation.longitude + destination.longitude) / 2.0
            )
        } else if (!isNavigationMode && destination != null) {
            GeoPoint(destination.latitude, destination.longitude)
        } else {
            GeoPoint(currentLocation.latitude, currentLocation.longitude)
        }
        mapView.controller.setZoom(zoomLevel)
        mapView.controller.animateTo(targetCenter)
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxSize()
            .testTag("osmdroid_live_map_view"),
        update = { view ->
            view.overlays.clear()

            // 1. Map Tap Listener Overlay
            if (onMapClick != null) {
                val eventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p != null) {
                            onMapClick(p.latitude, p.longitude)
                            return true
                        }
                        return false
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        if (p != null) {
                            onMapClick(p.latitude, p.longitude)
                            return true
                        }
                        return false
                    }
                }
                view.overlays.add(MapEventsOverlay(eventsReceiver))
            }

            // 2. Secondary / Alternate or Previous Route Polyline
            if (secondaryRoute != null && secondaryRoute.points.size >= 2) {
                val altLine = OsmPolyline(view).apply {
                    setPoints(secondaryRoute.points.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = Color(0xFF94A3B8).toArgb()
                    outlinePaint.strokeWidth = 14f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                view.overlays.add(altLine)
            }

            // 3. Primary / Safer Active Route Polyline
            if (primaryRoute != null && primaryRoute.points.size >= 2) {
                val routeColor = if (isSaferGreenRoute || primaryRoute.isDivertedForSafety) {
                    Color(0xFF189B52).toArgb()
                } else {
                    RoutePilotBlue.toArgb()
                }

                // Outer route casing
                val casingLine = OsmPolyline(view).apply {
                    setPoints(primaryRoute.points.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = if (isSaferGreenRoute || primaryRoute.isDivertedForSafety) {
                        Color(0xFF0E6235).toArgb()
                    } else {
                        Color(0xFF0B3C8C).toArgb()
                    }
                    outlinePaint.strokeWidth = 22f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                view.overlays.add(casingLine)

                val mainLine = OsmPolyline(view).apply {
                    setPoints(primaryRoute.points.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = routeColor
                    outlinePaint.strokeWidth = 15f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                view.overlays.add(mainLine)
            }

            // 4. Active Hazard Radius Polygons & Warning Triangle Markers
            hazards.filter { it.isEffectiveHazard }.forEach { hazard ->
                val isCrit = hazard.severity == HazardSeverity.CRITICAL || hazard.severity == HazardSeverity.HIGH
                val strokeArgb = if (isCrit) HazardRed.toArgb() else HazardOrange.toArgb()
                val fillArgb = if (isCrit) {
                    HazardRed.copy(alpha = 0.22f).toArgb()
                } else {
                    HazardOrange.copy(alpha = 0.20f).toArgb()
                }

                val circlePolygon = OsmPolygon(view).apply {
                    points = OsmPolygon.pointsAsCircle(
                        GeoPoint(hazard.latitude, hazard.longitude),
                        hazard.radiusMeters
                    )
                    fillPaint.color = fillArgb
                    outlinePaint.color = strokeArgb
                    outlinePaint.strokeWidth = 4f
                }
                view.overlays.add(circlePolygon)

                val hazardMarker = OsmMarker(view).apply {
                    position = GeoPoint(hazard.latitude, hazard.longitude)
                    title = "${hazard.name} (${hazard.severity.name})"
                    snippet = hazard.description
                    icon = if (isCrit) criticalHazardDrawable else warningHazardDrawable
                    setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                }
                view.overlays.add(hazardMarker)
            }

            // 5. Destination Red Pin Marker
            if (destination != null) {
                val destMarker = OsmMarker(view).apply {
                    position = GeoPoint(destination.latitude, destination.longitude)
                    title = destination.name
                    snippet = destination.address
                    icon = destPinDrawable
                    setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                }
                view.overlays.add(destMarker)
            }

            // 6. Current Driver Location Marker
            val driverMarker = OsmMarker(view).apply {
                position = GeoPoint(currentLocation.latitude, currentLocation.longitude)
                title = "Your Location"
                icon = driverIconDrawable
                setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
            }
            view.overlays.add(driverMarker)

            view.invalidate()
        }
    )
}

private fun createDriverMarkerDrawable(context: Context, isNavigationMode: Boolean): BitmapDrawable {
    val sizePx = 96
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RoutePilotBlue.copy(alpha = 0.24f).toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.46f, haloPaint)

    val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.31f, whiteBorderPaint)

    val blueFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RoutePilotBlue.toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.25f, blueFillPaint)

    if (isNavigationMode) {
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val path = android.graphics.Path().apply {
            moveTo(cx, cy - 13f)
            lineTo(cx - 10f, cy + 10f)
            lineTo(cx, cy + 5f)
            lineTo(cx + 10f, cy + 10f)
            close()
        }
        canvas.drawPath(path, arrowPaint)
    }

    return BitmapDrawable(context.resources, bmp)
}

private fun createDestinationPinDrawable(context: Context): BitmapDrawable {
    val w = 88
    val h = 104
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = w / 2f

    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HazardRed.toArgb()
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    val path = android.graphics.Path().apply {
        moveTo(cx, h - 6f)
        cubicTo(cx - 34f, h - 42f, cx - 32f, 12f, cx, 12f)
        cubicTo(cx + 32f, 12f, cx + 34f, h - 42f, cx, h - 6f)
        close()
    }
    canvas.drawPath(path, pinPaint)
    canvas.drawPath(path, borderPaint)

    val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, 40f, 11f, holePaint)

    return BitmapDrawable(context.resources, bmp)
}

private fun createHazardTriangleDrawable(context: Context, colorArgb: Int): BitmapDrawable {
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

    return BitmapDrawable(context.resources, bmp)
}
