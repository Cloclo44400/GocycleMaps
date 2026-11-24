package com.example.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.data.model.BikeRide
import com.example.data.model.CosmeticDefaults
import com.example.data.model.CyclingPointsCalculator
import com.example.data.model.LeaderboardCategory
import com.example.data.model.LeaderboardEntry
import com.example.data.model.PlannedRoute
import com.example.data.model.RouteGeometryHelper
import com.example.data.model.RoutePointDto
import com.example.data.model.SportType
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

class LocalGuestRepository(context: Context) : SportDataRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val profileFlow = MutableStateFlow(readProfile())
    private val ridesFlow = MutableStateFlow(readRides())
    private val plannedRoutesFlow = MutableStateFlow(readPlannedRoutes())

    override suspend fun ensureUserProfile(displayNameFallback: String): UserProfile {
        val current = readProfile()
        val resolvedName = if (current.displayName.isBlank()) {
            displayNameFallback.trim().take(30).ifEmpty { "Invité" }
        } else {
            current.displayName
        }
        val normalized = current.copy(
            id = GUEST_USER_ID,
            userId = GUEST_USER_ID,
            displayName = resolvedName,
        )
        saveProfile(normalized)
        return normalized
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> = profileFlow

    override fun observeRides(userId: String): Flow<List<BikeRide>> = ridesFlow

    override fun observeLeaderboard(
        category: LeaderboardCategory,
        limit: Long,
    ): Flow<List<LeaderboardEntry>> = profileFlow.map { profile ->
        val entry = LeaderboardEntry(
            id = GUEST_USER_ID,
            userId = GUEST_USER_ID,
            displayName = profile.displayName.ifBlank { "Invité" },
            totalPointsEarned = profile.totalPointsEarned,
            totalDistanceMeters = profile.totalDistanceMeters,
            totalRides = profile.totalRides,
            cyclingPointsEarned = profile.cyclingPointsEarned,
            cyclingDistanceMeters = profile.cyclingDistanceMeters,
            cyclingRides = profile.cyclingRides,
            runningPointsEarned = profile.runningPointsEarned,
            runningDistanceMeters = profile.runningDistanceMeters,
            runningRides = profile.runningRides,
        )
        when (category) {
            LeaderboardCategory.ALL -> listOf(entry)
            LeaderboardCategory.CYCLING -> if (entry.cyclingRides > 0L || entry.cyclingPointsEarned > 0L || entry.cyclingDistanceMeters > 0L) {
                listOf(entry)
            } else {
                emptyList()
            }
            LeaderboardCategory.RUNNING -> if (entry.runningRides > 0L || entry.runningPointsEarned > 0L || entry.runningDistanceMeters > 0L) {
                listOf(entry)
            } else {
                emptyList()
            }
        }
    }

    override suspend fun recordCompletedRide(
        distanceMeters: Long,
        durationSeconds: Long,
        avgSpeedKmh: Double,
        trailCosmeticId: String,
        startLabel: String,
        sportType: SportType,
        routeWaypoints: List<RoutePointDto>,
    ): Long {
        require(distanceMeters > 0L) { "La distance doit être supérieure à 0 m." }
        val pointsEarned = CyclingPointsCalculator.calculatePointsForDistanceMeters(distanceMeters, sportType)
        val current = profileFlow.value

        val updatedProfile = current.copy(
            pointsBalance = current.pointsBalance + pointsEarned,
            totalPointsEarned = current.totalPointsEarned + pointsEarned,
            totalDistanceMeters = current.totalDistanceMeters + distanceMeters,
            totalRides = current.totalRides + 1L,
            cyclingPointsEarned = if (sportType == SportType.CYCLING) {
                current.cyclingPointsEarned + pointsEarned
            } else {
                current.cyclingPointsEarned
            },
            cyclingDistanceMeters = if (sportType == SportType.CYCLING) {
                current.cyclingDistanceMeters + distanceMeters
            } else {
                current.cyclingDistanceMeters
            },
            cyclingRides = if (sportType == SportType.CYCLING) {
                current.cyclingRides + 1L
            } else {
                current.cyclingRides
            },
            runningPointsEarned = if (sportType == SportType.RUNNING) {
                current.runningPointsEarned + pointsEarned
            } else {
                current.runningPointsEarned
            },
            runningDistanceMeters = if (sportType == SportType.RUNNING) {
                current.runningDistanceMeters + distanceMeters
            } else {
                current.runningDistanceMeters
            },
            runningRides = if (sportType == SportType.RUNNING) {
                current.runningRides + 1L
            } else {
                current.runningRides
            },
        )
        saveProfile(updatedProfile)

        val defaultLabel = if (sportType == SportType.RUNNING) "Course à pied" else "Balade à vélo"
        val compactedWaypoints = RouteGeometryHelper.downsampleWaypoints(routeWaypoints)
        val newRide = BikeRide(
            id = UUID.randomUUID().toString(),
            userId = GUEST_USER_ID,
            sportType = sportType.id,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds.coerceIn(0L, 86400L),
            pointsEarned = pointsEarned,
            avgSpeedKmh = avgSpeedKmh.coerceIn(0.0, 150.0),
            trailCosmeticId = trailCosmeticId,
            startLabel = startLabel.trim().take(100).ifEmpty { defaultLabel },
            waypointsJson = RouteGeometryHelper.encodeWaypointsJson(compactedWaypoints),
            createdAt = Timestamp.now(),
        )
        val updatedRides = listOf(newRide) + ridesFlow.value
        saveRides(updatedRides)
        return pointsEarned
    }

    override suspend fun updateDisplayName(newDisplayName: String) {
        val cleanName = newDisplayName.trim()
        require(cleanName.length in 2..30) {
            "Le pseudo doit contenir entre 2 et 30 caractères."
        }
        saveProfile(profileFlow.value.copy(displayName = cleanName))
    }

    override suspend fun deleteRide(rideId: String) {
        saveRides(ridesFlow.value.filterNot { it.id == rideId })
    }

    override fun observePlannedRoutes(userId: String): Flow<List<PlannedRoute>> = plannedRoutesFlow

    override suspend fun savePlannedRoute(
        name: String,
        sportType: SportType,
        distanceMeters: Long,
        waypoints: List<RoutePointDto>,
    ): String {
        require(waypoints.size in 2..500) { "Placez entre 2 et 500 points sur la carte." }
        val resolvedDistance = distanceMeters.coerceIn(1L, 1_000_000L)
        val estimatedPoints = CyclingPointsCalculator.calculatePointsForDistanceMeters(resolvedDistance, sportType)
        val cleanName = name.trim().take(80).ifEmpty {
            if (sportType == SportType.RUNNING) "Parcours course" else "Itinéraire vélo"
        }
        val routeId = UUID.randomUUID().toString()
        val route = PlannedRoute(
            id = routeId,
            userId = GUEST_USER_ID,
            name = cleanName,
            sportType = sportType.id,
            distanceMeters = resolvedDistance,
            estimatedPoints = estimatedPoints,
            waypointCount = waypoints.size.toLong(),
            waypointsJson = RouteGeometryHelper.encodeWaypointsJson(waypoints),
            createdAt = Timestamp.now(),
        )
        savePlannedRoutes(listOf(route) + plannedRoutesFlow.value)
        return routeId
    }

    override suspend fun deletePlannedRoute(routeId: String) {
        savePlannedRoutes(plannedRoutesFlow.value.filterNot { it.id == routeId })
    }

    private fun readProfile(): UserProfile {
        val raw = prefs.getString(KEY_PROFILE, null)
        if (raw.isNullOrBlank()) {
            return UserProfile(
                id = GUEST_USER_ID,
                userId = GUEST_USER_ID,
                displayName = "Invité",
                unlockedCosmetics = CosmeticDefaults.INITIAL_UNLOCKED_IDS,
                equippedBikeId = CosmeticDefaults.BIKE_MINT,
                equippedHelmetId = CosmeticDefaults.HELMET_CLASSIC,
                equippedTrailId = CosmeticDefaults.TRAIL_EMERALD,
                equippedBadgeId = CosmeticDefaults.BADGE_FREEWHEEL,
            )
        }
        return runCatching {
            val json = JSONObject(raw)
            UserProfile(
                id = GUEST_USER_ID,
                userId = GUEST_USER_ID,
                displayName = json.optString("displayName", "Invité").ifBlank { "Invité" },
                pointsBalance = json.optLong("pointsBalance", 0L),
                totalPointsEarned = json.optLong("totalPointsEarned", 0L),
                totalDistanceMeters = json.optLong("totalDistanceMeters", 0L),
                totalRides = json.optLong("totalRides", 0L),
                cyclingPointsEarned = json.optLong("cyclingPointsEarned", 0L),
                cyclingDistanceMeters = json.optLong("cyclingDistanceMeters", 0L),
                cyclingRides = json.optLong("cyclingRides", 0L),
                runningPointsEarned = json.optLong("runningPointsEarned", 0L),
                runningDistanceMeters = json.optLong("runningDistanceMeters", 0L),
                runningRides = json.optLong("runningRides", 0L),
                unlockedCosmetics = CosmeticDefaults.INITIAL_UNLOCKED_IDS,
                equippedBikeId = CosmeticDefaults.BIKE_MINT,
                equippedHelmetId = CosmeticDefaults.HELMET_CLASSIC,
                equippedTrailId = CosmeticDefaults.TRAIL_EMERALD,
                equippedBadgeId = CosmeticDefaults.BADGE_FREEWHEEL,
            )
        }.getOrElse {
            UserProfile(id = GUEST_USER_ID, userId = GUEST_USER_ID, displayName = "Invité")
        }
    }

    private fun saveProfile(profile: UserProfile) {
        val json = JSONObject().apply {
            put("displayName", profile.displayName)
            put("pointsBalance", profile.pointsBalance)
            put("totalPointsEarned", profile.totalPointsEarned)
            put("totalDistanceMeters", profile.totalDistanceMeters)
            put("totalRides", profile.totalRides)
            put("cyclingPointsEarned", profile.cyclingPointsEarned)
            put("cyclingDistanceMeters", profile.cyclingDistanceMeters)
            put("cyclingRides", profile.cyclingRides)
            put("runningPointsEarned", profile.runningPointsEarned)
            put("runningDistanceMeters", profile.runningDistanceMeters)
            put("runningRides", profile.runningRides)
        }
        prefs.edit { putString(KEY_PROFILE, json.toString()) }
        profileFlow.value = profile
    }

    private fun readRides(): List<BikeRide> {
        val raw = prefs.getString(KEY_RIDES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val createdMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                    add(
                        BikeRide(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            userId = GUEST_USER_ID,
                            sportType = obj.optString("sportType", SportType.CYCLING.id),
                            distanceMeters = obj.optLong("distanceMeters", 0L),
                            durationSeconds = obj.optLong("durationSeconds", 0L),
                            pointsEarned = obj.optLong("pointsEarned", 0L),
                            avgSpeedKmh = obj.optDouble("avgSpeedKmh", 0.0),
                            trailCosmeticId = obj.optString("trailCosmeticId", CosmeticDefaults.TRAIL_EMERALD),
                            startLabel = obj.optString("startLabel", "Balade à vélo"),
                            waypointsJson = obj.optString("waypointsJson", "[]"),
                            createdAt = Timestamp(Date(createdMillis)),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRides(rides: List<BikeRide>) {
        val array = JSONArray()
        rides.forEach { ride ->
            array.put(
                JSONObject().apply {
                    put("id", ride.id)
                    put("sportType", ride.sportType)
                    put("distanceMeters", ride.distanceMeters)
                    put("durationSeconds", ride.durationSeconds)
                    put("pointsEarned", ride.pointsEarned)
                    put("avgSpeedKmh", ride.avgSpeedKmh)
                    put("trailCosmeticId", ride.trailCosmeticId)
                    put("startLabel", ride.startLabel)
                    put("waypointsJson", ride.waypointsJson)
                    put("createdAtMillis", ride.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                }
            )
        }
        prefs.edit { putString(KEY_RIDES, array.toString()) }
        ridesFlow.value = rides
    }

    private fun readPlannedRoutes(): List<PlannedRoute> {
        val raw = prefs.getString(KEY_ROUTES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val createdMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                    add(
                        PlannedRoute(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            userId = GUEST_USER_ID,
                            name = obj.optString("name", "Mon trajet"),
                            sportType = obj.optString("sportType", SportType.CYCLING.id),
                            distanceMeters = obj.optLong("distanceMeters", 0L),
                            estimatedPoints = obj.optLong("estimatedPoints", 0L),
                            waypointCount = obj.optLong("waypointCount", 0L),
                            waypointsJson = obj.optString("waypointsJson", "[]"),
                            createdAt = Timestamp(Date(createdMillis)),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun savePlannedRoutes(routes: List<PlannedRoute>) {
        val array = JSONArray()
        routes.forEach { route ->
            array.put(
                JSONObject().apply {
                    put("id", route.id)
                    put("name", route.name)
                    put("sportType", route.sportType)
                    put("distanceMeters", route.distanceMeters)
                    put("estimatedPoints", route.estimatedPoints)
                    put("waypointCount", route.waypointCount)
                    put("waypointsJson", route.waypointsJson)
                    put("createdAtMillis", route.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                }
            )
        }
        prefs.edit { putString(KEY_ROUTES, array.toString()) }
        plannedRoutesFlow.value = routes
    }

    companion object {
        const val GUEST_USER_ID = "guest_local"
        private const val PREFS_NAME = "gocyclemaps_guest_prefs"
        private const val KEY_PROFILE = "guest_profile_json"
        private const val KEY_RIDES = "guest_rides_json"
        private const val KEY_ROUTES = "guest_routes_json"
    }
}
