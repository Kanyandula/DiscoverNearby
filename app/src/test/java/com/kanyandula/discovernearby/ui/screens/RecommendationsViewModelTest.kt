package com.kanyandula.discovernearby.ui.screens

import com.kanyandula.discovernearby.MainDispatcherRule
import com.kanyandula.discovernearby.car.DrivingState
import com.kanyandula.discovernearby.car.FakeDrivingRestrictions
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.CategoryConfigs
import com.kanyandula.discovernearby.discovery.DiscoverError
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.ORIGIN
import com.kanyandula.discovernearby.discovery.PROVIDER_TIMEOUT_MILLIS
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.ProviderFailure
import com.kanyandula.discovernearby.places.ScriptedPlaces
import com.kanyandula.discovernearby.places.fake.SLOW_DELAY_MILLIS
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Content
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Empty
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Error
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Loading
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.PermissionRequired
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

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy
class RecommendationsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val places = ScriptedPlaces()
    private val restrictions = FakeDrivingRestrictions()

    private fun cafes(count: Int) = List(count) { testPlace("p$it", "cafe", metersNorth = 100 * (it + 1)) }

    private fun newViewModel(location: LocationResult = LocationResult.Available(ORIGIN)) = RecommendationsViewModel(
        category = COFFEE,
        discover = DiscoverUseCase(places, FakeLocationProvider(location), BasicRecommendationEngine()),
        drivingRestrictions = restrictions,
    )

    /** A view model whose state the UI is collecting, after its first request ran as far as it can. */
    private fun TestScope.collected(location: LocationResult = LocationResult.Available(ORIGIN)) =
        newViewModel(location).also { vm ->
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
        }

    private val RecommendationsViewModel.shown: List<String>
        get() = (uiState.value as Content).recommendations.map { it.place.id }

    private fun limitTo(listLimit: Int?) {
        restrictions.state.value = DrivingState(distractionOptimizationRequired = listLimit != null, listLimit)
    }

    @Test
    fun loadingUntilTheResultArrives() = runTest {
        places.reply = {
            delay(1_000)
            cafes(2)
        }
        val vm = collected()
        assertEquals(Loading, vm.uiState.value)
        advanceTimeBy(1_001)
        assertEquals(listOf("p0", "p1"), vm.shown)
    }

    @Test
    fun showsTheCategoryCountWithoutALimit() = runTest {
        places.reply = { cafes(7) }
        val vm = collected()
        assertEquals(CategoryConfigs.getValue(COFFEE).desiredResults, vm.shown.size)
        assertEquals(listOf("p0", "p1", "p2", "p3", "p4"), vm.shown)
    }

    @Test
    fun listLimitCapsTheCount() = runTest {
        limitTo(3)
        places.reply = { cafes(7) }
        assertEquals(listOf("p0", "p1", "p2"), collected().shown)
    }

    @Test
    fun retrimsWhenTheDrivingStateChangesWithoutANewRequest() = runTest {
        places.reply = { cafes(7) }
        val vm = collected()
        limitTo(2)
        runCurrent()
        assertEquals(2, vm.shown.size)
        limitTo(null)
        runCurrent()
        assertEquals(5, vm.shown.size)
        assertEquals(1, places.searches)
    }

    @Test
    fun limitChangedWhileLoadingAppliesToTheResult() = runTest {
        places.reply = {
            delay(1_000)
            cafes(7)
        }
        val vm = collected()
        limitTo(2)
        advanceTimeBy(1_001)
        assertEquals(2, vm.shown.size)
    }

    @Test
    fun fewerResultsAreShownWithoutPadding() = runTest {
        places.reply = { cafes(2) }
        assertEquals(2, collected().shown.size)
    }

    @Test
    fun noResultsIsEmpty() = runTest {
        assertEquals(Empty, collected().uiState.value)
    }

    @Test
    fun failuresAreErrorStates() = runTest {
        places.reply = { throw NetworkUnavailable() }
        assertEquals(Error(DiscoverError.NetworkUnavailable), collected().uiState.value)
        places.reply = { throw ProviderFailure() }
        assertEquals(Error(DiscoverError.ProviderFailure), collected().uiState.value)
        assertEquals(Error(DiscoverError.LocationUnavailable), collected(LocationResult.Unavailable).uiState.value)
    }

    @Test
    fun timeoutEndsLoadingWithAnError() = runTest {
        places.reply = {
            delay(SLOW_DELAY_MILLIS)
            cafes(1)
        }
        val vm = collected()
        advanceTimeBy(PROVIDER_TIMEOUT_MILLIS - 1)
        assertEquals(Loading, vm.uiState.value)
        advanceTimeBy(2)
        assertEquals(Error(DiscoverError.Timeout), vm.uiState.value)
    }

    @Test
    fun retryShowsLoadingThenANewRequest() = runTest {
        places.reply = { throw NetworkUnavailable() }
        val vm = collected()
        places.reply = {
            delay(1_000)
            cafes(1)
        }
        vm.retry()
        runCurrent()
        assertEquals(Loading, vm.uiState.value)
        advanceTimeBy(1_001)
        assertEquals(2L, (vm.uiState.value as Content).requestId)
    }

    @Test
    fun retryCancelsTheRequestInFlightAndItsLateResponseIsDropped() = runTest {
        places.reply = {
            delay(1_000)
            cafes(1)
        }
        val vm = collected()
        places.reply = { cafes(3) }
        vm.retry()
        runCurrent()
        assertEquals(0, places.running) // the first search was cancelled, not left to finish
        advanceTimeBy(PROVIDER_TIMEOUT_MILLIS + 1)
        assertEquals(2L, (vm.uiState.value as Content).requestId)
        assertEquals(3, vm.shown.size)
    }

    @Test
    fun permissionRequiredFollowsTheRestrictions() = runTest {
        restrictions.state.value = DrivingState(distractionOptimizationRequired = true, listLimit = null)
        val vm = collected(LocationResult.PermissionMissing)
        assertEquals(PermissionRequired(canRequest = false), vm.uiState.value)
        restrictions.state.value = DrivingState(distractionOptimizationRequired = false, listLimit = null)
        runCurrent()
        assertEquals(PermissionRequired(canRequest = true), vm.uiState.value)
    }

    // DN-M0-010: the restrictions connection exists only while collected, so the screen must be the collector.
    @Test
    fun drivingStateIsCollectedOnlyWhileTheUiCollects() = runTest {
        val vm = newViewModel()
        runCurrent()
        assertEquals(0, restrictions.state.subscriptionCount.value)
        val ui = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(1, restrictions.state.subscriptionCount.value)
        ui.cancel()
        advanceTimeBy(UI_STATE_STOP_TIMEOUT_MILLIS + 1)
        runCurrent()
        assertEquals(0, restrictions.state.subscriptionCount.value)
    }
}
