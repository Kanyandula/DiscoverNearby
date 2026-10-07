package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC

/** What `/browse` asks for per category: ADR-001 "Selected provider" mapping (HERE Places category system [H3]). */
internal val HERE_CATEGORIES: Map<DiscoveryCategory, String> = mapOf(
    COFFEE to "100-1100",
    FOOD to "100-1000",
    OUTDOORS to "550-5510-0202,550-5510-0205,350-3522-0239",
    FAMILY to "550-5520-0208,550-5520-0211,550-5520-0207,550-5520-0357,300-3100-0027",
    SCENIC to "550-5510-0242,350-3510-0238",
    EXPLORE to "300-3000-0023,300-3000,300-3100",
)

// A HERE category, or its parent, → the normalised kind CategoryConfigs targets. The category's own entry wins: a
// Coffee Shop (100-1100-0010) is "coffee_shop", any other Coffee-Tea place (100-1100-…) "cafe".
private val KINDS = mapOf(
    "100-1100-0010" to "coffee_shop",
    "100-1100" to "cafe",
    "100-1000" to "restaurant",
    "550-5510-0202" to "park",
    "550-5510-0205" to "beach",
    "350-3522-0239" to "forest",
    "550-5520-0208" to "zoo",
    "550-5520-0207" to "amusement_park",
    "550-5520-0211" to "aquarium",
    "550-5520-0357" to "water_park",
    "300-3100-0027" to "childrens_museum",
    "550-5510-0242" to "viewpoint",
    "350-3510-0238" to "natural_attraction",
    "300-3000-0023" to "tourist_attraction",
    "300-3000" to "landmark",
    "300-3100" to "museum",
)

/** The domain kind for a HERE category ID (its own, else its parent's); null when no category we search covers it. */
internal fun kindFor(hereCategoryId: String): String? =
    KINDS[hereCategoryId] ?: KINDS[hereCategoryId.substringBeforeLast("-")]
