package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onNodeWithContentDescription
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.useRotaryInput
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsScreen
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState
import com.kanyandula.discovernearby.ui.theme.Highlight
import com.kanyandula.discovernearby.ui.theme.OnSurface
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.Accent
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.SurfaceVariant
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private const val TOLERANCE = 0.02f

// docs/02 §16: focus must be clearly visible. Native graphics, so the capture draws real pixels.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusRingTest {

    @get:Rule
    val rule = createComposeRule()

    /** The colour 2 px inside the tile's left edge, halfway down: inside the ring when it is drawn. */
    private fun SemanticsNodeInteraction.edgePixel(): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[2, pixels.height / 2]
    }

    private fun Color.rgb() = floatArrayOf(red, green, blue)

    @Test
    fun focusedTileShowsTheRing() {
        rule.setContent {
            inputModes = LocalInputModeManager.current
            DiscoverNearbyTheme {
                CategoryTile(DiscoveryCategory.COFFEE, onClick = {}, modifier = Modifier.size(300.dp, 200.dp))
            }
        }
        val tile = rule.onNodeWithText("Coffee")
        // Checked before rotary input starts: switching to keyboard mode can move focus onto the tile by itself.
        assertArrayEquals(SurfaceVariant.rgb(), tile.edgePixel().rgb(), TOLERANCE)
        rule.useRotaryInput(inputModes)
        tile.requestFocus()
        assertArrayEquals(Accent.rgb(), tile.edgePixel().rgb(), TOLERANCE)
    }

    // A touch selection never leaves a ring behind (docs/02 §16 rings are for rotary only).
    @Test
    fun touchedTileShowsNoRing() {
        rule.setContent {
            DiscoverNearbyTheme {
                CategoryTile(DiscoveryCategory.COFFEE, onClick = {}, modifier = Modifier.size(300.dp, 200.dp))
            }
        }
        val tile = rule.onNodeWithText("Coffee")
        tile.performClick()
        assertArrayEquals(SurfaceVariant.rgb(), tile.edgePixel().rgb(), TOLERANCE)
    }

    private lateinit var inputModes: InputModeManager

    /** Content under rotary (keyboard-mode) input. */
    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            inputModes = LocalInputModeManager.current
            DiscoverNearbyTheme { content() }
        }
        rule.useRotaryInput(inputModes)
    }

    private fun text(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun focusedRecommendationRowShowsTheRing() {
        val place = testPlace("cafe-1", "cafe")
        val recommendation = Recommendation(place, 1.0, distanceMeters = 500, null, null, null)
        show { RecommendationRow(recommendation, onClick = {}) }
        val row = rule.onNodeWithText(place.name)
        row.requestFocus()
        assertArrayEquals(Accent.rgb(), row.edgePixel().rgb(), TOLERANCE)
    }

    @Test
    fun focusedHeaderBackShowsTheRing() {
        show { ScreenHeader(title = "Coffee", onBack = {}) }
        val back = rule.onNodeWithContentDescription(text(R.string.back))
        back.requestFocus()
        assertArrayEquals(Accent.rgb(), back.edgePixel().rgb(), TOLERANCE)
    }

    // Navigate is filled with Action blue, so an Accent ring would barely show; it gets a light one.
    @Test
    @Config(qualifiers = AUTOMOTIVE_1024P)
    fun focusedNavigateShowsALightRing() {
        val place = testPlace("cafe-1", "cafe")
        show {
            PlaceDetailsScreen(
                distanceMeters = 500,
                state = PlaceDetailsUiState.SummaryOnly(place),
                onNavigate = {},
                onBack = {},
            )
        }
        val navigate = rule.onNodeWithText(text(R.string.navigate))
        navigate.requestFocus()
        assertArrayEquals(OnSurface.rgb(), navigate.edgePixel().rgb(), TOLERANCE)
    }

    // Error and permission states: Retry/Grant are Action blue (light ring), Back is Raised (Accent ring).
    @Test
    fun focusedMessageButtonsShowTheirRings() {
        show {
            MessageState(
                message = Message(R.drawable.ic_empty, Highlight, R.string.empty_title, R.string.empty_body),
                backLabel = R.string.back,
                onBack = {},
                onPrimary = {},
                primaryLabel = R.string.try_again,
            )
        }
        val retry = rule.onNodeWithText(text(R.string.try_again))
        retry.requestFocus()
        assertArrayEquals(OnSurface.rgb(), retry.edgePixel().rgb(), TOLERANCE)
        val back = rule.onNodeWithText(text(R.string.back))
        back.requestFocus()
        assertArrayEquals(Accent.rgb(), back.edgePixel().rgb(), TOLERANCE)
    }
}
