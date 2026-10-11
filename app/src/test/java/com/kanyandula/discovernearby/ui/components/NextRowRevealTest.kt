package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.screens.RecommendationsScreen
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.useRotaryInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DN-UX-006, found on the emulator: the rotary service moves only to rows on screen, and the app rail now follows the
 * list. Unless the next row shows, a turn from the last visible row jumps to the rail instead of scrolling the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class NextRowRevealTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aFocusedRowShowsTheNextOne() {
        val places = List(5) { testPlace("Row $it", "cafe") }
        lateinit var inputModes: InputModeManager
        rule.setContent {
            inputModes = LocalInputModeManager.current
            DiscoverNearbyTheme {
                // The panel a destination gets beside the rail on the reference AVD (PlaceDetailsFitTest).
                Box(modifier = Modifier.size(width = 836.dp, height = 468.dp)) {
                    RecommendationsScreen(
                        category = DiscoveryCategory.COFFEE,
                        state = RecommendationsUiState.Content(
                            requestId = 1,
                            recommendations = places.map { Recommendation(it, 1.0, 500, null, null, null) },
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
        for (index in 0 until places.lastIndex) {
            rule.onNodeWithText("Row $index").requestFocus()
            rule.onNodeWithText("Row ${index + 1}").assertIsDisplayed()
        }
    }
}
