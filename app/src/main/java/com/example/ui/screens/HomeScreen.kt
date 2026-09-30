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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Destination
import com.example.domain.model.Hazard
import com.example.domain.model.OperatingMode
import com.example.domain.model.User
import com.example.domain.routing.GeoUtils
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
 * - Hamburger menu + RoutePilot title + Profile icon
 * - "Where do you want to go?" search bar with mic icon
 * - Quick destinations: Home, Work, Hospital
 * - Recent Destinations preview (shows visited destinations on Home screen, "See All" opens all visited destinations, and supports individual or bulk deletion)
 * - Bottom navigation: Navigate, History, Settings
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onDeleteRecentDestination: (String) -> Unit = {},
    onClearAllRecentDestinations: () -> Unit = {},
    onRestoreRecentDestinations: () -> Unit = {},
    onNavigateHistory: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAllRecentSheet by remember { mutableStateOf(false) }
    var isInlineSeeAllExpanded by remember { mutableStateOf(false) }

    // By default show 4 visited destinations on Home Screen; if expanded or in See All sheet, show all visited destinations
    val displayedHomeDestinations = if (isInlineSeeAllExpanded) {
        recentDestinations
    } else {
        recentDestinations.take(4)
    }

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
                        .windowInsetsPadding(WindowInsets.statusBars)
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
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currentUser?.name
                            ?.replace(Regex("(?i)driver"), "User")
                            ?.trim()
                            ?.ifBlank { "RoutePilot User" }
                            ?: "RoutePilot User",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    val displayEmail = currentUser?.email?.trim().orEmpty()
                    if (displayEmail.isNotBlank()) {
                        Text(
                            text = displayEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1)
                        )
                    }
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
                    .padding(innerPadding)
                    .padding(horizontal = 18.dp)
            ) {
                // Top Bar: Hamburger Menu | RoutePilot | Profile Icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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

                Spacer(modifier = Modifier.height(4.dp))

                // Main Search Field Card: "Where do you want to go?"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
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

                Spacer(modifier = Modifier.height(14.dp))

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

                Spacer(modifier = Modifier.height(12.dp))

                // Live Corridor Safety Status Strip
                val effectiveHazardsCount = activeHazards.count { it.isEffectiveHazard }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (effectiveHazardsCount > 0) Color(0xFFFFF3E0) else Color(0xFFE8F8EE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
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

                Spacer(modifier = Modifier.height(12.dp))

                // Recent Destinations Header + "See All" Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.section_recent_destinations),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                        if (recentDestinations.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = RoutePilotBlueLight
                            ) {
                                Text(
                                    text = "${ displayedHomeDestinations.size }/${ recentDestinations.size }",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = RoutePilotBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (recentDestinations.size > 4) {
                            TextButton(
                                onClick = { isInlineSeeAllExpanded = !isInlineSeeAllExpanded },
                                modifier = Modifier.testTag("home_inline_toggle_recent")
                            ) {
                                Text(
                                    text = if (isInlineSeeAllExpanded) "Show Less" else "Expand",
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                isInlineSeeAllExpanded = true
                                showAllRecentSheet = true
                            },
                            modifier = Modifier.testTag("home_see_all_recent_button")
                        ) {
                            Text(
                                text = if (recentDestinations.isNotEmpty()) {
                                    "${stringResource(R.string.action_see_all)} (${recentDestinations.size})"
                                } else {
                                    stringResource(R.string.action_see_all)
                                },
                                color = RoutePilotBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Recent Visited Destinations List on Home Screen
                if (recentDestinations.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Recent Visited Destinations",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Places you visit or search will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onRestoreRecentDestinations,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlueLight),
                                    modifier = Modifier.testTag("restore_recent_destinations_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = null,
                                        tint = RoutePilotBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Restore Visited",
                                        color = RoutePilotBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = onOpenDestinationSearch,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RoutePilotBlue)
                                ) {
                                    Text(
                                        text = "Search Place",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recent_destinations_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedHomeDestinations, key = { it.id }) { destination ->
                            RecentDestinationItemCard(
                                destination = destination,
                                useKilometers = useKilometers,
                                onClick = { onSelectRecentDestination(destination) },
                                onDelete = { onDeleteRecentDestination(destination.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // "See All" Modal Bottom Sheet showing ALL visited destinations with individual & Clear All delete
    if (showAllRecentSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAllRecentSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceBackground,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 380.dp, max = 640.dp)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "All Visited Destinations (${recentDestinations.size})",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = RoutePilotNavy
                            )
                        )
                        Text(
                            text = "Tap a destination to navigate, or tap the trash icon to delete",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = Color(0xFF64748B)
                        )
                    }

                    IconButton(
                        onClick = { showAllRecentSheet = false },
                        modifier = Modifier.testTag("close_see_all_recent_sheet")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF475569)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (recentDestinations.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAllRecentDestinations,
                            modifier = Modifier.testTag("clear_all_recent_destinations_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = HazardRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Delete All Recent",
                                color = HazardRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        TextButton(
                            onClick = onRestoreRecentDestinations,
                            modifier = Modifier.testTag("sheet_restore_recent_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = RoutePilotBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Restore Visited Destinations",
                                color = RoutePilotBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            showAllRecentSheet = false
                            onOpenDestinationSearch()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = RoutePilotBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Search New Place",
                            color = RoutePilotBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(10.dp))

                if (recentDestinations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All recent destinations have been deleted.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .testTag("see_all_recent_destinations_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentDestinations, key = { "all_${it.id}" }) { destination ->
                            RecentDestinationItemCard(
                                destination = destination,
                                useKilometers = useKilometers,
                                onClick = {
                                    showAllRecentSheet = false
                                    onSelectRecentDestination(destination)
                                },
                                onDelete = { onDeleteRecentDestination(destination.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
    onClick: () -> Unit,
    onDelete: () -> Unit
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
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

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = destination.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE8F8EE)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SafeRouteGreen,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Visited",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = SafeRouteGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val distPart = destination.distanceFromUserKm?.let {
                    "${GeoUtils.formatDistanceKm(it, useKilometers)} • "
                } ?: ""
                Text(
                    text = "$distPart${destination.address}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("delete_recent_${destination.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete ${destination.name}",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}
