package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

enum class SportType(
    val id: String,
    val labelFr: String,
    val pointsPer100m: Long,
    val pointsPerKm: Long,
    val maxSpeedKmh: Double,
    val rewardRuleShort: String,
) {
    CYCLING(
        id = "cycling",
        labelFr = "Vélo",
        pointsPer100m = 1L,
        pointsPerKm = 10L,
        maxSpeedKmh = 110.0,
        rewardRuleShort = "1 km = 10 points",
    ),
    RUNNING(
        id = "running",
        labelFr = "Course à pied",
        pointsPer100m = 25L,
        pointsPerKm = 250L,
        maxSpeedKmh = 45.0,
        rewardRuleShort = "100 m = 25 points",
    );

    companion object {
        fun fromId(id: String?): SportType =
            entries.firstOrNull { it.id == id } ?: CYCLING
    }
}

enum class LeaderboardCategory(
    val labelFr: String,
    val orderByField: String,
) {
    ALL("Tout sport", "totalPointsEarned"),
    CYCLING("Vélo", "cyclingPointsEarned"),
    RUNNING("Course à pied", "runningPointsEarned"),
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Sportif",
    val pointsBalance: Long = 0L,
    val totalPointsEarned: Long = 0L,
    val totalDistanceMeters: Long = 0L,
    val totalRides: Long = 0L,
    val cyclingPointsEarned: Long = 0L,
    val cyclingDistanceMeters: Long = 0L,
    val cyclingRides: Long = 0L,
    val runningPointsEarned: Long = 0L,
    val runningDistanceMeters: Long = 0L,
    val runningRides: Long = 0L,
    val unlockedCosmetics: List<String> = CosmeticDefaults.INITIAL_UNLOCKED_IDS,
    val equippedBikeId: String = CosmeticDefaults.BIKE_MINT,
    val equippedHelmetId: String = CosmeticDefaults.HELMET_CLASSIC,
    val equippedTrailId: String = CosmeticDefaults.TRAIL_EMERALD,
    val equippedBadgeId: String = CosmeticDefaults.BADGE_FREEWHEEL,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class BikeRide(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sportType: String = SportType.CYCLING.id,
    val distanceMeters: Long = 0L,
    val durationSeconds: Long = 0L,
    val pointsEarned: Long = 0L,
    val avgSpeedKmh: Double = 0.0,
    val trailCosmeticId: String = CosmeticDefaults.TRAIL_EMERALD,
    val startLabel: String = "Balade à vélo",
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    fun resolvedSportType(): SportType = SportType.fromId(sportType)
}

@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Sportif",
    val totalPointsEarned: Long = 0L,
    val totalDistanceMeters: Long = 0L,
    val totalRides: Long = 0L,
    val cyclingPointsEarned: Long = 0L,
    val cyclingDistanceMeters: Long = 0L,
    val cyclingRides: Long = 0L,
    val runningPointsEarned: Long = 0L,
    val runningDistanceMeters: Long = 0L,
    val runningRides: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun pointsFor(category: LeaderboardCategory): Long = when (category) {
        LeaderboardCategory.ALL -> totalPointsEarned
        LeaderboardCategory.CYCLING -> cyclingPointsEarned
        LeaderboardCategory.RUNNING -> runningPointsEarned
    }

    fun distanceFor(category: LeaderboardCategory): Long = when (category) {
        LeaderboardCategory.ALL -> totalDistanceMeters
        LeaderboardCategory.CYCLING -> cyclingDistanceMeters
        LeaderboardCategory.RUNNING -> runningDistanceMeters
    }

    fun sessionsFor(category: LeaderboardCategory): Long = when (category) {
        LeaderboardCategory.ALL -> totalRides
        LeaderboardCategory.CYCLING -> cyclingRides
        LeaderboardCategory.RUNNING -> runningRides
    }
}

object CosmeticDefaults {
    const val BIKE_MINT = "bike_mint"
    const val HELMET_CLASSIC = "helmet_classic"
    const val TRAIL_EMERALD = "trail_emerald"
    const val BADGE_FREEWHEEL = "badge_freewheel"

    val INITIAL_UNLOCKED_IDS = listOf(
        BIKE_MINT,
        HELMET_CLASSIC,
        TRAIL_EMERALD,
        BADGE_FREEWHEEL,
    )
}

object CyclingPointsCalculator {
    const val POINTS_PER_KM = 10L
    const val METERS_PER_POINT = 100L
    const val RUNNING_POINTS_PER_100M = 25L

    fun calculatePointsForDistanceMeters(
        distanceMeters: Long,
        sportType: SportType = SportType.CYCLING,
    ): Long {
        if (distanceMeters <= 0L) return 0L
        val chunks = distanceMeters / METERS_PER_POINT
        return chunks * sportType.pointsPer100m
    }

    fun progressTowardNextPoint(distanceMeters: Long): Float {
        if (distanceMeters <= 0L) return 0f
        return (distanceMeters % METERS_PER_POINT) / METERS_PER_POINT.toFloat()
    }

    fun progressTowardNextKilometer(distanceMeters: Long): Float {
        if (distanceMeters <= 0L) return 0f
        return (distanceMeters % 1000L) / 1000f
    }

    fun progressTowardNextReward(distanceMeters: Long, sportType: SportType): Float = when (sportType) {
        SportType.CYCLING -> progressTowardNextKilometer(distanceMeters)
        SportType.RUNNING -> progressTowardNextPoint(distanceMeters)
    }
}

data class RoutePointDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

@IgnoreExtraProperties
data class PlannedRoute(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "Mon trajet",
    val sportType: String = SportType.CYCLING.id,
    val distanceMeters: Long = 0L,
    val estimatedPoints: Long = 0L,
    val waypointCount: Long = 0L,
    val waypointsJson: String = "[]",
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    fun resolvedSportType(): SportType = SportType.fromId(sportType)

    fun decodeWaypoints(): List<RoutePointDto> = RouteGeometryHelper.decodeWaypointsJson(waypointsJson)
}

object RouteGeometryHelper {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun calculateDistanceMeters(points: List<RoutePointDto>): Long {
        if (points.size < 2) return 0L
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(points[i - 1], points[i])
        }
        return total.roundToLong()
    }

    fun haversineMeters(a: RoutePointDto, b: RoutePointDto): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val sinLat = sin(dLat / 2.0)
        val sinLon = sin(dLon / 2.0)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        val c = 2.0 * atan2(sqrt(h), sqrt((1.0 - h).coerceAtLeast(0.0)))
        return EARTH_RADIUS_METERS * c
    }

    private val PAIR_REGEX = Regex(
        """\[\s*(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\s*,\s*(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\s*]"""
    )

    fun encodeWaypointsJson(points: List<RoutePointDto>): String =
        points.joinToString(prefix = "[", postfix = "]", separator = ",") {
            "[${it.latitude},${it.longitude}]"
        }

    fun decodeWaypointsJson(raw: String): List<RoutePointDto> {
        if (raw.isBlank()) return emptyList()
        return PAIR_REGEX.findAll(raw).mapNotNull { match ->
            val lat = match.groupValues[1].toDoubleOrNull()
            val lon = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null) RoutePointDto(lat, lon) else null
        }.toList()
    }
}
