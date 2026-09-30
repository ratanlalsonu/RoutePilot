package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class OperatingMode {
    LIVE,
    DEMO
}

enum class HazardType(val displayName: String) {
    BRIDGE_DAMAGE("Bridge Structural Damage"),
    ROAD_BLOCK("Road Blockade"),
    FLOOD("Waterlogging / Flood"),
    ACCIDENT("Traffic Accident"),
    CONSTRUCTION("Road Construction"),
    HIGH_VIBRATION("Bridge Structural Alert"),
    ABNORMAL_TILT("Bridge Pier Tilt Alert"),
    HIGH_STRAIN("Bridge Overload Alert"),
    HIGH_DISPLACEMENT("Bridge Deck Alert"),
    HIGH_WATER_LEVEL("Bridge Flood Level Alert"),
    RESTRICTED_ROAD("Restricted Corridor"),
    OTHER("Road Hazard")
}

enum class HazardSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class HazardStatus {
    WARNING,
    PARTIALLY_BLOCKED,
    BLOCKED,
    CLEARED
}

enum class RoadStatus {
    NORMAL,
    WARNING,
    CRITICAL,
    BLOCKED
}

@Immutable
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float = 0f,
    val speedMps: Float = 0f,
    val accuracyMeters: Float = 10f,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val languageCode: String = "en"
)

@Immutable
data class Destination(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String = "Place",
    val distanceFromUserKm: Double? = null,
    val isDemoSample: Boolean = false,
    val lastVisitedTimestamp: Long = System.currentTimeMillis()
)

@Immutable
data class RoadNode(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val name: String = ""
)

@Immutable
data class RoadEdge(
    val id: String,
    val fromNode: String,
    val toNode: String,
    val roadName: String,
    val distanceMeters: Double,
    val travelTimeSeconds: Double,
    val roadType: String = "highway",
    val speedLimitKmh: Int = 60,
    val roadStatus: RoadStatus = RoadStatus.NORMAL,
    val blocked: Boolean = false,
    val hazardPenaltySeconds: Double = 0.0,
    val geometry: List<LocationPoint> = emptyList()
)

@Immutable
data class RouteSegment(
    val instruction: String,
    val roadName: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val startPoint: LocationPoint,
    val endPoint: LocationPoint,
    val maneuverType: String = "STRAIGHT" // LEFT, RIGHT, STRAIGHT, ARRIVE
)

@Immutable
data class Route(
    val id: String,
    val origin: LocationPoint,
    val destination: Destination,
    val nodeIds: List<String>,
    val points: List<LocationPoint>,
    val segments: List<RouteSegment>,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Double,
    val roadConditionSummary: String = "Mostly Good",
    val isAlternative: Boolean = false,
    val isDivertedForSafety: Boolean = false,
    val avoidedHazardIds: List<String> = emptyList()
) {
    val distanceKm: Double
        get() = totalDistanceMeters / 1000.0

    val durationMinutes: Int
        get() = kotlin.math.max(1, kotlin.math.round(estimatedDurationSeconds / 60.0).toInt())
}

@Immutable
data class Hazard(
    val id: String,
    val name: String,
    val type: HazardType,
    val severity: HazardSeverity,
    val status: HazardStatus,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 350.0,
    val description: String = "",
    val roadId: String? = null,
    val bridgeId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val active: Boolean = true,
    val source: String = "BACKEND", // BACKEND, IOT_ESP32, DEMO
    val distanceAheadMeters: Double? = null
) {
    val isEffectiveHazard: Boolean
        get() = active && status != HazardStatus.CLEARED
}

@Immutable
data class Journey(
    val id: String,
    val userId: String,
    val sourceName: String,
    val destinationName: String,
    val destinationAddress: String,
    val sourceLat: Double,
    val sourceLng: Double,
    val destLat: Double,
    val destLng: Double,
    val distanceKm: Double,
    val durationMinutes: Int,
    val startedAt: Long,
    val completedAt: Long,
    val status: String, // COMPLETED, SAFELY_DIVERTED, CANCELLED
    val hazardsAvoidedCount: Int = 0,
    val isDemoRecord: Boolean = false
)

@Immutable
data class SensorStatus(
    val sensorId: String,
    val bridgeId: String,
    val status: String,
    val lastUpdated: Long
)

@Immutable
data class RouteCalculationResult(
    val recommendedRoute: Route?,
    val alternateRoute: Route?,
    val errorMessage: String? = null
)

@Immutable
data class RouteImpactAnalysis(
    val isAffected: Boolean,
    val requiresImmediateReroute: Boolean,
    val primaryAffectingHazard: Hazard?,
    val allRelevantHazards: List<Hazard>,
    val distanceToHazardMeters: Double?
)
