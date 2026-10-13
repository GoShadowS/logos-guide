package com.logos.navigator.svg

import android.graphics.Matrix
import android.graphics.PointF

/** Преобразует экранные точки и user coordinates SVG с учётом матрицы камеры и поворота. */
class SvgCoordinateConverter(private val svgToViewMatrix: Matrix) {
    fun svgToView(x: Float, y: Float): PointF {
        val points = floatArrayOf(x, y)
        svgToViewMatrix.mapPoints(points)
        return PointF(points[0], points[1])
    }

    fun viewToSvg(x: Float, y: Float): PointF? {
        val inverse = Matrix()
        if (!svgToViewMatrix.invert(inverse)) return null
        val points = floatArrayOf(x, y)
        inverse.mapPoints(points)
        return PointF(points[0], points[1])
    }
}
