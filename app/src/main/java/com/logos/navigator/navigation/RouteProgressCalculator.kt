package com.logos.navigator.navigation

import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.MapPoint
import com.logos.navigator.data.model.NavigationData
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.Room
import kotlin.math.hypot

private const val DISTANCE_EPSILON = 1e-9

/** Измерения связующих отрезков и графового пути в метрах. */
data class RouteDistanceBreakdown(
    val startConnectorMeters: Double,
    val networkMeters: Double,
    val destinationConnectorMeters: Double,
) {
    val totalMeters: Double
        get() = startConnectorMeters + networkMeters + destinationConnectorMeters
}

/** Ближайшая проекция позиции на фактически нарисованную линию маршрута. */
data class RouteProjection(
    val deviationMeters: Double,
    val traveledMeters: Double,
    val totalMeters: Double,
) {
    val progress: Float
        get() = if (!totalMeters.isFinite() || totalMeters <= DISTANCE_EPSILON) {
            1f
        } else {
            (traveledMeters / totalMeters).toFloat().coerceIn(0f, 1f)
        }
}

/** Считает расстояние и прогресс по переданной маршрутной ломаной и её конечным коннекторам. */
object RouteProgressCalculator {
    fun distances(
        graph: NavigationData,
        route: List<NavNode>,
        origin: MapPoint?,
        destination: Room?,
    ): RouteDistanceBreakdown {
        if (route.isEmpty()) return RouteDistanceBreakdown(0.0, 0.0, 0.0)
        val floors = graph.floors.associateBy(FloorPlan::number)
        val first = route.first()
        val last = route.last()
        val startConnector = origin
            ?.takeIf { it.floor == first.floor && it.x.isFinite() && it.y.isFinite() }
            ?.let { point -> mapDistance(point.x.toDouble(), point.y.toDouble(), first.x, first.y, first.floor, floors) }
            ?: 0.0
        val network = route.zipWithNext().sumOf { (from, to) -> routeEdgeMeters(graph, from, to, floors) }
        val destinationConnector = destination
            ?.takeIf { it.floor == last.floor }
            ?.let { room ->
                val x = room.x
                val y = room.y
                if (x != null && y != null && x.isFinite() && y.isFinite()) {
                    mapDistance(last.x, last.y, x, y, last.floor, floors)
                } else {
                    0.0
                }
            }
            ?: 0.0
        return RouteDistanceBreakdown(startConnector, network, destinationConnector)
    }

    fun project(
        graph: NavigationData,
        route: List<NavNode>,
        origin: MapPoint?,
        destination: Room?,
        point: MapPoint,
    ): RouteProjection? {
        if (route.isEmpty() || !point.x.isFinite() || !point.y.isFinite()) return null
        val floors = graph.floors.associateBy(FloorPlan::number)
        val distances = distances(graph, route, origin, destination)
        val metersAtNode = DoubleArray(route.size)
        metersAtNode[0] = distances.startConnectorMeters
        for (index in 0 until route.lastIndex) {
            metersAtNode[index + 1] = metersAtNode[index] + routeEdgeMeters(graph, route[index], route[index + 1], floors)
        }

        var nearestDeviationMeters = Double.POSITIVE_INFINITY
        var traveledMeters = 0.0

        fun considerSegment(
            floor: Int,
            startX: Double,
            startY: Double,
            endX: Double,
            endY: Double,
            distanceBeforeSegment: Double,
            segmentDistanceMeters: Double,
        ) {
            if (floor != point.floor || segmentDistanceMeters <= DISTANCE_EPSILON) return
            val deltaX = endX - startX
            val deltaY = endY - startY
            val lengthSquared = deltaX * deltaX + deltaY * deltaY
            if (!lengthSquared.isFinite() || lengthSquared <= COORDINATE_EPSILON) return
            val ratio = (((point.x.toDouble() - startX) * deltaX + (point.y.toDouble() - startY) * deltaY) / lengthSquared)
                .coerceIn(0.0, 1.0)
            val projectedX = startX + ratio * deltaX
            val projectedY = startY + ratio * deltaY
            val deviation = mapDistance(point.x.toDouble(), point.y.toDouble(), projectedX, projectedY, floor, floors)
            if (deviation.isFinite() && deviation < nearestDeviationMeters) {
                nearestDeviationMeters = deviation
                traveledMeters = distanceBeforeSegment + segmentDistanceMeters * ratio
            }
        }

        val first = route.first()
        origin?.takeIf { it.floor == first.floor }?.let { start ->
            considerSegment(
                floor = first.floor,
                startX = start.x.toDouble(),
                startY = start.y.toDouble(),
                endX = first.x,
                endY = first.y,
                distanceBeforeSegment = 0.0,
                segmentDistanceMeters = distances.startConnectorMeters,
            )
        }

        for (index in 0 until route.lastIndex) {
            val from = route[index]
            val to = route[index + 1]
            val edgeMeters = routeEdgeMeters(graph, from, to, floors)
            if (from.floor == to.floor) {
                considerSegment(
                    floor = from.floor,
                    startX = from.x,
                    startY = from.y,
                    endX = to.x,
                    endY = to.y,
                    distanceBeforeSegment = metersAtNode[index],
                    segmentDistanceMeters = edgeMeters,
                )
            }
        }

        val last = route.last()
        destination?.takeIf { it.floor == last.floor }?.let { room ->
            val destinationX = room.x
            val destinationY = room.y
            if (destinationX != null && destinationY != null) {
                considerSegment(
                    floor = last.floor,
                    startX = last.x,
                    startY = last.y,
                    endX = destinationX,
                    endY = destinationY,
                    distanceBeforeSegment = metersAtNode.last(),
                    segmentDistanceMeters = distances.destinationConnectorMeters,
                )
            }
        }

        if (!nearestDeviationMeters.isFinite()) {
            val nearest = route.withIndex()
                .filter { it.value.floor == point.floor && it.value.x.isFinite() && it.value.y.isFinite() }
                .minByOrNull { (_, node) -> hypot(point.x.toDouble() - node.x, point.y.toDouble() - node.y) }
                ?: return null
            nearestDeviationMeters = mapDistance(
                point.x.toDouble(),
                point.y.toDouble(),
                nearest.value.x,
                nearest.value.y,
                point.floor,
                floors,
            )
            traveledMeters = metersAtNode[nearest.index]
        }

        return RouteProjection(
            deviationMeters = nearestDeviationMeters,
            traveledMeters = traveledMeters.coerceIn(0.0, distances.totalMeters.coerceAtLeast(0.0)),
            totalMeters = distances.totalMeters,
        )
    }

    private fun routeEdgeMeters(
        graph: NavigationData,
        from: NavNode,
        to: NavNode,
        floors: Map<Int, FloorPlan>,
    ): Double {
        val edgeMeters = graph.edges.asSequence()
            .filter {
                (it.from == from.id && it.to == to.id) ||
                    (it.bidirectional != false && it.from == to.id && it.to == from.id)
            }
            .map { it.weight }
            .filter { it.isFinite() && it > 0.0 }
            .minOrNull()
        return edgeMeters ?: if (from.floor == to.floor) {
            mapDistance(from.x, from.y, to.x, to.y, from.floor, floors)
        } else {
            DEFAULT_FLOOR_TRANSITION_METERS
        }
    }

    private fun mapDistance(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        floor: Int,
        floors: Map<Int, FloorPlan>,
    ): Double = hypot(endX - startX, endY - startY) *
        (floors[floor]?.metersPerUnit?.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_METERS_PER_UNIT)

    private const val COORDINATE_EPSILON = 1e-12
    private const val DEFAULT_METERS_PER_UNIT = 0.083333
    private const val DEFAULT_FLOOR_TRANSITION_METERS = 4.0
}
