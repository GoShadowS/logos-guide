package com.logos.navigator.ui.map

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.logos.navigator.R
import com.logos.navigator.data.model.DistanceUnit
import com.logos.navigator.data.model.IndoorUiState
import com.logos.navigator.data.model.LocationMode
import com.logos.navigator.data.model.MapPoint
import com.logos.navigator.data.model.RouteAction
import com.logos.navigator.data.model.RouteInstruction
import com.logos.navigator.data.model.Room
import com.logos.navigator.ui.MainViewModel
import com.logos.navigator.utils.categoryLabel
import com.logos.navigator.utils.formatDistance
import com.logos.navigator.utils.localizedDescription
import com.logos.navigator.utils.localizedName
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    state: IndoorUiState,
    viewModel: MainViewModel,
    favoriteRoomIds: Set<String> = emptySet(),
    quickFavoriteRooms: List<Room> = emptyList(),
    onToggleFavorite: (Room) -> Unit = {},
    onOpenFavoriteRoom: (Room) -> Unit = {},
    onSaveCurrentRoute: (Room) -> Unit = {},
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val roomsOnSelectedFloor = remember(state.rooms, state.selectedFloor) {
        state.rooms.filter { it.floor == state.selectedFloor }
    }
    val navigationNodes = remember(state.navigation) { state.navigation?.nodes.orEmpty() }
    val navigationEdges = remember(state.navigation) { state.navigation?.edges.orEmpty() }
    val snackbarHostState = remember { SnackbarHostState() }
    var mapView by remember { mutableStateOf<SvgMapView?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var floorMenuExpanded by remember { mutableStateOf(false) }
    var quickActionsExpanded by remember { mutableStateOf(false) }
    var isPlacingManualPosition by remember { mutableStateOf(false) }
    var isRouteSheetVisible by remember { mutableStateOf(false) }
    var rationaleVisible by remember { mutableStateOf(false) }
    var pendingCameraCenter by remember { mutableStateOf<MapPoint?>(null) }
    var gpsProviderEnabled by remember(context) { mutableStateOf(isGpsProviderEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) gpsProviderEnabled = isGpsProviderEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            viewModel.setLocationMode(LocationMode.GPS)
        } else {
            viewModel.setLocationMode(LocationMode.MANUAL)
            viewModel.showMessage(context.getString(R.string.gps_permission_denied))
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }
    LaunchedEffect(state.destination?.id) {
        isRouteSheetVisible = state.destination != null
    }
    LaunchedEffect(state.selectedFloor, state.floorSvg, pendingCameraCenter, mapView) {
        val point = pendingCameraCenter
        if (point != null && point.floor == state.selectedFloor && state.floorSvg != null) {
            delay(60)
            mapView?.centerOn(point)
            pendingCameraCenter = null
        }
    }

    fun requestGpsPermissionOrStart() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            viewModel.setLocationMode(LocationMode.GPS)
        } else {
            val activity = context.findActivity()
            val shouldExplain = activity != null && (
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            if (shouldExplain) rationaleVisible = true
            else locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.map_screen_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                        Text(stringResource(R.string.map_screen_subtitle), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = stringResource(R.string.splash_description),
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp).size(38.dp).clip(CircleShape),
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about_app)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = { menuExpanded = false; onOpenAbout() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_location)) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = { menuExpanded = false; shareLocation(context, viewModel) },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                state.isLoading -> LoadingPanel(Modifier.align(Alignment.Center), stringResource(R.string.loading_data))
                state.loadError != null -> ErrorPanel(
                    title = stringResource(R.string.map_load_error_title),
                    details = state.loadError,
                    onRetry = viewModel::retryLoading,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> {
                    val floor = state.navigation?.floors?.firstOrNull { it.number == state.selectedFloor }
                    AndroidView(
                        factory = { viewContext -> SvgMapView(viewContext).also { mapView = it } },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            mapView = view
                            view.submit(
                                svg = state.floorSvg,
                                floor = floor,
                                rooms = roomsOnSelectedFloor,
                                nodes = navigationNodes,
                                edges = navigationEdges,
                                userPosition = state.userPosition,
                                accuracyMeters = state.gpsAccuracyMeters,
                                locationMode = state.locationMode,
                                route = state.route,
                                destination = state.destination,
                                selectedRoom = state.selectedRoom,
                                settings = state.settings,
                                compassHeadingDegrees = state.compassHeadingDegrees,
                                onManualPosition = { x, y ->
                                    viewModel.setManualPosition(x, y)
                                    isPlacingManualPosition = false
                                    if (state.destination != null) isRouteSheetVisible = true
                                },
                                onRoomTap = viewModel::showRoom,
                                onTapCoordinate = viewModel::onMapTapCoordinate,
                                manualPositionSelectionMode = isPlacingManualPosition,
                                onManualPositionInRoom = { room, x, y ->
                                    viewModel.setManualPositionInRoom(room, x, y)
                                    isPlacingManualPosition = false
                                    if (state.destination != null) isRouteSheetVisible = true
                                },
                            )
                        },
                    )

                    if (state.isMapLoading) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(20.dp),
                            tonalElevation = 5.dp,
                        ) {
                            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.loading_map))
                            }
                        }
                    }
                    if (state.mapError != null) {
                        ErrorPanel(
                            title = stringResource(R.string.floor_map_error_title),
                            details = state.mapError,
                            onRetry = viewModel::retryMap,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModeSwitch(
                            selectedMode = state.locationMode,
                            onSelectGps = {
                                isPlacingManualPosition = false
                                if (state.locationMode == LocationMode.GPS) viewModel.setLocationMode(LocationMode.MANUAL)
                                else requestGpsPermissionOrStart()
                            },
                            onSelectManual = {
                                isPlacingManualPosition = false
                                viewModel.setLocationMode(LocationMode.MANUAL)
                            },
                        )
                        if (state.locationMode == LocationMode.GPS && !gpsProviderEnabled) {
                            GpsProviderWarning(
                                onOpenSettings = {
                                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                                },
                                modifier = Modifier.width(290.dp),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                val currentFloorTitle = state.navigation?.floors
                                    ?.firstOrNull { it.number == state.selectedFloor }
                                    ?.title ?: stringResource(R.string.floor_title_format, state.selectedFloor)
                                FilterChip(
                                    selected = true,
                                    onClick = { floorMenuExpanded = true },
                                    label = { Text(currentFloorTitle) },
                                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                                )
                                DropdownMenu(expanded = floorMenuExpanded, onDismissRequest = { floorMenuExpanded = false }) {
                                    state.navigation?.floors.orEmpty().sortedBy { it.number }.forEach { floorOption ->
                                        DropdownMenuItem(
                                            text = { Text(floorOption.title) },
                                            onClick = {
                                                floorMenuExpanded = false
                                                viewModel.selectFloor(floorOption.number)
                                            },
                                        )
                                    }
                                }
                            }
                            Box {
                                AssistChip(
                                    onClick = { quickActionsExpanded = true },
                                    leadingIcon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
                                    label = { Text(stringResource(R.string.quick_actions)) },
                                )
                                DropdownMenu(expanded = quickActionsExpanded, onDismissRequest = { quickActionsExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.quick_nearest_restroom)) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_restroom_24), contentDescription = null) },
                                        onClick = {
                                            quickActionsExpanded = false
                                            viewModel.navigateToNearest("restroom")
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.quick_nearest_stairs)) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_stairs_24), contentDescription = null) },
                                        onClick = {
                                            quickActionsExpanded = false
                                            viewModel.navigateToNearest("stairs")
                                        },
                                    )
                                    if (quickFavoriteRooms.isNotEmpty()) {
                                        HorizontalDivider()
                                        quickFavoriteRooms.take(5).forEach { room ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            room.number?.let { context.getString(R.string.cabinet_number_format, it) }
                                                                ?: context.categoryLabel(room.category),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                        )
                                                        Text(room.localizedName(context), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                },
                                                leadingIcon = { Icon(Icons.Default.StarBorder, contentDescription = null) },
                                                onClick = {
                                                    quickActionsExpanded = false
                                                    onOpenFavoriteRoom(room)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        when {
                            state.locationMode == LocationMode.GPS && state.gpsWarning != null -> GpsWarningCard(
                                warning = state.gpsWarning,
                                onManual = {
                                    isPlacingManualPosition = false
                                    viewModel.setLocationMode(LocationMode.MANUAL)
                                },
                                onRetryGps = if (state.isGpsActive) null else ::requestGpsPermissionOrStart,
                                modifier = Modifier.width(290.dp),
                            )
                            state.locationMode == LocationMode.GPS && state.gpsAccuracyMeters != null -> Text(
                                stringResource(R.string.location_accuracy, state.gpsAccuracyMeters.toInt()),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            state.locationMode == LocationMode.MANUAL && isPlacingManualPosition -> Text(
                                stringResource(R.string.manual_place_hint),
                                modifier = Modifier
                                    .width(270.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            state.locationMode == LocationMode.MANUAL && state.userPosition == null -> Text(
                                stringResource(R.string.location_hint),
                                modifier = Modifier
                                    .width(270.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            state.locationMode == LocationMode.MANUAL && state.userPosition?.floor != state.selectedFloor -> Text(
                                stringResource(R.string.location_hint_other_floor, state.userPosition?.floor ?: state.selectedFloor),
                                modifier = Modifier
                                    .width(270.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        if (state.locationMode == LocationMode.GPS && state.userPosition != null && state.userPosition.floor != state.selectedFloor) {
                            Text(
                                stringResource(R.string.location_on_floor, state.userPosition.floor),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    ExtendedFloatingActionButton(
                        onClick = {
                            if (state.locationMode == LocationMode.MANUAL) {
                                if (state.destination != null && !isRouteSheetVisible && !isPlacingManualPosition) {
                                    isRouteSheetVisible = true
                                } else {
                                    isPlacingManualPosition = !isPlacingManualPosition
                                    if (isPlacingManualPosition) viewModel.showMessage(context.getString(R.string.manual_place_hint))
                                }
                            } else {
                                val point = state.userPosition
                                if (point == null) viewModel.goToMyLocation()
                                else if (point.floor != state.selectedFloor) {
                                    pendingCameraCenter = point
                                    viewModel.selectFloor(point.floor)
                                } else mapView?.centerOn(point)
                            }
                        },
                        icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                        text = {
                            Text(
                                stringResource(
                                    if (isPlacingManualPosition) R.string.manual_place_location else R.string.my_location,
                                ),
                            )
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )

                    val transitionHint = viewModel.transitionHint(state.selectedFloor)
                    if (transitionHint != null) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 76.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.97f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Text(transitionHint, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                if (state.locationMode == LocationMode.GPS && state.userPosition?.floor != state.selectedFloor) {
                                    TextButton(onClick = { viewModel.confirmGpsFloor(state.selectedFloor) }) {
                                        Text(stringResource(R.string.confirm_floor))
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    if (state.destination != null && state.selectedRoom == null && isRouteSheetVisible) {
        RouteBottomSheet(
            state = state,
            onDismiss = viewModel::clearRoute,
            onSelectStartLocation = {
                isRouteSheetVisible = false
                isPlacingManualPosition = true
                if (state.locationMode != LocationMode.MANUAL) viewModel.setLocationMode(LocationMode.MANUAL)
            },
            onStartNavigation = viewModel::startNavigation,
            onCancelRoute = viewModel::clearRoute,
            onPreviousStep = viewModel::previousRouteStep,
            onNextStep = viewModel::advanceRouteStep,
            onShare = { shareLocation(context, viewModel) },
            onSaveRoute = onSaveCurrentRoute,
            distanceUnit = state.settings.distanceUnit,
        )
    }

    if (rationaleVisible) {
        AlertDialog(
            onDismissRequest = { rationaleVisible = false },
            title = { Text(stringResource(R.string.gps_permission_title)) },
            text = {
                Text(stringResource(R.string.gps_permission_rationale))
            },
            confirmButton = {
                Button(onClick = {
                    rationaleVisible = false
                    locationPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                }) { Text(stringResource(R.string.gps_permission_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { rationaleVisible = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    state.selectedRoom?.let { room ->
        RoomDetailsSheet(
            room = room,
            isFavorite = room.id in favoriteRoomIds,
            canSetPosition = state.locationMode == LocationMode.MANUAL && room.routeAvailable &&
                !room.nodeId.isNullOrBlank() && (room.centerX ?: room.x) != null && (room.centerY ?: room.y) != null,
            onToggleFavorite = { onToggleFavorite(room) },
            onDismiss = viewModel::dismissRoomDetails,
            onSetPosition = {
                val x = room.centerX ?: room.x
                val y = room.centerY ?: room.y
                if (x != null && y != null) {
                    viewModel.setManualPositionInRoom(room, x.toFloat(), y.toFloat())
                    viewModel.showMessage(context.getString(R.string.manual_location_set))
                }
                viewModel.dismissRoomDetails()
            },
            onRoute = { viewModel.navigateTo(room) },
            onShare = { shareRoom(context, room) },
        )
    }
}

@Composable
private fun ModeSwitch(
    selectedMode: LocationMode,
    onSelectGps: () -> Unit,
    onSelectManual: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeButton(stringResource(R.string.location_gps), selectedMode == LocationMode.GPS, onSelectGps)
            ModeButton(stringResource(R.string.location_manual), selectedMode == LocationMode.MANUAL, onSelectManual)
        }
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun GpsProviderWarning(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.98f)),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.gps_enable_message), style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.gps_enable_button))
            }
        }
    }
}

@Composable
private fun GpsWarningCard(
    warning: String,
    onManual: () -> Unit,
    onRetryGps: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(warning, style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onRetryGps != null) {
                    TextButton(onClick = onRetryGps) { Text(stringResource(R.string.gps_retry)) }
                }
                TextButton(onClick = onManual) { Text(stringResource(R.string.location_manual)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteBottomSheet(
    state: IndoorUiState,
    onDismiss: () -> Unit,
    onSelectStartLocation: () -> Unit,
    onStartNavigation: () -> Unit,
    onCancelRoute: () -> Unit,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    onShare: () -> Unit,
    onSaveRoute: (Room) -> Unit,
    distanceUnit: DistanceUnit,
) {
    val context = LocalContext.current
    val destination = state.destination ?: return
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var actionsExpanded by remember(destination.id) { mutableStateOf(false) }

    LaunchedEffect(destination.id, state.route != null) {
        if (state.route != null) sheetState.expand() else sheetState.partialExpand()
    }

    val displaySteps = state.routeSteps.withIndex().filter { (_, instruction) ->
        instruction.action != RouteAction.ARRIVAL &&
            (instruction.action != RouteAction.START || instruction.destinationName != null)
    }.toMutableList().apply {
        state.routeSteps.lastOrNull()
            ?.takeIf { instruction ->
                state.isNavigationStarted && state.currentRouteStepIndex == state.routeSteps.lastIndex &&
                    instruction.action == RouteAction.ARRIVAL
            }
            ?.let { add(IndexedValue(state.routeSteps.lastIndex, it)) }
    }
    val matchedCurrentStep = displaySteps.indexOfFirst { it.index == state.currentRouteStepIndex }
    val activeDisplayIndex = when {
        displaySteps.isEmpty() -> -1
        !state.isNavigationStarted -> 0
        matchedCurrentStep >= 0 -> matchedCurrentStep
        state.currentRouteStepIndex >= state.routeSteps.lastIndex -> displaySteps.lastIndex
        else -> 0
    }
    val floorLabel = stringResource(R.string.floor_number_format, destination.floor)
    val destinationHeading = destination.number?.let { stringResource(R.string.route_destination_room, it) }
        ?: destination.localizedName(context)
    val destinationDetail = if (destination.number != null) {
        stringResource(R.string.route_room_floor_detail, floorLabel, destination.localizedName(context))
    } else {
        stringResource(R.string.route_room_floor_detail, floorLabel, context.categoryLabel(destination.category))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.78f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            destinationHeading,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            destinationDetail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton(onClick = { actionsExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.quick_actions))
                        }
                        DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.change_start_location)) },
                                leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    onSelectStartLocation()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_route)) },
                                leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    onSaveRoute(destination)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_button)) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    onShare()
                                },
                            )
                        }
                    }
                    IconButton(onClick = onCancelRoute) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_route))
                    }
                }

                Spacer(Modifier.height(8.dp))
                if (state.routeDistanceMeters != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(
                                R.string.route_card_summary,
                                state.routeMinutes ?: 1,
                                context.formatDistance(state.routeDistanceMeters, distanceUnit),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (state.isNavigationStarted) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = state.routeProgress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (activeDisplayIndex >= 0) {
                        Text(
                            stringResource(R.string.route_step_count, activeDisplayIndex + 1, displaySteps.size),
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                if (state.route == null) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 18.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            stringResource(
                                if (state.userPosition == null) R.string.route_card_location_missing
                                else R.string.route_card_no_path,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    ) {
                        if (!state.isNavigationStarted && state.routeSteps.isNotEmpty()) {
                            Text(
                                stringResource(R.string.route_preview_hint),
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (displaySteps.isEmpty()) {
                            Text(
                                stringResource(R.string.route_no_steps),
                                modifier = Modifier.padding(vertical = 18.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            displaySteps.forEachIndexed { displayIndex, indexedInstruction ->
                                val instruction = indexedInstruction.value
                                val isCurrent = displayIndex == activeDisplayIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isCurrent && state.isNavigationStarted) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                Color.Transparent
                                            },
                                        )
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier.size(42.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                (displayIndex + 1).toString(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    Icon(
                                        routeActionIcon(instruction.action),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(25.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            routeInstructionLabel(context, instruction, state),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isCurrent && state.isNavigationStarted) FontWeight.Bold else FontWeight.Medium,
                                        )
                                        if (instruction.distanceMeters > 0.0) {
                                            Text(
                                                context.formatDistance(instruction.distanceMeters, distanceUnit),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                if (displayIndex < displaySteps.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 22.dp),
            ) {
                when {
                    state.route == null -> OutlinedButton(
                        onClick = onSelectStartLocation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.route_choose_start))
                    }
                    !state.isNavigationStarted -> Button(
                        onClick = onStartNavigation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.start_navigation))
                    }
                    state.locationMode == LocationMode.MANUAL -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onPreviousStep,
                            enabled = state.currentRouteStepIndex > 0,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.previous_step), maxLines = 1)
                        }
                        Button(
                            onClick = onNextStep,
                            enabled = state.currentRouteStepIndex < state.routeSteps.lastIndex,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.next_step), maxLines = 1)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                    else -> OutlinedButton(onClick = onCancelRoute, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cancel_route))
                    }
                }
            }
        }
    }
}

private fun routeInstructionLabel(context: Context, instruction: RouteInstruction, state: IndoorUiState): String {
    val floor = instruction.targetFloor ?: state.destination?.floor ?: state.selectedFloor
    return when (instruction.action) {
        RouteAction.START -> instruction.destinationName?.let { context.getString(R.string.step_start_room, it) }
            ?: context.getString(R.string.step_start)
        RouteAction.STRAIGHT -> context.getString(R.string.step_straight)
        RouteAction.TURN_LEFT -> context.getString(R.string.step_turn_left)
        RouteAction.TURN_RIGHT -> context.getString(R.string.step_turn_right)
        RouteAction.SLIGHT_LEFT -> context.getString(R.string.step_slight_left)
        RouteAction.SLIGHT_RIGHT -> context.getString(R.string.step_slight_right)
        RouteAction.U_TURN -> context.getString(R.string.step_u_turn)
        RouteAction.DOOR -> instruction.destinationName?.let { context.getString(R.string.step_door, it) }
            ?: context.getString(R.string.step_door_generic)
        RouteAction.STAIRS_UP -> context.getString(R.string.step_stairs_up, floor)
        RouteAction.STAIRS_DOWN -> context.getString(R.string.step_stairs_down, floor)
        RouteAction.ELEVATOR -> context.getString(R.string.step_elevator, floor)
        RouteAction.ARRIVAL -> instruction.destinationName?.let { context.getString(R.string.step_arrival, it) }
            ?: context.getString(R.string.step_start)
    }
}

private fun routeActionIcon(action: RouteAction): androidx.compose.ui.graphics.vector.ImageVector = when (action) {
    RouteAction.START -> Icons.Default.PlayArrow
    RouteAction.STRAIGHT, RouteAction.STAIRS_UP, RouteAction.ELEVATOR -> Icons.Default.ArrowUpward
    RouteAction.TURN_LEFT, RouteAction.SLIGHT_LEFT -> Icons.Default.KeyboardArrowLeft
    RouteAction.TURN_RIGHT, RouteAction.SLIGHT_RIGHT -> Icons.Default.KeyboardArrowRight
    RouteAction.U_TURN -> Icons.Default.Replay
    RouteAction.DOOR -> Icons.Default.Home
    RouteAction.STAIRS_DOWN -> Icons.Default.ArrowDownward
    RouteAction.ARRIVAL -> Icons.Default.Flag
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomDetailsSheet(
    room: Room,
    isFavorite: Boolean,
    canSetPosition: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onSetPosition: () -> Unit,
    onRoute: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val cabinetLabel = room.number?.let { stringResource(R.string.cabinet_number_format, it) }
    val floorLabel = stringResource(R.string.floor_number_format, room.floor)
    val roomCategory = context.categoryLabel(room.category)
    val roomMetadata = if (cabinetLabel == null) {
        stringResource(R.string.room_floor_category_only, floorLabel, roomCategory)
    } else {
        stringResource(R.string.room_number_floor_category, cabinetLabel, floorLabel, roomCategory)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(room.localizedName(context), modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_room_card))
                }
            }
            Text(roomMetadata, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            room.localizedDescription(context)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canSetPosition) {
                OutlinedButton(onClick = onSetPosition, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.set_location_here))
                }
            }
            if (room.routeAvailable && !room.nodeId.isNullOrBlank()) {
                Button(onClick = onRoute, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_route)) }
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_room_button))
            }
        }
    }
}

@Composable
private fun LoadingPanel(modifier: Modifier = Modifier, message: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CircularProgressIndicator()
        Text(message)
    }
}

@Composable
private fun ErrorPanel(title: String, details: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(details, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

private fun shareLocation(context: Context, viewModel: MainViewModel) {
    shareText(context, viewModel.shareLocationText(), context.getString(R.string.share_location))
}

private fun shareRoom(context: Context, room: Room) {
    val roomNumber = room.number?.let { number ->
        context.getString(R.string.share_room_number_format, context.getString(R.string.cabinet_number_format, number))
    }.orEmpty()
    val shareMessage = context.getString(
        R.string.share_room_text,
        context.getString(R.string.organization_name),
        room.localizedName(context),
        roomNumber,
        context.getString(R.string.floor_number_format, room.floor),
    )
    shareText(context, shareMessage, context.getString(R.string.share_room_button))
}

private fun isGpsProviderEnabled(context: Context): Boolean = runCatching {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
}.getOrDefault(false)

private fun shareText(context: Context, text: String, chooserTitle: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(sendIntent, chooserTitle)) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
