package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.LeaderboardCategory
import com.example.data.model.RoutePointDto
import com.example.data.model.SportType
import com.example.data.repository.LocalGuestRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    )
    assertEquals(125L, runningPts)

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
