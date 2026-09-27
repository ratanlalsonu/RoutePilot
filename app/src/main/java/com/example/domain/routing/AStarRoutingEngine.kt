package com.example.domain.routing

import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.HazardStatus
import com.example.domain.model.LocationPoint
import com.example.domain.model.RoadEdge
import com.example.domain.model.RoadNode
import com.example.domain.model.RoadStatus
import com.example.domain.model.Route
import com.example.domain.model.RouteCalculationResult
import com.example.domain.model.RouteSegment
import java.util.PriorityQueue
import java.util.UUID
import kotlin.math.min

/**
 * Internal Hazard-Aware A* Routing Engine.
 *
 * Implements:
 * - Open Set (PriorityQueue ordered by fScore = gScore + heuristic)
 * - Closed Set (HashSet of visited node IDs)
 * - gScore (exact accumulated travel-time + hazard-penalty cost from source)
 * - fScore (gScore + Haversine geographic heuristic)
 * - cameFrom (predecessor node & edge reconstruction)
 * - Dynamic hazard evaluation along exact street geometries (`edge.geometry`)
 * - Full road-following polyline reconstruction (`Route.points`) so both the Best Route
 *   and Alternate Route highlight the actual roads on the map.
 */
class AStarRoutingEngine(
    private val roadNetworkProvider: OsmRoadNetworkProvider
) {

    private data class OpenSetEntry(
        val nodeId: String,
        val fScore: Double
    ) : Comparable<OpenSetEntry> {
        override fun compareTo(other: OpenSetEntry): Int = this.fScore.compareTo(other.fScore)
    }

    fun calculateRoutes(
        origin: LocationPoint,
        destination: Destination,
        activeHazards: List<Hazard>,
        isRerouting: Boolean = false
    ): RouteCalculationResult {
        val destPoint = LocationPoint(destination.latitude, destination.longitude)
        val rawGraph = roadNetworkProvider.getRoadGraphForRegion(origin, destPoint)

        // Apply active hazards to graph edges (checking full street geometry)
        val effectiveHazards = activeHazards.filter { it.isEffectiveHazard }
        val avoidedHazardIds = mutableSetOf<String>()

        val hazardAwareEdges = rawGraph.allEdges.map { edge ->
            val u = rawGraph.nodes[edge.fromNode]
            val v = rawGraph.nodes[edge.toNode]
            if (u == null || v == null) {
                edge
            } else {
                evaluateEdgeAgainstHazards(edge, u, v, effectiveHazards, avoidedHazardIds)
            }
        }

        val hazardGraph = rawGraph.copy(
            adjacency = hazardAwareEdges.groupBy { it.fromNode },
            allEdges = hazardAwareEdges
        )

        val startNodeId = if (hazardGraph.nodes.containsKey("N_ORIGIN")) {
            "N_ORIGIN"
        } else {
            findNearestNodeId(hazardGraph.nodes, origin) ?: return RouteCalculationResult(
                recommendedRoute = null,
                alternateRoute = null,
                errorMessage = "Unable to locate starting road segment."
            )
        }

        val goalNodeId = if (hazardGraph.nodes.containsKey("N_DEST")) {
            "N_DEST"
        } else {
            findNearestNodeId(hazardGraph.nodes, destPoint) ?: return RouteCalculationResult(
                recommendedRoute = null,
                alternateRoute = null,
                errorMessage = "Unable to locate destination road segment."
            )
        }

        // Run A* for primary Best (Recommended / Safest) route
        val primaryPath = runAStarSearch(
            graph = hazardGraph,
            startNodeId = startNodeId,
            goalNodeId = goalNodeId,
            penalizedEdgeIds = emptySet()
        )

        if (primaryPath == null) {
            return RouteCalculationResult(
                recommendedRoute = null,
                alternateRoute = null,
                errorMessage = "No safe alternative route found."
            )
        }

        val recommendedRoute = buildRouteFromPath(
            origin = origin,
            destination = destination,
            nodes = hazardGraph.nodes,
            pathNodes = primaryPath.first,
            pathEdges = primaryPath.second,
            isAlternative = false,
            isDivertedForSafety = isRerouting || avoidedHazardIds.isNotEmpty(),
            avoidedHazardIds = avoidedHazardIds.toList()
        )

        // Compute the Alternate Route by applying a strong diversity penalty to the edges of the primary path
        val primaryEdgeIds = if (primaryPath.second.size > 2) {
            primaryPath.second.map { it.id }.toSet()
        } else {
            primaryPath.second.map { it.id }.toSet()
        }

        val altPath = if (primaryEdgeIds.isNotEmpty()) {
            runAStarSearch(
                graph = hazardGraph,
                startNodeId = startNodeId,
                goalNodeId = goalNodeId,
                penalizedEdgeIds = primaryEdgeIds
            )
        } else {
            null
        }

        val alternateRoute = if (altPath != null && altPath.first != primaryPath.first) {
            buildRouteFromPath(
                origin = origin,
                destination = destination,
                nodes = hazardGraph.nodes,
                pathNodes = altPath.first,
                pathEdges = altPath.second,
                isAlternative = true,
                isDivertedForSafety = false,
                avoidedHazardIds = avoidedHazardIds.toList()
            )
        } else {
            null
        }

        return RouteCalculationResult(
            recommendedRoute = recommendedRoute,
            alternateRoute = alternateRoute,
            errorMessage = null
        )
    }

    private fun evaluateEdgeAgainstHazards(
        edge: RoadEdge,
        nodeU: RoadNode,
        nodeV: RoadNode,
        hazards: List<Hazard>,
        avoidedHazardIds: MutableSet<String>
    ): RoadEdge {
        var isBlocked = edge.blocked
        var totalPenalty = edge.hazardPenaltySeconds
        var worstStatus = edge.roadStatus

        for (hazard in hazards) {
            val matchesId = (!hazard.roadId.isNullOrBlank() && edge.id.contains(hazard.roadId, ignoreCase = true)) ||
                (!hazard.bridgeId.isNullOrBlank() && edge.id.contains(hazard.bridgeId, ignoreCase = true)) ||
                (hazard.name.contains("Bridge B1", ignoreCase = true) && edge.id.contains("bridge_b1", ignoreCase = true))

            val distToEdge = computeMinDistanceToEdgeGeometry(
                hazardLat = hazard.latitude,
                hazardLon = hazard.longitude,
                nodeU = nodeU,
                nodeV = nodeV,
                geometry = edge.geometry
            )

            if (matchesId || distToEdge <= hazard.radiusMeters) {
                avoidedHazardIds.add(hazard.id)
                when (hazard.status) {
                    HazardStatus.BLOCKED -> {
                        isBlocked = true
                        worstStatus = RoadStatus.BLOCKED
                    }
                    HazardStatus.PARTIALLY_BLOCKED -> {
                        worstStatus = RoadStatus.CRITICAL
                        totalPenalty += when (hazard.severity) {
                            HazardSeverity.CRITICAL -> 3600.0
                            HazardSeverity.HIGH -> 1800.0
                            HazardSeverity.MEDIUM -> 900.0
                            HazardSeverity.LOW -> 450.0
                        }
                    }
                    HazardStatus.WARNING -> {
                        if (worstStatus == RoadStatus.NORMAL) worstStatus = RoadStatus.WARNING
                        totalPenalty += when (hazard.severity) {
                            HazardSeverity.CRITICAL -> 2400.0
                            HazardSeverity.HIGH -> 1200.0
                            HazardSeverity.MEDIUM -> 600.0
                            HazardSeverity.LOW -> 240.0
                        }
                    }
                    HazardStatus.CLEARED -> Unit
                }
            }
        }

        return edge.copy(
            blocked = isBlocked,
            roadStatus = worstStatus,
            hazardPenaltySeconds = totalPenalty
        )
    }

    private fun computeMinDistanceToEdgeGeometry(
        hazardLat: Double,
        hazardLon: Double,
        nodeU: RoadNode,
        nodeV: RoadNode,
        geometry: List<LocationPoint>
    ): Double {
        if (geometry.size >= 2) {
            var minDist = Double.MAX_VALUE
            for (i in 0 until geometry.lastIndex) {
                val a = geometry[i]
                val b = geometry[i + 1]
                val d = GeoUtils.distanceToSegmentMeters(
                    pointLat = hazardLat,
                    pointLon = hazardLon,
                    segStartLat = a.latitude,
                    segStartLon = a.longitude,
                    segEndLat = b.latitude,
                    segEndLon = b.longitude
                )
                minDist = min(minDist, d)
            }
            return minDist
        }

        return GeoUtils.distanceToSegmentMeters(
            pointLat = hazardLat,
            pointLon = hazardLon,
            segStartLat = nodeU.latitude,
            segStartLon = nodeU.longitude,
            segEndLat = nodeV.latitude,
            segEndLon = nodeV.longitude
        )
    }

    /**
     * Core A* Graph Search: f(n) = g(n) + h(n).
     */
    private fun runAStarSearch(
        graph: RoadGraph,
        startNodeId: String,
        goalNodeId: String,
        penalizedEdgeIds: Set<String>
    ): Pair<List<String>, List<RoadEdge>>? {
        val goalNode = graph.nodes[goalNodeId] ?: return null
        val startNode = graph.nodes[startNodeId] ?: return null

        val openSet = PriorityQueue<OpenSetEntry>()
        val closedSet = HashSet<String>()
        val gScore = HashMap<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val fScore = HashMap<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val cameFromNode = HashMap<String, String>()
        val cameFromEdge = HashMap<String, RoadEdge>()

        gScore[startNodeId] = 0.0
        val initialH = heuristicCostSeconds(startNode, goalNode)
        fScore[startNodeId] = initialH
        openSet.add(OpenSetEntry(startNodeId, initialH))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll() ?: break
            val currentId = current.nodeId

            if (currentId == goalNodeId) {
                return reconstructPath(goalNodeId, cameFromNode, cameFromEdge)
            }

            if (!closedSet.add(currentId)) {
                continue
            }

            val outgoingEdges = graph.adjacency[currentId].orEmpty()
            for (edge in outgoingEdges) {
                // Strictly skip blocked roads/bridges
                if (edge.blocked || edge.roadStatus == RoadStatus.BLOCKED) {
                    continue
                }

                val neighborId = edge.toNode
                if (closedSet.contains(neighborId)) {
                    continue
                }

                val neighborNode = graph.nodes[neighborId] ?: continue

                // Edge traversal cost = travelTime + dynamic hazardPenalty + diversity penalty (for alternate route)
                val diversityMultiplier = if (penalizedEdgeIds.contains(edge.id)) 2.40 else 1.0
                val edgeCost = (edge.travelTimeSeconds * diversityMultiplier) + edge.hazardPenaltySeconds

                val tentativeG = gScore.getValue(currentId) + edgeCost
                if (tentativeG < gScore.getValue(neighborId)) {
                    cameFromNode[neighborId] = currentId
                    cameFromEdge[neighborId] = edge
                    gScore[neighborId] = tentativeG
                    val f = tentativeG + heuristicCostSeconds(neighborNode, goalNode)
                    fScore[neighborId] = f
                    openSet.add(OpenSetEntry(neighborId, f))
                }
            }
        }

        return null
    }

    /**
     * Admissible geographic Haversine heuristic converted to minimum travel time in seconds.
     */
    private fun heuristicCostSeconds(current: RoadNode, goal: RoadNode): Double {
        val distanceMeters = GeoUtils.haversineMeters(
            current.latitude,
            current.longitude,
            goal.latitude,
            goal.longitude
        )
        val maxHighwaySpeedMps = 25.0 // 90 km/h
        return distanceMeters / maxHighwaySpeedMps
    }

    private fun reconstructPath(
        goalNodeId: String,
        cameFromNode: Map<String, String>,
        cameFromEdge: Map<String, RoadEdge>
    ): Pair<List<String>, List<RoadEdge>> {
        val nodes = ArrayList<String>()
        val edges = ArrayList<RoadEdge>()
        var curr: String? = goalNodeId
        while (curr != null) {
            nodes.add(curr)
            val edge = cameFromEdge[curr]
            if (edge != null) {
                edges.add(edge)
            }
            curr = cameFromNode[curr]
        }
        nodes.reverse()
        edges.reverse()
        return nodes to edges
    }

    private fun buildRouteFromPath(
        origin: LocationPoint,
        destination: Destination,
        nodes: Map<String, RoadNode>,
        pathNodes: List<String>,
        pathEdges: List<RoadEdge>,
        isAlternative: Boolean,
        isDivertedForSafety: Boolean,
        avoidedHazardIds: List<String>
    ): Route {
        // Reconstruct full road-following geometry from each traversed RoadEdge
        val roadGeometryPoints = ArrayList<LocationPoint>()
        for (edge in pathEdges) {
            if (edge.geometry.isNotEmpty()) {
                for (pt in edge.geometry) {
                    val lastPt = roadGeometryPoints.lastOrNull()
                    if (lastPt == null ||
                        GeoUtils.haversineMeters(lastPt.latitude, lastPt.longitude, pt.latitude, pt.longitude) > 1.5
                    ) {
                        roadGeometryPoints.add(pt)
                    }
                }
            } else {
                val u = nodes[edge.fromNode]
                val v = nodes[edge.toNode]
                if (u != null) {
                    val pU = LocationPoint(u.latitude, u.longitude)
                    if (roadGeometryPoints.isEmpty()) roadGeometryPoints.add(pU)
                }
                if (v != null) {
                    roadGeometryPoints.add(LocationPoint(v.latitude, v.longitude))
                }
            }
        }

        if (roadGeometryPoints.size < 2) {
            pathNodes.mapNotNullTo(roadGeometryPoints) { nodeId ->
                nodes[nodeId]?.let { LocationPoint(it.latitude, it.longitude) }
            }
        }

        val totalDistanceMeters = pathEdges.sumOf { it.distanceMeters }
        val totalDurationSeconds = pathEdges.sumOf { it.travelTimeSeconds }

        val segments = ArrayList<RouteSegment>()
        var prevBearing: Float? = null

        for (edge in pathEdges) {
            val u = nodes[edge.fromNode] ?: continue
            val v = nodes[edge.toNode] ?: continue
            val pU = edge.geometry.firstOrNull() ?: LocationPoint(u.latitude, u.longitude)
            val pV = edge.geometry.lastOrNull() ?: LocationPoint(v.latitude, v.longitude)
            val bearing = GeoUtils.calculateBearing(pU, pV)
            val maneuver = prevBearing?.let { GeoUtils.determineTurnManeuver(it, bearing) } ?: "RIGHT"
            prevBearing = bearing

            val instructionText = when (maneuver) {
                "LEFT" -> "Turn left onto ${edge.roadName}"
                "RIGHT" -> "Turn right onto ${edge.roadName}"
                else -> "Continue onto ${edge.roadName}"
            }

            segments.add(
                RouteSegment(
                    instruction = instructionText,
                    roadName = edge.roadName,
                    distanceMeters = edge.distanceMeters,
                    durationSeconds = edge.travelTimeSeconds,
                    startPoint = pU,
                    endPoint = pV,
                    maneuverType = maneuver
                )
            )
        }

        val hasWarningEdges = pathEdges.any {
            it.roadStatus == RoadStatus.WARNING || it.roadStatus == RoadStatus.CRITICAL
        }
        val conditionSummary = when {
            isDivertedForSafety -> "Good (Safer Route Selected)"
            hasWarningEdges -> "Moderate / Caution Advised"
            else -> "Mostly Good"
        }

        return Route(
            id = UUID.randomUUID().toString(),
            origin = origin,
            destination = destination,
            nodeIds = pathNodes,
            points = roadGeometryPoints,
            segments = segments,
            totalDistanceMeters = totalDistanceMeters,
            estimatedDurationSeconds = totalDurationSeconds,
            roadConditionSummary = conditionSummary,
            isAlternative = isAlternative,
            isDivertedForSafety = isDivertedForSafety,
            avoidedHazardIds = avoidedHazardIds
        )
    }

    private fun findNearestNodeId(
        nodes: Map<String, RoadNode>,
        point: LocationPoint
    ): String? {
        return nodes.values.minByOrNull {
            GeoUtils.haversineMeters(point.latitude, point.longitude, it.latitude, it.longitude)
        }?.id
    }
}
