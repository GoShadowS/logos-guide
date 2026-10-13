package com.logos.navigator.svg

import android.content.res.AssetManager
import android.graphics.PointF
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStreamReader

/**
 * Необязательное извлечение центра простого SVG-элемента с id. Поддержаны rect/circle/ellipse;
 * для path, group и геометрии с transform координаты безопаснее снять в Inkscape и занести в JSON.
 */
object SvgCoordinateExtractor {
    fun simpleShapeCenter(assets: AssetManager, assetPath: String, elementId: String): PointF? =
        assets.open(assetPath).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(InputStreamReader(input, Charsets.UTF_8))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.getAttributeValue(null, "id") == elementId) {
                    return@use when (parser.name) {
                        "rect" -> {
                            val x = parser.number("x")
                            val y = parser.number("y")
                            PointF(x + parser.number("width") / 2f, y + parser.number("height") / 2f)
                        }
                        "circle" -> PointF(parser.number("cx"), parser.number("cy"))
                        "ellipse" -> PointF(parser.number("cx"), parser.number("cy"))
                        else -> null
                    }
                }
                event = parser.next()
            }
            null
        }

    private fun XmlPullParser.number(name: String): Float =
        getAttributeValue(null, name)?.removeSuffix("px")?.toFloatOrNull() ?: 0f
}
