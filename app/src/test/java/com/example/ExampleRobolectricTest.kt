package com.example

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.LeaderboardCategory
import com.example.data.model.RoutePointDto
import com.example.data.model.SportType
import com.example.data.repository.LocalGuestRepository
import com.example.location.PowerMode
import com.example.location.PowerModePreferences
import com.example.location.RideSessionCoordinator
import com.example.location.RideTrackingService
import com.example.ui.viewmodel.RideStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GoCycleMaps", appName)
  }

  @Test
  fun `power mode preferences persists eco and standard selection`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val firstInstance = PowerModePreferences(context)
    assertEquals(PowerMode.STANDARD, firstInstance.powerMode.value)

    firstInstance.setPowerMode(PowerMode.ECO)
    assertEquals(PowerMode.ECO, firstInstance.powerMode.value)

    val reloadedInstance = PowerModePreferences(context)
    assertEquals(PowerMode.ECO, reloadedInstance.powerMode.value)

    reloadedInstance.setPowerMode(PowerMode.STANDARD)
    assertEquals(PowerMode.STANDARD, PowerModePreferences(context).powerMode.value)
  }

  @Test
  fun `ride session coordinator tracks locations across pause resume and notification channel`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    RideTrackingService.ensureNotificationChannel(context)

    val coordinator = RideSessionCoordinator(context)
    coordinator.selectSport(SportType.RUNNING)
    coordinator.startRide()
    assertEquals(RideStatus.RIDING, coordinator.rideSession.value.status)

    val p1 = Location("gps").apply {
      latitude = 48.8566
      longitude = 2.3522
      accuracy = 5f
      time = 1000L
    }
    val p2 = Location("gps").apply {
      latitude = 48.8576
      longitude = 2.3522
      accuracy = 5f
      time = 21000L
    }
    coordinator.onLocationUpdate(p1)
    coordinator.onLocationUpdate(p2)

    val ridingState = coordinator.rideSession.value
    assertTrue("Expected distance >= 100m, got ${ridingState.distanceMeters}", ridingState.distanceMeters >= 100L)
    assertEquals(2, ridingState.routePoints.size)
    assertTrue(ridingState.livePointsEarned >= 25L)

    coordinator.pauseRide()
    assertEquals(RideStatus.PAUSED, coordinator.rideSession.value.status)

    coordinator.resumeRide()
    assertEquals(RideStatus.RIDING, coordinator.rideSession.value.status)

    val finished = coordinator.beginFinishRide()
    assertTrue(finished != null && finished.distanceMeters >= 100L)
    assertEquals(RideStatus.SAVING, coordinator.rideSession.value.status)
  }

  @Test
  fun `local guest repository tracks cycling and running locally`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = LocalGuestRepository(context)
    val initial = repo.ensureUserProfile("Invité")
    assertEquals("Invité", initial.displayName)

    repo.updateDisplayName("Runner44")
    val runningPts = repo.recordCompletedRide(
      distanceMeters = 500L,
      durationSeconds = 180L,
      avgSpeedKmh = 10.0,
      trailCosmeticId = "trail_emerald",
      startLabel = "Course à pied",
      sportType = SportType.RUNNING,
      routeWaypoints = listOf(
        RoutePointDto(48.8566, 2.3522),
        RoutePointDto(48.8580, 2.3530),
      ),
    )
    assertEquals(125L, runningPts)

    val savedRides = repo.observeRides(LocalGuestRepository.GUEST_USER_ID).first()
    assertEquals(1, savedRides.size)
    assertTrue(savedRides.first().hasRecordedRoute())
    assertEquals(2, savedRides.first().decodeWaypoints().size)

    val cyclingPts = repo.recordCompletedRide(
      distanceMeters = 2000L,
      durationSeconds = 400L,
      avgSpeedKmh = 18.0,
      trailCosmeticId = "trail_emerald",
      startLabel = "Balade à vélo",
      sportType = SportType.CYCLING,
    )
    assertEquals(20L, cyclingPts)

    val profile = repo.observeUserProfile(LocalGuestRepository.GUEST_USER_ID).first()!!
    assertEquals("Runner44", profile.displayName)
    assertEquals(145L, profile.totalPointsEarned)
    assertEquals(125L, profile.runningPointsEarned)
    assertEquals(20L, profile.cyclingPointsEarned)

    val runLeaderboard = repo.observeLeaderboard(LeaderboardCategory.RUNNING).first()
    assertEquals(1, runLeaderboard.size)
    assertEquals(125L, runLeaderboard.first().runningPointsEarned)

    val routeId = repo.savePlannedRoute(
      name = "Boucle Parc",
      sportType = SportType.RUNNING,
      distanceMeters = 1200L,
      waypoints = listOf(
        RoutePointDto(47.21, -1.55),
        RoutePointDto(47.22, -1.55),
      ),
    )
    val routes = repo.observePlannedRoutes(LocalGuestRepository.GUEST_USER_ID).first()
    assertEquals(1, routes.size)
    assertEquals("Boucle Parc", routes.first().name)
    assertEquals(300L, routes.first().estimatedPoints)

    repo.deletePlannedRoute(routeId)
    val remaining = repo.observePlannedRoutes(LocalGuestRepository.GUEST_USER_ID).first()
    assertEquals(0, remaining.size)
  }
}
