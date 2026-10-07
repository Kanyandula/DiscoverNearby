package com.kanyandula.discovernearby.model

import kotlinx.serialization.Serializable

/** List-level fields only (docs/03 §8): keeps searches small and each field's cost visible. */
@Serializable
data class PlaceSummary(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val placeKinds: Set<String>, // normalised kinds, e.g. "park", "playground"
    val primaryKind: String?,
    val attributes: Set<PlaceAttribute>,
    val rating: Double?, // null if provider lacks it
    val ratingCount: Int?, // null if provider lacks it
    val isOpenNow: Boolean?, // null = unknown
    val travelTimeMinutes: Int?, // only if provider supplies it
    val attribution: String? = null, // the data's copyright holder, e.g. "HERE"; null when no notice is needed
)

/** Each known attribute type once, in the provider's order, whatever its source. Unknown ones are absent. */
fun PlaceSummary.attributeTypes() = attributes.map { it.type }.distinct()

/** Richer fields, fetched only when a place is opened. */
data class PlaceDetails(
    val summary: PlaceSummary,
    val openingSummary: String?,
)
