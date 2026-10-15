package com.logos.navigator.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.logos.navigator.R
import com.logos.navigator.data.model.LocationAccuracyMode

/** Обёртка над Fused Location Provider. Геоданные не отправляются приложением на сервер. */
class FusedLocationTracker(context: Context) {
    private val appContext = context.applicationContext
    private var client: com.google.android.gms.location.FusedLocationProviderClient? = null
    private var callback: LocationCallback? = null
    @Volatile private var generation = 0

    private fun getClient(): com.google.android.gms.location.FusedLocationProviderClient =
        client ?: LocationServices.getFusedLocationProviderClient(appContext).also { client = it }

    @SuppressLint("MissingPermission")
    fun start(
        accuracyMode: LocationAccuracyMode,
        updateIntervalSeconds: Int,
        onLocation: (Location) -> Unit,
        onAvailability: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        val requestGeneration = generation
        val hasFine = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            onError(appContext.getString(R.string.gps_permission_denied))
            return
        }
        val locationClient = try {
            getClient()
        } catch (_: Exception) {
            onError(appContext.getString(R.string.location_provider_client_error, appContext.getString(R.string.unknown_error)))
            return
        }

        val priority = when (accuracyMode) {
            LocationAccuracyMode.HIGH -> if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            LocationAccuracyMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            LocationAccuracyMode.BATTERY -> Priority.PRIORITY_LOW_POWER
        }
        val intervalMs = updateIntervalSeconds.coerceIn(1, 10) * 1_000L
        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2).coerceAtLeast(MIN_UPDATE_INTERVAL_MS))
            .setMinUpdateDistanceMeters(1f)
            .setWaitForAccurateLocation(false)
            .build()

        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (requestGeneration == generation) result.lastLocation?.let(onLocation)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (requestGeneration == generation) onAvailability(availability.isLocationAvailable)
            }
        }
        callback = newCallback

        try {
            locationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (requestGeneration == generation) location?.let(onLocation)
                }
                // Отказ в чтении кэшированной точки не отменяет запрос новых GPS-измерений.
                .addOnFailureListener {
                    if (requestGeneration == generation) onAvailability(false)
                }
            locationClient.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
                .addOnFailureListener {
                    if (requestGeneration == generation) {
                        onError(appContext.getString(R.string.location_provider_unavailable))
                    }
                }
        } catch (exception: SecurityException) {
            if (requestGeneration == generation) {
                callback = null
                onError(appContext.getString(R.string.location_no_access))
            }
        } catch (_: Exception) {
            if (requestGeneration == generation) {
                callback = null
                onError(appContext.getString(R.string.location_start_error, appContext.getString(R.string.location_check_device_settings)))
            }
        }
    }

    fun stop() {
        generation++
        callback?.let { oldCallback -> client?.removeLocationUpdates(oldCallback) }
        callback = null
    }

    private companion object {
        const val MIN_UPDATE_INTERVAL_MS = 500L
    }
}
