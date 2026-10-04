package com.kanyandula.discovernearby.places

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary

/** Answers searches with [reply] and details with [details]; records what it was asked and what still runs. */
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

    var details: suspend (String) -> PlaceDetails = { error("not used") }

    override suspend fun getPlaceDetails(placeId: String): PlaceDetails = details(placeId)
}
