package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RideLocationTracker(context: Context) {
    private val appContext = context.applicationContext
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasLocationPermission(): Boolean = hasLocationPermission(appContext)

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? = try {
        if (!hasLocationPermission()) null else fusedLocationClient.lastLocation.await()
    } catch (_: Exception) {
        null
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(powerMode: PowerMode = PowerMode.STANDARD): Location? {
        if (!hasLocationPermission()) return null
        val priority = priorityFor(powerMode)
        val tokenSource = CancellationTokenSource()
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setDurationMillis(8000L)
                .setMaxUpdateAgeMillis(5000L)
                .build()
            fusedLocationClient.getCurrentLocation(request, tokenSource.token).await()
                ?: fusedLocationClient.lastLocation.await()
        } catch (_: Exception) {
            getLastKnownLocation()
        }
    }

    @SuppressLint("MissingPermission")
    fun locationUpdates(powerMode: PowerMode = PowerMode.STANDARD): Flow<Location> = callbackFlow {
        val priority = priorityFor(powerMode)
        val request = LocationRequest.Builder(priority, powerMode.updateIntervalMillis)
            .setMinUpdateIntervalMillis(powerMode.minUpdateIntervalMillis)
            .setMinUpdateDistanceMeters(powerMode.minUpdateDistanceMeters)
            .setMaxUpdateDelayMillis(powerMode.updateIntervalMillis)
            .setWaitForAccurateLocation(false)
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

    private fun priorityFor(powerMode: PowerMode): Int = when (powerMode) {
        PowerMode.ECO -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        PowerMode.STANDARD -> Priority.PRIORITY_HIGH_ACCURACY
    }

    companion object {
        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }
}
