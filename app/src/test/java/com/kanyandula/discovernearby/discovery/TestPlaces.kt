package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceSummary

val ORIGIN = GeoPoint(lat = 53.0, lng = -6.0)

// One degree of latitude on GeoPoint's sphere, so metersNorth comes back exactly from distanceMetersTo.
private const val METERS_PER_DEGREE_LAT = 111_195.08

/** A place [metersNorth] of [ORIGIN]; every optional field is unknown unless a test sets it. */
fun testPlace(
    id: String,
    vararg kinds: String,
    metersNorth: Int = 100,
    primaryKind: String? = kinds.firstOrNull(),
) = PlaceSummary(
    id = id,
    name = id,
    location = GeoPoint(ORIGIN.lat + metersNorth / METERS_PER_DEGREE_LAT, ORIGIN.lng),
    placeKinds = kinds.toSet(),
    primaryKind = primaryKind,
    attributes = emptySet(),
    rating = null,
    ratingCount = null,
    isOpenNow = null,
    travelTimeMinutes = null,
)

fun testContext(category: DiscoveryCategory) =
    DiscoveryContext(requestId = 1, origin = ORIGIN, category = category, createdAtMillis = 0)
