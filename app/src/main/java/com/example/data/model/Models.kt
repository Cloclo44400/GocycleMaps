package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

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
