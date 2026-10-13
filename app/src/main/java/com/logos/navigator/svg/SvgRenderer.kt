package com.logos.navigator.svg

import android.graphics.Canvas
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.logos.navigator.data.model.FloorPlan

/** Тонкий векторный рендерер AndroidSVG; CSS-подсветка включается только при наличии SVG id. */
object SvgRenderer {
    private val safeSvgId = Regex("[A-Za-z_][A-Za-z0-9_.:-]*")

    fun render(canvas: Canvas, svg: SVG, floor: FloorPlan, highlightElementIds: List<String> = emptyList()) {
        val options = RenderOptions.create()
            .viewBox(floor.viewBoxX.toFloat(), floor.viewBoxY.toFloat(), floor.width.toFloat(), floor.height.toFloat())
            .viewPort(floor.viewBoxX.toFloat(), floor.viewBoxY.toFloat(), floor.width.toFloat(), floor.height.toFloat())
        val safeIds = highlightElementIds.distinct().filter(safeSvgId::matches)
        if (safeIds.isNotEmpty()) {
            options.css(
                safeIds.joinToString("\n") { id ->
                    "#$id { fill: #FFE082 !important; stroke: #F57C00 !important; stroke-width: 3 !important; }"
                },
            )
        }
        svg.renderToCanvas(canvas, options)
    }
}
