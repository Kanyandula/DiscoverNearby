package com.kanyandula.discovernearby.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.location.deviceAt
import com.kanyandula.discovernearby.places.fake.TestLocation
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class DiscoverNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        deviceAt(TestLocation.GREYSTONES.point) // the app reads the real location since DN-M0-006
        rule.setContent {
            inputModes = LocalInputModeManager.current
            DiscoverNearbyTheme { DiscoverNearbyApp(appContainer()) }
        }
    }

    /**
     * System Back as a person presses it: after the previous tap has been composed (NavHost enables its
     * back handler on recomposition), then long enough for Navigation's back coroutine to pop.
     */
    private fun systemBack() {
        rule.waitForIdle()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    private lateinit var inputModes: InputModeManager

    /** A rotary select, then long enough for the navigation it starts. */
    private fun rotarySelect(node: SemanticsNodeInteraction) {
        rule.rotarySelect(node, inputModes)
        rule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    /** Two clicks in one UI-thread turn, before any recomposition: a real double tap. */
    private fun doubleTap(node: SemanticsNodeInteraction) {
        val click = checkNotNull(node.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        rule.runOnUiThread {
            click()
            click()
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    private fun onDiscover() = rule.onNodeWithText("Coffee").assertIsDisplayed()

    @Test
    fun showsHeaderAndStartsOnDiscover() {
        rule.onNodeWithText("Discover Nearby").assertIsDisplayed()
        onDiscover()
    }

    @Test
    fun tileOpensRecommendationsForThatCategory() {
        rule.onNodeWithText("Food").performClick()
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
        rule.onNodeWithText("Food").assertIsDisplayed()
        rule.onNodeWithText("Coffee").assertDoesNotExist()
    }

    @Test
    fun backButtonReturnsToDiscover() {
        rule.onNodeWithText("Food").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        onDiscover()
    }

    @Test
    fun systemBackReturnsToDiscover() {
        rule.onNodeWithText("Food").performClick()
        systemBack()
        onDiscover()
    }

    // Loading then content draw on the one destination: a single Back returns to Discover.
    @Test
    fun recommendationsShowFakePlacesOnOneDestination() {
        rule.onNodeWithText("Family").performClick()
        rule.onNodeWithText("Adventure Playground, Greystones").assertIsDisplayed()
        systemBack()
        onDiscover()
    }

    // docs/02 §16, V7: after Back, rotary focus returns to the tile it left from.
    @Test
    fun rotaryFocusReturnsToTheOriginatingTile() {
        rotarySelect(rule.onNodeWithText("Family"))
        systemBack()
        rule.onNodeWithText("Family").assertIsFocused()
    }

    // V7: after Back from Details, focus returns to the row, so the next turn moves on from it.
    @Test
    fun rotaryFocusReturnsToTheOriginatingRow() {
        rotarySelect(rule.onNodeWithText("Family"))
        rotarySelect(rule.onNodeWithText("Adventure Playground, Greystones"))
        systemBack()
        rule.onNodeWithText("Adventure Playground, Greystones").assertIsFocused()
    }

    // Review Focus 2: a touch selection leaves no focus, so no ring appears on return.
    @Test
    fun touchSelectionLeavesNoFocusBehind() {
        rule.onNodeWithText("Family").performClick()
        systemBack()
        rule.onNodeWithText("Family").assertIsNotFocused()
    }

    @Test
    fun systemBackOnDiscoverLeavesTheApp() {
        systemBack()
        assertTrue(rule.activity.isFinishing)
    }

    @Test
    fun backButtonMeetsTheMinimumTouchTarget() {
        rule.onNodeWithText("Food").performClick()
        rule.onNodeWithContentDescription("Back")
            .assertHeightIsAtLeast(MinTouchTarget).assertWidthIsAtLeast(MinTouchTarget)
    }

    @Test
    fun doubleTapOnATileOpensOneScreen() {
        doubleTap(rule.onNodeWithText("Food"))
        systemBack()
        onDiscover()
    }

    @Test
    fun doubleTapOnBackStaysOnDiscover() {
        rule.onNodeWithText("Food").performClick()
        doubleTap(rule.onNodeWithContentDescription("Back"))
        onDiscover()
    }

    // A deliberate tap right after Back must still act; only the stale second tap is dropped.
    @Test
    fun tileTappedRightAfterBackOpensIt() {
        rule.onNodeWithText("Food").performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Back").performClick()
        rule.mainClock.advanceTimeBy(MID_FADE_MS)
        rule.onNodeWithText("Coffee").performClick()
        rule.mainClock.autoAdvance = true
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
        rule.onNodeWithText("Coffee").assertIsDisplayed()
        rule.onNodeWithText("Food").assertDoesNotExist()
    }

    @Test
    fun backTappedRightAfterOpeningReturns() {
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Food").performClick()
        rule.mainClock.advanceTimeBy(MID_FADE_MS)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.mainClock.autoAdvance = true
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
        onDiscover()
    }

    // docs/04 E: Navigate hands the selected place to the system — Greystones + Harbour Roasters' offset.
    @Test
    fun navigateHandsTheSelectedPlaceToTheSystem() {
        rule.onNodeWithText("Coffee").performClick()
        rule.onNodeWithText("Harbour Roasters, Greystones").performClick()
        rule.onNodeWithText("Navigate").performClick()
        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("geo:53.148000,-6.060300", started.dataString)
    }

    // docs/04 S: no navigation app → the message; Back returns to the list.
    @Test
    fun navigateWithNoHandlerShowsNavigationUnavailableAndBackReturns() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        rule.onNodeWithText("Coffee").performClick()
        rule.onNodeWithText("Harbour Roasters, Greystones").performClick()
        rule.onNodeWithText("Navigate").performClick()
        rule.onNodeWithText("Navigation unavailable").assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.onNodeWithText("The Daily Grind, Greystones").assertIsDisplayed()
        rule.onNodeWithText("Navigation unavailable").assertDoesNotExist()
    }

    private companion object {
        const val SETTLE_MS = 2_000L

        // Well inside NavHost's 700 ms default fade: the new screen is drawn but not yet RESUMED.
        const val MID_FADE_MS = 100L
    }

    // The route carries the place as JSON: a name with "&" and "," must arrive intact; Back returns to the list.
    @Test
    fun rowOpensPlaceDetailsAndBackReturnsToTheList() {
        rule.onNodeWithText("Coffee").performClick()
        rule.onNodeWithText("Brew & Bloom, Greystones").performClick()
        rule.onNodeWithText("Navigate").assertIsDisplayed()
        rule.onNodeWithText("Brew & Bloom, Greystones").assertIsDisplayed()
        systemBack()
        rule.onNodeWithText("Harbour Roasters, Greystones").assertIsDisplayed()
        rule.onNodeWithText("Navigate").assertDoesNotExist()
    }

    @Test
    fun doubleTappedRowOpensOneDetailsScreen() {
        rule.onNodeWithText("Coffee").performClick()
        doubleTap(rule.onNodeWithText("Harbour Roasters, Greystones"))
        systemBack()
        rule.onNodeWithText("Brew & Bloom, Greystones").assertIsDisplayed()
    }
}
