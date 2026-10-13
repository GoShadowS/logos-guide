package com.logos.navigator.svg

import android.content.res.AssetManager
import android.graphics.PointF
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStreamReader
import kotlin.math.pow

/** Извлекает простую геометрию SVG-элемента с заданным id в координатах viewBox. */
object SvgGeometryExtractor {
    fun polygon(assets: AssetManager, assetPath: String, elementId: String): List<PointF>? =
        assets.open(assetPath).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(InputStreamReader(input, Charsets.UTF_8))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.getAttributeValue(null, "id") == elementId) {
                    return@use when (parser.name.substringAfter(':')) {
                        "rect" -> rectPolygon(parser)
                        "polygon", "polyline" -> parsePoints(parser.getAttributeValue(null, "points"))
                        "path" -> parsePath(parser.getAttributeValue(null, "d"))
                        "circle" -> ellipsePolygon(
                            parser.number("cx"), parser.number("cy"),
                            parser.number("r"), parser.number("r"),
                        )
                        "ellipse" -> ellipsePolygon(
                            parser.number("cx"), parser.number("cy"),
                            parser.number("rx"), parser.number("ry"),
                        )
                        else -> null
                    }?.takeIf { it.size >= 3 }
                }
                event = parser.next()
            }
            null
        }

    private fun rectPolygon(parser: XmlPullParser): List<PointF> {
        val x = parser.number("x")
        val y = parser.number("y")
        val width = parser.number("width")
        val height = parser.number("height")
        if (width <= 0f || height <= 0f) return emptyList()
        return listOf(
            PointF(x, y),
            PointF(x + width, y),
            PointF(x + width, y + height),
            PointF(x, y + height),
        )
    }

    private fun parsePoints(source: String?): List<PointF> {
        val values = NUMBER.findAll(source.orEmpty()).mapNotNull { it.value.toFloatOrNull() }.toList()
        return values.chunked(2).filter { it.size == 2 }.map { (x, y) -> PointF(x, y) }
    }

    /**
     * Преобразует распространённые команды path (M/L/H/V/Z) и сглаживает Q/C-кривые.
     * Для команд SVG с трансформациями рекомендуется заранее записать polygon в rooms.json.
     */
    private fun parsePath(source: String?): List<PointF> {
        val tokens = PATH_TOKEN.findAll(source.orEmpty()).map { it.value }.toList()
        if (tokens.isEmpty()) return emptyList()
        val result = mutableListOf<PointF>()
        var cursorX = 0f
        var cursorY = 0f
        var startX = 0f
        var startY = 0f
        var command = ' '
        var index = 0

        fun number(): Float? = tokens.getOrNull(index++)?.toFloatOrNull()
        fun hasNumber() = tokens.getOrNull(index)?.firstOrNull()?.let { it.isDigit() || it == '-' || it == '+' || it == '.' } == true
        fun point(x: Float, y: Float, relative: Boolean): PointF {
            val px = if (relative) cursorX + x else x
            val py = if (relative) cursorY + y else y
            cursorX = px
            cursorY = py
            return PointF(px, py)
        }

        while (index < tokens.size) {
            val next = tokens[index]
            if (next.length == 1 && next[0].isLetter()) {
                command = next[0]
                index++
            }
            val relative = command.isLowerCase()
            when (command.lowercaseChar()) {
                'm' -> {
                    val x = number() ?: break
                    val y = number() ?: break
                    val first = point(x, y, relative)
                    result += first
                    startX = cursorX
                    startY = cursorY
                    command = if (relative) 'l' else 'L'
                    while (hasNumber()) {
                        val px = number() ?: break
                        val py = number() ?: break
                        result += point(px, py, relative)
                    }
                }
                'l' -> {
                    while (hasNumber()) {
                        val x = number() ?: break
                        val y = number() ?: break
                        result += point(x, y, relative)
                    }
                }
                'h' -> while (hasNumber()) {
                    val value = number() ?: break
                    cursorX = if (relative) cursorX + value else value
                    result += PointF(cursorX, cursorY)
                }
                'v' -> while (hasNumber()) {
                    val value = number() ?: break
                    cursorY = if (relative) cursorY + value else value
                    result += PointF(cursorX, cursorY)
                }
                'q' -> while (hasNumber()) {
                    val x1 = number() ?: break
                    val y1 = number() ?: break
                    val x2 = number() ?: break
                    val y2 = number() ?: break
                    val end = point(x2, y2, relative)
                    val controlX = if (relative) cursorX - x2 + x1 else x1
                    val controlY = if (relative) cursorY - y2 + y1 else y1
                    val origin = result.lastOrNull() ?: PointF(cursorX, cursorY)
                    for (step in 1..8) {
                        val t = step / 8f
                        val inverse = 1f - t
                        result += PointF(
                            inverse.pow(2) * origin.x + 2f * inverse * t * controlX + t.pow(2) * end.x,
                            inverse.pow(2) * origin.y + 2f * inverse * t * controlY + t.pow(2) * end.y,
                        )
                    }
                }
                'c' -> while (hasNumber()) {
                    val x1 = number() ?: break
                    val y1 = number() ?: break
                    val x2 = number() ?: break
                    val y2 = number() ?: break
                    val x3 = number() ?: break
                    val y3 = number() ?: break
                    val origin = result.lastOrNull() ?: PointF(cursorX, cursorY)
                    val baseX = cursorX
                    val baseY = cursorY
                    val control1 = PointF(if (relative) baseX + x1 else x1, if (relative) baseY + y1 else y1)
                    val control2 = PointF(if (relative) baseX + x2 else x2, if (relative) baseY + y2 else y2)
                    val end = PointF(if (relative) baseX + x3 else x3, if (relative) baseY + y3 else y3)
                    cursorX = end.x
                    cursorY = end.y
                    for (step in 1..10) {
                        val t = step / 10f
                        val inverse = 1f - t
                        result += PointF(
                            inverse.pow(3) * origin.x + 3f * inverse.pow(2) * t * control1.x + 3f * inverse * t.pow(2) * control2.x + t.pow(3) * end.x,
                            inverse.pow(3) * origin.y + 3f * inverse.pow(2) * t * control1.y + 3f * inverse * t.pow(2) * control2.y + t.pow(3) * end.y,
                        )
                    }
                }
                'z' -> {
                    cursorX = startX
                    cursorY = startY
                    if (result.lastOrNull()?.let { it.x != startX || it.y != startY } == true) result += PointF(startX, startY)
                    command = ' '
                }
                // Smooth curves and arcs are safely ignored unless their path is also supplied in rooms.json.
                else -> {
                    if (index < tokens.size && tokens[index].firstOrNull()?.isLetter() == false) index++
                    else command = ' '
                }
            }
        }
        return result
    }

    private fun ellipsePolygon(cx: Float, cy: Float, rx: Float, ry: Float): List<PointF> {
        if (rx <= 0f || ry <= 0f) return emptyList()
        return (0 until 24).map { index ->
            val angle = 2.0 * Math.PI * index / 24
            PointF(cx + rx * kotlin.math.cos(angle).toFloat(), cy + ry * kotlin.math.sin(angle).toFloat())
        }
    }

    private fun XmlPullParser.number(name: String): Float =
        getAttributeValue(null, name)?.removeSuffix("px")?.toFloatOrNull() ?: 0f

    private val NUMBER = Regex("[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?")
    private val PATH_TOKEN = Regex("[MmLlHhVvQqCcZzAaSsTt]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?")
}
