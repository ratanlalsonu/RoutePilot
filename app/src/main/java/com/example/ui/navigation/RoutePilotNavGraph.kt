package com.example.ui.navigation

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.OperatingMode
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

    // Dynamically apply English ("en") or Hindi ("hi") locale to stringResource() calls
    val localizedContext = remember(baseContext, uiState.preferences.languageCode) {
        createLocalizedContext(baseContext, uiState.preferences.languageCode)
    }

    // Runtime permission launcher for Location and Notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshDeviceLocationStatus()
    }

    CompositionLocalProvider(LocalContext provides localizedContext) {
        NavHost(
            navController = navController,
            startDestination = RoutePilotRoutes.SPLASH
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
                    onToggleOperatingMode = {
                        val nextMode = if (uiState.preferences.operatingMode == OperatingMode.DEMO) {
                            OperatingMode.LIVE
                        } else {
                            OperatingMode.DEMO
                        }
                        viewModel.setOperatingMode(nextMode)
                    },
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
                    onContinueWithGoogle = {
                        viewModel.continueWithGoogle {
                            navController.navigate(RoutePilotRoutes.HOME) {
                                popUpTo(RoutePilotRoutes.LOGIN) { inclusive = true }
                            }
                        }
                    },
                    onContinueAsGuest = {
                        viewModel.continueAsGuest {
                            navController.navigate(RoutePilotRoutes.HOME) {
                                popUpTo(RoutePilotRoutes.LOGIN) { inclusive = true }
                            }
                        }
                    },
                    onForgotPassword = { email ->
                        viewModel.sendPasswordReset(email)
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
                    onToggleOperatingMode = {
                        val nextMode = if (uiState.preferences.operatingMode == OperatingMode.DEMO) {
                            OperatingMode.LIVE
                        } else {
                            OperatingMode.DEMO
                        }
                        viewModel.setOperatingMode(nextMode)
                    },
                    onOpenDestinationSearch = {
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
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onSelectPlaceSuggestion = viewModel::selectDestinationCandidate,
                    onMapClickLocation = viewModel::selectPointOnMap,
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
                    operatingMode = uiState.preferences.operatingMode,
                    currentLocation = uiState.currentLocation,
                    destination = uiState.selectedDestination,
                    activeRoute = uiState.activeRoute,
                    previousRouteBeforeDiversion = uiState.previousRouteBeforeDiversion,
                    activeHazards = uiState.activeHazards,
                    primaryAffectingHazard = uiState.primaryAffectingHazard,
                    remainingDistanceMeters = uiState.remainingDistanceMeters,
                    remainingEtaMinutes = uiState.remainingEtaMinutes,
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
                    onTriggerDemoHazard = viewModel::triggerSimulatedHazardNow,
                    onDismissHazardAlert = viewModel::dismissHazardAlertAndContinue,
                    onTriggerRerouteNow = { viewModel.triggerAutomaticRerouting() },
                    onCancelRecalculation = viewModel::cancelRecalculation,
                    onArriveAtDestination = {
                        viewModel.completeActiveJourney()
                    },
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
                    onOperatingModeChange = viewModel::setOperatingMode,
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
                    operatingMode = uiState.preferences.operatingMode,
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
    }
}

private fun createLocalizedContext(baseContext: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val config = Configuration(baseContext.resources.configuration)
    config.setLocale(locale)
    return baseContext.createConfigurationContext(config)
}
