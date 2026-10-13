package com.logos.navigator.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Читает heading с локального датчика Rotation Vector; данные не отправляются наружу. */
class CompassHeadingTracker(context: Context) : SensorEventListener {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var callback: ((Float) -> Unit)? = null

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
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val rawDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        callback?.invoke((rawDegrees + 360f) % 360f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
