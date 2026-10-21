package com.logos.navigator.data.datastore

import android.content.Context
import java.io.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.logos.navigator.data.model.AppSettings
import com.logos.navigator.data.model.AppLanguage
import com.logos.navigator.data.model.DistanceUnit
import com.logos.navigator.data.model.LocationAccuracyMode
import com.logos.navigator.data.model.LocationMode
import com.logos.navigator.data.model.RouteLineStyle
import com.logos.navigator.data.model.RoutePreference
import com.logos.navigator.data.model.StartupLocationMode
import com.logos.navigator.data.model.SpeechLanguage
import com.logos.navigator.data.model.MapRenderQuality
import com.logos.navigator.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private val Context.logosSettingsStore by preferencesDataStore(name = "logos_navigator_settings")

/** DataStore-обёртка: весь экран настроек наблюдает один согласованный снимок через Flow. */
class SettingsDataStore(context: Context) {
    private val store = context.applicationContext.logosSettingsStore

    val settingsFlow: Flow<AppSettings> = store.data
        .onStart {
            // Удаляем сохранённые ключи прежней функции динамических подписей.
            store.edit { preferences ->
                if (preferences[LEGACY_ROOM_LABELS] != null) preferences.remove(LEGACY_ROOM_LABELS)
                if (preferences[LEGACY_ROOM_LABELS_DEFAULT_APPLIED] != null) {
                    preferences.remove(LEGACY_ROOM_LABELS_DEFAULT_APPLIED)
                }
            }
        }
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map(Preferences::toAppSettings)

    /** Транзакционно применяет изменение и сохраняет все значения в одном DataStore edit. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { preferences ->
            transform(preferences.toAppSettings()).normalized().writeTo(preferences)
        }
    }

    suspend fun setLastOpenedFloor(value: Int) = update { it.copy(lastOpenedFloor = value) }

    suspend fun resetToDefaults() {
        store.edit { preferences ->
            val onboardingCompleted = preferences[ONBOARDING_COMPLETED] ?: false
            preferences.clear()
            preferences[ONBOARDING_COMPLETED] = onboardingCompleted
        }
    }

    private fun AppSettings.normalized(): AppSettings {
        val minimum = minimumZoom.coerceIn(0.5f, 1f)
        return copy(
            fontScale = fontScale.coerceIn(0.8f, 1.6f),
            initialFloor = initialFloor.takeIf { it == LAST_FLOOR || it.toIntOrNull() != null } ?: LAST_FLOOR,
            lastOpenedFloor = lastOpenedFloor.coerceAtLeast(1),
            minimumZoom = minimum,
            maximumZoom = maximumZoom.coerceIn(maxOf(2f, minimum), 10f),
            routeLineWidthDp = routeLineWidthDp.coerceIn(2f, 10f),
            walkingSpeedKmh = walkingSpeedKmh.coerceIn(2f, 7f),
            locationUpdateIntervalSeconds = locationUpdateIntervalSeconds.coerceIn(1, 10),
            minimumAccuracyMeters = minimumAccuracyMeters.coerceIn(5f, 50f),
            maximumHistoryRecords = maximumHistoryRecords.coerceIn(10, 100),
        )
    }

    internal companion object {
        const val LAST_FLOOR = "last"

        internal val THEME = stringPreferencesKey("theme_mode")
        internal val ACCENT = intPreferencesKey("accent_color_argb")
        internal val FONT_SCALE = floatPreferencesKey("font_scale")
        internal val ANIMATIONS = booleanPreferencesKey("animations_enabled")
        internal val APP_LANGUAGE = stringPreferencesKey("app_language")
        private val LEGACY_ROOM_LABELS = stringPreferencesKey("room_label_mode")
        private val LEGACY_ROOM_LABELS_DEFAULT_APPLIED = booleanPreferencesKey("room_label_default_applied")
        // Сохраняем прежний ключ, чтобы не сбросить выбор качества после обновления приложения.
        internal val MAP_RENDER_QUALITY = stringPreferencesKey("svg_render_quality")
        internal val INITIAL_FLOOR = stringPreferencesKey("initial_floor")
        internal val LAST_OPENED_FLOOR = intPreferencesKey("last_opened_floor")
        internal val MAX_ZOOM = floatPreferencesKey("maximum_zoom")
        internal val MIN_ZOOM = floatPreferencesKey("minimum_zoom")
        internal val GRID = booleanPreferencesKey("coordinate_grid")
        internal val COMPASS = booleanPreferencesKey("compass_rotation")
        internal val ROUTE_COLOR = intPreferencesKey("route_color_argb")
        internal val ROUTE_WIDTH = floatPreferencesKey("route_line_width_dp")
        internal val ROUTE_STYLE = stringPreferencesKey("route_line_style")
        internal val STARTUP_LOCATION = stringPreferencesKey("startup_location_mode")
        internal val LAST_LOCATION_MODE = stringPreferencesKey("last_location_mode")
        internal val AUTO_ROUTE = booleanPreferencesKey("automatic_route_on_selection")
        internal val VIBRATION = booleanPreferencesKey("arrival_vibration")
        internal val VOICE = booleanPreferencesKey("voice_guidance")
        internal val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        internal val WALKING_SPEED = floatPreferencesKey("walking_speed_kmh")
        internal val ROUTE_PREFERENCE = stringPreferencesKey("route_preference")
        internal val GPS_ON_LAUNCH = booleanPreferencesKey("gps_enabled_on_launch")
        internal val ACCURACY_MODE = stringPreferencesKey("location_accuracy_mode")
        internal val LOCATION_INTERVAL = intPreferencesKey("location_update_interval_seconds")
        internal val MINIMUM_ACCURACY = floatPreferencesKey("minimum_accuracy_meters")
        internal val ACCURACY_CIRCLE = booleanPreferencesKey("show_accuracy_circle")
        internal val MANUAL_FALLBACK = booleanPreferencesKey("switch_to_manual_on_poor_gps")
        internal val SAVE_HISTORY = booleanPreferencesKey("save_route_history")
        internal val HISTORY_LIMIT = intPreferencesKey("maximum_history_records")
        internal val DEVELOPER_UNLOCKED = booleanPreferencesKey("developer_options_unlocked")
        internal val TAP_COORDINATES = booleanPreferencesKey("show_tap_coordinates")
        internal val SHOW_NODES = booleanPreferencesKey("show_navigation_nodes")
        internal val SHOW_EDGES = booleanPreferencesKey("show_navigation_edges")
        internal val SHOW_DOORS = booleanPreferencesKey("show_door_nodes")
        internal val USE_ELEVATOR = booleanPreferencesKey("use_elevator")
        internal val SPEECH_LANGUAGE = stringPreferencesKey("speech_language")
        internal val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        internal val FILE_LOGGING = booleanPreferencesKey("file_logging_enabled")
    }
}

private fun Preferences.toAppSettings() = AppSettings(
    themeMode = enumValue(this[SettingsDataStore.THEME], ThemeMode.SYSTEM),
    accentColorArgb = this[SettingsDataStore.ACCENT]?.takeUnless { it in AppSettings.LEGACY_BRAND_BLUES } ?: AppSettings.BRAND_BLUE,
    fontScale = this[SettingsDataStore.FONT_SCALE] ?: 1f,
    animationsEnabled = this[SettingsDataStore.ANIMATIONS] ?: true,
    appLanguage = enumValue(this[SettingsDataStore.APP_LANGUAGE], AppLanguage.SYSTEM),
    mapRenderQuality = enumValue(this[SettingsDataStore.MAP_RENDER_QUALITY], MapRenderQuality.HIGH),
    initialFloor = this[SettingsDataStore.INITIAL_FLOOR] ?: "last",
    lastOpenedFloor = this[SettingsDataStore.LAST_OPENED_FLOOR] ?: 1,
    maximumZoom = this[SettingsDataStore.MAX_ZOOM] ?: 6f,
    minimumZoom = this[SettingsDataStore.MIN_ZOOM] ?: 1f,
    showCoordinateGrid = this[SettingsDataStore.GRID] ?: false,
    compassRotationEnabled = this[SettingsDataStore.COMPASS] ?: false,
    routeColorArgb = this[SettingsDataStore.ROUTE_COLOR]?.takeUnless { it in AppSettings.LEGACY_BRAND_BLUES } ?: AppSettings.BRAND_BLUE,
    routeLineWidthDp = this[SettingsDataStore.ROUTE_WIDTH] ?: 5f,
    routeLineStyle = enumValue(this[SettingsDataStore.ROUTE_STYLE], RouteLineStyle.SOLID),
    startupLocationMode = enumValue(this[SettingsDataStore.STARTUP_LOCATION], StartupLocationMode.GPS),
    lastLocationMode = enumValue(this[SettingsDataStore.LAST_LOCATION_MODE], LocationMode.GPS),
    automaticRouteOnSelection = this[SettingsDataStore.AUTO_ROUTE] ?: true,
    arrivalVibrationEnabled = this[SettingsDataStore.VIBRATION] ?: true,
    voiceGuidanceEnabled = this[SettingsDataStore.VOICE] ?: false,
    distanceUnit = enumValue(this[SettingsDataStore.DISTANCE_UNIT], DistanceUnit.METERS),
    walkingSpeedKmh = this[SettingsDataStore.WALKING_SPEED] ?: 4f,
    routePreference = enumValue(this[SettingsDataStore.ROUTE_PREFERENCE], RoutePreference.SHORTEST),
    gpsEnabledOnLaunch = this[SettingsDataStore.GPS_ON_LAUNCH] ?: true,
    locationAccuracyMode = enumValue(this[SettingsDataStore.ACCURACY_MODE], LocationAccuracyMode.HIGH),
    locationUpdateIntervalSeconds = this[SettingsDataStore.LOCATION_INTERVAL] ?: 3,
    minimumAccuracyMeters = this[SettingsDataStore.MINIMUM_ACCURACY] ?: 20f,
    showAccuracyCircle = this[SettingsDataStore.ACCURACY_CIRCLE] ?: true,
    switchToManualOnPoorGps = this[SettingsDataStore.MANUAL_FALLBACK] ?: false,
    saveRouteHistory = this[SettingsDataStore.SAVE_HISTORY] ?: true,
    maximumHistoryRecords = this[SettingsDataStore.HISTORY_LIMIT] ?: 25,
    developerOptionsUnlocked = this[SettingsDataStore.DEVELOPER_UNLOCKED] ?: false,
    showTapCoordinates = this[SettingsDataStore.TAP_COORDINATES] ?: false,
    showNavigationNodes = this[SettingsDataStore.SHOW_NODES] ?: false,
    showNavigationEdges = this[SettingsDataStore.SHOW_EDGES] ?: false,
    showDoorNodes = this[SettingsDataStore.SHOW_DOORS] ?: false,
    useElevator = this[SettingsDataStore.USE_ELEVATOR] ?: false,
    speechLanguage = enumValue(this[SettingsDataStore.SPEECH_LANGUAGE], SpeechLanguage.SYSTEM),
    onboardingCompleted = this[SettingsDataStore.ONBOARDING_COMPLETED] ?: false,
    fileLoggingEnabled = this[SettingsDataStore.FILE_LOGGING] ?: false,
)

private fun AppSettings.writeTo(preferences: androidx.datastore.preferences.core.MutablePreferences) {
    preferences[SettingsDataStore.THEME] = themeMode.name
    preferences[SettingsDataStore.ACCENT] = accentColorArgb
    preferences[SettingsDataStore.FONT_SCALE] = fontScale
    preferences[SettingsDataStore.ANIMATIONS] = animationsEnabled
    preferences[SettingsDataStore.APP_LANGUAGE] = appLanguage.name
    preferences[SettingsDataStore.MAP_RENDER_QUALITY] = mapRenderQuality.name
    preferences[SettingsDataStore.INITIAL_FLOOR] = initialFloor
    preferences[SettingsDataStore.LAST_OPENED_FLOOR] = lastOpenedFloor
    preferences[SettingsDataStore.MAX_ZOOM] = maximumZoom
    preferences[SettingsDataStore.MIN_ZOOM] = minimumZoom
    preferences[SettingsDataStore.GRID] = showCoordinateGrid
    preferences[SettingsDataStore.COMPASS] = compassRotationEnabled
    preferences[SettingsDataStore.ROUTE_COLOR] = routeColorArgb
    preferences[SettingsDataStore.ROUTE_WIDTH] = routeLineWidthDp
    preferences[SettingsDataStore.ROUTE_STYLE] = routeLineStyle.name
    preferences[SettingsDataStore.STARTUP_LOCATION] = startupLocationMode.name
    preferences[SettingsDataStore.LAST_LOCATION_MODE] = lastLocationMode.name
    preferences[SettingsDataStore.AUTO_ROUTE] = automaticRouteOnSelection
    preferences[SettingsDataStore.VIBRATION] = arrivalVibrationEnabled
    preferences[SettingsDataStore.VOICE] = voiceGuidanceEnabled
    preferences[SettingsDataStore.DISTANCE_UNIT] = distanceUnit.name
    preferences[SettingsDataStore.WALKING_SPEED] = walkingSpeedKmh
    preferences[SettingsDataStore.ROUTE_PREFERENCE] = routePreference.name
    preferences[SettingsDataStore.GPS_ON_LAUNCH] = gpsEnabledOnLaunch
    preferences[SettingsDataStore.ACCURACY_MODE] = locationAccuracyMode.name
    preferences[SettingsDataStore.LOCATION_INTERVAL] = locationUpdateIntervalSeconds
    preferences[SettingsDataStore.MINIMUM_ACCURACY] = minimumAccuracyMeters
    preferences[SettingsDataStore.ACCURACY_CIRCLE] = showAccuracyCircle
    preferences[SettingsDataStore.MANUAL_FALLBACK] = switchToManualOnPoorGps
    preferences[SettingsDataStore.SAVE_HISTORY] = saveRouteHistory
    preferences[SettingsDataStore.HISTORY_LIMIT] = maximumHistoryRecords
    preferences[SettingsDataStore.DEVELOPER_UNLOCKED] = developerOptionsUnlocked
    preferences[SettingsDataStore.TAP_COORDINATES] = showTapCoordinates
    preferences[SettingsDataStore.SHOW_NODES] = showNavigationNodes
    preferences[SettingsDataStore.SHOW_EDGES] = showNavigationEdges
    preferences[SettingsDataStore.SHOW_DOORS] = showDoorNodes
    preferences[SettingsDataStore.USE_ELEVATOR] = useElevator
    preferences[SettingsDataStore.SPEECH_LANGUAGE] = speechLanguage.name
    preferences[SettingsDataStore.ONBOARDING_COMPLETED] = onboardingCompleted
    preferences[SettingsDataStore.FILE_LOGGING] = fileLoggingEnabled
}

private inline fun <reified T : Enum<T>> enumValue(value: String?, default: T): T =
    value?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default
