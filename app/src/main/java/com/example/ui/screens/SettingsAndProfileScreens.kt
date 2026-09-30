package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Journey
import com.example.domain.model.User
import com.example.domain.repository.DriverPreferences
import com.example.domain.routing.GeoUtils
import com.example.ui.components.RoutePilotBottomBar
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.RoutePilotBlueLight
import com.example.ui.theme.RoutePilotNavy
import com.example.ui.theme.SafeRouteGreen
import com.example.ui.theme.SurfaceBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * PROFESSIONAL JOURNEY HISTORY SCREEN
 * Opened from the Sidebar or Bottom Navigation Bar:
 * - Header with Back to Home button, Title & Subtitle, and Clear History pill button
 * - 3-Card Summary Analytics Strip (Total Trips, Total Distance, Hazards Avoided)
 * - Interactive Filter Pills (All Trips, Safer Route Used, Standard Route)
 * - Rich Timeline Journey Cards with Origin -> Destination path, date/time, distance, duration, and safety badge
 */
@Composable
fun HistoryScreen(
    journeys: List<Journey>,
    useKilometers: Boolean,
    onClearHistory: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredJourneys = remember(journeys, selectedFilter) {
        when (selectedFilter) {
            "DIVERTED" -> journeys.filter { it.status == "SAFELY_DIVERTED" || it.hazardsAvoidedCount > 0 }
            "STANDARD" -> journeys.filter { it.status != "SAFELY_DIVERTED" && it.hazardsAvoidedCount == 0 }
            else -> journeys
        }
    }

    val totalDistanceKm = remember(journeys) { journeys.sumOf { it.distanceKm } }
    val totalHazardsAvoided = remember(journeys) {
        journeys.sumOf { max(if (it.status == "SAFELY_DIVERTED") 1 else 0, it.hazardsAvoidedCount) }
    }

    Scaffold(
        containerColor = SurfaceBackground,
        bottomBar = {
            RoutePilotBottomBar(
                currentRoute = "history",
                onNavigateTab = onNavigateHome,
                onHistoryTab = { },
                onSettingsTab = onNavigateSettings
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(innerPadding)
                .padding(horizontal = 18.dp)
        ) {
            // Top Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(onClick = onNavigateHome)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RoutePilotNavy,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.title_journey_history),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = RoutePilotNavy,
                                fontSize = 22.sp
                            )
                        )
                        Text(
                            text = "Completed trips & hazard safety log",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                if (journeys.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier
                            .clickable(onClick = onClearHistory)
                            .testTag("clear_history_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = HazardRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_clear_history),
                                color = HazardRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 3-Card Summary Analytics Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HistoryStatSummaryCard(
                    value = "${journeys.size}",
                    label = "Total Trips",
                    accentColor = RoutePilotBlue,
                    bgColor = Color(0xFFEFF6FF),
                    modifier = Modifier.weight(1f)
                )
                HistoryStatSummaryCard(
                    value = GeoUtils.formatDistanceKm(totalDistanceKm, useKilometers),
                    label = "Total Distance",
                    accentColor = Color(0xFF0284C7),
                    bgColor = Color(0xFFE0F2FE),
                    modifier = Modifier.weight(1f)
                )
                HistoryStatSummaryCard(
                    value = "$totalHazardsAvoided",
                    label = "Hazards Avoided",
                    accentColor = SafeRouteGreen,
                    bgColor = Color(0xFFECFDF5),
                    modifier = Modifier.weight(1f)
                )
            }

            if (journeys.isNotEmpty()) {
                // Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryFilterChip(
                        label = "All Trips (${journeys.size})",
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" }
                    )
                    HistoryFilterChip(
                        label = "Safer Route Used",
                        selected = selectedFilter == "DIVERTED",
                        onClick = { selectedFilter = "DIVERTED" }
                    )
                    HistoryFilterChip(
                        label = "Direct Route",
                        selected = selectedFilter == "STANDARD",
                        onClick = { selectedFilter = "STANDARD" }
                    )
                }
            }

            if (filteredJourneys.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(RoutePilotBlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = RoutePilotBlue,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.empty_history_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.empty_history_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onNavigateHome,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue),
                                modifier = Modifier.height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Start a New Journey",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredJourneys, key = { it.id }) { journey ->
                        val isSafelyDiverted = journey.status == "SAFELY_DIVERTED" || journey.hazardsAvoidedCount > 0
                        val avoidedCount = max(if (isSafelyDiverted) 1 else 0, journey.hazardsAvoidedCount)

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Top row: Date & Safety Status Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateFormatter.format(Date(journey.completedAt)),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (isSafelyDiverted) Color(0xFFDCFCE7) else Color(0xFFEFF6FF)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSafelyDiverted) Icons.Default.Shield else Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = if (isSafelyDiverted) SafeRouteGreen else RoutePilotBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isSafelyDiverted) {
                                                    "Safer Route • $avoidedCount Hazard Avoided"
                                                } else {
                                                    "Completed Safely"
                                                },
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = if (isSafelyDiverted) Color(0xFF15803D) else RoutePilotBlue,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Origin -> Destination Timeline
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(end = 12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(SafeRouteGreen)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(22.dp)
                                                .background(Color(0xFFCBD5E1))
                                        )
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = HazardRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = journey.sourceName.ifBlank { "Current Location" },
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color(0xFF64748B),
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = journey.destinationName,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF0F172A),
                                                fontSize = 16.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (journey.destinationAddress.isNotBlank()) {
                                            Text(
                                                text = journey.destinationAddress,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = Color(0xFF64748B),
                                                    fontSize = 12.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = Color(0xFFF1F5F9)
                                )

                                // Bottom Metrics Strip
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Route,
                                            contentDescription = null,
                                            tint = RoutePilotBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = GeoUtils.formatDistanceKm(journey.distanceKm, useKilometers),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF0F172A)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = RoutePilotBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = GeoUtils.formatDurationMinutes(journey.durationMinutes),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF0F172A)
                                            )
                                        )
                                    }

                                    Text(
                                        text = "Bridge B1 Protected",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color(0xFF475569),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStatSummaryCard(
    value: String,
    label: String,
    accentColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    fontSize = 17.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HistoryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) RoutePilotNavy else Color.White,
        border = BorderStroke(1.dp, if (selected) RoutePilotNavy else Color(0xFFCBD5E1)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (selected) Color.White else Color(0xFF334155),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/**
 * PROFESSIONAL SETTINGS SCREEN
 * Opened from the Sidebar or Bottom Navigation Bar:
 * - Header with Back button, Settings title & subtitle, and Reset Defaults shortcut
 * - Section 1: App Language & Voice Localization (English / Hindi)
 * - Section 2: Safety Alerts, Voice Guidance & Sound Controls (with rich descriptions)
 * - Section 3: Distance & Map Measurement Units (KM / Miles)
 * - Section 4: Privacy, Security & About RoutePilot
 */
@Composable
fun SettingsScreen(
    preferences: DriverPreferences,
    onLanguageChange: (String) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onNavigationVoiceChange: (Boolean) -> Unit,
    onAlertSoundChange: (Boolean) -> Unit,
    onUnitsChange: (Boolean) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateHistory: () -> Unit
) {
    Scaffold(
        containerColor = SurfaceBackground,
        bottomBar = {
            RoutePilotBottomBar(
                currentRoute = "settings",
                onNavigateTab = onNavigateHome,
                onHistoryTab = onNavigateHistory,
                onSettingsTab = { }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(onClick = onNavigateHome)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RoutePilotNavy,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.title_settings),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = RoutePilotNavy,
                                fontSize = 22.sp
                            )
                        )
                        Text(
                            text = "Navigation, voice & safety preferences",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                TextButton(
                    onClick = {
                        onLanguageChange("en")
                        onNotificationsChange(true)
                        onNavigationVoiceChange(true)
                        onAlertSoundChange(true)
                        onUnitsChange(true)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = RoutePilotBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset",
                        color = RoutePilotBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // 1. Language Selection Card (English / Hindi)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(RoutePilotBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = RoutePilotBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.setting_language),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                )
                            )
                            Text(
                                text = "Choose interface & voice prompt language",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SelectablePillOption(
                            label = stringResource(R.string.lang_english),
                            selected = preferences.languageCode == "en",
                            onClick = { onLanguageChange("en") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lang_option_en")
                        )
                        SelectablePillOption(
                            label = stringResource(R.string.lang_hindi),
                            selected = preferences.languageCode == "hi",
                            onClick = { onLanguageChange("hi") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lang_option_hi")
                        )
                    }
                }
            }

            // 2. Safety Notifications, Voice Guidance, Alert Sound, and Distance Units
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NAVIGATION & HAZARD ALERTS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.7.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsToggleRow(
                        icon = Icons.Default.Notifications,
                        iconTint = Color(0xFFEA580C),
                        iconBg = Color(0xFFFFEDD5),
                        title = stringResource(R.string.setting_notifications),
                        subtitle = "Instant popup & banner alerts for road hazards ahead",
                        checked = preferences.notificationsEnabled,
                        onCheckedChange = onNotificationsChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFF1F5F9))

                    SettingsToggleRow(
                        icon = Icons.Default.RecordVoiceOver,
                        iconTint = RoutePilotBlue,
                        iconBg = RoutePilotBlueLight,
                        title = stringResource(R.string.setting_navigation_voice),
                        subtitle = "Spoken turn-by-turn guidance & safer route announcements",
                        checked = preferences.navigationVoiceEnabled,
                        onCheckedChange = onNavigationVoiceChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFF1F5F9))

                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        iconTint = Color(0xFF7C3AED),
                        iconBg = Color(0xFFEDE9FE),
                        title = stringResource(R.string.setting_sound),
                        subtitle = "Warning chime & haptic vibration on critical bridge alerts",
                        checked = preferences.alertSoundEnabled,
                        onCheckedChange = onAlertSoundChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFF1F5F9))

                    SettingsToggleRow(
                        icon = Icons.Default.Straighten,
                        iconTint = SafeRouteGreen,
                        iconBg = Color(0xFFDCFCE7),
                        title = "${stringResource(R.string.setting_units)}: ${if (preferences.useKilometers) stringResource(R.string.units_km) else stringResource(R.string.units_miles)}",
                        subtitle = "Switch between metric (km/m) and imperial (mi/ft) units",
                        checked = preferences.useKilometers,
                        onCheckedChange = onUnitsChange
                    )
                }
            }

            // 3. Privacy, Security & About RoutePilot
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = SafeRouteGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.setting_privacy),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                )
                            )
                            Text(
                                text = "Encrypted GPS & Zero Background Tracking",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = SafeRouteGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Location data is used strictly during active navigation, nearby place searches, and real-time road hazard checks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = Color(0xFFF1F5F9))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RoutePilotBlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = RoutePilotBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.setting_about),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                )
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFEFF6FF)
                        ) {
                            Text(
                                text = "v2.4.0",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = RoutePilotBlue,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF475569)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SelectablePillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) RoutePilotBlue else Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, if (selected) RoutePilotBlue else Color(0xFFE2E8F0)),
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = if (selected) Color.White else Color(0xFF334155),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RoutePilotBlue
            )
        )
    }
}

/**
 * PROFESSIONAL PROFILE & ACCOUNT SCREEN
 * Opened from the Sidebar or Top Profile Avatar:
 * - Executive Gradient Hero Banner with Initials Avatar, Verified Badge, Name & Email
 * - 3-Card Safety & Trip Metrics Row (Recorded Journeys, Safety Rating, Protection Status)
 * - Account & Emergency Details Card (Account Status, Registered Email, Automatic Rerouting, Highway Helpline)
 * - Prominent Sign Out Button
 */
@Composable
fun ProfileScreen(
    user: User?,
    completedTripsCount: Int,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val cleanUserName = user?.name
        ?.replace(Regex("(?i)driver"), "User")
        ?.trim()
        ?.ifBlank { "RoutePilot User" }
        ?: "RoutePilot User"
    val cleanUserEmail = user?.email
        ?.replace(Regex("(?i)driver"), "user")
        ?: "user@routepilot.in"
    val initials = cleanUserName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "RP" }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onBack)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = RoutePilotNavy
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.title_profile),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = RoutePilotNavy
                    ),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(40.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Executive Gradient Hero Profile Card
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    RoutePilotNavy,
                                    Color(0xFF1E3A8A),
                                    RoutePilotBlue
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.size(84.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.18f),
                                border = BorderStroke(2.5.dp, Color.White),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(SafeRouteGreen)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = cleanUserName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = cleanUserEmail,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFDBEAFE),
                                fontSize = 14.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF22C55E).copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Account Status: Verified Account",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3-Column Profile Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HistoryStatSummaryCard(
                    value = "$completedTripsCount",
                    label = "Recorded Trips",
                    accentColor = RoutePilotBlue,
                    bgColor = Color(0xFFEFF6FF),
                    modifier = Modifier.weight(1f)
                )
                HistoryStatSummaryCard(
                    value = "100%",
                    label = "Safety Score",
                    accentColor = SafeRouteGreen,
                    bgColor = Color(0xFFECFDF5),
                    modifier = Modifier.weight(1f)
                )
                HistoryStatSummaryCard(
                    value = "Active",
                    label = "IoT Shield",
                    accentColor = Color(0xFF7C3AED),
                    bgColor = Color(0xFFEDE9FE),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Detailed Account & Safety Information Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "ACCOUNT & SAFETY OVERVIEW",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.7.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ProfileInfoDetailRow(
                        icon = Icons.Default.Person,
                        iconTint = RoutePilotBlue,
                        iconBg = RoutePilotBlueLight,
                        label = "Recorded Journeys",
                        value = "Recorded Journeys: $completedTripsCount"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF1F5F9))

                    ProfileInfoDetailRow(
                        icon = Icons.Default.Email,
                        iconTint = Color(0xFF0284C7),
                        iconBg = Color(0xFFE0F2FE),
                        label = "Registered Email",
                        value = cleanUserEmail
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF1F5F9))

                    ProfileInfoDetailRow(
                        icon = Icons.Default.Shield,
                        iconTint = SafeRouteGreen,
                        iconBg = Color(0xFFDCFCE7),
                        label = "Hazard Protection",
                        value = "Automatic Safer Rerouting Enabled"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF1F5F9))

                    ProfileInfoDetailRow(
                        icon = Icons.Default.PhoneInTalk,
                        iconTint = Color(0xFFEA580C),
                        iconBg = Color(0xFFFFEDD5),
                        label = "24x7 Highway & SOS Helpline",
                        value = "NHAI Helpline: 1033 • Emergency: 112"
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onLogout,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HazardRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("profile_logout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_logout),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProfileInfoDetailRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 14.sp
                )
            )
        }
    }
}
