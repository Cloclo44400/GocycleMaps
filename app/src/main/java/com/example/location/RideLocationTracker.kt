package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RideLocationTracker(context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? = try {
        fusedLocationClient.lastLocation.await()
    } catch (_: Exception) {
        null
    }

    @SuppressLint("MissingPermission")
    fun locationUpdates(powerMode: PowerMode = PowerMode.STANDARD): Flow<Location> = callbackFlow {
        val priority = when (powerMode) {
            PowerMode.ECO -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            PowerMode.STANDARD -> Priority.PRIORITY_HIGH_ACCURACY
        }
        val request = LocationRequest.Builder(priority, powerMode.updateIntervalMillis)
            .setMinUpdateIntervalMillis(powerMode.minUpdateIntervalMillis)
            .setMinUpdateDistanceMeters(powerMode.minUpdateDistanceMeters)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    trySend(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper(),
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }
}
