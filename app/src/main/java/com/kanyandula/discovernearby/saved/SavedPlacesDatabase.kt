package com.kanyandula.discovernearby.saved

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One saved place; the summary and enrichment as JSON, so the table needs no column per field (DN-UX-006). */
@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey val placeId: String,
    val category: String, // DiscoveryCategory.name
    val savedAt: Long,
    val summaryJson: String,
    val enrichmentJson: String?,
)

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_places WHERE placeId = :placeId)")
    fun observeIsSaved(placeId: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(place: SavedPlaceEntity)

    @Query("DELETE FROM saved_places WHERE placeId = :placeId")
    suspend fun delete(placeId: String)
}

// No exported schema: a schema change wipes saved places (destructive migration), which the POC accepts (ADR-004).
// Bump the version with any change to SavedPlaceEntity, or Room refuses to open the old file.
@Database(entities = [SavedPlaceEntity::class], version = 1, exportSchema = false)
abstract class SavedPlacesDatabase : RoomDatabase() {
    abstract fun savedPlaceDao(): SavedPlaceDao
}
