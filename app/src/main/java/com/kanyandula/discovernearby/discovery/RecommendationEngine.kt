package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.model.attributeTypes

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
        val cap = config.maxPerKind ?: Int.MAX_VALUE
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
                kept < cap
            }
            .map { it.recommendation }
    }

    /** The place scored, with the kind it counts as for diversity; null when its category match is below the floor. */
    private fun score(place: PlaceSummary, context: DiscoveryContext, config: CategoryConfig): Scored? {
        // The best target kind; one that isn't the primary counts a share. Sorted, so ties pick the same kind.
        val (kind, match) = place.placeKinds.sorted()
            .map { it to (config.kindWeights[it] ?: 0) * if (it == place.primaryKind) 1.0 else SECONDARY_KIND_SHARE }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= SCORE_FLOOR }
            ?: return null
        val distance = context.origin.distanceMetersTo(place.location)
        val nearness = NEARNESS_WEIGHT * (1 - minOf(distance, config.radiusMeters).toDouble() / config.radiusMeters)
        val rating = if (place.rating != null && place.rating >= HIGH_RATING) HIGH_RATING_BONUS else 0.0
        val amenities = place.attributeTypes().sumOf { config.amenityWeights[it] ?: 0 }
        val open = if (place.isOpenNow == true) OPEN_NOW_BONUS else 0.0
        val recommendation = Recommendation(
            place = place,
            score = match + nearness + rating + amenities + open,
            distanceMeters = distance,
            travelTimeMinutes = place.travelTimeMinutes,
            minutesAhead = null,
            detourMinutes = null,
        )
        // Without a known primary kind, the kind that matched stands in, so such places share its cap.
        return Scored(recommendation, kind = place.primaryKind ?: kind)
    }

    private data class Scored(val recommendation: Recommendation, val kind: String)
}
