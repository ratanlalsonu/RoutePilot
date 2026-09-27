package com.example.data.repository

import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.LocationPoint
import com.example.domain.model.RouteCalculationResult
import com.example.domain.repository.RoutingRepository
import com.example.domain.routing.AStarRoutingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoutingRepositoryImpl(
    private val aStarEngine: AStarRoutingEngine
) : RoutingRepository {

    override suspend fun calculateRoutes(
        origin: LocationPoint,
        destination: Destination,
        hazards: List<Hazard>,
        isRerouting: Boolean
    ): RouteCalculationResult = withContext(Dispatchers.Default) {
        aStarEngine.calculateRoutes(
            origin = origin,
            destination = destination,
            activeHazards = hazards,
            isRerouting = isRerouting
        )
    }
}
