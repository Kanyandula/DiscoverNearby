package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary

// The holder HERE's brand guidance names in its notice, "© 20XX HERE" (ADR-001 V6a).
private const val HERE_ATTRIBUTION = "HERE"

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
    val primary = categories.firstOrNull { it.primary }
    return PlaceSummary(
        id = placeId,
        name = name,
        location = location,
        placeKinds = kinds.toSet(),
        // HERE's own primary decides; without one flagged, the first mapped kind leads. A kind that only rules a place
        // out never leads (RULE_OUT_KINDS).
        primaryKind = (if (primary != null) kindFor(primary.id) else kinds.firstOrNull { it !in RULE_OUT_KINDS })
            ?.takeUnless { it in RULE_OUT_KINDS },
        attributes = emptySet(),
        rating = null,
        ratingCount = null,
        // HERE can send one hours entry per category: any open entry means open, and null means unknown.
        isOpenNow = openingHours.mapNotNull { it.isOpen }.let { if (true in it) true else it.firstOrNull() },
        travelTimeMinutes = null,
        attribution = HERE_ATTRIBUTION,
        phones = contacts.flatMap { it.phone }.mapNotNull { phoneDigits(it.value) }.toSet(),
    )
}

/** A phone number as digits, with a leading + kept; null without any digit. */
private fun phoneDigits(value: String): String? {
    val digits = value.filter { it in '0'..'9' }
    return when {
        digits.isEmpty() -> null
        value.trimStart().startsWith('+') -> "+$digits"
        else -> digits
    }
}

/** Details add the opening hours, as text and as periods; the copyright notice comes with the summary. */
internal fun HereItem.toDetails(): PlaceDetails? = toSummary()?.let { summary ->
    PlaceDetails(
        summary = summary,
        openingSummary = openingHours.flatMap { it.text }.joinToString("; ").ifEmpty { null },
        openingPeriods = openingHours.flatMap { it.structured }.mapNotNull { it.toPeriod() },
    )
}
