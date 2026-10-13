package com.logos.navigator.navigation

import com.logos.navigator.data.model.GeoAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class GeoReferencerTest {
    @Test
    fun fitsAffineTransformFromFourAnchors() {
        val anchors = listOf(
            anchor("south_west", east = -20.0, north = -15.0),
            anchor("south_east", east = 20.0, north = -15.0),
            anchor("north_west", east = -20.0, north = 15.0),
            anchor("north_east", east = 20.0, north = 15.0),
        )

        val point = GeoReferencer.toMapPoint(latitudeFor(north = 5.0), longitudeFor(east = 10.0), anchors)
            ?: error("Аффинное преобразование не вернуло координаты.")

        assertEquals(1_025.0, point.first, 0.05)
        assertEquals(405.0, point.second, 0.05)
    }

    @Test
    fun supportsTwoAnchorSimilarityTransform() {
        val anchors = listOf(
            GeoAnchor(floor = 1, id = "a", latitude = latitudeFor(0.0), longitude = longitudeFor(0.0), x = 100.0, y = 200.0),
            GeoAnchor(floor = 1, id = "b", latitude = latitudeFor(0.0), longitude = longitudeFor(10.0), x = 120.0, y = 210.0),
        )

        val point = GeoReferencer.toMapPoint(latitudeFor(north = 3.0), longitudeFor(east = 5.0), anchors)
            ?: error("Преобразование по двум точкам не вернуло координаты.")

        assertEquals(107.0, point.first, 0.05)
        assertEquals(211.0, point.second, 0.05)
    }

    @Test
    fun rejectsInvalidCoordinatesAndDegenerateAnchors() {
        val samePointAnchors = listOf(
            GeoAnchor(floor = 1, id = "a", latitude = 55.0, longitude = 37.0, x = 1.0, y = 1.0),
            GeoAnchor(floor = 1, id = "b", latitude = 55.0, longitude = 37.0, x = 2.0, y = 2.0),
        )

        assertNull(GeoReferencer.toMapPoint(91.0, 37.0, samePointAnchors))
        assertNull(GeoReferencer.toMapPoint(55.0, 37.0, samePointAnchors))
    }

    private fun anchor(id: String, east: Double, north: Double): GeoAnchor = GeoAnchor(
        floor = 1,
        id = id,
        latitude = latitudeFor(north),
        longitude = longitudeFor(east),
        x = 1_000.0 + 2.0 * east + north,
        y = 400.0 - east + 3.0 * north,
    )

    private fun latitudeFor(north: Double): Double =
        REFERENCE_LATITUDE + north / EARTH_RADIUS_METERS * DEGREES_TO_RADIANS

    private fun longitudeFor(east: Double): Double =
        REFERENCE_LONGITUDE + east / (EARTH_RADIUS_METERS * cos(REFERENCE_LATITUDE * DEGREES_TO_RADIANS)) * DEGREES_TO_RADIANS

    private companion object {
        const val REFERENCE_LATITUDE = 55.0
        const val REFERENCE_LONGITUDE = 37.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val DEGREES_TO_RADIANS = PI / 180.0
    }
}
