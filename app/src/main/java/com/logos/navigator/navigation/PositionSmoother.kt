package com.logos.navigator.navigation

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Сглаживает GPS-точки на плане этажа простым фильтром Калмана с моделью «пешеход стоит или идёт».
 * Неточные измерения сдвигают маркер слабее точных, поэтому он не прыгает по кабинетам при каждом фиксе.
 */
class PositionSmoother(
    /** Скорость, с которой допускается смещение оценки между измерениями, м/с. */
    private val walkingSpeedMetersPerSecond: Double = DEFAULT_WALKING_SPEED,
) {
    private var floor: Int? = null
    private var x = 0.0
    private var y = 0.0
    /** Дисперсия оценки в квадратных метрах. */
    private var varianceSquareMeters = 0.0
    private var lastTimestampMs = 0L

    fun reset() {
        floor = null
        varianceSquareMeters = 0.0
        lastTimestampMs = 0L
    }

    /**
     * Добавляет измерение в координатах SVG и возвращает сглаженную точку.
     * Смена этажа, долгий перерыв или резкий скачок далеко за пределы погрешности сбрасывают фильтр.
     */
    fun update(
        floor: Int,
        x: Double,
        y: Double,
        accuracyMeters: Double?,
        timestampMs: Long,
        metersPerUnit: Double,
    ): Pair<Double, Double> {
        val scale = metersPerUnit.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_METERS_PER_UNIT
        val sigma = accuracyMeters?.takeIf { it.isFinite() && it > 0.0 }?.coerceAtLeast(MIN_ACCURACY_METERS)
            ?: DEFAULT_ACCURACY_METERS
        val measurementVariance = sigma * sigma
        val elapsedMs = timestampMs - lastTimestampMs

        val previousFloor = this.floor
        if (previousFloor == null || previousFloor != floor || elapsedMs < 0L || elapsedMs > RESET_AFTER_MS) {
            return restart(floor, x, y, measurementVariance, timestampMs)
        }

        val elapsedSeconds = elapsedMs / 1_000.0
        val travel = walkingSpeedMetersPerSecond * elapsedSeconds
        val predictedVariance = varianceSquareMeters + travel * travel + MIN_PROCESS_VARIANCE
        val jumpMeters = hypot(x - this.x, y - this.y) * scale
        if (jumpMeters > OUTLIER_SIGMAS * sqrt(predictedVariance + measurementVariance) && sigma <= sqrt(predictedVariance)) {
            // Точное измерение далеко от старой оценки: человек действительно переместился (например, после лестницы).
            return restart(floor, x, y, measurementVariance, timestampMs)
        }

        val gain = predictedVariance / (predictedVariance + measurementVariance)
        this.x += gain * (x - this.x)
        this.y += gain * (y - this.y)
        varianceSquareMeters = (1.0 - gain) * predictedVariance
        lastTimestampMs = timestampMs
        return this.x to this.y
    }

    private fun restart(floor: Int, x: Double, y: Double, variance: Double, timestampMs: Long): Pair<Double, Double> {
        this.floor = floor
        this.x = x
        this.y = y
        varianceSquareMeters = variance
        lastTimestampMs = timestampMs
        return x to y
    }

    private companion object {
        const val DEFAULT_WALKING_SPEED = 1.6
        const val DEFAULT_ACCURACY_METERS = 15.0
        const val MIN_ACCURACY_METERS = 1.0
        const val MIN_PROCESS_VARIANCE = 0.25
        const val DEFAULT_METERS_PER_UNIT = 0.083333
        const val RESET_AFTER_MS = 30_000L
        const val OUTLIER_SIGMAS = 3.0
    }
}
