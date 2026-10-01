package com.example.ui.navigation

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JourneyCompletedScreen
import com.example.ui.screens.LiveNavigationScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RoutePreviewScreen
import com.example.ui.screens.SelectDestinationScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.viewmodel.NavigationWorkflowState
import com.example.ui.viewmodel.RoutePilotViewModel
import java.util.Locale

object RoutePilotRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SELECT_DESTINATION = "select_destination"
    const val ROUTE_PREVIEW = "route_preview"
    const val LIVE_NAVIGATION = "live_navigation"
    const val JOURNEY_COMPLETED = "journey_completed"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
}

@Composable
fun RoutePilotAppRoot(
    viewModel: RoutePilotViewModel,
    navController: NavHostController = rememberNavController()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val baseContext = LocalContext.current
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current

    val localizedContext = remember(baseContext, uiState.preferences.languageCode) {
        createLocalizedContext(baseContext, uiState.preferences.languageCode)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshDeviceLocationStatus()
    }

    val compositionProviders = remember(localizedContext, activityResultRegistryOwner) {
        if (activityResultRegistryOwner != null) {
            arrayOf(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides activityResultRegistryOwner
            )
        } else {
            arrayOf(LocalContext provides localizedContext)
        }
    }

    CompositionLocalProvider(*compositionProviders) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = RoutePilotRoutes.SPLASH,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(RoutePilotRoutes.SPLASH) {
                    SplashScreen(
                        onSplashComplete = {
                            val nextState = viewModel.onSplashFinished()
                            val targetRoute = if (nextState == NavigationWorkflowState.HOME) {
                                RoutePilotRoutes.HOME
                            } else {
                                RoutePilotRoutes.LOGIN
                            }
                            navController.navigate(targetRoute) {
                                popUpTo(RoutePilotRoutes.SPLASH) { inclusive = true }
                            }
                        }
                    )
                }

                composable(RoutePilotRoutes.LOGIN) {
                    LoginScreen(
                        initialEmail = uiState.preferences.savedEmail,
                        initialRememberMe = uiState.preferences.rememberMe,
                        operatingMode = uiState.preferences.operatingMode,
                        authError = uiState.authError,
                        statusMessage = uiState.statusBannerMessage,
                        onToggleOperatingMode = { },
                        onLoginSubmit = { email, password, remember ->
                            viewModel.loginWithEmail(email, password, remember) {
                                navController.navigate(RoutePilotRoutes.HOME) {
                                    popUpTo(RoutePilotRoutes.LOGIN) { inclusive = true }
                                }
                            }
                        },
                        onSignUpSubmit = { name, email, password ->
                            viewModel.signUpWithEmail(name, email, password) {
                                navController.navigate(RoutePilotRoutes.HOME) {
                                    popUpTo(RoutePilotRoutes.LOGIN) { inclusive = true }
                                }
                            }
                        },
                        onContinueWithGoogle = { selectedEmail, selectedName, idToken ->
                            viewModel.continueWithGoogle(selectedEmail, selectedName, idToken) {
                                navController.navigate(RoutePilotRoutes.HOME) {
                                    popUpTo(RoutePilotRoutes.LOGIN) { inclusive = true }
                                }
                            }
                        },
                        onAuthError = { errorMsg ->
                            viewModel.reportAuthError(errorMsg)
                        },
                        onForgotPassword = { email, newPassword ->
                            viewModel.sendPasswordReset(email, newPassword)
                        }
                    )
                }

                composable(RoutePilotRoutes.HOME) {
                    LaunchedEffect(Unit) {
                        if (!uiState.hasLocationPermission) {
                            val perms = buildList {
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        }
                    }

                    HomeScreen(
                        currentUser = uiState.currentUser,
                        operatingMode = uiState.preferences.operatingMode,
                        useKilometers = uiState.preferences.useKilometers,
                        recentDestinations = uiState.recentDestinations,
                        activeHazards = uiState.activeHazards,
                        onToggleOperatingMode = { },
                        onOpenDestinationSearch = {
                            viewModel.openDestinationSearchAtCurrentLocation()
                            navController.navigate(RoutePilotRoutes.SELECT_DESTINATION)
                        },
                        onSelectQuickCategory = { category ->
                            viewModel.selectQuickCategoryDestination(category) {
                                navController.navigate(RoutePilotRoutes.SELECT_DESTINATION)
                            }
                        },
                        onSelectRecentDestination = { dest ->
                            viewModel.selectDestinationCandidate(dest)
                            navController.navigate(RoutePilotRoutes.SELECT_DESTINATION)
                        },
                        onDeleteRecentDestination = viewModel::deleteRecentDestination,
                        onClearAllRecentDestinations = viewModel::clearAllRecentDestinations,
                        onRestoreRecentDestinations = viewModel::restoreDefaultRecentDestinations,
                        onNavigateHistory = {
                            navController.navigate(RoutePilotRoutes.HISTORY) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateSettings = {
                            navController.navigate(RoutePilotRoutes.SETTINGS) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateProfile = {
                            navController.navigate(RoutePilotRoutes.PROFILE)
                        }
                    )
                }

                composable(RoutePilotRoutes.SELECT_DESTINATION) {
                    SelectDestinationScreen(
                        currentLocation = uiState.currentLocation,
                        selectedDestination = uiState.selectedDestination,
                        searchQuery = uiState.searchQuery,
                        searchResults = uiState.searchResults,
                        isSearchingPlaces = uiState.isSearchingPlaces,
                        activeHazards = uiState.activeHazards,
                        isMapsApiKeyConfigured = uiState.isMapsApiKeyConfigured,
                        hasLocationPermission = uiState.hasLocationPermission,
                        useKilometers = uiState.preferences.useKilometers,
                        searchCenterLocation = uiState.searchCenterLocation,
                        searchCenterLabel = uiState.searchCenterLabel,
                        selectedCategoryChip = uiState.selectedCategoryChip,
                        isMultiMarkerCategoryView = uiState.isMultiMarkerCategoryView,
                        fitAllMarkersTrigger = uiState.fitAllMarkersTrigger,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSearchSubmit = viewModel::submitPlaceSearch,
                        onSelectCategoryChip = viewModel::selectCategoryChip,
                        onShowAllMarkersOnMap = viewModel::showAllSearchMarkersOnMap,
                        onUseCurrentLocation = viewModel::openDestinationSearchAtCurrentLocation,
                        onSelectPlaceSuggestion = viewModel::focusOnSinglePlaceCandidate,
                        onMapClickLocation = viewModel::selectPointOnMap,
                        onMapViewportChanged = viewModel::onMapViewportChanged,
                        onConfirmDestination = {
                            viewModel.confirmDestinationAndCalculatePreview {
                                navController.navigate(RoutePilotRoutes.ROUTE_PREVIEW)
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(RoutePilotRoutes.ROUTE_PREVIEW) {
                    RoutePreviewScreen(
                        currentLocation = uiState.currentLocation,
                        destination = uiState.selectedDestination,
                        recommendedRoute = uiState.recommendedRoute,
                        alternateRoute = uiState.alternateRoute,
                        isUsingAlternate = uiState.isUsingAlternateInPreview,
                        activeHazards = uiState.activeHazards,
                        isMapsApiKeyConfigured = uiState.isMapsApiKeyConfigured,
                        hasLocationPermission = uiState.hasLocationPermission,
                        useKilometers = uiState.preferences.useKilometers,
                        onSelectRouteOption = viewModel::selectRouteOptionInPreview,
                        onChooseOtherPath = { viewModel.triggerAutomaticRerouting() },
                        onStartDriving = {
                            viewModel.startDrivingNavigation()
                            navController.navigate(RoutePilotRoutes.LIVE_NAVIGATION)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(RoutePilotRoutes.LIVE_NAVIGATION) {
                    LaunchedEffect(uiState.workflowState) {
                        if (uiState.workflowState == NavigationWorkflowState.ARRIVED) {
                            navController.navigate(RoutePilotRoutes.JOURNEY_COMPLETED) {
                                popUpTo(RoutePilotRoutes.HOME) { inclusive = false }
                            }
                        }
                    }

                    LiveNavigationScreen(
                        workflowState = uiState.workflowState,
                        currentLocation = uiState.currentLocation,
                        destination = uiState.selectedDestination,
                        activeRoute = uiState.activeRoute,
                        previousRouteBeforeDiversion = uiState.previousRouteBeforeDiversion,
                        activeHazards = uiState.activeHazards,
                        primaryAffectingHazard = uiState.primaryAffectingHazard,
                        remainingDistanceMeters = uiState.remainingDistanceMeters,
                        remainingEtaMinutes = uiState.remainingEtaMinutes,
                        remainingEtaSeconds = uiState.remainingEtaSeconds,
                        currentTurnDistanceMeters = uiState.currentTurnDistanceMeters,
                        currentTurnInstruction = uiState.currentTurnInstruction,
                        currentTurnManeuver = uiState.currentTurnManeuver,
                        recalculationProgress = uiState.recalculationProgress,
                        recalculationErrorMessage = uiState.recalculationErrorMessage,
                        isMapsApiKeyConfigured = uiState.isMapsApiKeyConfigured,
                        hasLocationPermission = uiState.hasLocationPermission,
                        useKilometers = uiState.preferences.useKilometers,
                        isVoiceMuted = uiState.isVoiceMutedInNav,
                        onToggleVoiceMute = viewModel::toggleVoiceMuteInNavigation,
                        onDismissHazardAlert = viewModel::dismissHazardAlertAndContinue,
                        onTriggerRerouteNow = { viewModel.triggerAutomaticRerouting() },
                        onCancelRecalculation = viewModel::cancelRecalculation,
                        onEndNavigation = {
                            viewModel.stopActiveNavigation()
                            navController.popBackStack(RoutePilotRoutes.HOME, inclusive = false)
                        }
                    )
                }

                composable(RoutePilotRoutes.JOURNEY_COMPLETED) {
                    JourneyCompletedScreen(
                        journey = uiState.lastCompletedJourney,
                        fallbackDestination = uiState.selectedDestination,
                        useKilometers = uiState.preferences.useKilometers,
                        onDone = {
                            viewModel.stopActiveNavigation()
                            navController.popBackStack(RoutePilotRoutes.HOME, inclusive = false)
                        }
                    )
                }

                composable(RoutePilotRoutes.HISTORY) {
                    HistoryScreen(
                        journeys = uiState.journeyHistory,
                        useKilometers = uiState.preferences.useKilometers,
                        onClearHistory = viewModel::clearJourneyHistory,
                        onNavigateHome = {
                            navController.popBackStack(RoutePilotRoutes.HOME, inclusive = false)
                        },
                        onNavigateSettings = {
                            navController.navigate(RoutePilotRoutes.SETTINGS) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(RoutePilotRoutes.SETTINGS) {
                    SettingsScreen(
                        preferences = uiState.preferences,
                        onLanguageChange = viewModel::setLanguage,
                        onNotificationsChange = viewModel::setNotificationsEnabled,
                        onNavigationVoiceChange = viewModel::setNavigationVoiceEnabled,
                        onAlertSoundChange = viewModel::setAlertSoundEnabled,
                        onUnitsChange = viewModel::setUseKilometers,
                        onNavigateHome = {
                            navController.popBackStack(RoutePilotRoutes.HOME, inclusive = false)
                        },
                        onNavigateHistory = {
                            navController.navigate(RoutePilotRoutes.HISTORY) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(RoutePilotRoutes.PROFILE) {
                    ProfileScreen(
                        user = uiState.currentUser,
                        completedTripsCount = uiState.journeyHistory.size,
                        onBack = { navController.popBackStack() },
                        onLogout = {
                            viewModel.logout {
                                navController.navigate(RoutePilotRoutes.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                    )
                }
            }

            // Dedicated top status bar scrim so mobile Time, Date, Battery & Signal are always readable
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Color.White.copy(alpha = 0.92f))
                    .align(Alignment.TopCenter)
            )
        }
    }
}

private fun createLocalizedContext(baseContext: Context, languageCode: String): Context {
    val locale = Locale.forLanguageTag(languageCode)
    Locale.setDefault(locale)
    val config = Configuration(baseContext.resources.configuration)
    config.setLocale(locale)
    return baseContext.createConfigurationContext(config)
}
