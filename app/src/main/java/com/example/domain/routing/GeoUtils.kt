package com.example.domain.routing

import com.example.domain.model.LocationPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle Haversine distance between two coordinates in meters.
     */
    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))
        return EARTH_RADIUS_METERS * c
    }

    fun haversineMeters(p1: LocationPoint, p2: LocationPoint): Double =
        haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

    /**
     * Calculates initial forward azimuth/bearing in degrees [0, 360) from p1 to p2.
     */
    fun calculateBearing(p1: LocationPoint, p2: LocationPoint): Float {
        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)
        val dLon = lon2 - lon1

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360.0) % 360.0).toFloat()
    }

    /**
     * Calculates the shortest distance in meters from point P to line segment AB.
     */
    fun distanceToSegmentMeters(
        pointLat: Double,
        pointLon: Double,
        segStartLat: Double,
        segStartLon: Double,
        segEndLat: Double,
        segEndLon: Double
    ): Double {
        val latScale = 111320.0
        val lonScale = 111320.0 * cos(Math.toRadians((segStartLat + segEndLat) / 2.0))

        val px = (pointLon - segStartLon) * lonScale
        val py = (pointLat - segStartLat) * latScale
        val bx = (segEndLon - segStartLon) * lonScale
        val by = (segEndLat - segStartLat) * latScale

        val segLenSq = bx * bx + by * by
        if (segLenSq <= 1e-6) {
            return sqrt(px * px + py * py)
        }
        val t = min(1.0, max(0.0, (px * bx + py * by) / segLenSq))
        val projX = t * bx
        val projY = t * by
        val dx = px - projX
        val dy = py - projY
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Determines a human-readable turn maneuver from two consecutive bearings.
     */
    fun determineTurnManeuver(prevBearing: Float, nextBearing: Float): String {
        var diff = (nextBearing - prevBearing) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return when {
            diff > 28f -> "RIGHT"
            diff < -28f -> "LEFT"
            else -> "STRAIGHT"
        }
    }

    /**
     * Formats distance in meters into responsive user-friendly km / m or mi / ft.
     * Uses 1-meter precision below 1 km and 0.01 km (10m) precision at or above 1 km
     * so travelling any distance immediately updates the displayed value.
     */
    fun formatDistance(meters: Double, useKilometers: Boolean = true): String {
        val safeMeters = max(0.0, meters)
        if (!useKilometers) {
            val miles = safeMeters / 1609.344
            return if (miles < 0.2) {
                val feet = (safeMeters * 3.28084).roundToInt()
                "${max(1, feet)} ft"
            } else {
                String.format(java.util.Locale.US, "%.2f mi", miles)
            }
        }
        return if (safeMeters < 1000.0) {
            val exactMeters = safeMeters.roundToInt()
            "${max(1, exactMeters)} m"
        } else {
            val km = safeMeters / 1000.0
            String.format(java.util.Locale.US, "%.2f km", km)
        }
    }

    fun formatDistanceKm(km: Double, useKilometers: Boolean = true): String {
        return formatDistance(km * 1000.0, useKilometers)
    }

    fun formatDurationMinutes(minutes: Int): String {
        if (minutes < 60) {
            return "$minutes min"
        }
        val hrs = minutes / 60
        val mins = minutes % 60
        return if (mins == 0) "${hrs} hr" else "${hrs} hr ${mins} min"
    }

    /**
     * Formats live remaining duration in seconds into a responsive display string
     * (e.g. "3 min 42 s" or "48 sec") so the user sees time counting down as they travel.
     */
    fun formatLiveRemainingTime(totalSeconds: Int): String {
        val safeSecs = max(1, totalSeconds)
        if (safeSecs < 60) {
            return "$safeSecs sec"
        }
        if (safeSecs < 3600) {
            val mins = safeSecs / 60
            val secs = safeSecs % 60
            return if (secs == 0) "$mins min" else "${mins} min ${secs} s"
        }
        val hrs = safeSecs / 3600
        val mins = (safeSecs % 3600) / 60
        val secs = safeSecs % 60
        return if (secs == 0) "${hrs} hr ${mins} min" else "${hrs}h ${mins}m ${secs}s"
    }

    /**
     * Projects [currentLocation] onto [routePoints] and calculates the remaining path length
     * from the projected point to the end of the polyline, scaled to [totalRouteMeters].
     * Returns null if the driver is far (> 220m) from the polyline.
     */
    fun computeRemainingPolylineDistanceMeters(
        currentLocation: LocationPoint,
        routePoints: List<LocationPoint>,
        totalRouteMeters: Double
    ): Double? {
        if (routePoints.size < 2) return null

        var bestSegIdx = 0
        var bestT = 0.0
        var bestDistToSeg = Double.MAX_VALUE

        val segmentLengths = DoubleArray(routePoints.size - 1)
        var totalPolyLen = 0.0

        for (i in 0 until routePoints.size - 1) {
            val a = routePoints[i]
            val b = routePoints[i + 1]
            val segLen = haversineMeters(a, b)
            segmentLengths[i] = segLen
            totalPolyLen += segLen

            val latScale = 111320.0
            val lonScale = 111320.0 * cos(Math.toRadians((a.latitude + b.latitude) / 2.0))
            val px = (currentLocation.longitude - a.longitude) * lonScale
            val py = (currentLocation.latitude - a.latitude) * latScale
            val bx = (b.longitude - a.longitude) * lonScale
            val by = (b.latitude - a.latitude) * latScale
            val lenSq = bx * bx + by * by
            val t = if (lenSq <= 1e-6) 0.0 else ((px * bx + py * by) / lenSq).coerceIn(0.0, 1.0)
            val projX = t * bx
            val projY = t * by
            val dist = sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))

            if (dist < bestDistToSeg) {
                bestDistToSeg = dist
                bestSegIdx = i
                bestT = t
            }
        }

        if (totalPolyLen <= 1.0) return null

        var remainingPolyLen = segmentLengths[bestSegIdx] * (1.0 - bestT)
        for (j in (bestSegIdx + 1) until segmentLengths.size) {
            remainingPolyLen += segmentLengths[j]
        }

        val scale = if (totalRouteMeters > 10.0) (totalRouteMeters / totalPolyLen) else 1.0
        val extraOffRouteMeters = if (bestDistToSeg > 8.0) bestDistToSeg else 0.0
        return (remainingPolyLen * scale + extraOffRouteMeters).coerceAtLeast(0.0)
    }
}
