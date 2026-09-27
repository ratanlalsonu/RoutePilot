package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.OperatingMode
import com.example.ui.components.OperatingModeBadge
import com.example.ui.components.RoutePilotBrandLogo
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.SurfaceBackground

/**
 * SCREEN 2 — LOGIN / GUEST SCREEN
 * Reproduces the reference Login UI with Driver Login / Guest segmented tabs,
 * Email, Password, Remember me, Forgot Password, Login, Continue with Google, and Sign Up.
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
    onContinueWithGoogle: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onForgotPassword: (String) -> Unit
) {
    var isGuestTab by rememberSaveable { mutableStateOf(false) }
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(initialEmail.ifBlank { "driver@routepilot.in" }) }
    var password by rememberSaveable { mutableStateOf("driver123") }
    var rememberMe by rememberSaveable { mutableStateOf(initialRememberMe) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OperatingModeBadge(
                    mode = operatingMode,
                    onToggleMode = onToggleOperatingMode
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            RoutePilotBrandLogo(iconSize = 72.dp, showTagline = false)

            Spacer(modifier = Modifier.height(22.dp))

            // Segmented Pill Tabs: Driver Login | Guest (matching Screen 2)
            Row(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE6ECF5))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(if (!isGuestTab) RoutePilotBlue else Color.Transparent)
                        .clickable { isGuestTab = false }
                        .testTag("tab_driver_login"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.tab_driver_login),
                        color = if (!isGuestTab) Color.White else Color(0xFF475569),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(if (isGuestTab) RoutePilotBlue else Color.Transparent)
                        .clickable { isGuestTab = true }
                        .testTag("tab_guest_mode"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.tab_guest),
                        color = if (isGuestTab) Color.White else Color(0xFF475569),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

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

            if (!isGuestTab) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                ) {
                    if (isSignUpMode) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Driver Full Name") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = "Driver Name"
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

                        TextButton(onClick = { onForgotPassword(email) }) {
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
                        onClick = onContinueWithGoogle,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFD8E0EC)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_login_button")
                    ) {
                        // Colorful Google 'G' badge
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
                            text = if (isSignUpMode) "Already have an account? " else "${stringResource(R.string.prompt_no_account)} ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = if (isSignUpMode) stringResource(R.string.action_login) else stringResource(R.string.action_sign_up),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = RoutePilotBlue,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .clickable { isSignUpMode = !isSignUpMode }
                                .padding(4.dp)
                                .testTag("toggle_signup_mode")
                        )
                    }
                }
            } else {
                // Guest Mode Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.tab_guest),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.guest_mode_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Button(
                            onClick = onContinueAsGuest,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("guest_login_button")
                        ) {
                            Text(
                                text = stringResource(R.string.action_continue_guest),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
