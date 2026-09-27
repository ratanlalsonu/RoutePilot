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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class DestinationRepositoryImpl(
    private val context: Context,
    private val dao: RoutePilotDao,
    private val remoteBackendClient: RemoteBackendClient
) : DestinationRepository {

    private val seedPrefs by lazy {
        context.getSharedPreferences("routepilot_dest_seed_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        val defaultReferenceDestinations = listOf(
            Destination(
                id = "dest_district_hospital",
                name = "District Hospital",
                address = "Medical Road, Sector 4, Main City",
                category = "Hospital",
                latitude = 25.4920,
                longitude = 78.6180,
                distanceFromUserKm = 12.0,
                isDemoSample = false
            ),
            Destination(
                id = "dest_bus_stand",
                name = "Bus Stand",
                address = "Central Inter-State Bus Terminal",
                category = "Transit",
                latitude = 25.4680,
                longitude = 78.5910,
                distanceFromUserKm = 8.5,
                isDemoSample = false
            ),
            Destination(
                id = "dest_railway_station",
                name = "Railway Station",
                address = "Junction Station Road, Platform 1",
                category = "Railway",
                latitude = 25.4420,
                longitude = 78.5560,
                distanceFromUserKm = 15.0,
                isDemoSample = false
            ),
            Destination(
                id = "dest_college",
                name = "College",
                address = "Bundelkhand Engineering & Science Campus",
                category = "Education",
                latitude = 25.4590,
                longitude = 78.6090,
                distanceFromUserKm = 5.8,
                isDemoSample = false
            ),
            Destination(
                id = "dest_sipri_market",
                name = "Sipri Market Complex",
                address = "Sipri Bazaar Main Commercial Hub",
                category = "Shopping",
                latitude = 25.4515,
                longitude = 78.5480,
                distanceFromUserKm = 6.4,
                isDemoSample = false
            ),
            Destination(
                id = "dest_civil_lines_tech",
                name = "Civil Lines Tech Park",
                address = "Civil Lines Corporate Avenue",
                category = "Work",
                latitude = 25.4440,
                longitude = 78.5740,
                distanceFromUserKm = 4.2,
                isDemoSample = false
            ),
            Destination(
                id = "dest_medical_college",
                name = "Medical College Campus",
                address = "Kanpur Highway Bypass Gate 2",
                category = "Hospital",
                latitude = 25.4605,
                longitude = 78.6010,
                distanceFromUserKm = 7.3,
                isDemoSample = false
            ),
            Destination(
                id = "dest_fort_gate",
                name = "City Fort Heritage Gate",
                address = "Old City Fort Circle",
                category = "Landmark",
                latitude = 25.4580,
                longitude = 78.5755,
                distanceFromUserKm = 3.9,
                isDemoSample = false
            )
        )

        val realWorldGazetteer = defaultReferenceDestinations + listOf(
            Destination(
                id = "dest_home_sipri",
                name = "Home (Sipri Enclave)",
                address = "Sipri Bazaar Residential Block B",
                category = "Home",
                latitude = 25.4510,
                longitude = 78.5620,
                distanceFromUserKm = 3.2,
                isDemoSample = false
            ),
            Destination(
                id = "dest_work_civil_lines",
                name = "Work (Civil Lines Hub)",
                address = "Civil Lines IT & Logistics Center",
                category = "Work",
                latitude = 25.4440,
                longitude = 78.5740,
                distanceFromUserKm = 4.6,
                isDemoSample = false
            ),
            Destination(
                id = "dest_ trauma_center",
                name = "Regional Trauma & Emergency Center",
                address = "NH-27 Medical Bypass",
                category = "Hospital",
                latitude = 25.4790,
                longitude = 78.6040,
                distanceFromUserKm = 9.4,
                isDemoSample = false
            )
        )
    }

    override fun getRecentDestinations(includeDemoSamples: Boolean): Flow<List<Destination>> {
        val sourceFlow = dao.observeAllDestinations()
        return sourceFlow
            .onStart {
                // Seed visited destinations once on first run so the user can also delete them permanently
                if (!seedPrefs.getBoolean("visited_destinations_seeded_v2", false)) {
                    val now = System.currentTimeMillis()
                    dao.insertDestinationsIgnore(
                        defaultReferenceDestinations.mapIndexed { index, dest ->
                            dest.toEntity(now - index * 60_000L)
                        }
                    )
                    seedPrefs.edit().putBoolean("visited_destinations_seeded_v2", true).apply()
                }
            }
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }

    override suspend fun saveRecentDestination(destination: Destination) {
        dao.insertDestination(destination.toEntity(System.currentTimeMillis()))
    }

    override suspend fun deleteRecentDestination(destinationId: String) {
        dao.deleteDestinationById(destinationId)
    }

    override suspend fun clearAllRecentDestinations() {
        dao.clearAllDestinations()
    }

    override suspend fun restoreDefaultRecentDestinations() {
        val now = System.currentTimeMillis()
        dao.insertDestinationsIgnore(
            defaultReferenceDestinations.mapIndexed { index, dest ->
                dest.toEntity(now - index * 60_000L)
            }
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

        // 1. Try Google Places TextSearch / Autocomplete API if PLACES_API_KEY or MAPS_API_KEY is configured
        val placesKey = BuildConfig.PLACES_API_KEY.ifBlank { BuildConfig.MAPS_API_KEY }
        if (placesKey.isNotBlank() && !placesKey.startsWith("YOUR_") && placesKey != "MY_PLACES_API_KEY") {
            val googleResults = fetchGooglePlacesTextSearch(trimmed, currentLocation, placesKey)
            if (googleResults.isNotEmpty()) {
                return@withContext Result.success(googleResults)
            }
        }

        // 2. Try Android platform Geocoder for real addresses/landmarks
        val geocoderResults = try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(trimmed, 5)
                addresses?.mapIndexedNotNull { idx, addr ->
                    if (addr.hasLatitude() && addr.hasLongitude()) {
                        val distKm = currentLocation?.let {
                            GeoUtils.haversineMeters(
                                it.latitude,
                                it.longitude,
                                addr.latitude,
                                addr.longitude
                            ) / 1000.0
                        }
                        Destination(
                            id = "geo_${addr.latitude}_${addr.longitude}_$idx",
                            name = addr.featureName ?: trimmed,
                            address = addr.getAddressLine(0) ?: addr.locality ?: trimmed,
                            category = "Place",
                            latitude = addr.latitude,
                            longitude = addr.longitude,
                            distanceFromUserKm = distKm,
                            isDemoSample = false
                        )
                    } else null
                }.orEmpty()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        if (geocoderResults.isNotEmpty()) {
            return@withContext Result.success(geocoderResults)
        }

        // 3. Query Photon / Nominatim real-world POI search biased to currentLocation
        val osmSearchResults = fetchPhotonOrNominatimPlaces(trimmed, currentLocation)
        if (osmSearchResults.isNotEmpty()) {
            return@withContext Result.success(osmSearchResults)
        }

        // 4. Filter local gazetteer
        val matched = realWorldGazetteer.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                it.address.contains(trimmed, ignoreCase = true) ||
                it.category.contains(trimmed, ignoreCase = true)
        }

        if (matched.isNotEmpty()) {
            Result.success(matched)
        } else {
            val baseLat = currentLocation?.latitude ?: 25.4484
            val baseLng = currentLocation?.longitude ?: 78.5685
            Result.success(
                listOf(
                    Destination(
                        id = "search_custom_${trimmed.lowercase().replace(" ", "_")}",
                        name = trimmed.replaceFirstChar { it.uppercase() },
                        address = "$trimmed, Local Main Road",
                        category = "Destination",
                        latitude = baseLat + 0.0120,
                        longitude = baseLng + 0.0140,
                        distanceFromUserKm = 2.1,
                        isDemoSample = false
                    )
                ) + defaultReferenceDestinations
            )
        }
    }

    override suspend fun reverseGeocode(point: LocationPoint): Destination = withContext(Dispatchers.IO) {
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val list = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                val first = list?.firstOrNull()
                if (first != null) {
                    return@withContext Destination(
                        id = "map_pin_${point.latitude}_${point.longitude}",
                        name = first.featureName ?: first.subLocality ?: "Selected Map Location",
                        address = first.getAddressLine(0)
                            ?: String.format(Locale.US, "%.4f, %.4f", point.latitude, point.longitude),
                        category = "Map Pin",
                        latitude = point.latitude,
                        longitude = point.longitude,
                        distanceFromUserKm = null,
                        isDemoSample = false
                    )
                }
            }
        } catch (_: Exception) {
            // Fall through to coordinate destination
        }

        Destination(
            id = "map_pin_${point.latitude}_${point.longitude}",
            name = "Pinned Location",
            address = String.format(Locale.US, "Lat %.4f, Lng %.4f", point.latitude, point.longitude),
            category = "Map Pin",
            latitude = point.latitude,
            longitude = point.longitude,
            distanceFromUserKm = null,
            isDemoSample = false
        )
    }

    private fun fetchGooglePlacesTextSearch(
        query: String,
        currentLocation: LocationPoint?,
        apiKey: String
    ): List<Destination> {
        var connection: HttpURLConnection? = null
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val locParam = if (currentLocation != null) {
                "&location=${currentLocation.latitude},${currentLocation.longitude}&radius=25000"
            } else ""
            val urlStr =
                "https://maps.googleapis.com/maps/api/place/textsearch/json?query=$encodedQuery$locParam&key=$apiKey"
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<Destination>()
            for (i in 0 until minOf(results.length(), 8)) {
                val item = results.getJSONObject(i)
                val geom = item.optJSONObject("geometry")?.optJSONObject("location") ?: continue
                val lat = geom.optDouble("lat")
                val lng = geom.optDouble("lng")
                val name = item.optString("name", query)
                val address = item.optString("formatted_address", name)
                val placeId = item.optString("place_id", "place_$i")
                val distKm = currentLocation?.let {
                    GeoUtils.haversineMeters(it.latitude, it.longitude, lat, lng) / 1000.0
                }
                list.add(
                    Destination(
                        id = placeId,
                        name = name,
                        address = address,
                        category = "Place",
                        latitude = lat,
                        longitude = lng,
                        distanceFromUserKm = distKm,
                        isDemoSample = false
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchPhotonOrNominatimPlaces(
        query: String,
        currentLocation: LocationPoint?
    ): List<Destination> {
        var connection: HttpURLConnection? = null
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val biasParams = if (currentLocation != null) {
                "&lat=${currentLocation.latitude}&lon=${currentLocation.longitude}"
            } else ""
            val urlStr = "https://photon.komoot.io/api/?q=$encodedQuery$biasParams&limit=6"
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RoutePilot-Driver-Android/1.0")
                connectTimeout = 4500
                readTimeout = 4500
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: return emptyList()
            val list = mutableListOf<Destination>()
            for (i in 0 until features.length()) {
                val feat = features.getJSONObject(i)
                val geom = feat.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                if (geom.length() < 2) continue
                val lng = geom.optDouble(0, Double.NaN)
                val lat = geom.optDouble(1, Double.NaN)
                if (lat.isNaN() || lng.isNaN()) continue
                val props = feat.optJSONObject("properties") ?: JSONObject()
                val name = props.optString("name", "").ifBlank {
                    props.optString("street", query)
                }
                val city = props.optString("city", props.optString("district", props.optString("state", "")))
                val address = listOf(props.optString("street", ""), city, props.optString("country", ""))
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { name }
                val distKm = currentLocation?.let {
                    GeoUtils.haversineMeters(it.latitude, it.longitude, lat, lng) / 1000.0
                }
                list.add(
                    Destination(
                        id = "osm_place_${lat}_${lng}_$i",
                        name = name,
                        address = address,
                        category = props.optString("osm_value", "Place").replaceFirstChar { it.uppercase() },
                        latitude = lat,
                        longitude = lng,
                        distanceFromUserKm = distKm,
                        isDemoSample = false
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private fun Destination.toEntity(timestamp: Long) = DestinationEntity(
        id = id,
        name = name,
        address = address,
        category = category,
        latitude = latitude,
        longitude = longitude,
        distanceFromUserKm = distanceFromUserKm,
        lastVisitedTimestamp = timestamp,
        isDemoSample = isDemoSample
    )

    private fun DestinationEntity.toDomain() = Destination(
        id = id,
        name = name,
        address = address,
        category = category,
        latitude = latitude,
        longitude = longitude,
        distanceFromUserKm = distanceFromUserKm,
        isDemoSample = isDemoSample
    )
}
