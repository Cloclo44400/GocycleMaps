package com.example.location

import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.util.Log
import com.example.data.model.PlannedRoute
import com.example.data.model.SportType
import com.example.ui.viewmodel.RideSessionState
import com.example.ui.viewmodel.RideStatus
import com.example.ui.viewmodel.RideSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class RideSessionCoordinator(
    private val appContext: Context,
    private val locationTracker: RideLocationTracker = RideLocationTracker(appContext),
    private val powerModePreferences: PowerModePreferences = PowerModePreferences(appContext),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val powerMode: StateFlow<PowerMode> = powerModePreferences.powerMode

    private val _rideSession = MutableStateFlow(RideSessionState())
    val rideSession: StateFlow<RideSessionState> = _rideSession.asStateFlow()

    private var timerJob: Job? = null
    private var locationJob: Job? = null
    private var lastAcceptedLocation: Location? = null
    private var pendingResidualMeters: Double = 0.0
    private var accumulatedElapsedMillis: Long = 0L
    private var activeSegmentStartElapsedMillis: Long = 0L

    fun selectSport(sportType: SportType) {
        if (_rideSession.value.status != RideStatus.IDLE) return
        _rideSession.update { it.copy(selectedSport = sportType) }
    }

    fun setPowerMode(mode: PowerMode) {
        powerModePreferences.setPowerMode(mode)
    }

    fun followPlannedRoute(route: PlannedRoute) {
        _rideSession.update { state ->
            val updatedSport = if (state.status == RideStatus.IDLE) {
                route.resolvedSportType()
            } else {
                state.selectedSport
            }
            state.copy(
                selectedSport = updatedSport,
                activeGuideRoute = route,
            )
        }
    }

    fun clearActiveGuideRoute() {
        _rideSession.update { it.copy(activeGuideRoute = null) }
    }

    fun clearActiveGuideRouteIfMatches(routeId: String) {
        if (_rideSession.value.activeGuideRoute?.id == routeId) {
            clearActiveGuideRoute()
        }
    }

    fun onPermissionGranted() {
        if (!locationTracker.hasLocationPermission()) return
        scope.launch {
            val fresh = locationTracker.fetchCurrentLocation(powerMode.value)
            if (fresh != null) {
                handleNewLocation(fresh)
            }
        }
        ensureLocationUpdatesActive(restart = false)
    }

    fun onAppResumed() {
        if (!locationTracker.hasLocationPermission()) return
        val status = _rideSession.value.status
        if (status == RideStatus.RIDING) {
            syncWallClockElapsed()
        }
        scope.launch {
            val fresh = locationTracker.fetchCurrentLocation(powerMode.value)
            if (fresh != null) {
                handleNewLocation(fresh)
            }
        }
        ensureLocationUpdatesActive(restart = (status == RideStatus.IDLE))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun ensureLocationUpdatesActive(restart: Boolean = false) {
        if (!locationTracker.hasLocationPermission()) return
        if (!restart && locationJob?.isActive == true) return
        locationJob?.cancel()
        locationJob = scope.launch {
            powerMode
                .flatMapLatest { mode -> locationTracker.locationUpdates(mode) }
                .catch { e ->
                    Log.w(TAG, "Location updates stopped: ${e.localizedMessage}")
                }
                .collect { location ->
                    handleNewLocation(location)
                }
        }
    }

    fun startRide() {
        lastAcceptedLocation = null
        pendingResidualMeters = 0.0
        accumulatedElapsedMillis = 0L
        activeSegmentStartElapsedMillis = SystemClock.elapsedRealtime()

        val initialPoints = _rideSession.value.currentLocation?.let { listOf(it) } ?: emptyList()
        _rideSession.update {
            it.copy(
                status = RideStatus.RIDING,
                routePoints = initialPoints,
                distanceMeters = 0L,
                elapsedSeconds = 0L,
                currentSpeedKmh = 0.0,
                lastCompletedSummary = null,
            )
        }
        startTimer()
        ensureLocationUpdatesActive(restart = true)
        RideTrackingService.startOrUpdate(appContext)
    }

    fun pauseRide() {
        if (_rideSession.value.status != RideStatus.RIDING) return
        val now = SystemClock.elapsedRealtime()
        if (activeSegmentStartElapsedMillis > 0L) {
            accumulatedElapsedMillis += (now - activeSegmentStartElapsedMillis).coerceAtLeast(0L)
        }
        activeSegmentStartElapsedMillis = 0L
        timerJob?.cancel()
        lastAcceptedLocation = null
        _rideSession.update {
            it.copy(
                status = RideStatus.PAUSED,
                elapsedSeconds = accumulatedElapsedMillis / 1000L,
                currentSpeedKmh = 0.0,
            )
        }
        RideTrackingService.startOrUpdate(appContext)
    }

    fun resumeRide() {
        if (_rideSession.value.status != RideStatus.PAUSED) return
        lastAcceptedLocation = null
        activeSegmentStartElapsedMillis = SystemClock.elapsedRealtime()
        _rideSession.update { it.copy(status = RideStatus.RIDING) }
        startTimer()
        ensureLocationUpdatesActive(restart = true)
        RideTrackingService.startOrUpdate(appContext)
    }

    fun beginFinishRide(): RideSessionState? {
        val state = _rideSession.value
        if (state.status == RideStatus.IDLE || state.status == RideStatus.SAVING) return null

        if (state.status == RideStatus.RIDING && activeSegmentStartElapsedMillis > 0L) {
            val now = SystemClock.elapsedRealtime()
            accumulatedElapsedMillis += (now - activeSegmentStartElapsedMillis).coerceAtLeast(0L)
            activeSegmentStartElapsedMillis = 0L
        }
        timerJob?.cancel()
        lastAcceptedLocation = null

        val finalizedState = state.copy(elapsedSeconds = accumulatedElapsedMillis / 1000L)
        if (finalizedState.distanceMeters < MIN_RECORDABLE_DISTANCE_METERS) {
            accumulatedElapsedMillis = 0L
            _rideSession.update {
                RideSessionState(
                    selectedSport = it.selectedSport,
                    currentLocation = it.currentLocation,
                    activeGuideRoute = it.activeGuideRoute,
                )
            }
            RideTrackingService.stop(appContext)
            return finalizedState
        }

        _rideSession.value = finalizedState.copy(status = RideStatus.SAVING)
        RideTrackingService.stop(appContext)
        return finalizedState
    }

    fun markRideSaved(summary: RideSummary) {
        accumulatedElapsedMillis = 0L
        activeSegmentStartElapsedMillis = 0L
        _rideSession.update { current ->
            RideSessionState(
                selectedSport = current.selectedSport,
                currentLocation = current.currentLocation,
                activeGuideRoute = current.activeGuideRoute,
                lastCompletedSummary = summary,
            )
        }
    }

    fun restorePausedAfterSaveError() {
        _rideSession.update { it.copy(status = RideStatus.PAUSED) }
    }

    fun dismissSummary() {
        _rideSession.update { it.copy(lastCompletedSummary = null) }
    }

    fun stopIdleUpdates() {
        if (_rideSession.value.status == RideStatus.IDLE) {
            locationJob?.cancel()
            locationJob = null
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                syncWallClockElapsed()
            }
        }
    }

    private fun syncWallClockElapsed() {
        val current = _rideSession.value
        if (current.status != RideStatus.RIDING || activeSegmentStartElapsedMillis <= 0L) return
        val totalMillis = accumulatedElapsedMillis +
            (SystemClock.elapsedRealtime() - activeSegmentStartElapsedMillis).coerceAtLeast(0L)
        val totalSeconds = totalMillis / 1000L
        if (totalSeconds != current.elapsedSeconds) {
            _rideSession.update { state ->
                if (state.status == RideStatus.RIDING) {
                    state.copy(elapsedSeconds = totalSeconds)
                } else {
                    state
                }
            }
        }
    }

    internal fun onLocationUpdate(location: Location) {
        handleNewLocation(location)
    }

    private fun handleNewLocation(location: Location) {
        val newGeoPoint = GeoPoint(location.latitude, location.longitude)
        val currentState = _rideSession.value

        if (currentState.status != RideStatus.RIDING) {
            _rideSession.update { it.copy(currentLocation = newGeoPoint) }
            return
        }

        val currentElapsedSeconds = if (activeSegmentStartElapsedMillis > 0L) {
            (accumulatedElapsedMillis +
                (SystemClock.elapsedRealtime() - activeSegmentStartElapsedMillis).coerceAtLeast(0L)) / 1000L
        } else {
            currentState.elapsedSeconds
        }

        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            _rideSession.update {
                it.copy(
                    currentLocation = newGeoPoint,
                    elapsedSeconds = currentElapsedSeconds,
                )
            }
            return
        }

        val maxSpeed = currentState.selectedSport.maxSpeedKmh
        val previous = lastAcceptedLocation
        if (previous == null) {
            lastAcceptedLocation = location
            _rideSession.update {
                it.copy(
                    currentLocation = newGeoPoint,
                    routePoints = it.routePoints + newGeoPoint,
                    elapsedSeconds = currentElapsedSeconds,
                    currentSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6).coerceIn(0.0, maxSpeed) else 0.0,
                )
            }
            return
        }

        val deltaMeters = previous.distanceTo(location).toDouble()
        val deltaTimeSec = ((location.time - previous.time) / 1000.0).coerceAtLeast(0.5)
        val impliedSpeedKmh = (deltaMeters / deltaTimeSec) * 3.6

        if (deltaMeters < MIN_SEGMENT_METERS || impliedSpeedKmh > maxSpeed) {
            _rideSession.update {
                it.copy(
                    currentLocation = newGeoPoint,
                    elapsedSeconds = currentElapsedSeconds,
                    currentSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6).coerceIn(0.0, maxSpeed) else 0.0,
                )
            }
            return
        }

        lastAcceptedLocation = location
        pendingResidualMeters += deltaMeters
        val wholeMetersAdded = pendingResidualMeters.toLong()
        pendingResidualMeters -= wholeMetersAdded

        val speedKmh = if (location.hasSpeed() && location.speed > 0.2f) {
            (location.speed * 3.6).coerceIn(0.0, maxSpeed)
        } else {
            impliedSpeedKmh.coerceIn(0.0, maxSpeed)
        }

        _rideSession.update { state ->
            state.copy(
                currentLocation = newGeoPoint,
                routePoints = state.routePoints + newGeoPoint,
                distanceMeters = state.distanceMeters + wholeMetersAdded,
                elapsedSeconds = currentElapsedSeconds,
                currentSpeedKmh = speedKmh,
            )
        }
    }

    companion object {
        private const val TAG = "RideCoordinator"
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 40f
        const val MIN_SEGMENT_METERS = 2.0
        const val MIN_RECORDABLE_DISTANCE_METERS = 10L

        @Volatile
        private var instance: RideSessionCoordinator? = null

        fun getInstance(context: Context): RideSessionCoordinator {
            return instance ?: synchronized(this) {
                instance ?: RideSessionCoordinator(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
