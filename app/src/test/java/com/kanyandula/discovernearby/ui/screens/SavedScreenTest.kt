package com.kanyandula.discovernearby.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.saved.SavedPlace
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.ROTARY_VERTICALLY_SCROLLABLE
import com.kanyandula.discovernearby.ui.hereNotice
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class SavedScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf<SavedUiState>(SavedUiState.Loading)
    private val opened = mutableListOf<String>()

    private val cafe = SavedPlace(
        testPlace("Harbour Café", "cafe").copy(attribution = "HERE"),
        DiscoveryCategory.COFFEE,
        enrichment = null,
    )
    private val park = SavedPlace(testPlace("Hill Park", "park"), DiscoveryCategory.OUTDOORS, enrichment = null)

    @Before
    fun setUp() {
        rule.setContent {
            DiscoverNearbyTheme {
                SavedScreen(state = state, onBack = {}, onPlaceSelected = { opened += it.place.summary.id })
            }
        }
    }

    @Test
    fun nothingSavedSaysHowToSave() {
        state = SavedUiState.Empty
        rule.onNodeWithText("No saved places yet").assertIsDisplayed()
        rule.onNodeWithText("Tap Save on a place to keep it here.").assertIsDisplayed()
    }

    @Test
    fun rowsShowTheDistanceOnlyWhenKnownAndOpenThePlace() {
        state = SavedUiState.Content(listOf(SavedRow(cafe, 2_100), SavedRow(park, null)))
        rule.onNodeWithText("2.1 km").assertIsDisplayed()
        rule.onAllNodesWithText("km", substring = true).assertCountEquals(1)
        rule.onNodeWithText("Hill Park").performClick()
        assertEquals(listOf("Hill Park"), opened)
    }

    @Test
    fun hereRowsCarryHeresNotice() {
        state = SavedUiState.Content(listOf(SavedRow(cafe, 2_100)))
        rule.onNodeWithText(hereNotice()).assertIsDisplayed()
    }

    // DN-UX-009, DN-UX-010: Saved's list is marked for the rotary service, as Recommendations' is.
    @Test
    fun savedRowsSitInAListMarkedForTheRotaryService() {
        state = SavedUiState.Content(listOf(SavedRow(cafe, 2_100), SavedRow(park, null)))
        rule.onNode(hasScrollAction() and hasContentDescription(ROTARY_VERTICALLY_SCROLLABLE)).assertExists()
    }
}
