package com.example

import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.HazardStatus
import com.example.domain.model.HazardType
import com.example.domain.routing.AStarRoutingEngine
import com.example.domain.routing.OsmRoadNetworkProvider
import com.example.domain.routing.RouteImpactDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AStarAndImpactTest {

    private val networkProvider = OsmRoadNetworkProvider()
    private val aStarEngine = AStarRoutingEngine(networkProvider)
    private val impactDetector = RouteImpactDetector()

    private val origin = OsmRoadNetworkProvider.DEFAULT_ORIGIN
    private val hospitalDestination = Destination(
        id = "dest_district_hospital_jhansi",
        name = "District Hospital",
        address = "Jhansi, Uttar Pradesh",
        latitude = 25.4595,
        longitude = 78.5820,
        category = "Hospital"
    )

    @Test
    fun `AStar calculates normal primary and alternate routes when no hazards block Bridge B1`() {
        val result = aStarEngine.calculateRoutes(
            origin = origin,
            destination = hospitalDestination,
            activeHazards = emptyList(),
            isRerouting = false
        )

        val recommended = result.recommendedRoute
        val alternate = result.alternateRoute

        assertNotNull("Recommended route should be found", recommended)
        assertNotNull("Alternate route should be found", alternate)
        assertTrue(
            "Normal route should traverse Bridge B1 approach nodes",
            recommended!!.nodeIds.contains("N_BRIDGE_B1_IN")
        )
        assertEquals(32, recommended.durationMinutes)
    }

    @Test
    fun `RouteImpactDetector ignores distant hazards and detects Bridge B1 hazard on active route`() {
        val normalRoute = aStarEngine.calculateRoutes(
            origin = origin,
            destination = hospitalDestination,
            activeHazards = emptyList()
        ).recommendedRoute!!

        val distantHazard = Hazard(
            id = "distant_hazard",
            name = "Southern Cantonment Construction",
            type = HazardType.CONSTRUCTION,
            severity = HazardSeverity.MEDIUM,
            status = HazardStatus.WARNING,
            latitude = 25.4392,
            longitude = 78.5572,
            radiusMeters = 150.0,
            active = true
        )

        val distantImpact = impactDetector.analyzeRouteImpact(
            currentLocation = origin,
            activeRoute = normalRoute,
            activeHazards = listOf(distantHazard)
        )
        assertFalse("Distant hazard should not affect NH-27 route", distantImpact.isAffected)

        val bridgeB1Hazard = Hazard(
            id = "hazard_bridge_b1",
            name = "Bridge B1",
            type = HazardType.BRIDGE_DAMAGE,
            severity = HazardSeverity.CRITICAL,
            status = HazardStatus.BLOCKED,
            latitude = OsmRoadNetworkProvider.BRIDGE_B1_LAT,
            longitude = OsmRoadNetworkProvider.BRIDGE_B1_LNG,
            radiusMeters = 380.0,
            roadId = OsmRoadNetworkProvider.BRIDGE_B1_ROAD_ID,
            bridgeId = OsmRoadNetworkProvider.BRIDGE_B1_ID,
            active = true
        )

        val bridgeImpact = impactDetector.analyzeRouteImpact(
            currentLocation = origin,
            activeRoute = normalRoute,
            activeHazards = listOf(bridgeB1Hazard)
        )
        assertTrue("Bridge B1 hazard must affect active NH-27 route", bridgeImpact.isAffected)
        assertTrue("Critical blocked bridge must require immediate reroute", bridgeImpact.requiresImmediateReroute)
    }

    @Test
    fun `AStar diverts around Bridge B1 onto safer Northern Bypass when Bridge B1 is blocked`() {
        val bridgeB1Hazard = Hazard(
            id = "hazard_bridge_b1",
            name = "Bridge B1",
            type = HazardType.BRIDGE_DAMAGE,
            severity = HazardSeverity.CRITICAL,
            status = HazardStatus.BLOCKED,
            latitude = OsmRoadNetworkProvider.BRIDGE_B1_LAT,
            longitude = OsmRoadNetworkProvider.BRIDGE_B1_LNG,
            radiusMeters = 380.0,
            roadId = OsmRoadNetworkProvider.BRIDGE_B1_ROAD_ID,
            bridgeId = OsmRoadNetworkProvider.BRIDGE_B1_ID,
            active = true
        )

        val divertedResult = aStarEngine.calculateRoutes(
            origin = origin,
            destination = hospitalDestination,
            activeHazards = listOf(bridgeB1Hazard),
            isRerouting = true
        )

        val saferRoute = divertedResult.recommendedRoute
        assertNotNull("Safer diverted route must be found", saferRoute)
        assertFalse(
            "Diverted route must NOT cross blocked Bridge B1",
            saferRoute!!.nodeIds.contains("N_BRIDGE_B1_OUT")
        )
        assertTrue(
            "Diverted route must use the Northern Ring Bypass",
            saferRoute.nodeIds.contains("N_NORTH_BYPASS_MID")
        )
        assertTrue(saferRoute.isDivertedForSafety)
    }
}
