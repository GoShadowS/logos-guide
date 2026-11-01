package com.logos.navigator.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
    @Test
    fun releaseUpdateIsNewerThanPreviousOpenDoorsVersion() {
        assertTrue(isNewerVersion("1.1.0-release", "1.0.0-opendoors"))
    }

    @Test
    fun comparesNumericVersionComponents() {
        assertTrue(isNewerVersion("1.2.0-release", "1.1.9-release"))
        assertFalse(isNewerVersion("1.1.9-release", "1.2.0-release"))
    }

    @Test
    fun equalNumericVersionsDoNotTriggerAnUpdate() {
        assertFalse(isNewerVersion("1.1.0-release", "1.1.0-opendoors"))
    }

    @Test
    fun malformedVersionsAreNotConsideredNewer() {
        assertFalse(isNewerVersion("latest", "1.0.0-opendoors"))
        assertFalse(isNewerVersion("1.1.0-release", "unknown"))
    }
}
