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
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.OperatingMode
import com.example.domain.model.User
import com.example.domain.routing.GeoUtils
import com.example.ui.components.OperatingModeBadge
import com.example.ui.components.RoutePilotBottomBar
import com.example.ui.theme.HazardOrange
import com.example.ui.theme.HazardRed
import com.example.ui.theme.RoutePilotBlue
import com.example.ui.theme.RoutePilotBlueLight
import com.example.ui.theme.RoutePilotNavy
import com.example.ui.theme.SafeRouteGreen
import com.example.ui.theme.SurfaceBackground
import kotlinx.coroutines.launch

/**
 * SCREEN 3 — HOME / DESTINATION SCREEN
 * Matches Screen 3 of the reference design:
 * - Hamburger menu + RoutePilot title + Profile icon
 * - "Where do you want to go?" search bar with mic icon
 * - Quick destinations: Home, Work, Hospital
 * - Recent Destinations list (District Hospital 12 km, Bus Stand 8.5 km, Railway Station 15 km, College 5.8 km)
 * - Bottom navigation: Navigate, History, Settings
 */
@Composable
fun HomeScreen(
    currentUser: User?,
    operatingMode: OperatingMode,
    useKilometers: Boolean,
    recentDestinations: List<Destination>,
    activeHazards: List<Hazard>,
    onToggleOperatingMode: () -> Unit,
    onOpenDestinationSearch: () -> Unit,
    onSelectQuickCategory: (String) -> Unit,
    onSelectRecentDestination: (Destination) -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                modifier = Modifier.width(290.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RoutePilotNavy)
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(RoutePilotBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Driver Profile",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currentUser?.name ?: "Guest Driver",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = currentUser?.email ?: "RoutePilot Driver APK",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_navigate)) },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.title_select_destination)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenDestinationSearch()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_history)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateHistory()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_settings)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateSettings()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_profile)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateProfile()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = stringResource(R.string.setting_operating_mode),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OperatingModeBadge(
                        mode = operatingMode,
                        onToggleMode = onToggleOperatingMode
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = SurfaceBackground,
            bottomBar = {
                RoutePilotBottomBar(
                    currentRoute = "home",
                    onNavigateTab = { },
                    onHistoryTab = onNavigateHistory,
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
                // Top Bar: Hamburger Menu | RoutePilot | Mode Badge + Profile Icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.testTag("home_hamburger_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Menu",
                            tint = Color(0xFF0F172A)
                        )
                    }

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = RoutePilotNavy
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OperatingModeBadge(
                            mode = operatingMode,
                            onToggleMode = onToggleOperatingMode
                        )

                        Surface(
                            shape = CircleShape,
                            color = RoutePilotBlueLight,
                            modifier = Modifier
                                .size(38.dp)
                                .clickable(onClick = onNavigateProfile)
                                .testTag("home_profile_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = stringResource(R.string.nav_profile),
                                    tint = RoutePilotBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Main Search Field Card: "Where do you want to go?"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable(onClick = onOpenDestinationSearch)
                        .testTag("home_search_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.search_placeholder_home),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Search",
                            tint = Color(0xFF475569)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Destinations Row: Home | Work | Hospital
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickDestinationChip(
                        label = stringResource(R.string.quick_dest_home),
                        icon = Icons.Default.Home,
                        iconTint = RoutePilotBlue,
                        onClick = { onSelectQuickCategory("HOME") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_chip_home")
                    )
                    QuickDestinationChip(
                        label = stringResource(R.string.quick_dest_work),
                        icon = Icons.Default.Work,
                        iconTint = RoutePilotNavy,
                        onClick = { onSelectQuickCategory("WORK") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_chip_work")
                    )
                    QuickDestinationChip(
                        label = stringResource(R.string.quick_dest_hospital),
                        icon = Icons.Default.AddBox,
                        iconTint = HazardRed,
                        onClick = { onSelectQuickCategory("HOSPITAL") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_chip_hospital")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Corridor Safety Status Strip
                val effectiveHazardsCount = activeHazards.count { it.isEffectiveHazard }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (effectiveHazardsCount > 0) Color(0xFFFFF3E0) else Color(0xFFE8F8EE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (effectiveHazardsCount > 0) HazardOrange else SafeRouteGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (effectiveHazardsCount > 0) {
                                stringResource(R.string.active_hazards_banner_count, effectiveHazardsCount)
                            } else {
                                stringResource(R.string.no_active_hazards_banner)
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (effectiveHazardsCount > 0) Color(0xFFE65100) else SafeRouteGreen,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Recent Destinations Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.section_recent_destinations),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    )
                    TextButton(onClick = onOpenDestinationSearch) {
                        Text(
                            text = stringResource(R.string.action_see_all),
                            color = RoutePilotBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Recent Destinations List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentDestinations, key = { it.id }) { destination ->
                        RecentDestinationItemCard(
                            destination = destination,
                            useKilometers = useKilometers,
                            onClick = { onSelectRecentDestination(destination) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickDestinationChip(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    fontSize = 13.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecentDestinationItemCard(
    destination: Destination,
    useKilometers: Boolean,
    onClick: () -> Unit
) {
    val isHospital = destination.category.equals("Hospital", ignoreCase = true) ||
        destination.name.contains("Hospital", ignoreCase = true)
    val isBus = destination.category.equals("Transit", ignoreCase = true) ||
        destination.name.contains("Bus", ignoreCase = true)
    val isTrain = destination.category.equals("Railway", ignoreCase = true) ||
        destination.name.contains("Station", ignoreCase = true)
    val isCollege = destination.category.equals("Education", ignoreCase = true) ||
        destination.name.contains("College", ignoreCase = true)

    val icon = when {
        isHospital -> Icons.Default.LocationOn
        isBus -> Icons.Default.DirectionsBus
        isTrain -> Icons.Default.Train
        isCollege -> Icons.Default.School
        else -> Icons.Default.LocationOn
    }

    val iconBg = if (isHospital) Color(0xFFFFEBEE) else RoutePilotBlueLight
    val iconColor = if (isHospital) HazardRed else RoutePilotBlue

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("recent_dest_${destination.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = destination.name,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destination.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                val distText = destination.distanceFromUserKm?.let {
                    GeoUtils.formatDistanceKm(it, useKilometers)
                } ?: destination.address
                Text(
                    text = distText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
