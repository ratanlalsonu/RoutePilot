package com.example.data.repository

import com.example.data.local.HazardCacheEntity
import com.example.data.local.RoutePilotDao
import com.example.data.remote.FirestoreHazardDataSource
import com.example.data.remote.RemoteBackendClient
import com.example.domain.model.Hazard
import com.example.domain.model.OperatingMode
import com.example.domain.repository.HazardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach

/**
 * Pure Live Mode HazardRepository implementation.
 *
 * Subscribes in real time to the shared Firebase Firestore `hazards` collection
 * (`whereEqualTo("active", true)`) updated by the Admin Panel Website and IoT backend,
 * and caches fresh hazards locally in Room for offline resilience.
 */
class HazardRepositoryImpl(
    private val dao: RoutePilotDao,
    private val firestoreDataSource: FirestoreHazardDataSource,
    private val restClient: RemoteBackendClient,
    @Suppress("unused") private val externalScope: CoroutineScope
) : HazardRepository {

    companion object {
        private const val CACHE_FRESHNESS_MS = 30 * 60 * 1000L // 30 minutes
    }

    override fun observeActiveHazards(mode: OperatingMode): Flow<List<Hazard>> {
        return combine(
            firestoreDataSource.observeActiveHazardsRealtime()
                .onEach { remoteList ->
                    if (remoteList.isNotEmpty()) {
                        dao.upsertCachedHazards(remoteList.map { HazardCacheEntity.fromDomain(it) })
                    }
                }
                .catch { emit(emptyList()) },
            com.example.data.remote.UserConsoleFirestoreBridge.observeActiveConsoleHazards()
                .onEach { consoleList ->
                    if (consoleList.isNotEmpty()) {
                        dao.upsertCachedHazards(consoleList.map { HazardCacheEntity.fromDomain(it) })
                    } else {
                        dao.deleteStaleCachedHazards(Long.MAX_VALUE)
                    }
                }
                .catch { emit(emptyList()) },
            dao.observeActiveCachedHazards()
        ) { realtimeHazards, consoleHazards, _ ->
            (realtimeHazards + consoleHazards)
                .associateBy { it.id }
                .values
                .filter { it.isEffectiveHazard }
                .toList()
        }
    }

    override suspend fun getHazard(id: String): Hazard? {
        firestoreDataSource.getHazardById(id)?.let { return it }
        return dao.getCachedHazardById(id)?.toDomain()
    }

    override suspend fun refreshHazardsFromBackend(): Result<List<Hazard>> {
        val result = restClient.fetchActiveHazardsFromRest()
        result.onSuccess { list ->
            val minFresh = System.currentTimeMillis() - CACHE_FRESHNESS_MS
            dao.deleteStaleCachedHazards(minFresh)
            dao.upsertCachedHazards(list.map { HazardCacheEntity.fromDomain(it) })
        }
        return result
    }

    override fun triggerDemoBridgeHazard() = Unit
    override fun updateDemoHazardStatusToWarning() = Unit
    override fun clearDemoHazards() = Unit
    override fun resetDemoScenario() = Unit
}
