package com.kanyandula.discovernearby

import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import com.kanyandula.discovernearby.places.here.HerePlacesRepository
import com.kanyandula.discovernearby.ui.appContainer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {

    private val app = RuntimeEnvironment.getApplication()

    // DN-M1-002: live HERE data whenever a key is configured (ADR-001, provisionally selected).
    @Test
    fun aKeyMakesTheDataLive() {
        assertTrue(AppContainer(app, hereApiKey = "k").placesRepository is HerePlacesRepository)
    }

    @Test
    fun noKeyServesTheFakes() {
        assertTrue(AppContainer(app, hereApiKey = "").placesRepository is FakePlacesRepository)
    }

    // Review Focus 2: the docs/04 scenarios (timeout, Park/Drive) need the fakes, key or not.
    @Test
    fun aDebugScenarioServesTheFakesEvenWithAKey() {
        val container = AppContainer(app, hereApiKey = "k").apply { useFakeScenario("SLOW") }
        assertTrue(container.placesRepository is FakePlacesRepository)
    }

    // Review Focus 1: Robolectric runs TestDiscoverApplication, so no test reaches HERE, whatever the local key.
    @Test
    fun robolectricTestsUseTheFakes() {
        assertTrue(app is TestDiscoverApplication)
        assertTrue(appContainer().placesRepository is FakePlacesRepository)
    }

    // A scenario named on a warm relaunch, after live data was already used, still serves the fakes.
    @Test
    fun aScenarioNamedAfterLiveUseStillServesTheFakes() {
        val container = AppContainer(app, hereApiKey = "k")
        assertTrue(container.placesRepository is HerePlacesRepository)
        container.useFakeScenario("SLOW")
        assertTrue(container.placesRepository is FakePlacesRepository)
    }
}
