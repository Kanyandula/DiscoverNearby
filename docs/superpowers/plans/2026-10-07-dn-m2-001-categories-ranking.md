# DN-M2-001 Categories and Ranking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace M0's stub ranking with docs/03 §10 scoring, with every weight and rule held as data in
`CategoryConfigs`, applying the Product Lead's 2026-10-07 decisions on closed places, diversity and the score floor.

**Architecture:**
- **Data:** `CategoryConfig` gains `kindWeights` (category relevance per target kind), `amenityWeights`,
  `excludeClosed` and `maxPerKind`. `targetKinds` becomes `kindWeights.keys`, so its callers don't change. Signals
  shared by every category (secondary share, floor, nearness, open-now and rating bonuses) are named constants next
  to it.
- **Engine:** `BasicRecommendationEngine` keeps its name and interface. It drops known-closed places where the
  category says so, scores the rest (category match + nearness + rating + amenities + open now), drops matches below
  the floor, sorts (score, then nearer, then id), removes repeated ids, then applies the per-kind cap.
- **Unchanged:** the HERE category mapping (already ADR-001's table), `DiscoverUseCase`, the ViewModel's
  `min(5, listLimit)` trim, the UI.

**Tech Stack:** Kotlin (pure, JVM tests with JUnit 4), detekt 1.23.8, the AAOS emulator for one live smoke.

**Spec:**
- the ticket `~/.claude/projects/Discover Nearby/tickets/DN-M2-001-categories-and-ranking.md`, including its
  2026-10-07 Product Lead decisions;
- docs/03 §2 (capability-driven ranking, seams), §9 (categories), §10 (engine), §20 (tests);
- docs/01 §10–§12 (categories, recommendation philosophy, acceptance);
- docs/04 §11 "RecommendationEngine (fixture-based; assert order, not individual weights)";
- ADR-001 "Category mapping" and "Field availability".

## Global Constraints

- **Ticket acceptance criteria:**
  - Coffee, Food, Outdoors, Family, Scenic, and Explore map to provider types recorded in ADR-001.
  - CategoryConfig keeps radii, result count, and ranking signals data-driven.
  - RecommendationEngine is pure Kotlin and deterministic.
  - Known-closed time-sensitive results are excluded; unknown signals are neutral.
  - Category relevance, location relevance, available quality/amenity signals, and diversity are applied as
    specified.
  - Weak category matches fall below the score floor; results are never padded.
  - The engine returns every place that passes the score floor and diversity rule, in ranked order with stable
    tie-breaking. It has no display limit; the ViewModel shows the first min(5, uxLimit).
- **Product Lead decisions (the user, 2026-10-07):**
  - **Closed:** `isOpenNow == false` is dropped in Coffee, Food, Family and Explore. Outdoors and Scenic keep it,
    with no open-now bonus.
  - **Diversity:** at most 2 results per primary kind in Outdoors, Family, Scenic and Explore; places over the cap
    are dropped. Coffee and Food have no cap.
  - **Score floor:** each category weights its target kinds; a matching kind that isn't the place's primary counts
    half; a category match below 10 is dropped.
- **Mapping (already done, verify only):** `HERE_CATEGORIES` in `places/here/HereCategories.kt` equals ADR-001's
  "Selected provider" table, and `HereCategoriesTest.everyRequestedCategoryMapsToAKindItsCategoryAccepts` is the
  docs/03 §20 table-driven test over all six categories. No change.
- **Architecture:** `discovery/` has no Android, Compose or `android.car` imports. The engine never sees the driving
  state and never pads. Weights live in `CategoryConfigs`, not in code branches.
- **Tests:** docs/04: assert order (and score equality for neutrality), not individual weights.
- **Public repo:** no provider content (place names, IDs, coordinates of live results) in commits or tracked docs.
  The emulator smoke records counts only.
- **Emulator:** always `adb -s emulator-5554`. Send `geo fix` twice. No `uiautomator`.
- **Checks:** `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`, plus the import-order check (Task 3).
- **Attribution:** none, in commits or the PR.

## Review Focus

1. **A place past the radius** (a provider's circle edge, rounding). Expect zero nearness, never negative, so it
   ranks as an edge place. Pinned by `nothingPastTheRadiusScoresBelowZeroNearness` (Task 2).
2. **HERE's primary category is one we don't search** (a business also filed under Scenic Point), so
   `primaryKind == null`. Expect a half-weight match that ranks below real viewpoints and counts toward the
   viewpoint cap. Pinned by `anUnknownPrimaryKindCountsAsTheKindItMatched` (Task 2).
3. **The provider lists a place twice.** Expect one row, and the copy must not use up its kind's cap. Pinned by
   `aRepeatedPlaceDoesNotUseUpTheCap` (Task 2).
4. **The same amenity from two sources** (PROVIDED and DERIVED toilets). Expect it to count once. Pinned by
   `anAmenityFromTwoSourcesCountsOnce` (Task 2).
5. **A kind shared by two categories** (landmark: Scenic and Explore). Expect a full match in Explore, and a weak one
   in Scenic that drops below the floor when it isn't the primary kind. Pinned by
   `aLandmarkWeighsDifferentlyPerCategory` (Task 2).

---

### Task 1: Ranking signals as data in `CategoryConfig`

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/discovery/CategoryConfig.kt` (whole file)
- Test: `app/src/test/java/com/kanyandula/discovernearby/discovery/CategoryConfigTest.kt`

**Interfaces:**
- Consumes: `DiscoveryCategory`, `model.AttributeType` (existing).
- Produces:
  - `data class CategoryConfig(radiusMeters: Int, desiredResults: Int, kindWeights: Map<String, Int>,
    amenityWeights: Map<AttributeType, Int> = emptyMap(), excludeClosed: Boolean, maxPerKind: Int?)` with
    `val targetKinds: Set<String>` (= `kindWeights.keys`);
  - `const val SECONDARY_KIND_SHARE = 0.5`, `SCORE_FLOOR = 10.0`, `NEARNESS_WEIGHT = 20.0`,
    `OPEN_NOW_BONUS = 5.0`, `HIGH_RATING = 4.5`, `HIGH_RATING_BONUS = 5.0` (package `discovery`);
  - `CategoryConfigs: Map<DiscoveryCategory, CategoryConfig>` (same name as today).

- [ ] **Step 1: Write the failing tests**

Add to `CategoryConfigTest.kt` the imports `com.kanyandula.discovernearby.model.AttributeType.CAFE`,
`…AttributeType.PARKING` and `…AttributeType.TOILETS` (sorted after the `discovery` imports), and these tests at the
end of the class:

```kotlin
    // Product Lead, 2026-10-07: these categories drop known-closed places; Outdoors and Scenic keep them.
    @Test
    fun timeSensitiveCategoriesDropClosedPlaces() {
        assertEquals(setOf(COFFEE, FOOD, FAMILY, EXPLORE), CategoryConfigs.filterValues { it.excludeClosed }.keys)
    }

    // Product Lead, 2026-10-07: two of a kind at most, except in Coffee and Food.
    @Test
    fun diversityCapsAllButCoffeeAndFood() {
        assertEquals(
            mapOf(COFFEE to null, FOOD to null, OUTDOORS to 2, FAMILY to 2, SCENIC to 2, EXPLORE to 2),
            CategoryConfigs.mapValues { it.value.maxPerKind },
        )
    }

    // docs/03 §10 Family example: the strongest kinds outweigh a park; toilets, parking and café count.
    @Test
    fun familyWeightsFollowTheDocsExample() {
        val family = CategoryConfigs.getValue(FAMILY)
        assertTrue(family.kindWeights.getValue("playground") > family.kindWeights.getValue("park"))
        assertEquals(setOf(TOILETS, PARKING, CAFE), family.amenityWeights.keys)
    }

    // A target kind that could never pass the floor would be dead configuration.
    @Test
    fun everyTargetKindPassesTheFloorAsPrimary() {
        CategoryConfigs.forEach { (category, config) ->
            config.kindWeights.forEach { (kind, weight) -> assertTrue("$category $kind", weight >= SCORE_FLOOR) }
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*CategoryConfigTest' --console=plain -q 2>&1 | tail -5`
Expected: compilation fails, `Unresolved reference 'excludeClosed'` (and `maxPerKind`, `kindWeights`, `SCORE_FLOOR`).

- [ ] **Step 3: Write the implementation**

Replace `CategoryConfig.kt` with:

```kotlin
package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC
import com.kanyandula.discovernearby.model.AttributeType
import com.kanyandula.discovernearby.model.AttributeType.CAFE
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.AttributeType.TOILETS

/** Up to this many recommendations are shown, before the driving list limit (docs/03 §6). */
const val DESIRED_RECOMMENDATIONS = 5

// docs/03 §10 signals every category shares; tune them here. The per-category ones are in CategoryConfig.

/** A matching kind that isn't the place's primary kind counts this share of its weight. */
const val SECONDARY_KIND_SHARE = 0.5

/** A category match below this is dropped, never shown as filler. */
const val SCORE_FLOOR = 10.0

/** Nearness adds up to this: all of it at the origin, none at the category's radius or beyond. */
const val NEARNESS_WEIGHT = 20.0

/** Added when the provider says the place is open now; unknown or closed adds nothing. */
const val OPEN_NOW_BONUS = 5.0

/** A rating at or above [HIGH_RATING] adds [HIGH_RATING_BONUS]; no rating adds nothing. */
const val HIGH_RATING = 4.5
const val HIGH_RATING_BONUS = 5.0

/** Per-category tuning as data, not code branches (docs/03 §2, §10). */
data class CategoryConfig(
    val radiusMeters: Int,
    val desiredResults: Int,
    /** The category match each target kind gives a place whose primary kind it is. */
    val kindWeights: Map<String, Int>,
    /** Added once per known amenity (PROVIDED or DERIVED); an absent one is unknown and adds nothing. */
    val amenityWeights: Map<AttributeType, Int> = emptyMap(),
    /** Time-sensitive: a place known to be closed now is dropped, not penalised (Product Lead, 2026-10-07). */
    val excludeClosed: Boolean,
    /** At most this many results share a primary kind; null for no cap (Product Lead, 2026-10-07). */
    val maxPerKind: Int?,
) {
    val targetKinds: Set<String> get() = kindWeights.keys
}

private const val STRONG_MATCH = 30
private const val WEAK_MATCH = 15
private const val DIVERSITY_CAP = 2

private fun strong(vararg kinds: String) = kinds.associateWith { STRONG_MATCH }

// Radii: docs/03 §9 illustrative values. Kinds: docs/03 §9, normalised to snake_case. Weights: docs/03 §10's Family
// example; amenities: docs/01 §10's optional signals per category.
val CategoryConfigs: Map<DiscoveryCategory, CategoryConfig> = mapOf(
    COFFEE to CategoryConfig(
        radiusMeters = 5_000,
        desiredResults = DESIRED_RECOMMENDATIONS,
        kindWeights = strong("cafe", "coffee_shop"),
        amenityWeights = mapOf(PARKING to 5),
        excludeClosed = true,
        maxPerKind = null,
    ),
    FOOD to CategoryConfig(
        radiusMeters = 5_000,
        desiredResults = DESIRED_RECOMMENDATIONS,
        kindWeights = strong("restaurant", "fast_food", "takeaway"),
        amenityWeights = mapOf(PARKING to 5),
        excludeClosed = true,
        maxPerKind = null,
    ),
    OUTDOORS to CategoryConfig(
        radiusMeters = 20_000,
        desiredResults = DESIRED_RECOMMENDATIONS,
        kindWeights = strong("park", "trail", "forest", "beach", "hiking_area", "outdoor_attraction"),
        excludeClosed = false,
        maxPerKind = DIVERSITY_CAP,
    ),
    FAMILY to CategoryConfig(
        radiusMeters = 15_000,
        desiredResults = DESIRED_RECOMMENDATIONS,
        kindWeights = strong("playground", "zoo", "aquarium", "family_attraction") + ("park" to WEAK_MATCH),
        amenityWeights = mapOf(TOILETS to 10, PARKING to 10, CAFE to 5),
        excludeClosed = true,
        maxPerKind = DIVERSITY_CAP,
    ),
    SCENIC to CategoryConfig(
        radiusMeters = 30_000,
        desiredResults = DESIRED_RECOMMENDATIONS,
        // Scenic is for looking at: a landmark is a weaker match here than in Explore.
        kindWeights = strong("viewpoint", "scenic_spot", "coastal_overlook", "waterfall", "natural_attraction") +
            ("landmark" to WEAK_MATCH),
        excludeClosed = false,
        maxPerKind = DIVERSITY_CAP,
    ),
    EXPLORE to CategoryConfig(
        radiusMeters = 15_000,
        desiredResults = DESIRED_RECOMMENDATIONS,
        kindWeights = strong("tourist_attraction", "museum", "landmark", "heritage_site"),
        excludeClosed = true,
        maxPerKind = DIVERSITY_CAP,
    ),
)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*CategoryConfigTest' --tests '*HereCategoriesTest' --tests '*FakePlacesRepositoryTest' --console=plain -q 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (the existing `targetKinds` callers still compile and pass).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/discovery/CategoryConfig.kt \
  app/src/test/java/com/kanyandula/discovernearby/discovery/CategoryConfigTest.kt
git commit -m "Hold ranking weights and rules in CategoryConfig"
```

---

### Task 2: docs/03 §10 scoring in `BasicRecommendationEngine`

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/discovery/RecommendationEngine.kt:11-42`
- Test: `app/src/test/java/com/kanyandula/discovernearby/discovery/BasicRecommendationEngineTest.kt` (whole file)

**Interfaces:**
- Consumes: `CategoryConfig`, `CategoryConfigs`, `SECONDARY_KIND_SHARE`, `SCORE_FLOOR`, `NEARNESS_WEIGHT`,
  `OPEN_NOW_BONUS`, `HIGH_RATING`, `HIGH_RATING_BONUS` (Task 1); `testPlace`, `testContext` (existing,
  `discovery/TestPlaces.kt`).
- Produces: `BasicRecommendationEngine.rank` with the same signature; `Recommendation.score` is now the docs/03 §10
  sum.

- [ ] **Step 1: Write the failing tests**

Replace `BasicRecommendationEngineTest.kt` with:

```kotlin
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
        mapOf(COFFEE to "cafe", FOOD to "restaurant", FAMILY to "zoo", EXPLORE to "museum").forEach { (category, kind) ->
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*BasicRecommendationEngineTest' --console=plain -q 2>&1 | tail -15`
Expected: FAIL. M0's engine drops every closed place, has no floor below a kind match, no bonuses and no cap, so at
least `weakMatchesFallBelowTheFloor`, `knownClosedStaysInOutdoorsAndScenicWithoutTheBonus`,
`openNowAHighRatingAndAWeightedAmenityRaiseAPlace`, `amenitiesCountOnlyWhereTheCategoryWeightsThem`,
`keepsAtMostTwoOfAKind`, `anUnknownPrimaryKindCountsAsTheKindItMatched`, `aRepeatedPlaceDoesNotUseUpTheCap` and
`aLandmarkWeighsDifferentlyPerCategory` fail.

- [ ] **Step 3: Write the implementation**

In `RecommendationEngine.kt`, replace everything from the `/**` above `class BasicRecommendationEngine` to the end
of the file with:

```kotlin
/**
 * docs/03 §10 scoring, every weight from [CategoryConfigs]: category match + nearness + rating + amenities + open
 * now. Time-sensitive categories drop known-closed places; unknown data adds nothing; a category match below
 * [SCORE_FLOOR] is dropped; at most [CategoryConfig.maxPerKind] results share a kind. Ties: nearer, then id.
 */
class BasicRecommendationEngine : RecommendationEngine {

    override fun rank(places: List<PlaceSummary>, context: DiscoveryContext): List<Recommendation> {
        val config = CategoryConfigs.getValue(context.category)
        val keptPerKind = mutableMapOf<String, Int>()
        return places
            .filterNot { config.excludeClosed && it.isOpenNow == false }
            .mapNotNull { score(it, context, config) }
            .sortedWith(
                compareByDescending<Scored> { it.recommendation.score }
                    .thenBy { it.recommendation.distanceMeters }
                    .thenBy { it.recommendation.place.id },
            )
            .distinctBy { it.recommendation.place.id } // the list keys rows by id; a repeated place would crash it
            .filter { scored ->
                // Light diversity, in ranked order, so the strongest of each kind stay.
                val kept = keptPerKind.getOrDefault(scored.kind, 0)
                keptPerKind[scored.kind] = kept + 1
                config.maxPerKind == null || kept < config.maxPerKind
            }
            .map { it.recommendation }
    }

    /** The place scored, with the kind it counts as for diversity; null when its category match is below the floor. */
    private fun score(place: PlaceSummary, context: DiscoveryContext, config: CategoryConfig): Scored? {
        // Sorted, so equal matches settle on the same kind every time.
        val best = place.placeKinds.sorted()
            .map { it to categoryMatch(it, place.primaryKind, config) }
            .maxByOrNull { it.second }
        if (best == null || best.second < SCORE_FLOOR) return null
        val distance = context.origin.distanceMetersTo(place.location)
        val nearness = NEARNESS_WEIGHT * (1 - minOf(distance, config.radiusMeters).toDouble() / config.radiusMeters)
        val rating = if (place.rating != null && place.rating >= HIGH_RATING) HIGH_RATING_BONUS else 0.0
        val amenities = place.attributes.map { it.type }.toSet().sumOf { config.amenityWeights[it] ?: 0 }
        val open = if (place.isOpenNow == true) OPEN_NOW_BONUS else 0.0
        val recommendation = Recommendation(
            place = place,
            score = best.second + nearness + rating + amenities + open,
            distanceMeters = distance,
            travelTimeMinutes = place.travelTimeMinutes,
            minutesAhead = null,
            detourMinutes = null,
        )
        // Without a known primary kind, the kind that matched stands in, so such places share its cap.
        return Scored(recommendation, kind = place.primaryKind ?: best.first)
    }

    private fun categoryMatch(kind: String, primaryKind: String?, config: CategoryConfig): Double {
        val weight = config.kindWeights[kind] ?: 0
        return if (kind == primaryKind) weight.toDouble() else weight * SECONDARY_KIND_SHARE
    }

    private data class Scored(val recommendation: Recommendation, val kind: String)
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*BasicRecommendationEngineTest' --console=plain -q 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL, 20 tests.

- [ ] **Step 5: Run the whole suite**

Run: `./gradlew testDebugUnitTest --console=plain -q 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL. The UI tests find fake places by name. On the fakes the order they rely on holds:
Greystones Family still ranks Adventure Playground first, and Greystones Coffee keeps Harbour Roasters, The Daily
Grind, Brew & Bloom, Station Espresso (Corner Café is closed). If a test fails, stop and report it: don't change a
weight to make a UI test pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/discovery/RecommendationEngine.kt \
  app/src/test/java/com/kanyandula/discovernearby/discovery/BasicRecommendationEngineTest.kt
git commit -m "Rank by category, nearness and signals; cap each kind"
```

---

### Task 3: Docs and the full check

**Files:**
- Modify: `docs/03-discover-nearby-engineering-implementation-plan.md` (§10, after the Family weights block)
- Modify: `CLAUDE.md` ("Current state", "Next", "Architecture rules")

- [ ] **Step 1: docs/03 §10**

After the fenced block that follows `Illustrative Family weights (tunable, not requirements):` (it ends with
`Rating ≥ 4.5 (if rating present)                     +5` and a closing fence), insert a blank line and:

```markdown
**Implemented (DN-M2-001):** the weights and rules live in `CategoryConfigs` (`discovery/CategoryConfig.kt`).
Product Lead decisions (2026-10-07):

- known-closed places are dropped in Coffee, Food, Family and Explore; Outdoors and Scenic keep them, with no
  open-now bonus;
- at most two results per primary kind in Outdoors, Family, Scenic and Explore; Coffee and Food have no cap;
- a matching kind that isn't the place's primary counts half, and a category match below 10 is dropped.
```

- [ ] **Step 2: CLAUDE.md**

In "Current state", after the line `    for HERE data only (\`PlaceSummary.attribution\`). Placement without a map is pending Legal.`
insert:

```markdown
- **Ranking (DN-M2-001):** `BasicRecommendationEngine` scores docs/03 §10 (category match, nearness, rating,
  amenities, open now) with every weight in `CategoryConfigs`. Closed places are dropped in Coffee, Food, Family and
  Explore; at most two per primary kind outside Coffee and Food; below a category match of 10 is dropped.
```

Replace the paragraph that starts `Next: M1's remaining items are Legal's` (through `P3.`) with:

```markdown
Next: DN-M2-002 (the relevance benchmark, Product sign-off is M2's exit). Legal still owes the terms before
production and attribution placement without a map. ADR-001 provisionally selects HERE. DN-TD-002 (Gradle/CI
tuning) is P3.
```

In "Architecture rules", replace:

```markdown
- RecommendationEngine is pure Kotlin. Unknown data is neutral; known-closed places are excluded;
  never pad results. It never sees driving state.
```

with:

```markdown
- RecommendationEngine is pure Kotlin. Unknown data is neutral; known-closed places are excluded where the
  category is time-sensitive (`CategoryConfig.excludeClosed`); never pad results. It never sees driving state.
```

- [ ] **Step 3: The full check**

```bash
./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q; echo "exit=$?"
for f in $(git ls-files '*.kt'); do
  grep '^import ' "$f" > /tmp/dn-actual
  awk '{k=0} /^import java\./{k=1} /^import javax\./{k=2} /^import kotlin\./{k=3} {print k" "$0}' /tmp/dn-actual \
    | LC_ALL=C sort | cut -d' ' -f2- > /tmp/dn-expected
  diff -q /tmp/dn-actual /tmp/dn-expected > /dev/null || echo "UNSORTED $f"
done
awk 'length > 120 {print FILENAME":"NR}' CLAUDE.md
```

Expected:
- `exit=0`, with 232 tests (216 + 4 in Task 1 + 12 net in Task 2);
- no `UNSORTED` lines;
- no CLAUDE.md lines printed.

- [ ] **Step 4: Commit**

```bash
git add docs/03-discover-nearby-engineering-implementation-plan.md CLAUDE.md
git commit -m "Record the ranking rules and where they live"
```

---

### Task 4: Live smoke at Greystones (counts only)

No code. Confirms that live HERE results still reach every category after the floor and the cap, so DN-M2-002
starts from working lists. Nothing from the results goes into tracked files.

- [ ] **Step 1: Install and place the emulator**

```bash
adb -s emulator-5554 get-state
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain -q
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
adb -s emulator-5554 emu geo fix -6.0633 53.1440; sleep 3; adb -s emulator-5554 emu geo fix -6.0633 53.1440
adb -s emulator-5554 shell am start -S -n com.kanyandula.discovernearby/.ui.MainActivity
```

Expected: `device`; the Discover grid in Park. If the emulator isn't running, stop and ask the user to start
`AAOS_AOSP_33_userdebug`.

- [ ] **Step 2: Open each category, count the rows**

For each of Coffee, Food, Outdoors, Family, Scenic, Explore: tap the tile, wait 5 s, take
`adb -s emulator-5554 exec-out screencap -p > "$S/<category>.png"` (`S` = the session scratchpad), read the
screenshot, record the number of rows and the state (Content, Empty, Error), then Back.

Expected: no crash and no endless loading; each category shows Content or Empty. Outdoors, Family, Scenic and Explore
never show three rows of one kind.

- [ ] **Step 3: Record the counts in the ticket**

Append to the ticket's Notes: the date, location (Greystones), and per category the row count and state. Counts and
states only.

---

## After the tasks

- The final whole-branch review on the most capable model (executing-plans), then `simplify` on the `app/` diff.
- PR with `pr-description`: DN-M2-001, its acceptance criteria, the three Product decisions, the live counts.
- Step 6 after merge:
  - verify MERGED in its own call;
  - DN-M2-001 `done`;
  - NOW.md and BACKLOG.md;
  - DN-M2-002 (the benchmark) becomes the next ticket.
