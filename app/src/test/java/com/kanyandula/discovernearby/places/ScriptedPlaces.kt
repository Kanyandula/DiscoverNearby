package com.kanyandula.discovernearby.places

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary

/** Answers every search with [reply]; records what it was asked and how many searches are still running. */
class ScriptedPlaces(var reply: suspend () -> List<PlaceSummary> = { emptyList() }) : PlacesRepository {
    var searches = 0
        private set
    var running = 0
        private set
    var lastRadius: Int? = null
        private set

    override suspend fun searchNearby(
        origin: GeoPoint,
        category: DiscoveryCategory,
        radiusMeters: Int,
    ): List<PlaceSummary> {
        searches++
        lastRadius = radiusMeters
        running++
        try {
            return reply()
        } finally {
            running--
        }
    }

    override suspend fun getPlaceDetails(placeId: String): PlaceDetails = error("not used")
}
