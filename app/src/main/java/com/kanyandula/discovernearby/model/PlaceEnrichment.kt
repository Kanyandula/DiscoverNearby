package com.kanyandula.discovernearby.model

import kotlinx.serialization.Serializable

/**
 * Tripadvisor's photo and rating for a place on screen (DN-UX-004, ADR-003), for display only. Ranking never sees it,
 * and nothing here is stored beyond the screens that show it (Tripadvisor's caching policy).
 */
@Serializable
data class PlaceEnrichment(val photoUrl: String? = null, val rating: ProviderRating? = null)

/** Tripadvisor's rating; [iconUrl] is its rating graphic, which its display rules require. */
@Serializable
data class ProviderRating(val value: Double, val count: Int?, val iconUrl: String)
