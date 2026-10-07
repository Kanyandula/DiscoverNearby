package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary

/**
 * A HERE item as a domain place, or null without an ID, a name or a full position. `/browse` has no rating, parking,
 * toilets or travel time (ADR-001 Field availability), so those stay unknown; unknown data is neutral.
 */
internal fun HereItem.toSummary(): PlaceSummary? {
    val placeId = id?.takeIf { it.isNotBlank() }
    val name = title?.trim()?.takeIf { it.isNotEmpty() }
    val location = position?.run { if (lat != null && lng != null) GeoPoint(lat, lng) else null }
    if (placeId == null || name == null || location == null) return null
    val kinds = categories.mapNotNull { kindFor(it.id) }
    return PlaceSummary(
        id = placeId,
        name = name,
        location = location,
        placeKinds = kinds.toSet(),
        primaryKind = categories.firstOrNull { it.primary }?.let { kindFor(it.id) } ?: kinds.firstOrNull(),
        attributes = emptySet(),
        rating = null,
        ratingCount = null,
        // HERE can send one hours entry per category: any open entry means open, and null means unknown.
        isOpenNow = openingHours.mapNotNull { it.isOpen }.let { if (true in it) true else it.firstOrNull() },
        travelTimeMinutes = null,
    )
}

/** Details add the opening-hours text. Attribution stays null until HERE's brand guidance is read (ADR-001 V6a). */
internal fun HereItem.toDetails(): PlaceDetails? = toSummary()?.let { summary ->
    PlaceDetails(
        summary = summary,
        openingSummary = openingHours.flatMap { it.text }.joinToString("; ").ifEmpty { null },
        attribution = null,
    )
}
