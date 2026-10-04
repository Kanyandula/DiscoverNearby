package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.Accent
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.SurfaceVariant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

// docs/02 §16: focus must be clearly visible. Native graphics, so the capture draws real pixels.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusRingTest {

    @get:Rule
    val rule = createComposeRule()

    // Touch never focuses a clickable; rotary (keyboard mode) does.
    private lateinit var inputModes: InputModeManager

    /** The colour 2 px inside the tile's left edge, halfway down: inside the ring when it is drawn. */
    private fun SemanticsNodeInteraction.edgePixel(): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[2, pixels.height / 2]
    }

    private fun assertNear(expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < TOLERANCE &&
            abs(expected.green - actual.green) < TOLERANCE &&
            abs(expected.blue - actual.blue) < TOLERANCE
        assertTrue("expected about $expected, was $actual", close)
    }

    @Test
    fun focusedTileShowsTheRing() {
        rule.setContent {
            inputModes = LocalInputModeManager.current
            DiscoverNearbyTheme {
                CategoryTile(DiscoveryCategory.COFFEE, onClick = {}, modifier = Modifier.size(300.dp, 200.dp))
            }
        }
        val tile = rule.onNodeWithText("Coffee")
        assertNear(SurfaceVariant, tile.edgePixel())
        rule.runOnIdle { inputModes.requestInputMode(InputMode.Keyboard) } // rotary mode: tiles take focus
        tile.requestFocus()
        assertNear(Accent, tile.edgePixel())
    }

    private companion object {
        const val TOLERANCE = 0.02f
    }
}
