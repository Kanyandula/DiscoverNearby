package com.kanyandula.discovernearby.saved

import androidx.room.Room
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.PriceLevel
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomSavedPlacesTest {

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SavedPlacesDatabase::class.java)
        .build()
    private var now = 1_000L
    private val saved = RoomSavedPlaces(db.savedPlaceDao(), clock = { now })

    private val cafe = testPlace("cafe-1", "cafe").copy(attribution = "HERE")
    private val park = testPlace("park-1", "park")

    @After
    fun close() = db.close()

    private suspend fun ids() = saved.savedPlaces().first().map { it.summary.id }

    @Test
    fun aSavedPlaceComesBackWithItsCategoryAndEnrichment() = runTest {
        val enrichment = PlaceEnrichment(priceLevel = PriceLevel.MID, description = "A made-up café.")
        saved.save(cafe, COFFEE, enrichment)
        assertEquals(listOf(SavedPlace(cafe, COFFEE, enrichment, savedAt = 1_000L)), saved.savedPlaces().first())
    }

    @Test
    fun newestFirst() = runTest {
        saved.save(cafe, COFFEE, null)
        now = 2_000L
        saved.save(park, OUTDOORS, null)
        assertEquals(listOf("park-1", "cafe-1"), ids())
    }

    // Review Focus 3: saving again keeps one row, now the newest.
    @Test
    fun savingTwiceKeepsOneRowAndMovesItToTheTop() = runTest {
        saved.save(cafe, COFFEE, null)
        now = 2_000L
        saved.save(park, OUTDOORS, null)
        now = 3_000L
        saved.save(cafe, COFFEE, null)
        assertEquals(listOf("cafe-1", "park-1"), ids())
    }

    @Test
    fun removeDropsIt() = runTest {
        saved.save(cafe, COFFEE, null)
        saved.save(park, OUTDOORS, null)
        saved.remove("cafe-1")
        assertEquals(listOf("park-1"), ids())
    }

    @Test
    fun isSavedFollowsSaveAndRemove() = runTest {
        val seen = mutableListOf<Boolean>()
        saved.isSaved("cafe-1").take(3).collectIndexed { index, value ->
            seen += value
            when (index) {
                0 -> saved.save(cafe, COFFEE, null)
                1 -> saved.remove("cafe-1")
            }
        }
        assertEquals(listOf(false, true, false), seen)
    }

    @Test
    fun theListUpdatesLive() = runTest {
        val seen = mutableListOf<List<String>>()
        saved.savedPlaces().take(2).collectIndexed { index, places ->
            seen += places.map { it.summary.id }
            if (index == 0) saved.save(cafe, COFFEE, null)
        }
        assertEquals(listOf(emptyList(), listOf("cafe-1")), seen)
    }

    // Review Focus 1: a row stored by an older build that no longer decodes is skipped, not a crash.
    @Test
    fun aRowThatNoLongerDecodesIsSkipped() = runTest {
        saved.save(cafe, COFFEE, null)
        val dao = db.savedPlaceDao()
        dao.upsert(SavedPlaceEntity("broken", "COFFEE", 1L, summaryJson = """{"id": 1}""", enrichmentJson = null))
        val parkJson = Json.encodeToString(PlaceSummary.serializer(), park)
        dao.upsert(SavedPlaceEntity("park-1", "BOWLING", 2L, summaryJson = parkJson, enrichmentJson = null))
        assertEquals(listOf("cafe-1"), ids())
    }
}
