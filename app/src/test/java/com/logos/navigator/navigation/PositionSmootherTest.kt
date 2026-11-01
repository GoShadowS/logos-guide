package com.logos.navigator.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionSmootherTest {
    @Test
    fun firstFixIsReturnedAsIs() {
        val smoother = PositionSmoother()
        val (x, y) = smoother.update(1, 100.0, 50.0, accuracyMeters = 10.0, timestampMs = 1_000L, metersPerUnit = 0.1)
        assertEquals(100.0, x, 1e-9)
        assertEquals(50.0, y, 1e-9)
    }

    @Test
    fun noisyFixMovesMarkerLessThanPreciseFix() {
        val noisy = PositionSmoother()
        noisy.update(1, 100.0, 50.0, accuracyMeters = 5.0, timestampMs = 0L, metersPerUnit = 0.1)
        val (noisyX, _) = noisy.update(1, 140.0, 50.0, accuracyMeters = 30.0, timestampMs = 1_000L, metersPerUnit = 0.1)

        val precise = PositionSmoother()
        precise.update(1, 100.0, 50.0, accuracyMeters = 5.0, timestampMs = 0L, metersPerUnit = 0.1)
        val (preciseX, _) = precise.update(1, 140.0, 50.0, accuracyMeters = 5.0, timestampMs = 1_000L, metersPerUnit = 0.1)

        assertTrue(noisyX > 100.0 && noisyX < preciseX)
        assertTrue(preciseX < 140.0)
    }

    @Test
    fun floorChangeResetsFilter() {
        val smoother = PositionSmoother()
        smoother.update(1, 100.0, 50.0, accuracyMeters = 5.0, timestampMs = 0L, metersPerUnit = 0.1)
        val (x, y) = smoother.update(2, 300.0, 20.0, accuracyMeters = 30.0, timestampMs = 1_000L, metersPerUnit = 0.1)
        assertEquals(300.0, x, 1e-9)
        assertEquals(20.0, y, 1e-9)
    }
}
