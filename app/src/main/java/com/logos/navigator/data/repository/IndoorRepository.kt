package com.logos.navigator.data.repository

import android.content.Context
import com.logos.navigator.R
import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.LoadedIndoorData
import com.logos.navigator.data.model.NavigationData
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.RoomCatalog
import com.logos.navigator.data.model.RestrictedArea
import com.logos.navigator.data.model.SvgPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** Загружает навигационные JSON; планы отрисовываются из сгенерированных Kotlin Canvas/Path-команд. */
class IndoorRepository(context: Context) {
    private val appContext = context.applicationContext
    private val gson = com.google.gson.Gson()

    /** Данные из assets неизменны: оба ViewModel получают один разобранный и проверенный снимок. */
    suspend fun loadIndoorData(): LoadedIndoorData = cached ?: loadMutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val navigation = readJson("navigation_graph.json", NavigationData::class.java)
            val catalog = readJson("rooms.json", RoomCatalog::class.java)
            validate(navigation, catalog)
            LoadedIndoorData(navigation, catalog.rooms)
        }.also { cached = it }
    }

    private fun <T> readJson(assetName: String, clazz: Class<T>): T = try {
        appContext.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, clazz)
                ?: throw IOException(appContext.getString(R.string.data_empty_asset, assetName))
        }
    } catch (exception: IOException) {
        throw IOException(
            appContext.getString(R.string.data_open_asset_error, assetName, appContext.getString(R.string.unknown_error)),
            exception,
        )
    } catch (exception: RuntimeException) {
        throw IOException(
            appContext.getString(R.string.data_parse_asset_error, assetName, appContext.getString(R.string.unknown_error)),
            exception,
        )
    }

    private fun validate(navigation: NavigationData, catalog: RoomCatalog) {
        require(navigation.floors.isNotEmpty()) { appContext.getString(R.string.data_validation_floors_empty) }
        require(navigation.nodes.isNotEmpty()) { appContext.getString(R.string.data_validation_nodes_empty) }
        require(catalog.rooms.isNotEmpty()) { appContext.getString(R.string.data_validation_rooms_empty) }

        val floorNumbers = navigation.floors.map { it.number }.toSet()
        require(floorNumbers.size == navigation.floors.size) { appContext.getString(R.string.data_validation_floor_ids) }
        require(navigation.floors.all { floor ->
            !floor.title.isNullOrBlank() && !floor.mapAsset.isNullOrBlank() && floor.mapAsset.endsWith(".svg", true) &&
                floor.width > 0 && floor.height > 0 && floor.metersPerUnit.isFinite() && floor.metersPerUnit > 0.0 &&
                floor.viewBoxX.isFinite() && floor.viewBoxY.isFinite()
        }) {
            appContext.getString(R.string.data_validation_floor_fields)
        }
        val floorsByNumber = navigation.floors.associateBy(FloorPlan::number)

        val nodeIds = navigation.nodes.map { it.id }.toSet()
        require(nodeIds.size == navigation.nodes.size) { appContext.getString(R.string.data_validation_node_ids) }
        require(navigation.nodes.all { node ->
            val floor = floorsByNumber[node.floor]
            !node.id.isNullOrBlank() && !node.name.isNullOrBlank() && !node.type.isNullOrBlank() && floor != null &&
                node.x.isFinite() && node.y.isFinite() &&
                node.x in floor.viewBoxX..(floor.viewBoxX + floor.width) &&
                node.y in floor.viewBoxY..(floor.viewBoxY + floor.height)
        }) {
            appContext.getString(R.string.data_validation_node_fields)
        }

        val anchorIds = navigation.gpsAnchors.map { "${it.floor}:${it.id}" }.toSet()
        require(anchorIds.size == navigation.gpsAnchors.size) { appContext.getString(R.string.data_validation_anchor_ids) }
        require(navigation.gpsAnchors.all { anchor ->
            val floor = floorsByNumber[anchor.floor]
            !anchor.id.isNullOrBlank() && floor != null &&
                anchor.latitude.isFinite() && anchor.longitude.isFinite() &&
                anchor.latitude in -90.0..90.0 && anchor.longitude in -180.0..180.0 &&
                anchor.x.isFinite() && anchor.y.isFinite() &&
                anchor.x in floor.viewBoxX..(floor.viewBoxX + floor.width) &&
                anchor.y in floor.viewBoxY..(floor.viewBoxY + floor.height)
        }) {
            appContext.getString(R.string.data_validation_anchor_fields)
        }
        require(navigation.edges.all { edge ->
            !edge.from.isNullOrBlank() && !edge.to.isNullOrBlank() && !edge.type.isNullOrBlank() &&
                edge.from != edge.to && edge.from in nodeIds && edge.to in nodeIds &&
                edge.weight.isFinite() && edge.weight > 0.0
        }) { appContext.getString(R.string.data_validation_edge) }

        require(navigation.restrictedAreas.all { area ->
            val floor = floorsByNumber[area.floor]
            !area.id.isNullOrBlank() && floor != null && area.polygons.isNotEmpty() &&
                area.polygons.all { polygon -> polygon.size >= 3 && polygon.all { point ->
                    point.x.isFinite() && point.y.isFinite() &&
                        point.x in floor.viewBoxX..(floor.viewBoxX + floor.width) &&
                        point.y in floor.viewBoxY..(floor.viewBoxY + floor.height)
                } }
        }) { appContext.getString(R.string.data_validation_restricted_area) }

        val nodesById = navigation.nodes.associateBy(NavNode::id)
        require(navigation.nodes.none { node ->
            navigation.restrictedAreas.any { area ->
                area.floor == node.floor && area.polygons.any { polygon -> contains(SvgPoint(node.x, node.y), polygon) }
            }
        }) { appContext.getString(R.string.data_validation_node_in_restricted_area) }

        val roomIds = catalog.rooms.map { it.id }.toSet()
        require(roomIds.size == catalog.rooms.size) { appContext.getString(R.string.data_validation_room_ids) }
        val allowedCategories = setOf("classroom", "laboratory", "administrative", "medical", "common", "entrance")
        require(catalog.rooms.all { room ->
            val floor = floorsByNumber[room.floor]
            val polygons = room.polygons + listOfNotNull(room.polygon.takeIf { it.isNotEmpty() })
            val geometryIsValid = polygons.all { polygon -> polygon.size >= 3 && polygon.all { point ->
                point.x.isFinite() && point.y.isFinite() && floor != null &&
                    point.x in floor.viewBoxX..(floor.viewBoxX + floor.width) &&
                    point.y in floor.viewBoxY..(floor.viewBoxY + floor.height)
            } }
            val mappedNodeIsValid = if (room.routeAvailable) {
                !room.nodeId.isNullOrBlank() && room.nodeId in nodeIds && nodesById[room.nodeId]?.floor == room.floor
            } else {
                room.nodeId == null || (room.nodeId in nodeIds && nodesById[room.nodeId]?.floor == room.floor)
            }
            val pointIsValid = when {
                room.x == null && room.y == null -> !room.routeAvailable
                room.x == null || room.y == null -> false
                else -> room.x.isFinite() && room.y.isFinite() && floor != null &&
                    room.x in floor.viewBoxX..(floor.viewBoxX + floor.width) &&
                    room.y in floor.viewBoxY..(floor.viewBoxY + floor.height)
            }
            !room.id.isNullOrBlank() && !room.name.isNullOrBlank() && !room.category.isNullOrBlank() &&
                room.category.lowercase() in allowedCategories && floor != null && mappedNodeIsValid && geometryIsValid && pointIsValid
        }) { appContext.getString(R.string.data_validation_room_fields) }
    }

    /** Проверка точки по polygon сохраняет серые и выделенные зоны вне навигационного графа. */
    private fun contains(point: SvgPoint, polygon: List<SvgPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            val crosses = (current.y > point.y) != (previous.y > point.y)
            if (crosses) {
                val crossingX = (previous.x - current.x) * (point.y - current.y) /
                    (previous.y - current.y) + current.x
                if (point.x < crossingX) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private companion object {
        @Volatile
        private var cached: LoadedIndoorData? = null
        private val loadMutex = Mutex()
    }
}
