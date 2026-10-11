package com.kanyandula.discovernearby.saved

import android.content.Context
import androidx.room.Room
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Opens the saved places in the database file [name], or in memory when [name] is null (Robolectric). */
fun openSavedPlaces(context: Context, name: String?): SavedPlacesRepository {
    val builder = if (name == null) {
        Room.inMemoryDatabaseBuilder(context, SavedPlacesDatabase::class.java)
    } else {
        Room.databaseBuilder(context, SavedPlacesDatabase::class.java, name)
    }
    return RoomSavedPlaces(builder.fallbackToDestructiveMigration(dropAllTables = true).build().savedPlaceDao())
}

/** [SavedPlacesRepository] on Room; [clock] stamps each save, which orders the list. */
class RoomSavedPlaces(
    private val dao: SavedPlaceDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : SavedPlacesRepository {

    override fun savedPlaces(): Flow<List<SavedPlace>> =
        dao.observeAll()
            .map { rows -> rows.mapNotNull { it.toSavedPlace() } }
            .flowOn(Dispatchers.Default) // the JSON decoding, off the collector's main thread
            .distinctUntilChanged()

    override fun isSaved(placeId: String): Flow<Boolean> = dao.observeIsSaved(placeId).distinctUntilChanged()

    override suspend fun save(summary: PlaceSummary, category: DiscoveryCategory, enrichment: PlaceEnrichment?) =
        dao.upsert(
            SavedPlaceEntity(
                placeId = summary.id,
                category = category.name,
                savedAt = clock(),
                summaryJson = json.encodeToString(PlaceSummary.serializer(), summary),
                enrichmentJson = enrichment?.let { json.encodeToString(PlaceEnrichment.serializer(), it) },
            ),
        )

    override suspend fun remove(placeId: String) = dao.delete(placeId)
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * The saved place, or null when a row stored by an older build no longer decodes (a field or a category changed): the
 * list goes on without it. SerializationException is an IllegalArgumentException, as is an unknown category name.
 */
private fun SavedPlaceEntity.toSavedPlace(): SavedPlace? = try {
    SavedPlace(
        summary = json.decodeFromString(PlaceSummary.serializer(), summaryJson),
        category = DiscoveryCategory.valueOf(category),
        enrichment = enrichmentJson?.let { json.decodeFromString(PlaceEnrichment.serializer(), it) },
    )
} catch (ignored: IllegalArgumentException) {
    null
}
