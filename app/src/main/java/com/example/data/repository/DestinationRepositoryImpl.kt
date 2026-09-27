package com.example.data.repository

import android.content.Context
import android.location.Geocoder
import com.example.BuildConfig
import com.example.data.local.DestinationEntity
import com.example.data.local.RoutePilotDao
import com.example.data.remote.RemoteBackendClient
import com.example.domain.model.Destination
import com.example.domain.model.LocationPoint
import com.example.domain.repository.DestinationRepository
import com.example.domain.routing.GeoUtils
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

class DestinationRepositoryImpl(
    private val context: Context,
    private val dao: RoutePilotDao,
    private val remoteBackendClient: RemoteBackendClient
) : DestinationRepository {

    private var sessionToken: AutocompleteSessionToken? = null

    private val placesClient: PlacesClient? by lazy {
        runCatching {
            val apiKey = resolvePlacesApiKey()
            if (apiKey.isNotBlank()) {
                if (!Places.isInitialized()) {
                    Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey)
                }
                Places.createClient(context.applicationContext)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun resolvePlacesApiKey(): String {
        val placesKey = runCatching { BuildConfig.PLACES_API_KEY }.getOrDefault("")
        val mapsKey = runCatching { BuildConfig.MAPS_API_KEY }.getOrDefault("")
        return when {
            placesKey.isNotBlank() && !placesKey.startsWith("YOUR_") -> placesKey
            mapsKey.isNotBlank() && !mapsKey.startsWith("YOUR_") -> mapsKey
            else -> ""
        }
    }

    override fun getRecentDestinations(includeDemoSamples: Boolean): Flow<List<Destination>> {
        val flow = if (includeDemoSamples) {
            dao.observeAllDestinations()
        } else {
            dao.observeLiveDestinations()
        }
        return flow.map { list ->
            val mapped = list.map { it.toDomain() }
            if (mapped.isEmpty() && includeDemoSamples) {
                defaultReferenceDestinations
            } else {
                mapped
            }
        }
    }

    override suspend fun saveRecentDestination(destination: Destination) {
        dao.insertDestination(
            DestinationEntity.fromDomain(
                destination.copy(lastVisitedTimestamp = System.currentTimeMillis())
            )
        )
    }

    override suspend fun searchPlaces(
        query: String,
        currentLocation: LocationPoint?
    ): Result<List<Destination>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.success(defaultReferenceDestinations)
        }

        // 1. Try Google Places SDK (New) with AutocompleteSessionToken if API key is configured
        val client = placesClient
        if (client != null) {
            val placesResult = runCatching {
                val token = sessionToken ?: AutocompleteSessionToken.newInstance().also {
                    sessionToken = it
                }
                val requestBuilder = FindAutocompletePredictionsRequest.builder()
                    .setSessionToken(token)
                    .setQuery(trimmed)

                if (currentLocation != null) {
                    val delta = 0.25
                    requestBuilder.setLocationBias(
                        RectangularBounds.newInstance(
                            LatLng(currentLocation.latitude - delta, currentLocation.longitude - delta),
                            LatLng(currentLocation.latitude + delta, currentLocation.longitude + delta)
                        )
                    )
                }

                val response = client.findAutocompletePredictions(requestBuilder.build()).await()
                val predictions = response.autocompletePredictions.take(5)
                val fetchedDestinations = ArrayList<Destination>()

                val placeFields = listOf(
                    Place.Field.ID,
                    Place.Field.DISPLAY_NAME,
                    Place.Field.FORMATTED_ADDRESS,
                    Place.Field.LOCATION
                )

                for (prediction in predictions) {
                    val fetchReq = FetchPlaceRequest.builder(prediction.placeId, placeFields)
                        .setSessionToken(token)
                        .build()
                    val placeResp = client.fetchPlace(fetchReq).await()
                    val place = placeResp.place
                    val loc = place.location ?: continue
                    val distKm = currentLocation?.let {
                        GeoUtils.haversineMeters(it.latitude, it.longitude, loc.latitude, loc.longitude) / 1000.0
                    }
                    fetchedDestinations.add(
                        Destination(
                            id = place.id ?: prediction.placeId,
                            name = place.displayName ?: prediction.getPrimaryText(null).toString(),
                            address = place.formattedAddress ?: prediction.getSecondaryText(null).toString(),
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            category = "Place",
                            distanceFromUserKm = distKm,
                            isDemoSample = false
                        )
                    )
                }
                // Reset session token after place fetch
                sessionToken = null
                fetchedDestinations
            }.getOrNull()

            if (!placesResult.isNullOrEmpty()) {
                return@withContext Result.success(placesResult)
            }
        }

        // 2. Match against real-coordinate gazetteer + OpenStreetMap Nominatim live geocoding
        val gazetteerMatches = realWorldGazetteer.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                it.address.contains(trimmed, ignoreCase = true) ||
                it.category.contains(trimmed, ignoreCase = true)
        }.map { dest ->
            val distKm = currentLocation?.let {
                (GeoUtils.haversineMeters(it.latitude, it.longitude, dest.latitude, dest.longitude) * 2.4) / 1000.0
            } ?: dest.distanceFromUserKm
            dest.copy(distanceFromUserKm = distKm)
        }

        val nominatimMatches = remoteBackendClient.searchRealWorldPlacesNominatim(trimmed)
            .getOrDefault(emptyList())
            .map { dest ->
                val distKm = currentLocation?.let {
                    GeoUtils.haversineMeters(it.latitude, it.longitude, dest.latitude, dest.longitude) / 1000.0
                }
                dest.copy(distanceFromUserKm = distKm)
            }

        val combined = (gazetteerMatches + nominatimMatches)
            .distinctBy { "${it.name.lowercase()}_${String.format(Locale.US, "%.3f", it.latitude)}" }

        Result.success(combined)
    }

    override suspend fun reverseGeocode(point: LocationPoint): Destination = withContext(Dispatchers.IO) {
        val addressText = runCatching {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
            val first = addresses?.firstOrNull()
            if (first != null) {
                val feature = first.featureName ?: first.thoroughfare ?: "Selected Map Pin"
                val locality = listOfNotNull(first.subLocality, first.locality, first.adminArea)
                    .joinToString(", ")
                return@runCatching Destination(
                    id = "map_pin_${System.currentTimeMillis()}",
                    name = feature,
                    address = locality.ifBlank {
                        String.format(Locale.US, "%.4f° N, %.4f° E", point.latitude, point.longitude)
                    },
                    latitude = point.latitude,
                    longitude = point.longitude,
                    category = "Map Pin"
                )
            }
            null
        }.getOrNull()

        addressText ?: Destination(
            id = "map_pin_${System.currentTimeMillis()}",
            name = "Pinned Road Location",
            address = String.format(Locale.US, "%.4f° N, %.4f° E", point.latitude, point.longitude),
            latitude = point.latitude,
            longitude = point.longitude,
            category = "Map Pin"
        )
    }

    companion object {
        // Matches Screen 3, 4, 5 of the reference design + real Indian landmarks from Prompt Section 6
        val defaultReferenceDestinations = listOf(
            Destination(
                id = "dest_district_hospital_jhansi",
                name = "District Hospital",
                address = "Jhansi, Uttar Pradesh",
                latitude = 25.4595,
                longitude = 78.5820,
                category = "Hospital",
                distanceFromUserKm = 12.0,
                isDemoSample = true
            ),
            Destination(
                id = "dest_bus_stand_jhansi",
                name = "Bus Stand",
                address = "Kanpur Road, Jhansi, Uttar Pradesh",
                latitude = 25.4455,
                longitude = 78.5698,
                category = "Transit",
                distanceFromUserKm = 8.5,
                isDemoSample = true
            ),
            Destination(
                id = "dest_railway_station_jhansi",
                name = "Railway Station",
                address = "Virangana Lakshmibai Junction, Jhansi, UP",
                latitude = 25.4392,
                longitude = 78.5572,
                category = "Railway",
                distanceFromUserKm = 15.0,
                isDemoSample = true
            ),
            Destination(
                id = "dest_college_jhansi",
                name = "College",
                address = "Bundelkhand Institute of Engineering & Technology, Jhansi",
                latitude = 25.4590,
                longitude = 78.6040,
                category = "Education",
                distanceFromUserKm = 5.8,
                isDemoSample = true
            )
        )

        val realWorldGazetteer = defaultReferenceDestinations + listOf(
            Destination(
                id = "dest_rec_banda",
                name = "Rajkiya Engineering College Banda",
                address = "Atarra, Banda, Uttar Pradesh 210201",
                latitude = 25.2916,
                longitude = 80.5698,
                category = "College",
                distanceFromUserKm = 192.0,
                isDemoSample = false
            ),
            Destination(
                id = "dest_lucknow_railway",
                name = "Lucknow Railway Station",
                address = "Charbagh, Lucknow, Uttar Pradesh 226004",
                latitude = 26.8317,
                longitude = 80.9234,
                category = "Railway",
                distanceFromUserKm = 298.0,
                isDemoSample = false
            ),
            Destination(
                id = "dest_india_gate",
                name = "India Gate",
                address = "Kartavya Path, New Delhi, Delhi 110001",
                latitude = 28.6129,
                longitude = 77.2295,
                category = "Landmark",
                distanceFromUserKm = 415.0,
                isDemoSample = false
            ),
            Destination(
                id = "dest_kanpur_central",
                name = "Kanpur Central Railway Station",
                address = "Mirpur, Kanpur, Uttar Pradesh 208004",
                latitude = 26.4539,
                longitude = 80.3512,
                category = "Railway",
                distanceFromUserKm = 220.0,
                isDemoSample = false
            ),
            Destination(
                id = "dest_igi_airport",
                name = "Indira Gandhi International Airport",
                address = "New Delhi, Delhi 110037",
                latitude = 28.5562,
                longitude = 77.1000,
                category = "Airport",
                distanceFromUserKm = 422.0,
                isDemoSample = false
            ),
            Destination(
                id = "dest_home_sipri",
                name = "Home (Sipri Bazaar)",
                address = "Sipri Bazaar, Jhansi, Uttar Pradesh",
                latitude = 25.4510,
                longitude = 78.5380,
                category = "Home",
                distanceFromUserKm = 2.4,
                isDemoSample = false
            ),
            Destination(
                id = "dest_work_civil_lines",
                name = "Work (Civil Lines Tech Park)",
                address = "Civil Lines, Jhansi, Uttar Pradesh",
                latitude = 25.4578,
                longitude = 78.5745,
                category = "Work",
                distanceFromUserKm = 9.2,
                isDemoSample = false
            )
        )
    }
}
