package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.LocationPoint
import com.example.domain.routing.GeoUtils
import com.example.domain.routing.OsmRoadNetworkProvider
import com.example.ui.components.RoutePilotMapView
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.RoutePilotBlueLight
import com.example.ui.theme.SafeRouteGreen
import com.example.ui.theme.SurfaceBackground
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private data class NearbyCategoryUiItem(
    val label: String,
    val icon: ImageVector,
    val tint: Color
)

private val nearbyCategoriesBar = listOf(
    NearbyCategoryUiItem("Service Centre", Icons.Default.Build, Color(0xFF0284C7)),
    NearbyCategoryUiItem("School", Icons.Default.School, Color(0xFF7C3AED)),
    NearbyCategoryUiItem("Hospital", Icons.Default.LocalHospital, Color(0xFFDC2626)),
    NearbyCategoryUiItem("Petrol Pump", Icons.Default.LocalGasStation, Color(0xFFEA580C)),
    NearbyCategoryUiItem("Restaurant", Icons.Default.Restaurant, Color(0xFFD97706)),
    NearbyCategoryUiItem("Pharmacy", Icons.Default.LocalPharmacy, Color(0xFF059669)),
    NearbyCategoryUiItem("ATM / Bank", Icons.Default.AccountBalance, Color(0xFF2563EB)),
    NearbyCategoryUiItem("Police Station", Icons.Default.LocalPolice, Color(0xFF1E293B)),
    NearbyCategoryUiItem("Hotel", Icons.Default.Hotel, Color(0xFF9333EA)),
    NearbyCategoryUiItem("College", Icons.Default.School, Color(0xFF0D9488))
)

/**
 * SCREEN 4 — SELECT DESTINATION & VOICE / TEXT PLACE SEARCH SCREEN
 */
@Composable
fun SelectDestinationScreen(
    currentLocation: LocationPoint,
    selectedDestination: Destination,
    searchQuery: String,
    searchResults: List<Destination>,
    isSearchingPlaces: Boolean,
    activeHazards: List<Hazard>,
    isMapsApiKeyConfigured: Boolean,
    hasLocationPermission: Boolean,
    useKilometers: Boolean,
    @Suppress("UNUSED_PARAMETER") locationFilterQuery: String = "",
    searchCenterLocation: LocationPoint? = null,
    searchCenterLabel: String = "Your Current Location",
    selectedCategoryChip: String? = null,
    isMultiMarkerCategoryView: Boolean = false,
    fitAllMarkersTrigger: Int = 0,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: ((String) -> Unit)? = null,
    onSelectCategoryChip: ((String) -> Unit)? = null,
    onShowAllMarkersOnMap: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onUpdateLocationFilter: ((String) -> Unit)? = null,
    onUseCurrentLocation: (() -> Unit)? = null,
    onSelectPlaceSuggestion: (Destination) -> Unit,
    onMapClickLocation: (Double, Double) -> Unit,
    onConfirmDestination: () -> Unit,
    onBack: () -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    var showVoiceFallbackDialog by remember { mutableStateOf(false) }
    var voiceSpokenDraft by remember { mutableStateOf("") }
    val context = LocalContext.current
    val registryOwner = LocalActivityResultRegistryOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val speechRecognizerLauncher = if (registryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (spokenText.isNotEmpty()) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showDropdown = false
                    onSearchQueryChange(spokenText)
                    if (onSearchSubmit != null) {
                        onSearchSubmit(spokenText)
                    }
                }
            }
        }
    } else {
        null
    }

    val launchVoiceSearch = {
        val launcher = speechRecognizerLauncher
        if (launcher != null) {
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak place name to search...")
            }
            try {
                launcher.launch(speechIntent)
            } catch (_: Exception) {
                voiceSpokenDraft = ""
                showVoiceFallbackDialog = true
            }
        } else {
            voiceSpokenDraft = ""
            showVoiceFallbackDialog = true
        }
    }

    val audioPermissionLauncher = if (registryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ ->
            launchVoiceSearch()
        }
    } else {
        null
    }

    val onMicClicked = {
        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPerm || audioPermissionLauncher == null) {
            launchVoiceSearch()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val distanceKmToSelected = remember(currentLocation, searchCenterLocation, selectedDestination) {
        val rawDistKm = selectedDestination.distanceFromUserKm ?: (
            GeoUtils.haversineMeters(
                currentLocation.latitude,
                currentLocation.longitude,
                selectedDestination.latitude,
                selectedDestination.longitude
            ) / 1000.0
            )
        if (rawDistKm <= 500.0) {
            rawDistKm
        } else {
            val refLoc = searchCenterLocation ?: OsmRoadNetworkProvider.DEFAULT_ORIGIN
            val refDistKm = GeoUtils.haversineMeters(
                refLoc.latitude,
                refLoc.longitude,
                selectedDestination.latitude,
                selectedDestination.longitude
            ) / 1000.0
            if (refDistKm <= 500.0) refDistKm.coerceAtLeast(1.2) else 4.8
        }
    }
    val estimatedMinutesToSelected = remember(distanceKmToSelected) {
        max(2, (distanceKmToSelected * 2.2).roundToInt())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onBack()
                    },
                    modifier = Modifier.testTag("select_dest_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F172A)
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.title_select_destination),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            fontSize = 18.sp
                        )
                    )
                }
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onUseCurrentLocation?.invoke()
                    },
                    modifier = Modifier.testTag("use_current_gps_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Detect Current Location",
                        tint = RoutePilotBlue
                    )
                }
            }

            // Main Search Box with Voice Mic Icon and "Search" Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        onSearchQueryChange(it)
                        showDropdown = true
                    },
                    placeholder = {
                        Text(
                            text = "Search Service Centre, School, Hospital...",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = RoutePilotBlue
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSearchingPlaces) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = RoutePilotBlue
                                )
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        onSearchQueryChange("")
                                        showDropdown = false
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Microphone button for Voice Place Search
                            IconButton(
                                onClick = onMicClicked,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("voice_search_mic_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Search Place",
                                    tint = RoutePilotBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = RoutePilotBlue,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clickable {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        showDropdown = false
                                        if (onSearchSubmit != null) {
                                            onSearchSubmit(searchQuery)
                                        } else {
                                            onSearchQueryChange(searchQuery)
                                        }
                                    }
                                    .testTag("search_show_on_map_button")
                            ) {
                                Text(
                                    text = "Search",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            showDropdown = false
                            if (onSearchSubmit != null) {
                                onSearchSubmit(searchQuery)
                            } else {
                                onSearchQueryChange(searchQuery)
                            }
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = RoutePilotBlue,
                        unfocusedBorderColor = Color(0xFFD8E0EC)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("destination_search_input")
                )
            }

            // Nearby Categories Scrollable Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                nearbyCategoriesBar.forEach { catItem ->
                    val isSelected = selectedCategoryChip.equals(catItem.label, ignoreCase = true) ||
                        searchQuery.trim().equals(catItem.label, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) HazardRed else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) HazardRed else Color(0xFFD8E0EC)
                        ),
                        shadowElevation = if (isSelected) 4.dp else 1.dp,
                        modifier = Modifier
                            .clickable {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                showDropdown = false
                                if (onSelectCategoryChip != null) {
                                    onSelectCategoryChip(catItem.label)
                                } else {
                                    onSearchQueryChange(catItem.label)
                                }
                            }
                            .testTag("nearby_category_chip_${catItem.label.lowercase(Locale.US).replace(" ", "_").replace("/", "_")}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = catItem.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else catItem.tint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = catItem.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFF1E293B),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // Map + Autocomplete Suggestions Overlay + Bottom Selected Place Sheet
            Box(modifier = Modifier.weight(1f)) {
                RoutePilotMapView(
                    currentLocation = currentLocation,
                    destination = selectedDestination,
                    primaryRoute = null,
                    hazards = activeHazards,
                    nearbyPlaces = searchResults,
                    searchCenterLocation = searchCenterLocation,
                    isMultiMarkerCategoryView = isMultiMarkerCategoryView,
                    fitAllMarkersTrigger = fitAllMarkersTrigger,
                    isNavigationMode = false,
                    isMapsApiKeyConfigured = isMapsApiKeyConfigured,
                    hasLocationPermission = hasLocationPermission,
                    showNavigationControls = false,
                    onSelectNearbyPlace = { tappedPlace ->
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        showDropdown = false
                        onSelectPlaceSuggestion(tappedPlace)
                    },
                    onMapClick = { lat, lng ->
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        showDropdown = false
                        onMapClickLocation(lat, lng)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Google Maps-style Red Markers Count & "Show All on Map" Pill
                if (!showDropdown && searchResults.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White,
                        border = BorderStroke(1.dp, HazardRed.copy(alpha = 0.45f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp, start = 14.dp, end = 14.dp)
                            .clickable {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onShowAllMarkersOnMap?.invoke()
                            }
                            .testTag("red_markers_map_summary_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = HazardRed,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${searchResults.size} Red Markers • $searchCenterLabel",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Fit All",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = HazardRed,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // Expandable Places List / Autocomplete Suggestions Overlay when typing
                if (showDropdown && searchResults.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .heightIn(max = 220.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEF2F2))
                                    .clickable {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        showDropdown = false
                                        if (onSearchSubmit != null && searchQuery.isNotBlank()) {
                                            onSearchSubmit(searchQuery)
                                        } else {
                                            onShowAllMarkersOnMap?.invoke()
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                                    .testTag("dropdown_show_all_red_markers_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = HazardRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Mark all ${searchResults.size} in Red on Map • $searchCenterLabel",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = HazardRed
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "Show Map",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = RoutePilotBlue
                                    )
                                )
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            LazyColumn {
                                items(searchResults, key = { it.id }) { suggestion ->
                                    val isCurrentlySelected = suggestion.id == selectedDestination.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isCurrentlySelected) Color(0xFFFEF2F2) else Color.White
                                            )
                                            .clickable {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                showDropdown = false
                                                onSelectPlaceSuggestion(suggestion)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                            .testTag("place_suggestion_${suggestion.id}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = HazardRed,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = suggestion.name,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp
                                                ),
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = "${suggestion.category} • ${suggestion.address}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                                color = Color(0xFF64748B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        suggestion.distanceFromUserKm?.let { distKm ->
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = GeoUtils.formatDistanceKm(distKm, useKilometers),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = RoutePilotBlue
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }
                            }
                        }
                    }
                }

                // Bottom Place Details Sheet
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color.White,
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .testTag("selected_place_details_sheet")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        // Quick horizontal strip of nearby places
                        if (searchResults.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("nearby_markers_quick_strip")
                            ) {
                                items(searchResults, key = { "strip_${it.id}" }) { placeItem ->
                                    val isActive = placeItem.id == selectedDestination.id
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isActive) Color(0xFFFEF2F2) else Color(0xFFF8FAFC),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isActive) HazardRed else Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier.clickable {
                                            onSelectPlaceSuggestion(placeItem)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = HazardRed,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = placeItem.name,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    color = if (isActive) HazardRed else Color(0xFF334155),
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Place Details Header
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFEBEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = stringResource(R.string.label_selected_location),
                                    tint = HazardRed,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFFEBEE)
                                    ) {
                                        Text(
                                            text = selectedDestination.category.ifBlank { "Place" },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = HazardRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = RoutePilotBlueLight
                                    ) {
                                        Text(
                                            text = "${GeoUtils.formatDistanceKm(distanceKmToSelected, useKilometers)} • ~$estimatedMinutesToSelected min",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = RoutePilotBlue,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFE8F8EE)
                                    ) {
                                        Text(
                                            text = "Open",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = SafeRouteGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = selectedDestination.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A),
                                        fontSize = 17.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = selectedDestination.address,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = Color(0xFF475569),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Coordinates: %.4f, %.4f",
                                        selectedDestination.latitude,
                                        selectedDestination.longitude
                                    ),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Full-Width "Get Route to Place" Button ("List" button removed)
                        Button(
                            onClick = onConfirmDestination,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("confirm_destination_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Get Route to Place",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Voice Search Fallback Modal (if running on an emulator without hardware microphone / Google Voice service)
        if (showVoiceFallbackDialog) {
            val quickVoiceSuggestions = listOf(
                "District Hospital",
                "Railway Station",
                "Service Centre",
                "Bus Stand",
                "School",
                "Petrol Pump"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(RoutePilotBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = RoutePilotBlue,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Voice Place Search",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                        )
                        Text(
                            text = "Say or select a place name to search immediately",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF64748B)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = voiceSpokenDraft,
                            onValueChange = { voiceSpokenDraft = it },
                            placeholder = { Text("Speak or type place name...", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickVoiceSuggestions.forEach { voicePlace ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = RoutePilotBlueLight,
                                    modifier = Modifier.clickable {
                                        showVoiceFallbackDialog = false
                                        showDropdown = false
                                        onSearchQueryChange(voicePlace)
                                        onSearchSubmit?.invoke(voicePlace)
                                    }
                                ) {
                                    Text(
                                        text = "🎤 \"$voicePlace\"",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = RoutePilotBlue,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showVoiceFallbackDialog = false },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    val query = voiceSpokenDraft.trim()
                                    showVoiceFallbackDialog = false
                                    if (query.isNotEmpty()) {
                                        showDropdown = false
                                        onSearchQueryChange(query)
                                        onSearchSubmit?.invoke(query)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text("Search", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
