package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC
import com.kanyandula.discovernearby.model.AttributeSource.DERIVED
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType.DRIVE_THROUGH
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.AttributeType.TOILETS
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.PlaceSummary
import org.junit.Assert.assertEquals
import org.junit.Test

// docs/04 §11: fixtures assert order, and equal scores for neutrality, never individual weights.
class BasicRecommendationEngineTest {

    private val engine = BasicRecommendationEngine()

    private fun ranked(category: DiscoveryCategory, vararg places: PlaceSummary) =
        engine.rank(places.toList(), testContext(category)).map { it.place.id }

    private fun scores(category: DiscoveryCategory, vararg places: PlaceSummary) =
        engine.rank(places.toList(), testContext(category)).associate { it.place.id to it.score }

    // Score floor (docs/03 §10): no target kind, not credible; never kept as filler.
    @Test
    fun dropsPlacesOutsideTheCategory() {
        val ranked = ranked(COFFEE, testPlace("cafe", "cafe"), testPlace("pub", "bar"), testPlace("none"))
        assertEquals(listOf("cafe"), ranked)
    }

    // Score floor (Product Lead, 2026-10-07): a kind that isn't the place's primary counts half, so a museum that is
    // also a park is too weak for Family, and one that is also a playground passes.
    @Test
    fun weakMatchesFallBelowTheFloor() {
        val alsoPark = testPlace("also-park", "museum", "park")
        val alsoPlayground = testPlace("also-playground", "museum", "playground")
        assertEquals(listOf("also-playground"), ranked(FAMILY, alsoPark, alsoPlayground))
    }

    // Category match dominates (docs/04 §11): a primary match well away beats a secondary one next door.
    @Test
    fun primaryKindMatchesRankAboveSecondaryOnes() {
        val secondary = testPlace("secondary", "museum", "playground", metersNorth = 100)
        val primary = testPlace("primary", "playground", metersNorth = 5_000)
        assertEquals(listOf("primary", "secondary"), ranked(FAMILY, secondary, primary))
    }

    // Product Lead, 2026-10-07: time-sensitive categories drop a place known to be closed now.
    @Test
    fun knownClosedIsDroppedWhereTheCategoryIsTimeSensitive() {
        val timeSensitive = mapOf(COFFEE to "cafe", FOOD to "restaurant", FAMILY to "zoo", EXPLORE to "museum")
        timeSensitive.forEach { (category, kind) ->
            val closed = testPlace("closed", kind).copy(isOpenNow = false)
            val open = testPlace("open", kind).copy(isOpenNow = true)
            assertEquals(category.name, listOf("open"), ranked(category, closed, open))
        }
    }

    // Product Lead, 2026-10-07: Outdoors and Scenic keep a closed place, scored like an unknown one.
    @Test
    fun knownClosedStaysInOutdoorsAndScenicWithoutTheBonus() {
        mapOf(OUTDOORS to "park", SCENIC to "viewpoint").forEach { (category, kind) ->
            val scores = scores(category, testPlace("closed", kind).copy(isOpenNow = false), testPlace("unknown", kind))
            assertEquals(category.name, setOf("closed", "unknown"), scores.keys)
            assertEquals(category.name, scores.getValue("unknown"), scores.getValue("closed"), 0.0)
        }
    }

    // Unknown is neutral (docs/03 §10): a missing rating, open state or amenity scores the same as known values
    // that earn nothing.
    @Test
    fun unknownDataIsNeutral() {
        val known = testPlace("known", "cafe").copy(
            rating = 4.0,
            ratingCount = 12,
            attributes = setOf(PlaceAttribute(DRIVE_THROUGH, PROVIDED)),
        )
        val scores = scores(COFFEE, testPlace("unknown", "cafe"), known)
        assertEquals(scores.getValue("unknown"), scores.getValue("known"), 0.0)
    }

    // docs/03 §10 optional signals: each lifts a place above an identical one without it ("a-plain" wins any tie).
    @Test
    fun openNowAHighRatingAndAWeightedAmenityRaiseAPlace() {
        val plain = testPlace("a-plain", "cafe")
        listOf(
            testPlace("open", "cafe").copy(isOpenNow = true),
            testPlace("rated", "cafe").copy(rating = 4.5, ratingCount = 30),
            testPlace("parking", "cafe").copy(attributes = setOf(PlaceAttribute(PARKING, PROVIDED))),
        ).forEach { better ->
            assertEquals(better.id, listOf(better.id, "a-plain"), ranked(COFFEE, plain, better))
        }
    }

    // Amenities count only where the category weights them: parking means nothing to Outdoors, toilets lift Family.
    @Test
    fun amenitiesCountOnlyWhereTheCategoryWeightsThem() {
        val parking = testPlace("parking", "park").copy(attributes = setOf(PlaceAttribute(PARKING, PROVIDED)))
        val outdoors = scores(OUTDOORS, testPlace("plain", "park"), parking)
        assertEquals(outdoors.getValue("plain"), outdoors.getValue("parking"), 0.0)
        val toilets = testPlace("toilets", "zoo").copy(attributes = setOf(PlaceAttribute(TOILETS, DERIVED)))
        assertEquals(listOf("toilets", "a-plain"), ranked(FAMILY, testPlace("a-plain", "zoo"), toilets))
    }

    // Review Focus 4: PROVIDED and DERIVED toilets are still one amenity.
    @Test
    fun anAmenityFromTwoSourcesCountsOnce() {
        val once = testPlace("once", "zoo").copy(attributes = setOf(PlaceAttribute(TOILETS, PROVIDED)))
        val twice = testPlace("twice", "zoo").copy(
            attributes = setOf(PlaceAttribute(TOILETS, PROVIDED), PlaceAttribute(TOILETS, DERIVED)),
        )
        val scores = scores(FAMILY, once, twice)
        assertEquals(scores.getValue("once"), scores.getValue("twice"), 0.0)
    }

    // Light diversity (Product Lead, 2026-10-07): Outdoors keeps the two best parks, then the beach; the third park
    // is dropped, not moved down.
    @Test
    fun keepsAtMostTwoOfAKind() {
        val parks = List(3) { testPlace("park-$it", "park", metersNorth = 100 * (it + 1)) }
        val beach = testPlace("beach", "beach", metersNorth = 900)
        assertEquals(listOf("park-0", "park-1", "beach"), ranked(OUTDOORS, *(parks + beach).toTypedArray()))
    }

    // Product Lead, 2026-10-07: every café is "cafe" on HERE data, so Coffee and Food have no cap.
    @Test
    fun coffeeAndFoodAreNotCapped() {
        mapOf(COFFEE to "cafe", FOOD to "restaurant").forEach { (category, kind) ->
            val places = List(4) { testPlace("p$it", kind, metersNorth = 100 * (it + 1)) }
            assertEquals(category.name, 4, engine.rank(places, testContext(category)).size)
        }
    }

    // Review Focus 2: HERE leaves the primary kind null when its primary category is one we don't search (a
    // business also filed under Scenic Point). It matches at half weight and counts as a viewpoint for the cap.
    @Test
    fun anUnknownPrimaryKindCountsAsTheKindItMatched() {
        val v1 = testPlace("v1", "viewpoint", metersNorth = 2_000)
        val v2 = testPlace("v2", "viewpoint", metersNorth = 3_000)
        val filed = testPlace("filed", "viewpoint", metersNorth = 100, primaryKind = null)
        assertEquals(listOf("v1", "v2"), ranked(SCENIC, filed, v1, v2))
        assertEquals(listOf("v1", "filed"), ranked(SCENIC, filed, v1))
    }

    // Review Focus 3: a provider listing a place twice must not use up its kind's cap.
    @Test
    fun aRepeatedPlaceDoesNotUseUpTheCap() {
        val a = testPlace("a", "park", metersNorth = 100)
        val places = listOf(a, a, testPlace("b", "park", metersNorth = 200), testPlace("c", "park", metersNorth = 300))
        assertEquals(listOf("a", "b"), engine.rank(places, testContext(OUTDOORS)).map { it.place.id })
    }

    // Review Focus 1: a provider can return a place past the radius; it gets no nearness, never a negative one.
    @Test
    fun nothingPastTheRadiusScoresBelowZeroNearness() {
        val near = testPlace("near", "cafe", metersNorth = 6_000)
        val far = testPlace("far", "cafe", metersNorth = 9_000)
        val scores = scores(COFFEE, near, far)
        assertEquals(scores.getValue("near"), scores.getValue("far"), 0.0)
        assertEquals(listOf("near", "far"), ranked(COFFEE, far, near)) // equal scores: nearer first
    }

    // Review Focus 5: a landmark is a full match for Explore but a weak one for Scenic, so a museum that is also a
    // landmark explores well and isn't scenic, while a place that is mainly a landmark is both.
    @Test
    fun aLandmarkWeighsDifferentlyPerCategory() {
        val museum = testPlace("museum", "museum", "landmark")
        val lighthouse = testPlace("lighthouse", "landmark")
        assertEquals(setOf("lighthouse", "museum"), ranked(EXPLORE, museum, lighthouse).toSet())
        assertEquals(listOf("lighthouse"), ranked(SCENIC, museum, lighthouse))
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
