package com.kanyandula.discovernearby.model

import kotlinx.serialization.Serializable

/**
 * What a second provider adds to a place on screen (DN-UX-004, ADR-003): a photo and a rating, for display only.
 * Ranking never sees it, and nothing here is stored beyond the screen that shows it (the provider's caching policy).
 */
@Serializable
data class PlaceEnrichment(
    val source: String, // the provider whose content this is and whose mark goes with it, e.g. "Tripadvisor"
    val photo: PlacePhoto? = null,
    val rating: ProviderRating? = null,
)

/** A photo to show as is; [credit] names who took it, when the provider says. */
@Serializable
data class PlacePhoto(val url: String, val credit: String? = null)

/** The provider's own rating: its [iconUrl] is the provider's rating graphic, which its display rules require. */
@Serializable
data class ProviderRating(val value: Double, val count: Int? = null, val iconUrl: String? = null)
