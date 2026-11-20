package com.example.ui.viewmodel

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
import com.example.location.RideSessionCoordinator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val sessionCoordinator: RideSessionCoordinator,
    private val currentUserId: String,
    initialDisplayName: String,
) : ViewModel() {

    val powerMode: StateFlow<PowerMode> = sessionCoordinator.powerMode

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

    val rideSession: StateFlow<RideSessionState> = sessionCoordinator.rideSession

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
        sessionCoordinator.selectSport(sportType)
    }

    fun selectLeaderboardCategory(category: LeaderboardCategory) {
        _leaderboardCategory.value = category
    }

    fun setPowerMode(mode: PowerMode) {
        sessionCoordinator.setPowerMode(mode)
        val msg = if (mode == PowerMode.ECO) {
            "Mode Éco activé (limitation de la batterie)."
        } else {
            "Mode Standard activé (aucune restriction de batterie)."
        }
        _events.tryEmit(msg)
    }

    fun onLocationPermissionGranted() {
        sessionCoordinator.onPermissionGranted()
    }

    fun onAppResumed() {
        sessionCoordinator.onAppResumed()
    }

    fun startRide() {
        sessionCoordinator.startRide()
    }

    fun pauseRide() {
        sessionCoordinator.pauseRide()
    }

    fun resumeRide() {
        sessionCoordinator.resumeRide()
    }

    fun finishRide(equippedTrailId: String) {
        val state = sessionCoordinator.beginFinishRide() ?: return

        if (state.distanceMeters < RideSessionCoordinator.MIN_RECORDABLE_DISTANCE_METERS) {
            _events.tryEmit(
                "Parcourez au moins ${RideSessionCoordinator.MIN_RECORDABLE_DISTANCE_METERS} m pour enregistrer votre sortie."
            )
            return
        }

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
                sessionCoordinator.markRideSaved(
                    RideSummary(
                        sportType = sport,
                        distanceMeters = state.distanceMeters,
                        durationSeconds = state.elapsedSeconds,
                        pointsEarned = points,
                        avgSpeedKmh = avgSpeed,
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                sessionCoordinator.restorePausedAfterSaveError()
                _events.tryEmit("Impossible d'enregistrer la sortie : ${e.localizedMessage}")
            }
        }
    }

    fun dismissSummary() {
        sessionCoordinator.dismissSummary()
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
                sessionCoordinator.clearActiveGuideRouteIfMatches(routeId)
                _events.tryEmit("Trajet supprimé.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _events.tryEmit("Erreur lors de la suppression du trajet.")
            }
        }
    }

    fun followPlannedRoute(route: PlannedRoute) {
        sessionCoordinator.followPlannedRoute(route)
        _events.tryEmit("Tracé « ${route.name} » affiché sur la carte Sport.")
    }

    fun clearActiveGuideRoute() {
        sessionCoordinator.clearActiveGuideRoute()
    }

    override fun onCleared() {
        super.onCleared()
        sessionCoordinator.stopIdleUpdates()
    }

    private companion object {
        const val TAG = "GoCycleMapsVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
