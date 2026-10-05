package com.kanyandula.discovernearby.ui.screens

import com.kanyandula.discovernearby.MainDispatcherRule
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.PROVIDER_TIMEOUT_MILLIS
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.navigation.fake.FakeNavigationLauncher
import com.kanyandula.discovernearby.places.ProviderFailure
import com.kanyandula.discovernearby.places.ScriptedPlaces
import com.kanyandula.discovernearby.places.fake.SLOW_DELAY_MILLIS
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Content
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Loading
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.NavigationUnavailable
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.SummaryOnly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy
class PlaceDetailsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val places = ScriptedPlaces()
    private val launcher = FakeNavigationLauncher()
    private val place = testPlace("p1", "cafe", metersNorth = 500)
    private val loaded = PlaceDetails(place.copy(rating = 4.6), openingSummary = "Open until 18:00", attribution = null)

    private fun viewModel() = PlaceDetailsViewModel(
        place = place,
        discover = DiscoverUseCase(places, FakeLocationProvider(), BasicRecommendationEngine()),
        navigation = launcher,
    )

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
}
