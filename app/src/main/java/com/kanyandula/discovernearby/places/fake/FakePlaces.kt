package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC
import com.kanyandula.discovernearby.model.AttributeSource
import com.kanyandula.discovernearby.model.AttributeSource.DERIVED
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType
import com.kanyandula.discovernearby.model.AttributeType.BEACH
import com.kanyandula.discovernearby.model.AttributeType.CAFE
import com.kanyandula.discovernearby.model.AttributeType.DRIVE_THROUGH
import com.kanyandula.discovernearby.model.AttributeType.FAMILY_FRIENDLY
import com.kanyandula.discovernearby.model.AttributeType.MUSEUM
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.AttributeType.PLAYGROUND
import com.kanyandula.discovernearby.model.AttributeType.TOILETS
import com.kanyandula.discovernearby.model.AttributeType.TRAILS
import com.kanyandula.discovernearby.model.AttributeType.VIEWPOINT
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.PlaceSummary

/** One fictional place, placed at an offset (degrees) from a test location. */
private data class Template(
    val name: String,
    val kinds: Set<String>,
    val dLat: Double,
    val dLng: Double,
    val attributes: Set<PlaceAttribute> = emptySet(),
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val isOpenNow: Boolean? = null,
)

private fun attrs(vararg pairs: Pair<AttributeType, AttributeSource>) =
    pairs.map { (type, source) -> PlaceAttribute(type, source) }.toSet()

// Mixes known and unknown ratings and open states, one known-closed place for time-sensitive categories,
// and PROVIDED vs DERIVED attributes, so later ranking meets every capability case (docs/03 §10).
private val Templates: Map<DiscoveryCategory, List<Template>> = mapOf(
    COFFEE to listOf(
        Template("Harbour Roasters", setOf("cafe"), 0.004, 0.003, attrs(PARKING to PROVIDED), 4.6, 212, true),
        Template(
            "The Daily Grind", setOf("coffee_shop"), -0.008, 0.010,
            attrs(DRIVE_THROUGH to PROVIDED), 4.4, 128, true,
        ),
        Template("Brew & Bloom", setOf("cafe"), 0.015, -0.012),
        Template("Corner Café", setOf("cafe"), 0.002, -0.004, rating = 4.1, ratingCount = 40, isOpenNow = false),
        Template(
            "Station Espresso", setOf("coffee_shop"), 0.030, 0.020,
            rating = 3.9, ratingCount = 15, isOpenNow = true,
        ),
    ),
    FOOD to listOf(
        Template("Seaview Kitchen", setOf("restaurant"), 0.006, -0.005, attrs(PARKING to PROVIDED), 4.5, 300, true),
        Template("Fish & Chips Co.", setOf("takeaway"), 0.012, 0.008, rating = 4.2, ratingCount = 90, isOpenNow = true),
        Template("Olive Tree Bistro", setOf("restaurant"), -0.010, -0.015),
        Template("Burger Barn", setOf("fast_food"), 0.020, 0.025, attrs(DRIVE_THROUGH to PROVIDED), 3.8, 60, false),
    ),
    OUTDOORS to listOf(
        Template("Riverside Park", setOf("park"), 0.020, 0.030, attrs(PARKING to PROVIDED), 4.5, 400, true),
        Template("Glen Forest Walk", setOf("forest", "trail"), 0.060, -0.040, attrs(TRAILS to DERIVED), 4.7, 150),
        Template("North Beach", setOf("beach"), -0.050, 0.070, attrs(BEACH to DERIVED, PARKING to PROVIDED), 4.3, 220),
        Template("Hilltop Trail", setOf("trail", "hiking_area"), 0.100, 0.050, attrs(TRAILS to DERIVED)),
    ),
    FAMILY to listOf(
        Template(
            "Adventure Playground", setOf("playground"), 0.010, 0.012,
            attrs(PLAYGROUND to DERIVED, TOILETS to PROVIDED, PARKING to PROVIDED), 4.6, 180, true,
        ),
        Template(
            "Seal Rescue Aquarium", setOf("aquarium"), 0.045, -0.030,
            attrs(FAMILY_FRIENDLY to DERIVED, CAFE to PROVIDED), 4.4, 520, true,
        ),
        Template("Meadow Park", setOf("park", "playground"), -0.030, 0.040, attrs(PLAYGROUND to DERIVED), 4.5, 400),
        Template("Little Acres Farm", setOf("family_attraction"), 0.070, 0.060, attrs(FAMILY_FRIENDLY to DERIVED)),
    ),
    SCENIC to listOf(
        Template(
            "Cliff Viewpoint", setOf("viewpoint"), 0.080, 0.090,
            attrs(VIEWPOINT to DERIVED, PARKING to PROVIDED), 4.8, 260,
        ),
        Template(
            "Waterfall Glen", setOf("waterfall", "natural_attraction"), 0.150, -0.100,
            rating = 4.7, ratingCount = 310,
        ),
        Template("Harbour Lighthouse", setOf("landmark", "coastal_overlook"), -0.120, 0.150),
    ),
    EXPLORE to listOf(
        Template("County Museum", setOf("museum"), 0.008, 0.010, attrs(MUSEUM to DERIVED), 4.5, 140, true),
        Template("Old Abbey", setOf("heritage_site"), 0.040, -0.035, rating = 4.3, ratingCount = 75),
        Template("Market Square", setOf("tourist_attraction"), -0.050, 0.030),
    ),
)

/**
 * The fake places for [category] around [location]. Offsets rotate by location so the nearest place
 * differs per town, and names carry the town so a location change is visible (docs/04 Scenario B).
 */
internal fun fakePlaces(location: TestLocation, category: DiscoveryCategory): List<PlaceSummary> {
    val templates = Templates.getValue(category)
    return templates.mapIndexed { i, template ->
        val offset = templates[(i + location.ordinal) % templates.size]
        PlaceSummary(
            id = "${location.name.lowercase()}-${category.name.lowercase()}-$i",
            name = "${template.name}, ${location.label}",
            location = GeoPoint(location.point.lat + offset.dLat, location.point.lng + offset.dLng),
            placeKinds = template.kinds,
            primaryKind = template.kinds.first(),
            attributes = template.attributes,
            rating = template.rating,
            ratingCount = template.ratingCount,
            isOpenNow = template.isOpenNow,
            travelTimeMinutes = null,
        )
    }
}
