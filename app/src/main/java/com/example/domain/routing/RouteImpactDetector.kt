package com.example.domain.routing

import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.HazardStatus
import com.example.domain.model.LocationPoint
import com.example.domain.model.Route
import com.example.domain.model.RouteImpactAnalysis
import kotlin.math.min

/**
 * Smart Hazard Relevance & Route Impact Detector.
 *
 * Evaluates whether active backend/IoT hazards actually lie on or near the driver's
 * remaining active route geometry, and detects off-route GPS deviations.
 */
class RouteImpactDetector {

    companion object {
        const val OFF_ROUTE_THRESHOLD_METERS = 140.0
        const val ARRIVAL_PROXIMITY_METERS = 85.0
    }

    /**
     * Checks if any active hazard affects the remaining path of [activeRoute] ahead of [currentLocation].
     */
    fun analyzeRouteImpact(
        currentLocation: LocationPoint,
        activeRoute: Route,
        activeHazards: List<Hazard>
    ): RouteImpactAnalysis {
        val points = activeRoute.points
        if (points.size < 2) {
            return RouteImpactAnalysis(
                isAffected = false,
                requiresImmediateReroute = false,
                primaryAffectingHazard = null,
                allRelevantHazards = emptyList(),
                distanceToHazardMeters = null
            )
        }

        // Find the closest index along the route to the driver's current GPS position
        val closestIndex = points.indices.minByOrNull { idx ->
            GeoUtils.haversineMeters(currentLocation, points[idx])
        } ?: 0

        val effectiveHazards = activeHazards.filter { it.isEffectiveHazard }
        val affectingHazards = ArrayList<Hazard>()

        for (hazard in effectiveHazards) {
            // If this route already explicitly avoided this hazard, check actual geometry distance
            var minDistToAheadRoute = Double.POSITIVE_INFINITY
            var nearestRoutePointIndex = closestIndex

            for (i in closestIndex until (points.size - 1)) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val segDist = GeoUtils.distanceToSegmentMeters(
                    pointLat = hazard.latitude,
                    pointLon = hazard.longitude,
                    segStartLat = p1.latitude,
                    segStartLon = p1.longitude,
                    segEndLat = p2.latitude,
                    segEndLon = p2.longitude
                )
                if (segDist < minDistToAheadRoute) {
                    minDistToAheadRoute = segDist
                    nearestRoutePointIndex = i
                }
            }

            // Only consider the hazard relevant if it intersects the remaining route within its hazard radius
            if (minDistToAheadRoute <= hazard.radiusMeters) {
                val directDistMeters = GeoUtils.haversineMeters(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    hazard.latitude,
                    hazard.longitude
                )
                // Compute distance ahead along the route
                var routeAheadDist = 0.0
                for (j in closestIndex until nearestRoutePointIndex) {
                    routeAheadDist += GeoUtils.haversineMeters(points[j], points[j + 1])
                }
                val displayDist = if (routeAheadDist > 100.0) routeAheadDist * 2.1 else directDistMeters

                affectingHazards.add(
                    hazard.copy(distanceAheadMeters = displayDist)
                )
            }
        }

        if (affectingHazards.isEmpty()) {
            return RouteImpactAnalysis(
                isAffected = false,
                requiresImmediateReroute = false,
                primaryAffectingHazard = null,
                allRelevantHazards = emptyList(),
                distanceToHazardMeters = null
            )
        }

        // Prioritize blocked / critical hazards closest ahead
        val sorted = affectingHazards.sortedWith(
            compareByDescending<Hazard> { it.status == HazardStatus.BLOCKED }
                .thenByDescending { it.severity.ordinal }
                .thenBy { it.distanceAheadMeters ?: Double.MAX_VALUE }
        )

        val primary = sorted.first()
        val needsReroute = primary.status == HazardStatus.BLOCKED ||
            primary.status == HazardStatus.PARTIALLY_BLOCKED ||
            primary.severity == HazardSeverity.CRITICAL ||
            primary.severity == HazardSeverity.HIGH

        return RouteImpactAnalysis(
            isAffected = true,
            requiresImmediateReroute = needsReroute,
            primaryAffectingHazard = primary,
            allRelevantHazards = sorted,
            distanceToHazardMeters = primary.distanceAheadMeters
        )
    }

    /**
     * Checks whether the driver has significantly deviated from the active route polyline.
     */
    fun isDriverOffRoute(
        currentLocation: LocationPoint,
        activeRoute: Route
    ): Boolean {
        val points = activeRoute.points
        if (points.size < 2) return false

        var minDist = Double.POSITIVE_INFINITY
        for (i in 0 until (points.size - 1)) {
            val d = GeoUtils.distanceToSegmentMeters(
                pointLat = currentLocation.latitude,
                pointLon = currentLocation.longitude,
                segStartLat = points[i].latitude,
                segStartLon = points[i].longitude,
                segEndLat = points[i + 1].latitude,
                segEndLon = points[i + 1].longitude
            )
            minDist = min(minDist, d)
        }
        return minDist > OFF_ROUTE_THRESHOLD_METERS
    }

    /**
     * Checks whether the driver has arrived within destination proximity radius.
     */
    fun hasArrivedAtDestination(
        currentLocation: LocationPoint,
        destinationLat: Double,
        destinationLng: Double
    ): Boolean {
        val dist = GeoUtils.haversineMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            destinationLat,
            destinationLng
        )
        return dist <= ARRIVAL_PROXIMITY_METERS
    }
}
