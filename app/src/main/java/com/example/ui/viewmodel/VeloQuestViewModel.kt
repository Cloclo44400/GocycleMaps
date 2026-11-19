package com.example.ui.viewmodel

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BikeRide
import com.example.data.model.CyclingPointsCalculator
import com.example.data.model.LeaderboardCategory
import com.example.data.model.LeaderboardEntry
import com.example.data.model.PlannedRoute
import com.example.data.model.RouteGeometryHelper
import com.example.data.model.RoutePointDto
import com.example.data.model.SportType
import com.example.data.model.UserProfile
import com.example.data.repository.SportDataRepository
import com.example.location.PowerMode
import com.example.location.PowerModePreferences
import com.example.location.RideLocationTracker
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class RideStatus {
    IDLE,
    RIDING,
    PAUSED,
    SAVING,
}

data class RideSummary(
    val sportType: SportType,
    val distanceMeters: Long,
    val durationSeconds: Long,
    val pointsEarned: Long,
    val avgSpeedKmh: Double,
)

data class RideSessionState(
    val selectedSport: SportType = SportType.CYCLING,
    val status: RideStatus = RideStatus.IDLE,
    val routePoints: List<GeoPoint> = emptyList(),
    val currentLocation: GeoPoint? = null,
    val distanceMeters: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val currentSpeedKmh: Double = 0.0,
    val lastCompletedSummary: RideSummary? = null,
    val activeGuideRoute: PlannedRoute? = null,
) {
    val livePointsEarned: Long
        get() = CyclingPointsCalculator.calculatePointsForDistanceMeters(distanceMeters, selectedSport)

    val avgSpeedKmh: Double
        get() = if (elapsedSeconds > 0L) {
            (distanceMeters / 1000.0) / (elapsedSeconds / 3600.0)
        } else {
            0.0
        }
}

data class RouteEditorState(
    val sportType: SportType = SportType.CYCLING,
    val waypoints: List<GeoPoint> = emptyList(),
    val isSaving: Boolean = false,
) {
    val waypointDtos: List<RoutePointDto>
        get() = waypoints.map { RoutePointDto(it.latitude, it.longitude) }

    val distanceMeters: Long
        get() = RouteGeometryHelper.calculateDistanceMeters(waypointDtos)

    val estimatedPoints: Long
        get() = CyclingPointsCalculator.calculatePointsForDistanceMeters(distanceMeters, sportType)

    val estimatedDurationMinutes: Long
        get() {
            if (distanceMeters <= 0L) return 0L
            val avgSpeedKmh = if (sportType == SportType.RUNNING) 10.0 else 18.0
            val hours = (distanceMeters / 1000.0) / avgSpeedKmh
            return (hours * 60.0).toLong().coerceAtLeast(1L)
        }
}

class VeloQuestViewModel(
    private val repository: SportDataRepository,
    private val locationTracker: RideLocationTracker,
    private val currentUserId: String,
    initialDisplayName: String,
    private val powerModePreferences: PowerModePreferences? = null,
) : ViewModel() {

    private val _fallbackPowerMode = MutableStateFlow(PowerMode.STANDARD)
    val powerMode: StateFlow<PowerMode> = powerModePreferences?.powerMode ?: _fallbackPowerMode.asStateFlow()

    val profileState: StateFlow<UiState<UserProfile>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile>> { profile ->
            if (profile != null) {
                UiState.Success(profile)
            } else {
                UiState.Loading
            }
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Impossible de charger le profil."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val ridesState: StateFlow<UiState<List<BikeRide>>> = repository.observeRides(currentUserId)
        .map<List<BikeRide>, UiState<List<BikeRide>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing rides", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Impossible de charger les activités."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _leaderboardCategory = MutableStateFlow(LeaderboardCategory.ALL)
    val leaderboardCategory: StateFlow<LeaderboardCategory> = _leaderboardCategory.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val leaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> = _leaderboardCategory
        .flatMapLatest { category ->
            repository.observeLeaderboard(category)
                .map<List<LeaderboardEntry>, UiState<List<LeaderboardEntry>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing leaderboard", rootError)
                    emit(UiState.Error(rootError.localizedMessage ?: "Impossible de charger le classement."))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _rideSession = MutableStateFlow(RideSessionState())
    val rideSession: StateFlow<RideSessionState> = _rideSession.asStateFlow()

    private val _editorState = MutableStateFlow(RouteEditorState())
    val editorState: StateFlow<RouteEditorState> = _editorState.asStateFlow()

    val plannedRoutesState: StateFlow<UiState<List<PlannedRoute>>> = repository.observePlannedRoutes(currentUserId)
        .map<List<PlannedRoute>, UiState<List<PlannedRoute>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing planned routes", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Impossible de charger vos trajets."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var timerJob: Job? = null
    private var locationJob: Job? = null
    private var lastAcceptedLocation: Location? = null
    private var pendingResidualMeters: Double = 0.0

    init {
        viewModelScope.launch {
            try {
                repository.ensureUserProfile(initialDisplayName)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to initialize profile", e)
                _events.tryEmit("Erreur d'initialisation du profil : ${e.localizedMessage}")
            }
        }
    }

    fun selectSport(sportType: SportType) {
        if (_rideSession.value.status != RideStatus.IDLE) return
        _rideSession.update { it.copy(selectedSport = sportType) }
    }

    fun selectLeaderboardCategory(category: LeaderboardCategory) {
        _leaderboardCategory.value = category
    }

    fun setPowerMode(mode: PowerMode) {
        if (powerModePreferences != null) {
            powerModePreferences.setPowerMode(mode)
        } else {
            _fallbackPowerMode.value = mode
        }
        val msg = if (mode == PowerMode.ECO) {
            "Mode Éco activé (limitation de la batterie)."
        } else {
            "Mode Standard activé (aucune restriction de batterie)."
        }
        _events.tryEmit(msg)
    }

    fun onLocationPermissionGranted() {
        viewModelScope.launch {
            val lastLoc = locationTracker.getLastKnownLocation()
            if (lastLoc != null && _rideSession.value.currentLocation == null) {
                _rideSession.update {
                    it.copy(currentLocation = GeoPoint(lastLoc.latitude, lastLoc.longitude))
                }
            }
        }
        startLocationUpdates()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startLocationUpdates() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
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

    private fun handleNewLocation(location: Location) {
        val newGeoPoint = GeoPoint(location.latitude, location.longitude)
        val currentState = _rideSession.value

        if (currentState.status != RideStatus.RIDING) {
            _rideSession.update { it.copy(currentLocation = newGeoPoint) }
            return
        }

        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            _rideSession.update { it.copy(currentLocation = newGeoPoint) }
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
                currentSpeedKmh = speedKmh,
            )
        }
    }

    fun startRide() {
        lastAcceptedLocation = null
        pendingResidualMeters = 0.0
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
        startLocationUpdates()
    }

    fun pauseRide() {
        if (_rideSession.value.status != RideStatus.RIDING) return
        timerJob?.cancel()
        lastAcceptedLocation = null
        _rideSession.update { it.copy(status = RideStatus.PAUSED, currentSpeedKmh = 0.0) }
    }

    fun resumeRide() {
        if (_rideSession.value.status != RideStatus.PAUSED) return
        lastAcceptedLocation = null
        _rideSession.update { it.copy(status = RideStatus.RIDING) }
        startTimer()
    }

    fun finishRide(equippedTrailId: String) {
        val state = _rideSession.value
        if (state.status == RideStatus.IDLE || state.status == RideStatus.SAVING) return
        timerJob?.cancel()
        lastAcceptedLocation = null

        if (state.distanceMeters < MIN_RECORDABLE_DISTANCE_METERS) {
            _rideSession.update {
                RideSessionState(
                    selectedSport = it.selectedSport,
                    currentLocation = it.currentLocation,
                )
            }
            _events.tryEmit("Parcourez au moins $MIN_RECORDABLE_DISTANCE_METERS m pour enregistrer votre sortie.")
            return
        }

        _rideSession.update { it.copy(status = RideStatus.SAVING) }
        viewModelScope.launch {
            try {
                val avgSpeed = state.avgSpeedKmh
                val sport = state.selectedSport
                val defaultLabel = if (sport == SportType.RUNNING) "Course à pied" else "Balade à vélo"
                val points = repository.recordCompletedRide(
                    distanceMeters = state.distanceMeters,
                    durationSeconds = state.elapsedSeconds,
                    avgSpeedKmh = avgSpeed,
                    trailCosmeticId = equippedTrailId,
                    startLabel = defaultLabel,
                    sportType = sport,
                )
                _rideSession.update { current ->
                    RideSessionState(
                        selectedSport = current.selectedSport,
                        currentLocation = current.currentLocation,
                        lastCompletedSummary = RideSummary(
                            sportType = sport,
                            distanceMeters = state.distanceMeters,
                            durationSeconds = state.elapsedSeconds,
                            pointsEarned = points,
                            avgSpeedKmh = avgSpeed,
                        ),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _rideSession.update { it.copy(status = RideStatus.PAUSED) }
                _events.tryEmit("Impossible d'enregistrer la sortie : ${e.localizedMessage}")
            }
        }
    }

    fun dismissSummary() {
        _rideSession.update { it.copy(lastCompletedSummary = null) }
    }

    fun updateDisplayName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.length !in 2..30) {
            _events.tryEmit("Le pseudo doit contenir entre 2 et 30 caractères.")
            return
        }
        viewModelScope.launch {
            try {
                repository.updateDisplayName(trimmed)
                _events.tryEmit("Pseudo mis à jour : $trimmed")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _events.tryEmit("Impossible de modifier le pseudo : ${e.localizedMessage}")
            }
        }
    }

    fun deleteRide(rideId: String) {
        viewModelScope.launch {
            try {
                repository.deleteRide(rideId)
                _events.tryEmit("Activité supprimée.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _events.tryEmit("Erreur lors de la suppression.")
            }
        }
    }

    fun selectEditorSport(sportType: SportType) {
        _editorState.update { it.copy(sportType = sportType) }
    }

    fun addEditorWaypoint(point: GeoPoint) {
        if (_editorState.value.waypoints.size >= 500) {
            _events.tryEmit("Limite de 500 repères atteinte pour ce trajet.")
            return
        }
        _editorState.update { it.copy(waypoints = it.waypoints + point) }
    }

    fun undoEditorWaypoint() {
        _editorState.update { state ->
            if (state.waypoints.isEmpty()) state else state.copy(waypoints = state.waypoints.dropLast(1))
        }
    }

    fun closeEditorLoop() {
        val current = _editorState.value.waypoints
        if (current.size < 2) {
            _events.tryEmit("Placez au moins 2 points avant de boucler le trajet.")
            return
        }
        val first = current.first()
        val last = current.last()
        if (first.latitude == last.latitude && first.longitude == last.longitude) return
        _editorState.update { it.copy(waypoints = it.waypoints + first) }
    }

    fun clearEditorRoute() {
        _editorState.update { it.copy(waypoints = emptyList()) }
    }

    fun loadRouteIntoEditor(route: PlannedRoute) {
        val loaded = route.decodeWaypoints().map { GeoPoint(it.latitude, it.longitude) }
        _editorState.value = RouteEditorState(
            sportType = route.resolvedSportType(),
            waypoints = loaded,
            isSaving = false,
        )
        _events.tryEmit("Trajet « ${route.name} » chargé dans l'éditeur.")
    }

    fun saveEditorRoute(routeName: String) {
        val current = _editorState.value
        if (current.waypoints.size < 2) {
            _events.tryEmit("Placez au moins 2 points sur la carte pour enregistrer un trajet.")
            return
        }
        if (current.isSaving) return
        val dtos = current.waypointDtos
        val distanceMeters = current.distanceMeters.coerceAtLeast(1L)
        _editorState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.savePlannedRoute(
                    name = routeName,
                    sportType = current.sportType,
                    distanceMeters = distanceMeters,
                    waypoints = dtos,
                )
                _editorState.update { it.copy(isSaving = false) }
                _events.tryEmit("Trajet enregistré !")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _editorState.update { it.copy(isSaving = false) }
                _events.tryEmit("Impossible d'enregistrer le trajet : ${e.localizedMessage}")
            }
        }
    }

    fun deletePlannedRoute(routeId: String) {
        viewModelScope.launch {
            try {
                repository.deletePlannedRoute(routeId)
                if (_rideSession.value.activeGuideRoute?.id == routeId) {
                    clearActiveGuideRoute()
                }
                _events.tryEmit("Trajet supprimé.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _events.tryEmit("Erreur lors de la suppression du trajet.")
            }
        }
    }

    fun followPlannedRoute(route: PlannedRoute) {
        _rideSession.update { state ->
            val updatedSport = if (state.status == RideStatus.IDLE) route.resolvedSportType() else state.selectedSport
            state.copy(
                selectedSport = updatedSport,
                activeGuideRoute = route,
            )
        }
        _events.tryEmit("Tracé « ${route.name} » affiché sur la carte Sport.")
    }

    fun clearActiveGuideRoute() {
        _rideSession.update { it.copy(activeGuideRoute = null) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _rideSession.update { state ->
                    if (state.status == RideStatus.RIDING) {
                        state.copy(elapsedSeconds = state.elapsedSeconds + 1L)
                    } else {
                        state
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        locationJob?.cancel()
    }

    private companion object {
        const val TAG = "GoCycleMapsVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 40f
        const val MIN_SEGMENT_METERS = 2.0
        const val MIN_RECORDABLE_DISTANCE_METERS = 10L
    }
}
