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
import com.example.domain.routing.GeoUtils
import com.example.domain.routing.OsmRoadNetworkProvider
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real Android Location Services wrapper combining:
 * 1. Google Play Services [FusedLocationProviderClient] (High Accuracy, 500ms)
 * 2. Platform [LocationManager] (GPS_PROVIDER + NETWORK_PROVIDER)
 * 3. Hardware Inertial Pedestrian Dead Reckoning ([Sensor.TYPE_LINEAR_ACCELERATION] /
 *    [Sensor.TYPE_ACCELEROMETER] + [Sensor.TYPE_ROTATION_VECTOR]) so physical walking/movement
 *    with the mobile phone updates distance and ETA even indoors where satellite lock is slow.
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

        var latestPoint: LocationPoint = OsmRoadNetworkProvider.DEFAULT_ORIGIN
        var lastGpsDisplacementMillis = 0L
        var lastInertialStepMillis = 0L
        var latestHeadingDegrees = 45f

        val emitGpsLocation = { loc: android.location.Location ->
            val now = System.currentTimeMillis()
            val candidate = LocationPoint(
                latitude = loc.latitude,
                longitude = loc.longitude,
                bearing = if (loc.hasBearing()) loc.bearing else latestHeadingDegrees,
                speedMps = if (loc.hasSpeed()) loc.speed else 0f,
                accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 10f,
                timestamp = if (loc.time > 0L) loc.time else now
            )
            val movedMeters = GeoUtils.haversineMeters(latestPoint, candidate)
            if (movedMeters >= 0.4) {
                lastGpsDisplacementMillis = now
            }
            if (loc.hasBearing()) {
                latestHeadingDegrees = loc.bearing
            }
            latestPoint = candidate
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

        // Hardware inertial motion & orientation listener so physical walking/movement with the phone
        // immediately updates travelled distance and remaining ETA even when indoor GPS is static
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val gravity = FloatArray(3)
        var gravityInitialized = false

        val inertialListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        runCatching {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)
                            val deg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
                            if (!deg.isNaN()) {
                                latestHeadingDegrees = deg
                            }
                        }
                    }

                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        val lx = event.values[0]
                        val ly = event.values[1]
                        val lz = event.values[2]
                        val linMag = sqrt(lx * lx + ly * ly + lz * lz)
                        handleInertialMotionImpulse(linMag)
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        if (!gravityInitialized) {
                            gravity[0] = event.values[0]
                            gravity[1] = event.values[1]
                            gravity[2] = event.values[2]
                            gravityInitialized = true
                            return
                        }
                        val alpha = 0.85f
                        gravity[0] = alpha * gravity[0] + (1f - alpha) * event.values[0]
                        gravity[1] = alpha * gravity[1] + (1f - alpha) * event.values[1]
                        gravity[2] = alpha * gravity[2] + (1f - alpha) * event.values[2]
                        val lx = event.values[0] - gravity[0]
                        val ly = event.values[1] - gravity[1]
                        val lz = event.values[2] - gravity[2]
                        val linMag = sqrt(lx * lx + ly * ly + lz * lz)
                        handleInertialMotionImpulse(linMag)
                    }
                }
            }

            private fun handleInertialMotionImpulse(linMag: Float) {
                val now = System.currentTimeMillis()
                // Trigger only on real physical movement/walking (> 1.15 m/s²) when GPS hasn't already displaced
                if (linMag >= 1.15f &&
                    now - lastInertialStepMillis >= 380L &&
                    now - lastGpsDisplacementMillis >= 450L
                ) {
                    lastInertialStepMillis = now
                    val strideMeters = (linMag * 2.5).coerceIn(2.5, 14.0)
                    val headingRad = Math.toRadians(latestHeadingDegrees.toDouble())
                    val dLat = (strideMeters * cos(headingRad)) / 111320.0
                    val cosLat = cos(Math.toRadians(latestPoint.latitude)).coerceAtLeast(0.1)
                    val dLon = (strideMeters * sin(headingRad)) / (111320.0 * cosLat)
                    val steppedPoint = latestPoint.copy(
                        latitude = latestPoint.latitude + dLat,
                        longitude = latestPoint.longitude + dLon,
                        bearing = latestHeadingDegrees,
                        speedMps = (strideMeters / 0.40).toFloat().coerceIn(1.5f, 16.0f),
                        timestamp = now
                    )
                    latestPoint = steppedPoint
                    trySend(steppedPoint)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        runCatching {
            val rotSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            if (rotSensor != null) {
                sensorManager?.registerListener(inertialListener, rotSensor, SensorManager.SENSOR_DELAY_UI)
            }
            val linAccSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            if (linAccSensor != null) {
                sensorManager?.registerListener(inertialListener, linAccSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                val accSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                if (accSensor != null) {
                    sensorManager?.registerListener(inertialListener, accSensor, SensorManager.SENSOR_DELAY_UI)
                }
            }
        }

        awaitClose {
            runCatching { fusedClient.removeLocationUpdates(fusedCallback) }
            runCatching { locationManager?.removeUpdates(platformListener) }
            runCatching { sensorManager?.unregisterListener(inertialListener) }
        }
    }
}

