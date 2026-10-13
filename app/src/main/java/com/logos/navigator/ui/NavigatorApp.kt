package com.logos.navigator.ui

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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val activity = context.findActivity()
    val widthClass = activity?.let { runCatching { calculateWindowSizeClass(it).widthSizeClass }.getOrNull() }
    val isTablet = widthClass?.let { it != WindowWidthSizeClass.Compact } ?: (configuration.screenWidthDp >= 600)
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
    Scaffold(
        snackbarHost = { SnackbarHost(rootSnackbarHostState) },
        bottomBar = {
            if (!isTablet && currentRoute != ROUTE_ABOUT) {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateTo(navController, tab.route) },
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
                            onClick = { navigateTo(navController, tab.route) },
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
                composable(ROUTE_MAP) {
                    MapScreen(
                        state = state,
                        viewModel = viewModel,
                        favoriteRoomIds = favoriteIds,
                        quickFavoriteRooms = quickFavorites,
                        onToggleFavorite = favoritesViewModel::toggleFavorite,
                        onOpenFavoriteRoom = viewModel::selectRoomFromSearch,
                        onSaveCurrentRoute = { destination -> saveRoute(favoritesViewModel, state, destination) },
                        onOpenSearch = { navigateTo(navController, ROUTE_SEARCH) },
                        onOpenFavorites = { navigateTo(navController, ROUTE_FAVORITES) },
                        onOpenAbout = { navigateTo(navController, ROUTE_ABOUT) },
                        onOpenSettings = { navigateTo(navController, ROUTE_SETTINGS) },
                    )
                }
                composable(ROUTE_FAVORITES) {
                    if (isTablet) {
                        Row(Modifier.fillMaxSize()) {
                            FavoritesScreen(
                                state = favoritesState,
                                viewModel = favoritesViewModel,
                                modifier = Modifier.weight(0.43f),
                                onBack = { navigateTo(navController, ROUTE_MAP) },
                                onOpenSearch = { navigateTo(navController, ROUTE_SEARCH) },
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
                                onOpenSearch = { navigateTo(navController, ROUTE_SEARCH) },
                                onOpenFavorites = { navigateTo(navController, ROUTE_FAVORITES) },
                                onOpenAbout = { navigateTo(navController, ROUTE_ABOUT) },
                                onOpenSettings = { navigateTo(navController, ROUTE_SETTINGS) },
                            )
                        }
                    } else {
                        FavoritesScreen(
                            state = favoritesState,
                            viewModel = favoritesViewModel,
                            onBack = { navigateTo(navController, ROUTE_MAP) },
                            onOpenSearch = { navigateTo(navController, ROUTE_SEARCH) },
                            onOpenRoom = { room ->
                                viewModel.selectRoomFromSearch(room)
                                navigateTo(navController, ROUTE_MAP)
                            },
                            onRepeatRoute = { fromRoomId, toRoomId ->
                                viewModel.replaySavedRoute(fromRoomId, toRoomId)
                                navigateTo(navController, ROUTE_MAP)
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
                                onBack = { navigateTo(navController, ROUTE_MAP) },
                                onSelectRoom = viewModel::selectRoomFromSearch,
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
                                onOpenSearch = { navigateTo(navController, ROUTE_SEARCH) },
                                onOpenFavorites = { navigateTo(navController, ROUTE_FAVORITES) },
                                onOpenAbout = { navigateTo(navController, ROUTE_ABOUT) },
                                onOpenSettings = { navigateTo(navController, ROUTE_SETTINGS) },
                            )
                        }
                    } else {
                        SearchScreen(
                            rooms = state.rooms,
                            floors = state.navigation?.floors.orEmpty(),
                            favoriteRoomIds = favoriteIds,
                            onToggleFavorite = favoritesViewModel::toggleFavorite,
                            onBack = { navigateTo(navController, ROUTE_MAP) },
                            onSelectRoom = { room ->
                                viewModel.selectRoomFromSearch(room)
                                navigateTo(navController, ROUTE_MAP)
                            },
                        )
                    }
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        settings = state.settings,
                        floors = state.navigation?.floors.orEmpty(),
                        onUpdateSettings = viewModel::updateSettings,
                        onResetSettings = viewModel::resetSettings,
                        onBack = { navigateTo(navController, ROUTE_MAP) },
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
    MAP("map", R.string.nav_map, Icons.Default.Map),
    FAVORITES("favorites", R.string.nav_favorites, Icons.Default.Favorite),
    SEARCH("search", R.string.nav_search, Icons.Default.Search),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings),
}

private fun navigateTo(navController: NavHostController, route: String) {
    navController.navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    }
}

private fun navPadding(padding: PaddingValues, route: String?): PaddingValues =
    if (route == ROUTE_ABOUT) PaddingValues(0.dp) else padding

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
private const val ROUTE_FAVORITES = "favorites"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ABOUT = "about"
private const val SPLASH_DURATION_MS = 1_800L
