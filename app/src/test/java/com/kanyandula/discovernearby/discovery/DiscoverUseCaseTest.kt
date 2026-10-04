package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.ScriptedPlaces
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import com.kanyandula.discovernearby.places.fake.FakeScenario
import com.kanyandula.discovernearby.places.fake.SLOW_CATEGORY
import com.kanyandula.discovernearby.places.fake.SLOW_DELAY_MILLIS
import com.kanyandula.discovernearby.places.fake.TestLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class) // currentTime
class DiscoverUseCaseTest {

    private fun useCase(
        places: PlacesRepository = FakePlacesRepository(),
        location: LocationResult = LocationResult.Available(TestLocation.GREYSTONES.point),
    ) = DiscoverUseCase(places, FakeLocationProvider(location), BasicRecommendationEngine())

    private fun failing(scenario: FakeScenario) = useCase(FakePlacesRepository(scenario))

    // The slow fake must outlast the timeout, or Scenario O never times out.
    @Test
    fun timeoutStaysBelowTheSlowFake() {
        assertTrue(PROVIDER_TIMEOUT_MILLIS < SLOW_DELAY_MILLIS)
    }

    @Test
    fun ranksTheCategoryAroundTheCurrentLocation() = runTest {
        val result = useCase().invoke(7, FAMILY) as DiscoverResult.Success
        val expected = DiscoveryContext(7, TestLocation.GREYSTONES.point, FAMILY, result.context.createdAtMillis)
        assertEquals(expected, result.context)
        val distances = result.recommendations.map { it.distanceMeters }
        assertTrue(distances.isNotEmpty())
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun searchesTheCategoryRadius() = runTest {
        val places = ScriptedPlaces()
        useCase(places).invoke(1, OUTDOORS)
        assertEquals(CategoryConfigs.getValue(OUTDOORS).radiusMeters, places.lastRadius)
    }

    @Test
    fun noMatchesIsAnEmptySuccess() = runTest {
        val result = failing(FakeScenario.EMPTY).invoke(1, COFFEE) as DiscoverResult.Success
        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun providerFailuresKeepTheirCause() = runTest {
        val network = failing(FakeScenario.NETWORK_FAILURE).invoke(1, COFFEE)
        assertEquals(DiscoverResult.Failure(DiscoverError.NetworkUnavailable), network)
        val provider = failing(FakeScenario.PROVIDER_FAILURE).invoke(1, COFFEE)
        assertEquals(DiscoverResult.Failure(DiscoverError.ProviderFailure), provider)
    }

    @Test
    fun slowProviderTimesOut() = runTest {
        val result = failing(FakeScenario.SLOW).invoke(1, SLOW_CATEGORY)
        assertEquals(DiscoverResult.Failure(DiscoverError.Timeout), result)
        assertEquals(PROVIDER_TIMEOUT_MILLIS, currentTime)
    }

    // Location is read first and the provider is never asked without one (docs/03 §13).
    @Test
    fun locationProblemsNeverReachTheProvider() = runTest {
        val places = ScriptedPlaces()
        val permission = useCase(places, LocationResult.PermissionMissing).invoke(1, COFFEE)
        assertEquals(DiscoverResult.PermissionRequired, permission)
        val unavailable = useCase(places, LocationResult.Unavailable).invoke(1, COFFEE)
        assertEquals(DiscoverResult.Failure(DiscoverError.LocationUnavailable), unavailable)
        assertEquals(0, places.searches)
    }
}
