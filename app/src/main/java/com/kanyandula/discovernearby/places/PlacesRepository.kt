package com.kanyandula.discovernearby.places

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary

/** How far from a place a car park counts as nearby (DN-UX-005; DN-SP-005 measured 117 of 173 places). */
const val CAR_PARK_RADIUS_METERS = 150

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

    /** Whether a car park lies within [CAR_PARK_RADIUS_METERS] of [location]: a fact about the area, display only. */
    suspend fun hasCarParkNear(location: GeoPoint): Boolean
}
