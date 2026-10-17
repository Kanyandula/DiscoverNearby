package com.kanyandula.discovernearby.ui

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.location.deviceAt
import com.kanyandula.discovernearby.places.fake.TestLocation
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// DN-UX-006: the rail's tabs never build a stack, and the Saved tab goes back to the Saved screen it left.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class OpenTabTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        deviceAt(TestLocation.GREYSTONES.point)
        rule.setContent {
            nav = rememberNavController()
            DiscoverNearbyTheme { DiscoverNavHost(appContainer(), navController = nav) }
        }
    }

    private fun open(tab: RailTab) = rule.runOnIdle { nav.openTab(tab) }

    private fun topId() = rule.runOnIdle { nav.currentBackStackEntry?.id }

    // Tapped again, on Saved or on a place opened from it, Saved keeps its screen: list, scroll and focus stay.
    @Test
    fun savedAgainReturnsToTheSameSavedScreen() {
        open(RailTab.SAVED)
        val savedScreen = topId()
        open(RailTab.SAVED)
        assertEquals(savedScreen, topId())
        rule.runOnIdle {
            nav.navigate(PlaceDetailsRoute(testPlace("cafe-1", "cafe"), null, DiscoveryCategory.COFFEE))
        }
        open(RailTab.SAVED)
        assertEquals(savedScreen, topId())
    }

    // DN-UX-011: Map and Profile follow the same rules: tapped again they keep their screen, and the tab screens
    // replace each other on Discover, so Back from any of them lands on the grid.
    @Test
    fun tabScreensReplaceEachOtherOnDiscover() {
        open(RailTab.MAP)
        val mapScreen = topId()
        open(RailTab.MAP)
        assertEquals(mapScreen, topId())
        open(RailTab.PROFILE)
        assertEquals(RailTab.PROFILE, rule.runOnIdle { nav.selectedTab() })
        open(RailTab.SAVED)
        rule.runOnIdle { nav.popBackStack() }
        assertTrue(rule.runOnIdle { nav.currentDestination?.hasRoute<DiscoverRoute>() == true })
    }
}
