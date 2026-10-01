package com.example.data.remote

import com.example.domain.model.Hazard
import com.example.domain.model.HazardSeverity
import com.example.domain.model.HazardStatus
import com.example.domain.model.HazardType
import com.example.domain.model.Journey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Bridges the user's personal Firebase Console project (`routepilot-2196a`, `(default)` database)
 * via the Firebase Authentication REST API (`identitytoolkit.googleapis.com`) and
 * Firestore REST API (`firestore.googleapis.com`) so that:
 * 1. Every user who signs up or logs in (via Email/Password OR Google Sign-In) is created and
 *    updated in **Firebase Console -> Authentication -> Users** (with email, displayName, and UID)
 *    AND in **Firestore Database -> `users/{uid}`**.
 * 2. Existing active sessions are automatically synced to Firebase Authentication on app launch.
 * 3. Any hazard toggled or added in the `routepilot-2196a` Firebase Console is streamed live into the app.
 */
object UserConsoleFirestoreBridge {

    private const val PROJECT_ID = "routepilot-2196a"
    private const val API_KEY = "AIzaSyDXVhjL0DtgXNP53ulXOfdWg2ncGg5Q5NM"
    private const val BASE_URL =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents"
    private const val AUTH_BASE_URL =
        "https://identitytoolkit.googleapis.com/v1"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var collectionsSeeded = false

    data class ConsoleAuthResult(
        val uid: String,
        val name: String,
        val email: String,
        val idToken: String? = null,
        val registeredInFirebaseAuth: Boolean = false
    )

    private fun googleBridgePassword(email: String): String {
        val clean = email.trim().lowercase(Locale.ROOT)
        return "RpGoogle#${sha256("routepilot_google_bridge_$clean").take(16)}"
    }

    /**
     * Registers a user with Email & Password in Firebase Authentication (`accounts:signUp`)
     * and stores their profile in Firestore `users/{uid}`.
     */
    suspend fun registerEmailPasswordUser(
        name: String,
        email: String,
        password: String
    ): Result<ConsoleAuthResult> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanName = name.trim().ifBlank {
            cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        }
        val passwordHash = sha256(password)
        val deterministicUid = "usr_${cleanEmail.hashCode().toUInt()}"

        // 1. Call Firebase Auth REST API accounts:signUp with displayName included
        val signUpBody = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
            put("displayName", cleanName)
            put("returnSecureToken", true)
        }
        val (statusCode, respText) = postAuthRequest("accounts:signUp", signUpBody)
        if (statusCode in 200..299 && !respText.isNullOrBlank()) {
            val json = runCatching { JSONObject(respText) }.getOrNull()
            val idToken = json?.optString("idToken")?.takeIf { it.isNotBlank() }
            val localId = json?.optString("localId")?.takeIf { it.isNotBlank() } ?: deterministicUid

            if (idToken != null) {
                updateFirebaseAuthProfile(idToken, cleanName)
            }

            syncUserProfileWithPasswordHash(
                uid = localId,
                name = cleanName,
                email = cleanEmail,
                provider = "password",
                passwordHash = passwordHash
            )
            return@withContext Result.success(
                ConsoleAuthResult(
                    uid = localId,
                    name = cleanName,
                    email = cleanEmail,
                    idToken = idToken,
                    registeredInFirebaseAuth = true
                )
            )
        }

        val errorMsg = parseFirebaseAuthError(respText)
        if (errorMsg == "EMAIL_EXISTS") {
            // If this email was previously bridged via Google Sign-In or same password, try signing in & updating profile
            val signInAttempt = trySignInAndUpdateFirebaseAuth(cleanEmail, password, cleanName)
                ?: trySignInAndUpdateFirebaseAuth(cleanEmail, googleBridgePassword(cleanEmail), cleanName, newPassword = password)
            if (signInAttempt != null) {
                syncUserProfileWithPasswordHash(
                    uid = signInAttempt.uid,
                    name = cleanName,
                    email = cleanEmail,
                    provider = "password",
                    passwordHash = passwordHash
                )
                return@withContext Result.success(signInAttempt.copy(name = cleanName))
            }
            return@withContext Result.failure(
                IllegalArgumentException("An account with $cleanEmail already exists. Please Login instead.")
            )
        }

        // Fallback to Firestore if offline or provider not reachable
        syncUserProfileWithPasswordHash(
            uid = deterministicUid,
            name = cleanName,
            email = cleanEmail,
            provider = "password",
            passwordHash = passwordHash
        )
        Result.success(
            ConsoleAuthResult(
                uid = deterministicUid,
                name = cleanName,
                email = cleanEmail,
                idToken = null,
                registeredInFirebaseAuth = false
            )
        )
    }

    /**
     * Signs in a user with Email & Password against Firebase Authentication (`accounts:signInWithPassword`).
     * If the account existed locally/in Firestore before Firebase Auth was enabled, automatically creates
     * the account in Firebase Authentication so it appears in Firebase Console -> Authentication -> Users.
     */
    suspend fun loginEmailPasswordUser(
        email: String,
        password: String,
        preferredName: String? = null
    ): Result<ConsoleAuthResult> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val passwordHash = sha256(password)
        val deterministicUid = "usr_${cleanEmail.hashCode().toUInt()}"

        // 1. Try Firebase Auth REST API signInWithPassword
        val signInBody = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
            put("returnSecureToken", true)
        }
        val (statusCode, respText) = postAuthRequest("accounts:signInWithPassword", signInBody)
        if (statusCode in 200..299 && !respText.isNullOrBlank()) {
            val json = runCatching { JSONObject(respText) }.getOrNull()
            val idToken = json?.optString("idToken")?.takeIf { it.isNotBlank() }
            val localId = json?.optString("localId")?.takeIf { it.isNotBlank() } ?: deterministicUid
            val displayName = json?.optString("displayName")?.takeIf { it.isNotBlank() }
                ?: preferredName?.takeIf { it.isNotBlank() }
                ?: fetchUserNameFromFirestore(localId, deterministicUid)
                ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

            if (idToken != null && json?.optString("displayName").isNullOrBlank()) {
                updateFirebaseAuthProfile(idToken, displayName)
            }

            syncUserProfileWithPasswordHash(
                uid = localId,
                name = displayName,
                email = cleanEmail,
                provider = "password",
                passwordHash = passwordHash
            )
            return@withContext Result.success(
                ConsoleAuthResult(
                    uid = localId,
                    name = displayName,
                    email = cleanEmail,
                    idToken = idToken,
                    registeredInFirebaseAuth = true
                )
            )
        }

        val authError = parseFirebaseAuthError(respText)
        if (authError == "INVALID_PASSWORD" || authError == "USER_DISABLED") {
            return@withContext Result.failure(
                IllegalArgumentException("Incorrect password for $cleanEmail. Please try again or tap Forgot Password.")
            )
        }

        // 2. Check Firestore `users/{deterministicUid}` or if preferredName was passed from a verified local account
        val existingDoc = getDocumentRaw("users/$deterministicUid")
        val fields = existingDoc?.let { runCatching { JSONObject(it).optJSONObject("fields") }.getOrNull() }
        val storedHash = fields?.let { readString(it, "passwordHash") }
        val storedName = preferredName?.takeIf { it.isNotBlank() }
            ?: fields?.let { readString(it, "name") }
            ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

        if (preferredName != null || (fields != null && (storedHash == null || storedHash == passwordHash))) {
            // Automatically register in Firebase Auth so the user shows up in Firebase Console -> Authentication -> Users
            val signUpAttempt = postAuthRequest(
                "accounts:signUp",
                JSONObject().apply {
                    put("email", cleanEmail)
                    put("password", password)
                    put("displayName", storedName)
                    put("returnSecureToken", true)
                }
            )
            var resolvedUid = deterministicUid
            var idToken: String? = null
            if (signUpAttempt.first in 200..299 && !signUpAttempt.second.isNullOrBlank()) {
                val signUpJson = runCatching { JSONObject(signUpAttempt.second!!) }.getOrNull()
                resolvedUid = signUpJson?.optString("localId")?.takeIf { it.isNotBlank() } ?: deterministicUid
                idToken = signUpJson?.optString("idToken")?.takeIf { it.isNotBlank() }
                if (idToken != null) {
                    updateFirebaseAuthProfile(idToken, storedName)
                }
            }

            syncUserProfileWithPasswordHash(
                uid = resolvedUid,
                name = storedName,
                email = cleanEmail,
                provider = "password",
                passwordHash = passwordHash
            )
            return@withContext Result.success(
                ConsoleAuthResult(
                    uid = resolvedUid,
                    name = storedName,
                    email = cleanEmail,
                    idToken = idToken,
                    registeredInFirebaseAuth = signUpAttempt.first in 200..299
                )
            )
        }

        if (fields != null && storedHash != null && storedHash != passwordHash) {
            return@withContext Result.failure(
                IllegalArgumentException("Incorrect password. Please try again or tap Forgot Password.")
            )
        }

        Result.failure(
            IllegalArgumentException("No account found for $cleanEmail. Please Sign Up or Continue with Google.")
        )
    }

    /**
     * Signs in a Google user and guarantees the user is ALSO registered/visible in
     * `routepilot-2196a` **Firebase Console -> Authentication -> Users** (with email, displayName, and UID)
     * as well as **Firestore Database -> `users/{uid}`**.
     */
    suspend fun signInWithGoogleIdToken(
        idToken: String?,
        email: String,
        name: String,
        fallbackUid: String
    ): ConsoleAuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanName = name.trim().ifBlank {
            cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        }
        var resolvedUid = fallbackUid
        var resolvedToken = idToken
        var registeredInAuth = false

        // 1. First try exchanging Google idToken via accounts:signInWithIdp
        if (!idToken.isNullOrBlank()) {
            val idpBody = JSONObject().apply {
                put("postBody", "id_token=$idToken&providerId=google.com")
                put("requestUri", "http://localhost")
                put("returnIdpCredential", true)
                put("returnSecureToken", true)
            }
            val (status, resp) = postAuthRequest("accounts:signInWithIdp", idpBody)
            if (status in 200..299 && !resp.isNullOrBlank()) {
                val json = runCatching { JSONObject(resp) }.getOrNull()
                val localId = json?.optString("localId")?.takeIf { it.isNotBlank() }
                val authIdToken = json?.optString("idToken")?.takeIf { it.isNotBlank() }
                if (localId != null) {
                    resolvedUid = localId
                    registeredInAuth = true
                }
                if (authIdToken != null) {
                    resolvedToken = authIdToken
                    updateFirebaseAuthProfile(authIdToken, cleanName)
                }
            }
        }

        // 2. If signInWithIdp didn't register in `routepilot-2196a` (e.g. client_id audience belongs to AI Studio project),
        // ensure the Google user is registered in `routepilot-2196a` Firebase Authentication so they appear in Console -> Authentication -> Users!
        if (!registeredInAuth && cleanEmail.contains("@")) {
            val bridgePwd = googleBridgePassword(cleanEmail)
            val signUpBody = JSONObject().apply {
                put("email", cleanEmail)
                put("password", bridgePwd)
                put("displayName", cleanName)
                put("returnSecureToken", true)
            }
            val (signUpStatus, signUpResp) = postAuthRequest("accounts:signUp", signUpBody)
            if (signUpStatus in 200..299 && !signUpResp.isNullOrBlank()) {
                val json = runCatching { JSONObject(signUpResp) }.getOrNull()
                val localId = json?.optString("localId")?.takeIf { it.isNotBlank() }
                val authIdToken = json?.optString("idToken")?.takeIf { it.isNotBlank() }
                if (localId != null) {
                    resolvedUid = localId
                    registeredInAuth = true
                }
                if (authIdToken != null) {
                    resolvedToken = authIdToken
                    updateFirebaseAuthProfile(authIdToken, cleanName)
                }
            } else {
                // User already exists in Firebase Auth; try signing in with bridge password or looking up their UID from Firestore
                val existingAuth = trySignInAndUpdateFirebaseAuth(cleanEmail, bridgePwd, cleanName)
                if (existingAuth != null) {
                    resolvedUid = existingAuth.uid
                    resolvedToken = existingAuth.idToken ?: resolvedToken
                    registeredInAuth = true
                } else {
                    val existingFirestoreUid = findExistingUidByEmailInFirestore(cleanEmail)
                    if (!existingFirestoreUid.isNullOrBlank()) {
                        resolvedUid = existingFirestoreUid
                    }
                }
            }
        }

        syncUserProfile(
            uid = resolvedUid,
            name = cleanName,
            email = cleanEmail,
            provider = "google"
        )

        ConsoleAuthResult(
            uid = resolvedUid,
            name = cleanName,
            email = cleanEmail,
            idToken = resolvedToken,
            registeredInFirebaseAuth = registeredInAuth
        )
    }

    /**
     * Ensures an already-logged-in user session is synced to both Firebase Console -> Authentication -> Users
     * and Firestore Database -> `users/{uid}` on app launch.
     */
    suspend fun ensureSessionSyncedToConsole(
        uid: String,
        name: String,
        email: String,
        password: String?
    ): ConsoleAuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanName = name.trim().ifBlank {
            cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        }
        val effectivePassword = if (!password.isNullOrBlank() && password != "google_oauth_user" && password.length >= 6) {
            password
        } else {
            googleBridgePassword(cleanEmail)
        }

        // Try signing in first
        val signIn = trySignInAndUpdateFirebaseAuth(cleanEmail, effectivePassword, cleanName)
        if (signIn != null) {
            syncUserProfile(
                uid = signIn.uid,
                name = cleanName,
                email = cleanEmail,
                provider = if (password == "google_oauth_user" || password.isNullOrBlank()) "google" else "password"
            )
            return@withContext signIn
        }

        // Otherwise try creating the account in Firebase Auth so it is visible in Authentication -> Users
        val signUpBody = JSONObject().apply {
            put("email", cleanEmail)
            put("password", effectivePassword)
            put("displayName", cleanName)
            put("returnSecureToken", true)
        }
        val (status, resp) = postAuthRequest("accounts:signUp", signUpBody)
        var resolvedUid = uid
        if (status in 200..299 && !resp.isNullOrBlank()) {
            val json = runCatching { JSONObject(resp) }.getOrNull()
            val localId = json?.optString("localId")?.takeIf { it.isNotBlank() }
            val idToken = json?.optString("idToken")?.takeIf { it.isNotBlank() }
            if (localId != null) {
                resolvedUid = localId
            }
            if (idToken != null) {
                updateFirebaseAuthProfile(idToken, cleanName)
            }
        } else {
            val firestoreUid = findExistingUidByEmailInFirestore(cleanEmail)
            if (!firestoreUid.isNullOrBlank()) {
                resolvedUid = firestoreUid
            }
        }

        syncUserProfile(
            uid = resolvedUid,
            name = cleanName,
            email = cleanEmail,
            provider = if (password == "google_oauth_user" || password.isNullOrBlank()) "google" else "password"
        )

        ConsoleAuthResult(
            uid = resolvedUid,
            name = cleanName,
            email = cleanEmail,
            idToken = null,
            registeredInFirebaseAuth = status in 200..299
        )
    }

    private fun trySignInAndUpdateFirebaseAuth(
        email: String,
        password: String,
        displayName: String,
        newPassword: String? = null
    ): ConsoleAuthResult? {
        val signInBody = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("returnSecureToken", true)
        }
        val (status, resp) = postAuthRequest("accounts:signInWithPassword", signInBody)
        if (status !in 200..299 || resp.isNullOrBlank()) return null
        val json = runCatching { JSONObject(resp) }.getOrNull() ?: return null
        val localId = json.optString("localId").takeIf { it.isNotBlank() } ?: return null
        val idToken = json.optString("idToken").takeIf { it.isNotBlank() }

        if (idToken != null) {
            val updateBody = JSONObject().apply {
                put("idToken", idToken)
                put("displayName", displayName)
                if (!newPassword.isNullOrBlank() && newPassword.length >= 6) {
                    put("password", newPassword)
                }
                put("returnSecureToken", true)
            }
            postAuthRequest("accounts:update", updateBody)
        }
        return ConsoleAuthResult(
            uid = localId,
            name = displayName,
            email = email,
            idToken = idToken,
            registeredInFirebaseAuth = true
        )
    }

    private fun updateFirebaseAuthProfile(idToken: String, displayName: String) {
        if (displayName.isBlank()) return
        val updateBody = JSONObject().apply {
            put("idToken", idToken)
            put("displayName", displayName)
            put("returnSecureToken", false)
        }
        postAuthRequest("accounts:update", updateBody)
    }

    private fun findExistingUidByEmailInFirestore(email: String): String? {
        val raw = getDocumentRaw("users") ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val docs = root.optJSONArray("documents") ?: return null
        for (i in 0 until docs.length()) {
            val docObj = docs.optJSONObject(i) ?: continue
            val fields = docObj.optJSONObject("fields") ?: continue
            val docEmail = readString(fields, "email") ?: continue
            if (docEmail.equals(email, ignoreCase = true)) {
                val docUid = readString(fields, "uid")
                if (!docUid.isNullOrBlank()) return docUid
                val fullName = docObj.optString("name", "")
                val id = fullName.substringAfterLast("/")
                if (id.isNotBlank()) return id
            }
        }
        return null
    }

    /**
     * Sends a password reset email via Firebase Auth (`accounts:sendOobCode`) and/or updates
     * the password hash in Firestore `users/{uid}` if a new password is provided.
     */
    suspend fun resetUserPassword(
        email: String,
        newPassword: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val deterministicUid = findExistingUidByEmailInFirestore(cleanEmail)
            ?: "usr_${cleanEmail.hashCode().toUInt()}"
        var handled = false

        if (!newPassword.isNullOrBlank() && newPassword.length >= 6) {
            val existingDoc = getDocumentRaw("users/$deterministicUid")
            val fields = existingDoc?.let {
                runCatching { JSONObject(it).optJSONObject("fields") }.getOrNull()
            }
            val existingName = fields?.let { readString(it, "name") }
                ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            syncUserProfileWithPasswordHash(
                uid = deterministicUid,
                name = existingName,
                email = cleanEmail,
                provider = "password",
                passwordHash = sha256(newPassword)
            )
            handled = true
        }

        val resetBody = JSONObject().apply {
            put("requestType", "PASSWORD_RESET")
            put("email", cleanEmail)
        }
        val (status, _) = postAuthRequest("accounts:sendOobCode", resetBody)
        if (status in 200..299) {
            handled = true
        }
        handled
    }

    private fun fetchUserNameFromFirestore(vararg candidateUids: String): String? {
        for (uid in candidateUids) {
            if (uid.isBlank()) continue
            val raw = getDocumentRaw("users/$uid") ?: continue
            val fields = runCatching { JSONObject(raw).optJSONObject("fields") }.getOrNull() ?: continue
            val name = readString(fields, "name")
            if (!name.isNullOrBlank()) return name
        }
        return null
    }

    suspend fun seedRequiredCollectionsIfNeeded(
        userId: String = "IyB8Xm4B6MRYy01gu4M5P4JmCa73",
        userName: String = "Ratan Kishan",
        userEmail: String = "ratankishan2525@gmail.com"
    ) = withContext(Dispatchers.IO) {
        if (collectionsSeeded) return@withContext
        collectionsSeeded = true
        val nowIso = Instant.now().toString()

        // Ensure `users/{userId}` exists (never auto-create hazards; hazards are generated exclusively by the Admin Panel)
        val existingUser = getDocumentRaw("users/$userId")
        if (existingUser == null) {
            val userFields = JSONObject().apply {
                put("id", stringVal(userId))
                put("uid", stringVal(userId))
                put("name", stringVal(userName.ifBlank { "RoutePilot User" }))
                put("displayName", stringVal(userName.ifBlank { "RoutePilot User" }))
                put("email", stringVal(userEmail.ifBlank { "ratankishan2525@gmail.com" }))
                put("authProvider", stringVal("google"))
                put("role", stringVal("user"))
                put("createdAt", timestampVal(nowIso))
                put("updatedAt", timestampVal(nowIso))
            }
            patchDocument("users/$userId", userFields)
        }
    }

    suspend fun syncUserProfile(
        uid: String,
        name: String,
        email: String,
        provider: String
    ) = withContext(Dispatchers.IO) {
        val nowIso = Instant.now().toString()
        val resolvedName = name.ifBlank { "Google User" }
        val fields = JSONObject().apply {
            put("id", stringVal(uid))
            put("uid", stringVal(uid))
            put("name", stringVal(resolvedName))
            put("displayName", stringVal(resolvedName))
            put("email", stringVal(email))
            put("authProvider", stringVal(provider))
            put("role", stringVal("user"))
            put("createdAt", timestampVal(nowIso))
            put("updatedAt", timestampVal(nowIso))
        }
        patchDocument("users/$uid", fields)
    }

    private suspend fun syncUserProfileWithPasswordHash(
        uid: String,
        name: String,
        email: String,
        provider: String,
        passwordHash: String
    ) = withContext(Dispatchers.IO) {
        val nowIso = Instant.now().toString()
        val resolvedName = name.ifBlank { "RoutePilot User" }
        val fields = JSONObject().apply {
            put("id", stringVal(uid))
            put("uid", stringVal(uid))
            put("name", stringVal(resolvedName))
            put("displayName", stringVal(resolvedName))
            put("email", stringVal(email))
            put("authProvider", stringVal(provider))
            put("passwordHash", stringVal(passwordHash))
            put("role", stringVal("user"))
            put("createdAt", timestampVal(nowIso))
            put("updatedAt", timestampVal(nowIso))
        }
        patchDocument("users/$uid", fields)
    }

    suspend fun syncJourney(journey: Journey) = withContext(Dispatchers.IO) {
        val nowIso = Instant.now().toString()
        val fields = JSONObject().apply {
            put("userId", stringVal(journey.userId.ifBlank { "IyB8Xm4B6MRYy01gu4M5P4JmCa73" }))
            put("source", stringVal(journey.sourceName.ifBlank { "Current Location" }))
            put("destination", stringVal(journey.destinationName.ifBlank { "Destination" }))
            put("destinationAddress", stringVal(journey.destinationAddress))
            put("distance", doubleVal(journey.distanceKm))
            put("duration", intVal(journey.durationMinutes.toLong()))
            put("startedAt", intVal(journey.startedAt))
            put("completedAt", intVal(journey.completedAt))
            put("status", stringVal(journey.status.ifBlank { "COMPLETED" }))
            put("hazardsAvoidedCount", intVal(journey.hazardsAvoidedCount.toLong()))
            put("createdAt", timestampVal(nowIso))
            put("updatedAt", timestampVal(nowIso))
        }
        patchDocument("journeys/${journey.id}", fields)
    }

    suspend fun syncLiveDriverTelemetry(
        userId: String,
        userName: String,
        userEmail: String,
        currentLat: Double,
        currentLng: Double,
        destinationName: String,
        destinationAddress: String,
        destLat: Double,
        destLng: Double,
        workflowState: String,
        isDivertedForSafety: Boolean,
        routePointsJson: String
    ) = withContext(Dispatchers.IO) {
        val nowIso = Instant.now().toString()
        val fields = JSONObject().apply {
            put("userId", stringVal(userId.ifBlank { "IyB8Xm4B6MRYy01gu4M5P4JmCa73" }))
            put("userName", stringVal(userName.ifBlank { "RoutePilot User" }))
            put("userEmail", stringVal(userEmail.ifBlank { "ratankishan2525@gmail.com" }))
            put("currentLat", doubleVal(currentLat))
            put("currentLng", doubleVal(currentLng))
            put("destinationName", stringVal(destinationName))
            put("destinationAddress", stringVal(destinationAddress))
            put("destLat", doubleVal(destLat))
            put("destLng", doubleVal(destLng))
            put("workflowState", stringVal(workflowState))
            put("isDivertedForSafety", boolVal(isDivertedForSafety))
            put("routePointsJson", stringVal(routePointsJson))
            put("updatedAt", timestampVal(nowIso))
        }
        patchDocument("telemetry/active_driver", fields)
    }

    fun observeActiveConsoleHazards(): Flow<List<Hazard>> = flow {
        while (true) {
            runCatching {
                fetchActiveHazardsFromConsole()
            }.onSuccess { list ->
                emit(list)
            }
            delay(2000L)
        }
    }

    private suspend fun fetchActiveHazardsFromConsole(): List<Hazard> = withContext(Dispatchers.IO) {
        val raw = getDocumentRaw("hazards") ?: return@withContext emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext emptyList()
        val docs = root.optJSONArray("documents") ?: return@withContext emptyList()
        val result = mutableListOf<Hazard>()
        for (i in 0 until docs.length()) {
            val docObj = docs.optJSONObject(i) ?: continue
            val fullName = docObj.optString("name", "")
            val id = fullName.substringAfterLast("/").ifBlank { "hazard_$i" }
            val fields = docObj.optJSONObject("fields") ?: continue

            val statusStr = (readString(fields, "status") ?: "BLOCKED").trim()
            val rawStatus = parseAdminHazardStatus(statusStr)

            val hasActiveField = fields.has("active")
            val activeFlag = fields.optJSONObject("active")?.optBoolean("booleanValue", false) ?: false
            val isEffectiveActive = if (hasActiveField) {
                activeFlag
            } else {
                rawStatus != HazardStatus.CLEARED
            }
            if (!isEffectiveActive) continue

            // If Admin toggled active=true on a previously cleared record, treat status as BLOCKED
            val effectiveStatus = if (rawStatus == HazardStatus.CLEARED) {
                HazardStatus.BLOCKED
            } else {
                rawStatus
            }

            val locationLabel = readString(fields, "location")
            val roadNameLabel = readString(fields, "roadName")
            val bridgeId = readString(fields, "bridgeId")
            val name = readString(fields, "name")
                ?: listOfNotNull(locationLabel, roadNameLabel).joinToString(" - ").takeIf { it.isNotBlank() }
                ?: bridgeId?.let { "Bridge $it" }
                ?: "Road / Bridge Hazard"

            val typeStr = readString(fields, "type") ?: "BRIDGE_DAMAGE"
            val severityStr = readString(fields, "severity") ?: "CRITICAL"
            val parsedSeverity = parseAdminHazardSeverity(severityStr).let { sev ->
                if (rawStatus == HazardStatus.CLEARED && sev == HazardSeverity.LOW) HazardSeverity.HIGH else sev
            }
            val lat = readDouble(fields, "latitude") ?: readDouble(fields, "lat") ?: 25.4580
            val lng = readDouble(fields, "longitude") ?: readDouble(fields, "lng") ?: 78.5820
            val radius = readDouble(fields, "radius") ?: 350.0
            val rawDesc = readString(fields, "description") ?: ""
            val description = if (rawDesc.startsWith("CLEARED:", ignoreCase = true) || rawDesc.isBlank()) {
                "Hazard reported by Admin on $name. Road section unsafe — please find an alternative safer route."
            } else {
                rawDesc
            }
            val roadId = readString(fields, "roadId") ?: roadNameLabel
            val source = readString(fields, "source") ?: "ADMIN_BACKEND"

            result.add(
                Hazard(
                    id = id,
                    name = name,
                    type = parseAdminHazardType(typeStr),
                    severity = parsedSeverity,
                    status = effectiveStatus,
                    latitude = lat,
                    longitude = lng,
                    radiusMeters = radius,
                    description = description,
                    roadId = roadId,
                    bridgeId = bridgeId,
                    active = true,
                    source = source
                )
            )
        }
        result
    }

    private fun parseAdminHazardType(raw: String): HazardType {
        val u = raw.trim().uppercase(Locale.ROOT).replace(" ", "_").replace("-", "_")
        runCatching { return HazardType.valueOf(u) }
        return when {
            u.contains("VIBRATION") -> HazardType.HIGH_VIBRATION
            u.contains("TILT") -> HazardType.ABNORMAL_TILT
            u.contains("STRAIN") -> HazardType.HIGH_STRAIN
            u.contains("DISPLACEMENT") -> HazardType.HIGH_DISPLACEMENT
            u.contains("WATER") || u.contains("FLOOD") -> HazardType.FLOOD
            u.contains("ACCIDENT") || u.contains("CRASH") -> HazardType.ACCIDENT
            u.contains("CONSTRUCT") || u.contains("WORK") -> HazardType.CONSTRUCTION
            u.contains("RESTRICT") -> HazardType.RESTRICTED_ROAD
            u.contains("ROAD") || u.contains("BLOCK") -> HazardType.ROAD_BLOCK
            u.contains("BRIDGE") || u.contains("STRUCTUR") || u.contains("DAMAGE") -> HazardType.BRIDGE_DAMAGE
            else -> HazardType.BRIDGE_DAMAGE
        }
    }

    private fun parseAdminHazardSeverity(raw: String): HazardSeverity {
        val u = raw.trim().uppercase(Locale.ROOT)
        runCatching { return HazardSeverity.valueOf(u) }
        return when {
            u.contains("CRIT") || u.contains("SEVERE") || u.contains("EXTREME") -> HazardSeverity.CRITICAL
            u.contains("HIGH") || u.contains("MAJOR") -> HazardSeverity.HIGH
            u.contains("MED") || u.contains("MOD") -> HazardSeverity.MEDIUM
            u.contains("LOW") || u.contains("MIN") -> HazardSeverity.LOW
            else -> HazardSeverity.HIGH
        }
    }

    private fun parseAdminHazardStatus(raw: String): HazardStatus {
        val u = raw.trim().uppercase(Locale.ROOT).replace(" ", "_").replace("-", "_")
        runCatching { return HazardStatus.valueOf(u) }
        return when {
            u.contains("CLEAR") || u.contains("OPEN") || u.contains("SAFE") || u.contains("NORMAL") || u.contains("RESOLVED") -> HazardStatus.CLEARED
            u.contains("PARTIAL") || u.contains("RESTRICT") || u.contains("SINGLE") -> HazardStatus.PARTIALLY_BLOCKED
            u.contains("WARN") || u.contains("CAUTION") || u.contains("ALERT") -> HazardStatus.WARNING
            u.contains("BLOCK") || u.contains("CLOSE") || u.contains("DANGER") || u.contains("UNSAFE") -> HazardStatus.BLOCKED
            else -> HazardStatus.BLOCKED
        }
    }

    private fun postAuthRequest(endpoint: String, body: JSONObject): Pair<Int, String?> {
        return runCatching {
            val req = Request.Builder()
                .url("$AUTH_BASE_URL/$endpoint?key=$API_KEY")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                resp.code to resp.body?.string()
            }
        }.getOrDefault(-1 to null)
    }

    private fun parseFirebaseAuthError(respText: String?): String? {
        if (respText.isNullOrBlank()) return null
        return runCatching {
            val errObj = JSONObject(respText).optJSONObject("error")
            errObj?.optString("message")?.substringBefore(" ")?.trim()
        }.getOrNull()
    }

    private fun getDocumentRaw(relativePath: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/$relativePath?key=$API_KEY")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private fun patchDocument(relativePath: String, fields: JSONObject): Boolean {
        return runCatching {
            val bodyJson = JSONObject().apply {
                put("fields", fields)
            }
            val req = Request.Builder()
                .url("$BASE_URL/$relativePath?key=$API_KEY")
                .patch(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        }.getOrDefault(false)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun stringVal(value: String): JSONObject = JSONObject().put("stringValue", value)
    private fun doubleVal(value: Double): JSONObject = JSONObject().put("doubleValue", value)
    private fun intVal(value: Long): JSONObject = JSONObject().put("integerValue", value.toString())
    private fun boolVal(value: Boolean): JSONObject = JSONObject().put("booleanValue", value)
    private fun timestampVal(isoString: String): JSONObject = JSONObject().put("timestampValue", isoString)

    private fun readString(fields: JSONObject, key: String): String? =
        fields.optJSONObject(key)?.optString("stringValue")?.takeIf { it.isNotBlank() }

    private fun readDouble(fields: JSONObject, key: String): Double? {
        val obj = fields.optJSONObject(key) ?: return null
        if (obj.has("doubleValue")) return obj.optDouble("doubleValue")
        if (obj.has("integerValue")) return obj.optString("integerValue").toDoubleOrNull()
        return null
    }
}
