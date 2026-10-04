package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation

/** Turns provider results into ranked recommendations (docs/03 §10). Pure Kotlin, no display limit. */
interface RecommendationEngine {
    fun rank(places: List<PlaceSummary>, context: DiscoveryContext): List<Recommendation>
}

/**
 * M0 ranking: keep open-or-unknown places with a kind the category targets; primary-kind matches first, then
 * nearest, then id. ponytail: DN-M2-001 replaces this with docs/03 §10 scoring (quality, amenities,
 * diversity, closed only for time-sensitive categories).
 */
class BasicRecommendationEngine : RecommendationEngine {

    override fun rank(places: List<PlaceSummary>, context: DiscoveryContext): List<Recommendation> {
        val kinds = CategoryConfigs.getValue(context.category).targetKinds
        return places
            .filter { place -> place.isOpenNow != false && place.placeKinds.any { it in kinds } }
            .map { place ->
                Recommendation(
                    place = place,
                    score = if (place.primaryKind in kinds) PRIMARY_MATCH else SECONDARY_MATCH,
                    distanceMeters = context.origin.distanceMetersTo(place.location),
                    travelTimeMinutes = place.travelTimeMinutes,
                    minutesAhead = null,
                    detourMinutes = null,
                )
            }
            .sortedWith(
                compareByDescending<Recommendation> { it.score }.thenBy { it.distanceMeters }.thenBy { it.place.id },
            )
            .distinctBy { it.place.id } // the list keys rows by id; a repeated place would crash it
    }

    private companion object {
        const val PRIMARY_MATCH = 1.0
        const val SECONDARY_MATCH = 0.5
    }
}
