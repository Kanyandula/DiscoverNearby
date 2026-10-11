package com.kanyandula.discovernearby.ui.screens

import com.kanyandula.discovernearby.MainDispatcherRule
import com.kanyandula.discovernearby.car.DrivingState
import com.kanyandula.discovernearby.car.FakeDrivingRestrictions
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.ORIGIN
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.saved.FakeSavedPlaces
import com.kanyandula.discovernearby.ui.screens.SavedUiState.Content
import com.kanyandula.discovernearby.ui.screens.SavedUiState.Empty
import com.kanyandula.discovernearby.ui.screens.SavedUiState.Loading
import com.kanyandula.discovernearby.ui.screens.SavedUiState.ParkToSee
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
class SavedViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val saved = FakeSavedPlaces()
    private val location = FakeLocationProvider(LocationResult.Available(ORIGIN))
    private val restrictions = FakeDrivingRestrictions()

    /** A Saved screen collecting its state, the location read as far as it can. */
    private fun TestScope.collected(provider: LocationProvider = location) =
        SavedViewModel(saved, provider, restrictions).also {
            backgroundScope.launch { it.uiState.collect {} }
            runCurrent()
        }

    /** Saves in order, so the last one is the newest. */
    private suspend fun save(vararg metersNorth: Int) =
        metersNorth.forEach { saved.save(testPlace("m$it", "cafe", metersNorth = it), COFFEE, null) }

    private val SavedViewModel.shown get() =
        (uiState.value as Content).rows.map { it.place.summary.id to it.distanceMeters }

    @Test
    fun emptyWithNothingSaved() = runTest {
        assertEquals(Empty, collected().uiState.value)
    }

    @Test
    fun loadingWhileLocating() = runTest {
        save(100)
        val slow = object : LocationProvider {
            override suspend fun currentLocation(): LocationResult {
                delay(1_000)
                return LocationResult.Available(ORIGIN)
            }
        }
        val vm = collected(slow)
        assertEquals(Loading, vm.uiState.value)
        advanceTimeBy(1_001)
        assertEquals(listOf("m100" to 100), vm.shown)
    }

    @Test
    fun nearestFirstFromWhereTheCarIsNow() = runTest {
        save(900, 100, 500)
        assertEquals(listOf("m100" to 100, "m500" to 500, "m900" to 900), collected().shown)
    }

    // Without a location, the newest first and no distances; Saved never asks for permission.
    @Test
    fun withoutALocationNewestFirstAndNoDistances() = runTest {
        location.result = LocationResult.PermissionMissing
        save(100, 900)
        assertEquals(listOf("m900" to null, "m100" to null), collected().shown)
    }

    // docs/03 §6: the driving limit trims the list.
    @Test
    fun theDrivingLimitTrimsTheList() = runTest {
        save(100, 200, 300)
        restrictions.state.value = DrivingState(distractionOptimizationRequired = true, listLimit = 2)
        assertEquals(listOf("m100" to 100, "m200" to 200), collected().shown)
    }

    @Test
    fun aLimitOfZeroAsksToPark() = runTest {
        save(100)
        restrictions.state.value = DrivingState(distractionOptimizationRequired = true, listLimit = 0)
        assertEquals(ParkToSee, collected().uiState.value)
    }

    // Removed on Place Details, gone here on Back, without a reload.
    @Test
    fun aPlaceRemovedElsewhereLeavesTheList() = runTest {
        save(100, 200)
        val vm = collected()
        saved.remove("m100")
        runCurrent()
        assertEquals(listOf("m200" to 200), vm.shown)
    }
}
