package com.logos.navigator.navigation

import com.logos.navigator.data.model.GeoAnchor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Orientation of a floor plan relative to the geographic frame: SVG offsets that correspond
 * to one metre east and one metre north. Used to turn compass headings into plan directions.
 */
data class MapOrientation(
    val eastX: Double,
    val eastY: Double,
    val northX: Double,
    val northY: Double,
) {
    /** Compass bearing (degrees clockwise from north) of the plan's "up" direction (negative SVG y). */
    val mapUpBearingDegrees: Double
        get() {
            // Solve a * east + b * north = (0, -1); (a, b) is the up direction in metres east/north.
            val determinant = eastX * northY - eastY * northX
            val a = (northX) / determinant
            val b = (-eastX) / determinant
            return normalizeDegrees(Math.toDegrees(atan2(a, b)))
        }

    /** Angle in SVG space (radians, atan2(y, x) convention) of the given compass bearing. */
    fun svgAngleForBearing(bearingDegrees: Double): Double {
        val radians = Math.toRadians(bearingDegrees)
        val x = kotlin.math.sin(radians) * eastX + cos(radians) * northX
        val y = kotlin.math.sin(radians) * eastY + cos(radians) * northY
        return atan2(y, x)
    }

    private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
}

object GeoReferencer {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val DEGREES_TO_RADIANS = PI / 180.0

    /** Derives the plan orientation from the anchors, or null when they cannot define one. */
    fun orientation(anchors: List<GeoAnchor>): MapOrientation? {
        if (anchors.size < 2) return null
        val latitude = anchors.map { it.latitude }.average()
        val longitude = anchors.map { it.longitude }.average()
        val latitudeScale = cos(latitude * DEGREES_TO_RADIANS)
        if (abs(latitudeScale) < MIN_LATITUDE_SCALE) return null
        val metreLatitude = 1.0 / (DEGREES_TO_RADIANS * EARTH_RADIUS_METERS)
        val metreLongitude = metreLatitude / latitudeScale
        val origin = toMapPoint(latitude, longitude, anchors) ?: return null
        val east = toMapPoint(latitude, longitude + metreLongitude, anchors) ?: return null
        val north = toMapPoint(latitude + metreLatitude, longitude, anchors) ?: return null
        val result = MapOrientation(
            eastX = east.first - origin.first,
            eastY = east.second - origin.second,
            northX = north.first - origin.first,
            northY = north.second - origin.second,
        )
        val determinant = result.eastX * result.northY - result.eastY * result.northX
        if (!determinant.isFinite() || abs(determinant) < MIN_BASELINE_SQUARED ||
            hypot(result.eastX, result.eastY) < MIN_COORDINATE_SCALE
        ) return null
        return result
    }

    fun toMapPoint(latitude: Double, longitude: Double, anchors: List<GeoAnchor>): Pair<Double, Double>? {
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0 || anchors.size < 2
        ) return null
        if (anchors.any { anchor ->
                !anchor.latitude.isFinite() || anchor.latitude !in -90.0..90.0 ||
                    !anchor.longitude.isFinite() || anchor.longitude !in -180.0..180.0 ||
                    !anchor.x.isFinite() || !anchor.y.isFinite()
            }
        ) return null

        val referenceLatitude = anchors.map { it.latitude }.average()
        val longitudeOrigin = anchors.first().longitude
        val referenceLongitudeOffset = anchors.map { wrappedLongitudeDelta(it.longitude, longitudeOrigin) }.average()
        val latitudeScale = cos(referenceLatitude * DEGREES_TO_RADIANS)
        if (abs(latitudeScale) < MIN_LATITUDE_SCALE) return null

        fun east(lon: Double): Double =
            (wrappedLongitudeDelta(lon, longitudeOrigin) - referenceLongitudeOffset) *
                DEGREES_TO_RADIANS * EARTH_RADIUS_METERS * latitudeScale
        fun north(lat: Double) = (lat - referenceLatitude) * DEGREES_TO_RADIANS * EARTH_RADIUS_METERS

        val samples = anchors.map { anchor ->
            doubleArrayOf(east(anchor.longitude), north(anchor.latitude), anchor.x, anchor.y)
        }
        val userEast = east(longitude)
        val userNorth = north(latitude)

        return if (samples.size >= 3) {
            val coefficientsX = fitAffine(samples, outputColumn = 2) ?: return null
            val coefficientsY = fitAffine(samples, outputColumn = 3) ?: return null
            val vector = doubleArrayOf(userEast, userNorth, 1.0)
            val x = dot(coefficientsX, vector)
            val y = dot(coefficientsY, vector)
            if (x.isFinite() && y.isFinite()) x to y else null
        } else {
            twoAnchorSimilarity(samples, userEast, userNorth)
        }
    }

    private fun fitAffine(samples: List<DoubleArray>, outputColumn: Int): DoubleArray? {
        val eastScale = samples.maxOf { abs(it[0]) }.coerceAtLeast(MIN_COORDINATE_SCALE)
        val northScale = samples.maxOf { abs(it[1]) }.coerceAtLeast(MIN_COORDINATE_SCALE)
        val normal = Array(3) { DoubleArray(4) }
        samples.forEach { row ->
            val vector = doubleArrayOf(row[0] / eastScale, row[1] / northScale, 1.0)
            for (i in 0..2) {
                for (j in 0..2) normal[i][j] += vector[i] * vector[j]
                normal[i][3] += vector[i] * row[outputColumn]
            }
        }
        val normalized = solve3x3(normal) ?: return null
        return doubleArrayOf(normalized[0] / eastScale, normalized[1] / northScale, normalized[2])
    }

    private fun solve3x3(matrix: Array<DoubleArray>): DoubleArray? {
        for (column in 0..2) {
            var pivot = column
            for (row in column + 1..2) {
                if (kotlin.math.abs(matrix[row][column]) > kotlin.math.abs(matrix[pivot][column])) pivot = row
            }
            if (kotlin.math.abs(matrix[pivot][column]) < 1e-12) return null
            val swap = matrix[column]
            matrix[column] = matrix[pivot]
            matrix[pivot] = swap

            val divisor = matrix[column][column]
            for (j in column..3) matrix[column][j] /= divisor
            for (row in 0..2) {
                if (row == column) continue
                val factor = matrix[row][column]
                for (j in column..3) matrix[row][j] -= factor * matrix[column][j]
            }
        }
        return doubleArrayOf(matrix[0][3], matrix[1][3], matrix[2][3])
    }

    private fun twoAnchorSimilarity(
        samples: List<DoubleArray>,
        userEast: Double,
        userNorth: Double,
    ): Pair<Double, Double>? {
        val first = samples[0]
        val second = samples[1]
        val geoEast = second[0] - first[0]
        val geoNorth = second[1] - first[1]
        val unitX = second[2] - first[2]
        val unitY = second[3] - first[3]
        val geoLengthSquared = geoEast * geoEast + geoNorth * geoNorth
        val unitLengthSquared = unitX * unitX + unitY * unitY
        if (geoLengthSquared < MIN_BASELINE_SQUARED || unitLengthSquared < MIN_BASELINE_SQUARED) return null

        val scaleCos = (unitX * geoEast + unitY * geoNorth) / geoLengthSquared
        val scaleSin = (unitY * geoEast - unitX * geoNorth) / geoLengthSquared
        val deltaEast = userEast - first[0]
        val deltaNorth = userNorth - first[1]
        val x = first[2] + scaleCos * deltaEast - scaleSin * deltaNorth
        val y = first[3] + scaleSin * deltaEast + scaleCos * deltaNorth
        return if (x.isFinite() && y.isFinite()) x to y else null
    }

    private fun wrappedLongitudeDelta(longitude: Double, origin: Double): Double {
        var delta = (longitude - origin) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }

    private fun dot(left: DoubleArray, right: DoubleArray): Double =
        left.indices.sumOf { index -> left[index] * right[index] }

    private const val MIN_BASELINE_SQUARED = 1e-12
    private const val MIN_COORDINATE_SCALE = 1e-6
    private const val MIN_LATITUDE_SCALE = 1e-8
}
