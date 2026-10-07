package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC
import com.kanyandula.discovernearby.model.AttributeType
import com.kanyandula.discovernearby.model.AttributeType.CAFE
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.AttributeType.TOILETS

/** Up to this many recommendations are shown, before the driving list limit (docs/03 §6). */
const val DESIRED_RECOMMENDATIONS = 5

// docs/03 §10 signals every category shares; tune them here. The per-category ones are in CategoryConfig.

/** A matching kind that isn't the place's primary kind counts this share of its weight. */
const val SECONDARY_KIND_SHARE = 0.5

/** A category match below this is dropped, never shown as filler. */
const val SCORE_FLOOR = 10.0

/** Nearness adds up to this: all of it at the origin, none at the category's radius or beyond. */
const val NEARNESS_WEIGHT = 20.0

/** Added when the provider says the place is open now; unknown or closed adds nothing. */
const val OPEN_NOW_BONUS = 5.0

/** A rating at or above [HIGH_RATING] adds [HIGH_RATING_BONUS]; no rating adds nothing. */
const val HIGH_RATING = 4.5
const val HIGH_RATING_BONUS = 5.0

/** Per-category tuning as data, not code branches (docs/03 §2, §10). */
data class CategoryConfig(
    val radiusMeters: Int,
    val desiredResults: Int = DESIRED_RECOMMENDATIONS,
    /** The category match each target kind gives a place whose primary kind it is. */
    val kindWeights: Map<String, Int>,
    /** Added once per known amenity (PROVIDED or DERIVED); an absent one is unknown and adds nothing. */
    val amenityWeights: Map<AttributeType, Int> = emptyMap(),
    /** Time-sensitive: a place known to be closed now is dropped, not penalised (Product Lead, 2026-10-07). */
    val excludeClosed: Boolean,
    /**
     * At most this many results share a primary kind (or, without one, the kind they matched); null for no cap
     * (Product Lead, 2026-10-07).
     */
    val maxPerKind: Int?,
)

private const val STRONG_MATCH = 30
private const val WEAK_MATCH = 15
private const val DIVERSITY_CAP = 2

private fun strong(vararg kinds: String) = kinds.associateWith { STRONG_MATCH }

// Radii: docs/03 §9 illustrative values. Kinds: docs/03 §9, normalised to snake_case. Weights: docs/03 §10's Family
// example; amenities: docs/01 §10's optional signals per category.
val CategoryConfigs: Map<DiscoveryCategory, CategoryConfig> = mapOf(
    COFFEE to CategoryConfig(
        radiusMeters = 5_000,
        kindWeights = strong("cafe", "coffee_shop"),
        amenityWeights = mapOf(PARKING to 5),
        excludeClosed = true,
        maxPerKind = null,
    ),
    FOOD to CategoryConfig(
        radiusMeters = 5_000,
        kindWeights = strong("restaurant", "fast_food", "takeaway"),
        amenityWeights = mapOf(PARKING to 5),
        excludeClosed = true,
        maxPerKind = null,
    ),
    OUTDOORS to CategoryConfig(
        radiusMeters = 20_000,
        kindWeights = strong("park", "trail", "forest", "beach", "hiking_area", "outdoor_attraction"),
        excludeClosed = false,
        maxPerKind = DIVERSITY_CAP,
    ),
    FAMILY to CategoryConfig(
        radiusMeters = 15_000,
        kindWeights = strong("playground", "zoo", "aquarium", "family_attraction") + ("park" to WEAK_MATCH),
        amenityWeights = mapOf(TOILETS to 10, PARKING to 10, CAFE to 5),
        excludeClosed = true,
        maxPerKind = DIVERSITY_CAP,
    ),
    SCENIC to CategoryConfig(
        radiusMeters = 30_000,
        // Scenic is for looking at: a landmark is a weaker match here than in Explore.
        kindWeights = strong("viewpoint", "scenic_spot", "coastal_overlook", "waterfall", "natural_attraction") +
            ("landmark" to WEAK_MATCH),
        excludeClosed = false,
        // HERE files every Scenic Point as one kind, so a cap would drop real views (Product Lead, 2026-10-07).
        maxPerKind = null,
    ),
    EXPLORE to CategoryConfig(
        radiusMeters = 15_000,
        kindWeights = strong("tourist_attraction", "museum", "landmark", "heritage_site"),
        excludeClosed = true,
        maxPerKind = DIVERSITY_CAP,
    ),
)
