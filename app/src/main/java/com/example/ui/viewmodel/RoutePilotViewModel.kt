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
    val searchResults: List<Destination> = DestinationRepositoryImpl.defaultReferenceDestinations,
    val isSearchingPlaces: Boolean = false,
    val recentDestinations: List<Destination> = DestinationRepositoryImpl.defaultReferenceDestinations,
    val selectedDestination: Destination = DestinationRepositoryImpl.defaultReferenceDestinations.first(),
    val recommendedRoute: Route? = null,
    val alternateRoute: Route? = null,
    val activeRoute: Route? = null,
    val previousRouteBeforeDiversion: Route? = null,
    val isUsingAlternateInPreview: Boolean = false,
    val remainingDistanceMeters: Double = 18400.0,
    val remainingEtaMinutes: Int = 32,
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

    init {
        observePreferencesAndData()
        observeAuthentication()
        observeRealtimeHazards()
        refreshDeviceLocationStatus()
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
            destinationRepository.getRecentDestinations(includeDemoSamples = true)
                .collectLatest { recent ->
                    _uiState.update { it.copy(recentDestinations = recent) }
                }
        }

        viewModelScope.launch {
            journeyRepository.getJourneyHistory(includeDemoSamples = false)
                .collectLatest { history ->
                    _uiState.update { it.copy(journeyHistory = history) }
                }
        }
    }

    private fun observeAuthentication() {
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    private fun observeRealtimeHazards() {
        viewModelScope.launch {
            hazardRepository.observeActiveHazards(OperatingMode.LIVE)
                .collectLatest { hazards ->
                    rawBackendHazards = hazards
                    val referenceRoute = initialPrimaryRoute
                        ?: _uiState.value.previousRouteBeforeDiversion
                        ?: _uiState.value.activeRoute
                        ?: _uiState.value.recommendedRoute
                    val aligned = alignHazardsWithRoutePath(hazards, referenceRoute)
                    _uiState.update { it.copy(activeHazards = aligned) }
                    evaluateActiveNavigationAgainstHazards(aligned)
                }
        }
    }

    /**
     * Ensures that when an Admin triggers/creates an active hazard in the Admin Panel,
     * the hazard is positioned directly on the road path the user was travelling on (`referenceRoute`),
     * so the hazard marker + warning zone is clearly indicated on that exact path before the user
     * chooses another path.
     */
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
                // Anchor Admin preset/remote hazard directly onto the driver's active path midpoint
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
            startContinuousGpsListening()
        }
    }

    private fun startContinuousGpsListening() {
        if (gpsTrackingJob?.isActive == true) return
        gpsTrackingJob = viewModelScope.launch {
            locationTracker.observeLocationUpdates().collectLatest { gpsPoint ->
                onNewDriverLocationReceived(gpsPoint)
            }
        }
    }

    private fun onNewDriverLocationReceived(newPoint: LocationPoint) {
        val state = _uiState.value
        val activeRoute = state.activeRoute
        val isDriving = state.workflowState == NavigationWorkflowState.NAVIGATING ||
            state.workflowState == NavigationWorkflowState.ROUTE_UPDATED ||
            state.workflowState == NavigationWorkflowState.HAZARD_DETECTED

        if (!isDriving || activeRoute == null) {
            _uiState.update { it.copy(currentLocation = newPoint) }
            return
        }

        // 1. Check arrival at destination
        if (routeImpactDetector.hasArrivedAtDestination(
                newPoint,
                activeRoute.destination.latitude,
                activeRoute.destination.longitude
            )
        ) {
            completeActiveJourney()
            return
        }

        // 2. Update remaining distance and ETA based on driver progress along route
        val distToDest = GeoUtils.haversineMeters(
            newPoint.latitude,
            newPoint.longitude,
            activeRoute.destination.latitude,
            activeRoute.destination.longitude
        ) * 1.25
        val ratio = (distToDest / max(100.0, activeRoute.totalDistanceMeters)).coerceIn(0.05, 1.0)
        val remMeters = activeRoute.totalDistanceMeters * ratio
        val remMins = max(1, (activeRoute.durationMinutes * ratio).roundToInt())

        _uiState.update {
            it.copy(
                currentLocation = newPoint,
                remainingDistanceMeters = remMeters,
                remainingEtaMinutes = remMins
            )
        }

        // 3. Re-check hazard impact at the new location
        evaluateActiveNavigationAgainstHazards(state.activeHazards)
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
            _uiState.update { it.copy(authError = null) }
            val res = authRepository.loginWithEmail(email, password, rememberMe)
            res.onSuccess {
                preferencesRepository.setRememberMe(rememberMe, email)
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
            _uiState.update { it.copy(authError = null) }
            val res = authRepository.signUpWithEmail(name, email, password)
            res.onSuccess {
                preferencesRepository.setRememberMe(true, email)
                _uiState.update { state -> state.copy(workflowState = NavigationWorkflowState.HOME, authError = null) }
                onSuccess()
            }.onFailure { err ->
                _uiState.update { state -> state.copy(authError = err.message ?: "Sign up failed.") }
            }
        }
    }

    fun continueWithGoogle(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(authError = null) }
            authRepository.continueWithGoogle(null).onSuccess {
                _uiState.update { state -> state.copy(workflowState = NavigationWorkflowState.HOME) }
                onSuccess()
            }
        }
    }

    fun continueAsGuest(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(authError = null) }
            authRepository.continueAsGuest().onSuccess {
                _uiState.update { state -> state.copy(workflowState = NavigationWorkflowState.HOME) }
                onSuccess()
            }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            val res = authRepository.sendPasswordReset(email)
            res.onSuccess {
                _uiState.update { it.copy(statusBannerMessage = "Password reset instructions sent to $email") }
            }.onFailure { err ->
                _uiState.update { it.copy(authError = err.message) }
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

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchingPlaces = true) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(220L)
            val res = destinationRepository.searchPlaces(query, _uiState.value.currentLocation)
            res.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        searchResults = list,
                        isSearchingPlaces = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isSearchingPlaces = false) }
            }
        }
    }

    fun selectQuickCategoryDestination(category: String, onSelected: () -> Unit) {
        val target = when (category.uppercase()) {
            "HOME" -> DestinationRepositoryImpl.realWorldGazetteer.first { it.id == "dest_home_sipri" }
            "WORK" -> DestinationRepositoryImpl.realWorldGazetteer.first { it.id == "dest_work_civil_lines" }
            "HOSPITAL" -> DestinationRepositoryImpl.defaultReferenceDestinations.first()
            else -> DestinationRepositoryImpl.defaultReferenceDestinations.first()
        }
        selectDestinationCandidate(target)
        onSelected()
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
                        relevantHazardsOnRoute = impact.allRelevantHazards,
                        primaryAffectingHazard = impact.primaryAffectingHazard
                    )
                }
                onRouteReady()
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

        val firstSeg = route.segments.getOrNull(1) ?: route.segments.firstOrNull()
        val initialInstruction = firstSeg?.instruction ?: "Continue on main route"

        _uiState.update {
            it.copy(
                workflowState = NavigationWorkflowState.NAVIGATING,
                activeRoute = route,
                previousRouteBeforeDiversion = null,
                remainingDistanceMeters = route.totalDistanceMeters,
                remainingEtaMinutes = route.durationMinutes,
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
            userId = state.currentUser?.id ?: "guest_driver",
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
