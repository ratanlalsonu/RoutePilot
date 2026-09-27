package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
 * Real Android Location Services wrapper using FusedLocationProviderClient.
 *
 * Continuously streams driver GPS coordinates, heading/bearing, and speed during navigation,
 * and properly checks location permissions and GPS hardware status.
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
                null
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(
        intervalMillis: Long = 2500L,
        minDistanceMeters: Float = 4f
    ): Flow<LocationPoint> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    LocationPoint(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        bearing = if (loc.hasBearing()) loc.bearing else 0f,
                        speedMps = if (loc.hasSpeed()) loc.speed else 0f,
                        accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 10f,
                        timestamp = loc.time
                    )
                )
            }
        }

        runCatching {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }

        awaitClose {
            runCatching { fusedClient.removeLocationUpdates(callback) }
        }
    }
}
