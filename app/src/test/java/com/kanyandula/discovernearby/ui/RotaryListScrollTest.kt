package com.kanyandula.discovernearby.ui

import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.requestFocus
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.screens.RecommendationsScreen
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DN-UX-009, DN-UX-010: the rotary service moves only to rows on screen. At a marked list's visible edge it sends a
 * rotary scroll instead of leaving the list, and the app moves to the next or previous row, on screen or not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class RotaryListScrollTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val list = hasScrollAction()

    @Before
    fun setUp() {
        lateinit var inputModes: InputModeManager
        rule.setContent {
            inputModes = LocalInputModeManager.current
            DiscoverNearbyTheme {
                // The panel a destination gets beside the rail on the reference AVD (PlaceDetailsFitTest).
                Box(modifier = Modifier.size(width = ReferencePanelWidth, height = ReferencePanelHeight)) {
                    RecommendationsScreen(
                        category = DiscoveryCategory.COFFEE,
                        state = RecommendationsUiState.Content(
                            requestId = 1,
                            recommendations = List(ROWS) {
                                Recommendation(testPlace("Row $it", "cafe"), 1.0, 500, null, null, null)
                            },
                        ),
                        onRetry = {},
                        onBack = {},
                        onPlaceSelected = { _, _ -> },
                        onGrant = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
        rule.useRotaryInput(inputModes)
    }

    private fun scroll(event: MotionEvent): Boolean {
        var consumed = false
        rule.runOnUiThread { consumed = rule.activity.moveFocusForRotaryScroll(event) }
        rule.waitForIdle()
        return consumed
    }

    @Test
    fun theListIsMarkedForTheRotaryService() {
        rule.onNode(list and hasContentDescription(ROTARY_VERTICALLY_SCROLLABLE)).assertExists()
    }

    // D2: a quick turn from the last row on screen goes on to the row below it.
    @Test
    fun aClockwiseScrollMovesToTheRowBelow() {
        rule.onNodeWithText("Row 2").requestFocus()
        assertTrue(scroll(rotaryScroll(-1f)))
        rule.onNodeWithText("Row 3").assertIsFocused().assertIsDisplayed()
    }

    // D1: with the list scrolled down, a turn up from the top row on screen reaches the row above the screen.
    @Test
    fun aCounterClockwiseScrollMovesToTheRowAboveTheScreen() {
        rule.onNode(list).performScrollToIndex(2)
        rule.onNodeWithText("Row 1").assertDoesNotExist()
        rule.onNodeWithText("Row 2").requestFocus()
        assertTrue(scroll(rotaryScroll(1f)))
        rule.onNodeWithText("Row 1").assertIsFocused().assertIsDisplayed()
    }

    @Test
    fun eachDetentMovesOneRow() {
        rule.onNodeWithText("Row 0").requestFocus()
        scroll(rotaryScroll(-2f))
        rule.onNodeWithText("Row 2").assertIsFocused()
    }

    @Test
    fun otherScrollsAreLeftAlone() {
        rule.onNodeWithText("Row 0").requestFocus()
        assertFalse(scroll(rotaryScroll(-1f, source = InputDevice.SOURCE_MOUSE)))
        assertFalse(scroll(rotaryScroll(-1f, axis = MotionEvent.AXIS_SCROLL)))
        rule.onNodeWithText("Row 0").assertIsFocused()
    }

    private companion object {
        const val ROWS = 5
    }
}
