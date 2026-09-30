package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.LocationPoint
import com.example.domain.routing.GeoUtils
import com.example.ui.components.RoutePilotMapView
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.RoutePilotBlueLight
import com.example.ui.theme.RoutePilotNavy
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
 * SCREEN 4 — SELECT DESTINATION & GOOGLE MAPS-STYLE NEARBY PLACES SEARCH SCREEN
 * - Searches any category ("Service Centre", "School", "Hospital", "Petrol Pump", "Restaurant", etc.)
 *   around the user's live detected location OR around any user-specified location.
 * - Displays all matching real places on the Google Map with Red Location Markers at their actual coordinates.
 * - Tapping any Red Marker displays the full Place Details card at the bottom and lets the user
 *   calculate & view a route to that place.
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
    locationFilterQuery: String = "",
    searchCenterLocation: LocationPoint? = null,
    searchCenterLabel: String = "Your Current Location",
    selectedCategoryChip: String? = null,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: ((String) -> Unit)? = null,
    onSelectCategoryChip: ((String) -> Unit)? = null,
    onUpdateLocationFilter: ((String) -> Unit)? = null,
    onUseCurrentLocation: (() -> Unit)? = null,
    onSelectPlaceSuggestion: (Destination) -> Unit,
    onMapClickLocation: (Double, Double) -> Unit,
    onConfirmDestination: () -> Unit,
    onBack: () -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    var showLocationInputBar by remember { mutableStateOf(locationFilterQuery.isNotBlank()) }
    var customLocationDraft by remember(locationFilterQuery) { mutableStateOf(locationFilterQuery) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val distanceKmToSelected = remember(currentLocation, selectedDestination) {
        selectedDestination.distanceFromUserKm ?: (
            GeoUtils.haversineMeters(
                currentLocation.latitude,
                currentLocation.longitude,
                selectedDestination.latitude,
                selectedDestination.longitude
            ) / 1000.0
            )
    }
    val estimatedMinutesToSelected = remember(distanceKmToSelected) {
        max(2, (distanceKmToSelected * 2.2).roundToInt())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBackground)
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
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.title_select_destination),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "Search Nearby Places or Specify a Location",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                )
            }
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showLocationInputBar = false
                    customLocationDraft = ""
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

        // Main Search Box (Category, Place Name, or "<Category> in <Location>")
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
                        text = "Search Service Centre, School, Hospital, Petrol Pump...",
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
                                    .padding(end = 8.dp)
                                    .size(18.dp),
                                strokeWidth = 2.dp,
                                color = RoutePilotBlue
                            )
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onSearchQueryChange("")
                                    showDropdown = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
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
                                text = "Map",
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

        // Search Center Location Filter Bar: "Near: Your Current Location" OR Specify a Location
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (locationFilterQuery.isBlank()) Color(0xFFEFF6FF) else Color(0xFFFEF2F2),
                    border = BorderStroke(
                        1.dp,
                        if (locationFilterQuery.isBlank()) Color(0xFFBFDBFE) else Color(0xFFFECACA)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showLocationInputBar = !showLocationInputBar }
                        .testTag("search_location_anchor_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (locationFilterQuery.isBlank()) {
                                Icons.Default.MyLocation
                            } else {
                                Icons.Default.Place
                            },
                            contentDescription = null,
                            tint = if (locationFilterQuery.isBlank()) RoutePilotBlue else HazardRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (locationFilterQuery.isBlank()) {
                                "Near: My Current Location (GPS)"
                            } else {
                                "Near Specified Location: $locationFilterQuery"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (locationFilterQuery.isBlank()) RoutePilotNavy else HazardRed,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.EditLocationAlt,
                            contentDescription = "Specify Location",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showLocationInputBar) "Hide" else "Specify Location",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = RoutePilotBlue,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                if (locationFilterQuery.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = RoutePilotBlueLight,
                        modifier = Modifier
                            .clickable {
                                customLocationDraft = ""
                                showLocationInputBar = false
                                onUseCurrentLocation?.invoke()
                            }
                            .testTag("reset_to_current_location_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = RoutePilotBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Use GPS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RoutePilotBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            if (showLocationInputBar) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customLocationDraft,
                        onValueChange = { customLocationDraft = it },
                        placeholder = {
                            Text(
                                text = "Enter city or area (e.g. Civil Lines, Sipri, Kanpur, Delhi)",
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = HazardRed,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (customLocationDraft.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        customLocationDraft = ""
                                        onUpdateLocationFilter?.invoke("")
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear location",
                                        modifier = Modifier.size(16.dp)
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
                                showLocationInputBar = false
                                showDropdown = false
                                onUpdateLocationFilter?.invoke(customLocationDraft)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = HazardRed,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("specify_location_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            showLocationInputBar = false
                            showDropdown = false
                            onUpdateLocationFilter?.invoke(customLocationDraft)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoutePilotNavy),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("apply_specified_location_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Set Area",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Google Maps-Style Nearby Categories Scrollable Bar
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

        // Map + Red Markers + Optional Places List + Bottom Tapped Marker Place Details Sheet
        Box(modifier = Modifier.weight(1f)) {
            RoutePilotMapView(
                currentLocation = currentLocation,
                destination = selectedDestination,
                primaryRoute = null,
                hazards = activeHazards,
                nearbyPlaces = searchResults,
                searchCenterLocation = searchCenterLocation,
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

            // Top-Center Floating Red Markers Summary Pill & List Toggle
            if (searchResults.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .clickable { showDropdown = !showDropdown }
                        .testTag("red_markers_count_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = HazardRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${searchResults.size} Red Markers",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = HazardRed,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Toggle Places List",
                            tint = RoutePilotBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expandable Places List / Autocomplete Suggestions Overlay
            if (showDropdown && searchResults.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .heightIn(max = 250.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Places Found (${searchResults.size}) • $searchCenterLabel",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155)
                                )
                            )
                            Text(
                                text = "View on Map",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RoutePilotBlue
                                ),
                                modifier = Modifier.clickable { showDropdown = false }
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

            // Bottom Place Details Sheet (updates whenever user taps any Red Marker on the map)
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
                    // Quick horizontal strip of nearby red markers so user can also tap between markers
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

                    // Place Details Header: Red Marker Icon + Name + Category + Distance + Address + Coordinates
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
                                    "Coordinates: %.4f, %.4f • Tap any red pin on map for details",
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

                    // Action Buttons: "Get Route / Confirm Destination" + "All Places"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onConfirmDestination,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                            modifier = Modifier
                                .weight(1f)
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

                        OutlinedButton(
                            onClick = { showDropdown = !showDropdown },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, RoutePilotBlue),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("toggle_nearby_list_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                tint = RoutePilotBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "List (${searchResults.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoutePilotBlue
                            )
                        }
                    }
                }
            }
        }
    }
}
