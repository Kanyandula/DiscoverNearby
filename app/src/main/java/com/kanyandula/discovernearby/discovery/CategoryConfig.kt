package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC

/** Up to this many recommendations are shown, before the driving list limit (docs/03 §6). */
const val DESIRED_RECOMMENDATIONS = 5

/** Per-category tuning as data, not code branches (docs/03 §2). DN-M2-001 adds ranking weights here. */
data class CategoryConfig(
    val radiusMeters: Int,
    val desiredResults: Int,
    val targetKinds: Set<String>,
)

// Radii: docs/03 §9 illustrative values. Kinds: docs/03 §9, normalised to snake_case.
val CategoryConfigs: Map<DiscoveryCategory, CategoryConfig> = mapOf(
    COFFEE to CategoryConfig(5_000, DESIRED_RECOMMENDATIONS, setOf("cafe", "coffee_shop")),
    FOOD to CategoryConfig(5_000, DESIRED_RECOMMENDATIONS, setOf("restaurant", "fast_food", "takeaway")),
    OUTDOORS to CategoryConfig(
        20_000,
        DESIRED_RECOMMENDATIONS,
        setOf("park", "trail", "forest", "beach", "hiking_area", "outdoor_attraction"),
    ),
    FAMILY to CategoryConfig(
        15_000,
        DESIRED_RECOMMENDATIONS,
        setOf("playground", "zoo", "aquarium", "family_attraction", "park"),
    ),
    SCENIC to CategoryConfig(
        30_000,
        DESIRED_RECOMMENDATIONS,
        setOf("viewpoint", "scenic_spot", "coastal_overlook", "landmark", "waterfall", "natural_attraction"),
    ),
    EXPLORE to CategoryConfig(
        15_000,
        DESIRED_RECOMMENDATIONS,
        setOf("tourist_attraction", "museum", "landmark", "heritage_site"),
    ),
)
