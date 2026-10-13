package com.logos.navigator.data.model

import com.caverock.androidsvg.SVG
import com.google.gson.annotations.SerializedName

/** Описание SVG-плана: x/y/width/height соответствуют viewBox документа. */
data class FloorPlan(
    val number: Int,
    val title: String,
    val mapAsset: String,
    val width: Int,
    val height: Int,
    val metersPerUnit: Double = 0.083333,
    val viewBoxX: Double = 0.0,
    val viewBoxY: Double = 0.0,
)

/** Узел графа располагается в координатах viewBox карты своего этажа. */
data class NavNode(
    val id: String,
    val floor: Int,
    val x: Double,
    val y: Double,
    val name: String = "",
    val type: String = "hallway",
    val nameEn: String? = null,
    val connectsRoom: String? = null,
    val roomId: String? = null,
    val stairsGroup: String? = null,
    val elevatorGroup: String? = null,
    val requiresAccessibility: Boolean? = false,
)

/** Непроходимая область плана; внутри неё нельзя ставить маршрутные узлы и начинать маршрут. */
data class RestrictedArea(
    val id: String,
    val floor: Int,
    val reason: String = "",
    val reasonEn: String? = null,
    val polygons: List<List<SvgPoint>> = emptyList(),
)

/** Ребро — проходимый участок коридора или межэтажный переход. Вес задаётся в метрах. */
data class NavEdge(
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String,
    val weight: Double,
    val type: String = "corridor",
    // При пропущенном поле Gson оставляет null; null трактуется как двустороннее ребро.
    val bidirectional: Boolean? = true,
    val requiresAccessibility: Boolean? = false,
)

/** GPS-опорная точка, сопоставленная точке в системе координат SVG конкретного этажа. */
data class GeoAnchor(
    val floor: Int,
    val id: String,
    @SerializedName("lat") val latitude: Double,
    @SerializedName("lon") val longitude: Double,
    val x: Double,
    val y: Double,
)

data class NavigationData(
    val floors: List<FloorPlan> = emptyList(),
    val gpsAnchors: List<GeoAnchor> = emptyList(),
    val nodes: List<NavNode> = emptyList(),
    val edges: List<NavEdge> = emptyList(),
    val restrictedAreas: List<RestrictedArea> = emptyList(),
)

data class SvgPoint(val x: Double, val y: Double)

data class Room(
    val id: String,
    @SerializedName("name_ru") val name: String,
    @SerializedName("name_en") val nameEn: String? = null,
    val number: String? = null,
    val floor: Int,
    val category: String,
    @SerializedName("description_ru") val description: String? = null,
    @SerializedName("description_en") val descriptionEn: String? = null,
    val nodeId: String? = null,
    /** null означает, что положение помещения нельзя надёжно определить по текущему плану. */
    val x: Double? = null,
    val y: Double? = null,
    /** Необязательный SVG ID; для сложных форм точный полигон хранится в JSON. */
    val svgElementId: String? = null,
    val svgElementIds: List<String> = emptyList(),
    val doorNodeId: String? = null,
    val doorNodeIds: List<String> = emptyList(),
    val centerX: Double? = null,
    val centerY: Double? = null,
    /** Старый однополигональный формат оставлен для совместимости с сохранёнными наборами данных. */
    val polygon: List<SvgPoint> = emptyList(),
    /** Несколько несмежных контуров одного физического помещения, например кабинета 230. */
    val polygons: List<List<SvgPoint>> = emptyList(),
    @SerializedName("aliases_ru") val aliasesRu: List<String> = emptyList(),
    @SerializedName("aliases_en") val aliasesEn: List<String> = emptyList(),
    val role: String? = null,
    @SerializedName("route_available") val routeAvailable: Boolean = true,
    @SerializedName("geometry_status") val geometryStatus: String = "mapped",
    @SerializedName("expected_geometry_parts") val expectedGeometryParts: Int? = null,
    val requiresAccessibility: Boolean = false,
)

data class RoomCatalog(val rooms: List<Room> = emptyList())
data class LoadedIndoorData(val navigation: NavigationData, val rooms: List<Room>)

data class MapPoint(val floor: Int, val x: Float, val y: Float)

enum class RouteAction {
    START, STRAIGHT, TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, U_TURN,
    DOOR, STAIRS_UP, STAIRS_DOWN, ELEVATOR, ARRIVAL,
}

data class RouteInstruction(
    val action: RouteAction,
    val distanceMeters: Double = 0.0,
    val targetFloor: Int? = null,
    val targetNodeId: String? = null,
    val destinationName: String? = null,
    val progress: Float = 0f,
)

enum class LocationMode { GPS, MANUAL }

/** Единый источник состояния экранов Compose. */
data class IndoorUiState(
    val isLoading: Boolean = true,
    val isMapLoading: Boolean = false,
    val loadError: String? = null,
    val mapError: String? = null,
    val navigation: NavigationData? = null,
    val rooms: List<Room> = emptyList(),
    val selectedFloor: Int = 1,
    val floorSvg: SVG? = null,
    val locationMode: LocationMode = LocationMode.MANUAL,
    val userPosition: MapPoint? = null,
    val isManualPositionConfirmed: Boolean = false,
    val gpsAccuracyMeters: Float? = null,
    val gpsWarning: String? = null,
    val isGpsActive: Boolean = false,
    val compassHeadingDegrees: Float = 0f,
    val selectedRoom: Room? = null,
    val destination: Room? = null,
    val route: List<NavNode>? = null,
    val routeSteps: List<RouteInstruction> = emptyList(),
    val currentRouteStepIndex: Int = 0,
    val routeProgress: Float = 0f,
    val routeDistanceMeters: Double? = null,
    val routeMinutes: Int? = null,
    val message: String? = null,
    val settings: AppSettings = AppSettings(),
)
