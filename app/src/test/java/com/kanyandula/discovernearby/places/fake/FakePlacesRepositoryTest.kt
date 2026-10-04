package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.discovery.CategoryConfigs
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.AttributeSource
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.ProviderFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePlacesRepositoryTest {

    private val normal = FakePlacesRepository()

    private suspend fun FakePlacesRepository.at(location: TestLocation, category: DiscoveryCategory) =
        searchNearby(location.point, category, CategoryConfigs.getValue(category).radiusMeters)

    private suspend fun allNormal() = TestLocation.entries.flatMap { l ->
        DiscoveryCategory.entries.flatMap { c -> normal.at(l, c) }
    }

    @Test
    fun normalScenarioCoversEveryCategoryAtEveryTestLocation() = runTest {
        TestLocation.entries.forEach { location ->
            DiscoveryCategory.entries.forEach { category ->
                val places = normal.at(location, category)
                val cell = "$location × $category"
                val config = CategoryConfigs.getValue(category)
                assertTrue("$cell has at least three places", places.size >= 3)
                val withinRadius = places.all { location.point.distanceMetersTo(it.location) <= config.radiusMeters }
                assertTrue("$cell within radius", withinRadius)
                assertTrue("$cell fits the category", places.all { it.placeKinds.any { k -> k in config.targetKinds } })
            }
        }
    }

    @Test
    fun resultsAreDeterministic() = runTest {
        assertEquals(allNormal(), allNormal())
    }

    @Test
    fun idsAreUnique() = runTest {
        val ids = allNormal().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun resultsDifferByLocation() = runTest {
        val greystones = normal.at(TestLocation.GREYSTONES, DiscoveryCategory.COFFEE).map { it.name }.toSet()
        val dublin = normal.at(TestLocation.DUBLIN, DiscoveryCategory.COFFEE).map { it.name }.toSet()
        assertTrue(greystones.intersect(dublin).isEmpty())
    }

    @Test
    fun normalDataExercisesUnknownAndClosedAndProvenance() = runTest {
        val places = allNormal()
        assertTrue("some rating unknown", places.any { it.rating == null })
        assertTrue("some open state unknown", places.any { it.isOpenNow == null })
        assertTrue("some known closed", places.any { it.isOpenNow == false })
        assertTrue("some PROVIDED", places.any { p -> p.attributes.any { it.source == AttributeSource.PROVIDED } })
        assertTrue("some DERIVED", places.any { p -> p.attributes.any { it.source == AttributeSource.DERIVED } })
    }

    @Test
    fun originFarFromTestLocationsReturnsNothing() = runTest {
        assertTrue(normal.searchNearby(GeoPoint(51.5074, -0.1278), DiscoveryCategory.COFFEE, 30_000).isEmpty())
    }

    @Test
    fun radiusLimitsResults() = runTest {
        val origin = TestLocation.GREYSTONES.point
        val wide = normal.searchNearby(origin, DiscoveryCategory.COFFEE, 5_000)
        val narrow = normal.searchNearby(origin, DiscoveryCategory.COFFEE, 500)
        assertTrue(narrow.size < wide.size)
        assertTrue(narrow.all { origin.distanceMetersTo(it.location) <= 500 })
    }

    @Test
    fun emptyScenarioReturnsNothing() = runTest {
        assertTrue(FakePlacesRepository(FakeScenario.EMPTY).at(TestLocation.DUBLIN, DiscoveryCategory.FOOD).isEmpty())
    }

    @Test
    fun sparseScenarioReturnsOneOrTwo() = runTest {
        val size = FakePlacesRepository(FakeScenario.SPARSE).at(TestLocation.DUBLIN, DiscoveryCategory.SCENIC).size
        assertTrue(size in 1..2)
    }

    @Test
    fun nullHeavyScenarioDropsEveryOptionalField() = runTest {
        val places = FakePlacesRepository(FakeScenario.NULL_HEAVY).at(TestLocation.GALWAY, DiscoveryCategory.FAMILY)
        assertTrue(places.isNotEmpty())
        assertTrue(places.all { it.rating == null && it.ratingCount == null && it.isOpenNow == null })
        assertTrue(places.all { it.attributes.isEmpty() && it.primaryKind == null && it.travelTimeMinutes == null })
    }

    // Longer than docs/03 §15's ~8 s provider timeout, so DN-M0-004 can reach Error(Timeout).
    @OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy
    @Test
    fun slowScenarioTakesLongerThanTheProviderTimeout() = runTest {
        val slow = FakePlacesRepository(FakeScenario.SLOW)
        val result = async { slow.at(TestLocation.GREYSTONES, DiscoveryCategory.COFFEE) }
        advanceTimeBy(SLOW_DELAY_MILLIS - 1)
        assertFalse(result.isCompleted)
        advanceTimeBy(2)
        assertTrue(result.isCompleted)
        assertTrue(SLOW_DELAY_MILLIS > 8_000)
    }

    // docs/04 Scenario P: a slow Coffee request is overtaken by a quick Family one.
    @OptIn(ExperimentalCoroutinesApi::class) // runCurrent
    @Test
    fun slowScenarioDelaysCoffeeOnly() = runTest {
        val slow = FakePlacesRepository(FakeScenario.SLOW)
        val family = async { slow.at(TestLocation.GREYSTONES, DiscoveryCategory.FAMILY) }
        runCurrent()
        assertTrue(family.isCompleted)
        assertTrue(family.await().isNotEmpty())
    }

    @Test
    fun failureScenariosThrowTheDomainExceptions() = runTest {
        suspend fun failureOf(scenario: FakeScenario) =
            runCatching { FakePlacesRepository(scenario).at(TestLocation.DUBLIN, DiscoveryCategory.COFFEE) }
                .exceptionOrNull()
        val provider = failureOf(FakeScenario.PROVIDER_FAILURE)
        val network = failureOf(FakeScenario.NETWORK_FAILURE)
        assertTrue(provider is ProviderFailure)
        assertTrue(network is NetworkUnavailable)
    }

    @Test
    fun detailsMatchTheSummary() = runTest {
        val summary = normal.at(TestLocation.GREYSTONES, DiscoveryCategory.FAMILY).first()
        assertEquals(summary, normal.getPlaceDetails(summary.id).summary)
    }

    @Test
    fun detailsFailureScenarioSearchesButFailsDetails() = runTest {
        val repo = FakePlacesRepository(FakeScenario.DETAILS_FAILURE)
        val summary = repo.at(TestLocation.GREYSTONES, DiscoveryCategory.FAMILY).first()
        assertTrue(runCatching { repo.getPlaceDetails(summary.id) }.exceptionOrNull() is ProviderFailure)
    }

    @Test
    fun unknownPlaceIdIsAProviderFailure() = runTest {
        assertTrue(runCatching { normal.getPlaceDetails("no-such-place") }.exceptionOrNull() is ProviderFailure)
    }
}
