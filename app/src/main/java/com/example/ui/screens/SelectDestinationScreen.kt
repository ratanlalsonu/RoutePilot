package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.SurfaceBackground

/**
 * SCREEN 4 — SELECT DESTINATION SCREEN
 * Matches Screen 4 of the reference UI:
 * - Top bar with Back button and "Select Destination"
 * - Real-time Places Autocomplete search bar ("Search location here")
 * - Real Google Map with current location & selected destination red pin marker
 * - Bottom card showing Selected Location (Place Name + Address) and "Confirm Destination" button
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
    onSearchQueryChange: (String) -> Unit,
    onSelectPlaceSuggestion: (Destination) -> Unit,
    onMapClickLocation: (Double, Double) -> Unit,
    onConfirmDestination: () -> Unit,
    onBack: () -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }

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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("select_dest_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF0F172A)
                )
            }
            Text(
                text = stringResource(R.string.title_select_destination),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                ),
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Search Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    onSearchQueryChange(it)
                    showDropdown = true
                },
                placeholder = { Text(stringResource(R.string.search_placeholder_destination)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF64748B)
                    )
                },
                trailingIcon = {
                    if (isSearchingPlaces) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = RoutePilotBlue
                        )
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            showDropdown = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice search",
                            tint = Color(0xFF475569)
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
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

        // Map + Autocomplete Dropdown + Bottom Selected Location Sheet
        Box(modifier = Modifier.weight(1f)) {
            RoutePilotMapView(
                currentLocation = currentLocation,
                destination = selectedDestination,
                primaryRoute = null,
                hazards = activeHazards,
                isNavigationMode = false,
                isMapsApiKeyConfigured = isMapsApiKeyConfigured,
                hasLocationPermission = hasLocationPermission,
                showNavigationControls = false,
                onMapClick = { lat, lng ->
                    showDropdown = false
                    onMapClickLocation(lat, lng)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Autocomplete Suggestions Overlay
            if (showDropdown && searchResults.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .heightIn(max = 260.dp)
                        .align(Alignment.TopCenter)
                ) {
                    LazyColumn {
                        items(searchResults, key = { it.id }) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showDropdown = false
                                        onSelectPlaceSuggestion(suggestion)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("place_suggestion_${suggestion.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = HazardRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = suggestion.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        ),
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = suggestion.address,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                        color = Color(0xFF64748B),
                                        maxLines = 1
                                    )
                                }
                                suggestion.distanceFromUserKm?.let { distKm ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = GeoUtils.formatDistanceKm(distKm, useKilometers),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = RoutePilotBlue
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }

            // Bottom Selected Location Sheet (matching Screen 4)
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.label_selected_location),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = selectedDestination.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            )
                            Text(
                                text = selectedDestination.address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onConfirmDestination,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("confirm_destination_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_confirm_destination),
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
