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
        firestoreDataSource.saveDriverJourney(journey)
    }

    override fun getJourneyHistory(includeDemoSamples: Boolean): Flow<List<Journey>> {
        return dao.observeLiveJourneys().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun clearLocalHistory() {
        dao.clearAllJourneys()
    }
}
