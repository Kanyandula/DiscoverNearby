package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.model.GeoPoint
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Synthetic: invented values in the shape of HERE's documented /browse item (ADR-001 [H2]). The spike recorded no
// fixtures (public repo; ADR-001 V6b).
internal const val FULL_ITEM = """
{"title": "Test Coffee", "id": "here:pds:place:test-1", "resultType": "place",
 "position": {"lat": 53.1445, "lng": -6.0631}, "distance": 60,
 "categories": [{"id": "100-1000-0000", "name": "Restaurant"},
                {"id": "100-1100-0010", "name": "Coffee Shop", "primary": true}],
 "contacts": [{"phone": [{"value": "+353 1 000 0000"}]}],
 "openingHours": [{"text": ["Mon-Sat: 07:30 - 18:00", "Sun: 09:00 - 17:00"], "isOpen": true, "structured": []}]}
"""

class HereMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun item(text: String) = json.decodeFromString<HereItem>(text)

    @Test
    fun aFullItemMapsEveryKnownField() {
        val place = checkNotNull(item(FULL_ITEM).toSummary())
        assertEquals("here:pds:place:test-1", place.id)
        assertEquals("Test Coffee", place.name)
        assertEquals(GeoPoint(53.1445, -6.0631), place.location)
        assertEquals(setOf("restaurant", "coffee_shop"), place.placeKinds)
        assertEquals("coffee_shop", place.primaryKind)
        assertEquals(true, place.isOpenNow)
        assertEquals("HERE", place.attribution) // DN-M1-003: HERE's notice goes with its data (ADR-001 V6a)
    }

    // ADR-001 Field availability: /browse has no rating, parking, toilets or travel time. Unknown stays unknown.
    @Test
    fun fieldsHereLacksStayUnknown() {
        val place = checkNotNull(item(FULL_ITEM).toSummary())
        assertNull(place.rating)
        assertNull(place.ratingCount)
        assertNull(place.travelTimeMinutes)
        assertTrue(place.attributes.isEmpty())
    }

    // Review Focus 2
    @Test
    fun anItemWithoutAnIdNameOrPositionIsDropped() {
        assertNull(item("""{"title": "A", "position": {"lat": 1.0, "lng": 2.0}}""").toSummary())
        assertNull(item("""{"id": "x", "title": "  ", "position": {"lat": 1.0, "lng": 2.0}}""").toSummary())
        assertNull(item("""{"id": "x", "title": "A"}""").toSummary())
        assertNull(item("""{"id": "x", "title": "A", "position": {"lat": 1.0}}""").toSummary())
    }

    // Review Focus 2: one bad item must not fail the whole response.
    @Test
    fun aMalformedItemDoesNotFailTheResponse() {
        val body = """{"items": [{"id": "bad", "title": "Bad", "position": {"lat": 1.0}}, $FULL_ITEM]}"""
        val places = json.decodeFromString<HereBrowseResponse>(body).items.mapNotNull { it.toSummary() }
        assertEquals(listOf("Test Coffee"), places.map { it.name })
    }

    @Test
    fun aSparseItemKeepsWhatItHas() {
        val place = checkNotNull(item("""{"id": "x", "title": "Somewhere", "position": {"lat": 1.0, "lng": 2.0}}""")
            .toSummary())
        assertTrue(place.placeKinds.isEmpty())
        assertNull(place.primaryKind)
        assertNull(place.isOpenNow)
    }

    @Test
    fun withoutAPrimaryFlagTheFirstMappedKindLeads() {
        val place = checkNotNull(item("""{"id": "x", "title": "A", "position": {"lat": 1.0, "lng": 2.0},
            "categories": [{"id": "700-7300-0000"}, {"id": "550-5510-0242"}]}""").toSummary())
        assertEquals("viewpoint", place.primaryKind)
    }

    @Test
    fun detailsAddTheOpeningHoursText() {
        val details = checkNotNull(item(FULL_ITEM).toDetails())
        assertEquals("Mon-Sat: 07:30 - 18:00; Sun: 09:00 - 17:00", details.openingSummary)
        assertEquals("HERE", details.summary.attribution)
    }

    @Test
    fun detailsWithoutHoursHaveNoOpeningSummary() {
        val details = checkNotNull(item("""{"id": "x", "title": "A", "position": {"lat": 1.0, "lng": 2.0}}""")
            .toDetails())
        assertNull(details.openingSummary)
    }

    // Final review: HERE can send one hours entry per category (kitchen closed, café open). Any open entry means open;
    // known-closed (which the engine excludes) only when no entry says open.
    @Test
    fun anyOpenHoursEntryMeansOpen() {
        val base = """{"id": "x", "title": "A", "position": {"lat": 1.0, "lng": 2.0}, "openingHours": """
        assertEquals(true, item(base + """[{"isOpen": false}, {"isOpen": true}]}""").toSummary()?.isOpenNow)
        assertEquals(false, item(base + """[{"isOpen": false}, {"text": ["x"]}]}""").toSummary()?.isOpenNow)
        assertNull(item(base + """[{"text": ["x"]}]}""").toSummary()?.isOpenNow)
    }

    // Review Focus 3 (DN-M1-001 review): a petrol forecourt (primary, not searched) with a coffee counter is a place
    // that serves coffee, not a café; it must not score as a primary match.
    @Test
    fun aPrimaryCategoryWeDoNotSearchLeavesNoPrimaryKind() {
        val place = checkNotNull(item("""{"id": "x", "title": "A", "position": {"lat": 1.0, "lng": 2.0},
            "categories": [{"id": "700-7600-0116", "primary": true}, {"id": "100-1100-0010"}]}""").toSummary())
        assertEquals(setOf("coffee_shop"), place.placeKinds)
        assertNull(place.primaryKind)
    }
}
