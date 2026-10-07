package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation

/** Turns provider results into ranked recommendations (docs/03 §10). Pure Kotlin, no display limit. */
interface RecommendationEngine {
    fun rank(places: List<PlaceSummary>, context: DiscoveryContext): List<Recommendation>
}

/**
 * docs/03 §10 scoring, every weight from [CategoryConfigs]: category match + nearness + rating + amenities + open
 * now. Time-sensitive categories drop known-closed places; unknown data adds nothing; a category match below
 * [SCORE_FLOOR] is dropped; at most [CategoryConfig.maxPerKind] results share a kind. Ties: nearer, then id.
 */
class BasicRecommendationEngine : RecommendationEngine {

    override fun rank(places: List<PlaceSummary>, context: DiscoveryContext): List<Recommendation> {
        val config = CategoryConfigs.getValue(context.category)
        val keptPerKind = mutableMapOf<String, Int>()
        return places
            .filterNot { config.excludeClosed && it.isOpenNow == false }
            .mapNotNull { score(it, context, config) }
            .sortedWith(
                compareByDescending<Scored> { it.recommendation.score }
                    .thenBy { it.recommendation.distanceMeters }
                    .thenBy { it.recommendation.place.id },
            )
            .distinctBy { it.recommendation.place.id } // the list keys rows by id; a repeated place would crash it
            .filter { scored ->
                // Light diversity, in ranked order, so the strongest of each kind stay.
                val kept = keptPerKind.getOrDefault(scored.kind, 0)
                keptPerKind[scored.kind] = kept + 1
                config.maxPerKind == null || kept < config.maxPerKind
            }
            .map { it.recommendation }
    }

    /** The place scored, with the kind it counts as for diversity; null when its category match is below the floor. */
    private fun score(place: PlaceSummary, context: DiscoveryContext, config: CategoryConfig): Scored? {
        // Sorted, so equal matches settle on the same kind every time.
        val best = place.placeKinds.sorted()
            .map { it to categoryMatch(it, place.primaryKind, config) }
            .maxByOrNull { it.second }
        if (best == null || best.second < SCORE_FLOOR) return null
        val distance = context.origin.distanceMetersTo(place.location)
        val nearness = NEARNESS_WEIGHT * (1 - minOf(distance, config.radiusMeters).toDouble() / config.radiusMeters)
        val rating = if (place.rating != null && place.rating >= HIGH_RATING) HIGH_RATING_BONUS else 0.0
        val amenities = place.attributes.map { it.type }.toSet().sumOf { config.amenityWeights[it] ?: 0 }
        val open = if (place.isOpenNow == true) OPEN_NOW_BONUS else 0.0
        val recommendation = Recommendation(
            place = place,
            score = best.second + nearness + rating + amenities + open,
            distanceMeters = distance,
            travelTimeMinutes = place.travelTimeMinutes,
            minutesAhead = null,
            detourMinutes = null,
        )
        // Without a known primary kind, the kind that matched stands in, so such places share its cap.
        return Scored(recommendation, kind = place.primaryKind ?: best.first)
    }

    private fun categoryMatch(kind: String, primaryKind: String?, config: CategoryConfig): Double {
        val weight = config.kindWeights[kind] ?: 0
        return if (kind == primaryKind) weight.toDouble() else weight * SECONDARY_KIND_SHARE
    }

    private data class Scored(val recommendation: Recommendation, val kind: String)
}
