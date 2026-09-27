package com.example.domain.repository

import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.Journey
import com.example.domain.model.LocationPoint
import com.example.domain.model.OperatingMode
import com.example.domain.model.RouteCalculationResult
import com.example.domain.model.User
import kotlinx.coroutines.flow.Flow

interface HazardRepository {
    fun observeActiveHazards(mode: OperatingMode): Flow<List<Hazard>>
    suspend fun getHazard(id: String): Hazard?
    suspend fun refreshHazardsFromBackend(): Result<List<Hazard>>

    // Isolated Demo Mode controls for academic/project presentation only
    fun triggerDemoBridgeHazard()
    fun updateDemoHazardStatusToWarning()
    fun clearDemoHazards()
    fun resetDemoScenario()
}

interface JourneyRepository {
    suspend fun saveJourney(journey: Journey)
    fun getJourneyHistory(includeDemoSamples: Boolean): Flow<List<Journey>>
    suspend fun clearLocalHistory()
}

interface DestinationRepository {
    fun getRecentDestinations(includeDemoSamples: Boolean): Flow<List<Destination>>
    suspend fun saveRecentDestination(destination: Destination)
    suspend fun searchPlaces(
        query: String,
        currentLocation: LocationPoint?
    ): Result<List<Destination>>
    suspend fun reverseGeocode(point: LocationPoint): Destination
}

interface RoutingRepository {
    suspend fun calculateRoutes(
        origin: LocationPoint,
        destination: Destination,
        hazards: List<Hazard>,
        isRerouting: Boolean = false
    ): RouteCalculationResult
}

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun loginWithEmail(email: String, password: String, rememberMe: Boolean): Result<User>
    suspend fun signUpWithEmail(name: String, email: String, password: String): Result<User>
    suspend fun continueWithGoogle(idToken: String?): Result<User>
    suspend fun continueAsGuest(): Result<User>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun logout()
}

data class DriverPreferences(
    val languageCode: String = "en",
    val notificationsEnabled: Boolean = true,
    val navigationVoiceEnabled: Boolean = true,
    val alertSoundEnabled: Boolean = true,
    val useKilometers: Boolean = true,
    val operatingMode: OperatingMode = OperatingMode.DEMO,
    val rememberMe: Boolean = true,
    val savedEmail: String = ""
)

interface PreferencesRepository {
    val preferencesFlow: Flow<DriverPreferences>
    suspend fun setLanguage(languageCode: String)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setNavigationVoiceEnabled(enabled: Boolean)
    suspend fun setAlertSoundEnabled(enabled: Boolean)
    suspend fun setUseKilometers(useKm: Boolean)
    suspend fun setOperatingMode(mode: OperatingMode)
    suspend fun setRememberMe(remember: Boolean, email: String)
}
