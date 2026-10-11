package com.kanyandula.discovernearby.saved

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Test stand-in: newest first, like Room; [places] can be read or changed directly; savedAt counts 1, 2, 3 … */
class FakeSavedPlaces : SavedPlacesRepository {
    val places = MutableStateFlow<List<SavedPlace>>(emptyList())
    private var clock = 0L

    override fun savedPlaces(): Flow<List<SavedPlace>> = places

    override fun isSaved(placeId: String): Flow<Boolean> = places.map { list -> list.any { it.summary.id == placeId } }

    override suspend fun save(summary: PlaceSummary, category: DiscoveryCategory, enrichment: PlaceEnrichment?) {
        val place = SavedPlace(summary, category, enrichment, ++clock)
        places.update { list -> listOf(place) + list.filterNot { it.summary.id == summary.id } }
    }

    override suspend fun remove(placeId: String) {
        places.update { list -> list.filterNot { it.summary.id == placeId } }
    }
}
