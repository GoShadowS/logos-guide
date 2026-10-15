package com.logos.navigator.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.logos.navigator.R
import com.logos.navigator.data.model.AppLanguage
import com.logos.navigator.data.model.AppSettings
import com.logos.navigator.data.model.LocationMode
import com.logos.navigator.data.model.StartupLocationMode
import com.logos.navigator.ui.about.AboutScreen
import com.logos.navigator.ui.favorites.FavoritesScreen
import com.logos.navigator.ui.favorites.FavoritesViewModel
import com.logos.navigator.ui.map.MapScreen
import com.logos.navigator.ui.onboarding.OnboardingScreen
import com.logos.navigator.ui.search.SearchScreen
import com.logos.navigator.ui.settings.SettingsScreen
import com.logos.navigator.ui.splash.SplashScreen
import com.logos.navigator.ui.theme.LogosTheme
import com.logos.navigator.utils.AppLocaleController
import kotlinx.coroutines.delay
import kotlin.math.hypot

/** Корневой Compose UI: onboarding, адаптивная навигация, локали и экраны проекта. */
@Composable
fun NavigatorApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favoritesViewModel: FavoritesViewModel = composeViewModel()
    val favoritesState by favoritesViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    var splashFinished by remember { mutableStateOf(false) }
    val rootSnackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        splashFinished = true
    }
    LaunchedEffect(state.isLoading, state.settings.appLanguage) {
        if (!state.isLoading) AppLocaleController.applyStoredOverride(state.settings.appLanguage)
    }

    val favoriteIds = remember(favoritesState.favoriteRooms) {
        favoritesState.favoriteRooms.mapTo(mutableSetOf()) { it.roomId }
    }
    val quickFavorites = remember(favoritesState.favoriteRooms, state.rooms) {
        favoritesState.favoriteRooms.mapNotNull { favorite -> state.rooms.firstOrNull { it.id == favorite.roomId } }
            .filter { it.routeAvailable && !it.nodeId.isNullOrBlank() }
    }
    LaunchedEffect(favoritesState.message, favoritesState.error, currentRoute) {
        val feedback = favoritesState.error ?: favoritesState.message ?: return@LaunchedEffect
        if (currentRoute != ROUTE_FAVORITES) {
            viewModel.showMessage(feedback)
            favoritesViewModel.clearMessage()
        }
    }
    LaunchedEffect(state.message, currentRoute) {
        val message = state.message ?: return@LaunchedEffect
        if (currentRoute != ROUTE_MAP) {
            rootSnackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    LogosTheme(
        themeMode = state.settings.themeMode,
        accentColorArgb = state.settings.accentColorArgb,
        fontScale = state.settings.fontScale,
    ) {
        when {
            !splashFinished || state.isLoading -> SplashScreen()
            !state.settings.onboardingCompleted -> OnboardingScreen(
                useElevator = state.settings.useElevator,
                onLanguageSelected = { language ->
                    viewModel.updateSettings { it.copy(appLanguage = language) }
                    AppLocaleController.apply(language)
                },
                onUseElevatorChanged = { enabled -> viewModel.updateSettings { it.copy(useElevator = enabled) } },
                onLocationPermissionResult = { granted ->
                    viewModel.updateSettings {
                        it.copy(
                            lastLocationMode = if (granted) LocationMode.GPS else LocationMode.MANUAL,
                            startupLocationMode = if (granted) AppSettings().startupLocationMode else com.logos.navigator.data.model.StartupLocationMode.MANUAL,
                            gpsEnabledOnLaunch = granted,
                        )
                    }
                },
                onSkipLocation = {
                    viewModel.updateSettings {
                        it.copy(
                            lastLocationMode = LocationMode.MANUAL,
                            startupLocationMode = com.logos.navigator.data.model.StartupLocationMode.MANUAL,
                            gpsEnabledOnLaunch = false,
                        )
                    }
                },
                onComplete = {
                    viewModel.updateSettings { it.copy(onboardingCompleted = true) }
                },
            )
            else -> MainNavigation(
                viewModel = viewModel,
                favoritesViewModel = favoritesViewModel,
                favoritesState = favoritesState,
                favoriteIds = favoriteIds,
                quickFavorites = quickFavorites,
                navController = navController,
                currentRoute = currentRoute,
                isTablet = isTablet,
                rootSnackbarHostState = rootSnackbarHostState,
            )
        }
    }
}

@Composable
private fun MainNavigation(
    viewModel: MainViewModel,
    favoritesViewModel: FavoritesViewModel,
    favoritesState: com.logos.navigator.ui.favorites.FavoritesUiState,
    favoriteIds: Set<String>,
    quickFavorites: List<com.logos.navigator.data.model.Room>,
    navController: NavHostController,
    currentRoute: String?,
    isTablet: Boolean,
    rootSnackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var startupLocationHandled by remember { mutableStateOf(false) }
    val startupLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.setLocationMode(LocationMode.GPS)
        } else {
            viewModel.setLocationMode(LocationMode.MANUAL)
            viewModel.showMessage(context.getString(R.string.gps_permission_denied))
        }
    }
    LaunchedEffect(state.navigation, startupLocationHandled) {
        if (state.navigation == null || startupLocationHandled) return@LaunchedEffect
        startupLocationHandled = true
        val settings = state.settings
        val shouldStartGps = when (settings.startupLocationMode) {
            StartupLocationMode.GPS -> true
            StartupLocationMode.MANUAL -> false
            StartupLocationMode.LAST -> settings.gpsEnabledOnLaunch && settings.lastLocationMode == LocationMode.GPS
        }
        if (!shouldStartGps) {
            viewModel.setLocationMode(LocationMode.MANUAL)
            return@LaunchedEffect
        }
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission) {
            viewModel.setLocationMode(LocationMode.GPS)
        } else {
            startupLocationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }
    var pendingSearchQuery by rememberSaveable { mutableStateOf("") }
    var pendingSearchCategory by rememberSaveable { mutableStateOf(SEARCH_ALL_CATEGORIES) }
    var searchRequestVersion by rememberSaveable { mutableIntStateOf(0) }
    fun openSearch(query: String = "", category: String = SEARCH_ALL_CATEGORIES) {
        pendingSearchQuery = query
        pendingSearchCategory = category
        searchRequestVersion += 1
        navigateTo(navController, ROUTE_SEARCH)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(rootSnackbarHostState) },
        bottomBar = {
            if (!isTablet && currentRoute != ROUTE_ABOUT) {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (tab == MainTab.SEARCH) openSearch()
                                else navigateToTab(navController, tab.route)
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(navPadding(innerPadding, currentRoute))) {
            if (isTablet && currentRoute != ROUTE_ABOUT) {
                NavigationRail {
                    MainTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (tab == MainTab.SEARCH) openSearch()
                                else navigateToTab(navController, tab.route)
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = ROUTE_MAP,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                composable(LEGACY_HOME_ROUTE) {
                    LaunchedEffect(Unit) { navigateToTab(navController, ROUTE_MAP) }
                }
                composable(ROUTE_MAP) {
                    MapScreen(
                        state = state,
                        viewModel = viewModel,
                        favoriteRoomIds = favoriteIds,
                        quickFavoriteRooms = quickFavorites,
                        onToggleFavorite = favoritesViewModel::toggleFavorite,
                        onOpenFavoriteRoom = viewModel::selectRoomFromSearch,
                        onSaveCurrentRoute = { destination -> saveRoute(favoritesViewModel, state, destination) },
                        onOpenAbout = { navigateTo(navController, ROUTE_ABOUT) },
                    )
                }
                composable(ROUTE_FAVORITES) {
                    if (isTablet) {
                        Row(Modifier.fillMaxSize()) {
                            FavoritesScreen(
                                state = favoritesState,
                                viewModel = favoritesViewModel,
                                modifier = Modifier.weight(0.43f),
                                onBack = { navigateToTab(navController, ROUTE_MAP) },
                                onOpenSearch = { openSearch() },
                                onOpenRoom = viewModel::selectRoomFromSearch,
                                onRepeatRoute = { fromId, toId -> viewModel.replaySavedRoute(fromId, toId) },
                            )
                            MapScreen(
                                state = state,
                                viewModel = viewModel,
                                favoriteRoomIds = favoriteIds,
                                quickFavoriteRooms = quickFavorites,
                                modifier = Modifier.weight(0.57f),
                                onToggleFavorite = favoritesViewModel::toggleFavorite,
                                onOpenFavoriteRoom = viewModel::selectRoomFromSearch,
                                onSaveCurrentRoute = { destination -> saveRoute(favoritesViewModel, state, destination) },
                                onOpenAbout = { navigateTo(navController, ROUTE_ABOUT) },
                            )
                        }
                    } else {
                        FavoritesScreen(
                            state = favoritesState,
                            viewModel = favoritesViewModel,
                            onBack = { navigateToTab(navController, ROUTE_MAP) },
                            onOpenSearch = { openSearch() },
                            onOpenRoom = { room ->
                                viewModel.selectRoomFromSearch(room)
                                navigateToTab(navController, ROUTE_MAP)
                            },
                            onRepeatRoute = { fromRoomId, toRoomId ->
                                viewModel.replaySavedRoute(fromRoomId, toRoomId)
                                navigateToTab(navController, ROUTE_MAP)
                            },
                        )
                    }
                }
                composable(ROUTE_SEARCH) {
                    if (isTablet) {
                        Row(Modifier.fillMaxSize()) {
                            SearchScreen(
                                rooms = state.rooms,
                                floors = state.navigation?.floors.orEmpty(),
                                favoriteRoomIds = favoriteIds,
                                modifier = Modifier.weight(0.43f),
                                onToggleFavorite = favoritesViewModel::toggleFavorite,
                                onBack = { navigateToTab(navController, ROUTE_MAP) },
                                onSelectRoom = viewModel::selectRoomFromSearch,
                                onBuildRoute = viewModel::navigateTo,
                                initialQuery = pendingSearchQuery,
                                initialCategory = pendingSearchCategory,
                                searchRequestVersion = searchRequestVersion,
                            )
                            MapScreen(
                                state = state,
                                viewModel = viewModel,
                                favoriteRoomIds = favoriteIds,
                                quickFavoriteRooms = quickFavorites,
                                modifier = Modifier.weight(0.57f),
                                onToggleFavorite = favoritesViewModel::toggleFavorite,
                                onOpenFavoriteRoom = viewModel::selectRoomFromSearch,
                                onSaveCurrentRoute = { destination -> saveRoute(favoritesViewModel, state, destination) },
                                onOpenAbout = { navigateTo(navController, ROUTE_ABOUT) },
                            )
                        }
                    } else {
                        SearchScreen(
                            rooms = state.rooms,
                            floors = state.navigation?.floors.orEmpty(),
                            favoriteRoomIds = favoriteIds,
                            onToggleFavorite = favoritesViewModel::toggleFavorite,
                            onBack = { navigateToTab(navController, ROUTE_MAP) },
                            onSelectRoom = { room ->
                                viewModel.selectRoomFromSearch(room)
                                navigateToTab(navController, ROUTE_MAP)
                            },
                            onBuildRoute = { room ->
                                viewModel.navigateTo(room)
                                navigateToTab(navController, ROUTE_MAP)
                            },
                            initialQuery = pendingSearchQuery,
                            initialCategory = pendingSearchCategory,
                            searchRequestVersion = searchRequestVersion,
                        )
                    }
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        settings = state.settings,
                        floors = state.navigation?.floors.orEmpty(),
                        onUpdateSettings = viewModel::updateSettings,
                        onResetSettings = viewModel::resetSettings,
                        onBack = { navigateToTab(navController, ROUTE_MAP) },
                        onOpenFavorites = { navigateTo(navController, ROUTE_FAVORITES) },
                        onOpenAbout = { navigateTo(navController, ROUTE_ABOUT) },
                        onOpenAppPermissions = { AppSettingsIntent.openAppDetails(context) },
                        onReplayOnboarding = { viewModel.updateSettings { it.copy(onboardingCompleted = false) } },
                    )
                }
                composable(ROUTE_ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

private fun saveRoute(
    favoritesViewModel: FavoritesViewModel,
    state: com.logos.navigator.data.model.IndoorUiState,
    destination: com.logos.navigator.data.model.Room,
) {
    val position = state.userPosition
    val startRoom = position?.let { point ->
        val metersPerUnit = state.navigation?.floors?.firstOrNull { it.number == point.floor }?.metersPerUnit ?: 0.083333
        state.rooms.asSequence()
            .filter { it.floor == point.floor && it.routeAvailable }
            .mapNotNull { room ->
                val x = room.x ?: return@mapNotNull null
                val y = room.y ?: return@mapNotNull null
                room to hypot(x - point.x, y - point.y)
            }
            .minByOrNull { it.second }
            ?.takeIf { it.second * metersPerUnit <= 3.0 }
            ?.first
    }
    favoritesViewModel.saveRoute(
        destination = destination,
        fromRoomId = startRoom?.id,
        fromFloor = position?.floor,
        distanceMeters = state.routeDistanceMeters,
    )
}

private enum class MainTab(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    MAP(ROUTE_MAP, R.string.nav_map, Icons.Default.Map),
    FAVORITES(ROUTE_FAVORITES, R.string.nav_favorites, Icons.Default.Favorite),
    SEARCH(ROUTE_SEARCH, R.string.nav_search, Icons.Default.Search),
    SETTINGS(ROUTE_SETTINGS, R.string.nav_settings, Icons.Default.Settings),
}

private fun navigateTo(navController: NavHostController, route: String) {
    navController.navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    }
}

/** Переключает нижние вкладки возвратом к уже открытому экрану, а не дублирует его в back stack. */
private fun navigateToTab(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route == route) return
    if (!navController.popBackStack(route, inclusive = false, saveState = true)) {
        navigateTo(navController, route)
    }
}

private fun navPadding(padding: PaddingValues, route: String?): PaddingValues =
    if (route == ROUTE_ABOUT) PaddingValues(0.dp) else padding

private object AppSettingsIntent {
    fun openAppDetails(context: android.content.Context) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                },
            )
        }
    }
}

private const val ROUTE_MAP = "map"
private const val LEGACY_HOME_ROUTE = "home"
private const val ROUTE_FAVORITES = "favorites"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ABOUT = "about"
private const val SEARCH_ALL_CATEGORIES = "all"
private const val SPLASH_DURATION_MS = 1_800L
