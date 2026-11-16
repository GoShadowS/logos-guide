package com.logos.navigator.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsAccuracyPolicyTest {
    @Test
    fun missingOrInvalidAccuracyIsNotSuitableForAutomaticGuidance() {
        assertFalse(GpsAccuracyPolicy.isWithinThreshold(null, 20f))
        assertFalse(GpsAccuracyPolicy.isWithinThreshold(Float.NaN, 20f))
        assertFalse(GpsAccuracyPolicy.isWithinThreshold(Float.POSITIVE_INFINITY, 20f))
        assertFalse(GpsAccuracyPolicy.isWithinThreshold(-1f, 20f))
    }

    @Test
    fun acceptsAccuracyAtOrBelowTheConfiguredThresholdOnly() {
        assertTrue(GpsAccuracyPolicy.isWithinThreshold(19.9f, 20f))
        assertTrue(GpsAccuracyPolicy.isWithinThreshold(20f, 20f))
        assertFalse(GpsAccuracyPolicy.isWithinThreshold(20.01f, 20f))
    }

    @Test
    fun invalidThresholdIsNeverAccepted() {
        assertFalse(GpsAccuracyPolicy.isWithinThreshold(1f, Float.NaN))
        assertFalse(GpsAccuracyPolicy.isWithinThreshold(1f, Float.POSITIVE_INFINITY))
        assertFalse(GpsAccuracyPolicy.isWithinThreshold(0f, -1f))
    }

    @Test
    fun locationAgeAcceptsOnlyTheConfiguredPastAndFutureWindow() {
        assertTrue(GpsAccuracyPolicy.isAgeWithinBounds(120_000L, 120_000L, 5_000L))
        assertTrue(GpsAccuracyPolicy.isAgeWithinBounds(-5_000L, 120_000L, 5_000L))
        assertFalse(GpsAccuracyPolicy.isAgeWithinBounds(120_001L, 120_000L, 5_000L))
        assertFalse(GpsAccuracyPolicy.isAgeWithinBounds(-5_001L, 120_000L, 5_000L))
        assertFalse(GpsAccuracyPolicy.isAgeWithinBounds(0L, -1L, 5_000L))
        assertFalse(GpsAccuracyPolicy.isAgeWithinBounds(0L, 120_000L, -1L))
    }

    @Test
    fun olderDelayedMeasurementsDoNotReplaceNewerAcceptedFixes() {
        assertTrue(GpsAccuracyPolicy.isNotOlderThan(200L, 100L, 800L, 1_000L))
        assertFalse(GpsAccuracyPolicy.isNotOlderThan(100L, 200L, 1_200L, 1_000L))
        assertTrue(GpsAccuracyPolicy.isNotOlderThan(0L, 0L, 1_001L, 1_000L))
        assertFalse(GpsAccuracyPolicy.isNotOlderThan(0L, 0L, 999L, 1_000L))
        assertTrue(GpsAccuracyPolicy.isNotOlderThan(200L, 0L, 1_001L, 1_000L))
    }

    @Test
    fun staleTimerExpiresOnlyAfterTheInclusiveAgeLimit() {
        assertEquals(120_001L, GpsAccuracyPolicy.millisecondsUntilStale(0L, 120_000L, 1L))
        assertEquals(1L, GpsAccuracyPolicy.millisecondsUntilStale(120_000L, 120_000L, 1L))
        assertEquals(2L, GpsAccuracyPolicy.millisecondsUntilStale(119_999_999_000L, 120_000_000_000L, 1_000_000L))
        assertEquals(125_001L, GpsAccuracyPolicy.millisecondsUntilStale(-5_000L, 120_000L, 1L))
        assertEquals(0L, GpsAccuracyPolicy.millisecondsUntilStale(120_001L, 120_000L, 1L))
        assertNull(GpsAccuracyPolicy.millisecondsUntilStale(0L, -1L, 1L))
        assertNull(GpsAccuracyPolicy.millisecondsUntilStale(0L, 120_000L, 0L))
    }

    @Test
    fun automaticGuidanceRequiresBothFreshnessAndThresholdAccuracy() {
        assertTrue(GpsAccuracyPolicy.isEligibleForAutomaticGuidance(19f, 20f, 120_000L, 120_000L, 5_000L))
        assertFalse(GpsAccuracyPolicy.isEligibleForAutomaticGuidance(19f, 20f, 120_001L, 120_000L, 5_000L))
        assertFalse(GpsAccuracyPolicy.isEligibleForAutomaticGuidance(null, 20f, 0L, 120_000L, 5_000L))
        assertFalse(GpsAccuracyPolicy.isEligibleForAutomaticGuidance(21f, 20f, 0L, 120_000L, 5_000L))
    }

    @Test
    fun displayedRadiusRoundsUpAndRejectsUnknownValues() {
        assertEquals(0, requireNotNull(GpsAccuracyPolicy.displayRadiusMeters(0f)))
        assertEquals(15, requireNotNull(GpsAccuracyPolicy.displayRadiusMeters(15f)))
        assertEquals(16, requireNotNull(GpsAccuracyPolicy.displayRadiusMeters(15.01f)))
        assertNull(GpsAccuracyPolicy.displayRadiusMeters(null))
        assertNull(GpsAccuracyPolicy.displayRadiusMeters(Float.NaN))
        assertNull(GpsAccuracyPolicy.displayRadiusMeters(-1f))
    }
}
