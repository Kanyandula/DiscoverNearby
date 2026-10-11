package com.kanyandula.discovernearby.model

import kotlinx.serialization.Serializable

/**
 * Tripadvisor's photo, rating, price level and description for a place on screen (DN-UX-004, DN-UX-005, ADR-003), for
 * display only: ranking never sees it. Saved places keep it on the device (ADR-004).
 */
@Serializable
data class PlaceEnrichment(
    val photoUrl: String? = null,
    val rating: ProviderRating? = null,
    val priceLevel: PriceLevel? = null,
    val description: String? = null,
)

/** Tripadvisor's restaurant price levels: Cheap Eats, Mid Range, Fine Dining. */
@Serializable
enum class PriceLevel { CHEAP, MID, FINE }

/** Tripadvisor's rating; [iconUrl] is its rating graphic, which its display rules require. */
@Serializable
data class ProviderRating(val value: Double, val count: Int?, val iconUrl: String)
