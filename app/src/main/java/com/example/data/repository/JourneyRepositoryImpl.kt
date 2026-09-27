package com.example.data.repository

import com.example.data.local.JourneyEntity
import com.example.data.local.RoutePilotDao
import com.example.data.remote.FirestoreHazardDataSource
import com.example.domain.model.Journey
import com.example.domain.repository.JourneyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class JourneyRepositoryImpl(
    private val dao: RoutePilotDao,
    private val firestoreDataSource: FirestoreHazardDataSource
) : JourneyRepository {

    override suspend fun saveJourney(journey: Journey) {
        dao.insertJourney(JourneyEntity.fromDomain(journey))
        if (!journey.isDemoRecord) {
            firestoreDataSource.saveDriverJourney(journey)
        }
    }

    override fun getJourneyHistory(includeDemoSamples: Boolean): Flow<List<Journey>> {
        val sourceFlow = if (includeDemoSamples) {
            dao.observeAllJourneys()
        } else {
            dao.observeLiveJourneys()
        }
        return sourceFlow.map { entities ->
            val list = entities.map { it.toDomain() }
            if (list.isEmpty() && includeDemoSamples) {
                getDefaultDemoJourneySamples()
            } else {
                list
            }
        }
    }

    override suspend fun clearLocalHistory() {
        dao.clearAllJourneys()
    }

    private fun getDefaultDemoJourneySamples(): List<Journey> {
        val now = System.currentTimeMillis()
        return listOf(
            Journey(
                id = "demo_journey_1",
                userId = "demo_driver",
                sourceName = "Current Location",
                destinationName = "District Hospital",
                destinationAddress = "Jhansi, Uttar Pradesh",
                sourceLat = 25.4484,
                sourceLng = 78.5320,
                destLat = 25.4595,
                destLng = 78.5820,
                distanceKm = 17.6,
                durationMinutes = 30,
                startedAt = now - 86_400_000L,
                completedAt = now - 84_600_000L,
                status = "SAFELY_DIVERTED",
                hazardsAvoidedCount = 1,
                isDemoRecord = true
            ),
            Journey(
                id = "demo_journey_2",
                userId = "demo_driver",
                sourceName = "Sipri Crossing",
                destinationName = "Lucknow Railway Station",
                destinationAddress = "Charbagh, Lucknow, Uttar Pradesh",
                sourceLat = 26.8315,
                sourceLng = 80.9160,
                destLat = 26.8317,
                destLng = 80.9234,
                distanceKm = 18.4,
                durationMinutes = 32,
                startedAt = now - 172_800_000L,
                completedAt = now - 170_880_000L,
                status = "COMPLETED",
                hazardsAvoidedCount = 0,
                isDemoRecord = true
            )
        )
    }
}
