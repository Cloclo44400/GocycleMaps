package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CosmeticDefaults
import com.example.data.model.LeaderboardCategory
import com.example.data.model.SportType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class CyclingRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_and_recordCyclingAndRunning_updatesPerSportStatsAndLeaderboards() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val uid = signInTestUser("alice_$uniqueSuffix@test.com")
        val repository = CyclingRepository(firestore, auth)

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile("Alice Sportive")
        }
        assertEquals(uid, profile.userId)
        assertEquals(0L, profile.pointsBalance)

        // 1. Cycling 2500m => 25 pts
        val cyclingEarned = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordCompletedRide(
                distanceMeters = 2500L,
                durationSeconds = 600L,
                avgSpeedKmh = 15.0,
                trailCosmeticId = CosmeticDefaults.TRAIL_EMERALD,
                startLabel = "Balade à vélo",
                sportType = SportType.CYCLING,
            )
        }
        assertEquals(25L, cyclingEarned)

        // 2. Running 400m => 4 * 25 = 100 pts
        val runningEarned = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordCompletedRide(
                distanceMeters = 400L,
                durationSeconds = 150L,
                avgSpeedKmh = 9.6,
                trailCosmeticId = CosmeticDefaults.TRAIL_EMERALD,
                startLabel = "Course à pied",
                sportType = SportType.RUNNING,
            )
        }
        assertEquals(100L, runningEarned)

        val updatedProfile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first { it != null && it.totalRides == 2L }
        }
        assertNotNull(updatedProfile)
        assertEquals(125L, updatedProfile!!.totalPointsEarned)
        assertEquals(2900L, updatedProfile.totalDistanceMeters)
        assertEquals(25L, updatedProfile.cyclingPointsEarned)
        assertEquals(2500L, updatedProfile.cyclingDistanceMeters)
        assertEquals(1L, updatedProfile.cyclingRides)
        assertEquals(100L, updatedProfile.runningPointsEarned)
        assertEquals(400L, updatedProfile.runningDistanceMeters)
        assertEquals(1L, updatedProfile.runningRides)

        val rides = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeRides(uid).first { it.size == 2 }
        }
        assertTrue(rides.any { it.resolvedSportType() == SportType.CYCLING && it.pointsEarned == 25L })
        assertTrue(rides.any { it.resolvedSportType() == SportType.RUNNING && it.pointsEarned == 100L })

        val allBoard = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLeaderboard(LeaderboardCategory.ALL).first { entries ->
                entries.any { it.userId == uid && it.totalPointsEarned == 125L }
            }
        }
        assertTrue(allBoard.any { it.userId == uid })

        val cyclingBoard = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLeaderboard(LeaderboardCategory.CYCLING).first { entries ->
                entries.any { it.userId == uid && it.cyclingPointsEarned == 25L }
            }
        }
        assertTrue(cyclingBoard.any { it.userId == uid })

        val runningBoard = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLeaderboard(LeaderboardCategory.RUNNING).first { entries ->
                entries.any { it.userId == uid && it.runningPointsEarned == 100L }
            }
        }
        assertTrue(runningBoard.any { it.userId == uid })
    }

    @Test
    fun updateDisplayName_updatesProfileAndLeaderboardAtomically() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val uid = signInTestUser("pseudo_$uniqueSuffix@test.com")
        val repository = CyclingRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile("Sportif Initial")
            repository.updateDisplayName("TurboRunner42")
        }

        val updatedProfile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first {
                it != null && it.displayName == "TurboRunner42"
            }
        }
        assertEquals("TurboRunner42", updatedProfile!!.displayName)

        val leaderboard = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLeaderboard(LeaderboardCategory.ALL).first { entries ->
                entries.any { it.userId == uid && it.displayName == "TurboRunner42" }
            }
        }
        assertTrue(leaderboard.any { it.userId == uid && it.displayName == "TurboRunner42" })
    }

    @Test
    fun observeRides_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val aliceUid = signInTestUser("alice_iso_$uniqueSuffix@test.com")
        val aliceRepo = CyclingRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice")
        }

        signInTestUser("bob_iso_$uniqueSuffix@test.com")
        val bobRepo = CyclingRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeRides(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's rides")
        } catch (e: Exception) {
            val fsException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)
        }
    }

    @Test
    fun observeRides_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val aliceUid = signInTestUser("alice_unauth_$uniqueSuffix@test.com")
        val repository = CyclingRepository(firestore, auth)

        auth.signOut()
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeRides(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)
        }
    }

    @Test
    fun observeLeaderboard_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        signInTestUser("lb_unauth_$uniqueSuffix@test.com")
        val repository = CyclingRepository(firestore, auth)

        auth.signOut()
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeLeaderboard(LeaderboardCategory.RUNNING).first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
