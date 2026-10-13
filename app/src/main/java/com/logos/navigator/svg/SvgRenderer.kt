package com.logos.navigator.svg

import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.logos.navigator.data.model.FloorPlan
import kotlin.math.ceil

/** Рендерит исходный SVG; растровый результат используется только как временный кэш кадра. */
object SvgRenderer {
    /** Растеризует SVG для текущего масштаба в памяти, не создавая PNG-файл и не меняя источник координат. */
    fun renderToBitmap(svg: SVG, floor: FloorPlan, pixelsPerSvgUnit: Float): Bitmap {
        val width = ceil(floor.width * pixelsPerSvgUnit).toInt().coerceAtLeast(1)
        val height = ceil(floor.height * pixelsPerSvgUnit).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            svg.renderToCanvas(
                canvas,
                renderOptions(floor, 0f, 0f, width.toFloat(), height.toFloat()),
            )
            bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun renderOptions(floor: FloorPlan, viewportX: Float, viewportY: Float, viewportWidth: Float, viewportHeight: Float) =
        RenderOptions.create()
            .viewBox(floor.viewBoxX.toFloat(), floor.viewBoxY.toFloat(), floor.width.toFloat(), floor.height.toFloat())
            .viewPort(viewportX, viewportY, viewportWidth, viewportHeight)
}
