package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PriceLevel
import com.kanyandula.discovernearby.model.ProviderRating
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.ReferencePanelHeight
import com.kanyandula.discovernearby.ui.ReferencePanelWidth
import com.kanyandula.discovernearby.ui.hereNotice
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Content
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.SummaryOnly
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The summary-only note is the only sign of the fallback; a full set of facts must not push it off the panel.
// Native graphics measure real text: the legacy mode gives every character about 1 px, so nothing ever wraps.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceDetailsFitTest {

    @get:Rule
    val rule = createComposeRule()

    private val everything = testPlace("Adventure Playground, Greystones", "playground").copy(
        rating = 4.6,
        ratingCount = 180,
        isOpenNow = true,
        attributes = AttributeType.entries.map { PlaceAttribute(it, PROVIDED) }.toSet(),
        attribution = "HERE",
    )
    private val enrichment = PlaceEnrichment(
        photoUrl = "https://example.test/p.jpg",
        rating = ProviderRating(4.5, 312, "https://example.test/b.png"),
        priceLevel = PriceLevel.FINE,
        description = "Seasonal plates from local growers and the morning's catch, served in a " +
            "bright room over the bay, with a short wine list and a garden terrace in summer.",
    )

    @Test
    fun theWholeConceptFitsTheReferencePanel() {
        rule.setContent {
            DiscoverNearbyTheme {
                Box(modifier = Modifier.height(ReferencePanelHeight).width(ReferencePanelWidth)) {
                    PlaceDetailsScreen(
                        distanceMeters = 1_400,
                        category = DiscoveryCategory.FAMILY,
                        state = SummaryOnly(everything),
                        onNavigate = {},
                        onBack = {},
                        saved = false,
                        onToggleSave = {},
                        drivingRestricted = false, // parked: the description shows
                        enrichment = enrichment,
                    )
                }
            }
        }
        val note = rule.onNodeWithText("More details unavailable right now")
        note.assertHeightIsAtLeast(30.dp) // one whole titleMedium line
        assertTrue(note.getUnclippedBoundsInRoot().bottom <= ReferencePanelHeight)
        val notice = rule.onNodeWithText(hereNotice())
        notice.assertIsDisplayed()
        notice.assertHeightIsAtLeast(16.dp) // one whole labelMedium line: the last child is squeezed first
        assertTrue(notice.getUnclippedBoundsInRoot().bottom <= ReferencePanelHeight)
        assertTrue(note.getUnclippedBoundsInRoot().bottom <= notice.getUnclippedBoundsInRoot().top)
        // Navigate sits bottom right (03b-place-details-concept) and must stay whole on the panel.
        val navigate = rule.onNodeWithText("Navigate")
        navigate.assertIsDisplayed()
        assertTrue(navigate.getUnclippedBoundsInRoot().bottom <= ReferencePanelHeight)

        // Every part of the concept is on the panel: the tiles and the description sit above the actions.
        rule.onNodeWithText("Parking").assertIsDisplayed()
        val description = rule.onNodeWithText("Seasonal plates", substring = true)
        description.assertIsDisplayed()
        assertTrue(description.getUnclippedBoundsInRoot().bottom <= navigate.getUnclippedBoundsInRoot().top)
        // DN-UX-007: on the fallback the note joins the facts beside the photo, so the description keeps one line.
        val lines = description.getUnclippedBoundsInRoot()
        assertTrue(lines.bottom - lines.top <= 30.dp)
        navigate.assertHeightIsAtLeast(MinTouchTarget)
        val save = rule.onNodeWithText("Save")
        save.assertIsDisplayed()
        save.assertHeightIsAtLeast(MinTouchTarget)
        assertTrue(save.getUnclippedBoundsInRoot().right <= navigate.getUnclippedBoundsInRoot().left)
    }

    @Test
    fun theNormalStateFitsWithATwoLineDescription() {
        rule.setContent {
            DiscoverNearbyTheme {
                Box(modifier = Modifier.height(ReferencePanelHeight).width(ReferencePanelWidth)) {
                    PlaceDetailsScreen(
                        distanceMeters = 1_400,
                        category = DiscoveryCategory.FAMILY,
                        state = Content(PlaceDetails(everything, openingSummary = null)),
                        onNavigate = {},
                        onBack = {},
                        saved = false,
                        onToggleSave = {},
                        drivingRestricted = false,
                        enrichment = enrichment,
                    )
                }
            }
        }
        val description = rule.onNodeWithText("Seasonal plates", substring = true)
        description.assertHeightIsAtLeast(56.dp) // two bodyLarge lines
        val navigate = rule.onNodeWithText("Navigate")
        assertTrue(description.getUnclippedBoundsInRoot().bottom <= navigate.getUnclippedBoundsInRoot().top)
        assertTrue(navigate.getUnclippedBoundsInRoot().bottom <= ReferencePanelHeight)
        rule.onNodeWithText("Parking").assertIsDisplayed()
        rule.onNodeWithText(hereNotice()).assertIsDisplayed()
    }
}
