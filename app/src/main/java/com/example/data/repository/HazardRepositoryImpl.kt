package com.example.data.repository

import com.example.data.local.HazardCacheEntity
import com.example.data.local.RoutePilotDao
import com.example.data.remote.FirestoreHazardDataSource
import com.example.data.remote.RemoteBackendClient
import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.HazardStatus
import com.example.domain.model.HazardType
import com.example.domain.model.OperatingMode
import com.example.domain.repository.HazardRepository
import com.example.domain.routing.OsmRoadNetworkProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HazardRepositoryImpl(
    private val dao: RoutePilotDao,
    private val firestoreDataSource: FirestoreHazardDataSource,
    private val restClient: RemoteBackendClient,
    private val externalScope: CoroutineScope
) : HazardRepository {

    companion object {
        private const val CACHE_FRESHNESS_MS = 30 * 60 * 1000L // 30 minutes
    }

    // Isolated Demo Mode state (never mixed into LIVE mode)
    private val demoHazardsFlow = MutableStateFlow<List<Hazard>>(
        listOf(
            // Non-interfering advisory hazard off the main route to show smart relevance filtering
            Hazard(
                id = "demo_hazard_cantonment_work",
                name = "Cantonment Link Road",
                type = HazardType.CONSTRUCTION,
                severity = HazardSeverity.LOW,
                status = HazardStatus.WARNING,
                latitude = 25.4410,
                longitude = 78.5490,
                radiusMeters = 180.0,
                description = "Minor lane maintenance along southern cantonment road",
                roadId = "edge_south_2",
                active = true,
                source = "DEMO"
            )
        )
    )

    override fun observeActiveHazards(mode: OperatingMode): Flow<List<Hazard>> {
        return when (mode) {
            OperatingMode.DEMO -> demoHazardsFlow
            OperatingMode.LIVE -> {
                combine(
                    firestoreDataSource.observeActiveHazardsRealtime()
                        .onEach { remoteList ->
                            if (remoteList.isNotEmpty()) {
                                dao.upsertCachedHazards(remoteList.map { HazardCacheEntity.fromDomain(it) })
                            }
                        },
                    dao.observeActiveCachedHazards()
                ) { realtimeHazards, cachedEntities ->
                    if (realtimeHazards.isNotEmpty()) {
                        realtimeHazards.filter { it.isEffectiveHazard }
                    } else {
                        val minFreshTimestamp = System.currentTimeMillis() - CACHE_FRESHNESS_MS
                        cachedEntities
                            .filter { it.cachedAtTimestamp >= minFreshTimestamp && it.source != "DEMO" }
                            .map { it.toDomain() }
                            .filter { it.isEffectiveHazard }
                    }
                }
            }
        }
    }

    override suspend fun getHazard(id: String): Hazard? {
        demoHazardsFlow.value.find { it.id == id }?.let { return it }
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

    /**
     * Simulates the Admin Website / IoT Backend publishing the critical Bridge B1 hazard
     * onto NH-27 during Demo Mode navigation (matching Screen 7 of the reference flow).
     */
    override fun triggerDemoBridgeHazard() {
        val bridgeB1Hazard = Hazard(
            id = "hazard_bridge_b1",
            name = "Bridge B1",
            type = HazardType.BRIDGE_DAMAGE,
            severity = HazardSeverity.CRITICAL,
            status = HazardStatus.BLOCKED,
            latitude = OsmRoadNetworkProvider.BRIDGE_B1_LAT,
            longitude = OsmRoadNetworkProvider.BRIDGE_B1_LNG,
            radiusMeters = 380.0,
            description = "Critical structural alert on Bridge B1 NH 27 Overpass — Road Blocked",
            roadId = OsmRoadNetworkProvider.BRIDGE_B1_ROAD_ID,
            bridgeId = OsmRoadNetworkProvider.BRIDGE_B1_ID,
            active = true,
            source = "DEMO",
            distanceAheadMeters = 2100.0
        )
        val current = demoHazardsFlow.value.filterNot { it.id == "hazard_bridge_b1" }
        demoHazardsFlow.value = current + bridgeB1Hazard
    }

    override fun updateDemoHazardStatusToWarning() {
        demoHazardsFlow.value = demoHazardsFlow.value.map { hazard ->
            if (hazard.id == "hazard_bridge_b1") {
                hazard.copy(
                    severity = HazardSeverity.MEDIUM,
                    status = HazardStatus.WARNING,
                    description = "Caution Advised on Bridge B1 — Speed Restriction Active",
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                hazard
            }
        }
    }

    override fun clearDemoHazards() {
        demoHazardsFlow.value = demoHazardsFlow.value.map { hazard ->
            if (hazard.id == "hazard_bridge_b1") {
                hazard.copy(
                    status = HazardStatus.CLEARED,
                    active = false,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                hazard
            }
        }.filter { it.isEffectiveHazard }
    }

    override fun resetDemoScenario() {
        externalScope.launch(Dispatchers.Default) {
            demoHazardsFlow.value = listOf(
                Hazard(
                    id = "demo_hazard_cantonment_work",
                    name = "Cantonment Link Road",
                    type = HazardType.CONSTRUCTION,
                    severity = HazardSeverity.LOW,
                    status = HazardStatus.WARNING,
                    latitude = 25.4410,
                    longitude = 78.5490,
                    radiusMeters = 180.0,
                    description = "Minor lane maintenance along southern cantonment road",
                    roadId = "edge_south_2",
                    active = true,
                    source = "DEMO"
                )
            )
        }
    }
}
