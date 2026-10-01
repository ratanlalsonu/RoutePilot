package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.domain.model.LocationPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Real Android Location Services wrapper using:
 * 1. Google Play Services [FusedLocationProviderClient] (High Accuracy)
 * 2. Platform [LocationManager] (GPS_PROVIDER + NETWORK_PROVIDER)
 * 3. Hardware compass/rotation sensor ([Sensor.TYPE_ROTATION_VECTOR]) for live device heading only
 *    (never synthesizes or steps fake coordinates when stationary).
 */
class LocationTracker(
    private val context: Context
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationOnce(): LocationPoint? {
        if (!hasLocationPermission()) return null
        return runCatching {
            val loc = fusedClient.lastLocation.await()
            if (loc != null) {
                LocationPoint(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    bearing = if (loc.hasBearing()) loc.bearing else 0f,
                    speedMps = if (loc.hasSpeed()) loc.speed else 0f,
                    accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 10f,
                    timestamp = loc.time
                )
            } else {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val fallbackLoc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                fallbackLoc?.let {
                    LocationPoint(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        bearing = if (it.hasBearing()) it.bearing else 0f,
                        speedMps = if (it.hasSpeed()) it.speed else 0f,
                        accuracyMeters = if (it.hasAccuracy()) it.accuracy else 10f,
                        timestamp = it.time
                    )
                }
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(
        intervalMillis: Long = 500L,
        minDistanceMeters: Float = 0f
    ): Flow<LocationPoint> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(250L)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .setWaitForAccurateLocation(false)
            .build()

        var latestHeadingDegrees = 0f

        val emitGpsLocation = { loc: android.location.Location ->
            val now = System.currentTimeMillis()
            if (loc.hasBearing()) {
                latestHeadingDegrees = loc.bearing
            }
            val candidate = LocationPoint(
                latitude = loc.latitude,
                longitude = loc.longitude,
                bearing = if (loc.hasBearing()) loc.bearing else latestHeadingDegrees,
                speedMps = if (loc.hasSpeed()) loc.speed else 0f,
                accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 10f,
                timestamp = if (loc.time > 0L) loc.time else now
            )
            trySend(candidate)
        }

        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                emitGpsLocation(loc)
            }
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val platformListener = android.location.LocationListener { loc ->
            emitGpsLocation(loc)
        }

        if (hasLocationPermission()) {
            runCatching {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) emitGpsLocation(loc)
                }
                fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            }

            runCatching {
                if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(emitGpsLocation)
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        intervalMillis,
                        minDistanceMeters,
                        platformListener,
                        Looper.getMainLooper()
                    )
                }
                if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                    locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let(emitGpsLocation)
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        intervalMillis,
                        minDistanceMeters,
                        platformListener,
                        Looper.getMainLooper()
                    )
                }
            }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val headingListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR ||
                    event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR
                ) {
                    runCatching {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val deg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
                        if (!deg.isNaN()) {
                            latestHeadingDegrees = deg
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        runCatching {
            val rotSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            if (rotSensor != null) {
                sensorManager?.registerListener(headingListener, rotSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }

        awaitClose {
            runCatching { fusedClient.removeLocationUpdates(fusedCallback) }
            runCatching { locationManager?.removeUpdates(platformListener) }
            runCatching { sensorManager?.unregisterListener(headingListener) }
        }
    }
}
