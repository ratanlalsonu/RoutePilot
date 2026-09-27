package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.HazardStatus
import com.example.domain.model.HazardType
import com.example.domain.model.Journey

@Entity(tableName = "recent_destinations")
data class DestinationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val distanceFromUserKm: Double?,
    val isDemoSample: Boolean,
    val lastVisitedTimestamp: Long
) {
    fun toDomain(): Destination = Destination(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        category = category,
        distanceFromUserKm = distanceFromUserKm,
        isDemoSample = isDemoSample,
        lastVisitedTimestamp = lastVisitedTimestamp
    )

    companion object {
        fun fromDomain(dest: Destination): DestinationEntity = DestinationEntity(
            id = dest.id,
            name = dest.name,
            address = dest.address,
            latitude = dest.latitude,
            longitude = dest.longitude,
            category = dest.category,
            distanceFromUserKm = dest.distanceFromUserKm,
            isDemoSample = dest.isDemoSample,
            lastVisitedTimestamp = dest.lastVisitedTimestamp
        )
    }
}

@Entity(tableName = "journeys")
data class JourneyEntity(
    @PrimaryKey val id: String,
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
    val status: String,
    val hazardsAvoidedCount: Int,
    val isDemoRecord: Boolean
) {
    fun toDomain(): Journey = Journey(
        id = id,
        userId = userId,
        sourceName = sourceName,
        destinationName = destinationName,
        destinationAddress = destinationAddress,
        sourceLat = sourceLat,
        sourceLng = sourceLng,
        destLat = destLat,
        destLng = destLng,
        distanceKm = distanceKm,
        durationMinutes = durationMinutes,
        startedAt = startedAt,
        completedAt = completedAt,
        status = status,
        hazardsAvoidedCount = hazardsAvoidedCount,
        isDemoRecord = isDemoRecord
    )

    companion object {
        fun fromDomain(journey: Journey): JourneyEntity = JourneyEntity(
            id = journey.id,
            userId = journey.userId,
            sourceName = journey.sourceName,
            destinationName = journey.destinationName,
            destinationAddress = journey.destinationAddress,
            sourceLat = journey.sourceLat,
            sourceLng = journey.sourceLng,
            destLat = journey.destLat,
            destLng = journey.destLng,
            distanceKm = journey.distanceKm,
            durationMinutes = journey.durationMinutes,
            startedAt = journey.startedAt,
            completedAt = journey.completedAt,
            status = journey.status,
            hazardsAvoidedCount = journey.hazardsAvoidedCount,
            isDemoRecord = journey.isDemoRecord
        )
    }
}

@Entity(tableName = "cached_hazards")
data class HazardCacheEntity(
    @PrimaryKey val id: String,
    val name: String,
    val typeName: String,
    val severityName: String,
    val statusName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val description: String,
    val roadId: String?,
    val bridgeId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val active: Boolean,
    val source: String,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): Hazard = Hazard(
        id = id,
        name = name,
        type = runCatching { HazardType.valueOf(typeName) }.getOrDefault(HazardType.OTHER),
        severity = runCatching { HazardSeverity.valueOf(severityName) }.getOrDefault(HazardSeverity.MEDIUM),
        status = runCatching { HazardStatus.valueOf(statusName) }.getOrDefault(HazardStatus.WARNING),
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        description = description,
        roadId = roadId,
        bridgeId = bridgeId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        active = active,
        source = source
    )

    companion object {
        fun fromDomain(hazard: Hazard): HazardCacheEntity = HazardCacheEntity(
            id = hazard.id,
            name = hazard.name,
            typeName = hazard.type.name,
            severityName = hazard.severity.name,
            statusName = hazard.status.name,
            latitude = hazard.latitude,
            longitude = hazard.longitude,
            radiusMeters = hazard.radiusMeters,
            description = hazard.description,
            roadId = hazard.roadId,
            bridgeId = hazard.bridgeId,
            createdAt = hazard.createdAt,
            updatedAt = hazard.updatedAt,
            active = hazard.active,
            source = hazard.source
        )
    }
}
