package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.FirestoreHazardDataSource
import com.example.domain.model.Journey
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class RoutePilotRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun saveAndReadUserProfile_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val authRepo = AuthRepositoryImpl(context, firestore, auth)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.saveUserToFirestore(
                uid = aliceUid,
                name = "Alice RoutePilot",
                email = ALICE_EMAIL,
                provider = "google"
            )
        }
        assertTrue(saveResult.isSuccess)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.getUserProfileById(aliceUid)
        }
        assertTrue(profileResult.isSuccess)
        val user = profileResult.getOrThrow()
        assertEquals(aliceUid, user?.id)
        assertEquals("Alice RoutePilot", user?.name)
        assertEquals(ALICE_EMAIL, user?.email)
    }

    @Test
    fun getUserProfileById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = AuthRepositoryImpl(context, firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveUserToFirestore(
                uid = aliceUid,
                name = "Alice RoutePilot",
                email = ALICE_EMAIL,
                provider = "google"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = AuthRepositoryImpl(context, firestore, auth)
        val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getUserProfileById(aliceUid)
        }
        assertTrue(crossResult.isFailure)
        val ex = crossResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun saveAndQueryJourneys_authenticatedOwner_succeedsAndIsolatesUsers() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val dataSource = FirestoreHazardDataSource(context, firestore, auth)
        val journeyId = "j_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val journey = Journey(
            id = journeyId,
            userId = aliceUid,
            sourceName = "City Center",
            destinationName = "District Hospital",
            destinationAddress = "Sector 4",
            distanceKm = 12.4,
            durationMinutes = 22,
            startedAt = 1700000000000L,
            completedAt = 1700001320000L,
            status = "COMPLETED",
            hazardsAvoidedCount = 1
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.saveDriverJourney(journey)
        }
        assertTrue(createResult.isSuccess)

        val listResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            dataSource.getUserJourneys()
        }
        assertTrue(listResult.isSuccess)
        assertTrue(listResult.getOrThrow().any { it.id == journeyId })

        // Cross-user read by Bob must fail with PERMISSION_DENIED
        signInTestUser(BOB_EMAIL)
        val bobSource = FirestoreHazardDataSource(context, firestore, auth)
        val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobSource.getJourneyById(journeyId)
        }
        assertTrue(bobReadResult.isFailure)
        val ex = bobReadResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeActiveHazards_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val dataSource = FirestoreHazardDataSource(context, firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                dataSource.observeActiveHazardsRealtime().first()
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for unauthenticated caller")
        } catch (t: Throwable) {
            val firestoreEx = generateSequence(t) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.routepilot@test.com"
        const val BOB_EMAIL = "bob.routepilot@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
