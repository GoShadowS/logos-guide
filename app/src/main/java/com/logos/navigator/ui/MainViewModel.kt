package com.logos.navigator.ui

import android.app.Application
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.logos.navigator.data.location.FusedLocationTracker
import com.logos.navigator.data.location.CompassHeadingTracker
import com.logos.navigator.data.datastore.SettingsDataStore
import com.logos.navigator.data.database.AppDatabase
import com.logos.navigator.data.database.entity.RouteHistoryEntity
import com.logos.navigator.R
import com.logos.navigator.data.model.AppSettings
import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.GeoCoordinate
import com.logos.navigator.data.model.IndoorUiState
import com.logos.navigator.data.model.RouteAction
import com.logos.navigator.data.model.RouteInstruction
import com.logos.navigator.data.model.SpeechLanguage
import com.logos.navigator.data.model.LocationMode
import com.logos.navigator.data.model.MapPoint
import com.logos.navigator.data.model.NavigationData
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.Room
import com.logos.navigator.data.model.SvgPoint
import com.logos.navigator.data.repository.FavoritesRepository
import com.logos.navigator.data.repository.IndoorRepository
import com.logos.navigator.navigation.GeoReferencer
import com.logos.navigator.navigation.RouteProgressCalculator
import com.logos.navigator.navigation.RouteStepGenerator
import com.logos.navigator.navigation.SiteIndoorRouter
import com.logos.navigator.utils.localizedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/** ViewModel приложения: данные карт, выбор этажа, геопозиция и расчёт маршрута. */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = IndoorRepository(application)
    private val locationTracker = FusedLocationTracker(application)
    private val compassTracker = CompassHeadingTracker(application)
    private val settingsStore = SettingsDataStore(application)
    private val debugLogFile = File(application.filesDir, "debug/navigator.log")
    private val debugLogLock = Any()
    private var compassTrackingEnabled = false
    private val favoritesRepository = FavoritesRepository(application, AppDatabase.getInstance(application))

    private val _state = MutableStateFlow(IndoorUiState())
    val state: StateFlow<IndoorUiState> = _state.asStateFlow()

    private var navigationData: NavigationData? = null
    private var lastGpsLocation: Location? = null
    /** Последний этаж, подтверждённый пользователем или предыдущим позиционированием. */
    private var lastPositionFloor: Int? = null
    /** GPS сам не определяет этаж, поэтому используем только сохранённый/подтверждённый этаж. */
    private var gpsFloorNumber: Int? = null
    private var dataLoadJob: Job? = null
    private var gpsTimeoutJob: Job? = null
    private var gpsAttempt = 0
    private var arrivalRoomId: String? = null
    /** Если ручную точку поставили внутри комнаты, маршрут начинается у её двери. */
    private var manualStartRoomId: String? = null
    private var lastRouteRecalculationAt = 0L
    private var routeSession = 0L
    private var recordedRouteSession = -1L
    private var announcedRouteSession = -1L
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private var pendingSpeech: String? = null

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                _state.update { it.copy(settings = settings) }
                if (settings.compassRotationEnabled != compassTrackingEnabled) {
                    compassTrackingEnabled = settings.compassRotationEnabled
                    if (compassTrackingEnabled) {
                        val started = compassTracker.start { heading ->
                            _state.update { it.copy(compassHeadingDegrees = heading) }
                        }
                        if (!started) showMessage(appContext.getString(R.string.compass_unavailable))
                    } else {
                        compassTracker.stop()
                        _state.update { it.copy(compassHeadingDegrees = 0f) }
                    }
                }
            }
        }
        loadData()
    }

    private fun loadData() {
        dataLoadJob?.cancel()
        dataLoadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            try {
                val loaded = repository.loadIndoorData()
                navigationData = loaded.navigation
                val settings = settingsStore.settingsFlow.first()
                val fallbackFloor = loaded.navigation.floors.minByOrNull(FloorPlan::number)
                    ?: error(appContext.getString(R.string.data_missing_floor))
                val requestedFloor = if (settings.initialFloor == "last") {
                    settings.lastOpenedFloor
                } else {
                    settings.initialFloor.toIntOrNull() ?: fallbackFloor.number
                }
                val firstFloor = loaded.navigation.floors.firstOrNull { it.number == requestedFloor } ?: fallbackFloor
                lastPositionFloor = firstFloor.number
                _state.update {
                    it.copy(
                        isLoading = false,
                        settings = settings,
                        navigation = loaded.navigation,
                        rooms = loaded.rooms,
                        selectedFloor = firstFloor.number,
                        selectedRoom = null,
                        destination = null,
                        route = null,
                        routeOriginPosition = null,
                        isNavigationStarted = false,
                        routeSteps = emptyList(),
                        currentRouteStepIndex = 0,
                        routeProgress = 0f,
                        routeDistanceMeters = null,
                        routeMinutes = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        loadError = appContext.getString(
                            R.string.data_load_error,
                            "\n${exception.localizedMessage ?: appContext.getString(R.string.unknown_error)}",
                        ),
                    )
                }
            }
        }
    }

    fun retryLoading() {
        loadData()
    }

    fun selectFloor(floorNumber: Int, userSelected: Boolean = true) {
        if (navigationData?.floors?.any { it.number == floorNumber } != true) return
        if (userSelected && _state.value.locationMode == LocationMode.MANUAL) {
            lastPositionFloor = floorNumber
        }
        if (_state.value.selectedFloor == floorNumber) return
        _state.update { it.copy(selectedFloor = floorNumber) }
        viewModelScope.launch { settingsStore.setLastOpenedFloor(floorNumber) }
        // Намеренно не переносим GPS-маркер на другой этаж: GPS не определяет этаж здания.
    }

    fun showRoom(room: Room) {
        if (room.floor != _state.value.selectedFloor) selectFloor(room.floor, userSelected = false)
        _state.update { it.copy(selectedRoom = room) }
    }

    fun dismissRoomDetails() {
        _state.update { it.copy(selectedRoom = null) }
    }

    /** Выбор в каталоге открывает соответствующий этаж и карточку комнаты. */
    fun selectRoomFromSearch(room: Room) {
        stopRouteSpeech()
        arrivalRoomId = null
        routeSession++
        selectFloor(room.floor, userSelected = false)
        if (!room.routeAvailable || room.nodeId.isNullOrBlank()) {
            _state.update {
                it.copy(
                    destination = null,
                    selectedRoom = room,
                    route = null,
                    routeOriginPosition = null,
                    isNavigationStarted = false,
                    routeSteps = emptyList(),
                    currentRouteStepIndex = 0,
                    routeProgress = 0f,
                    routeDistanceMeters = null,
                    routeMinutes = null,
                    message = appContext.getString(R.string.room_route_unavailable, room.localizedName(appContext)),
                )
            }
            return
        }
        _state.update {
            it.copy(
                destination = room,
                selectedRoom = null,
                route = null,
                routeOriginPosition = null,
                isNavigationStarted = false,
                routeSteps = emptyList(),
                currentRouteStepIndex = 0,
                routeProgress = 0f,
                routeDistanceMeters = null,
                routeMinutes = null,
                message = null,
            )
        }
        if (_state.value.settings.automaticRouteOnSelection && _state.value.userPosition != null) buildRoute(room)
    }

    fun setManualPosition(x: Float, y: Float) {
        updateManualPosition(x, y, startRoom = null)
    }

    /** Устанавливает старт внутри выбранного маршрутизируемого помещения у известной двери. */
    fun setManualPositionInRoom(room: Room, x: Float, y: Float) {
        if (!room.routeAvailable || room.nodeId.isNullOrBlank()) {
            showMessage(appContext.getString(R.string.room_route_unavailable, room.localizedName(appContext)))
            return
        }
        if (room.floor != _state.value.selectedFloor) selectFloor(room.floor, userSelected = false)
        updateManualPosition(x, y, startRoom = room)
    }

    private fun updateManualPosition(x: Float, y: Float, startRoom: Room?) {
        val current = _state.value
        val floor = navigationData?.floors?.firstOrNull { it.number == current.selectedFloor } ?: return
        if (current.locationMode != LocationMode.MANUAL) return
        if (!SiteIndoorRouter.isInsideCollege(floor.number, x.toDouble(), y.toDouble(), current.rooms)) {
            showMessage(appContext.getString(R.string.manual_position_outside_map))
            return
        }
        val point = MapPoint(floor = floor.number, x = x, y = y)
        nonRouteableRoomAt(point)?.let { room ->
            showMessage(appContext.getString(R.string.room_route_unavailable, room.localizedName(appContext)))
            return
        }
        val graph = navigationData ?: return
        if (isInsideRestrictedArea(graph, point.floor, point.x.toDouble(), point.y.toDouble())) {
            showMessage(appContext.getString(R.string.route_start_in_restricted_area))
            return
        }
        manualStartRoomId = startRoom
            ?.takeIf { it.floor == point.floor && it.routeAvailable }
            ?.takeIf { room ->
                (room.doorNodeIds + listOfNotNull(room.doorNodeId, room.nodeId))
                    .any { doorId -> graph.nodes.any { it.id == doorId && it.floor == point.floor } }
            }
            ?.id
        lastPositionFloor = floor.number
        _state.update {
            it.copy(
                userPosition = point,
                isManualPositionConfirmed = true,
                gpsAccuracyMeters = null,
                gpsWarning = null,
            )
        }
        current.destination?.let(::buildRoute)
    }

    fun setLocationMode(mode: LocationMode) {
        updateSettings { it.copy(lastLocationMode = mode) }
        if (mode == LocationMode.MANUAL) {
            gpsTimeoutJob?.cancel()
            gpsTimeoutJob = null
            gpsAttempt++
            lastGpsLocation = null
            locationTracker.stop()
            _state.update {
                it.copy(
                    locationMode = LocationMode.MANUAL,
                    gpsCoordinates = null,
                    isGpsActive = false,
                    gpsAccuracyMeters = null,
                    gpsWarning = null,
                )
            }
            return
        }

        stopRouteSpeech()
        manualStartRoomId = null
        val current = _state.value
        gpsFloorNumber = when {
            current.locationMode == LocationMode.MANUAL && current.isManualPositionConfirmed -> current.userPosition?.floor
            else -> lastPositionFloor ?: current.userPosition?.floor
        } ?: current.selectedFloor
        lastGpsLocation = null
        val attempt = ++gpsAttempt
        gpsTimeoutJob?.cancel()
        _state.update {
            it.copy(
                locationMode = LocationMode.GPS,
                isGpsActive = true,
                userPosition = null,
                gpsCoordinates = null,
                isManualPositionConfirmed = false,
                route = null,
                routeOriginPosition = null,
                isNavigationStarted = false,
                routeSteps = emptyList(),
                currentRouteStepIndex = 0,
                routeProgress = 0f,
                routeDistanceMeters = null,
                routeMinutes = null,
                gpsAccuracyMeters = null,
                gpsWarning = appContext.getString(R.string.gps_determining),
            )
        }
        gpsTimeoutJob = viewModelScope.launch {
            delay(GPS_FIRST_FIX_TIMEOUT_MS)
            val current = _state.value
            if (attempt == gpsAttempt && current.locationMode == LocationMode.GPS && lastGpsLocation == null) {
                _state.update {
                    it.copy(
                        isGpsActive = false,
                        gpsWarning = appContext.getString(R.string.gps_no_fresh_fix),
                    )
                }
            }
        }
        startGpsTracking()
    }

    private fun startGpsTracking() {
        if (_state.value.locationMode != LocationMode.GPS) return
        locationTracker.start(
            accuracyMode = _state.value.settings.locationAccuracyMode,
            updateIntervalSeconds = _state.value.settings.locationUpdateIntervalSeconds,
            onLocation = ::onGpsLocation,
            onAvailability = ::onGpsAvailability,
            onError = ::onGpsError,
        )
    }

    private fun onGpsLocation(location: Location) {
        if (_state.value.locationMode != LocationMode.GPS) return
        if (!location.latitude.isFinite() || location.latitude !in -90.0..90.0 ||
            !location.longitude.isFinite() || location.longitude !in -180.0..180.0
        ) {
            return
        }
        if (!isFreshLocation(location)) {
            if (lastGpsLocation == null) {
                _state.update {
                    it.copy(gpsWarning = appContext.getString(R.string.gps_stale_fix))
                }
            }
            return
        }

        val acceptedLocation = Location(location)
        lastGpsLocation = acceptedLocation
        _state.update {
            it.copy(gpsCoordinates = GeoCoordinate(acceptedLocation.latitude, acceptedLocation.longitude))
        }
        gpsTimeoutJob?.cancel()
        gpsTimeoutJob = null
        val current = _state.value
        val currentFloor = gpsFloorNumber ?: current.userPosition?.floor ?: lastPositionFloor ?: current.selectedFloor
        updateGpsPosition(acceptedLocation, currentFloor)
    }

    private fun isFreshLocation(location: Location): Boolean {
        val fixElapsedNanos = location.elapsedRealtimeNanos
        if (fixElapsedNanos > 0L) {
            val ageNanos = SystemClock.elapsedRealtimeNanos() - fixElapsedNanos
            return ageNanos in -MAX_FUTURE_FIX_SKEW_NANOS..MAX_LOCATION_AGE_NANOS
        }
        val ageMillis = System.currentTimeMillis() - location.time
        return ageMillis in -MAX_FUTURE_FIX_SKEW_MS..MAX_LOCATION_AGE_MS
    }

    private fun locationAccuracy(location: Location): Float? {
        val hasAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            location.hasAccuracy()
        } else {
            location.accuracy > 0f
        }
        return location.accuracy.takeIf { hasAccuracy && it.isFinite() && it >= 0f }
    }

    private fun updateGpsPosition(location: Location, floorNumber: Int) {
        val graph = navigationData ?: return
        val floor = graph.floors.firstOrNull { it.number == floorNumber } ?: return
        val anchors = graph.gpsAnchors.filter { it.floor == floorNumber }
        val svgPoint = GeoReferencer.toMapPoint(location.latitude, location.longitude, anchors)
        val accuracy = locationAccuracy(location)
        if (svgPoint == null) {
            _state.update {
                it.copy(
                    userPosition = null,
                    isManualPositionConfirmed = false,
                    gpsWarning = appContext.getString(
                        R.string.gps_no_anchors,
                        appContext.getString(R.string.floor_number_format, floor.number),
                    ),
                    gpsAccuracyMeters = accuracy,
                    isGpsActive = true,
                )
            }
            return
        }
        val settings = _state.value.settings
        if (accuracy != null && accuracy > settings.minimumAccuracyMeters) {
            val warning = appContext.getString(
                R.string.gps_accuracy_too_low,
                accuracy.toInt(),
                settings.minimumAccuracyMeters.toInt(),
            )
            _state.update { it.copy(userPosition = null, gpsAccuracyMeters = accuracy, gpsWarning = warning, isGpsActive = true) }
            if (settings.switchToManualOnPoorGps) {
                setLocationMode(LocationMode.MANUAL)
                showMessage(
                    appContext.getString(
                        R.string.gps_provider_error_hint,
                        warning,
                        appContext.getString(R.string.gps_manual_mode_switched),
                    ),
                )
            }
            return
        }
        if (!SiteIndoorRouter.isInsideCollege(floor.number, svgPoint.first, svgPoint.second, _state.value.rooms)) {
            _state.update {
                it.copy(
                    userPosition = null,
                    isManualPositionConfirmed = false,
                    route = null,
                    routeOriginPosition = null,
                    isNavigationStarted = false,
                    routeSteps = emptyList(),
                    currentRouteStepIndex = 0,
                    routeProgress = 0f,
                    routeDistanceMeters = null,
                    routeMinutes = null,
                    gpsAccuracyMeters = accuracy,
                    gpsWarning = appContext.getString(R.string.gps_position_outside_map),
                    isGpsActive = true,
                )
            }
            return
        }
        val point = MapPoint(floor = floorNumber, x = svgPoint.first.toFloat(), y = svgPoint.second.toFloat())
        lastPositionFloor = floorNumber
        val warning = if (accuracy == null) appContext.getString(R.string.gps_accuracy_missing) else null
        _state.update {
            it.copy(
                userPosition = point,
                isManualPositionConfirmed = false,
                gpsAccuracyMeters = accuracy,
                gpsWarning = warning,
                isGpsActive = true,
            )
        }
        if (_state.value.settings.automaticRouteOnSelection) {
            _state.value.destination?.let { destination ->
                val latest = _state.value
                when {
                    latest.route == null -> buildRoute(destination)
                    latest.isNavigationStarted -> updateRouteProgress(point)
                }
            }
        }
        maybeNotifyArrival(point, accuracy)
    }

    private fun onGpsAvailability(isAvailable: Boolean) {
        if (isAvailable || _state.value.locationMode != LocationMode.GPS) return
        val hasFreshFix = lastGpsLocation?.let(::isFreshLocation) == true
        if (!hasFreshFix) {
            _state.update {
                it.copy(
                    userPosition = null,
                    gpsCoordinates = null,
                    isManualPositionConfirmed = false,
                    isGpsActive = false,
                    gpsWarning = appContext.getString(R.string.gps_unavailable),
                )
            }
        }
    }

    private fun onGpsError(message: String) {
        if (_state.value.locationMode == LocationMode.GPS) {
            gpsTimeoutJob?.cancel()
            gpsTimeoutJob = null
            gpsAttempt++
            locationTracker.stop()
            _state.update {
                it.copy(
                    gpsCoordinates = null,
                    isGpsActive = false,
                    gpsWarning = appContext.getString(R.string.gps_error, message),
                )
            }
        }
    }

    /** Вызывается после фактического перехода на другой этаж: этаж нельзя надёжно вывести из GPS. */
    fun confirmGpsFloor(floorNumber: Int) {
        val location = lastGpsLocation
        if (_state.value.locationMode != LocationMode.GPS || location == null || !isFreshLocation(location)) {
            lastGpsLocation = null
            showMessage(appContext.getString(R.string.gps_floor_no_fix))
            return
        }
        if (navigationData?.floors?.any { it.number == floorNumber } != true) {
            showMessage(appContext.getString(R.string.floor_not_found))
            return
        }
        gpsFloorNumber = floorNumber
        lastPositionFloor = floorNumber
        updateGpsPosition(location, floorNumber)
    }

    fun navigateTo(room: Room) {
        stopRouteSpeech()
        arrivalRoomId = null
        routeSession++
        if (!room.routeAvailable || room.nodeId.isNullOrBlank()) {
            selectFloor(room.floor, userSelected = false)
            _state.update {
                it.copy(
                    destination = null,
                    selectedRoom = room,
                    route = null,
                    routeOriginPosition = null,
                    isNavigationStarted = false,
                    routeSteps = emptyList(),
                    currentRouteStepIndex = 0,
                    routeProgress = 0f,
                    routeDistanceMeters = null,
                    routeMinutes = null,
                )
            }
            showMessage(appContext.getString(R.string.room_route_unavailable, room.localizedName(appContext)))
            return
        }
        _state.update {
            it.copy(
                destination = room,
                selectedRoom = null,
                route = null,
                routeOriginPosition = null,
                isNavigationStarted = false,
                routeSteps = emptyList(),
                currentRouteStepIndex = 0,
                routeProgress = 0f,
                routeDistanceMeters = null,
                routeMinutes = null,
                message = null,
            )
        }
        val position = _state.value.userPosition
        if (position == null) {
            selectFloor(room.floor, userSelected = false)
            showMessage(appContext.getString(R.string.manual_start_hint))
            return
        }
        // При начале маршрута показываем план этажа, на котором пользователь находится сейчас.
        selectFloor(position.floor)
        buildRoute(room)
    }

    /** Находит ближайшую подходящую комнату по коридорному маршруту сайта. */
    fun navigateToNearest(role: String) {
        val state = _state.value
        val position = state.userPosition
        val graph = navigationData
        if (position == null || graph == null) {
            showMessage(appContext.getString(R.string.quick_action_location_required))
            return
        }
        if (!SiteIndoorRouter.isInsideCollege(position.floor, position.x.toDouble(), position.y.toDouble(), state.rooms)) {
            showMessage(appContext.getString(R.string.manual_position_outside_map))
            return
        }
        if (isInsideRestrictedArea(graph, position.floor, position.x.toDouble(), position.y.toDouble())) {
            showMessage(appContext.getString(R.string.route_start_in_restricted_area))
            return
        }
        nonRouteableRoomAt(position)?.let { room ->
            showMessage(appContext.getString(R.string.room_route_unavailable, room.localizedName(appContext)))
            return
        }
        val transition = routeTransition(state.settings)
        val destination = state.rooms.asSequence()
            .filter { it.role?.equals(role, ignoreCase = true) == true && it.routeAvailable && !it.nodeId.isNullOrBlank() }
            .mapNotNull { room ->
                val plan = SiteIndoorRouter.plan(
                    start = position,
                    target = room,
                    rooms = state.rooms,
                    transition = transition,
                    startRoomId = manualStartRoomId.takeIf { state.locationMode == LocationMode.MANUAL },
                    routeSession = routeSession,
                ) ?: return@mapNotNull null
                val routeGraph = SiteIndoorRouter.graphWithRouteTransitions(graph, plan.nodes)
                val destinationDoor = routeDestinationAtDoor(room, plan.nodes)
                val distance = RouteProgressCalculator.distances(routeGraph, plan.nodes, position, destinationDoor).totalMeters
                room to distance
            }
            .minByOrNull { it.second }
            ?.first
        if (destination == null) {
            showMessage(appContext.getString(R.string.quick_action_destination_not_found))
            return
        }
        navigateTo(destination)
    }

    /** Повторяет сохранённый маршрут, фиксируя сохранённое помещение-старт как ручную точку. */
    fun replaySavedRoute(fromRoomId: String?, toRoomId: String) {
        val destination = _state.value.rooms.firstOrNull { it.id == toRoomId }
        if (destination == null) {
            showMessage(appContext.getString(R.string.room_not_in_catalog))
            return
        }
        clearRoute()
        setLocationMode(LocationMode.MANUAL)
        val startRoom = fromRoomId?.let { id -> _state.value.rooms.firstOrNull { it.id == id } }
        val startX = startRoom?.centerX ?: startRoom?.x
        val startY = startRoom?.centerY ?: startRoom?.y
        if (startRoom != null && startX != null && startY != null) {
            selectFloor(startRoom.floor, userSelected = false)
            setManualPositionInRoom(startRoom, startX.toFloat(), startY.toFloat())
        }
        navigateTo(destination)
    }

    private fun buildRoute(room: Room) {
        val graph = navigationData ?: return
        val current = _state.value
        val position = current.userPosition ?: return

        fun failRoute(message: String) {
            stopRouteSpeech()
            announcedRouteSession = -1L
            _state.update {
                it.copy(
                    route = null,
                    routeOriginPosition = null,
                    isNavigationStarted = false,
                    routeSteps = emptyList(),
                    currentRouteStepIndex = 0,
                    routeProgress = 0f,
                    routeDistanceMeters = null,
                    routeMinutes = null,
                    message = message,
                )
            }
        }

        if (!SiteIndoorRouter.isInsideCollege(position.floor, position.x.toDouble(), position.y.toDouble(), current.rooms)) {
            failRoute(appContext.getString(R.string.manual_position_outside_map))
            return
        }
        nonRouteableRoomAt(position)?.let { nonRouteableRoom ->
            failRoute(appContext.getString(R.string.room_route_unavailable, nonRouteableRoom.localizedName(appContext)))
            return
        }
        if (isInsideRestrictedArea(graph, position.floor, position.x.toDouble(), position.y.toDouble())) {
            failRoute(appContext.getString(R.string.route_start_in_restricted_area))
            return
        }
        if (!room.routeAvailable || room.nodeId.isNullOrBlank()) {
            showMessage(appContext.getString(R.string.room_route_unavailable, room.localizedName(appContext)))
            return
        }

        val plan = SiteIndoorRouter.plan(
            start = position,
            target = room,
            rooms = current.rooms,
            transition = routeTransition(current.settings),
            startRoomId = manualStartRoomId.takeIf { current.locationMode == LocationMode.MANUAL },
            routeSession = routeSession,
        )
        val route = plan?.nodes?.takeIf { it.isNotEmpty() }
        if (route == null) {
            failRoute(appContext.getString(R.string.route_not_found))
            return
        }

        val routeGraph = SiteIndoorRouter.graphWithRouteTransitions(graph, route)
        val destinationAtDoor = routeDestinationAtDoor(room, route)
        val routeDistances = RouteProgressCalculator.distances(routeGraph, route, position, destinationAtDoor)
        val distance = routeDistances.totalMeters
        val speedMetersPerMinute = current.settings.walkingSpeedKmh * 1_000.0 / 60.0
        val minutes = ceil(distance / speedMetersPerMinute).toInt().coerceAtLeast(1)
        val generatedSteps = RouteStepGenerator.generate(
            graph = routeGraph,
            path = route,
            destinationName = room.localizedName(appContext),
            totalDistanceMeters = distance,
            startConnectorMeters = routeDistances.startConnectorMeters,
        )
        val selectedStartRoom = manualStartRoomId
            ?.let { id -> current.rooms.firstOrNull { it.id == id && it.floor == position.floor } }
        val routeSteps = generatedSteps.mapIndexed { index, instruction ->
            if (index == 0 && instruction.action == RouteAction.START) {
                instruction.copy(
                    destinationName = selectedStartRoom?.localizedName(appContext),
                    distanceMeters = routeDistances.startConnectorMeters,
                )
            } else instruction
        }
        _state.update {
            it.copy(
                destination = room,
                selectedRoom = null,
                route = route,
                routeOriginPosition = position,
                routeSteps = routeSteps,
                currentRouteStepIndex = 0,
                routeProgress = 0f,
                routeDistanceMeters = distance,
                routeMinutes = minutes,
                message = null,
            )
        }
        writeDebugLog("Site route built for destination ${room.id}; units=${route.size}; distanceMeters=${distance.toInt()}")
    }

    /** Начинает пошаговое ведение только после явного подтверждения пользователя. */
    fun startNavigation() {
        val destination = _state.value.destination ?: return
        val position = _state.value.userPosition
        if (position == null) {
            showMessage(appContext.getString(R.string.manual_start_hint))
            return
        }
        if (_state.value.isNavigationStarted) return
        // Для GPS предпросмотр мог устареть, поэтому перед стартом строим путь от свежей точки.
        buildRoute(destination)
        val current = _state.value
        if (current.route == null) return
        _state.update { it.copy(isNavigationStarted = true) }
        recordRouteHistory(
            room = destination,
            position = position,
            distanceMeters = current.routeDistanceMeters ?: 0.0,
            minutes = current.routeMinutes ?: 1,
        )
        announceRoute(destination, current.routeDistanceMeters ?: 0.0, current.routeMinutes ?: 1)
        writeDebugLog("Navigation started for destination ${destination.id}")
    }

    private fun announceRoute(room: Room, distanceMeters: Double, minutes: Int) {
        if (!_state.value.settings.voiceGuidanceEnabled || announcedRouteSession == routeSession) return
        announcedRouteSession = routeSession
        pendingSpeech = appContext.getString(
            R.string.route_summary_speech,
            room.localizedName(appContext),
            distanceMeters.toInt(),
            minutes,
        )
        ensureTextToSpeech()
    }

    private fun requestedSpeechLocale(): Locale = when (_state.value.settings.speechLanguage) {
        SpeechLanguage.RUSSIAN -> Locale.forLanguageTag("ru-RU")
        SpeechLanguage.ENGLISH -> Locale.forLanguageTag("en-US")
        SpeechLanguage.SYSTEM -> appContext.resources.configuration.locales
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?: Locale.getDefault()
    }

    /** Текст шага формируется из локализуемых ресурсов, а не хранится в данных графа. */
    private fun routeStepText(instruction: RouteInstruction?): String {
        if (instruction == null) return appContext.getString(R.string.route_no_steps)
        val floor = instruction.targetFloor ?: _state.value.destination?.floor ?: _state.value.selectedFloor
        return when (instruction.action) {
            RouteAction.START -> instruction.destinationName?.let { appContext.getString(R.string.step_start_room, it) }
                ?: appContext.getString(R.string.step_start)
            RouteAction.STRAIGHT -> appContext.getString(R.string.step_straight_voice, instruction.distanceMeters.toInt().coerceAtLeast(1))
            RouteAction.TURN_LEFT -> appContext.getString(R.string.step_turn_left)
            RouteAction.TURN_RIGHT -> appContext.getString(R.string.step_turn_right)
            RouteAction.SLIGHT_LEFT -> appContext.getString(R.string.step_slight_left)
            RouteAction.SLIGHT_RIGHT -> appContext.getString(R.string.step_slight_right)
            RouteAction.U_TURN -> appContext.getString(R.string.step_u_turn)
            RouteAction.DOOR -> instruction.destinationName?.let { appContext.getString(R.string.step_door, it) }
                ?: appContext.getString(R.string.step_door_generic)
            RouteAction.STAIRS_UP -> appContext.getString(R.string.step_stairs_up, floor)
            RouteAction.STAIRS_DOWN -> appContext.getString(R.string.step_stairs_down, floor)
            RouteAction.ELEVATOR -> appContext.getString(R.string.step_elevator, floor)
            RouteAction.ARRIVAL -> instruction.destinationName?.let { appContext.getString(R.string.step_arrival, it) }
                ?: appContext.getString(R.string.step_start)
        }
    }

    private fun announceCurrentRouteStep() {
        if (!_state.value.settings.voiceGuidanceEnabled) return
        pendingSpeech = routeStepText(_state.value.routeSteps.getOrNull(_state.value.currentRouteStepIndex))
        ensureTextToSpeech()
    }

    /** В ручном режиме пользователь сам отмечает выполнение текущей подсказки. */
    fun advanceRouteStep() {
        val current = _state.value
        if (!current.isNavigationStarted || current.locationMode != LocationMode.MANUAL || current.routeSteps.isEmpty()) return
        val nextIndex = (current.currentRouteStepIndex + 1).coerceAtMost(current.routeSteps.lastIndex)
        if (nextIndex == current.currentRouteStepIndex) return
        val step = current.routeSteps[nextIndex]
        _state.update {
            it.copy(
                currentRouteStepIndex = nextIndex,
                routeProgress = maxOf(it.routeProgress, step.progress).coerceIn(0f, 1f),
            )
        }
        announceCurrentRouteStep()
    }

    /** Возвращает ручное пошаговое ведение к предыдущей подсказке. */
    fun previousRouteStep() {
        val current = _state.value
        if (!current.isNavigationStarted || current.locationMode != LocationMode.MANUAL || current.routeSteps.isEmpty()) return
        val previousIndex = (current.currentRouteStepIndex - 1).coerceAtLeast(0)
        if (previousIndex == current.currentRouteStepIndex) return
        val step = current.routeSteps[previousIndex]
        _state.update {
            it.copy(
                currentRouteStepIndex = previousIndex,
                routeProgress = step.progress.coerceIn(0f, 1f),
            )
        }
        announceCurrentRouteStep()
    }

    /** Сопоставляет GPS-точку с ближайшим отрезком связанной линии маршрута. */
    private fun updateRouteProgress(point: MapPoint) {
        val current = _state.value
        if (!current.isNavigationStarted) return
        val path = current.route ?: return
        val destination = current.destination ?: return
        val graph = navigationData ?: return
        val routeGraph = SiteIndoorRouter.graphWithRouteTransitions(graph, path)
        val projection = RouteProgressCalculator.project(
            graph = routeGraph,
            route = path,
            origin = current.routeOriginPosition,
            destination = routeDestinationAtDoor(destination, path),
            point = point,
        ) ?: return

        if (projection.deviationMeters > OFF_ROUTE_DISTANCE_METERS) {
            val now = SystemClock.elapsedRealtime()
            if (lastRouteRecalculationAt == 0L || now - lastRouteRecalculationAt >= ROUTE_RECALCULATION_COOLDOWN_MS) {
                lastRouteRecalculationAt = now
                buildRoute(destination)
                if (_state.value.route != null) {
                    showMessage(appContext.getString(R.string.route_recalculated_status))
                }
            }
            return
        }

        val progress = projection.progress
        val nextIndex = current.routeSteps.indexOfLast { it.progress <= progress + ROUTE_STEP_PROGRESS_EPSILON }
            .coerceAtLeast(0)
        val changedStep = nextIndex != current.currentRouteStepIndex
        _state.update { it.copy(routeProgress = progress, currentRouteStepIndex = nextIndex) }
        if (changedStep && nextIndex > 0) announceCurrentRouteStep()
    }

    private fun stopRouteSpeech() {
        pendingSpeech = null
        textToSpeech?.stop()
    }

    private fun ensureTextToSpeech() {
        if (textToSpeech != null) {
            speakPendingRoute()
            return
        }
        try {
            textToSpeech = TextToSpeech(getApplication()) { status ->
                val engine = textToSpeech ?: return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) {
                    pendingSpeech = null
                    showMessage(appContext.getString(R.string.tts_unavailable))
                    return@TextToSpeech
                }
                val speechLocale = requestedSpeechLocale()
                val languageStatus = engine.setLanguage(speechLocale)
                if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                    pendingSpeech = null
                    val missingVoiceString = if (speechLocale.language == Locale.ENGLISH.language) {
                        R.string.tts_missing_english
                    } else {
                        R.string.tts_missing_russian
                    }
                    showMessage(appContext.getString(missingVoiceString))
                    return@TextToSpeech
                }
                textToSpeechReady = true
                speakPendingRoute()
            }
        } catch (_: Exception) {
            pendingSpeech = null
            showMessage(
                appContext.getString(
                    R.string.tts_start_error,
                    appContext.getString(R.string.tts_unavailable),
                ),
            )
        }
    }

    private fun speakPendingRoute() {
        val engine = textToSpeech ?: return
        if (!textToSpeechReady) return
        val text = pendingSpeech ?: return
        pendingSpeech = null
        val status = try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "route-summary")
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
        if (status == TextToSpeech.ERROR) {
            showMessage(
                appContext.getString(
                    R.string.tts_start_error,
                    appContext.getString(R.string.tts_unavailable),
                ),
            )
        }
    }

    private fun recordRouteHistory(room: Room, position: MapPoint, distanceMeters: Double, minutes: Int) {
        val settings = _state.value.settings
        if (!settings.saveRouteHistory || recordedRouteSession == routeSession) return
        recordedRouteSession = routeSession
        val graph = navigationData ?: return
        val floor = graph.floors.firstOrNull { it.number == position.floor }
        val nearestRoom = _state.value.rooms.asSequence()
            .filter { it.floor == position.floor }
            .mapNotNull { candidate ->
                val x = candidate.x ?: return@mapNotNull null
                val y = candidate.y ?: return@mapNotNull null
                candidate to hypot(x - position.x.toDouble(), y - position.y.toDouble())
            }
            .minByOrNull { it.second }
            ?.takeIf { (_, distanceInSvgUnits) ->
                val scale = floor?.metersPerUnit ?: DEFAULT_METERS_PER_UNIT
                distanceInSvgUnits * scale <= 3.0
            }
            ?.first
        val timestamp = System.currentTimeMillis()
        val entry = RouteHistoryEntity(
            fromRoomId = nearestRoom?.id,
            toRoomId = room.id,
            timestamp = timestamp,
            durationSeconds = (minutes * 60).coerceAtLeast(1),
            pathLengthMeters = distanceMeters,
            fromLabel = nearestRoom?.localizedName(appContext)
                ?: appContext.getString(R.string.history_start_point, appContext.getString(R.string.floor_number_format, position.floor)),
            fromFloor = position.floor,
        )
        viewModelScope.launch {
            try {
                favoritesRepository.recordRoute(entry, settings.maximumHistoryRecords)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage(
                    appContext.getString(
                        R.string.favorite_history_error,
                        appContext.getString(R.string.operation_failed),
                    ),
                )
            }
        }
    }

    /** Сайт не проводит линию от двери до центра кабинета: конечной точкой остаётся дверь. */
    private fun routeDestinationAtDoor(room: Room, route: List<NavNode>): Room {
        val door = route.lastOrNull()?.takeIf { it.floor == room.floor } ?: return room
        return room.copy(x = door.x, y = door.y)
    }

    /** Лифт доступен только при явном включении, как и в старом графе; иначе выбирается кратчайшая лестница. */
    private fun routeTransition(settings: AppSettings): SiteIndoorRouter.Transition =
        if (settings.useElevator) SiteIndoorRouter.Transition.LIFT else SiteIndoorRouter.Transition.STAIRS

    private fun nonRouteableRoomAt(point: MapPoint): Room? = _state.value.rooms.firstOrNull { room ->
        if (room.floor != point.floor || room.routeAvailable) return@firstOrNull false
        val polygons = if (room.polygon.isEmpty()) room.polygons else room.polygons + listOf(room.polygon)
        polygons.any { polygon -> pointInPolygonOrBoundary(point.x.toDouble(), point.y.toDouble(), polygon) }
    }

    /** Запрещает начинать маршрут из наблюдательной и прочих закрытых серых областей. */
    private fun isInsideRestrictedArea(graph: NavigationData, floor: Int, x: Double, y: Double): Boolean =
        graph.restrictedAreas.asSequence()
            .filter { it.floor == floor }
            .flatMap { it.polygons.asSequence() }
            .any { polygon -> pointInPolygonOrBoundary(x, y, polygon) }

    private fun pointInPolygonOrBoundary(x: Double, y: Double, polygon: List<SvgPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            val cross = (x - previous.x) * (current.y - previous.y) -
                (y - previous.y) * (current.x - previous.x)
            val onSegment = abs(cross) <= RESTRICTED_BOUNDARY_EPSILON &&
                x in minOf(previous.x, current.x)..maxOf(previous.x, current.x) &&
                y in minOf(previous.y, current.y)..maxOf(previous.y, current.y)
            if (onSegment) return true
            if ((current.y > y) != (previous.y > y)) {
                val crossingX = (previous.x - current.x) * (y - current.y) /
                    (previous.y - current.y) + current.x
                if (x < crossingX) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun maybeNotifyArrival(point: MapPoint, accuracy: Float?) {
        val destination = _state.value.destination ?: return
        if (_state.value.locationMode != LocationMode.GPS || accuracy == null ||
            !accuracy.isFinite() || accuracy > _state.value.settings.minimumAccuracyMeters
        ) return
        if (point.floor != destination.floor) return
        val routeEndpoint = _state.value.route?.lastOrNull()?.takeIf { it.floor == point.floor }
        val destinationX = routeEndpoint?.x ?: destination.x ?: return
        val destinationY = routeEndpoint?.y ?: destination.y ?: return
        val floor = navigationData?.floors?.firstOrNull { it.number == point.floor } ?: return
        val distanceMeters = hypot(destinationX - point.x, destinationY - point.y) * floor.metersPerUnit
        if (distanceMeters <= ARRIVAL_RADIUS_METERS && arrivalRoomId != destination.id) {
            arrivalRoomId = destination.id
            if (_state.value.settings.arrivalVibrationEnabled) vibrate()
            showMessage(appContext.getString(R.string.arrival_message, destination.localizedName(appContext)))
        }
    }

    private fun vibrate() {
        val context = getApplication<Application>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(ARRIVAL_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(ARRIVAL_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(ARRIVAL_VIBRATION_MS)
                }
            }
        } catch (_: SecurityException) {
            // Вибрация — необязательная функция; отсутствие доступа не прерывает навигацию.
        }
    }

    fun clearRoute() {
        arrivalRoomId = null
        stopRouteSpeech()
        _state.update {
            it.copy(
                route = null,
                routeOriginPosition = null,
                isNavigationStarted = false,
                routeSteps = emptyList(),
                currentRouteStepIndex = 0,
                routeProgress = 0f,
                destination = null,
                routeDistanceMeters = null,
                routeMinutes = null,
                selectedRoom = null,
                message = null,
            )
        }
    }

    fun goToMyLocation() {
        val current = _state.value
        val position = current.userPosition
        if (position == null) {
            if (current.locationMode == LocationMode.GPS) {
                if (current.isGpsActive) showMessage(appContext.getString(R.string.gps_determining))
                else setLocationMode(LocationMode.GPS)
            } else {
                showMessage(appContext.getString(R.string.manual_position_required))
            }
        } else if (position.floor != current.selectedFloor) {
            showMessage(appContext.getString(R.string.location_on_other_floor, position.floor))
        }
    }

    fun shareLocationText(): String {
        val current = _state.value
        val point = current.userPosition
        val mayNameNearbyRoom = (current.locationMode == LocationMode.MANUAL && current.isManualPositionConfirmed) ||
            (current.locationMode == LocationMode.GPS &&
                current.gpsAccuracyMeters?.let { it.isFinite() && it <= current.settings.minimumAccuracyMeters } == true)
        val nearby = point?.takeIf { mayNameNearbyRoom }?.let { location ->
            val metersPerUnit = current.navigation?.floors
                ?.firstOrNull { it.number == location.floor }
                ?.metersPerUnit ?: return@let null
            current.rooms.asSequence()
                .filter { it.floor == location.floor && it.routeAvailable }
                .mapNotNull { room ->
                    val x = room.x ?: return@mapNotNull null
                    val y = room.y ?: return@mapNotNull null
                    room to hypot(x - location.x, y - location.y)
                }
                .minByOrNull { it.second }
                ?.takeIf { it.second * metersPerUnit <= SHARE_NEARBY_LIMIT_METERS }
                ?.first
        }
        val floorLabel = point?.let { appContext.getString(R.string.floor_number_format, it.floor) }.orEmpty()
        val locationLabel = when {
            point == null -> appContext.getString(R.string.share_location_unknown)
            nearby != null -> appContext.getString(
                R.string.share_near_room,
                floorLabel,
                nearby.localizedName(appContext),
                nearby.number?.let { appContext.getString(R.string.share_cabinet_number, it) }.orEmpty(),
            )
            else -> appContext.getString(R.string.share_floor, floorLabel)
        }
        return appContext.getString(
            R.string.share_location_prefix,
            appContext.getString(R.string.organization_name),
            locationLabel,
        )
    }

    /** Диагностический журнал хранится только во внутреннем каталоге приложения и не содержит координат. */
    private fun writeDebugLog(event: String) {
        if (!_state.value.settings.fileLoggingEnabled) return
        val safeEvent = event.replace('\n', ' ').take(240)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                synchronized(debugLogLock) {
                    debugLogFile.parentFile?.mkdirs()
                    if (debugLogFile.length() > MAX_DEBUG_LOG_BYTES) debugLogFile.writeText("", Charsets.UTF_8)
                    debugLogFile.appendText("${System.currentTimeMillis()} $safeEvent\n", Charsets.UTF_8)
                }
            }
        }
    }

    /** Изменения настроек отображаются сразу, а DataStore сохраняет их транзакционно. */
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val previous = _state.value.settings
        val updated = transform(previous)
        _state.update { it.copy(settings = updated) }
        applySettingsToRuntime(previous, updated)
        viewModelScope.launch {
            try {
                settingsStore.update(transform)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage(
                    appContext.getString(
                        R.string.settings_save_error,
                        appContext.getString(R.string.settings_store_error),
                    ),
                )
            }
        }
    }

    /** Обновляет текущую GPS-сессию, маршрут и озвучивание после изменения настроек. */
    private fun applySettingsToRuntime(previous: AppSettings, updated: AppSettings) {
        if ((previous.locationAccuracyMode != updated.locationAccuracyMode ||
                previous.locationUpdateIntervalSeconds != updated.locationUpdateIntervalSeconds) &&
            _state.value.locationMode == LocationMode.GPS
        ) {
            startGpsTracking()
        }
        if (previous.routePreference != updated.routePreference ||
            previous.walkingSpeedKmh != updated.walkingSpeedKmh ||
            previous.useElevator != updated.useElevator
        ) {
            _state.value.destination?.let(::buildRoute)
        }
        if (previous.speechLanguage != updated.speechLanguage) {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            textToSpeechReady = false
            pendingSpeech = null
            if (updated.voiceGuidanceEnabled && _state.value.routeSteps.isNotEmpty()) announceCurrentRouteStep()
        }
        if (previous.voiceGuidanceEnabled && !updated.voiceGuidanceEnabled) {
            stopRouteSpeech()
        }
        if (!previous.voiceGuidanceEnabled && updated.voiceGuidanceEnabled) {
            announcedRouteSession = -1L
            _state.value.destination?.let { destination ->
                val distance = _state.value.routeDistanceMeters
                val minutes = _state.value.routeMinutes
                if (distance != null && minutes != null) announceRoute(destination, distance, minutes)
            }
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            try {
                val previous = _state.value.settings
                settingsStore.resetToDefaults()
                val defaults = settingsStore.settingsFlow.first()
                _state.update { it.copy(settings = defaults) }
                applySettingsToRuntime(previous, defaults)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage(
                    appContext.getString(
                        R.string.settings_save_error,
                        appContext.getString(R.string.settings_store_error),
                    ),
                )
            }
        }
    }

    fun onMapTapCoordinate(x: Double, y: Double) {
        if (_state.value.settings.showTapCoordinates) {
            showMessage(appContext.getString(R.string.tap_coordinates_format, x, y))
        }
    }

    fun showMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun transitionHint(floorNumber: Int): String? {
        val route = _state.value.route ?: return null
        val crossings = route.zipWithNext().filter { (from, to) -> from.floor != to.floor }
        if (crossings.isEmpty()) return null

        val outgoing = crossings.firstOrNull { (from, _) -> from.floor == floorNumber }
        if (outgoing != null) {
            val (from, to) = outgoing
            val transitionType = navigationData?.edges?.firstOrNull { edge ->
                (edge.from == from.id && edge.to == to.id) ||
                    (edge.bidirectional != false && edge.from == to.id && edge.to == from.id)
            }?.type
            val isElevator = transitionType == "elevator" || from.type == "elevator" || to.type == "elevator"
            val messageId = when {
                isElevator -> R.string.transition_to_elevator_generic
                to.floor > from.floor -> R.string.transition_to_stairs_up_generic
                else -> R.string.transition_to_stairs_down_generic
            }
            return appContext.getString(messageId, to.floor)
        }

        if (crossings.none { (_, to) -> to.floor == floorNumber }) return null
        return appContext.getString(R.string.transition_after_floor, floorNumber)
    }

    override fun onCleared() {
        locationTracker.stop()
        compassTracker.stop()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        gpsTimeoutJob?.cancel()
        dataLoadJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_METERS_PER_UNIT = 0.083333
        const val RESTRICTED_BOUNDARY_EPSILON = 1e-6
        const val MAX_DEBUG_LOG_BYTES = 256_000L
        const val GPS_FIRST_FIX_TIMEOUT_MS = 12_000L
        const val MAX_LOCATION_AGE_MS = 120_000L
        const val MAX_FUTURE_FIX_SKEW_MS = 5_000L
        const val MAX_LOCATION_AGE_NANOS = 120_000_000_000L
        const val MAX_FUTURE_FIX_SKEW_NANOS = 5_000_000_000L
        const val SHARE_NEARBY_LIMIT_METERS = 20.0
        const val ARRIVAL_RADIUS_METERS = 3.5
        const val OFF_ROUTE_DISTANCE_METERS = 15.0
        const val ROUTE_RECALCULATION_COOLDOWN_MS = 10_000L
        const val ROUTE_STEP_PROGRESS_EPSILON = 0.0005f
        const val ARRIVAL_VIBRATION_MS = 180L
    }
}
