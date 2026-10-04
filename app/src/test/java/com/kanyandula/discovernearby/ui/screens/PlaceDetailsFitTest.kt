package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.SummaryOnly
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The panel a destination gets on the reference AVD: 768 dp, less the system bars (76 + 96), the app header (56),
 * the outer bottom padding (24) and the panel's own padding (24 + 24).
 */
private val ReferencePanelHeight = 468.dp

// The summary-only note is the only sign of the fallback; a full set of facts must not push it off the panel.
// Native graphics measure real text: the legacy mode gives every character about 1 px, so nothing ever wraps.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceDetailsFitTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun summaryOnlyNoteFitsTheReferencePanel() {
        val everything = testPlace("Adventure Playground, Greystones", "playground").copy(
            rating = 4.6,
            ratingCount = 180,
            isOpenNow = true,
            attributes = AttributeType.entries.map { PlaceAttribute(it, PROVIDED) }.toSet(),
        )
        rule.setContent {
            DiscoverNearbyTheme {
                Box(modifier = Modifier.height(ReferencePanelHeight)) {
                    PlaceDetailsScreen(
                        distanceMeters = 1_400,
                        state = SummaryOnly(everything),
                        onNavigate = {},
                        onBack = {},
                    )
                }
            }
        }
        val note = rule.onNodeWithText("More details unavailable right now")
        note.assertHeightIsAtLeast(30.dp) // one whole titleMedium line
        assertTrue(note.getUnclippedBoundsInRoot().bottom <= ReferencePanelHeight)
    }
}
