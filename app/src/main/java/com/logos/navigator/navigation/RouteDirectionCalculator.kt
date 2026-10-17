package com.logos.navigator.navigation

import com.logos.navigator.data.model.MapPoint
import com.logos.navigator.data.model.NavNode

/** Находит ближайшую следующую точку пути для ориентации маркера пользователя. */
object RouteDirectionCalculator {
    fun nextTarget(route: List<NavNode>, origin: MapPoint?, position: MapPoint): MapPoint? {
        if (route.isEmpty()) return null

        val firstRoutePoint = route.firstOrNull { it.floor == position.floor }
        val routeOrigin = origin?.takeIf { it.floor == position.floor }
        if (routeOrigin != null && firstRoutePoint != null) {
            val originX = routeOrigin.x
            val originY = routeOrigin.y
            val connectorX = firstRoutePoint.x.toFloat() - originX
            val connectorY = firstRoutePoint.y.toFloat() - originY
            val connectorLengthSquared = connectorX * connectorX + connectorY * connectorY
            if (connectorLengthSquared > DIRECTION_EPSILON_SQUARED) {
                val connectorProjection =
                    (position.x - originX) * connectorX + (position.y - originY) * connectorY
                val connectorProgress = connectorProjection / connectorLengthSquared
                if (connectorProgress < SEGMENT_END_TOLERANCE) {
                    return MapPoint(position.floor, firstRoutePoint.x.toFloat(), firstRoutePoint.y.toFloat())
                }
            }
        }

        var closestSegmentIndex = -1
        var closestSegmentProgress = 0f
        var closestDistanceSquared = Float.POSITIVE_INFINITY
        for (index in 0 until route.lastIndex) {
            val from = route[index]
            val to = route[index + 1]
            if (from.floor != position.floor || to.floor != position.floor) continue

            val fromX = from.x.toFloat()
            val fromY = from.y.toFloat()
            val segmentX = to.x.toFloat() - fromX
            val segmentY = to.y.toFloat() - fromY
            val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
            if (segmentLengthSquared <= DIRECTION_EPSILON_SQUARED) continue

            val segmentProjection =
                (position.x - fromX) * segmentX + (position.y - fromY) * segmentY
            val progress = (segmentProjection / segmentLengthSquared).coerceIn(0f, 1f)
            val offsetX = position.x - (fromX + progress * segmentX)
            val offsetY = position.y - (fromY + progress * segmentY)
            val distanceSquared = offsetX * offsetX + offsetY * offsetY
            if (distanceSquared < closestDistanceSquared) {
                closestSegmentIndex = index
                closestSegmentProgress = progress
                closestDistanceSquared = distanceSquared
            }
        }

        if (closestSegmentIndex < 0) {
            return route.firstOrNull { node ->
                if (node.floor != position.floor) return@firstOrNull false
                val dx = node.x.toFloat() - position.x
                val dy = node.y.toFloat() - position.y
                dx * dx + dy * dy > DIRECTION_EPSILON_SQUARED
            }?.toMapPoint()
        }

        val segmentEndIndex = closestSegmentIndex + 1
        val targetIndex = if (closestSegmentProgress < SEGMENT_END_TOLERANCE) {
            segmentEndIndex
        } else {
            (segmentEndIndex + 1 until route.size).firstOrNull { route[it].floor == position.floor } ?: segmentEndIndex
        }
        val target = route.getOrNull(targetIndex)?.takeIf { it.floor == position.floor } ?: return null
        val directionX = target.x.toFloat() - position.x
        val directionY = target.y.toFloat() - position.y
        if (directionX * directionX + directionY * directionY <= DIRECTION_EPSILON_SQUARED) return null
        return target.toMapPoint()
    }

    private fun NavNode.toMapPoint() = MapPoint(floor, x.toFloat(), y.toFloat())

    private const val DIRECTION_EPSILON_SQUARED = 0.0001f
    private const val SEGMENT_END_TOLERANCE = 0.995f
}
