package com.kanyandula.discovernearby.saved

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import kotlinx.coroutines.flow.Flow

/** A place the driver saved (DN-UX-006): as Place Details knew it, with the category it was found in. */
data class SavedPlace(
    val summary: PlaceSummary,
    val category: DiscoveryCategory, // for its artwork, as on the row it came from
    val enrichment: PlaceEnrichment?,
    val savedAt: Long, // epoch milliseconds
)

/** Saved places on the device (ADR-004). Flows update as places are saved and removed. */
interface SavedPlacesRepository {
    /** Every saved place, the most recently saved first. */
    fun savedPlaces(): Flow<List<SavedPlace>>

    fun isSaved(placeId: String): Flow<Boolean>

    /** Saves the place, or saves it again as the newest with what is known now. */
    suspend fun save(summary: PlaceSummary, category: DiscoveryCategory, enrichment: PlaceEnrichment?)

    suspend fun remove(placeId: String)
}
