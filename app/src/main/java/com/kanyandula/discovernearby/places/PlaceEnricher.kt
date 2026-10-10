package com.kanyandula.discovernearby.places

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary

/**
 * Adds a second provider's photo and rating to a place already ranked and on screen (DN-UX-004). Null when the
 * provider has nothing for it or can't be reached: the row keeps what it had, and nothing is retried.
 */
fun interface PlaceEnricher {
    suspend fun enrich(place: PlaceSummary, category: DiscoveryCategory): PlaceEnrichment?
}
