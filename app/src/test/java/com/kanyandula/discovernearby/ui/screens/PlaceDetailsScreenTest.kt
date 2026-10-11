package com.kanyandula.discovernearby.ui.screens

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.AttributeSource.DERIVED
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType.DRIVE_THROUGH
import com.kanyandula.discovernearby.model.AttributeType.FAMILY_FRIENDLY
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.Closing
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PriceLevel
import com.kanyandula.discovernearby.model.ProviderRating
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.hereNotice
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Content
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Loading
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.SummaryOnly
import com.kanyandula.discovernearby.ui.theme.DetailsImageWidth
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.OpenNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalTime

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
    private val tripadvisor = PlaceEnrichment(
        photoUrl = "https://example.test/p.jpg",
        rating = ProviderRating(4.5, 312, "https://example.test/b.png"),
    )
    private var state by mutableStateOf<PlaceDetailsUiState>(Loading(place))
    private var enrichment by mutableStateOf<PlaceEnrichment?>(null)
    private var restricted by mutableStateOf(false)
    private var isSaved by mutableStateOf(false)
    private var distance by mutableStateOf<Int?>(2_100)
    private var saves = 0
    private var navigations = 0
    private var backs = 0

    @Before
    fun setUp() {
        Settings.System.putString(RuntimeEnvironment.getApplication().contentResolver, Settings.System.TIME_12_24, "24")
        rule.setContent {
            DiscoverNearbyTheme {
                PlaceDetailsScreen(
                    distanceMeters = distance,
                    category = DiscoveryCategory.COFFEE,
                    state = state,
                    onNavigate = { navigations++ },
                    onBack = { backs++ },
                    saved = isSaved,
                    onToggleSave = { saves++ },
                    enrichment = enrichment,
                    drivingRestricted = restricted,
                )
            }
        }
    }

    @Test
    fun contentShowsWhatIsKnown() {
        state = Content(PlaceDetails(place, openingSummary = "Open until 18:00"))
        rule.onNodeWithText("The Daily Grind").assertIsDisplayed()
        rule.onNodeWithText("Café · 2.1 km away").assertIsDisplayed()
        rule.onNodeWithText("4.6 ★ (342 reviews)").assertIsDisplayed()
        // The status, as on the canvas; the provider's schedule only stands in when the status is unknown.
        rule.onNodeWithText("Open now").assertIsDisplayed()
        rule.onNodeWithText("Open until 18:00").assertDoesNotExist()
        rule.onNodeWithText("Parking").assertIsDisplayed()
        rule.onNodeWithText("Family-friendly").assertIsDisplayed()
        rule.onNodeWithText("More details unavailable right now").assertDoesNotExist()
    }

    // DN-UX-004: the row's Tripadvisor photo and rating, credited, in place of the place's own text rating.
    @Test
    fun tripadvisorsRatingAndPhotoCreditShow() {
        enrichment = tripadvisor
        state = Content(PlaceDetails(place, openingSummary = null))
        rule.onNodeWithContentDescription("Tripadvisor rating 4.5 of 5").assertExists()
        rule.onNodeWithText("(312 reviews)").assertIsDisplayed()
        rule.onNodeWithText("4.6 ★ (342 reviews)").assertDoesNotExist()
        rule.onNodeWithText("Photo: Tripadvisor").assertIsDisplayed()
        rule.onNodeWithText("Navigate").assertIsDisplayed()
    }

    // DN-UX-007 (artboard 3c): Back and the name head the page; the photo sits under Back, the facts beside it.
    @Test
    fun backHeadsThePageAndThePhotoSitsUnderIt() {
        enrichment = tripadvisor
        state = Content(PlaceDetails(place, openingSummary = null))
        val back = rule.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot()
        val name = rule.onNodeWithText("The Daily Grind").getUnclippedBoundsInRoot()
        val credit = rule.onNodeWithText("Photo: Tripadvisor").getUnclippedBoundsInRoot()
        val reviews = rule.onNodeWithText("(312 reviews)").getUnclippedBoundsInRoot()
        assertTrue(name.left >= back.right && name.bottom <= back.bottom)
        assertTrue(credit.top >= back.bottom)
        assertEquals(back.left.value, credit.left.value, 0.5f)
        assertTrue(reviews.left >= back.left + DetailsImageWidth && reviews.top < credit.top)
    }

    @Test
    fun scheduleStandsInWhenTheStatusIsUnknown() {
        state = Content(PlaceDetails(place.copy(isOpenNow = null), openingSummary = "Mon-Sun: 08:00 - 16:00"))
        rule.onNodeWithText("Mon-Sun: 08:00 - 16:00").assertIsDisplayed()
        rule.onNodeWithText("Open now").assertDoesNotExist()
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
        rule.onNodeWithText("Café · 2.1 km away").assertIsDisplayed()
        rule.onNodeWithText("More details unavailable right now").assertDoesNotExist()
        rule.onNodeWithText("Navigate").performClick()
        assertEquals(1, navigations)
    }

    @Test
    fun unknownFieldsAreLeftOut() {
        state = SummaryOnly(testPlace("Brew & Bloom", "cafe"))
        rule.onNodeWithText("Brew & Bloom").assertIsDisplayed()
        rule.onNodeWithText("Café · 2.1 km away").assertIsDisplayed()
        rule.onAllNodesWithText("★", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Parking").assertDoesNotExist()
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

    // DN-UX-005: the opening line says when an open place closes.
    @Test
    fun anOpenPlaceSaysWhenItCloses() {
        state = Content(PlaceDetails(place, openingSummary = null), Closing.At(LocalTime.of(18, 0)))
        rule.onNodeWithText("Open · Closes 18:00").assertIsDisplayed()
        state = Content(PlaceDetails(place, openingSummary = null), Closing.AllDay)
        rule.onNodeWithText("Open 24 hours").assertIsDisplayed()
    }

    // As in the concept: only "Open" is green; the closing time stays muted.
    @Test
    fun onlyOpenIsGreenBeforeTheClosingTime() {
        state = Content(PlaceDetails(place, openingSummary = null), Closing.At(LocalTime.of(18, 0)))
        val line = rule.onNodeWithText("Open · Closes 18:00").fetchSemanticsNode().config[SemanticsProperties.Text]
            .single()
        val green = line.spanStyles.filter { it.item.color == OpenNow }.map { line.text.substring(it.start, it.end) }
        assertEquals(listOf("Open"), green)
    }

    // Review Focus 2: HERE says open, but no period gives a closing time.
    @Test
    fun openWithoutAClosingTimeSaysOpenNow() {
        state = Content(PlaceDetails(place, openingSummary = "Mon-Sun: 08:00 - 16:00"), closing = null)
        rule.onNodeWithText("Open now").assertIsDisplayed()
        rule.onAllNodesWithText("Closes", substring = true).assertCountEquals(0)
    }

    @Test
    fun theKindLineAddsTheTripadvisorPrice() {
        enrichment = PlaceEnrichment(priceLevel = PriceLevel.MID)
        state = Content(PlaceDetails(place, openingSummary = null))
        rule.onNodeWithText("Café · €€–€€€ · 2.1 km away").assertIsDisplayed()
    }

    @Test
    fun aCarParkNearbyIsATile() {
        state = Content(PlaceDetails(place, openingSummary = null), carParkNearby = true)
        rule.onNodeWithText("Car park nearby").assertIsDisplayed()
        state = Content(PlaceDetails(place, openingSummary = null), carParkNearby = false)
        rule.onNodeWithText("Car park nearby").assertDoesNotExist()
    }

    // Review Focus 4: cars limit text while moving, so the description goes and comes back.
    @Test
    fun theDescriptionHidesWhileDriving() {
        enrichment = PlaceEnrichment(description = "A café by the harbour.")
        state = Content(PlaceDetails(place, openingSummary = null))
        rule.onNodeWithText("A café by the harbour.").assertIsDisplayed()
        restricted = true
        rule.onNodeWithText("A café by the harbour.").assertDoesNotExist()
        restricted = false
        rule.onNodeWithText("A café by the harbour.").assertIsDisplayed()
    }

    // The fallback keeps the row's Tripadvisor data too (docs/02 §7).
    @Test
    fun summaryOnlyKeepsThePriceAndDescription() {
        enrichment = PlaceEnrichment(priceLevel = PriceLevel.CHEAP, description = "A café by the harbour.")
        state = SummaryOnly(place)
        rule.onNodeWithText("Café · € · 2.1 km away").assertIsDisplayed()
        rule.onNodeWithText("A café by the harbour.").assertIsDisplayed()
    }

    // Emulator check (DN-UX-005): a tile without an icon keeps its label level with one that has the P icon.
    @Test
    fun tileLabelsLineUpWithOrWithoutAnIcon() {
        val driveThrough = place.copy(attributes = setOf(PlaceAttribute(DRIVE_THROUGH, PROVIDED)))
        state = Content(PlaceDetails(driveThrough, openingSummary = null), carParkNearby = true)
        val plain = rule.onNodeWithText("Drive-through").getUnclippedBoundsInRoot()
        val withIcon = rule.onNodeWithText("Car park nearby").getUnclippedBoundsInRoot()
        assertEquals(withIcon.top, plain.top)
    }

    // DN-UX-006: Save beside Navigate, one tap, also while driving; the label follows the database.
    @Test
    fun saveTogglesAndSaysWhetherThePlaceIsSaved() {
        state = Content(PlaceDetails(place, openingSummary = null))
        restricted = true
        rule.onNodeWithText("Save").performClick()
        assertEquals(1, saves)
        isSaved = true
        rule.onNodeWithText("Saved").assertIsDisplayed()
        rule.onNodeWithText("Save").assertDoesNotExist()
    }

    // DN-UX-006: opened from Saved without a location, the facts leave the distance out.
    @Test
    fun anUnknownDistanceIsLeftOut() {
        distance = null
        state = Content(PlaceDetails(place, openingSummary = null))
        rule.onNodeWithText("Café").assertIsDisplayed()
        rule.onAllNodesWithText("km", substring = true).assertCountEquals(0)
    }
}
