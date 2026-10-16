package com.logos.navigator.data.model

/** Режим выбора цветовой схемы приложения. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MapRenderQuality { LOW, MEDIUM, HIGH }
enum class RouteLineStyle { SOLID, DASHED, ANIMATED }
enum class StartupLocationMode { GPS, MANUAL, LAST }
enum class LocationAccuracyMode { HIGH, BALANCED, BATTERY }
enum class DistanceUnit { METERS, STEPS }
enum class RoutePreference { SHORTEST, AVOID_STAIRS, AVOID_ELEVATOR }
enum class AppLanguage { SYSTEM, RUSSIAN, ENGLISH }
enum class SpeechLanguage { SYSTEM, RUSSIAN, ENGLISH }

/** Все пользовательские предпочтения. Значения по умолчанию безопасны для офлайн-режима. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColorArgb: Int = BRAND_BLUE,
    val fontScale: Float = 1f,
    val animationsEnabled: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val mapRenderQuality: MapRenderQuality = MapRenderQuality.HIGH,
    /** Число этажа в виде строки или специальное значение "last". */
    val initialFloor: String = "last",
    val lastOpenedFloor: Int = 1,
    val maximumZoom: Float = 6f,
    val minimumZoom: Float = 1f,
    val showCoordinateGrid: Boolean = false,
    val showNavigationNodes: Boolean = false,
    val showNavigationEdges: Boolean = false,
    val showDoorNodes: Boolean = false,
    val compassRotationEnabled: Boolean = false,
    val routeColorArgb: Int = BRAND_BLUE,
    val routeLineWidthDp: Float = 5f,
    val routeLineStyle: RouteLineStyle = RouteLineStyle.SOLID,
    val startupLocationMode: StartupLocationMode = StartupLocationMode.GPS,
    val lastLocationMode: LocationMode = LocationMode.GPS,
    val automaticRouteOnSelection: Boolean = true,
    val useElevator: Boolean = false,
    val arrivalVibrationEnabled: Boolean = true,
    val voiceGuidanceEnabled: Boolean = false,
    val speechLanguage: SpeechLanguage = SpeechLanguage.SYSTEM,
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,
    val walkingSpeedKmh: Float = 4f,
    val routePreference: RoutePreference = RoutePreference.SHORTEST,
    val gpsEnabledOnLaunch: Boolean = true,
    val locationAccuracyMode: LocationAccuracyMode = LocationAccuracyMode.HIGH,
    val locationUpdateIntervalSeconds: Int = 3,
    val minimumAccuracyMeters: Float = 20f,
    val showAccuracyCircle: Boolean = true,
    val switchToManualOnPoorGps: Boolean = false,
    val saveRouteHistory: Boolean = true,
    val maximumHistoryRecords: Int = 25,
    val onboardingCompleted: Boolean = false,
    val developerOptionsUnlocked: Boolean = false,
    val showTapCoordinates: Boolean = false,
    val fileLoggingEnabled: Boolean = false,
) {
    companion object {
        const val BRAND_BLUE: Int = 0xFF245CE5.toInt()
        const val PREVIOUS_BRAND_BLUE: Int = 0xFF2538F5.toInt()
    }
}
