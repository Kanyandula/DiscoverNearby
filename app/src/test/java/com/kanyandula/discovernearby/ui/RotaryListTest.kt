package com.kanyandula.discovernearby.ui

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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DN-UX-006, DN-UX-009, DN-UX-010, found on the emulator: the rotary service moves only to rows on screen, and the
 * app rail follows the list. Unless the next row shows, a turn from the last visible row jumps to the rail; unless the
 * row above shows, a turn up from the top one jumps to Back. A turn quicker than the focused row's reveal reaches the
 * marked list's visible edge, where the service keeps focus and sends a rotary scroll; it shows the row beyond.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class RotaryListTest {

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

    @Test
    fun theListIsMarkedForTheRotaryService() {
        rule.onNode(list and hasContentDescription(ROTARY_VERTICALLY_SCROLLABLE)).assertExists()
    }

    @Test
    fun aFocusedRowShowsTheNextOne() {
        for (index in 0 until ROWS - 1) {
            rule.onNodeWithText("Row $index").requestFocus()
            rule.onNodeWithText("Row ${index + 1}").assertIsDisplayed()
        }
    }

    @Test
    fun aFocusedRowShowsThePreviousOne() {
        rule.onNode(list).performScrollToIndex(ROWS - 1)
        for (index in ROWS - 1 downTo 1) {
            rule.onNodeWithText("Row $index").requestFocus()
            rule.onNodeWithText("Row ${index - 1}").assertIsDisplayed()
        }
    }

    // D2: a quick turn down reaches the focused row at the list's bottom edge.
    @Test
    fun aClockwiseServiceScrollShowsTheRowBelowTheFocusedOne() {
        rule.onNodeWithText("Row 2").requestFocus()
        rule.onNode(list).performScrollToIndex(0)
        rule.onNodeWithText("Row 3").assertDoesNotExist()
        rule.serviceScroll(-1f)
        rule.onNodeWithText("Row 3").assertIsDisplayed()
        rule.onNodeWithText("Row 2").assertIsFocused()
    }

    // D1: a quick turn up reaches the focused row at the list's top edge.
    @Test
    fun aCounterClockwiseServiceScrollShowsTheRowAboveTheFocusedOne() {
        rule.onNodeWithText("Row 2").requestFocus()
        rule.onNode(list).performScrollToIndex(2)
        rule.onNodeWithText("Row 1").assertDoesNotExist()
        rule.serviceScroll(1f)
        rule.onNodeWithText("Row 1").assertIsDisplayed()
        rule.onNodeWithText("Row 2").assertIsFocused()
    }

    private companion object {
        const val ROWS = 5
    }
}
