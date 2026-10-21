package com.logos.navigator.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs

/** Читает heading с локального датчика Rotation Vector; данные не отправляются наружу. */
class CompassHeadingTracker(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var callback: ((Float) -> Unit)? = null
    private var smoothedHeading = Float.NaN
    private var lastEmittedHeading = Float.NaN

    fun start(onHeading: (Float) -> Unit): Boolean {
        stop()
        val sensor = rotationSensor ?: return false
        callback = onHeading
        val registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        if (!registered) callback = null
        return registered
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        callback = null
        smoothedHeading = Float.NaN
        lastEmittedHeading = Float.NaN
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        // Азимут датчика задан для портретной ориентации: в альбомной добавляем поворот экрана.
        val rawDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat() + displayRotationDegrees()
        val heading = normalize(rawDegrees)
        smoothedHeading = if (smoothedHeading.isNaN()) {
            heading
        } else {
            normalize(smoothedHeading + SMOOTHING * shortestDelta(smoothedHeading, heading))
        }
        // Мелкое дрожание стрелки не перерисовывает карту.
        if (lastEmittedHeading.isNaN() || abs(shortestDelta(lastEmittedHeading, smoothedHeading)) >= MIN_EMIT_DELTA_DEGREES) {
            lastEmittedHeading = smoothedHeading
            callback?.invoke(smoothedHeading)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Suppress("DEPRECATION")
    private fun displayRotationDegrees(): Float {
        val rotation = (appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.defaultDisplay
            ?.rotation
            ?: Surface.ROTATION_0
        return when (rotation) {
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> 0f
        }
    }

    private fun normalize(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    private fun shortestDelta(from: Float, to: Float): Float = ((to - from + 540f) % 360f) - 180f

    private companion object {
        const val SMOOTHING = 0.25f
        const val MIN_EMIT_DELTA_DEGREES = 1.5f
    }
}
