package com.logos.navigator.svg

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Bbox предварительно отсеивает заведомо неподходящие комнаты перед ray casting. */
fun polygonBounds(polygon: List<PointF>): RectF? {
    if (polygon.isEmpty()) return null
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    polygon.forEach { point ->
        left = min(left, point.x)
        top = min(top, point.y)
        right = max(right, point.x)
        bottom = max(bottom, point.y)
    }
    return RectF(left, top, right, bottom)
}

/** Проверяет попадание точки в произвольный полигон методом Ray Casting. */
fun isPointInPolygon(point: PointF, polygon: List<PointF>): Boolean {
    if (polygon.size < 3) return false
    val bounds = polygonBounds(polygon) ?: return false
    if (point.x < bounds.left || point.x > bounds.right || point.y < bounds.top || point.y > bounds.bottom) return false

    var inside = false
    var previous = polygon.last()
    polygon.forEach { current ->
        if (isOnSegment(point, previous, current)) return true
        val straddlesRay = (current.y > point.y) != (previous.y > point.y)
        if (straddlesRay) {
            val intersectionX = (previous.x - current.x) * (point.y - current.y) /
                (previous.y - current.y) + current.x
            if (point.x < intersectionX) inside = !inside
        }
        previous = current
    }
    return inside
}

private fun isOnSegment(point: PointF, start: PointF, end: PointF): Boolean {
    val cross = (point.y - start.y) * (end.x - start.x) - (point.x - start.x) * (end.y - start.y)
    if (abs(cross) > EDGE_TOLERANCE) return false
    val dot = (point.x - start.x) * (end.x - start.x) + (point.y - start.y) * (end.y - start.y)
    if (dot < 0f) return false
    val lengthSquared = (end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)
    return dot <= lengthSquared + EDGE_TOLERANCE
}

private const val EDGE_TOLERANCE = 0.01f
