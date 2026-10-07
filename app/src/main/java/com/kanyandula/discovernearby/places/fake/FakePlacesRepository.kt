package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.ProviderFailure
import kotlinx.coroutines.delay

/** Longer than docs/03 §15's ~8 s provider timeout, so the slow case reaches Error(Timeout). */
const val SLOW_DELAY_MILLIS = 10_000L

/** The category [FakeScenario.SLOW] delays; the rest answer at once (docs/04 Scenarios O and P). */
val SLOW_CATEGORY = DiscoveryCategory.COFFEE

/** docs/03 §20 fake cases, plus a details-only failure for the summary-only fallback (docs/04 R). */
enum class FakeScenario { NORMAL, EMPTY, SPARSE, NULL_HEAVY, SLOW, PROVIDER_FAILURE, NETWORK_FAILURE, DETAILS_FAILURE }

/** Deterministic stand-in for the provider until ADR-001 (M1). */
class FakePlacesRepository(
    var scenario: FakeScenario = FakeScenario.NORMAL,
) : PlacesRepository {

    private val byId: Map<String, PlaceSummary> by lazy {
        TestLocation.entries
            .flatMap { location -> DiscoveryCategory.entries.flatMap { fakePlaces(location, it) } }
            .associateBy { it.id }
    }

    override suspend fun searchNearby(
        origin: GeoPoint,
        category: DiscoveryCategory,
        radiusMeters: Int,
    ): List<PlaceSummary> {
        when (scenario) {
            FakeScenario.PROVIDER_FAILURE -> throw ProviderFailure()
            FakeScenario.NETWORK_FAILURE -> throw NetworkUnavailable()
            FakeScenario.SLOW -> if (category == SLOW_CATEGORY) delay(SLOW_DELAY_MILLIS)
            else -> Unit
        }
        val location = TestLocation.nearestTo(origin) ?: return emptyList()
        val places = fakePlaces(location, category).filter { origin.distanceMetersTo(it.location) <= radiusMeters }
        return when (scenario) {
            FakeScenario.EMPTY -> emptyList()
            FakeScenario.SPARSE -> places.take(SPARSE_COUNT)
            FakeScenario.NULL_HEAVY -> places.map { it.withoutOptionalFields() }
            else -> places
        }
    }

    override suspend fun getPlaceDetails(placeId: String): PlaceDetails {
        if (scenario == FakeScenario.DETAILS_FAILURE) throw ProviderFailure("Details unavailable")
        val summary = byId[placeId] ?: throw ProviderFailure("Unknown place $placeId")
        return if (scenario == FakeScenario.NULL_HEAVY) {
            PlaceDetails(summary.withoutOptionalFields(), openingSummary = null)
        } else {
            val opening = if (summary.isOpenNow == true) "Open until 18:00" else null
            PlaceDetails(summary, openingSummary = opening)
        }
    }

    private fun PlaceSummary.withoutOptionalFields() = copy(
        primaryKind = null,
        attributes = emptySet(),
        rating = null,
        ratingCount = null,
        isOpenNow = null,
        travelTimeMinutes = null,
    )

    private companion object {
        const val SPARSE_COUNT = 2
    }
}
