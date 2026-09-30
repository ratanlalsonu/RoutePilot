package com.example.data.repository

import android.content.Context
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class AuthRepositoryImpl(
    private val context: Context
) : AuthRepository {

    private val accountPrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val firebaseAuth: FirebaseAuth? by lazy {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        }.getOrNull()
    }

    private val firestore: FirebaseFirestore? by lazy {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        }.getOrNull()
    }

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = _currentUser.asStateFlow()

    init {
        ensureDefaultAccountSeeded()
        restoreSavedSession()
    }

    private fun ensureDefaultAccountSeeded() {
        val defaultEmail = "user@routepilot.in"
        val key = accountKey(defaultEmail)
        if (!accountPrefs.contains(key)) {
            saveLocalAccount(
                uid = "usr_default_routepilot",
                name = "RoutePilot User",
                email = defaultEmail,
                password = "routepilot123"
            )
        }
        val legacyEmail = "driver@routepilot.in"
        if (!accountPrefs.contains(accountKey(legacyEmail))) {
            saveLocalAccount(
                uid = "usr_legacy_routepilot",
                name = "RoutePilot User",
                email = legacyEmail,
                password = "driver123"
            )
        }
    }

    private fun restoreSavedSession() {
        val fbUser = firebaseAuth?.currentUser
        if (fbUser != null && !fbUser.isAnonymous) {
            val email = fbUser.email.orEmpty().replace(Regex("(?i)driver"), "user")
            val localRecord = getLocalAccount(email)
            val resolvedName = fbUser.displayName?.takeIf { it.isNotBlank() }
                ?: localRecord?.optString("name")?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "User" }
            _currentUser.value = User(
                id = fbUser.uid,
                name = resolvedName.replace(Regex("(?i)driver"), "User").trim(),
                email = email
            )
            return
        }

        val activeEmail = accountPrefs.getString(KEY_ACTIVE_USER_EMAIL, null)
        if (!activeEmail.isNullOrBlank()) {
            val record = getLocalAccount(activeEmail)
            if (record != null) {
                val sanitizedEmail = record.optString("email", activeEmail)
                    .replace(Regex("(?i)driver"), "user")
                _currentUser.value = User(
                    id = record.optString("uid", "usr_${sanitizedEmail.hashCode().toUInt()}"),
                    name = record.optString("name", sanitizedEmail.substringBefore("@"))
                        .replace(Regex("(?i)driver"), "User")
                        .trim(),
                    email = sanitizedEmail
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

        val auth = firebaseAuth
        if (auth != null) {
            return runCatching {
                val res = auth.signInWithEmailAndPassword(cleanEmail, password).await()
                val fbUser = res.user ?: throw IllegalStateException("Authentication failed.")
                val profileName = fetchOrSyncUserInFirestore(
                    uid = fbUser.uid,
                    fallbackName = fbUser.displayName
                        ?: getLocalAccount(cleanEmail)?.optString("name")
                        ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                    email = cleanEmail
                )
                val user = User(
                    id = fbUser.uid,
                    name = profileName,
                    email = cleanEmail
                )
                saveLocalAccount(
                    uid = user.id,
                    name = user.name,
                    email = cleanEmail,
                    password = password
                )
                setActiveSession(if (rememberMe) cleanEmail else null)
                _currentUser.value = user
                user
            }
        }

        val localAccount = getLocalAccount(cleanEmail)
            ?: return Result.failure(
                IllegalArgumentException("No account found for $cleanEmail. Please Sign Up first.")
            )

        val storedPassword = localAccount.optString("password", "")
        if (storedPassword != password) {
            return Result.failure(
                IllegalArgumentException("Incorrect password. Please try again or tap Forgot Password.")
            )
        }

        val user = User(
            id = localAccount.optString("uid", "drv_${cleanEmail.hashCode().toUInt()}"),
            name = localAccount.optString(
                "name",
                cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            ),
            email = cleanEmail
        )
        setActiveSession(if (rememberMe) cleanEmail else null)
        _currentUser.value = user
        return Result.success(user)
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

        val auth = firebaseAuth
        if (auth != null) {
            return runCatching {
                val res = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                val fbUser = res.user ?: throw IllegalStateException("Account creation failed.")
                runCatching {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                    fbUser.updateProfile(profileUpdates).await()
                }
                saveUserToFirestore(
                    uid = fbUser.uid,
                    name = cleanName,
                    email = cleanEmail,
                    provider = "email"
                )
                val user = User(
                    id = fbUser.uid,
                    name = cleanName,
                    email = cleanEmail
                )
                saveLocalAccount(
                    uid = user.id,
                    name = cleanName,
                    email = cleanEmail,
                    password = password
                )
                setActiveSession(cleanEmail)
                _currentUser.value = user
                user
            }
        }

        if (getLocalAccount(cleanEmail) != null) {
            return Result.failure(
                IllegalArgumentException("An account with $cleanEmail already exists. Please Login instead.")
            )
        }

        val uid = "drv_${UUID.randomUUID().toString().take(8)}"
        saveLocalAccount(
            uid = uid,
            name = cleanName,
            email = cleanEmail,
            password = password
        )
        setActiveSession(cleanEmail)
        val newUser = User(
            id = uid,
            name = cleanName,
            email = cleanEmail
        )
        _currentUser.value = newUser
        return Result.success(newUser)
    }

    override suspend fun continueWithGoogle(
        email: String,
        name: String,
        idToken: String?
    ): Result<User> {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Please select or enter a valid Google email address."))
        }
        val existingLocal = getLocalAccount(cleanEmail)
        val cleanName = name.trim().ifBlank {
            existingLocal?.optString("name")?.takeIf { it.isNotBlank() }
                ?: cleanEmail.substringBefore("@")
                    .split(".", "_", "-")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                    .ifBlank { "Google User" }
        }.replace("Driver", "User").trim()
        val uid = existingLocal?.optString("uid")?.takeIf { it.isNotBlank() }
            ?: "google_usr_${cleanEmail.hashCode().toUInt()}"

        runCatching {
            saveUserToFirestore(
                uid = uid,
                name = cleanName,
                email = cleanEmail,
                provider = "google"
            )
        }

        saveLocalAccount(
            uid = uid,
            name = cleanName,
            email = cleanEmail,
            password = existingLocal?.optString("password")?.takeIf { it.isNotBlank() } ?: "google_oauth_user"
        )
        setActiveSession(cleanEmail)

        val user = User(
            id = uid,
            name = cleanName,
            email = cleanEmail
        )
        _currentUser.value = user
        return Result.success(user)
    }

    override suspend fun sendPasswordReset(
        email: String,
        newPassword: String?
    ): Result<String> {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Please enter your registered email address."))
        }

        val auth = firebaseAuth
        if (auth != null) {
            return runCatching {
                auth.sendPasswordResetEmail(cleanEmail).await()
                if (!newPassword.isNullOrBlank() && newPassword.length >= 6) {
                    val existing = getLocalAccount(cleanEmail)
                    if (existing != null) {
                        saveLocalAccount(
                            uid = existing.optString("uid", "drv_${cleanEmail.hashCode().toUInt()}"),
                            name = existing.optString("name", cleanEmail.substringBefore("@")),
                            email = cleanEmail,
                            password = newPassword
                        )
                    }
                }
                "Password reset email sent to $cleanEmail via Firebase Authentication."
            }
        }

        val existing = getLocalAccount(cleanEmail)
            ?: return Result.failure(
                IllegalArgumentException("No registered account found for $cleanEmail. Please Sign Up first.")
            )

        if (!newPassword.isNullOrBlank()) {
            if (newPassword.length < 6) {
                return Result.failure(
                    IllegalArgumentException("New password must be at least 6 characters.")
                )
            }
            saveLocalAccount(
                uid = existing.optString("uid", "drv_${cleanEmail.hashCode().toUInt()}"),
                name = existing.optString("name", cleanEmail.substringBefore("@")),
                email = cleanEmail,
                password = newPassword
            )
            return Result.success("Password for $cleanEmail has been updated. You can now Login with your new password.")
        }

        return Result.success("Password reset link sent to $cleanEmail.")
    }

    override suspend fun logout() {
        runCatching { firebaseAuth?.signOut() }
        setActiveSession(null)
        _currentUser.value = null
    }

    private suspend fun saveUserToFirestore(
        uid: String,
        name: String,
        email: String,
        provider: String
    ) {
        val db = firestore ?: return
        val now = System.currentTimeMillis()
        val data = mapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "authProvider" to provider,
            "role" to "user",
            "updatedAt" to now,
            "lastLoginAt" to now
        )
        runCatching {
            db.collection("users")
                .document(uid)
                .set(data, SetOptions.merge())
                .await()
        }
    }

    private suspend fun fetchOrSyncUserInFirestore(
        uid: String,
        fallbackName: String,
        email: String
    ): String {
        val db = firestore ?: return fallbackName
        return runCatching {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val storedName = snap.getString("name")?.takeIf { it.isNotBlank() } ?: fallbackName
            docRef.set(
                mapOf(
                    "uid" to uid,
                    "name" to storedName,
                    "email" to email,
                    "lastLoginAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
            storedName
        }.getOrDefault(fallbackName)
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
        private const val PREFS_NAME = "routepilot_driver_accounts"
        private const val KEY_ACCOUNT_PREFIX = "account_"
        private const val KEY_ACTIVE_USER_EMAIL = "active_user_email"
    }
}
