package com.example.domain.routing

import com.example.BuildConfig
import com.example.domain.model.LocationPoint
import com.example.domain.model.RoadEdge
import com.example.domain.model.RoadNode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents a directed road network graph used by the internal A* routing engine.
 */
data class RoadGraph(
    val nodes: Map<String, RoadNode>,
    val adjacency: Map<String, List<RoadEdge>>,
    val allEdges: List<RoadEdge>
)

/**
 * Provides 100% exact, unrounded road-centerline graphs (`RoadNode` intersections + `RoadEdge` street geometries)
 * between `origin` and `destination` so that RoutePilot's Hazard-Aware A* engine highlights the
 * Best Route and Alternate Route strictly on the exact road path on Google Maps.
 *
 * Strategy:
 * 1. Primary: Google Maps Directions API (`alternatives=true`) using `BuildConfig.MAPS_API_KEY`,
 *    preserving 100% of decoded step polyline vertices without coordinate rounding or off-road connectors.
 * 2. Secondary (when Directions Web Service is not enabled on the key): Queries multiple real street-geometry
 *    routing clusters (`routing.openstreetmap.de/routed-car`, `router.project-osrm.org`,
 *    `routing.openstreetmap.de/routed-bike`, and `valhalla1.openstreetmap.de/route`) with full unsimplified
 *    road centerline geometry.
 * 3. Offline fallback (used only when device has no internet or in local JVM unit tests).
 */
class OsmRoadNetworkProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private data class ParsedRoadStep(
        val points: List<LocationPoint>,
        val distanceMeters: Double,
        val durationSeconds: Double,
        val roadName: String
    )

    fun getRoadGraphForRegion(
        origin: LocationPoint,
        destination: LocationPoint
    ): RoadGraph {
        // 1. Primary: Google Maps Directions API (alternatives=true) for 100% exact Google Maps road geometry
        val googleDirectionsGraph = runCatching {
            fetchGoogleDirectionsRoadGraph(origin, destination)
        }.getOrNull()

        if (googleDirectionsGraph != null && googleDirectionsGraph.allEdges.isNotEmpty()) {
            return googleDirectionsGraph
        }

        // 2. Secondary: Real street-centerline GeoJSON geometry from OSRM & Valhalla road servers
        val realStreetGraph = runCatching {
            fetchRealStreetGeometryGraph(origin, destination)
        }.getOrNull()

        if (realStreetGraph != null && realStreetGraph.allEdges.isNotEmpty()) {
            return realStreetGraph
        }

        // 3. Offline fallback (used only when device has no internet or in local JVM unit tests)
        return buildMultiCorridorGraph(origin, destination)
    }

    private fun fetchGoogleDirectionsRoadGraph(
        origin: LocationPoint,
        destination: LocationPoint
    ): RoadGraph? {
        val apiKey = runCatching {
            BuildConfig.MAPS_API_KEY.ifBlank { BuildConfig.PLACES_API_KEY }
        }.getOrDefault("")
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_") || apiKey == "MY_MAPS_API_KEY") return null

        val url = String.format(
            Locale.US,
            "https://maps.googleapis.com/maps/api/directions/json?origin=%.6f,%.6f&destination=%.6f,%.6f&alternatives=true&mode=driving&key=%s",
            origin.latitude,
            origin.longitude,
            destination.latitude,
            destination.longitude,
            apiKey
        )

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RoutePilot-Driver-Android/1.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            if (root.optString("status") != "OK") return null

            val routesArray = root.optJSONArray("routes") ?: return null
            if (routesArray.length() == 0) return null

            val nodes = LinkedHashMap<String, RoadNode>()
            val edges = ArrayList<RoadEdge>()

            val firstRouteSteps = extractGoogleParsedSteps(routesArray.getJSONObject(0), 0)
            if (firstRouteSteps.isEmpty()) return null

            val snappedOrigin = firstRouteSteps.first().points.first()
            val snappedDest = firstRouteSteps.last().points.last()

            val originNode = RoadNode("N_ORIGIN", snappedOrigin.latitude, snappedOrigin.longitude, "Road Start")
            val destNode = RoadNode("N_DEST", snappedDest.latitude, snappedDest.longitude, "Road Destination")
            nodes[originNode.id] = originNode
            nodes[destNode.id] = destNode

            for (rIdx in 0 until routesArray.length()) {
                val parsedSteps = if (rIdx == 0) {
                    firstRouteSteps
                } else {
                    extractGoogleParsedSteps(routesArray.getJSONObject(rIdx), rIdx)
                }
                appendParsedStepsToGraph(
                    parsedSteps = parsedSteps,
                    routeIndex = rIdx,
                    prefix = "GDIR",
                    originNode = originNode,
                    destNode = destNode,
                    nodes = nodes,
                    edges = edges
                )
            }

            if (routesArray.length() == 1) {
                runCatching {
                    fetchGoogleSecondaryCorridor(
                        apiKey = apiKey,
                        origin = origin,
                        destination = destination,
                        primarySteps = firstRouteSteps,
                        originNode = originNode,
                        destNode = destNode,
                        nodes = nodes,
                        edges = edges
                    )
                }
            }

            if (edges.isEmpty()) return null
            return RoadGraph(
                nodes = nodes,
                adjacency = edges.groupBy { it.fromNode },
                allEdges = edges
            )
        }
    }

    private fun fetchGoogleSecondaryCorridor(
        apiKey: String,
        origin: LocationPoint,
        destination: LocationPoint,
        primarySteps: List<ParsedRoadStep>,
        originNode: RoadNode,
        destNode: RoadNode,
        nodes: MutableMap<String, RoadNode>,
        edges: MutableList<RoadEdge>
    ) {
        val allPrimaryPts = primarySteps.flatMap { it.points }
        val midPt = if (allPrimaryPts.isNotEmpty()) {
            allPrimaryPts[allPrimaryPts.size / 2]
        } else {
            LocationPoint((origin.latitude + destination.latitude) / 2.0, (origin.longitude + destination.longitude) / 2.0)
        }

        val dLat = destination.latitude - origin.latitude
        val dLng = destination.longitude - origin.longitude
        val span = sqrt(dLat * dLat + dLng * dLng).coerceAtLeast(0.010)
        val perpLat = (-dLng / span) * (span * 0.26)
        val perpLng = (dLat / span) * (span * 0.26)

        val candidateOffsets = listOf(1.0, -1.0)
        var routeIdx = 1
        for (sign in candidateOffsets) {
            val viaLat = midPt.latitude + perpLat * sign
            val viaLng = midPt.longitude + perpLng * sign
            val url = String.format(
                Locale.US,
                "https://maps.googleapis.com/maps/api/directions/json?origin=%.6f,%.6f&destination=%.6f,%.6f&waypoints=via:%.6f,%.6f&mode=driving&key=%s",
                origin.latitude,
                origin.longitude,
                destination.latitude,
                destination.longitude,
                viaLat,
                viaLng,
                apiKey
            )
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "RoutePilot-Driver-Android/1.0")
                .get()
                .build()
            runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val routes = JSONObject(body).optJSONArray("routes") ?: return@use
                    if (routes.length() > 0) {
                        val steps = extractGoogleParsedSteps(routes.getJSONObject(0), routeIdx)
                        if (steps.isNotEmpty()) {
                            appendParsedStepsToGraph(
                                parsedSteps = steps,
                                routeIndex = routeIdx,
                                prefix = "GDIR_ALT",
                                originNode = originNode,
                                destNode = destNode,
                                nodes = nodes,
                                edges = edges
                            )
                            routeIdx++
                        }
                    }
                }
            }
        }
    }

    private fun extractGoogleParsedSteps(
        routeObj: JSONObject,
        routeIndex: Int
    ): List<ParsedRoadStep> {
        val summaryRoadName = routeObj.optString("summary").takeIf { it.isNotBlank() }
            ?: if (routeIndex == 0) "Recommended Main Road" else "Alternate Road $routeIndex"

        val result = ArrayList<ParsedRoadStep>()
        val legs = routeObj.optJSONArray("legs") ?: return emptyList()

        for (lIdx in 0 until legs.length()) {
            val leg = legs.getJSONObject(lIdx)
            val steps = leg.optJSONArray("steps") ?: continue

            for (sIdx in 0 until steps.length()) {
                val step = steps.getJSONObject(sIdx)
                val encodedPoly = step.optJSONObject("polyline")?.optString("points").orEmpty()
                val decodedPoints = if (encodedPoly.isNotBlank()) {
                    decodeGooglePolyline(encodedPoly, 1E5)
                } else {
                    emptyList()
                }
                if (decodedPoints.size < 2) continue

                val distObj = step.optJSONObject("distance")
                val durObj = step.optJSONObject("duration")
                val stepMeters = max(5.0, distObj?.optDouble("value", computePolylineLengthMeters(decodedPoints)) ?: 50.0)
                val stepSeconds = max(2.0, durObj?.optDouble("value", stepMeters / 12.0) ?: 8.0)

                val htmlInstr = step.optString("html_instructions", "")
                    .replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val roadName = htmlInstr.takeIf { it.isNotBlank() } ?: summaryRoadName

                result.add(
                    ParsedRoadStep(
                        points = decodedPoints,
                        distanceMeters = stepMeters,
                        durationSeconds = stepSeconds,
                        roadName = roadName
                    )
                )
            }
        }

        if (result.isEmpty()) {
            val overviewEncoded = routeObj.optJSONObject("overview_polyline")?.optString("points").orEmpty()
            val overviewPoints = if (overviewEncoded.isNotBlank()) decodeGooglePolyline(overviewEncoded, 1E5) else emptyList()
            if (overviewPoints.size >= 2) {
                val dist = max(10.0, computePolylineLengthMeters(overviewPoints))
                result.add(
                    ParsedRoadStep(
                        points = overviewPoints,
                        distanceMeters = dist,
                        durationSeconds = max(5.0, dist / 12.0),
                        roadName = summaryRoadName
                    )
                )
            }
        }
        return result
    }

    /**
     * Queries real road-centerline geometry from high-availability OSRM and Valhalla routing servers
     * when Google Directions API web service is not enabled on the user's key.
     * Preserves 100% of road vertices with exact Double precision so routes stay strictly on the road.
     */
    private fun fetchRealStreetGeometryGraph(
        origin: LocationPoint,
        destination: LocationPoint
    ): RoadGraph? {
        val osrmServers = listOf(
            "https://routing.openstreetmap.de/routed-car/route/v1/driving",
            "https://router.project-osrm.org/route/v1/driving",
            "https://routing.openstreetmap.de/routed-bike/route/v1/driving"
        )

        for (baseUrl in osrmServers) {
            val graph = runCatching {
                fetchOsrmServerGraph(baseUrl, origin, destination)
            }.getOrNull()
            if (graph != null && graph.allEdges.isNotEmpty()) {
                return graph
            }
        }

        // Fallback to Valhalla OpenStreetMap real road router (supports alternates natively)
        val valhallaGraph = runCatching {
            fetchValhallaRoadGraph(origin, destination)
        }.getOrNull()
        if (valhallaGraph != null && valhallaGraph.allEdges.isNotEmpty()) {
            return valhallaGraph
        }

        return null
    }

    private fun fetchOsrmServerGraph(
        baseUrl: String,
        origin: LocationPoint,
        destination: LocationPoint
    ): RoadGraph? {
        // Use standard boolean alternatives=true for universal OSRM v5 compatibility
        val url = String.format(
            Locale.US,
            "%s/%.6f,%.6f;%.6f,%.6f?alternatives=true&steps=true&geometries=geojson&overview=full",
            baseUrl,
            origin.longitude,
            origin.latitude,
            destination.longitude,
            destination.latitude
        )

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RoutePilot-Driver-Android/1.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val routesArray = root.optJSONArray("routes") ?: return null
            if (routesArray.length() == 0) return null

            val firstRouteSteps = extractOsrmParsedSteps(routesArray.getJSONObject(0), 0)
            if (firstRouteSteps.isEmpty()) return null

            val nodes = LinkedHashMap<String, RoadNode>()
            val edges = ArrayList<RoadEdge>()

            val snappedOrigin = firstRouteSteps.first().points.first()
            val snappedDest = firstRouteSteps.last().points.last()

            val originNode = RoadNode("N_ORIGIN", snappedOrigin.latitude, snappedOrigin.longitude, "Road Start")
            val destNode = RoadNode("N_DEST", snappedDest.latitude, snappedDest.longitude, "Road Destination")
            nodes[originNode.id] = originNode
            nodes[destNode.id] = destNode

            for (rIdx in 0 until routesArray.length()) {
                val parsedSteps = if (rIdx == 0) {
                    firstRouteSteps
                } else {
                    extractOsrmParsedSteps(routesArray.getJSONObject(rIdx), rIdx)
                }
                appendParsedStepsToGraph(
                    parsedSteps = parsedSteps,
                    routeIndex = rIdx,
                    prefix = "OSRM",
                    originNode = originNode,
                    destNode = destNode,
                    nodes = nodes,
                    edges = edges
                )
            }

            // If OSRM returned only 1 route, query an alternate road profile or continue_straight via route
            if (routesArray.length() == 1) {
                runCatching {
                    fetchOsrmSecondaryCorridor(
                        primaryBaseUrl = baseUrl,
                        origin = origin,
                        destination = destination,
                        primarySteps = firstRouteSteps,
                        originNode = originNode,
                        destNode = destNode,
                        nodes = nodes,
                        edges = edges
                    )
                }
            }

            if (edges.isEmpty()) return null
            return RoadGraph(
                nodes = nodes,
                adjacency = edges.groupBy { it.fromNode },
                allEdges = edges
            )
        }
    }

    private fun extractOsrmParsedSteps(
        routeObj: JSONObject,
        routeIndex: Int
    ): List<ParsedRoadStep> {
        val result = ArrayList<ParsedRoadStep>()
        val legsArray = routeObj.optJSONArray("legs")

        if (legsArray != null) {
            for (lIdx in 0 until legsArray.length()) {
                val legObj = legsArray.getJSONObject(lIdx)
                val stepsArray = legObj.optJSONArray("steps") ?: continue

                for (sIdx in 0 until stepsArray.length()) {
                    val stepObj = stepsArray.getJSONObject(sIdx)
                    val stepGeomPoints = parseGeoJsonCoordinates(
                        stepObj.optJSONObject("geometry")?.optJSONArray("coordinates")
                    )
                    // Filter out 1-point terminal "arrive" steps so every step is a real street segment
                    if (stepGeomPoints.size < 2) continue

                    val stepDist = stepObj.optDouble("distance", 0.0).let {
                        if (it > 1.0) it else computePolylineLengthMeters(stepGeomPoints).coerceAtLeast(3.0)
                    }
                    val stepDur = stepObj.optDouble("duration", 0.0).let {
                        if (it > 0.5) it else max(1.5, stepDist / 12.0)
                    }
                    val roadName = stepObj.optString("name", "").ifBlank {
                        stepObj.optString("ref", "").ifBlank {
                            if (routeIndex == 0) "Main Road" else "Alternate Road"
                        }
                    }

                    result.add(
                        ParsedRoadStep(
                            points = stepGeomPoints,
                            distanceMeters = stepDist,
                            durationSeconds = stepDur,
                            roadName = roadName
                        )
                    )
                }
            }
        }

        if (result.isEmpty()) {
            val overviewPts = parseGeoJsonCoordinates(
                routeObj.optJSONObject("geometry")?.optJSONArray("coordinates")
            )
            if (overviewPts.size >= 2) {
                val dist = routeObj.optDouble("distance", computePolylineLengthMeters(overviewPts)).coerceAtLeast(10.0)
                val dur = routeObj.optDouble("duration", max(5.0, dist / 12.0)).coerceAtLeast(3.0)
                result.add(
                    ParsedRoadStep(
                        points = overviewPts,
                        distanceMeters = dist,
                        durationSeconds = dur,
                        roadName = if (routeIndex == 0) "Main Road" else "Alternate Road"
                    )
                )
            }
        }

        return result
    }

    /**
     * Appends a sequence of validated street steps (`points.size >= 2`) from `originNode` to `destNode`.
     * Every edge carries the exact street vertices (`step.points`) with zero synthetic straight-line gaps.
     * Secondary routes (`routeIndex > 0`) receive a 1.25x base travel-time weight so A* always selects
     * `routeIndex == 0` (the true Best Route) unless an active hazard blocks `routeIndex == 0`.
     */
    private fun appendParsedStepsToGraph(
        parsedSteps: List<ParsedRoadStep>,
        routeIndex: Int,
        prefix: String,
        originNode: RoadNode,
        destNode: RoadNode,
        nodes: MutableMap<String, RoadNode>,
        edges: MutableList<RoadEdge>
    ) {
        if (parsedSteps.isEmpty()) return
        var prevNode = originNode
        val corridorWeight = if (routeIndex == 0) 1.0 else 1.25 + (routeIndex * 0.08)

        for (idx in parsedSteps.indices) {
            val step = parsedSteps[idx]
            val isLastStep = idx == parsedSteps.lastIndex
            val endPt = step.points.last()

            val targetNode = if (isLastStep) {
                destNode
            } else {
                val nodeId = "N_${prefix}_R${routeIndex}_S${idx}"
                RoadNode(
                    id = nodeId,
                    latitude = endPt.latitude,
                    longitude = endPt.longitude,
                    name = step.roadName
                ).also { nodes[nodeId] = it }
            }

            if (prevNode.id != targetNode.id) {
                val weightedDuration = max(1.5, step.durationSeconds * corridorWeight)
                val speedKmh = ((step.distanceMeters / max(1.0, step.durationSeconds)) * 3.6)
                    .toInt()
                    .coerceIn(20, 90)
                val edgeId = "E_${prefix}_R${routeIndex}_S${idx}"

                edges.add(
                    RoadEdge(
                        id = edgeId,
                        fromNode = prevNode.id,
                        toNode = targetNode.id,
                        roadName = step.roadName,
                        distanceMeters = step.distanceMeters,
                        travelTimeSeconds = weightedDuration,
                        roadType = if (routeIndex == 0) "primary" else "secondary",
                        speedLimitKmh = speedKmh,
                        geometry = step.points
                    )
                )
                edges.add(
                    RoadEdge(
                        id = "${edgeId}_REV",
                        fromNode = targetNode.id,
                        toNode = prevNode.id,
                        roadName = step.roadName,
                        distanceMeters = step.distanceMeters,
                        travelTimeSeconds = weightedDuration,
                        roadType = if (routeIndex == 0) "primary" else "secondary",
                        speedLimitKmh = speedKmh,
                        geometry = step.points.asReversed()
                    )
                )
                prevNode = targetNode
            }
        }
    }

    private fun fetchOsrmSecondaryCorridor(
        primaryBaseUrl: String,
        origin: LocationPoint,
        destination: LocationPoint,
        primarySteps: List<ParsedRoadStep>,
        originNode: RoadNode,
        destNode: RoadNode,
        nodes: MutableMap<String, RoadNode>,
        edges: MutableList<RoadEdge>
    ) {
        val allPrimaryPts = primarySteps.flatMap { it.points }
        val primaryMid = if (allPrimaryPts.isNotEmpty()) {
            allPrimaryPts[allPrimaryPts.size / 2]
        } else {
            LocationPoint((origin.latitude + destination.latitude) / 2.0, (origin.longitude + destination.longitude) / 2.0)
        }

        // First try alternate OSRM profile (e.g., routed-bike if primary was routed-car) for a natural street alternative
        val altProfileUrl = if (primaryBaseUrl.contains("routed-car")) {
            "https://routing.openstreetmap.de/routed-bike/route/v1/driving"
        } else {
            "https://routing.openstreetmap.de/routed-car/route/v1/driving"
        }

        val profileQuery = String.format(
            Locale.US,
            "%s/%.6f,%.6f;%.6f,%.6f?alternatives=true&steps=true&geometries=geojson&overview=full",
            altProfileUrl,
            origin.longitude,
            origin.latitude,
            destination.longitude,
            destination.latitude
        )

        var nextRouteIdx = 1
        runCatching {
            val req = Request.Builder()
                .url(profileQuery)
                .header("User-Agent", "RoutePilot-Driver-Android/1.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string() ?: return@use
                val routes = JSONObject(body).optJSONArray("routes") ?: return@use
                for (i in 0 until routes.length()) {
                    val steps = extractOsrmParsedSteps(routes.getJSONObject(i), nextRouteIdx)
                    val candPts = steps.flatMap { it.points }
                    if (candPts.isNotEmpty()) {
                        val candMid = candPts[candPts.size / 2]
                        val sepMeters = GeoUtils.haversineMeters(
                            primaryMid.latitude,
                            primaryMid.longitude,
                            candMid.latitude,
                            candMid.longitude
                        )
                        if (sepMeters >= 110.0) {
                            appendParsedStepsToGraph(
                                parsedSteps = steps,
                                routeIndex = nextRouteIdx,
                                prefix = "OSRM_ALT",
                                originNode = originNode,
                                destNode = destNode,
                                nodes = nodes,
                                edges = edges
                            )
                            nextRouteIdx++
                        }
                    }
                }
            }
        }

        // Also query perpendicular via-corridors around the primary midpoint so Choose Other Path always has a distinct street path
        val dLat = destination.latitude - origin.latitude
        val dLng = destination.longitude - origin.longitude
        val span = sqrt(dLat * dLat + dLng * dLng).coerceAtLeast(0.010)
        val perpLat = (-dLng / span) * (span * 0.25)
        val perpLng = (dLat / span) * (span * 0.25)

        for (sign in listOf(1.0, -1.0)) {
            if (nextRouteIdx > 2) break
            val viaLat = primaryMid.latitude + perpLat * sign
            val viaLng = primaryMid.longitude + perpLng * sign

            val url = String.format(
                Locale.US,
                "%s/%.6f,%.6f;%.6f,%.6f;%.6f,%.6f?continue_straight=true&steps=true&geometries=geojson&overview=full",
                primaryBaseUrl,
                origin.longitude,
                origin.latitude,
                viaLng,
                viaLat,
                destination.longitude,
                destination.latitude
            )
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "RoutePilot-Driver-Android/1.0")
                .get()
                .build()

            runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val root = JSONObject(body)
                    val routes = root.optJSONArray("routes") ?: return@use
                    if (routes.length() > 0) {
                        val steps = extractOsrmParsedSteps(routes.getJSONObject(0), nextRouteIdx)
                        if (steps.isNotEmpty()) {
                            appendParsedStepsToGraph(
                                parsedSteps = steps,
                                routeIndex = nextRouteIdx,
                                prefix = "OSRM_VIA",
                                originNode = originNode,
                                destNode = destNode,
                                nodes = nodes,
                                edges = edges
                            )
                            nextRouteIdx++
                        }
                    }
                }
            }
        }
    }

    /**
     * Queries Valhalla OpenStreetMap router (`valhalla1.openstreetmap.de/route`) which natively
     * returns high-precision (1E6) street-snapped geometry and alternate routes.
     */
    private fun fetchValhallaRoadGraph(
        origin: LocationPoint,
        destination: LocationPoint
    ): RoadGraph? {
        val payload = JSONObject().apply {
            put(
                "locations",
                JSONArray().apply {
                    put(JSONObject().put("lat", origin.latitude).put("lon", origin.longitude))
                    put(JSONObject().put("lat", destination.latitude).put("lon", destination.longitude))
                }
            )
            put("costing", "auto")
            put("alternates", 2)
            put("directions_options", JSONObject().put("units", "kilometers"))
        }

        val req = Request.Builder()
            .url("https://valhalla1.openstreetmap.de/route")
            .header("User-Agent", "RoutePilot-Driver-Android/1.0")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val root = JSONObject(body)
            val primaryTrip = root.optJSONObject("trip") ?: return null
            val primarySteps = extractValhallaSteps(primaryTrip, 0)
            if (primarySteps.isEmpty()) return null

            val nodes = LinkedHashMap<String, RoadNode>()
            val edges = ArrayList<RoadEdge>()

            val snappedOrigin = primarySteps.first().points.first()
            val snappedDest = primarySteps.last().points.last()

            val originNode = RoadNode("N_ORIGIN", snappedOrigin.latitude, snappedOrigin.longitude, "Road Start")
            val destNode = RoadNode("N_DEST", snappedDest.latitude, snappedDest.longitude, "Road Destination")
            nodes[originNode.id] = originNode
            nodes[destNode.id] = destNode

            appendParsedStepsToGraph(
                parsedSteps = primarySteps,
                routeIndex = 0,
                prefix = "VALH",
                originNode = originNode,
                destNode = destNode,
                nodes = nodes,
                edges = edges
            )

            val alternates = root.optJSONArray("alternates")
            if (alternates != null) {
                for (aIdx in 0 until alternates.length()) {
                    val altTrip = alternates.optJSONObject(aIdx)?.optJSONObject("trip") ?: continue
                    val altSteps = extractValhallaSteps(altTrip, aIdx + 1)
                    appendParsedStepsToGraph(
                        parsedSteps = altSteps,
                        routeIndex = aIdx + 1,
                        prefix = "VALH",
                        originNode = originNode,
                        destNode = destNode,
                        nodes = nodes,
                        edges = edges
                    )
                }
            }

            if (edges.isEmpty()) return null
            return RoadGraph(
                nodes = nodes,
                adjacency = edges.groupBy { it.fromNode },
                allEdges = edges
            )
        }
    }

    private fun extractValhallaSteps(
        tripObj: JSONObject,
        routeIndex: Int
    ): List<ParsedRoadStep> {
        val legs = tripObj.optJSONArray("legs") ?: return emptyList()
        if (legs.length() == 0) return emptyList()
        val leg = legs.getJSONObject(0)
        val shapeEncoded = leg.optString("shape", "")
        if (shapeEncoded.isBlank()) return emptyList()

        // Valhalla uses 1E6 precision for encoded polylines
        val fullShape = decodeGooglePolyline(shapeEncoded, 1E6)
        if (fullShape.size < 2) return emptyList()

        val maneuvers = leg.optJSONArray("maneuvers")
        val result = ArrayList<ParsedRoadStep>()
        if (maneuvers != null) {
            for (mIdx in 0 until maneuvers.length()) {
                val m = maneuvers.getJSONObject(mIdx)
                val bIdx = m.optInt("begin_shape_index", 0).coerceIn(0, fullShape.lastIndex)
                val eIdx = m.optInt("end_shape_index", fullShape.lastIndex).coerceIn(bIdx, fullShape.lastIndex)
                if (eIdx <= bIdx) continue
                val slice = fullShape.subList(bIdx, eIdx + 1).toList()
                if (slice.size < 2) continue

                val lengthMeters = max(5.0, m.optDouble("length", 0.05) * 1000.0)
                val timeSec = max(2.0, m.optDouble("time", lengthMeters / 12.0))
                val streetNames = m.optJSONArray("street_names")
                val roadName = if (streetNames != null && streetNames.length() > 0) {
                    streetNames.optString(0)
                } else {
                    m.optString("instruction", if (routeIndex == 0) "Main Road" else "Alternate Road")
                }
                result.add(
                    ParsedRoadStep(
                        points = slice,
                        distanceMeters = lengthMeters,
                        durationSeconds = timeSec,
                        roadName = roadName
                    )
                )
            }
        }

        if (result.isEmpty()) {
            val dist = computePolylineLengthMeters(fullShape).coerceAtLeast(10.0)
            result.add(
                ParsedRoadStep(
                    points = fullShape,
                    distanceMeters = dist,
                    durationSeconds = max(5.0, dist / 12.0),
                    roadName = if (routeIndex == 0) "Main Road" else "Alternate Road"
                )
            )
        }
        return result
    }

    private fun parseGeoJsonCoordinates(coords: JSONArray?): List<LocationPoint> {
        if (coords == null || coords.length() == 0) return emptyList()
        val list = ArrayList<LocationPoint>(coords.length())
        for (i in 0 until coords.length()) {
            val pt = coords.optJSONArray(i) ?: continue
            val lng = pt.optDouble(0, Double.NaN)
            val lat = pt.optDouble(1, Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) {
                list.add(LocationPoint(latitude = lat, longitude = lng))
            }
        }
        return list
    }

    private fun computePolylineLengthMeters(points: List<LocationPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until points.lastIndex) {
            sum += GeoUtils.haversineMeters(
                points[i].latitude,
                points[i].longitude,
                points[i + 1].latitude,
                points[i + 1].longitude
            )
        }
        return sum
    }

    /**
     * Decodes an encoded polyline string into a list of [LocationPoint]s with the given precision (`1E5` or `1E6`).
     */
    private fun decodeGooglePolyline(encoded: String, precision: Double = 1E5): List<LocationPoint> {
        val poly = ArrayList<LocationPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            if (index >= len) break
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            poly.add(
                LocationPoint(
                    latitude = lat / precision,
                    longitude = lng / precision
                )
            )
        }
        return poly
    }

    private fun buildCurvedEdgeGeometry(
        u: RoadNode,
        v: RoadNode,
        curveOffsetRatio: Double = 0.015
    ): List<LocationPoint> {
        val subSteps = 6
        val dLat = v.latitude - u.latitude
        val dLng = v.longitude - u.longitude
        val points = ArrayList<LocationPoint>(subSteps + 1)
        for (i in 0..subSteps) {
            val t = i.toDouble() / subSteps
            val arch = sin(t * Math.PI) * curveOffsetRatio
            points.add(
                LocationPoint(
                    latitude = u.latitude + dLat * t - dLng * arch,
                    longitude = u.longitude + dLng * t + dLat * arch
                )
            )
        }
        return points
    }

    /**
     * Offline fallback road network builder (used only when device has no internet or in local JVM unit tests).
     */
    fun buildMultiCorridorGraph(
        origin: LocationPoint,
        destination: LocationPoint
    ): RoadGraph {
        val nodes = LinkedHashMap<String, RoadNode>()
        val edges = ArrayList<RoadEdge>()

        val originNode = RoadNode("N_ORIGIN", origin.latitude, origin.longitude, "Current Location")
        val destNode = RoadNode("N_DEST", destination.latitude, destination.longitude, "Destination")
        nodes[originNode.id] = originNode
        nodes[destNode.id] = destNode

        val dLat = destination.latitude - origin.latitude
        val dLng = destination.longitude - origin.longitude
        val euclideanDeg = sqrt(dLat * dLat + dLng * dLng).coerceAtLeast(0.008)

        val perpLat = (-dLng / euclideanDeg) * (euclideanDeg * 0.18)
        val perpLng = (dLat / euclideanDeg) * (euclideanDeg * 0.18)

        val steps = 5
        val mainNodes = ArrayList<RoadNode>()
        val northNodes = ArrayList<RoadNode>()
        val southNodes = ArrayList<RoadNode>()

        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val curveFactor = sin(t * Math.PI)

            val mNode = RoadNode(
                id = "N_MAIN_$i",
                latitude = origin.latitude + dLat * t,
                longitude = origin.longitude + dLng * t,
                name = if (i == 2) "Bridge B1 Crossing" else "NH-27 Main Segment $i"
            )
            val nNode = RoadNode(
                id = "N_NORTH_$i",
                latitude = origin.latitude + dLat * t + perpLat * curveFactor,
                longitude = origin.longitude + dLng * t + perpLng * curveFactor,
                name = "Northern Safe Bypass $i"
            )
            val sNode = RoadNode(
                id = "N_SOUTH_$i",
                latitude = origin.latitude + dLat * t - perpLat * 0.95 * curveFactor,
                longitude = origin.longitude + dLng * t - perpLng * 0.95 * curveFactor,
                name = "Southern Sector Link $i"
            )

            nodes[mNode.id] = mNode
            nodes[nNode.id] = nNode
            nodes[sNode.id] = sNode

            mainNodes.add(mNode)
            northNodes.add(nNode)
            southNodes.add(sNode)
        }

        fun connect(
            u: RoadNode,
            v: RoadNode,
            roadName: String,
            speedKmh: Int,
            tortuosity: Double = 1.04
        ) {
            val dist = GeoUtils.haversineMeters(u.latitude, u.longitude, v.latitude, v.longitude) * tortuosity
            val speedMps = (speedKmh / 3.6).coerceAtLeast(5.0)
            val timeSec = max(5.0, dist / speedMps)
            val id = "E_${u.id}_${v.id}"
            val geom = buildCurvedEdgeGeometry(u, v, 0.01)
            edges.add(
                RoadEdge(
                    id = id,
                    fromNode = u.id,
                    toNode = v.id,
                    roadName = roadName,
                    distanceMeters = dist,
                    travelTimeSeconds = timeSec,
                    roadType = if (speedKmh >= 55) "primary" else "secondary",
                    speedLimitKmh = speedKmh,
                    geometry = geom
                )
            )
            edges.add(
                RoadEdge(
                    id = "${id}_REV",
                    fromNode = v.id,
                    toNode = u.id,
                    roadName = roadName,
                    distanceMeters = dist,
                    travelTimeSeconds = timeSec,
                    roadType = if (speedKmh >= 55) "primary" else "secondary",
                    speedLimitKmh = speedKmh,
                    geometry = geom.asReversed()
                )
            )
        }

        val fullMain = listOf(originNode) + mainNodes + listOf(destNode)
        val fullNorth = listOf(originNode) + northNodes + listOf(destNode)
        val fullSouth = listOf(originNode) + southNodes + listOf(destNode)

        for (i in 0 until fullMain.size - 1) {
            val roadLabel = if (i == 1 || i == 2) "Bridge B1 Highway Corridor" else "Main City Highway"
            connect(fullMain[i], fullMain[i + 1], roadLabel, speedKmh = 60, tortuosity = 1.04)
            connect(fullNorth[i], fullNorth[i + 1], "Green Bypass Express", speedKmh = 52, tortuosity = 1.10)
            connect(fullSouth[i], fullSouth[i + 1], "Civil Lines Ring Road", speedKmh = 48, tortuosity = 1.12)
        }

        for (i in mainNodes.indices) {
            connect(mainNodes[i], northNodes[i], "Cross Link Road ${i + 1}N", speedKmh = 42, tortuosity = 1.08)
            connect(mainNodes[i], southNodes[i], "Cross Link Road ${i + 1}S", speedKmh = 42, tortuosity = 1.08)
        }

        return RoadGraph(
            nodes = nodes,
            adjacency = edges.groupBy { it.fromNode },
            allEdges = edges
        )
    }

    companion object {
        val DEFAULT_ORIGIN = LocationPoint(
            latitude = 25.4484,
            longitude = 78.5685
        )
        const val BRIDGE_B1_LAT = 25.4702
        const val BRIDGE_B1_LNG = 78.5932
        const val BRIDGE_B1_ROAD_ID = "E_N_MAIN_1_N_MAIN_2"
        const val BRIDGE_B1_ID = "bridge_b1"
    }
}
