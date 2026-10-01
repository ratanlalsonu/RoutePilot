package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.domain.model.OperatingMode
import com.example.ui.components.RoutePilotBrandLogo
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.SurfaceBackground
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Silent Auto-Sign-In on App Startup / Login Screen Launch.
 * Uses a single GetGoogleIdOption in its own GetCredentialRequest.
 */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: (email: String, name: String, idToken: String?) -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
    if (currentUser != null && !currentUser.email.isNullOrBlank()) {
        onAuthSuccess(
            currentUser.email.orEmpty(),
            currentUser.displayName.orEmpty(),
            null
        )
        return
    }

    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val activityContext = context.findActivity() ?: context
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = runCatching {
                    Firebase.auth.signInWithCredential(authCredential).await()
                }.getOrNull()
                val fbUser = authResult?.user
                val resolvedEmail = fbUser?.email?.takeIf { it.isNotBlank() } ?: googleIdTokenCredential.id
                val resolvedName = fbUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: googleIdTokenCredential.displayName.orEmpty()
                onAuthSuccess(resolvedEmail, resolvedName, googleIdToken)
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

/**
 * Interactive Google Sign-In flow via Jetpack Credential Manager.
 * Uses GetSignInWithGoogleOption in its own single-option GetCredentialRequest
 * so the real Android system Google account chooser is presented.
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: (email: String, name: String, idToken: String) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
    onNoGoogleAccountOnDevice: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val activityContext = context.findActivity() ?: context
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = runCatching {
                    Firebase.auth.signInWithCredential(authCredential).await()
                }.getOrNull()
                val fbUser = authResult?.user
                val resolvedEmail = fbUser?.email?.takeIf { it.isNotBlank() } ?: googleIdTokenCredential.id
                val resolvedName = fbUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: googleIdTokenCredential.displayName.orEmpty()
                onAuthSuccess(resolvedEmail, resolvedName, googleIdToken)
            } else {
                onAuthError("Unexpected credential type returned from Google Sign-In.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.w("Auth", "No Google account found on device: ${e.message}", e)
            onNoGoogleAccountOnDevice()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed. Please try again.")
        }
    }
}

/**
 * SCREEN 2 — LOGIN & SIGN UP SCREEN
 * Uses real Google Sign-In via Jetpack Credential Manager (`GetSignInWithGoogleOption`)
 * and Firebase Authentication — with zero dummy email accounts.
 */
@Composable
fun LoginScreen(
    initialEmail: String,
    initialRememberMe: Boolean,
    operatingMode: OperatingMode,
    authError: String?,
    statusMessage: String?,
    onToggleOperatingMode: () -> Unit,
    onLoginSubmit: (email: String, password: String, rememberMe: Boolean) -> Unit,
    onSignUpSubmit: (name: String, email: String, password: String) -> Unit,
    onContinueWithGoogle: (email: String, name: String, idToken: String?) -> Unit,
    onAuthError: (String?) -> Unit = {},
    onForgotPassword: (email: String, newPassword: String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val sanitizedInitialEmail = remember(initialEmail) {
        val trimmed = initialEmail.trim()
        if (
            trimmed.contains("driver", ignoreCase = true) ||
            trimmed.equals("user@routepilot.in", ignoreCase = true) ||
            trimmed.equals("user.routepilot@gmail.com", ignoreCase = true) ||
            trimmed.equals("kishan.kumar@gmail.com", ignoreCase = true)
        ) {
            ""
        } else {
            trimmed
        }
    }

    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(sanitizedInitialEmail) }
    var password by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(initialRememberMe) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isGoogleLoading by rememberSaveable { mutableStateOf(false) }
    var showAddDeviceGoogleAccountPrompt by rememberSaveable { mutableStateOf(false) }

    var showForgotDialog by rememberSaveable { mutableStateOf(false) }
    var resetEmailInput by rememberSaveable { mutableStateOf("") }
    var resetNewPasswordInput by rememberSaveable { mutableStateOf("") }

    // Attempt silent auto sign-in once when LoginScreen launches if user already authorized Google Sign-In
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { resolvedEmail, resolvedName, idToken ->
                onContinueWithGoogle(resolvedEmail, resolvedName, idToken)
            },
            onUnauthenticated = { },
            scope = coroutineScope
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                RoutePilotBrandLogo(iconSize = 72.dp, showTagline = false)

                Spacer(modifier = Modifier.height(24.dp))

                if (authError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = authError,
                            color = HazardRed,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (showAddDeviceGoogleAccountPrompt) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No Google account is signed in on this device yet.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Add your Google account in Android Settings, then tap Continue with Google again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569)
                            )
                            Button(
                                onClick = {
                                    showAddDeviceGoogleAccountPrompt = false
                                    runCatching {
                                        val addAccountIntent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                                            putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                                        }
                                        context.findActivity()?.startActivity(addAccountIntent)
                                            ?: context.startActivity(
                                                addAccountIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                    }.onFailure {
                                        runCatching {
                                            val settingsIntent = Intent(Settings.ACTION_SYNC_SETTINGS)
                                            context.findActivity()?.startActivity(settingsIntent)
                                                ?: context.startActivity(
                                                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("add_device_google_account_button")
                            ) {
                                Text(
                                    text = "Add Google Account on Device",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (statusMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F8EE)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = statusMessage,
                            color = Color(0xFF16894C),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                ) {
                    if (isSignUpMode) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Full Name") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = "Full Name"
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = RoutePilotBlue,
                                unfocusedBorderColor = Color(0xFFD8E0EC)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_name_input")
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text(stringResource(R.string.label_email)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = stringResource(R.string.label_email)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = RoutePilotBlue,
                            unfocusedBorderColor = Color(0xFFD8E0EC)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_email_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text(stringResource(R.string.label_password)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = stringResource(R.string.label_password)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = RoutePilotBlue,
                            unfocusedBorderColor = Color(0xFFD8E0EC)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { rememberMe = !rememberMe }
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(checkedColor = RoutePilotBlue)
                            )
                            Text(
                                text = stringResource(R.string.label_remember_me),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF475569)
                            )
                        }

                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                resetEmailInput = email
                                resetNewPasswordInput = ""
                                showForgotDialog = true
                            },
                            modifier = Modifier.testTag("forgot_password_button")
                        ) {
                            Text(
                                text = stringResource(R.string.action_forgot_password),
                                color = RoutePilotBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            if (isSignUpMode) {
                                onSignUpSubmit(name, email, password)
                            } else {
                                onLoginSubmit(email, password, rememberMe)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("login_button")
                    ) {
                        Text(
                            text = if (isSignUpMode) {
                                stringResource(R.string.action_sign_up)
                            } else {
                                stringResource(R.string.action_login)
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFD8E0EC))
                        Text(
                            text = "  ${stringResource(R.string.label_or)}  ",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF64748B)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFD8E0EC))
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Real Google Sign-In button using Jetpack Credential Manager (GetSignInWithGoogleOption)
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onAuthError(null)
                            showAddDeviceGoogleAccountPrompt = false
                            isGoogleLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = { googleEmail, googleName, idToken ->
                                    isGoogleLoading = false
                                    onContinueWithGoogle(googleEmail, googleName, idToken)
                                },
                                onAuthError = { errorMsg ->
                                    isGoogleLoading = false
                                    onAuthError(errorMsg)
                                },
                                scope = coroutineScope,
                                onAuthCancelled = {
                                    isGoogleLoading = false
                                },
                                onNoGoogleAccountOnDevice = {
                                    isGoogleLoading = false
                                    showAddDeviceGoogleAccountPrompt = true
                                }
                            )
                        },
                        enabled = !isGoogleLoading,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFD8E0EC)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_login_button")
                    ) {
                        if (isGoogleLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = RoutePilotBlue,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.action_continue_google),
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RoutePilotBlue,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.action_continue_google),
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSignUpMode) {
                                "Already have an account? "
                            } else {
                                "${stringResource(R.string.prompt_no_account)} "
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = if (isSignUpMode) {
                                stringResource(R.string.action_login)
                            } else {
                                stringResource(R.string.action_sign_up)
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = RoutePilotBlue,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .clickable {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    isSignUpMode = !isSignUpMode
                                }
                                .padding(4.dp)
                                .testTag("toggle_signup_mode")
                        )
                    }
                }
            }

            // In-Window Forgot Password Overlay
            if (showForgotDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.48f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            showForgotDialog = false
                        }
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        tonalElevation = 6.dp,
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_forgot_password),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Enter your registered email to receive a Firebase password reset link or set a new password:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF475569)
                            )
                            OutlinedTextField(
                                value = resetEmailInput,
                                onValueChange = { resetEmailInput = it },
                                label = { Text(stringResource(R.string.label_email)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("forgot_password_email_input")
                            )
                            OutlinedTextField(
                                value = resetNewPasswordInput,
                                onValueChange = { resetNewPasswordInput = it },
                                label = { Text("New Password (optional, 6+ chars)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("forgot_password_new_password_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        showForgotDialog = false
                                    }
                                ) {
                                    Text(stringResource(R.string.action_cancel), color = Color(0xFF64748B))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        showForgotDialog = false
                                        onForgotPassword(
                                            resetEmailInput,
                                            resetNewPasswordInput.takeIf { it.isNotBlank() }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                                    modifier = Modifier.testTag("send_reset_button")
                                ) {
                                    Text("Reset Password", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
