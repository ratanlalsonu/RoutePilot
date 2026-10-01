package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HazardOrange
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.RoutePilotNavy
import com.example.ui.theme.SafeRouteGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class AvatarPresetOption(
    val id: String,
    val label: String,
    val bgColor: Color,
    val icon: ImageVector
)

val avatarPresetOptions = listOf(
    AvatarPresetOption("default_blue", "Default", RoutePilotBlue, Icons.Default.Person),
    AvatarPresetOption("navy_shield", "Captain", RoutePilotNavy, Icons.Default.Shield),
    AvatarPresetOption("green_pilot", "Navigator", SafeRouteGreen, Icons.Default.DirectionsCar),
    AvatarPresetOption("orange_eng", "Engineer", HazardOrange, Icons.Default.Engineering),
    AvatarPresetOption("crimson_pro", "Pro User", HazardRed, Icons.Default.Face),
    AvatarPresetOption("purple_star", "Explorer", Color(0xFF7C3AED), Icons.Default.EmojiEmotions)
)

data class ProfileAvatarData(
    val customBitmap: Bitmap? = null,
    val presetId: String = "default_blue"
)

object ProfileAvatarStore {
    private const val PREFS_NAME = "routepilot_avatar_prefs"
    private const val KEY_PRESET_ID = "avatar_preset_id"
    private const val AVATAR_FILE_NAME = "user_profile_avatar.jpg"

    private val _avatarState = MutableStateFlow(ProfileAvatarData())
    val avatarState: StateFlow<ProfileAvatarData> = _avatarState.asStateFlow()
    private var initialized = false

    suspend fun ensureLoaded(context: Context) {
        if (initialized) return
        initialized = true
        reloadFromDisk(context)
    }

    suspend fun reloadFromDisk(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val presetId = prefs.getString(KEY_PRESET_ID, "default_blue") ?: "default_blue"
        val file = File(context.filesDir, AVATAR_FILE_NAME)
        val bmp = if (file.exists()) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        } else {
            null
        }
        _avatarState.value = ProfileAvatarData(
            customBitmap = bmp,
            presetId = presetId
        )
    }

    suspend fun savePickedImageUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val decoded = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull() ?: return@withContext

        val scaled = scaleBitmapCenterSquare(decoded, 360)
        val outFile = File(context.filesDir, AVATAR_FILE_NAME)
        FileOutputStream(outFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        _avatarState.value = _avatarState.value.copy(customBitmap = scaled)
    }

    suspend fun selectPresetAvatar(context: Context, presetId: String) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PRESET_ID, presetId).apply()
        val file = File(context.filesDir, AVATAR_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
        _avatarState.value = ProfileAvatarData(
            customBitmap = null,
            presetId = presetId
        )
    }

    private fun scaleBitmapCenterSquare(src: Bitmap, targetSize: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val xOffset = (src.width - side) / 2
        val yOffset = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, xOffset, yOffset, side, side)
        return Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
    }
}

@Composable
fun UserProfileAvatar(
    size: Dp,
    userName: String = "",
    showEditBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val avatarData by ProfileAvatarStore.avatarState.collectAsState()

    LaunchedEffect(Unit) {
        ProfileAvatarStore.ensureLoaded(context)
    }

    val preset = avatarPresetOptions.firstOrNull { it.id == avatarData.presetId }
        ?: avatarPresetOptions.first()

    Box(
        modifier = modifier
            .size(size)
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m },
        contentAlignment = Alignment.Center
    ) {
        val bmp = avatarData.customBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = userName.ifBlank { "Profile Picture" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(preset.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = preset.icon,
                    contentDescription = userName.ifBlank { "Profile" },
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.54f)
                )
            }
        }

        if (showEditBadge) {
            Surface(
                shape = CircleShape,
                color = RoutePilotNavy,
                shadowElevation = 4.dp,
                border = BorderStroke(2.dp, Color.White),
                modifier = Modifier
                    .size(30.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change Profile Photo",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChangeProfilePhotoDialog(
    userName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val registryOwner = LocalActivityResultRegistryOwner.current
    val avatarData by ProfileAvatarStore.avatarState.collectAsState()

    val photoPickerLauncher = if (registryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    ProfileAvatarStore.savePickedImageUri(context, uri)
                    onDismiss()
                }
            }
        }
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("change_profile_photo_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserProfileAvatar(
                    size = 82.dp,
                    userName = userName,
                    showEditBadge = false
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Update Profile Picture",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        fontSize = 19.sp
                    )
                )
                Text(
                    text = "Choose a photo from your device gallery or select an avatar style",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Pick from Device Gallery Button (Zero-permission Android Photo Picker)
                if (photoPickerLauncher != null) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pick_gallery_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Choose Photo from Gallery",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text(
                    text = "Or Select an Avatar Style:",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2 rows of 3 avatar presets
                val rows = avatarPresetOptions.chunked(3)
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { preset ->
                            val isSelected = avatarData.customBitmap == null && avatarData.presetId == preset.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) RoutePilotBlue else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        scope.launch {
                                            ProfileAvatarStore.selectPresetAvatar(context, preset.id)
                                        }
                                    }
                                    .testTag("avatar_preset_${preset.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(preset.bgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = preset.icon,
                                            contentDescription = preset.label,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = RoutePilotBlue,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                        }
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E293B)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (avatarData.customBitmap != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                ProfileAvatarStore.selectPresetAvatar(context, "default_blue")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = HazardRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Remove Custom Photo",
                            color = HazardRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoutePilotNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("done_profile_photo_button")
                ) {
                    Text(
                        text = "Done",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
