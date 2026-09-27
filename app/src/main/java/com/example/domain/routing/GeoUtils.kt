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
     * Formats distance in meters into user-friendly km / m or mi / ft.
     */
    fun formatDistance(meters: Double, useKilometers: Boolean = true): String {
        if (!useKilometers) {
            val miles = meters / 1609.344
            return if (miles < 0.2) {
                val feet = (meters * 3.28084 / 50.0).roundToInt() * 50
                "${max(50, feet)} ft"
            } else {
                String.format(java.util.Locale.US, "%.1f mi", miles)
            }
        }
        return if (meters < 1000.0) {
            val roundedMeters = (meters / 50.0).roundToInt() * 50
            "${max(50, roundedMeters)} m"
        } else {
            val km = meters / 1000.0
            String.format(java.util.Locale.US, "%.1f km", km)
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
}
