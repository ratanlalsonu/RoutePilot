package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.RoutePilotDatabase
import com.example.data.local.UserPreferencesDataStore
import com.example.data.remote.FirestoreHazardDataSource
import com.example.data.remote.RemoteBackendClient
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.DestinationRepositoryImpl
import com.example.data.repository.HazardRepositoryImpl
import com.example.data.repository.JourneyRepositoryImpl
import com.example.data.repository.RoutingRepositoryImpl
import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.Journey
import com.example.domain.model.LocationPoint
import com.example.domain.model.OperatingMode
import com.example.domain.model.Route
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.DestinationRepository
import com.example.domain.repository.DriverPreferences
import com.example.domain.repository.HazardRepository
import com.example.domain.repository.JourneyRepository
import com.example.domain.repository.PreferencesRepository
import com.example.domain.repository.RoutingRepository
import com.example.domain.routing.AStarRoutingEngine
import com.example.domain.routing.GeoUtils
import com.example.domain.routing.OsmRoadNetworkProvider
import com.example.domain.routing.RouteImpactDetector
import com.example.hazard.HazardAlertService
import com.example.location.LocationTracker
import com.example.worker.HazardSyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

enum class NavigationWorkflowState {
    SPLASH,
    LOGIN,
    HOME,
    SEARCHING_DESTINATION,
    DESTINATION_SELECTED,
    ROUTE_CALCULATING,
    ROUTE_READY,
    NAVIGATING,
    HAZARD_DETECTED,
    RECALCULATING,
    ROUTE_UPDATED,
    ARRIVED,
    ERROR
}

data class RoutePilotUiState(
    val workflowState: NavigationWorkflowState = NavigationWorkflowState.SPLASH,
    val currentUser: User? = null,
    val preferences: DriverPreferences = DriverPreferences(),
    val currentLocation: LocationPoint = OsmRoadNetworkProvider.DEFAULT_ORIGIN,
    val hasLocationPermission: Boolean = false,
    val isGpsEnabled: Boolean = true,
    val isMapsApiKeyConfigured: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Destination> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val searchCenterLocation: LocationPoint? = null,
    val searchCenterLabel: String = "Your Current Location",
    val selectedCategoryChip: String? = null,
    val isMultiMarkerCategoryView: Boolean = false,
    val fitAllMarkersTrigger: Int = 0,
    val recentDestinations: List<Destination> = emptyList(),
    val selectedDestination: Destination = DestinationRepositoryImpl.defaultReferenceDestinations.first(),
    val recommendedRoute: Route? = null,
    val alternateRoute: Route? = null,
    val activeRoute: Route? = null,
    val previousRouteBeforeDiversion: Route? = null,
    val isUsingAlternateInPreview: Boolean = false,
    val remainingDistanceMeters: Double = 18400.0,
    val remainingEtaMinutes: Int = 32,
    val remainingEtaSeconds: Int = 1920,
    val currentTurnDistanceMeters: Double = 500.0,
    val currentTurnInstruction: String = "Continue on main route",
    val currentTurnManeuver: String = "RIGHT",
    val activeHazards: List<Hazard> = emptyList(),
    val relevantHazardsOnRoute: List<Hazard> = emptyList(),
    val primaryAffectingHazard: Hazard? = null,
    val recalculationProgress: Float = 0f,
    val recalculationErrorMessage: String? = null,
    val lastCompletedJourney: Journey? = null,
    val journeyHistory: List<Journey> = emptyList(),
    val isVoiceMutedInNav: Boolean = false,
    val authError: String? = null,
    val statusBannerMessage: String? = null
)

class RoutePilotViewModel(
    private val authRepository: AuthRepository,
    private val destinationRepository: DestinationRepository,
    private val hazardRepository: HazardRepository,
    private val journeyRepository: JourneyRepository,
    private val routingRepository: RoutingRepository,
    private val preferencesRepository: PreferencesRepository,
    private val locationTracker: LocationTracker,
    private val routeImpactDetector: RouteImpactDetector,
    private val hazardAlertService: HazardAlertService
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RoutePilotUiState(
            isMapsApiKeyConfigured = checkMapsApiKeyConfigured()
        )
    )
    val uiState: StateFlow<RoutePilotUiState> = _uiState.asStateFlow()

    private var gpsTrackingJob: Job? = null
    private var recalculationJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var navigationStartTimestamp: Long = 0L
    private var handledHazardIdsForCurrentRoute = mutableSetOf<String>()
    private var rawBackendHazards: List<Hazard> = emptyList()
    private var initialPrimaryRoute: Route? = null

    // Live navigation progress tracking baseline
    private var navigationOriginLocation: LocationPoint = OsmRoadNetworkProvider.DEFAULT_ORIGIN
    private var lastTrackedDriverLocation: LocationPoint? = null
    private var lastNavigationUpdateMillis: Long = 0L
    private var cumulativeTravelledMeters: Double = 0.0
    private var activeTravelElapsedSeconds: Int = 0
    private var initialRouteTotalDistanceMeters: Double = 18400.0
    private var initialRouteDurationSeconds: Int = 1920
    private var initialStraightLineToDestMeters: Double = 14000.0

    private var hazardSubscriptionJob: Job? = null

    init {
        observePreferencesAndData()
        observeAuthentication()
        refreshDeviceLocationStatus()
        viewModelScope.launch {
            runCatching {
                com.example.data.remote.UserConsoleFirestoreBridge.seedRequiredCollectionsIfNeeded()
            }
        }
    }

    private fun checkMapsApiKeyConfigured(): Boolean {
        val key = runCatching { BuildConfig.MAPS_API_KEY }.getOrDefault("")
        return key.isNotBlank() && !key.startsWith("YOUR_") && key != "MY_MAPS_API_KEY"
    }

    private fun observePreferencesAndData() {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collectLatest { prefs ->
                _uiState.update {
                    it.copy(
                        preferences = prefs,
                        isVoiceMutedInNav = !prefs.navigationVoiceEnabled
                    )
                }
            }
        }

        viewModelScope.launch {
            destinationRepository.getRecentDestinations(includeDemoSamples = false)
                .collectLatest { recent ->
                    _uiState.update { it.copy(recentDestinations = recent) }
                }
        }
    }

    private var journeySubscriptionJob: Job? = null

    private fun observeAuthentication() {
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { user ->
                (destinationRepository as? DestinationRepositoryImpl)?.setActiveUser(
                    userId = user?.id,
                    email = user?.email
                )
                _uiState.update { it.copy(currentUser = user) }
                journeySubscriptionJob?.cancel()
                if (user != null) {
                    journeySubscriptionJob = viewModelScope.launch {
                        journeyRepository.getJourneyHistory(includeDemoSamples = false)
                            .collectLatest { history ->
                                val userHistory = history.filter { j ->
                                    j.userId.equals(user.id, ignoreCase = true) ||
                                        j.userId.equals(user.email, ignoreCase = true)
                                }
                                _uiState.update { it.copy(journeyHistory = userHistory) }
                            }
                    }
                    observeRealtimeHazards()
                    viewModelScope.launch {
                        runCatching {
                            (authRepository as? com.example.data.repository.AuthRepositoryImpl)
                                ?.syncCurrentSessionToConsoleIfPresent()
                                ?: com.example.data.remote.UserConsoleFirestoreBridge.ensureSessionSyncedToConsole(
                                    uid = user.id,
                                    name = user.name,
                                    email = user.email,
                                    password = null
                                )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            recentDestinations = emptyList(),
                            journeyHistory = emptyList()
                        )
                    }
                    hazardSubscriptionJob?.cancel()
                    hazardSubscriptionJob = null
                }
            }
        }
    }

    private var lastTelemetrySyncMs: Long = 0L

    private fun pushLiveTelemetryToConsole(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastTelemetrySyncMs < 5000L) return
        lastTelemetrySyncMs = now
        val state = _uiState.value
        val activeRoute = state.activeRoute ?: state.recommendedRoute
        val sampledPts = activeRoute?.points.orEmpty().let { pts ->
            if (pts.size <= 30) pts else pts.filterIndexed { i, _ -> i % (pts.size / 25).coerceAtLeast(1) == 0 || i == pts.lastIndex }
        }
        val routeJson = sampledPts.joinToString(prefix = "[", postfix = "]") { pt ->
            "{\"lat\":${pt.latitude},\"lng\":${pt.longitude}}"
        }
        viewModelScope.launch {
            runCatching {
                com.example.data.remote.UserConsoleFirestoreBridge.syncLiveDriverTelemetry(
                    userId = state.currentUser?.id.orEmpty(),
                    userName = state.currentUser?.name.orEmpty(),
                    userEmail = state.currentUser?.email.orEmpty(),
                    currentLat = state.currentLocation.latitude,
                    currentLng = state.currentLocation.longitude,
                    destinationName = (activeRoute?.destination ?: state.selectedDestination).name,
                    destinationAddress = (activeRoute?.destination ?: state.selectedDestination).address,
                    destLat = (activeRoute?.destination ?: state.selectedDestination).latitude,
                    destLng = (activeRoute?.destination ?: state.selectedDestination).longitude,
                    workflowState = state.workflowState.name,
                    isDivertedForSafety = activeRoute?.isDivertedForSafety == true,
                    routePointsJson = routeJson
                )
            }
        }
    }

    private fun observeRealtimeHazards() {
        if (hazardSubscriptionJob?.isActive == true) return
        hazardSubscriptionJob = viewModelScope.launch {
            hazardRepository.observeActiveHazards(OperatingMode.LIVE)
                .catch { /* Handled via handleFirestoreError in FirestoreHazardDataSource */ }
                .collectLatest { hazards ->
                    rawBackendHazards = hazards
                    val referenceRoute = initialPrimaryRoute
                        ?: _uiState.value.previousRouteBeforeDiversion
                        ?: _uiState.value.activeRoute
                        ?: _uiState.value.recommendedRoute
                    val aligned = alignHazardsWithRoutePath(hazards, referenceRoute)
                    handledHazardIdsForCurrentRoute.removeAll { key ->
                        aligned.none { h -> key.startsWith("${h.id}_") }
                    }
                    _uiState.update { it.copy(activeHazards = aligned) }
                    evaluateActiveNavigationAgainstHazards(aligned)
                }
        }
    }

    private fun alignHazardsWithRoutePath(
        hazards: List<Hazard>,
        referenceRoute: Route?
    ): List<Hazard> {
        val pts = referenceRoute?.points.orEmpty()
        if (pts.size < 2) return hazards

        val midIndex = (pts.size / 2).coerceIn(0, pts.lastIndex)
        val midPt = pts[midIndex]

        return hazards.mapIndexed { idx, hazard ->
            if (!hazard.isEffectiveHazard) return@mapIndexed hazard

            val nearestPt = pts.minByOrNull { pt ->
                GeoUtils.haversineMeters(hazard.latitude, hazard.longitude, pt.latitude, pt.longitude)
            } ?: midPt

            val distToNearestMeters = GeoUtils.haversineMeters(
                hazard.latitude,
                hazard.longitude,
                nearestPt.latitude,
                nearestPt.longitude
            )

            val anchorPt = if (distToNearestMeters <= 1800.0) {
                nearestPt
            } else {
                val offsetIdx = (midIndex + (idx * 3)).coerceIn(1, (pts.lastIndex - 1).coerceAtLeast(1))
                pts[offsetIdx]
            }

            hazard.copy(
                latitude = anchorPt.latitude,
                longitude = anchorPt.longitude,
                radiusMeters = max(hazard.radiusMeters, 280.0)
            )
        }
    }

    fun refreshDeviceLocationStatus() {
        val hasPerm = locationTracker.hasLocationPermission()
        val gpsOn = locationTracker.isGpsEnabled()
        _uiState.update {
            it.copy(
                hasLocationPermission = hasPerm,
                isGpsEnabled = gpsOn
            )
        }
        if (hasPerm) {
            viewModelScope.launch {
                val loc = locationTracker.getCurrentLocationOnce()
                if (loc != null) {
                    _uiState.update { it.copy(currentLocation = loc) }
                }
            }
        }
        startContinuousGpsListening(forceRestart = hasPerm)
    }

    private fun startContinuousGpsListening(forceRestart: Boolean = false) {
        if (forceRestart) {
            gpsTrackingJob?.cancel()
            gpsTrackingJob = null
        } else if (gpsTrackingJob?.isActive == true) {
            return
        }
        gpsTrackingJob = viewModelScope.launch {
            locationTracker.observeLocationUpdates().collectLatest { gpsPoint ->
                onNewDriverLocationReceived(gpsPoint)
            }
        }
    }

    private fun resetNavigationProgressBaseline(route: Route, origin: LocationPoint) {
        navigationOriginLocation = origin
        lastTrackedDriverLocation = origin
        lastNavigationUpdateMillis = System.currentTimeMillis()
        cumulativeTravelledMeters = 0.0
        activeTravelElapsedSeconds = 0
        initialRouteTotalDistanceMeters = route.totalDistanceMeters.coerceAtLeast(50.0)
        initialRouteDurationSeconds = max(60, route.durationMinutes * 60)
        initialStraightLineToDestMeters = GeoUtils.haversineMeters(
            origin.latitude,
            origin.longitude,
            route.destination.latitude,
            route.destination.longitude
        ).coerceAtLeast(50.0)
    }

    fun onNewDriverLocationReceived(newPoint: LocationPoint) {
        val state = _uiState.value
        val activeRoute = state.activeRoute
        val isDriving = state.workflowState == NavigationWorkflowState.NAVIGATING ||
            state.workflowState == NavigationWorkflowState.ROUTE_UPDATED ||
            state.workflowState == NavigationWorkflowState.HAZARD_DETECTED

        if (!isDriving || activeRoute == null) {
            _uiState.update { it.copy(currentLocation = newPoint) }
            return
        }

        val nowMillis = System.currentTimeMillis()
        val prevPoint = lastTrackedDriverLocation ?: newPoint
        val coordStepMeters = GeoUtils.haversineMeters(prevPoint, newPoint)

        if (coordStepMeters > 35_000.0) {
            // Re-anchor baseline if initial location switched from fallback origin to real city GPS
            navigationOriginLocation = newPoint
            lastTrackedDriverLocation = newPoint
            lastNavigationUpdateMillis = nowMillis
            initialStraightLineToDestMeters = GeoUtils.haversineMeters(
                newPoint.latitude,
                newPoint.longitude,
                activeRoute.destination.latitude,
                activeRoute.destination.longitude
            ).coerceAtLeast(50.0)
            _uiState.update { it.copy(currentLocation = newPoint) }
            return
        }

        // Ignore tiny stationary GPS jitter (< 2.0m when not moving) so distance & ETA never drift while stationary
        if (coordStepMeters < 2.0 && newPoint.speedMps < 0.6f) {
            return
        }

        lastTrackedDriverLocation = newPoint
        lastNavigationUpdateMillis = nowMillis

        val currentStraightLineToDest = GeoUtils.haversineMeters(
            newPoint.latitude,
            newPoint.longitude,
            activeRoute.destination.latitude,
            activeRoute.destination.longitude
        )

        val straightLineRatio = currentStraightLineToDest / max(1.0, initialStraightLineToDestMeters)
        val straightLineScaledMeters = (initialRouteTotalDistanceMeters * straightLineRatio).coerceAtLeast(0.0)

        val polylineRem = GeoUtils.computeRemainingPolylineDistanceMeters(
            newPoint,
            activeRoute.points,
            initialRouteTotalDistanceMeters
        )

        // Real bidirectional remaining distance: decreases when moving toward destination, increases when moving away
        val remMeters = when {
            polylineRem != null && currentStraightLineToDest <= initialStraightLineToDestMeters -> {
                kotlin.math.min(polylineRem, straightLineScaledMeters).coerceAtLeast(0.0)
            }
            polylineRem != null -> {
                max(polylineRem, straightLineScaledMeters).coerceAtLeast(0.0)
            }
            else -> {
                straightLineScaledMeters
            }
        }

        // 1. Check arrival at destination
        if (remMeters <= 25.0 || routeImpactDetector.hasArrivedAtDestination(
                newPoint,
                activeRoute.destination.latitude,
                activeRoute.destination.longitude
            )
        ) {
            completeActiveJourney()
            return
        }

        // 2. Compute live remaining time in seconds & minutes strictly from real remaining distance & live GPS speed
        val avgRouteSpeedMps = (initialRouteTotalDistanceMeters / max(1, initialRouteDurationSeconds)).coerceIn(4.0, 25.0)
        val effectiveSpeedMps = if (newPoint.speedMps >= 1.5f) {
            (newPoint.speedMps * 0.4 + avgRouteSpeedMps * 0.6).coerceIn(1.5, 35.0)
        } else {
            avgRouteSpeedMps
        }
        val remSecs = (remMeters / effectiveSpeedMps).roundToInt().coerceAtLeast(1)
        val remMins = max(1, (remSecs + 30) / 60)

        // 3. Update live distance & instruction to next turn along route segments
        val netCoveredMeters = (initialRouteTotalDistanceMeters - remMeters).coerceAtLeast(0.0)
        val (turnDistMeters, turnInstr, turnManeuver) = computeLiveTurnGuidance(
            route = activeRoute,
            totalCoveredMeters = netCoveredMeters,
            fallbackDistance = state.currentTurnDistanceMeters,
            fallbackInstruction = state.currentTurnInstruction,
            fallbackManeuver = state.currentTurnManeuver
        )

        _uiState.update {
            it.copy(
                currentLocation = newPoint,
                remainingDistanceMeters = remMeters,
                remainingEtaMinutes = remMins,
                remainingEtaSeconds = remSecs,
                currentTurnDistanceMeters = turnDistMeters,
                currentTurnInstruction = turnInstr,
                currentTurnManeuver = turnManeuver
            )
        }

        // 4. Re-check hazard impact at the new location
        evaluateActiveNavigationAgainstHazards(state.activeHazards)
        pushLiveTelemetryToConsole(force = false)
    }

    private fun computeLiveTurnGuidance(
        route: Route,
        totalCoveredMeters: Double,
        fallbackDistance: Double,
        fallbackInstruction: String,
        fallbackManeuver: String
    ): Triple<Double, String, String> {
        val segments = route.segments
        if (segments.isEmpty()) {
            val updatedDist = (fallbackDistance - (totalCoveredMeters % 500.0)).coerceAtLeast(10.0)
            return Triple(updatedDist, fallbackInstruction, fallbackManeuver)
        }
        var accumulated = 0.0
        for (i in segments.indices) {
            val seg = segments[i]
            val segLen = seg.distanceMeters.coerceAtLeast(25.0)
            if (accumulated + segLen > totalCoveredMeters) {
                val remInSeg = (accumulated + segLen - totalCoveredMeters).coerceAtLeast(5.0)
                val targetSeg = segments.getOrNull(i + 1) ?: seg
                val instruction = targetSeg.instruction.ifBlank { seg.instruction.ifBlank { fallbackInstruction } }
                val maneuver = targetSeg.maneuverType.ifBlank { seg.maneuverType.ifBlank { fallbackManeuver } }
                return Triple(remInSeg, instruction, maneuver)
            }
            accumulated += segLen
        }
        val lastSeg = segments.last()
        val remToDest = (initialRouteTotalDistanceMeters - totalCoveredMeters).coerceAtLeast(5.0)
        return Triple(remToDest, lastSeg.instruction.ifBlank { "Arrive at destination" }, lastSeg.maneuverType)
    }

    private fun interpolateDriverPointAlongRouteIfIndoorStep(
        rawPoint: LocationPoint,
        route: Route,
        totalCoveredMeters: Double
    ): LocationPoint {
        val pts = route.points
        if (pts.size < 2 || totalCoveredMeters <= 0.5) return rawPoint
        val distToStart = GeoUtils.haversineMeters(rawPoint, pts.first())
        // If user is walking near the start point (e.g. indoor testing), advance smoothly along route points
        if (distToStart > 180.0) return rawPoint

        var totalPolyLen = 0.0
        for (i in 0 until pts.size - 1) {
            totalPolyLen += GeoUtils.haversineMeters(pts[i], pts[i + 1])
        }
        if (totalPolyLen <= 1.0) return rawPoint

        val targetPolyDist = (totalCoveredMeters * (totalPolyLen / max(1.0, initialRouteTotalDistanceMeters)))
            .coerceIn(0.0, totalPolyLen)

        var walked = 0.0
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val segLen = GeoUtils.haversineMeters(a, b)
            if (walked + segLen >= targetPolyDist && segLen > 0.1) {
                val frac = ((targetPolyDist - walked) / segLen).coerceIn(0.0, 1.0)
                return rawPoint.copy(
                    latitude = a.latitude + (b.latitude - a.latitude) * frac,
                    longitude = a.longitude + (b.longitude - a.longitude) * frac
                )
            }
            walked += segLen
        }
        return rawPoint
    }

    /**
     * Evaluates whether any active hazard from the Backend / Firestore affects the driver's
     * current route. When a hazard occurs on the path the user is travelling on, it indicates
     * the hazard on that path and prompts the user to "Choose Other Path" (no auto-reroute timer).
     */
    private fun evaluateActiveNavigationAgainstHazards(hazards: List<Hazard>) {
        val state = _uiState.value
        val route = state.activeRoute ?: state.recommendedRoute ?: return
        val isNavigating = state.workflowState == NavigationWorkflowState.NAVIGATING ||
            state.workflowState == NavigationWorkflowState.ROUTE_UPDATED

        val impact = routeImpactDetector.analyzeRouteImpact(
            currentLocation = state.currentLocation,
            activeRoute = route,
            activeHazards = hazards
        )

        _uiState.update {
            it.copy(
                relevantHazardsOnRoute = impact.allRelevantHazards,
                primaryAffectingHazard = impact.primaryAffectingHazard
            )
        }

        // Do not re-trigger hazard alert if the user has already chosen the new diverted safer path
        if (route.isDivertedForSafety) {
            return
        }

        if (isNavigating && impact.isAffected) {
            val hazard = impact.primaryAffectingHazard ?: return
            val hazardVersionKey = "${hazard.id}_${hazard.status}_${hazard.severity}_${hazard.type}"
            if (!handledHazardIdsForCurrentRoute.contains(hazardVersionKey)) {
                handledHazardIdsForCurrentRoute.add(hazardVersionKey)
                onRouteAffectingHazardDetected(hazard = hazard)
            }
        } else if (state.workflowState == NavigationWorkflowState.HAZARD_DETECTED && !impact.isAffected) {
            _uiState.update {
                it.copy(
                    workflowState = NavigationWorkflowState.NAVIGATING,
                    primaryAffectingHazard = null,
                    statusBannerMessage = "Hazard cleared — Continuing on route"
                )
            }
        }
    }

    private fun onRouteAffectingHazardDetected(hazard: Hazard) {
        val prefs = _uiState.value.preferences
        recalculationJob?.cancel()
        _uiState.update {
            it.copy(
                workflowState = NavigationWorkflowState.HAZARD_DETECTED,
                primaryAffectingHazard = hazard
            )
        }

        val statusLabel = when (hazard.status) {
            com.example.domain.model.HazardStatus.BLOCKED -> "Road Blocked"
            com.example.domain.model.HazardStatus.PARTIALLY_BLOCKED -> "Partially Blocked"
            else -> hazard.type.displayName
        }

        hazardAlertService.triggerHazardAlertFeedback(
            hazardTitle = "${hazard.name}, ${hazard.type.displayName}",
            statusText = statusLabel,
            soundEnabled = prefs.alertSoundEnabled,
            voiceEnabled = prefs.navigationVoiceEnabled && !_uiState.value.isVoiceMutedInNav
        )
        pushLiveTelemetryToConsole(force = true)
    }

    /**
     * Invoked when the user explicitly clicks "Choose Other Path" after a hazard is indicated
     * on their current route. Switches to the alternate/safer road corridor and highlights the new route.
     */
    fun triggerAutomaticRerouting(reasonHazard: Hazard? = _uiState.value.primaryAffectingHazard) {
        recalculationJob?.cancel()
        recalculationJob = viewModelScope.launch {
            val stateBefore = _uiState.value
            val wasInPreview = stateBefore.workflowState == NavigationWorkflowState.ROUTE_READY ||
                stateBefore.workflowState == NavigationWorkflowState.DESTINATION_SELECTED
            val currentRoute = stateBefore.activeRoute ?: stateBefore.recommendedRoute
            val dest = currentRoute?.destination ?: stateBefore.selectedDestination
            val origin = stateBefore.currentLocation
            val hazards = stateBefore.activeHazards

            if (!wasInPreview) {
                _uiState.update {
                    it.copy(
                        workflowState = NavigationWorkflowState.RECALCULATING,
                        recalculationProgress = 0.20f,
                        recalculationErrorMessage = null
                    )
                }
                delay(280L)
                _uiState.update { it.copy(recalculationProgress = 0.65f) }
            }

            val calcResult = routingRepository.calculateRoutes(
                origin = origin,
                destination = dest,
                hazards = hazards,
                isRerouting = true
            )

            if (!wasInPreview) {
                delay(280L)
                _uiState.update { it.copy(recalculationProgress = 0.95f) }
            }

            val candidateRec = calcResult.recommendedRoute
            val candidateAlt = calcResult.alternateRoute ?: stateBefore.alternateRoute

            // Guarantee that choosing "Other Path" selects a distinct road corridor from currentRoute when available
            val chosenOtherRoute = when {
                candidateRec != null && currentRoute != null && !areRoutesOnSameCorridor(currentRoute, candidateRec) -> {
                    candidateRec.copy(
                        isDivertedForSafety = true,
                        avoidedHazardIds = hazards.map { it.id }.ifEmpty { listOfNotNull(reasonHazard?.id) }
                    )
                }
                candidateAlt != null -> {
                    candidateAlt.copy(
                        isAlternative = false,
                        isDivertedForSafety = true,
                        avoidedHazardIds = hazards.map { it.id }.ifEmpty { listOfNotNull(reasonHazard?.id) }
                    )
                }
                candidateRec != null -> {
                    candidateRec.copy(
                        isDivertedForSafety = true,
                        avoidedHazardIds = hazards.map { it.id }.ifEmpty { listOfNotNull(reasonHazard?.id) }
                    )
                }
                else -> null
            }

            if (chosenOtherRoute == null) {
                _uiState.update {
                    it.copy(
                        workflowState = if (wasInPreview) NavigationWorkflowState.ROUTE_READY else NavigationWorkflowState.RECALCULATING,
                        recalculationProgress = 0f,
                        recalculationErrorMessage = calcResult.errorMessage ?: "No safe alternative route found."
                    )
                }
                return@launch
            }

            resetNavigationProgressBaseline(chosenOtherRoute, origin)
            val nextSegment = chosenOtherRoute.segments.firstOrNull()
            _uiState.update {
                it.copy(
                    workflowState = if (wasInPreview) NavigationWorkflowState.ROUTE_READY else NavigationWorkflowState.ROUTE_UPDATED,
                    previousRouteBeforeDiversion = currentRoute,
                    activeRoute = chosenOtherRoute,
                    recommendedRoute = chosenOtherRoute,
                    isUsingAlternateInPreview = false,
                    remainingDistanceMeters = chosenOtherRoute.totalDistanceMeters,
                    remainingEtaMinutes = chosenOtherRoute.durationMinutes,
                    remainingEtaSeconds = max(60, chosenOtherRoute.durationMinutes * 60),
                    currentTurnDistanceMeters = 350.0,
                    currentTurnInstruction = nextSegment?.instruction ?: "Continue onto safer route",
                    currentTurnManeuver = nextSegment?.maneuverType ?: "LEFT",
                    primaryAffectingHazard = null,
                    recalculationProgress = 1f,
                    recalculationErrorMessage = null
                )
            }

            hazardAlertService.announceRouteUpdated(
                voiceEnabled = _uiState.value.preferences.navigationVoiceEnabled && !_uiState.value.isVoiceMutedInNav
            )
            pushLiveTelemetryToConsole(force = true)
        }
    }

    private fun areRoutesOnSameCorridor(routeA: Route, routeB: Route): Boolean {
        if (routeA.points.size < 2 || routeB.points.size < 2) return false
        val midA = routeA.points[routeA.points.size / 2]
        val midB = routeB.points[routeB.points.size / 2]
        return GeoUtils.haversineMeters(midA.latitude, midA.longitude, midB.latitude, midB.longitude) < 75.0
    }

    // ========================================================================
    // Screen 1 & 2: Splash & Login Actions
    // ========================================================================

    fun onSplashFinished(): NavigationWorkflowState {
        val next = if (_uiState.value.currentUser != null) {
            NavigationWorkflowState.HOME
        } else {
            NavigationWorkflowState.LOGIN
        }
        _uiState.update { it.copy(workflowState = next) }
        return next
    }

    fun loginWithEmail(
        email: String,
        password: String,
        rememberMe: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(authError = null, statusBannerMessage = null) }
            val res = authRepository.loginWithEmail(email, password, rememberMe)
            res.onSuccess {
                preferencesRepository.setRememberMe(rememberMe, email.trim())
                _uiState.update { state -> state.copy(workflowState = NavigationWorkflowState.HOME, authError = null) }
                onSuccess()
            }.onFailure { err ->
                _uiState.update { state -> state.copy(authError = err.message ?: "Authentication failed.") }
            }
        }
    }

    fun signUpWithEmail(
        name: String,
        email: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(authError = null, statusBannerMessage = null) }
            val res = authRepository.signUpWithEmail(name, email, password)
            res.onSuccess {
                preferencesRepository.setRememberMe(true, email.trim())
                _uiState.update { state -> state.copy(workflowState = NavigationWorkflowState.HOME, authError = null) }
                onSuccess()
            }.onFailure { err ->
                _uiState.update { state -> state.copy(authError = err.message ?: "Sign up failed.") }
            }
        }
    }

    fun continueWithGoogle(
        email: String,
        name: String = "",
        idToken: String? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(authError = null, statusBannerMessage = null) }
            val res = authRepository.continueWithGoogle(email = email, name = name, idToken = idToken)
            res.onSuccess { user ->
                preferencesRepository.setRememberMe(true, user.email.trim())
                _uiState.update { state -> state.copy(workflowState = NavigationWorkflowState.HOME, authError = null) }
                onSuccess()
            }.onFailure { err ->
                _uiState.update { state -> state.copy(authError = err.message ?: "Google Sign-In failed.") }
            }
        }
    }

    fun reportAuthError(message: String?) {
        _uiState.update { it.copy(authError = message, statusBannerMessage = null) }
    }

    fun sendPasswordReset(email: String, newPassword: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(authError = null, statusBannerMessage = null) }
            val res = authRepository.sendPasswordReset(email, newPassword)
            res.onSuccess { message ->
                _uiState.update { it.copy(statusBannerMessage = message, authError = null) }
            }.onFailure { err ->
                _uiState.update { it.copy(authError = err.message, statusBannerMessage = null) }
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            stopActiveNavigation()
            authRepository.logout()
            _uiState.update { it.copy(workflowState = NavigationWorkflowState.LOGIN) }
            onLoggedOut()
        }
    }

    // ========================================================================
    // Screen 3 & 4: Destination Search & Map Selection
    // ========================================================================

    fun openDestinationSearchAtCurrentLocation() {
        searchDebounceJob?.cancel()
        refreshDeviceLocationStatus()
        val loc = _uiState.value.currentLocation
        val currentLocDest = Destination(
            id = "current_user_location",
            name = "Your Current Location",
            address = String.format(java.util.Locale.US, "Live GPS (%.4f, %.4f)", loc.latitude, loc.longitude),
            latitude = loc.latitude,
            longitude = loc.longitude,
            category = "Current Location",
            distanceFromUserKm = 0.0
        )
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearchingPlaces = false,
                searchCenterLocation = null,
                searchCenterLabel = "Your Current Location",
                selectedCategoryChip = null,
                isMultiMarkerCategoryView = false,
                selectedDestination = currentLocDest
            )
        }
        viewModelScope.launch {
            runCatching {
                val geocoded = destinationRepository.reverseGeocode(loc)
                if (_uiState.value.selectedDestination.id == "current_user_location") {
                    _uiState.update { state ->
                        state.copy(
                            selectedDestination = currentLocDest.copy(
                                address = geocoded.address.ifBlank { currentLocDest.address }
                            )
                        )
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        if (query.isBlank()) {
            openDestinationSearchAtCurrentLocation()
            return
        }
        val currentLoc = _uiState.value.currentLocation
        val repoImpl = destinationRepository as? DestinationRepositoryImpl
        val intent = repoImpl?.parseSearchIntent(query)
        val fastCenter = repoImpl?.getFastSearchCenter(query, currentLoc) ?: currentLoc
        val centerLabel = intent?.explicitLocationName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.split(" ")
            ?.joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
            ?: "Your Current Location"

        val instantSuggestions = repoImpl
            ?.getInstantPlaceSuggestions(query, currentLoc)
            .orEmpty()
        val instantFirst = instantSuggestions.firstOrNull()

        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                searchResults = instantSuggestions,
                isSearchingPlaces = true,
                searchCenterLocation = fastCenter,
                searchCenterLabel = centerLabel,
                selectedCategoryChip = intent?.categorySpec?.canonicalCategory,
                isMultiMarkerCategoryView = true,
                fitAllMarkersTrigger = state.fitAllMarkersTrigger + 1,
                selectedDestination = instantFirst ?: state.selectedDestination
            )
        }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(70L)
            val onlineCenter = repoImpl?.resolveOnlineSearchCenter(query, currentLoc) ?: fastCenter
            val res = destinationRepository.searchPlaces(query, currentLoc)
            res.onSuccess { list ->
                if (list.isNotEmpty()) {
                    _uiState.update { state ->
                        state.copy(
                            searchResults = list,
                            isSearchingPlaces = false,
                            searchCenterLocation = onlineCenter,
                            searchCenterLabel = centerLabel,
                            selectedCategoryChip = intent?.categorySpec?.canonicalCategory,
                            isMultiMarkerCategoryView = true,
                            fitAllMarkersTrigger = state.fitAllMarkersTrigger + 1,
                            selectedDestination = list.firstOrNull() ?: state.selectedDestination
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSearchingPlaces = false) }
                }
            }.onFailure {
                _uiState.update { it.copy(isSearchingPlaces = false) }
            }
        }
    }

    fun submitPlaceSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            openDestinationSearchAtCurrentLocation()
            return
        }
        val currentLoc = _uiState.value.currentLocation
        val repoImpl = destinationRepository as? DestinationRepositoryImpl
        val intent = repoImpl?.parseSearchIntent(trimmed)
        val fastCenter = repoImpl?.getFastSearchCenter(trimmed, currentLoc) ?: currentLoc
        val centerLabel = intent?.explicitLocationName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.split(" ")
            ?.joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
            ?: "Your Current Location"

        val instantSuggestions = repoImpl
            ?.getInstantPlaceSuggestions(trimmed, currentLoc)
            .orEmpty()
        val instantFirst = instantSuggestions.firstOrNull()

        _uiState.update { state ->
            state.copy(
                searchQuery = trimmed,
                searchResults = instantSuggestions,
                isSearchingPlaces = true,
                searchCenterLocation = fastCenter,
                searchCenterLabel = centerLabel,
                selectedCategoryChip = intent?.categorySpec?.canonicalCategory,
                isMultiMarkerCategoryView = true,
                fitAllMarkersTrigger = state.fitAllMarkersTrigger + 1,
                selectedDestination = instantFirst ?: state.selectedDestination
            )
        }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            val onlineCenter = repoImpl?.resolveOnlineSearchCenter(trimmed, currentLoc) ?: fastCenter
            val res = destinationRepository.searchPlaces(trimmed, currentLoc)
            res.onSuccess { list ->
                val firstPlace = list.firstOrNull()
                if (list.isNotEmpty()) {
                    _uiState.update { state ->
                        state.copy(
                            searchResults = list,
                            isSearchingPlaces = false,
                            searchCenterLocation = onlineCenter,
                            searchCenterLabel = centerLabel,
                            selectedCategoryChip = intent?.categorySpec?.canonicalCategory,
                            isMultiMarkerCategoryView = true,
                            fitAllMarkersTrigger = state.fitAllMarkersTrigger + 1,
                            selectedDestination = firstPlace ?: state.selectedDestination
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSearchingPlaces = false) }
                }
            }.onFailure {
                _uiState.update { it.copy(isSearchingPlaces = false) }
            }
        }
    }

    fun selectCategoryChip(category: String) {
        val currentQuery = _uiState.value.searchQuery.trim()
        val repoImpl = destinationRepository as? DestinationRepositoryImpl
        val currentIntent = repoImpl?.parseSearchIntent(currentQuery)
        val explicitLocFromQuery = currentIntent?.explicitLocationName
            ?: currentQuery.takeIf {
                it.isNotEmpty() &&
                    currentIntent?.categorySpec == null &&
                    DestinationRepositoryImpl.isKnownLocationName(it)
            }
        val combinedQuery = if (!explicitLocFromQuery.isNullOrBlank()) {
            "$category in $explicitLocFromQuery"
        } else {
            category
        }
        submitPlaceSearch(combinedQuery)
    }

    fun showAllSearchMarkersOnMap() {
        _uiState.update { state ->
            state.copy(
                isMultiMarkerCategoryView = true,
                fitAllMarkersTrigger = state.fitAllMarkersTrigger + 1
            )
        }
    }

    fun focusOnSinglePlaceCandidate(destination: Destination) {
        _uiState.update { state ->
            state.copy(
                selectedDestination = destination,
                isMultiMarkerCategoryView = false,
                workflowState = NavigationWorkflowState.DESTINATION_SELECTED
            )
        }
        viewModelScope.launch {
            destinationRepository.saveRecentDestination(destination.copy(isDemoSample = false))
        }
    }

    fun selectQuickCategoryDestination(category: String, onSelected: () -> Unit) {
        when (category.uppercase()) {
            "HOME" -> {
                val target = DestinationRepositoryImpl.realWorldGazetteer.first { it.id == "dest_home_sipri" }
                selectDestinationCandidate(target)
                onSelected()
            }
            "WORK" -> {
                val target = DestinationRepositoryImpl.realWorldGazetteer.first { it.id == "dest_work_civil_lines" }
                selectDestinationCandidate(target)
                onSelected()
            }
            else -> {
                val catLabel = if (category.equals("HOSPITAL", ignoreCase = true)) "Hospital" else category
                submitPlaceSearch(catLabel)
                onSelected()
            }
        }
    }

    private fun localizeDestinationToDriverRegion(
        destination: Destination,
        currentLocation: LocationPoint
    ): Destination {
        if (!destination.id.startsWith("dest_")) return destination
        val distMeters = GeoUtils.haversineMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            destination.latitude,
            destination.longitude
        )
        if (distMeters <= 35_000.0) return destination

        val refOrigin = OsmRoadNetworkProvider.DEFAULT_ORIGIN
        val rawDLat = (destination.latitude - refOrigin.latitude) * 0.45
        val rawDLng = (destination.longitude - refOrigin.longitude) * 0.45
        val dLat = if (kotlin.math.abs(rawDLat) < 0.004) 0.0085 else rawDLat.coerceIn(-0.024, 0.024)
        val dLng = if (kotlin.math.abs(rawDLng) < 0.004) 0.0110 else rawDLng.coerceIn(-0.024, 0.024)

        val localLat = currentLocation.latitude + dLat
        val localLng = currentLocation.longitude + dLng
        val localDistKm = GeoUtils.haversineMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            localLat,
            localLng
        ) / 1000.0

        return destination.copy(
            latitude = localLat,
            longitude = localLng,
            distanceFromUserKm = (localDistKm * 1.25).coerceAtLeast(1.2)
        )
    }

    fun selectDestinationCandidate(destination: Destination) {
        val localized = localizeDestinationToDriverRegion(destination, _uiState.value.currentLocation)
        _uiState.update {
            it.copy(
                selectedDestination = localized,
                searchResults = listOf(localized),
                isMultiMarkerCategoryView = false,
                workflowState = NavigationWorkflowState.DESTINATION_SELECTED
            )
        }
        viewModelScope.launch {
            destinationRepository.saveRecentDestination(localized.copy(isDemoSample = false))
        }
    }

    fun deleteRecentDestination(destinationId: String) {
        viewModelScope.launch {
            destinationRepository.deleteRecentDestination(destinationId)
        }
    }

    fun clearAllRecentDestinations() {
        viewModelScope.launch {
            destinationRepository.clearAllRecentDestinations()
        }
    }

    fun restoreDefaultRecentDestinations() {
        viewModelScope.launch {
            destinationRepository.restoreDefaultRecentDestinations()
        }
    }

    fun selectPointOnMap(lat: Double, lng: Double) {
        viewModelScope.launch {
            val point = LocationPoint(lat, lng)
            val dest = destinationRepository.reverseGeocode(point)
            _uiState.update {
                it.copy(
                    selectedDestination = dest,
                    isMultiMarkerCategoryView = false,
                    workflowState = NavigationWorkflowState.DESTINATION_SELECTED
                )
            }
        }
    }

    // ========================================================================
    // Screen 5: Route Preview (Internal A* Route Calculation)
    // ========================================================================

    fun confirmDestinationAndCalculatePreview(onRouteReady: () -> Unit) {
        val rawDest = _uiState.value.selectedDestination
        val dest = localizeDestinationToDriverRegion(rawDest, _uiState.value.currentLocation)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedDestination = dest,
                    workflowState = NavigationWorkflowState.ROUTE_CALCULATING
                )
            }
            destinationRepository.saveRecentDestination(dest.copy(isDemoSample = false))

            // Calculate initial primary path without pre-diverting so any Admin hazard is indicated on the user's path first
            val calcResult = routingRepository.calculateRoutes(
                origin = _uiState.value.currentLocation,
                destination = dest,
                hazards = emptyList(),
                isRerouting = false
            )

            val rec = calcResult.recommendedRoute
            val alt = calcResult.alternateRoute
            if (rec != null) {
                initialPrimaryRoute = rec
                resetNavigationProgressBaseline(rec, _uiState.value.currentLocation)
                val alignedHazards = alignHazardsWithRoutePath(rawBackendHazards.ifEmpty { _uiState.value.activeHazards }, rec)
                val impact = routeImpactDetector.analyzeRouteImpact(
                    currentLocation = _uiState.value.currentLocation,
                    activeRoute = rec,
                    activeHazards = alignedHazards
                )
                _uiState.update {
                    it.copy(
                        workflowState = NavigationWorkflowState.ROUTE_READY,
                        recommendedRoute = rec,
                        alternateRoute = alt,
                        activeRoute = rec,
                        previousRouteBeforeDiversion = null,
                        isUsingAlternateInPreview = false,
                        activeHazards = alignedHazards,
                        remainingDistanceMeters = rec.totalDistanceMeters,
                        remainingEtaMinutes = rec.durationMinutes,
                        remainingEtaSeconds = max(60, rec.durationMinutes * 60),
                        relevantHazardsOnRoute = impact.allRelevantHazards,
                        primaryAffectingHazard = impact.primaryAffectingHazard
                    )
                }
                onRouteReady()
                pushLiveTelemetryToConsole(force = true)
            } else {
                _uiState.update {
                    it.copy(
                        workflowState = NavigationWorkflowState.ERROR,
                        recalculationErrorMessage = calcResult.errorMessage ?: "Unable to calculate route."
                    )
                }
            }
        }
    }

    fun selectRouteOptionInPreview(useAlternate: Boolean) {
        val state = _uiState.value
        val chosen = if (useAlternate && state.alternateRoute != null) {
            state.alternateRoute
        } else {
            state.recommendedRoute
        } ?: return

        resetNavigationProgressBaseline(chosen, state.currentLocation)
        val impact = routeImpactDetector.analyzeRouteImpact(
            currentLocation = state.currentLocation,
            activeRoute = chosen,
            activeHazards = state.activeHazards
        )

        _uiState.update {
            it.copy(
                isUsingAlternateInPreview = useAlternate,
                activeRoute = chosen,
                remainingDistanceMeters = chosen.totalDistanceMeters,
                remainingEtaMinutes = chosen.durationMinutes,
                remainingEtaSeconds = max(60, chosen.durationMinutes * 60),
                relevantHazardsOnRoute = impact.allRelevantHazards,
                primaryAffectingHazard = impact.primaryAffectingHazard
            )
        }
    }

    // ========================================================================
    // Screen 6, 7, 8, 9, 10: Live Navigation, Real-Time Hazard Alert & Rerouting
    // ========================================================================

    fun startDrivingNavigation() {
        val route = _uiState.value.activeRoute ?: _uiState.value.recommendedRoute ?: return
        navigationStartTimestamp = System.currentTimeMillis()
        handledHazardIdsForCurrentRoute.clear()
        resetNavigationProgressBaseline(route, _uiState.value.currentLocation)
        startContinuousGpsListening()

        val firstSeg = route.segments.getOrNull(1) ?: route.segments.firstOrNull()
        val initialInstruction = firstSeg?.instruction ?: "Continue on main route"

        _uiState.update {
            it.copy(
                workflowState = NavigationWorkflowState.NAVIGATING,
                activeRoute = route,
                previousRouteBeforeDiversion = null,
                remainingDistanceMeters = route.totalDistanceMeters,
                remainingEtaMinutes = route.durationMinutes,
                remainingEtaSeconds = max(60, route.durationMinutes * 60),
                currentTurnDistanceMeters = firstSeg?.distanceMeters?.coerceAtMost(500.0) ?: 500.0,
                currentTurnInstruction = initialInstruction,
                currentTurnManeuver = firstSeg?.maneuverType ?: "RIGHT",
                primaryAffectingHazard = null
            )
        }

        hazardAlertService.announceTurnInstruction(
            instruction = initialInstruction,
            voiceEnabled = _uiState.value.preferences.navigationVoiceEnabled && !_uiState.value.isVoiceMutedInNav
        )

        // Immediately evaluate any active backend/Firestore hazards on the selected route
        evaluateActiveNavigationAgainstHazards(_uiState.value.activeHazards)
        pushLiveTelemetryToConsole(force = true)
    }

    fun dismissHazardAlertAndContinue() {
        recalculationJob?.cancel()
        _uiState.update {
            it.copy(
                workflowState = NavigationWorkflowState.NAVIGATING
            )
        }
    }

    fun cancelRecalculation() {
        recalculationJob?.cancel()
        _uiState.update {
            it.copy(
                workflowState = NavigationWorkflowState.NAVIGATING,
                recalculationProgress = 0f,
                recalculationErrorMessage = null
            )
        }
    }

    fun toggleVoiceMuteInNavigation() {
        _uiState.update { it.copy(isVoiceMutedInNav = !it.isVoiceMutedInNav) }
    }

    fun completeActiveJourney() {
        recalculationJob?.cancel()

        val state = _uiState.value
        val route = state.activeRoute ?: state.recommendedRoute
        val dest = route?.destination ?: state.selectedDestination
        val distanceKm = (route?.totalDistanceMeters ?: 18400.0) / 1000.0
        val durationMin = route?.durationMinutes ?: 31

        val journey = Journey(
            id = "jrn_${UUID.randomUUID().toString().take(8)}",
            userId = state.currentUser?.id ?: "user_local",
            sourceName = "Current Location",
            destinationName = dest.name,
            destinationAddress = dest.address,
            sourceLat = state.currentLocation.latitude,
            sourceLng = state.currentLocation.longitude,
            destLat = dest.latitude,
            destLng = dest.longitude,
            distanceKm = distanceKm,
            durationMinutes = durationMin,
            startedAt = if (navigationStartTimestamp > 0L) navigationStartTimestamp else System.currentTimeMillis() - 1860_000L,
            completedAt = System.currentTimeMillis(),
            status = if (route?.isDivertedForSafety == true) "SAFELY_DIVERTED" else "COMPLETED",
            hazardsAvoidedCount = route?.avoidedHazardIds?.size ?: 0,
            isDemoRecord = false
        )

        viewModelScope.launch {
            journeyRepository.saveJourney(journey)
            destinationRepository.saveRecentDestination(dest.copy(isDemoSample = false))
        }

        hazardAlertService.announceArrival(
            destinationName = dest.name,
            voiceEnabled = state.preferences.navigationVoiceEnabled && !state.isVoiceMutedInNav
        )

        _uiState.update {
            it.copy(
                workflowState = NavigationWorkflowState.ARRIVED,
                lastCompletedJourney = journey
            )
        }
    }

    fun stopActiveNavigation() {
        recalculationJob?.cancel()
        _uiState.update {
            it.copy(
                workflowState = NavigationWorkflowState.HOME,
                primaryAffectingHazard = null,
                previousRouteBeforeDiversion = null
            )
        }
    }

    // ========================================================================
    // Settings Controls
    // ========================================================================

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            preferencesRepository.setLanguage(languageCode)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setNavigationVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNavigationVoiceEnabled(enabled)
        }
    }

    fun setAlertSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAlertSoundEnabled(enabled)
        }
    }

    fun setUseKilometers(useKm: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setUseKilometers(useKm)
        }
    }

    fun clearJourneyHistory() {
        viewModelScope.launch {
            journeyRepository.clearLocalHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        hazardAlertService.shutdown()
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = RoutePilotDatabase.getInstance(appContext)
            val dao = db.routePilotDao()
            val firestoreSource = FirestoreHazardDataSource(appContext)
            val restClient = RemoteBackendClient()
            val prefsRepo = UserPreferencesDataStore(appContext)
            val osmProvider = OsmRoadNetworkProvider()
            val aStarEngine = AStarRoutingEngine(osmProvider)
            val impactDetector = RouteImpactDetector()
            val locationTracker = LocationTracker(appContext)
            val alertService = HazardAlertService(appContext)

            HazardSyncWorker.schedulePeriodicSync(appContext)

            val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

            return RoutePilotViewModel(
                authRepository = AuthRepositoryImpl(appContext),
                destinationRepository = DestinationRepositoryImpl(appContext, dao, restClient),
                hazardRepository = HazardRepositoryImpl(dao, firestoreSource, restClient, appScope),
                journeyRepository = JourneyRepositoryImpl(dao, firestoreSource),
                routingRepository = RoutingRepositoryImpl(aStarEngine),
                preferencesRepository = prefsRepo,
                locationTracker = locationTracker,
                routeImpactDetector = impactDetector,
                hazardAlertService = alertService
            ) as T
        }
    }
}
