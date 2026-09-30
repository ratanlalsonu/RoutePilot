package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.HazardStatus
import com.example.domain.model.HazardType
import com.example.domain.model.Journey
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Real-Time Firebase Firestore Data Source for RoutePilot APK.
 *
 * Uses the provisioned custom database ID (`R.string.firestore_database_id`),
 * enforces authenticated user access (`requireUserId()`), writes server timestamps
 * (`FieldValue.serverTimestamp()`), and routes all errors through `handleFirestoreError`.
 */
class FirestoreHazardDataSource(
    private val context: Context,
    private val providedFirestore: FirebaseFirestore? = null,
    private val providedAuth: FirebaseAuth? = null
) {
    private val databaseId: String by lazy {
        context.getString(R.string.firestore_database_id)
    }

    private val firestore: FirebaseFirestore? by lazy {
        providedFirestore ?: runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance(databaseId)
            } else {
                null
            }
        }.getOrNull()
    }

    private val auth: FirebaseAuth? by lazy {
        providedAuth ?: runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        }.getOrNull()
    }

    val isFirestoreConfigured: Boolean
        get() = firestore != null

    private fun requireUserId(): String {
        return auth?.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    /**
     * Real-time Firestore listener on `hazards` collection where `active == true`.
     */
    fun observeActiveHazardsRealtime(): Flow<List<Hazard>> = flow {
        val db = firestore
        if (db == null) {
            emit(emptyList())
            return@flow
        }
        val path = "hazards"
        emitAll(
            db.collection(path)
                .whereEqualTo("active", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toHazardOrNull()
                    }
                }
                .catch { error ->
                    val unwrapped = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                        ?: (error as? Exception)
                    if (unwrapped != null) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                        throw unwrapped
                    }
                    throw error
                }
        )
    }

    suspend fun getHazardById(hazardId: String): Hazard? {
        val db = firestore ?: return null
        val path = "hazards/$hazardId"
        return try {
            val doc = db.collection("hazards").document(hazardId).get().await()
            if (doc.exists()) doc.toHazardOrNull() else null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun saveDriverJourney(journey: Journey): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore not initialized"))
        val uid = requireUserId()
        val path = "journeys/${journey.id}"
        return try {
            val data = mapOf(
                "userId" to uid,
                "source" to journey.sourceName.ifBlank { "Current Location" },
                "destination" to journey.destinationName.ifBlank { "Destination" },
                "destinationAddress" to journey.destinationAddress,
                "distance" to journey.distanceKm.coerceAtLeast(0.0),
                "duration" to journey.durationMinutes.coerceAtLeast(0),
                "startedAt" to journey.startedAt.coerceAtLeast(0L),
                "completedAt" to journey.completedAt.coerceAtLeast(0L),
                "status" to journey.status.ifBlank { "COMPLETED" },
                "hazardsAvoidedCount" to journey.hazardsAvoidedCount.coerceAtLeast(0),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("journeys").document(journey.id).set(data).await()
            Result.success(journey.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun getUserJourneys(): Result<List<Journey>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore not initialized"))
        val uid = requireUserId()
        val path = "journeys"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val journeys = snapshot.documents.mapNotNull { doc ->
                doc.toJourneyOrNull()
            }
            Result.success(journeys)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getJourneyById(journeyId: String): Result<Journey?> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore not initialized"))
        val path = "journeys/$journeyId"
        return try {
            val doc = db.collection("journeys").document(journeyId).get().await()
            Result.success(if (doc.exists()) doc.toJourneyOrNull() else null)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    private fun DocumentSnapshot.toJourneyOrNull(): Journey? {
        return runCatching {
            Journey(
                id = id,
                userId = getString("userId") ?: "",
                sourceName = getString("source") ?: "",
                destinationName = getString("destination") ?: "",
                destinationAddress = getString("destinationAddress") ?: "",
                distanceKm = getDouble("distance") ?: 0.0,
                durationMinutes = (getLong("duration") ?: 0L).toInt(),
                startedAt = getLong("startedAt") ?: 0L,
                completedAt = getLong("completedAt") ?: 0L,
                status = getString("status") ?: "COMPLETED",
                hazardsAvoidedCount = (getLong("hazardsAvoidedCount") ?: 0L).toInt()
            )
        }.getOrNull()
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
            val createdAtTs = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val updatedAtTs = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val createdAt = createdAtTs?.toDate()?.time ?: (getLong("createdAt") ?: System.currentTimeMillis())
            val updatedAt = updatedAtTs?.toDate()?.time ?: (getLong("updatedAt") ?: System.currentTimeMillis())
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
