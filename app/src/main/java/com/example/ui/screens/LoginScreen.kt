package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.OperatingMode
import com.example.ui.components.RoutePilotBrandLogo
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.RoutePilotNavy
import com.example.ui.theme.SurfaceBackground

private data class GoogleAccountOption(
    val name: String,
    val email: String,
    val badgeColor: Color
)

/**
 * SCREEN 2 — LOGIN & SIGN UP SCREEN
 * Provides full Login, Sign Up, Remember Me, Forgot Password, and Continue with Google
 * (with interactive in-window Google Account Picker overlay to select or add a Google email).
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
    onContinueWithGoogle: (email: String, name: String) -> Unit,
    onForgotPassword: (email: String, newPassword: String?) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val sanitizedInitialEmail = remember(initialEmail) {
        val trimmed = initialEmail.trim()
        if (trimmed.contains("driver", ignoreCase = true)) "user@routepilot.in" else trimmed
    }

    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(sanitizedInitialEmail.ifBlank { "user@routepilot.in" }) }
    var password by rememberSaveable { mutableStateOf("routepilot123") }
    var rememberMe by rememberSaveable { mutableStateOf(initialRememberMe) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var showForgotDialog by rememberSaveable { mutableStateOf(false) }
    var resetEmailInput by rememberSaveable { mutableStateOf("") }
    var resetNewPasswordInput by rememberSaveable { mutableStateOf("") }

    // Google Account Chooser state
    var showGoogleAccountPicker by rememberSaveable { mutableStateOf(false) }
    var isAddingCustomGoogleAccount by rememberSaveable { mutableStateOf(false) }
    var customGoogleName by rememberSaveable { mutableStateOf("") }
    var customGoogleEmail by rememberSaveable { mutableStateOf("") }
    var customGoogleError by rememberSaveable { mutableStateOf<String?>(null) }

    val googleAccounts = remember {
        mutableStateListOf(
            GoogleAccountOption(
                name = "Ratan Kishan",
                email = "ratankishan2525@gmail.com",
                badgeColor = Color(0xFF1A73E8)
            ),
            GoogleAccountOption(
                name = "RoutePilot User",
                email = "user.routepilot@gmail.com",
                badgeColor = Color(0xFF16894C)
            ),
            GoogleAccountOption(
                name = "Kishan Kumar",
                email = "kishan.kumar@gmail.com",
                badgeColor = Color(0xFF9333EA)
            )
        ).apply {
            val saved = sanitizedInitialEmail.trim()
            if (saved.isNotBlank() && saved.contains("@") && none { it.email.equals(saved, ignoreCase = true) }) {
                add(
                    0,
                    GoogleAccountOption(
                        name = saved.substringBefore("@").replaceFirstChar { it.uppercase() },
                        email = saved,
                        badgeColor = Color(0xFFEA580C)
                    )
                )
            }
        }
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

                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            isAddingCustomGoogleAccount = false
                            customGoogleName = ""
                            customGoogleEmail = ""
                            customGoogleError = null
                            showGoogleAccountPicker = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFD8E0EC)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_login_button")
                    ) {
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
                                    if (isSignUpMode && email == "user@routepilot.in") {
                                        email = ""
                                        password = ""
                                    }
                                }
                                .padding(4.dp)
                                .testTag("toggle_signup_mode")
                        )
                    }
                }
            }

            // In-Window Google Sign-In Account Picker Overlay (avoids cross-window IME FrameTracker timeouts)
            if (showGoogleAccountPicker) {
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
                            showGoogleAccountPicker = false
                            isAddingCustomGoogleAccount = false
                            customGoogleError = null
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
                            .testTag("google_account_picker_dialog")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1A73E8),
                                    fontSize = 24.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = if (isAddingCustomGoogleAccount) {
                                    "Sign in with Google"
                                } else {
                                    "Choose an account"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "to continue to RoutePilot",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF64748B)
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            if (!isAddingCustomGoogleAccount) {
                                googleAccounts.forEachIndexed { index, account ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                showGoogleAccountPicker = false
                                                onContinueWithGoogle(account.email, account.name)
                                            }
                                            .padding(horizontal = 22.dp, vertical = 13.dp)
                                            .testTag("google_account_item_$index"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(account.badgeColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = account.name.firstOrNull()?.uppercase() ?: "G",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = account.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF0F172A)
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = account.email,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF64748B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 22.dp),
                                        color = Color(0xFFF1F5F9)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            customGoogleError = null
                                            isAddingCustomGoogleAccount = true
                                        }
                                        .padding(horizontal = 22.dp, vertical = 14.dp)
                                        .testTag("google_use_another_account"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF1F5F9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PersonAddAlt,
                                            contentDescription = "Use another account",
                                            tint = Color(0xFF334155),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Use another account",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1E293B)
                                        )
                                    )
                                }

                                HorizontalDivider(color = Color(0xFFE2E8F0))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            showGoogleAccountPicker = false
                                        }
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_cancel),
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 22.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (customGoogleError != null) {
                                        Text(
                                            text = customGoogleError!!,
                                            color = HazardRed,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    OutlinedTextField(
                                        value = customGoogleEmail,
                                        onValueChange = {
                                            customGoogleEmail = it
                                            customGoogleError = null
                                        },
                                        label = { Text("Google Email (e.g. name@gmail.com)") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Email,
                                                contentDescription = null
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("custom_google_email_input")
                                    )

                                    OutlinedTextField(
                                        value = customGoogleName,
                                        onValueChange = { customGoogleName = it },
                                        label = { Text("Display Name (optional)") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Person,
                                                contentDescription = null
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("custom_google_name_input")
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                isAddingCustomGoogleAccount = false
                                                customGoogleError = null
                                            }
                                        ) {
                                            Text(
                                                text = "Back to accounts",
                                                color = Color(0xFF475569)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                val cleanMail = customGoogleEmail.trim()
                                                if (cleanMail.isBlank() || !cleanMail.contains("@") || !cleanMail.contains(".")) {
                                                    customGoogleError = "Please enter a valid Google email address."
                                                } else {
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                    val resolvedName = customGoogleName.trim().ifBlank {
                                                        cleanMail.substringBefore("@")
                                                            .replaceFirstChar { it.uppercase() }
                                                    }
                                                    if (googleAccounts.none { it.email.equals(cleanMail, ignoreCase = true) }) {
                                                        googleAccounts.add(
                                                            0,
                                                            GoogleAccountOption(
                                                                name = resolvedName,
                                                                email = cleanMail,
                                                                badgeColor = RoutePilotNavy
                                                            )
                                                        )
                                                    }
                                                    showGoogleAccountPicker = false
                                                    isAddingCustomGoogleAccount = false
                                                    onContinueWithGoogle(cleanMail, resolvedName)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.testTag("confirm_custom_google_account")
                                        ) {
                                            Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
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
                                    Text(stringResource(R.string.action_cancel))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        val targetEmail = resetEmailInput.trim()
                                        val targetNewPass = resetNewPasswordInput.takeIf { it.isNotBlank() }
                                        onForgotPassword(targetEmail, targetNewPass)
                                        if (targetNewPass != null && targetNewPass.length >= 6) {
                                            email = targetEmail
                                            password = targetNewPass
                                        }
                                        showForgotDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                                    modifier = Modifier.testTag("confirm_forgot_password_button")
                                ) {
                                    Text("Reset Password", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
