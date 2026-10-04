package com.kanyandula.discovernearby.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.discovery.DiscoverError
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.AttributeSource.DERIVED
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType.CAFE
import com.kanyandula.discovernearby.model.AttributeType.DRIVE_THROUGH
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.AttributeType.TOILETS
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Content
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Empty
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Error
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Loading
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.PermissionRequired
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The screen is stateless: each test sets the state and reads what is drawn. One composition per test, so a
// state change redraws the same screen (docs/02 §6).
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class RecommendationsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf<RecommendationsUiState>(Loading)
    private var retries = 0
    private var backs = 0
    private var selected: Recommendation? = null
    private var grants = 0

    @Before
    fun setUp() {
        rule.setContent {
            DiscoverNearbyTheme {
                RecommendationsScreen(
                    category = COFFEE,
                    state = state,
                    onRetry = { retries++ },
                    onBack = { backs++ },
                    onPlaceSelected = { selected = it },
                    onGrant = { grants++ },
                )
            }
        }
    }

    private fun row(place: PlaceSummary, meters: Int) = Recommendation(
        place = place,
        score = 1.0,
        distanceMeters = meters,
        travelTimeMinutes = null,
        minutesAhead = null,
        detourMinutes = null,
    )

    @Test
    fun loadingKeepsTheCategoryTitle() {
        rule.onNodeWithText("Coffee").assertIsDisplayed()
        rule.onNodeWithText("Finding good places nearby…").assertIsDisplayed()
    }

    @Test
    fun rowsShowOnlyWhatIsKnown() {
        val full = testPlace("Harbour Roasters", "cafe").copy(
            rating = 4.6,
            ratingCount = 212,
            attributes = setOf(
                PlaceAttribute(PARKING, PROVIDED),
                PlaceAttribute(TOILETS, DERIVED),
                PlaceAttribute(CAFE, PROVIDED),
                PlaceAttribute(DRIVE_THROUGH, PROVIDED),
            ),
        )
        val noCount = testPlace("Corner Café", "cafe").copy(rating = 4.1)
        val bare = testPlace("Brew & Bloom", "cafe")
        state = Content(requestId = 1, recommendations = listOf(row(full, 2_100), row(noCount, 900), row(bare, 400)))

        rule.onNodeWithText("Harbour Roasters").assertIsDisplayed()
        rule.onNodeWithText("4.6 ★ (212) · Parking · Toilets · Café").assertIsDisplayed() // at most three
        rule.onNodeWithText("2.1 km").assertIsDisplayed()
        rule.onNodeWithText("4.1 ★").assertIsDisplayed()
        rule.onNodeWithText("0.9 km").assertIsDisplayed()
        rule.onNodeWithText("Brew & Bloom").assertIsDisplayed()
        rule.onNodeWithText("0.4 km").assertIsDisplayed()
        rule.onAllNodesWithText("★", substring = true).assertCountEquals(2) // nothing blank on the bare row
    }

    @Test
    fun emptyOffersOnlyBackToDiscover() {
        state = Empty
        rule.onNodeWithText("No good matches nearby").assertIsDisplayed()
        rule.onNodeWithText("Try another category.").assertIsDisplayed()
        rule.onNodeWithText("Try Again").assertDoesNotExist()
        rule.onNodeWithText("Back to Discover").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun networkAndProviderFailuresShareOneMessageWithRetryAndBack() {
        listOf(DiscoverError.NetworkUnavailable, DiscoverError.ProviderFailure).forEach { error ->
            state = Error(error)
            rule.onNodeWithText("Unable to load places").assertIsDisplayed()
            rule.onNodeWithText("Check your connection and try again.").assertIsDisplayed()
        }
        rule.onNodeWithText("Try Again").performClick()
        rule.onNodeWithText("Back").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun timeoutHasItsOwnMessage() {
        state = Error(DiscoverError.Timeout)
        rule.onNodeWithText("Taking longer than expected").assertIsDisplayed()
        rule.onNodeWithText("Please try again.").assertIsDisplayed()
        rule.onNodeWithText("Try Again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun locationUnavailableOffersRetry() {
        state = Error(DiscoverError.LocationUnavailable)
        rule.onNodeWithText("Location unavailable").assertIsDisplayed()
        rule.onNodeWithText("Try Again").assertIsDisplayed()
    }

    // docs/02 §10: Grant only while restrictions allow it; otherwise ask the user to park.
    @Test
    fun permissionCopyFollowsTheRestrictions() {
        state = PermissionRequired(canRequest = false)
        rule.onNodeWithText("Location permission required").assertIsDisplayed()
        rule.onNodeWithText("Park the vehicle to allow Discover Nearby to access your location.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").assertDoesNotExist()
        state = PermissionRequired(canRequest = true)
        rule.onNodeWithText("Discover Nearby needs your location to find places around you.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").performClick()
        assertEquals(1, grants)
        rule.onNodeWithText("Try Again").assertDoesNotExist()
    }

    @Test
    fun deniedOffersGrantAgain() {
        state = PermissionRequired(canRequest = true, denied = true)
        rule.onNodeWithText("Discover Nearby can't find places without location access.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").assertIsDisplayed()
        rule.onNodeWithText("Back").assertIsDisplayed()
    }

    // While driving the park-first copy wins over the denied copy, and no request can be made.
    @Test
    fun restrictedCopyWinsOverDenied() {
        state = PermissionRequired(canRequest = false, denied = true)
        rule.onNodeWithText("Park the vehicle to allow Discover Nearby to access your location.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").assertDoesNotExist()
    }

    @Test
    fun actionsMeetTheTouchTarget() {
        state = Error(DiscoverError.NetworkUnavailable)
        rule.onNodeWithText("Try Again").assertHeightIsAtLeast(MinTouchTarget)
        rule.onNodeWithText("Back").assertHeightIsAtLeast(MinTouchTarget)
    }

    @Test
    fun tappingARowSelectsIt() {
        val chosen = row(testPlace("Harbour Roasters", "cafe"), 500)
        state = Content(requestId = 1, recommendations = listOf(chosen))
        rule.onNodeWithText("Harbour Roasters").performClick()
        assertEquals(chosen, selected)
    }
}
