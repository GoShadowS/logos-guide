package com.logos.navigator.svg

import android.graphics.Matrix
import android.graphics.PointF

/** Преобразует экранные точки и координаты SVG; обратная матрица обновляется только вместе с камерой. */
class SvgCoordinateConverter(private val svgToViewMatrix: Matrix) {
    private val viewToSvgMatrix = Matrix()
    private val points = FloatArray(2)
    private var inverseValid = false
    private var inverseDirty = true

    /** Помечает обратное преобразование устаревшим; вычисление откладывается до следующего тапа. */
    fun refreshInverse() {
        inverseDirty = true
    }

    fun svgToView(x: Float, y: Float): PointF {
        points[0] = x
        points[1] = y
        svgToViewMatrix.mapPoints(points)
        return PointF(points[0], points[1])
    }

    fun viewToSvg(x: Float, y: Float): PointF? {
        if (inverseDirty) {
            inverseValid = svgToViewMatrix.invert(viewToSvgMatrix)
            inverseDirty = false
        }
        if (!inverseValid) return null
        points[0] = x
        points[1] = y
        viewToSvgMatrix.mapPoints(points)
        return PointF(points[0], points[1])
    }
}
