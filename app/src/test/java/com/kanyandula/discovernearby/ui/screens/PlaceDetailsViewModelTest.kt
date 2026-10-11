package com.kanyandula.discovernearby.ui.screens

import com.kanyandula.discovernearby.MainDispatcherRule
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.PROVIDER_TIMEOUT_MILLIS
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.model.Closing
import com.kanyandula.discovernearby.model.OpeningPeriod
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PriceLevel
import com.kanyandula.discovernearby.navigation.fake.FakeNavigationLauncher
import com.kanyandula.discovernearby.places.ProviderFailure
import com.kanyandula.discovernearby.places.ScriptedPlaces
import com.kanyandula.discovernearby.places.fake.SLOW_DELAY_MILLIS
import com.kanyandula.discovernearby.saved.FakeSavedPlaces
import com.kanyandula.discovernearby.saved.SavedPlace
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Content
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Loading
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.NavigationUnavailable
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.SummaryOnly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy
class PlaceDetailsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val places = ScriptedPlaces()
    private val launcher = FakeNavigationLauncher()
    private val place = testPlace("p1", "cafe", metersNorth = 500)
    private val loaded = PlaceDetails(place.copy(rating = 4.6), openingSummary = "Open until 18:00")

    private val monday10am = LocalDateTime.of(2026, 10, 12, 10, 0)

    private val saved = FakeSavedPlaces()
    private val rowEnrichment = PlaceEnrichment(priceLevel = PriceLevel.MID)

    private fun viewModel() = PlaceDetailsViewModel(
        place = place,
        discover = DiscoverUseCase(places, FakeLocationProvider(), BasicRecommendationEngine()),
        navigation = launcher,
        saved = saved,
        category = DiscoveryCategory.COFFEE,
        enrichment = rowEnrichment,
        now = { monday10am },
    )

    /** [vm] with the screen watching whether the place is saved, its first calls run as far as they can. */
    private fun TestScope.watched(vm: PlaceDetailsViewModel = viewModel()) = vm.also {
        backgroundScope.launch { vm.isSaved.collect {} }
        runCurrent()
    }

    private fun slowDetails(millis: Long): suspend (String) -> PlaceDetails = {
        delay(millis)
        loaded
    }

    @Test
    fun showsTheSummaryWhileDetailsLoad() = runTest {
        places.details = slowDetails(1_000)
        val vm = viewModel()
        runCurrent()
        assertEquals(Loading(place), vm.uiState.value)
        advanceTimeBy(1_001)
        assertEquals(Content(loaded), vm.uiState.value)
    }

    @Test
    fun detailsFailureFallsBackToTheSummary() = runTest {
        places.details = { throw ProviderFailure() }
        val vm = viewModel()
        runCurrent()
        assertEquals(SummaryOnly(place), vm.uiState.value)
    }

    @Test
    fun slowDetailsFallBackAtTheTimeout() = runTest {
        places.details = slowDetails(SLOW_DELAY_MILLIS)
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(PROVIDER_TIMEOUT_MILLIS - 1)
        assertEquals(Loading(place), vm.uiState.value)
        advanceTimeBy(2)
        assertEquals(SummaryOnly(place), vm.uiState.value)
    }

    // docs/02 §7: Navigate never waits for the optional details call.
    @Test
    fun navigateHandsOverThePlaceWhileDetailsLoad() = runTest {
        places.details = slowDetails(SLOW_DELAY_MILLIS)
        val vm = viewModel()
        runCurrent()
        vm.navigate()
        assertEquals(place.location, launcher.lastDestination)
    }

    // docs/03 §16: any hand-off failure becomes NavigationUnavailable, keeping the place for the header.
    @Test
    fun aFailedHandOffShowsNavigationUnavailable() = runTest {
        places.details = { loaded }
        val vm = viewModel()
        runCurrent()
        launcher.failure = SecurityException()
        vm.navigate()
        assertEquals(NavigationUnavailable(loaded.summary), vm.uiState.value)
    }

    // The driver asked to navigate: details arriving afterwards must not replace the message.
    @Test
    fun detailsArrivingAfterAFailedHandOffKeepTheMessage() = runTest {
        places.details = slowDetails(1_000)
        launcher.failure = IllegalStateException()
        val vm = viewModel()
        runCurrent()
        vm.navigate()
        advanceTimeBy(1_001)
        assertEquals(NavigationUnavailable(place), vm.uiState.value)
    }

    // DN-UX-005: today's closing time comes from the schedule, at the time the details arrive.
    @Test
    fun theClosingTimeComesWithTheDetails() = runTest {
        val schedule = OpeningPeriod(setOf(DayOfWeek.MONDAY), LocalTime.of(7, 0), Duration.ofHours(11))
        places.details = { loaded.copy(openingPeriods = listOf(schedule)) }
        val vm = viewModel()
        runCurrent()
        assertEquals(Closing.At(LocalTime.of(18, 0)), (vm.uiState.value as Content).closing)
    }

    @Test
    fun aCarParkNearbyIsAddedToTheDetails() = runTest {
        places.details = { loaded }
        places.carPark = { true }
        val vm = viewModel()
        runCurrent()
        assertEquals(Content(loaded, carParkNearby = true), vm.uiState.value)
    }

    // Review Focus 3: the details show as they arrive; the tile follows.
    @Test
    fun aSlowCarParkCheckDoesNotHoldBackTheDetails() = runTest {
        places.details = { loaded }
        places.carPark = {
            delay(5_000)
            true
        }
        val vm = viewModel()
        runCurrent()
        assertEquals(Content(loaded), vm.uiState.value)
        advanceTimeBy(5_001)
        assertEquals(Content(loaded, carParkNearby = true), vm.uiState.value)
    }

    // The fallback keeps the summary; a car park found anyway is not shown there.
    @Test
    fun theCarParkNeedsTheDetails() = runTest {
        places.details = { throw ProviderFailure() }
        places.carPark = { true }
        val vm = viewModel()
        runCurrent()
        assertEquals(SummaryOnly(place), vm.uiState.value)
    }

    // DN-UX-006: Save keeps the place as Details knows it (the refreshed summary), with the row's category and
    // enrichment.
    @Test
    fun saveKeepsTheRefreshedPlaceWithItsCategoryAndEnrichment() = runTest {
        places.details = { loaded }
        val vm = watched()
        vm.toggleSave()
        runCurrent()
        assertEquals(listOf(SavedPlace(loaded.summary, DiscoveryCategory.COFFEE, rowEnrichment, 1)), saved.places.value)
        assertEquals(true, vm.isSaved.value)
    }

    @Test
    fun saveAgainRemovesIt() = runTest {
        places.details = { loaded }
        val vm = watched()
        vm.toggleSave()
        runCurrent()
        vm.toggleSave()
        runCurrent()
        assertEquals(emptyList<SavedPlace>(), saved.places.value)
        assertEquals(false, vm.isSaved.value)
    }

    // Two quick taps toggle twice, in order: saved, then removed, never a duplicate row.
    @Test
    fun twoQuickTapsSaveThenRemove() = runTest {
        places.details = { loaded }
        val vm = watched()
        vm.toggleSave()
        vm.toggleSave()
        runCurrent()
        assertEquals(emptyList<SavedPlace>(), saved.places.value)
    }

    // A tap before the database first answers still removes a place that is already saved.
    @Test
    fun aTapBeforeTheDatabaseAnswersRemovesASavedPlace() = runTest {
        places.details = { loaded }
        saved.save(place, DiscoveryCategory.COFFEE, null)
        val vm = viewModel() // nothing watches isSaved yet, so it still reads false
        vm.toggleSave()
        runCurrent()
        assertEquals(emptyList<SavedPlace>(), saved.places.value)
    }
}
