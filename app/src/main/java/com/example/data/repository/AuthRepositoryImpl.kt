package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.remote.OperationType
import com.example.data.remote.UserConsoleFirestoreBridge
import com.example.data.remote.handleFirestoreError
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Locale

class AuthRepositoryImpl(
    private val context: Context,
    private val providedFirestore: FirebaseFirestore? = null,
    private val providedAuth: FirebaseAuth? = null
) : AuthRepository {

    private val accountPrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val databaseId: String by lazy {
        context.getString(R.string.firestore_database_id)
    }

    private val firebaseAuth: FirebaseAuth? by lazy {
        providedAuth ?: runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        }.getOrNull()
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

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = _currentUser.asStateFlow()

    init {
        purgeLegacyDummyAccounts()
        restoreSavedSession()
    }

    private fun requireUserId(): String {
        return firebaseAuth?.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun purgeLegacyDummyAccounts() {
        val dummyEmails = listOf(
            "user@routepilot.in",
            "driver@routepilot.in",
            "user.routepilot@gmail.com",
            "kishan.kumar@gmail.com"
        )
        val activeEmail = accountPrefs.getString(KEY_ACTIVE_USER_EMAIL, null)
        val editor = accountPrefs.edit()
        dummyEmails.forEach { dummy ->
            editor.remove(accountKey(dummy))
        }
        if (activeEmail != null && dummyEmails.any { it.equals(activeEmail, ignoreCase = true) }) {
            editor.remove(KEY_ACTIVE_USER_EMAIL)
        }
        editor.apply()
    }

    private fun restoreSavedSession() {
        val fbUser = firebaseAuth?.currentUser
        if (fbUser != null) {
            val email = fbUser.email.orEmpty().trim()
            val localRecord = if (email.isNotBlank()) getLocalAccount(email) else null
            val resolvedName = fbUser.displayName?.takeIf { it.isNotBlank() }
                ?: localRecord?.optString("name")?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "Google User" }
            _currentUser.value = User(
                id = fbUser.uid,
                name = resolvedName.trim(),
                email = email
            )
            return
        }

        val activeEmail = accountPrefs.getString(KEY_ACTIVE_USER_EMAIL, null)
        if (!activeEmail.isNullOrBlank()) {
            val record = getLocalAccount(activeEmail)
            if (record != null) {
                val cleanEmail = record.optString("email", activeEmail).trim()
                _currentUser.value = User(
                    id = record.optString("uid", "usr_${cleanEmail.hashCode().toUInt()}"),
                    name = record.optString("name", cleanEmail.substringBefore("@")).trim(),
                    email = cleanEmail
                )
            }
        }
    }

    override suspend fun loginWithEmail(
        email: String,
        password: String,
        rememberMe: Boolean
    ): Result<User> {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        // 1. Check local account first for fast verification
        val localAccount = getLocalAccount(cleanEmail)
        if (localAccount != null) {
            val storedPassword = localAccount.optString("password", "")
            if (storedPassword == password) {
                val localUid = localAccount.optString("uid", "usr_${cleanEmail.hashCode().toUInt()}")
                val localName = localAccount.optString(
                    "name",
                    cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                )
                val remoteResult = runCatching {
                    UserConsoleFirestoreBridge.loginEmailPasswordUser(
                        email = cleanEmail,
                        password = password,
                        preferredName = localName
                    ).getOrNull()
                }.getOrNull()

                val resolvedUid = remoteResult?.uid?.takeIf { it.isNotBlank() } ?: localUid
                val resolvedName = remoteResult?.name?.takeIf { it.isNotBlank() } ?: localName

                saveLocalAccount(
                    uid = resolvedUid,
                    name = resolvedName,
                    email = cleanEmail,
                    password = password
                )
                setActiveSession(if (rememberMe) cleanEmail else null)
                val user = User(
                    id = resolvedUid,
                    name = resolvedName,
                    email = cleanEmail
                )
                _currentUser.value = user
                return Result.success(user)
            }
        }

        // 2. Verify against Firebase Authentication REST API & Firestore `users` collection
        val remoteAuth = UserConsoleFirestoreBridge.loginEmailPasswordUser(cleanEmail, password)
        return remoteAuth.fold(
            onSuccess = { consoleUser ->
                saveLocalAccount(
                    uid = consoleUser.uid,
                    name = consoleUser.name,
                    email = consoleUser.email,
                    password = password
                )
                setActiveSession(if (rememberMe) cleanEmail else null)
                val user = User(
                    id = consoleUser.uid,
                    name = consoleUser.name,
                    email = consoleUser.email
                )
                _currentUser.value = user
                Result.success(user)
            },
            onFailure = { err ->
                if (localAccount != null) {
                    Result.failure(
                        IllegalArgumentException("Incorrect password. Please try again or tap Forgot Password.")
                    )
                } else {
                    Result.failure(err)
                }
            }
        )
    }

    override suspend fun signUpWithEmail(
        name: String,
        email: String,
        password: String
    ): Result<User> {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanName = name.trim().ifBlank {
            cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        }
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Please enter your full name."))
        }
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        val existingLocal = getLocalAccount(cleanEmail)
        if (existingLocal != null && existingLocal.optString("password") != "google_oauth_user") {
            return Result.failure(
                IllegalArgumentException("An account with $cleanEmail already exists. Please Login instead.")
            )
        }

        val remoteSignUp = UserConsoleFirestoreBridge.registerEmailPasswordUser(
            name = cleanName,
            email = cleanEmail,
            password = password
        )

        return remoteSignUp.fold(
            onSuccess = { consoleUser ->
                saveLocalAccount(
                    uid = consoleUser.uid,
                    name = consoleUser.name,
                    email = consoleUser.email,
                    password = password
                )
                setActiveSession(cleanEmail)
                val newUser = User(
                    id = consoleUser.uid,
                    name = consoleUser.name,
                    email = consoleUser.email
                )
                _currentUser.value = newUser
                Result.success(newUser)
            },
            onFailure = { err ->
                Result.failure(err)
            }
        )
    }

    override suspend fun continueWithGoogle(
        email: String,
        name: String,
        idToken: String?
    ): Result<User> {
        return try {
            val auth = firebaseAuth
            if (!idToken.isNullOrBlank() && auth != null && auth.currentUser == null) {
                runCatching {
                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(authCredential).await()
                }.onFailure { e ->
                    Log.w("AuthRepository", "Firebase signInWithCredential warning: ${e.message}")
                }
            }

            val fbUser = auth?.currentUser
            val resolvedEmail = (fbUser?.email?.takeIf { it.isNotBlank() } ?: email)
                .trim()
                .lowercase(Locale.ROOT)

            if (resolvedEmail.isBlank() || !resolvedEmail.contains("@") || !resolvedEmail.contains(".")) {
                return Result.failure(IllegalArgumentException("Google account email could not be verified."))
            }

            val existingLocal = getLocalAccount(resolvedEmail)
            val resolvedName = (fbUser?.displayName?.takeIf { it.isNotBlank() } ?: name.trim()).ifBlank {
                existingLocal?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: resolvedEmail.substringBefore("@")
                        .split(".", "_", "-")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                        .ifBlank { "Google User" }
            }.trim()

            val fallbackUid = fbUser?.uid?.takeIf { it.isNotBlank() }
                ?: existingLocal?.optString("uid")?.takeIf { it.isNotBlank() }
                ?: "google_usr_${resolvedEmail.hashCode().toUInt()}"

            if (fbUser != null) {
                saveUserToFirestore(
                    uid = fbUser.uid,
                    name = resolvedName,
                    email = resolvedEmail,
                    provider = "google"
                )
            }

            val consoleResult = runCatching {
                UserConsoleFirestoreBridge.signInWithGoogleIdToken(
                    idToken = idToken,
                    email = resolvedEmail,
                    name = resolvedName,
                    fallbackUid = fallbackUid
                )
            }.getOrNull()

            val finalUid = consoleResult?.uid?.takeIf { it.isNotBlank() }
                ?: fbUser?.uid?.takeIf { it.isNotBlank() }
                ?: fallbackUid

            saveLocalAccount(
                uid = finalUid,
                name = resolvedName,
                email = resolvedEmail,
                password = existingLocal?.optString("password")?.takeIf { it.isNotBlank() } ?: "google_oauth_user"
            )
            setActiveSession(resolvedEmail)

            val user = User(
                id = finalUid,
                name = resolvedName,
                email = resolvedEmail
            )
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun syncCurrentSessionToConsoleIfPresent() {
        val current = _currentUser.value ?: return
        val local = getLocalAccount(current.email)
        val storedPwd = local?.optString("password")
        val synced = runCatching {
            UserConsoleFirestoreBridge.ensureSessionSyncedToConsole(
                uid = current.id,
                name = current.name,
                email = current.email,
                password = storedPwd
            )
        }.getOrNull()
        if (synced != null && synced.uid.isNotBlank() && synced.uid != current.id) {
            saveLocalAccount(
                uid = synced.uid,
                name = current.name,
                email = current.email,
                password = storedPwd ?: "google_oauth_user"
            )
            _currentUser.value = current.copy(id = synced.uid)
        }
    }

    override suspend fun sendPasswordReset(
        email: String,
        newPassword: String?
    ): Result<String> {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Please enter your registered email address."))
        }

        if (!newPassword.isNullOrBlank() && newPassword.length < 6) {
            return Result.failure(
                IllegalArgumentException("New password must be at least 6 characters.")
            )
        }

        val existingLocal = getLocalAccount(cleanEmail)
        val resetOnConsole = runCatching {
            UserConsoleFirestoreBridge.resetUserPassword(cleanEmail, newPassword)
        }.getOrDefault(false)

        if (!newPassword.isNullOrBlank()) {
            val uid = existingLocal?.optString("uid") ?: "usr_${cleanEmail.hashCode().toUInt()}"
            val name = existingLocal?.optString("name")
                ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            saveLocalAccount(
                uid = uid,
                name = name,
                email = cleanEmail,
                password = newPassword
            )
            return Result.success("Password for $cleanEmail has been updated. You can now Login with your new password.")
        }

        val auth = firebaseAuth
        if (auth != null) {
            runCatching {
                auth.sendPasswordResetEmail(cleanEmail).await()
            }
        }

        if (existingLocal == null && !resetOnConsole) {
            return Result.failure(
                IllegalArgumentException("No registered account found for $cleanEmail. Please Sign Up first.")
            )
        }

        return Result.success("Password reset link sent to $cleanEmail.")
    }

    override suspend fun logout() {
        runCatching { firebaseAuth?.signOut() }
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
        setActiveSession(null)
        _currentUser.value = null
    }

    suspend fun saveUserToFirestore(
        uid: String = requireUserId(),
        name: String,
        email: String,
        provider: String = "google"
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore not initialized"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updateData = mapOf(
                    "name" to name.take(100).ifBlank { "Google User" },
                    "email" to email.take(150),
                    "authProvider" to provider,
                    "role" to "user",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(updateData, SetOptions.merge()).await()
            } else {
                val createData = mapOf(
                    "uid" to uid,
                    "name" to name.take(100).ifBlank { "Google User" },
                    "email" to email.take(150),
                    "authProvider" to provider,
                    "role" to "user",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createData).await()
            }
            Result.success(uid)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun getUserProfileById(targetUserId: String): Result<User?> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore not initialized"))
        val path = "users/$targetUserId"
        return try {
            val snap = db.collection("users").document(targetUserId).get().await()
            if (snap.exists()) {
                Result.success(snap.toDomainUser())
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(): Flow<User?> = flow {
        val db = firestore ?: throw IllegalStateException("Firestore not initialized")
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toDomainUser() else null
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    private fun DocumentSnapshot.toDomainUser(): User {
        return User(
            id = getString("uid") ?: id,
            name = getString("name") ?: "Google User",
            email = getString("email") ?: ""
        )
    }

    private fun accountKey(email: String): String =
        "$KEY_ACCOUNT_PREFIX${email.trim().lowercase(Locale.ROOT)}"

    private fun saveLocalAccount(
        uid: String,
        name: String,
        email: String,
        password: String
    ) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val json = JSONObject().apply {
            put("uid", uid)
            put("name", name)
            put("email", cleanEmail)
            put("password", password)
            put("updatedAt", System.currentTimeMillis())
        }
        accountPrefs.edit()
            .putString(accountKey(cleanEmail), json.toString())
            .apply()
    }

    private fun getLocalAccount(email: String): JSONObject? {
        val raw = accountPrefs.getString(accountKey(email), null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun setActiveSession(email: String?) {
        accountPrefs.edit().apply {
            if (email.isNullOrBlank()) {
                remove(KEY_ACTIVE_USER_EMAIL)
            } else {
                putString(KEY_ACTIVE_USER_EMAIL, email.trim().lowercase(Locale.ROOT))
            }
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "routepilot_user_accounts"
        private const val KEY_ACCOUNT_PREFIX = "account_"
        private const val KEY_ACTIVE_USER_EMAIL = "active_user_email"
    }
}
