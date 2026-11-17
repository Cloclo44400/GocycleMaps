package com.example.data.repository

import com.example.data.model.BikeRide
import com.example.data.model.LeaderboardCategory
import com.example.data.model.LeaderboardEntry
import com.example.data.model.SportType
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface SportDataRepository {
    suspend fun ensureUserProfile(displayNameFallback: String = "Sportif"): UserProfile

    fun observeUserProfile(userId: String): Flow<UserProfile?>

    fun observeRides(userId: String): Flow<List<BikeRide>>

    fun observeLeaderboard(
        category: LeaderboardCategory = LeaderboardCategory.ALL,
        limit: Long = 50L,
    ): Flow<List<LeaderboardEntry>>

    suspend fun recordCompletedRide(
        distanceMeters: Long,
        durationSeconds: Long,
        avgSpeedKmh: Double,
        trailCosmeticId: String,
        startLabel: String = "Balade à vélo",
        sportType: SportType = SportType.CYCLING,
    ): Long

    suspend fun updateDisplayName(newDisplayName: String)

    suspend fun deleteRide(rideId: String)
}
