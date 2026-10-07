package com.kanyandula.discovernearby.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.AttributeSource.DERIVED
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType.FAMILY_FRIENDLY
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.hereNotice
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Content
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Loading
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.SummaryOnly
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Stateless screen: each test sets the state; one composition per test, so a state change redraws in place.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class PlaceDetailsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val place = testPlace("The Daily Grind", "cafe").copy(
        rating = 4.6,
        ratingCount = 342,
        isOpenNow = true,
        attributes = setOf(PlaceAttribute(PARKING, PROVIDED), PlaceAttribute(FAMILY_FRIENDLY, DERIVED)),
    )
    private var state by mutableStateOf<PlaceDetailsUiState>(Loading(place))
    private var navigations = 0
    private var backs = 0

    @Before
    fun setUp() {
        rule.setContent {
            DiscoverNearbyTheme {
                PlaceDetailsScreen(
                    distanceMeters = 2_100,
                    state = state,
                    onNavigate = { navigations++ },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun contentShowsWhatIsKnown() {
        state = Content(PlaceDetails(place, openingSummary = "Open until 18:00"))
        rule.onNodeWithText("The Daily Grind").assertIsDisplayed()
        rule.onNodeWithText("2.1 km away").assertIsDisplayed()
        rule.onNodeWithText("4.6 ★ (342 reviews)").assertIsDisplayed()
        rule.onNodeWithText("Open until 18:00").assertIsDisplayed()
        rule.onNodeWithText("Parking · Family-friendly").assertIsDisplayed()
        rule.onNodeWithText("Amenities").assertIsDisplayed()
        rule.onNodeWithText("More details unavailable right now").assertDoesNotExist()
    }

    // docs/02 §7: a failed details call keeps the summary and Navigate.
    @Test
    fun summaryOnlyKeepsTheSummaryAndNavigate() {
        state = SummaryOnly(place)
        rule.onNodeWithText("4.6 ★ (342 reviews)").assertIsDisplayed()
        rule.onNodeWithText("Open now").assertIsDisplayed()
        rule.onNodeWithText("More details unavailable right now").assertIsDisplayed()
        rule.onNodeWithText("Navigate").performClick()
        assertEquals(1, navigations)
    }

    @Test
    fun navigateIsAvailableWhileDetailsLoad() {
        rule.onNodeWithText("2.1 km away").assertIsDisplayed()
        rule.onNodeWithText("More details unavailable right now").assertDoesNotExist()
        rule.onNodeWithText("Navigate").performClick()
        assertEquals(1, navigations)
    }

    @Test
    fun unknownFieldsAreLeftOut() {
        state = SummaryOnly(testPlace("Brew & Bloom", "cafe"))
        rule.onNodeWithText("Brew & Bloom").assertIsDisplayed()
        rule.onNodeWithText("2.1 km away").assertIsDisplayed()
        rule.onAllNodesWithText("★", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Amenities").assertDoesNotExist()
        rule.onNodeWithText("Open now").assertDoesNotExist()
    }

    @Test
    fun oneReviewIsSingular() {
        state = SummaryOnly(place.copy(ratingCount = 1))
        rule.onNodeWithText("4.6 ★ (1 review)").assertIsDisplayed()
    }

    @Test
    fun navigateMeetsTheTouchTargetAndBackWorks() {
        rule.onNodeWithText("Navigate").assertHeightIsAtLeast(MinTouchTarget)
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    // docs/02 §14, design 11: the message under the place's header; Back is the only action.
    @Test
    fun navigationUnavailableShowsTheMessageWithBack() {
        state = PlaceDetailsUiState.NavigationUnavailable(place)
        rule.onNodeWithText(place.name).assertIsDisplayed()
        rule.onNodeWithText("Navigation unavailable").assertIsDisplayed()
        rule.onNodeWithText("No compatible navigation app could open this destination.").assertIsDisplayed()
        rule.onNodeWithText("Navigate").assertDoesNotExist()
        rule.onNodeWithText("Back").performClick()
        assertEquals(1, backs)
    }

    // Review Focus 1: the notice comes with the summary, so it shows while loading, with details, and on the
    // summary-only fallback alike.
    @Test
    fun hereDataShowsTheNoticeInEveryState() {
        val live = place.copy(attribution = "HERE")
        val notice = hereNotice()
        listOf(Loading(live), Content(PlaceDetails(live, openingSummary = null)), SummaryOnly(live)).forEach {
            state = it
            rule.onNodeWithText(notice).assertIsDisplayed()
        }
    }

    // Review Focus 3
    @Test
    fun dataWithoutAnAttributionShowsNoNotice() {
        state = SummaryOnly(place)
        rule.onAllNodesWithText("©", substring = true).assertCountEquals(0)
    }
}
