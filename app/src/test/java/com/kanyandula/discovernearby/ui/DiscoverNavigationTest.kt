package com.kanyandula.discovernearby.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1024dp-h768dp-land-mdpi")
class DiscoverNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        rule.setContent { DiscoverNearbyTheme { DiscoverNearbyApp() } }
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

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
