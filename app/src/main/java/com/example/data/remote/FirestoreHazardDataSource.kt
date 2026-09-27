package com.example.data.remote

import android.content.Context
import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.HazardStatus
import com.example.domain.model.HazardType
import com.example.domain.model.Journey
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Real-Time Firebase Firestore Data Source for RoutePilot Driver APK.
 *
 * Subscribes to the shared `hazards` collection where `active == true` so changes made
 * from the separate Admin Website or ESP32 IoT backend immediately push to the Driver APK
 * without reopening or manually refreshing.
 */
class FirestoreHazardDataSource(
    private val context: Context
) {
    private val firestore: FirebaseFirestore? by lazy {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        }.getOrNull()
    }

    val isFirestoreConfigured: Boolean
        get() = firestore != null

    /**
     * Real-time Firestore listener on `hazards` collection where `active == true`.
     */
    fun observeActiveHazardsRealtime(): Flow<List<Hazard>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = db.collection("hazards")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toHazardOrNull()
                }.orEmpty()
                trySend(list)
            }

        awaitClose {
            registration.remove()
        }
    }

    suspend fun getHazardById(hazardId: String): Hazard? {
        val db = firestore ?: return null
        return runCatching {
            val doc = db.collection("hazards").document(hazardId).get().await()
            if (doc.exists()) doc.toHazardOrNull() else null
        }.getOrNull()
    }

    suspend fun saveDriverJourney(journey: Journey) {
        val db = firestore ?: return
        runCatching {
            val data = mapOf(
                "userId" to journey.userId,
                "source" to journey.sourceName,
                "destination" to journey.destinationName,
                "destinationAddress" to journey.destinationAddress,
                "distance" to journey.distanceKm,
                "duration" to journey.durationMinutes,
                "startedAt" to journey.startedAt,
                "completedAt" to journey.completedAt,
                "status" to journey.status,
                "hazardsAvoidedCount" to journey.hazardsAvoidedCount
            )
            db.collection("journeys").document(journey.id).set(data).await()
        }
    }

    private fun DocumentSnapshot.toHazardOrNull(): Hazard? {
        return runCatching {
            val id = id
            val name = getString("name") ?: "Road / Bridge Hazard"
            val typeStr = getString("type") ?: "BRIDGE_DAMAGE"
            val severityStr = getString("severity") ?: "CRITICAL"
            val statusStr = getString("status") ?: "BLOCKED"
            val lat = getDouble("latitude") ?: return null
            val lng = getDouble("longitude") ?: return null
            val radius = getDouble("radius") ?: 350.0
            val description = getString("description") ?: ""
            val roadId = getString("roadId")
            val bridgeId = getString("bridgeId")
            val active = getBoolean("active") ?: true
            val createdAt = getLong("createdAt") ?: System.currentTimeMillis()
            val updatedAt = getLong("updatedAt") ?: System.currentTimeMillis()
            val source = getString("source") ?: "ADMIN_BACKEND"

            Hazard(
                id = id,
                name = name,
                type = runCatching { HazardType.valueOf(typeStr.uppercase()) }.getOrDefault(HazardType.BRIDGE_DAMAGE),
                severity = runCatching { HazardSeverity.valueOf(severityStr.uppercase()) }.getOrDefault(HazardSeverity.CRITICAL),
                status = runCatching { HazardStatus.valueOf(statusStr.uppercase()) }.getOrDefault(HazardStatus.BLOCKED),
                latitude = lat,
                longitude = lng,
                radiusMeters = radius,
                description = description,
                roadId = roadId,
                bridgeId = bridgeId,
                createdAt = createdAt,
                updatedAt = updatedAt,
                active = active,
                source = source
            )
        }.getOrNull()
    }
}
