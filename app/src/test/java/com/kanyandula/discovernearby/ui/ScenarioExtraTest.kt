package com.kanyandula.discovernearby.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import com.kanyandula.discovernearby.places.fake.FakeScenario
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ScenarioExtraTest {

    private val app = RuntimeEnvironment.getApplication()

    private fun scenario() = (appContainer().placesRepository as FakePlacesRepository).scenario

    private fun launch(scenario: String) {
        val intent = Intent(app, MainActivity::class.java).putExtra(EXTRA_SCENARIO, scenario)
        Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    }

    @Test
    fun debugLaunchSelectsTheFakeScenario() {
        launch("SLOW")
        assertEquals(FakeScenario.SLOW, scenario())
    }

    @Test
    fun nonDebuggableBuildIgnoresTheExtra() {
        app.applicationInfo.flags = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        launch("SLOW")
        assertEquals(FakeScenario.NORMAL, scenario())
    }
}
