package com.example.data.remote

import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.HazardStatus
import com.example.domain.model.HazardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Clean REST Remote Data Source for:
 * 1. Common Backend Hazard & Journey API
 * 2. Real-world OpenStreetMap Nominatim Place Search (fallback when PLACES_API_KEY is not configured)
 */
class RemoteBackendClient(
    private val baseUrl: String = "https://routepilot-backend.example.com/api"
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fetchActiveHazardsFromRest(): Result<List<Hazard>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/hazards/active")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Backend HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parseHazardsJson(body)
            }
        }
    }

    /**
     * Queries OpenStreetMap Nominatim Geocoding service for real-world place search results
     * (cities, hospitals, stations, colleges, landmarks) with real coordinates.
     */
    suspend fun searchRealWorldPlacesNominatim(query: String): Result<List<Destination>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?format=json&limit=8&countrycodes=in&q=$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RoutePilot-DriverApp-BTechProject/1.0")
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Geocoding HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    val array = JSONArray(body)
                    val results = ArrayList<Destination>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val placeId = obj.optString("place_id", "osm_$i")
                        val displayName = obj.optString("display_name", query)
                        val parts = displayName.split(",").map { it.trim() }
                        val shortName = parts.firstOrNull().takeUnless { it.isNullOrBlank() } ?: query
                        val address = if (parts.size > 1) {
                            parts.drop(1).take(3).joinToString(", ")
                        } else {
                            displayName
                        }
                        val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                        val lon = obj.optString("lon").toDoubleOrNull() ?: continue
                        val type = obj.optString("type", "Place")

                        results.add(
                            Destination(
                                id = "osm_$placeId",
                                name = shortName,
                                address = address,
                                latitude = lat,
                                longitude = lon,
                                category = type.replaceFirstChar { it.uppercase() }
                            )
                        )
                    }
                    results
                }
            }
        }

    private fun parseHazardsJson(jsonStr: String): List<Hazard> {
        val arr = JSONArray(jsonStr)
        val list = ArrayList<Hazard>()
        for (i in 0 until arr.length()) {
            val obj: JSONObject = arr.getJSONObject(i)
            list.add(
                Hazard(
                    id = obj.optString("id", "hazard_$i"),
                    name = obj.optString("name", "Road Hazard"),
                    type = runCatching { HazardType.valueOf(obj.optString("type", "OTHER")) }
                        .getOrDefault(HazardType.OTHER),
                    severity = runCatching { HazardSeverity.valueOf(obj.optString("severity", "HIGH")) }
                        .getOrDefault(HazardSeverity.HIGH),
                    status = runCatching { HazardStatus.valueOf(obj.optString("status", "WARNING")) }
                        .getOrDefault(HazardStatus.WARNING),
                    latitude = obj.optDouble("latitude", 0.0),
                    longitude = obj.optDouble("longitude", 0.0),
                    radiusMeters = obj.optDouble("radius", 350.0),
                    description = obj.optString("description", ""),
                    roadId = obj.optString("roadId").takeIf { it.isNotBlank() },
                    bridgeId = obj.optString("bridgeId").takeIf { it.isNotBlank() },
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    active = obj.optBoolean("active", true),
                    source = obj.optString("source", "BACKEND")
                )
            )
        }
        return list
    }
}
