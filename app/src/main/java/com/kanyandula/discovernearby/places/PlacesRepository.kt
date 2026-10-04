package com.kanyandula.discovernearby.places

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary

/** Provider-neutral POI access (docs/03 §8). Implementations throw [PlacesException] subtypes on failure. */
interface PlacesRepository {
    suspend fun searchNearby(
        origin: GeoPoint,
        category: DiscoveryCategory,
        radiusMeters: Int,
    ): List<PlaceSummary>

    suspend fun getPlaceDetails(
        placeId: String,
    ): PlaceDetails
}
