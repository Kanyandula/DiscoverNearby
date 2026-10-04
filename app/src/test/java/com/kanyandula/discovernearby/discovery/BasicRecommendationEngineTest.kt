package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.model.PlaceSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class BasicRecommendationEngineTest {

    private val engine = BasicRecommendationEngine()

    private fun ranked(category: DiscoveryCategory, vararg places: PlaceSummary) =
        engine.rank(places.toList(), testContext(category)).map { it.place.id }

    // Score floor (docs/03 §10): no target kind, not credible; never kept as filler.
    @Test
    fun dropsPlacesOutsideTheCategory() {
        val ranked = ranked(COFFEE, testPlace("cafe", "cafe"), testPlace("pub", "bar"), testPlace("none"))
        assertEquals(listOf("cafe"), ranked)
    }

    // Known-closed is excluded, unknown is neutral (docs/03 §10).
    @Test
    fun excludesKnownClosedPlacesOnly() {
        val closed = testPlace("closed", "cafe").copy(isOpenNow = false)
        val unknown = testPlace("unknown", "cafe", metersNorth = 300)
        val open = testPlace("open", "cafe", metersNorth = 500).copy(isOpenNow = true)
        assertEquals(listOf("unknown", "open"), ranked(COFFEE, closed, unknown, open))
    }

    @Test
    fun primaryKindMatchesRankAboveSecondaryOnes() {
        val secondary = testPlace("secondary", "museum", "playground", metersNorth = 100)
        val primary = testPlace("primary", "playground", metersNorth = 5_000)
        assertEquals(listOf("primary", "secondary"), ranked(FAMILY, secondary, primary))
    }

    @Test
    fun nearerRanksFirstWithinTheSameMatch() {
        val far = testPlace("far", "cafe", metersNorth = 900)
        val near = testPlace("near", "cafe", metersNorth = 200)
        assertEquals(listOf("near", "far"), ranked(COFFEE, far, near))
    }

    // A provider listing a place twice must not crash the list, whose rows are keyed by id (docs/03 §16).
    @Test
    fun keepsTheNearestCopyOfARepeatedPlace() {
        val ranked = engine.rank(
            listOf(testPlace("a", "cafe", metersNorth = 300), testPlace("a", "cafe", metersNorth = 100)),
            testContext(COFFEE),
        )
        assertEquals(listOf(100), ranked.map { it.distanceMeters })
    }

    @Test
    fun tiesBreakById() {
        assertEquals(listOf("a", "b"), ranked(COFFEE, testPlace("b", "cafe"), testPlace("a", "cafe")))
    }

    // Null-heavy data (docs/04 Q): an unknown primary kind still matches on the kinds it has.
    @Test
    fun unknownPrimaryKindStillMatches() {
        assertEquals(listOf("cafe"), ranked(COFFEE, testPlace("cafe", "cafe", primaryKind = null)))
    }

    // No display limit: the ViewModel trims (docs/03 §10).
    @Test
    fun keepsEveryMatchAndReportsDistance() {
        val places = List(8) { testPlace("p$it", "cafe", metersNorth = 100 * (it + 1)) }
        val ranked = engine.rank(places, testContext(COFFEE))
        assertEquals(8, ranked.size)
        assertEquals(100, ranked.first().distanceMeters)
    }
}
