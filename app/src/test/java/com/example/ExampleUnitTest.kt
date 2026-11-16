package com.example

import com.example.data.model.CosmeticCatalog
import com.example.data.model.CyclingPointsCalculator
import com.example.data.model.SportType
import com.example.update.UpdateSettings
import com.example.update.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun cyclingPointsCalculator_awardsTenPointsPerKilometer() {
        assertEquals(0L, CyclingPointsCalculator.calculatePointsForDistanceMeters(0L, SportType.CYCLING))
        assertEquals(0L, CyclingPointsCalculator.calculatePointsForDistanceMeters(99L, SportType.CYCLING))
        assertEquals(1L, CyclingPointsCalculator.calculatePointsForDistanceMeters(100L, SportType.CYCLING))
        assertEquals(10L, CyclingPointsCalculator.calculatePointsForDistanceMeters(1000L, SportType.CYCLING))
        assertEquals(25L, CyclingPointsCalculator.calculatePointsForDistanceMeters(2550L, SportType.CYCLING))
        assertEquals(0.5f, CyclingPointsCalculator.progressTowardNextKilometer(1500L), 0.001f)
    }

    @Test
    fun runningPointsCalculator_awards25PointsPer100Meters() {
        assertEquals(0L, CyclingPointsCalculator.calculatePointsForDistanceMeters(0L, SportType.RUNNING))
        assertEquals(0L, CyclingPointsCalculator.calculatePointsForDistanceMeters(99L, SportType.RUNNING))
        assertEquals(25L, CyclingPointsCalculator.calculatePointsForDistanceMeters(100L, SportType.RUNNING))
        assertEquals(50L, CyclingPointsCalculator.calculatePointsForDistanceMeters(250L, SportType.RUNNING))
        assertEquals(250L, CyclingPointsCalculator.calculatePointsForDistanceMeters(1000L, SportType.RUNNING))
        assertEquals(0.5f, CyclingPointsCalculator.progressTowardNextReward(150L, SportType.RUNNING), 0.001f)
    }

    @Test
    fun versionComparator_detectsNewerGitHubReleases() {
        assertTrue(VersionComparator.isRemoteNewer("1.0", "v1.0.1"))
        assertTrue(VersionComparator.isRemoteNewer("1.0", "1.1.0"))
        assertTrue(VersionComparator.isRemoteNewer("1.2.3", "v2.0.0"))
        assertFalse(VersionComparator.isRemoteNewer("1.0", "v1.0.0"))
        assertFalse(VersionComparator.isRemoteNewer("1.2.0", "v1.1.9"))
    }

    @Test
    fun updateSettings_parsesOwnerRepoFormats() {
        assertEquals("cloclo44400/GoCycleMaps", UpdateSettings().githubRepoSlug)
        assertEquals("cloclo44400" to "GoCycleMaps", UpdateSettings.parseOwnerRepo("cloclo44400/GoCycleMaps"))
        assertEquals("cloclo44400" to "GoCycleMaps", UpdateSettings.parseOwnerRepo("https://github.com/cloclo44400/GoCycleMaps/"))
        assertNull(UpdateSettings.parseOwnerRepo("invalid-repo"))
        assertNotNull(CosmeticCatalog.findById("bike_mint"))
    }
}
