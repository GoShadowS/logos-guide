package com.logos.navigator.navigation

import kotlin.math.ceil

/** Policy for using provider-reported horizontal accuracy in map and route UI. */
internal object GpsAccuracyPolicy {
    /** Unknown or invalid accuracy is never sufficient for automatic route guidance. */
    fun isWithinThreshold(accuracyMeters: Float?, thresholdMeters: Float): Boolean {
        if (accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters < 0f) return false
        if (!thresholdMeters.isFinite() || thresholdMeters < 0f) return false
        return accuracyMeters <= thresholdMeters
    }

    /** Keep age checks on the same monotonic or wall-clock time base as the supplied fix. */
    fun isAgeWithinBounds(age: Long, maximumAge: Long, maximumFutureSkew: Long): Boolean {
        if (maximumAge < 0L || maximumFutureSkew < 0L) return false
        return age >= -maximumFutureSkew && age <= maximumAge
    }

    /** Reject a delayed provider result that would replace a newer accepted measurement. */
    fun isNotOlderThan(
        candidateElapsedRealtimeNanos: Long,
        previousElapsedRealtimeNanos: Long,
        candidateWallClockMillis: Long,
        previousWallClockMillis: Long,
    ): Boolean = if (candidateElapsedRealtimeNanos > 0L && previousElapsedRealtimeNanos > 0L) {
        candidateElapsedRealtimeNanos >= previousElapsedRealtimeNanos
    } else {
        candidateWallClockMillis >= previousWallClockMillis
    }

    /** Delay until the fix is strictly older than the inclusive maximum-age limit. */
    fun millisecondsUntilStale(age: Long, maximumAge: Long, unitsPerMillisecond: Long): Long? {
        if (maximumAge < 0L || unitsPerMillisecond <= 0L) return null
        val remainingUnits = try {
            Math.subtractExact(maximumAge, age)
        } catch (_: ArithmeticException) {
            return Long.MAX_VALUE
        }
        if (remainingUnits < 0L) return 0L
        val wholeMilliseconds = remainingUnits / unitsPerMillisecond
        val roundedMilliseconds = wholeMilliseconds + if (remainingUnits % unitsPerMillisecond == 0L) 0L else 1L
        return if (roundedMilliseconds == Long.MAX_VALUE) Long.MAX_VALUE else roundedMilliseconds + 1L
    }

    /** A route may be built or advanced automatically only from a fresh fix within the threshold. */
    fun isEligibleForAutomaticGuidance(
        accuracyMeters: Float?,
        thresholdMeters: Float,
        fixAge: Long,
        maximumAge: Long,
        maximumFutureSkew: Long,
    ): Boolean = isAgeWithinBounds(fixAge, maximumAge, maximumFutureSkew) &&
        isWithinThreshold(accuracyMeters, thresholdMeters)

    /** Round a displayed radius upward so the UI does not understate the provider's estimate. */
    fun displayRadiusMeters(accuracyMeters: Float?): Int? {
        val validAccuracy = accuracyMeters?.takeIf { it.isFinite() && it >= 0f } ?: return null
        return ceil(validAccuracy.toDouble()).toInt()
    }
}
