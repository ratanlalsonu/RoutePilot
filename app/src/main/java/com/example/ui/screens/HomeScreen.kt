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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Brush
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
            val cleanUserName = currentUser?.name
                ?.replace(Regex("(?i)driver"), "User")
                ?.trim()
                ?.ifBlank { "RoutePilot User" }
                ?: "RoutePilot User"
            val cleanUserEmail = currentUser?.email
                ?.replace(Regex("(?i)driver"), "user")
                ?: "user@routepilot.in"
            val userInitials = cleanUserName
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifBlank { "RP" }

            ModalDrawerSheet(
                drawerContainerColor = Color(0xFFF8FAFC),
                drawerShape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
                modifier = Modifier
                    .width(318.dp)
                    .testTag("home_navigation_drawer")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Executive Gradient Profile & Live Telemetry Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        RoutePilotNavy,
                                        Color(0xFF163B73),
                                        Color(0xFF1D4ED8)
                                    )
                                )
                            )
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                // Avatar with Initials & Online Indicator
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clickable {
                                            scope.launch { drawerState.close() }
                                            onNavigateProfile()
                                        }
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.16f),
                                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = userInitials,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 21.sp
                                                )
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(SafeRouteGreen)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF22C55E).copy(alpha = 0.22f),
                                        border = BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.55f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VerifiedUser,
                                                contentDescription = null,
                                                tint = Color(0xFF4ADE80),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Verified",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { scope.launch { drawerState.close() } },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.14f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Menu",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = cleanUserName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cleanUserEmail,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFDBEAFE),
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // 3-Column Quick Stats Strip inside Drawer Header
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DrawerHeaderStatItem(
                                        value = "${recentDestinations.size}",
                                        label = "Saved Places"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(24.dp)
                                            .background(Color.White.copy(alpha = 0.22f))
                                    )
                                    DrawerHeaderStatItem(
                                        value = "Active",
                                        label = "Hazard Shield"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(24.dp)
                                            .background(Color.White.copy(alpha = 0.22f))
                                    )
                                    DrawerHeaderStatItem(
                                        value = if (useKilometers) "KM" else "MI",
                                        label = "Distance Unit"
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Section: Navigation & Trips
                    Text(
                        text = "NAVIGATION & TRIPS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )

                    DrawerMenuSectionCard(
                        icon = Icons.Default.Navigation,
                        iconTint = RoutePilotBlue,
                        iconBg = RoutePilotBlueLight,
                        title = stringResource(R.string.nav_navigate),
                        subtitle = "Live map, quick destinations & safety radar",
                        badgeText = "Active",
                        selected = true,
                        onClick = { scope.launch { drawerState.close() } }
                    )

                    DrawerMenuSectionCard(
                        icon = Icons.Default.Search,
                        iconTint = Color(0xFF0284C7),
                        iconBg = Color(0xFFE0F2FE),
                        title = stringResource(R.string.title_select_destination),
                        subtitle = "Search any place, address or nearby category",
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenDestinationSearch()
                        }
                    )

                    DrawerMenuSectionCard(
                        icon = Icons.Default.History,
                        iconTint = Color(0xFF7C3AED),
                        iconBg = Color(0xFFEDE9FE),
                        title = stringResource(R.string.nav_history),
                        subtitle = "View completed trips, distance & avoided hazards",
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateHistory()
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Section: Quick Nearby Places (1-Tap Search from Sidebar)
                    Text(
                        text = "QUICK NEARBY PLACES",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DrawerQuickNearbyChip(
                            icon = Icons.Default.AddBox,
                            label = "Hospital",
                            tint = Color(0xFFDC2626),
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSelectQuickCategory("HOSPITAL")
                            }
                        )
                        DrawerQuickNearbyChip(
                            icon = Icons.Default.LocalGasStation,
                            label = "Petrol Pump",
                            tint = Color(0xFFD97706),
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSelectQuickCategory("PETROL_PUMP")
                            }
                        )
                        DrawerQuickNearbyChip(
                            icon = Icons.Default.Build,
                            label = "Service Centre",
                            tint = RoutePilotBlue,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSelectQuickCategory("SERVICE_CENTRE")
                            }
                        )
                        DrawerQuickNearbyChip(
                            icon = Icons.Default.School,
                            label = "School",
                            tint = Color(0xFF7C3AED),
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSelectQuickCategory("SCHOOL")
                            }
                        )
                        DrawerQuickNearbyChip(
                            icon = Icons.Default.Restaurant,
                            label = "Restaurant",
                            tint = SafeRouteGreen,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSelectQuickCategory("RESTAURANT")
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Section: Account & Preferences
                    Text(
                        text = "ACCOUNT & PREFERENCES",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )

                    DrawerMenuSectionCard(
                        icon = Icons.Default.Person,
                        iconTint = SafeRouteGreen,
                        iconBg = Color(0xFFDCFCE7),
                        title = stringResource(R.string.nav_profile),
                        subtitle = "Account status, safety stats & sign out",
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateProfile()
                        }
                    )

                    DrawerMenuSectionCard(
                        icon = Icons.Default.Settings,
                        iconTint = Color(0xFFEA580C),
                        iconBg = Color(0xFFFFEDD5),
                        title = stringResource(R.string.nav_settings),
                        subtitle = "Language (EN/HI), voice guidance & units",
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateSettings()
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. IoT Safety Shield Status Card at Bottom of Sidebar
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SafeRouteGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "IoT Hazard Protection Active",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color(0xFF065F46),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                                Text(
                                    text = "Real-time bridge & road monitoring enabled",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF047857),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
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

                Spacer(modifier = Modifier.height(10.dp))

                // Google Maps-Style Nearby Places Row: Service Centre | School | Petrol Pump | Restaurant
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("Service Centre", Icons.Default.Build, Color(0xFF0284C7)),
                        Triple("School", Icons.Default.School, Color(0xFF7C3AED)),
                        Triple("Petrol Pump", Icons.Default.LocalGasStation, Color(0xFFEA580C)),
                        Triple("Restaurant", Icons.Default.Restaurant, Color(0xFFD97706))
                    ).forEach { (catLabel, catIcon, catTint) ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .clickable { onSelectQuickCategory(catLabel) }
                                .testTag("home_nearby_chip_${catLabel.lowercase().replace(" ", "_")}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = catIcon,
                                    contentDescription = null,
                                    tint = catTint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = catLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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

@Composable
private fun DrawerHeaderStatItem(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun DrawerMenuSectionCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFFEFF6FF) else Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) RoutePilotBlue.copy(alpha = 0.35f) else Color(0xFFE2E8F0)
        ),
        shadowElevation = if (selected) 2.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) RoutePilotBlue else Color(0xFF0F172A),
                            fontSize = 15.sp
                        )
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = RoutePilotBlue
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (selected) RoutePilotBlue else Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DrawerQuickNearbyChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
    }
}
