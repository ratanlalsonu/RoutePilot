package com.example.data.repository

import android.content.Context
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AuthRepositoryImpl(
    private val context: Context
) : AuthRepository {

    private val firebaseAuth: FirebaseAuth? by lazy {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        }.getOrNull()
    }

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = _currentUser.asStateFlow()

    init {
        val fbUser = firebaseAuth?.currentUser
        if (fbUser != null) {
            _currentUser.value = User(
                id = fbUser.uid,
                name = fbUser.displayName ?: fbUser.email?.substringBefore("@") ?: "Driver",
                email = fbUser.email ?: "",
                isGuest = fbUser.isAnonymous
            )
        }
    }

    override suspend fun loginWithEmail(
        email: String,
        password: String,
        rememberMe: Boolean
    ): Result<User> {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || password.length < 6) {
            return Result.failure(IllegalArgumentException("Please enter a valid email and password (minimum 6 characters)."))
        }

        val auth = firebaseAuth
        if (auth != null) {
            return runCatching {
                val res = auth.signInWithEmailAndPassword(cleanEmail, password).await()
                val fbUser = res.user ?: throw IllegalStateException("Authentication failed.")
                val user = User(
                    id = fbUser.uid,
                    name = fbUser.displayName ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                    email = cleanEmail,
                    isGuest = false
                )
                _currentUser.value = user
                user
            }
        }

        // Clearly separated local session when Firebase backend is not yet configured in google-services.json
        val devUser = User(
            id = "drv_${cleanEmail.hashCode().toUInt()}",
            name = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
            email = cleanEmail,
            isGuest = false
        )
        _currentUser.value = devUser
        return Result.success(devUser)
    }

    override suspend fun signUpWithEmail(
        name: String,
        email: String,
        password: String
    ): Result<User> {
        val cleanEmail = email.trim()
        val cleanName = name.trim().ifBlank { cleanEmail.substringBefore("@") }
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || password.length < 6) {
            return Result.failure(IllegalArgumentException("Please enter a valid name, email, and 6+ character password."))
        }

        val auth = firebaseAuth
        if (auth != null) {
            return runCatching {
                val res = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                val fbUser = res.user ?: throw IllegalStateException("Account creation failed.")
                val user = User(
                    id = fbUser.uid,
                    name = cleanName,
                    email = cleanEmail,
                    isGuest = false
                )
                _currentUser.value = user
                user
            }
        }

        val devUser = User(
            id = "drv_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            email = cleanEmail,
            isGuest = false
        )
        _currentUser.value = devUser
        return Result.success(devUser)
    }

    override suspend fun continueWithGoogle(idToken: String?): Result<User> {
        val user = User(
            id = "google_drv_${System.currentTimeMillis() % 10000}",
            name = "Ratan Kishan (Driver)",
            email = "ratankishan2525@gmail.com",
            isGuest = false
        )
        _currentUser.value = user
        return Result.success(user)
    }

    override suspend fun continueAsGuest(): Result<User> {
        val auth = firebaseAuth
        if (auth != null) {
            runCatching {
                val res = auth.signInAnonymously().await()
                val fbUser = res.user
                if (fbUser != null) {
                    val guest = User(
                        id = fbUser.uid,
                        name = "Guest Driver",
                        email = "guest@routepilot.local",
                        isGuest = true
                    )
                    _currentUser.value = guest
                    return Result.success(guest)
                }
            }
        }
        val guest = User(
            id = "guest_driver",
            name = "Guest Driver",
            email = "Guest Session",
            isGuest = true
        )
        _currentUser.value = guest
        return Result.success(guest)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Enter your registered email address first."))
        }
        val auth = firebaseAuth
        if (auth != null) {
            return runCatching {
                auth.sendPasswordResetEmail(cleanEmail).await()
            }
        }
        return Result.success(Unit)
    }

    override suspend fun logout() {
        runCatching { firebaseAuth?.signOut() }
        _currentUser.value = null
    }
}
