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
import com.logos.navigator.navigation.RouteStepGenerator
import com.logos.navigator.navigation.pathfinding.AStarPathfinder
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
    private var mapLoadJob: Job? = null
    private var gpsTimeoutJob: Job? = null
    private var gpsAttempt = 0
    private var arrivalRoomId: String? = null
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
                        routeSteps = emptyList(),
                        currentRouteStepIndex = 0,
                        routeProgress = 0f,
                        routeDistanceMeters = null,
                        routeMinutes = null,
                    )
                }
                loadFloorSvg(firstFloor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isMapLoading = false,
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

    fun retryMap() {
        val floor = navigationData?.floors?.firstOrNull { it.number == _state.value.selectedFloor } ?: return
        loadFloorSvg(floor)
    }

    fun selectFloor(floorNumber: Int, userSelected: Boolean = true) {
        val floor = navigationData?.floors?.firstOrNull { it.number == floorNumber } ?: return
        if (userSelected && _state.value.locationMode == LocationMode.MANUAL) {
            lastPositionFloor = floorNumber
        }
        if (_state.value.selectedFloor == floorNumber && _state.value.floorSvg != null) return
        _state.update { it.copy(selectedFloor = floorNumber, floorSvg = null, mapError = null) }
        viewModelScope.launch { settingsStore.setLastOpenedFloor(floorNumber) }
        loadFloorSvg(floor)
        // Намеренно не переносим GPS-маркер на другой этаж: GPS не определяет этаж здания.
    }

    private fun loadFloorSvg(floor: FloorPlan) {
        mapLoadJob?.cancel()
        _state.update { it.copy(isMapLoading = true, mapError = null, floorSvg = null) }
        mapLoadJob = viewModelScope.launch {
            try {
                val svg = repository.loadFloorSvg(floor)
                if (_state.value.selectedFloor == floor.number) {
                    _state.update { it.copy(floorSvg = svg, isMapLoading = false, mapError = null) }
                    writeDebugLog("Loaded SVG floor ${floor.number}: ${floor.mapAsset}")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                writeDebugLog("SVG load failed on floor ${floor.number}: ${exception.javaClass.simpleName}")
                if (_state.value.selectedFloor == floor.number) {
                    _state.update {
                        it.copy(
                            floorSvg = null,
                            isMapLoading = false,
                            mapError = appContext.getString(
                                R.string.svg_floor_missing,
                                appContext.getString(R.string.floor_number_format, floor.number),
                                "\n${appContext.getString(R.string.svg_check_file_viewbox, floor.mapAsset)}",
                            ),
                        )
                    }
                }
            }
        }
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
        _state.update {
            it.copy(
                destination = room,
                selectedRoom = room,
                route = null,
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
        val current = _state.value
        val floor = navigationData?.floors?.firstOrNull { it.number == current.selectedFloor } ?: return
        if (current.locationMode != LocationMode.MANUAL) return
        lastPositionFloor = floor.number
        val point = MapPoint(
            floor = floor.number,
            x = x.coerceIn(floor.viewBoxX.toFloat(), (floor.viewBoxX + floor.width).toFloat()),
            y = y.coerceIn(floor.viewBoxY.toFloat(), (floor.viewBoxY + floor.height).toFloat()),
        )
        val graph = navigationData ?: return
        if (isInsideRestrictedArea(graph, point.floor, point.x.toDouble(), point.y.toDouble())) {
            showMessage(appContext.getString(R.string.route_start_in_restricted_area))
            return
        }
        _state.update {
            it.copy(
                userPosition = point,
                isManualPositionConfirmed = true,
                gpsAccuracyMeters = null,
                gpsWarning = null,
            )
        }
        if (_state.value.settings.automaticRouteOnSelection) current.destination?.let(::buildRoute)
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
                    isGpsActive = false,
                    gpsAccuracyMeters = null,
                    gpsWarning = null,
                )
            }
            return
        }

        stopRouteSpeech()
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
                isManualPositionConfirmed = false,
                route = null,
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
        val point = MapPoint(
            floor = floorNumber,
            x = svgPoint.first.toFloat().coerceIn(floor.viewBoxX.toFloat(), (floor.viewBoxX + floor.width).toFloat()),
            y = svgPoint.second.toFloat().coerceIn(floor.viewBoxY.toFloat(), (floor.viewBoxY + floor.height).toFloat()),
        )
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
                if (_state.value.route == null) buildRoute(destination) else updateRouteProgress(point)
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

    /** Находит доступный санузел или лестницу по фактическому положению пользователя. */
    fun navigateToNearest(role: String) {
        val state = _state.value
        val position = state.userPosition
        val graph = navigationData
        if (position == null || graph == null) {
            showMessage(appContext.getString(R.string.quick_action_location_required))
            return
        }
        if (isInsideRestrictedArea(graph, position.floor, position.x.toDouble(), position.y.toDouble())) {
            showMessage(appContext.getString(R.string.route_start_in_restricted_area))
            return
        }
        val start = AStarPathfinder.nearestNode(graph, position.floor, position.x.toDouble(), position.y.toDouble())
        if (start == null) {
            showMessage(appContext.getString(R.string.route_no_graph_nodes))
            return
        }
        val destination = state.rooms.asSequence()
            .filter { it.role?.equals(role, ignoreCase = true) == true && it.routeAvailable && !it.nodeId.isNullOrBlank() }
            .mapNotNull { room ->
                val path = AStarPathfinder.findPath(
                    graph = graph,
                    startId = start.id,
                    goalId = room.nodeId ?: return@mapNotNull null,
                    preference = state.settings.routePreference,
                    useElevator = state.settings.useElevator,
                ) ?: return@mapNotNull null
                room to graphPathCost(graph, path)
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
        val startX = startRoom?.x
        val startY = startRoom?.y
        if (startRoom != null && startX != null && startY != null) {
            selectFloor(startRoom.floor, userSelected = false)
            setManualPosition(startX.toFloat(), startY.toFloat())
        }
        navigateTo(destination)
    }

    private fun buildRoute(room: Room) {
        val graph = navigationData ?: return
        val position = _state.value.userPosition ?: return
        if (isInsideRestrictedArea(graph, position.floor, position.x.toDouble(), position.y.toDouble())) {
            _state.update {
                it.copy(
                    route = null,
                    routeSteps = emptyList(),
                    currentRouteStepIndex = 0,
                    routeProgress = 0f,
                    routeDistanceMeters = null,
                    routeMinutes = null,
                    message = appContext.getString(R.string.route_start_in_restricted_area),
                )
            }
            return
        }
        val goalId = room.nodeId
        if (!room.routeAvailable || goalId.isNullOrBlank()) {
            showMessage(appContext.getString(R.string.room_route_unavailable, room.localizedName(appContext)))
            return
        }
        val start = AStarPathfinder.nearestNode(graph, position.floor, position.x.toDouble(), position.y.toDouble())
        if (start == null) {
            _state.update {
                it.copy(
                    route = null,
                    routeSteps = emptyList(),
                    currentRouteStepIndex = 0,
                    routeProgress = 0f,
                    routeDistanceMeters = null,
                    routeMinutes = null,
                    message = appContext.getString(R.string.route_no_graph_nodes),
                )
            }
            return
        }
        val route = AStarPathfinder.findPath(
            graph,
            start.id,
            goalId,
            _state.value.settings.routePreference,
            useElevator = _state.value.settings.useElevator,
        )
        if (route == null) {
            _state.update {
                it.copy(
                    route = null,
                    routeSteps = emptyList(),
                    currentRouteStepIndex = 0,
                    routeProgress = 0f,
                    routeDistanceMeters = null,
                    routeMinutes = null,
                    message = appContext.getString(R.string.route_not_found),
                )
            }
            return
        }
        val distance = calculateRouteDistance(graph, route, position, room)
        val speedMetersPerMinute = _state.value.settings.walkingSpeedKmh * 1_000.0 / 60.0
        val minutes = ceil(distance / speedMetersPerMinute).toInt().coerceAtLeast(1)
        val routeSteps = RouteStepGenerator.generate(
            graph = graph,
            path = route,
            destinationName = room.localizedName(appContext),
            totalDistanceMeters = distance,
        )
        _state.update {
            it.copy(
                destination = room,
                selectedRoom = null,
                route = route,
                routeSteps = routeSteps,
                currentRouteStepIndex = 0,
                routeProgress = 0f,
                routeDistanceMeters = distance,
                routeMinutes = minutes,
                message = null,
            )
        }
        recordRouteHistory(room, position, distance, minutes)
        writeDebugLog("Route built for destination ${room.id}; units=${route.size}; distanceMeters=${distance.toInt()}")
        announceRoute(room, distance, minutes)
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
            RouteAction.START -> appContext.getString(R.string.step_start)
            RouteAction.STRAIGHT -> appContext.getString(R.string.step_straight, instruction.distanceMeters.toInt().coerceAtLeast(1))
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
        if (current.locationMode != LocationMode.MANUAL || current.routeSteps.isEmpty()) return
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

    /** Сопоставляет GPS-точку с ближайшим сегментом пути; порог отклонения задан в метрах. */
    private fun updateRouteProgress(point: MapPoint) {
        val current = _state.value
        val path = current.route ?: return
        val destination = current.destination ?: return
        val graph = navigationData ?: return
        if (path.size < 2) return
        val metersPerUnit = graph.floors.firstOrNull { it.number == point.floor }?.metersPerUnit
            ?: DEFAULT_METERS_PER_UNIT
        val metersAtNode = DoubleArray(path.size)
        for (index in 0 until path.lastIndex) {
            metersAtNode[index + 1] = metersAtNode[index] + routeEdgeMeters(graph, path[index], path[index + 1])
        }

        var nearestDistanceMeters = Double.POSITIVE_INFINITY
        var traveledMeters = 0.0
        var foundProjection = false
        for (index in 0 until path.lastIndex) {
            val from = path[index]
            val to = path[index + 1]
            if (from.floor != point.floor || to.floor != point.floor) continue
            val deltaX = to.x - from.x
            val deltaY = to.y - from.y
            val segmentLengthSquared = deltaX * deltaX + deltaY * deltaY
            if (segmentLengthSquared <= 0.0001) continue
            val ratio = (((point.x - from.x) * deltaX + (point.y - from.y) * deltaY) / segmentLengthSquared)
                .coerceIn(0.0, 1.0)
            val projectedX = from.x + ratio * deltaX
            val projectedY = from.y + ratio * deltaY
            val deviation = hypot(point.x - projectedX, point.y - projectedY) * metersPerUnit
            if (deviation < nearestDistanceMeters) {
                nearestDistanceMeters = deviation
                traveledMeters = metersAtNode[index] + routeEdgeMeters(graph, from, to) * ratio
                foundProjection = true
            }
        }
        if (!foundProjection) {
            val nearest = path.withIndex()
                .filter { it.value.floor == point.floor }
                .minByOrNull { (_, node) -> hypot(point.x - node.x, point.y - node.y) }
            if (nearest != null) {
                nearestDistanceMeters = hypot(point.x - nearest.value.x, point.y - nearest.value.y) * metersPerUnit
                traveledMeters = metersAtNode[nearest.index]
                foundProjection = true
            }
        }
        if (!foundProjection) return

        if (nearestDistanceMeters > OFF_ROUTE_DISTANCE_METERS) {
            val now = SystemClock.elapsedRealtime()
            if (lastRouteRecalculationAt == 0L || now - lastRouteRecalculationAt >= ROUTE_RECALCULATION_COOLDOWN_MS) {
                lastRouteRecalculationAt = now
                buildRoute(destination)
                showMessage(appContext.getString(R.string.route_recalculated_status))
            }
            return
        }

        val totalDistance = current.routeDistanceMeters?.takeIf { it > 0.0 } ?: 1.0
        val progress = (traveledMeters / totalDistance).toFloat().coerceIn(0f, 1f)
        val nextIndex = current.routeSteps.indexOfLast { it.progress <= progress + ROUTE_STEP_PROGRESS_EPSILON }
            .coerceAtLeast(0)
        val changedStep = nextIndex != current.currentRouteStepIndex
        _state.update { it.copy(routeProgress = progress, currentRouteStepIndex = nextIndex) }
        if (changedStep && nextIndex > 0) announceCurrentRouteStep()
    }

    private fun routeEdgeMeters(graph: NavigationData, from: NavNode, to: NavNode): Double =
        graph.edges.asSequence()
            .firstOrNull {
                (it.from == from.id && it.to == to.id) ||
                    (it.bidirectional != false && it.from == to.id && it.to == from.id)
            }?.weight?.takeIf { it.isFinite() && it > 0.0 }
            ?: hypot(to.x - from.x, to.y - from.y) *
            (graph.floors.firstOrNull { it.number == from.floor }?.metersPerUnit ?: DEFAULT_METERS_PER_UNIT)

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
        runCatching { engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "route-summary") }
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
            runCatching { favoritesRepository.recordRoute(entry, settings.maximumHistoryRecords) }
                .onFailure { _ ->
                    showMessage(
                        appContext.getString(
                            R.string.favorite_history_error,
                            appContext.getString(R.string.operation_failed),
                        ),
                    )
                }
        }
    }

    private fun graphPathCost(graph: NavigationData, route: List<NavNode>): Double {
        val metersPerUnit = graph.floors.associate { it.number to it.metersPerUnit }
        return route.zipWithNext().sumOf { (from, to) ->
            val edge = graph.edges.asSequence()
                .filter {
                    (it.from == from.id && it.to == to.id) ||
                        (it.bidirectional != false && it.from == to.id && it.to == from.id)
                }
                .minOfOrNull { it.weight }
            edge ?: if (from.floor == to.floor) {
                hypot(to.x - from.x, to.y - from.y) * (metersPerUnit[from.floor] ?: DEFAULT_METERS_PER_UNIT)
            } else {
                DEFAULT_FLOOR_TRANSITION_METERS
            }
        }
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

    private fun calculateRouteDistance(
        graph: NavigationData,
        route: List<NavNode>,
        position: MapPoint,
        destination: Room,
    ): Double {
        val floors = graph.floors.associateBy(FloorPlan::number)
        var distance = 0.0
        route.zipWithNext().forEach { (from, to) ->
            val edgeWeight = graph.edges.asSequence()
                .filter {
                    (it.from == from.id && it.to == to.id) ||
                        (it.bidirectional != false && it.from == to.id && it.to == from.id)
                }
                .minOfOrNull { it.weight }
            distance += edgeWeight ?: 0.0
        }
        route.firstOrNull()?.let { first ->
            val metersPerUnit = floors[position.floor]?.metersPerUnit ?: DEFAULT_METERS_PER_UNIT
            distance += hypot(first.x - position.x, first.y - position.y) * metersPerUnit
        }
        route.lastOrNull()?.let { last ->
            val destinationX = destination.x
            val destinationY = destination.y
            if (destinationX != null && destinationY != null) {
                val metersPerUnit = floors[destination.floor]?.metersPerUnit ?: DEFAULT_METERS_PER_UNIT
                distance += hypot(last.x - destinationX, last.y - destinationY) * metersPerUnit
            }
        }
        return distance.coerceAtLeast(0.0)
    }

    private fun maybeNotifyArrival(point: MapPoint, accuracy: Float?) {
        val destination = _state.value.destination ?: return
        if (_state.value.locationMode != LocationMode.GPS || accuracy == null ||
            !accuracy.isFinite() || accuracy > _state.value.settings.minimumAccuracyMeters
        ) return
        if (point.floor != destination.floor) return
        val destinationX = destination.x ?: return
        val destinationY = destination.y ?: return
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
        val position = _state.value.userPosition
        if (position == null) showMessage(appContext.getString(R.string.manual_position_required))
        else if (position.floor != _state.value.selectedFloor) {
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
        if ((previous.locationAccuracyMode != updated.locationAccuracyMode ||
                previous.locationUpdateIntervalSeconds != updated.locationUpdateIntervalSeconds) &&
            _state.value.locationMode == LocationMode.GPS
        ) {
            startGpsTracking()
        }
        if (previous.routePreference != updated.routePreference || previous.walkingSpeedKmh != updated.walkingSpeedKmh) {
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
        if (previous.voiceGuidanceEnabled && !updated.voiceGuidanceEnabled) stopRouteSpeech()
        if (!previous.voiceGuidanceEnabled && updated.voiceGuidanceEnabled) {
            _state.value.destination?.let { destination ->
                val distance = _state.value.routeDistanceMeters
                val minutes = _state.value.routeMinutes
                if (distance != null && minutes != null) announceRoute(destination, distance, minutes)
            }
        }
        viewModelScope.launch {
            runCatching { settingsStore.update(transform) }
                .onFailure { _ ->
                    showMessage(
                        appContext.getString(
                            R.string.settings_save_error,
                            appContext.getString(R.string.settings_store_error),
                        ),
                    )
                }
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            settingsStore.resetToDefaults()
            val defaults = settingsStore.settingsFlow.first()
            _state.update { it.copy(settings = defaults) }
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
        mapLoadJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_METERS_PER_UNIT = 0.083333
        const val DEFAULT_FLOOR_TRANSITION_METERS = 4.0
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
