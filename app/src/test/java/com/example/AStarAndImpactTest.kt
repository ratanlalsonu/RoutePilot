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
        id = "dest_district_hospital",
        name = "District Hospital",
        address = "Medical Road, Sector 4, Main City",
        latitude = 25.4920,
        longitude = 78.6180,
        category = "Hospital"
    )

    @Test
    fun `AStar calculates normal primary and alternate routes when no hazards block corridor`() {
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
            "Recommended route should have road geometry points",
            recommended!!.points.size >= 2
        )
        assertTrue(
            "Alternate route should have road geometry points",
            alternate!!.points.size >= 2
        )
    }

    @Test
    fun `RouteImpactDetector ignores distant hazards and detects hazard on active route`() {
        val normalRoute = aStarEngine.calculateRoutes(
            origin = origin,
            destination = hospitalDestination,
            activeHazards = emptyList()
        ).recommendedRoute!!

        val distantHazard = Hazard(
            id = "distant_hazard",
            name = "Far Southern Construction",
            type = HazardType.CONSTRUCTION,
            severity = HazardSeverity.MEDIUM,
            status = HazardStatus.WARNING,
            latitude = 25.3200,
            longitude = 78.4200,
            radiusMeters = 120.0,
            active = true
        )

        val distantImpact = impactDetector.analyzeRouteImpact(
            currentLocation = origin,
            activeRoute = normalRoute,
            activeHazards = listOf(distantHazard)
        )
        assertFalse("Distant hazard should not affect route", distantImpact.isAffected)

        val midPoint = normalRoute.points[normalRoute.points.size / 2]
        val corridorHazard = Hazard(
            id = "hazard_bridge_b1",
            name = "Bridge B1",
            type = HazardType.BRIDGE_DAMAGE,
            severity = HazardSeverity.CRITICAL,
            status = HazardStatus.BLOCKED,
            latitude = midPoint.latitude,
            longitude = midPoint.longitude,
            radiusMeters = 420.0,
            roadId = OsmRoadNetworkProvider.BRIDGE_B1_ROAD_ID,
            bridgeId = OsmRoadNetworkProvider.BRIDGE_B1_ID,
            active = true
        )

        val bridgeImpact = impactDetector.analyzeRouteImpact(
            currentLocation = origin,
            activeRoute = normalRoute,
            activeHazards = listOf(corridorHazard)
        )
        assertTrue("Hazard on route must affect active route", bridgeImpact.isAffected)
        assertTrue("Critical blocked hazard must require immediate reroute", bridgeImpact.requiresImmediateReroute)
    }

    @Test
    fun `AStar diverts around blocked corridor onto safer route when critical hazard blocks primary path`() {
        val normalRoute = aStarEngine.calculateRoutes(
            origin = origin,
            destination = hospitalDestination,
            activeHazards = emptyList()
        ).recommendedRoute!!

        val midPoint = normalRoute.points[normalRoute.points.size / 2]
        val bridgeB1Hazard = Hazard(
            id = "hazard_bridge_b1",
            name = "Bridge B1",
            type = HazardType.BRIDGE_DAMAGE,
            severity = HazardSeverity.CRITICAL,
            status = HazardStatus.BLOCKED,
            latitude = midPoint.latitude,
            longitude = midPoint.longitude,
            radiusMeters = 350.0,
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
        assertTrue(saferRoute!!.isDivertedForSafety)
    }

    @Test
    fun `Remaining polyline distance and live remaining time decrease as driver travels along route`() {
        val route = aStarEngine.calculateRoutes(
            origin = origin,
            destination = hospitalDestination,
            activeHazards = emptyList()
        ).recommendedRoute!!

        val startRem = com.example.domain.routing.GeoUtils.computeRemainingPolylineDistanceMeters(
            currentLocation = route.points.first(),
            routePoints = route.points,
            totalRouteMeters = route.totalDistanceMeters
        )
        val midRem = com.example.domain.routing.GeoUtils.computeRemainingPolylineDistanceMeters(
            currentLocation = route.points[route.points.size / 2],
            routePoints = route.points,
            totalRouteMeters = route.totalDistanceMeters
        )

        assertNotNull(startRem)
        assertNotNull(midRem)
        assertTrue("Midpoint remaining distance ($midRem) must be less than start ($startRem)", midRem!! < startRem!!)
        assertTrue(
            "Live time formatting includes seconds when not on exact minute boundary",
            com.example.domain.routing.GeoUtils.formatLiveRemainingTime(222).contains("3 min 42 s")
        )
    }

    @Test
    fun `category matching and red marker icon resolution work for general categories and single locations`() {
        val hospExact = com.example.data.repository.DestinationRepositoryImpl.matchCategoryByFuzzyOrExact("hospital")
        val hospTypo = com.example.data.repository.DestinationRepositoryImpl.matchCategoryByFuzzyOrExact("hospotal")
        val fuelSpec = com.example.data.repository.DestinationRepositoryImpl.matchCategoryByFuzzyOrExact("petrol pump")
        val schoolSpec = com.example.data.repository.DestinationRepositoryImpl.matchCategoryByFuzzyOrExact("school")
        val bankSpec = com.example.data.repository.DestinationRepositoryImpl.matchCategoryByFuzzyOrExact("bank")
        val bandaOnly = com.example.data.repository.DestinationRepositoryImpl.matchCategoryByFuzzyOrExact("Banda")

        assertEquals("Hospital", hospExact?.canonicalCategory)
        assertEquals("Hospital", hospTypo?.canonicalCategory)
        assertEquals("Fuel Station", fuelSpec?.canonicalCategory)
        assertEquals("School", schoolSpec?.canonicalCategory)
        assertEquals("Bank & ATM", bankSpec?.canonicalCategory)
        assertEquals(null, bandaOnly)
        assertTrue(com.example.data.repository.DestinationRepositoryImpl.isKnownLocationName("Banda"))

        assertEquals("HOSPITAL", com.example.ui.components.resolveCategoryIconType("Hospital", "Banda District Hospital"))
        assertEquals("FUEL", com.example.ui.components.resolveCategoryIconType("Fuel Station", "Indian Oil"))
        assertEquals("SCHOOL", com.example.ui.components.resolveCategoryIconType("School", "DAV Inter College"))
        assertEquals("BANK", com.example.ui.components.resolveCategoryIconType("Bank & ATM", "State Bank of India"))
        assertEquals("LOCATION", com.example.ui.components.resolveCategoryIconType("Location", "Banda"))
    }
}

