# DN-M0-003 Domain Models, Category Configuration and Fake Places Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provider-neutral domain types, data-driven category configuration, the `PlacesRepository` / `LocationProvider` seams, and a deterministic `FakePlacesRepository` covering all six categories at the three test locations plus empty, sparse, null-heavy, slow and failing cases — wired into `AppContainer` so DN-M0-004 can build the Recommendations flow on it.

**Architecture:** Pure Kotlin in `model/`, `discovery/`, `places/`, `location/` (no Android, Compose or `android.car`; `ArchitectureRulesTest` enforces it). Types follow docs/03 §7, §8, §10, §15 verbatim. The fake builds its places from per-category templates placed around each test location, rotating offsets per location so results differ by location. A `FakeScenario` selects the behaviour.

**Tech Stack:** Kotlin, kotlinx.coroutines (`delay`), JUnit 4 + kotlinx-coroutines-test (`runTest`, virtual time). No new dependencies.

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-003-domain-and-fake-data.md`; `docs/03-discover-nearby-engineering-implementation-plan.md` §2 (seams), §3 (structure), §7 (location, test locations), §8 (repository, domain model), §9 (categories), §10 (Recommendation), §15 (DiscoveryContext, timeout), §16, §20 (fakes); `docs/01-discover-nearby-poc-brief.md` §9–§11.

## Global Constraints

- Types and names exactly as docs/03 §7, §8, §10, §15 (`GeoPoint`, `AttributeType`, `AttributeSource`, `PlaceAttribute`, `PlaceSummary`, `PlaceDetails`, `Recommendation`, `DiscoveryContext`, `LocationResult`, `LocationProvider`, `PlacesRepository`, `ProviderFailure`, `NetworkUnavailable`).
- `DiscoveryCategory` already exists (DN-M0-002): extend, never re-create.
- `model/`, `discovery/`, `places/`, `location/` import neither Compose nor `android.car` (docs/03 §2; `ArchitectureRulesTest`).
- Category radius and desired-result count are data (`CategoryConfigs` map), not code branches (ticket AC; docs/03 §2). Radii: Coffee 5 km, Food 5 km, Outdoors 20 km, Family 15 km, Scenic 30 km, Explore 15 km (docs/03 §9); desired results 5 (`DESIRED_RECOMMENDATIONS`, docs/03 §6).
- Test locations exactly: Greystones 53.1440, -6.0633; Dublin 53.3498, -6.2603; Galway 53.2707, -9.0568 (docs/03 §7), stored once.
- Fake data deterministic: no randomness, no clock.
- Unknown data is neutral and attributes are only PROVIDED or DERIVED (docs/01 §10); "unavailable" = absent.
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes; never commit on local `main`; no AI attribution.

## Open decisions (recorded here, in the ticket and the PR)

1. **`RecommendationEngine` and `DiscoverUseCase` are not in this ticket.** docs/03 §3 lists them under `discovery/`, but this ticket's AC covers models, config, interfaces and fakes only. DN-M0-004 needs both (its AC says "the engine returns the full ranked list"); its ticket is updated in Task 4 to own the interface and a first deterministic implementation, with DN-M2-001 completing the scoring.
2. **`AndroidLocationProvider` is DN-M0-006.** Here `LocationProvider` gets a `FakeLocationProvider` (default: Greystones) so DN-M0-004 can run before the permission flow exists.
3. **Fake place names are fictional and carry the town** ("Harbour Roasters, Greystones") so a location change is visible in M0 demos (docs/04 Scenario B). They are not benchmark data; DN-M2-002 judges live provider data.
4. **Slow means slower than the timeout:** the slow fake waits 10 s, longer than docs/03 §15's ~8 s provider timeout, so DN-M0-004 can exercise `Error(Timeout)`.
5. **`GeoPoint.distanceMetersTo` (haversine) lives in `model/`**: the fake filters by radius with it now, and the engine needs it for `Recommendation.distanceMeters`.

## Review Focus

1. **A test location or radius silently wrong**, so every later distance is off. Pinned by `TestLocationTest.coordinatesMatchTheDocs` and `CategoryConfigTest.radiiMatchTheDocs` (literal values).
2. **Fake results that ignore location**, so docs/04 Scenario B (location change) cannot be demonstrated. Pinned by `FakePlacesRepositoryTest.resultsDifferByLocation`.
3. **Fake data that never exercises the capability-driven paths** (null rating, unknown open state, known-closed, PROVIDED vs DERIVED). Pinned by `normalDataExercisesUnknownAndClosedAndProvenance`.
4. **An origin far from every test location returning another town's places.** Pinned by `originFarFromTestLocationsReturnsNothing`.
5. **The slow fake finishing before the timeout DN-M0-004 will use.** Pinned by `slowScenarioTakesLongerThanTheProviderTimeout` (virtual time).

---

## Investigation findings (2026-10-04)

- docs/03 gives complete definitions for every type this ticket needs (§7 `LocationResult`/`LocationProvider`, §8 `PlacesRepository` and the domain model, §10 `Recommendation`, §15 `DiscoveryContext`); §16 names `ProviderFailure` and `NetworkUnavailable` as the provider's domain exceptions; §20 lists the fake scenarios (success, empty, sparse, null-heavy, slow, failing).
- docs/03 §9 target kinds per category, normalised for this plan: Coffee `cafe`, `coffee_shop`; Food `restaurant`, `fast_food`, `takeaway`; Outdoors `park`, `trail`, `forest`, `beach`, `hiking_area`, `outdoor_attraction`; Family `playground`, `zoo`, `aquarium`, `family_attraction`, `park`; Scenic `viewpoint`, `scenic_spot`, `coastal_overlook`, `landmark`, `waterfall`, `natural_attraction`; Explore `tourist_attraction`, `museum`, `landmark`, `heritage_site`.
- Haversine reference distances (R = 6 371 008.8 m): Greystones→Dublin 26 372 m, Greystones→Galway 199 841 m.
- `AppContainer` is an empty class created by `DiscoverApplication.onCreate`; `DiscoverApplicationTest` asserts it exists.
- detekt `MagicNumber` ignores property declarations but not literals nested in calls or enum arguments; fixture files may need a file-level suppression (Task 2, Step 7 decides by running detekt).

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `app/src/main/java/com/kanyandula/discovernearby/model/GeoPoint.kt` | Create | Coordinates + haversine distance |
| `…/model/PlaceAttribute.kt` | Create | `AttributeType`, `AttributeSource`, `PlaceAttribute` |
| `…/model/Place.kt` | Create | `PlaceSummary`, `PlaceDetails` |
| `…/model/Recommendation.kt` | Create | `Recommendation` |
| `…/discovery/DiscoveryContext.kt` | Create | Single input to ranking |
| `…/discovery/CategoryConfig.kt` | Create | `CategoryConfig`, `CategoryConfigs`, `DESIRED_RECOMMENDATIONS` |
| `…/places/PlacesRepository.kt` | Create | Repository interface |
| `…/places/PlacesException.kt` | Create | `ProviderFailure`, `NetworkUnavailable` |
| `…/places/fake/TestLocation.kt` | Create | The three test locations |
| `…/places/fake/FakePlaces.kt` | Create | Per-category templates → `PlaceSummary` lists |
| `…/places/fake/FakePlacesRepository.kt` | Create | `FakeScenario` + repository |
| `…/location/LocationProvider.kt` | Create | `LocationResult`, `LocationProvider` |
| `…/location/fake/FakeLocationProvider.kt` | Create | Fixed result, default Greystones |
| `…/AppContainer.kt` | Modify | Wire the fakes |
| `app/src/test/java/com/kanyandula/discovernearby/model/GeoPointTest.kt` | Create | Distance |
| `…/discovery/CategoryConfigTest.kt` | Create | Config matches docs |
| `…/places/fake/TestLocationTest.kt` | Create | Coordinates, nearest match |
| `…/places/fake/FakePlacesRepositoryTest.kt` | Create | All scenarios |
| `…/DiscoverApplicationTest.kt` | Modify | Container provides the fakes |

(`…` = `app/src/main/java/com/kanyandula/discovernearby` or the matching `app/src/test/java/...` path.)

---

### Task 0: Start the ticket

- [ ] **Step 1:** In the ticket set `status: in_progress`, `branch: dn-m0-003-domain-and-fakes`.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-003-domain-and-fakes
git add docs/superpowers/plans/2026-10-04-dn-m0-003-domain-and-fakes.md
git commit -m "Add DN-M0-003 implementation plan"
```

---

### Task 1: Domain model and category configuration

**Interfaces:**
- Produces: `data class GeoPoint(val lat: Double, val lng: Double)` with `fun distanceMetersTo(other: GeoPoint): Int`; `AttributeType`, `AttributeSource`, `PlaceAttribute`, `PlaceSummary`, `PlaceDetails`, `Recommendation` (docs/03 §8, §10); `data class DiscoveryContext(requestId: Long, origin: GeoPoint, category: DiscoveryCategory, createdAtMillis: Long)`; `data class CategoryConfig(radiusMeters: Int, desiredResults: Int, targetKinds: Set<String>)`; `val CategoryConfigs: Map<DiscoveryCategory, CategoryConfig>`; `const val DESIRED_RECOMMENDATIONS = 5`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kanyandula/discovernearby/model/GeoPointTest.kt`:

```kotlin
package com.kanyandula.discovernearby.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoPointTest {

    private val greystones = GeoPoint(53.1440, -6.0633)
    private val dublin = GeoPoint(53.3498, -6.2603)
    private val galway = GeoPoint(53.2707, -9.0568)

    @Test
    fun distanceIsGreatCircleMetres() {
        assertEquals(26_372.0, greystones.distanceMetersTo(dublin).toDouble(), 30.0)
        assertEquals(199_841.0, greystones.distanceMetersTo(galway).toDouble(), 200.0)
    }

    @Test
    fun distanceIsSymmetricAndZeroToItself() {
        assertEquals(greystones.distanceMetersTo(dublin), dublin.distanceMetersTo(greystones))
        assertEquals(0, greystones.distanceMetersTo(greystones))
    }
}
```

`app/src/test/java/com/kanyandula/discovernearby/discovery/CategoryConfigTest.kt`:

```kotlin
package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryConfigTest {

    @Test
    fun everyCategoryIsConfigured() {
        assertEquals(DiscoveryCategory.entries.toSet(), CategoryConfigs.keys)
    }

    // docs/03 §9 illustrative radii.
    @Test
    fun radiiMatchTheDocs() {
        val km = mapOf(COFFEE to 5, FOOD to 5, OUTDOORS to 20, FAMILY to 15, SCENIC to 30, EXPLORE to 15)
        km.forEach { (category, value) ->
            assertEquals(category.name, value * 1_000, CategoryConfigs.getValue(category).radiusMeters)
        }
    }

    @Test
    fun everyCategoryWantsFiveResults() {
        assertEquals(5, DESIRED_RECOMMENDATIONS)
        CategoryConfigs.values.forEach { assertEquals(DESIRED_RECOMMENDATIONS, it.desiredResults) }
    }

    // docs/03 §9 target kinds; Outdoors (do) and Scenic (look) stay distinct.
    @Test
    fun targetKindsFollowTheDocs() {
        assertEquals(setOf("cafe", "coffee_shop"), CategoryConfigs.getValue(COFFEE).targetKinds)
        assertTrue("park" in CategoryConfigs.getValue(OUTDOORS).targetKinds)
        assertTrue("viewpoint" in CategoryConfigs.getValue(SCENIC).targetKinds)
        assertTrue("viewpoint" !in CategoryConfigs.getValue(OUTDOORS).targetKinds)
        assertTrue("playground" in CategoryConfigs.getValue(FAMILY).targetKinds)
        assertTrue("museum" in CategoryConfigs.getValue(EXPLORE).targetKinds)
        assertTrue(CategoryConfigs.values.all { it.targetKinds.isNotEmpty() })
    }
}
```

- [ ] **Step 2: Run and watch them fail**

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "^e: |BUILD" | head -3`
Expected: compile failure (`Unresolved reference 'GeoPoint'`, `'CategoryConfigs'`).

- [ ] **Step 3: Model types**

`app/src/main/java/com/kanyandula/discovernearby/model/GeoPoint.kt`:

```kotlin
package com.kanyandula.discovernearby.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lng: Double) {

    /** Great-circle (haversine) distance in metres. */
    fun distanceMetersTo(other: GeoPoint): Int {
        val dLat = Math.toRadians(other.lat - lat)
        val dLng = Math.toRadians(other.lng - lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLng / 2).pow(2)
        return (2 * EARTH_RADIUS_METERS * asin(sqrt(h))).roundToInt()
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_008.8
    }
}
```

`…/model/PlaceAttribute.kt`:

```kotlin
package com.kanyandula.discovernearby.model

enum class AttributeType {
    PARKING, TOILETS, CAFE, PLAYGROUND, TRAILS, BEACH,
    VIEWPOINT, MUSEUM, FAMILY_FRIENDLY, DRIVE_THROUGH,
}

enum class AttributeSource {
    PROVIDED, // provider states it
    DERIVED, // reliably inferred from place type
}
// "Unavailable" = attribute absent from the set. It is never displayed.

data class PlaceAttribute(
    val type: AttributeType,
    val source: AttributeSource,
)
```

`…/model/Place.kt`:

```kotlin
package com.kanyandula.discovernearby.model

/** List-level fields only (docs/03 §8): keeps searches small and each field's cost visible. */
data class PlaceSummary(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val placeKinds: Set<String>, // normalised kinds, e.g. "park", "playground"
    val primaryKind: String?,
    val attributes: Set<PlaceAttribute>,
    val rating: Double?, // null if provider lacks it
    val ratingCount: Int?, // null if provider lacks it
    val isOpenNow: Boolean?, // null = unknown
    val travelTimeMinutes: Int?, // only if provider supplies it
)

/** Richer fields, fetched only when a place is opened. */
data class PlaceDetails(
    val summary: PlaceSummary,
    val openingSummary: String?,
    val attribution: String?,
)
```

`…/model/Recommendation.kt`:

```kotlin
package com.kanyandula.discovernearby.model

data class Recommendation(
    val place: PlaceSummary,
    val score: Double,
    val distanceMeters: Int,
    val travelTimeMinutes: Int?, // provider-supplied or calculated
    val minutesAhead: Int?, // stretch
    val detourMinutes: Int?, // stretch
)
```

- [ ] **Step 4: Discovery types**

`…/discovery/DiscoveryContext.kt`:

```kotlin
package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.model.GeoPoint

/** The single input to ranking (docs/03 §2, §15). New signals arrive as new optional fields. */
data class DiscoveryContext(
    val requestId: Long,
    val origin: GeoPoint,
    val category: DiscoveryCategory,
    val createdAtMillis: Long,
)
```

`…/discovery/CategoryConfig.kt`:

```kotlin
package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC

/** Up to this many recommendations are shown, before the driving list limit (docs/03 §6). */
const val DESIRED_RECOMMENDATIONS = 5

/** Per-category tuning as data, not code branches (docs/03 §2). DN-M2-001 adds ranking weights here. */
data class CategoryConfig(
    val radiusMeters: Int,
    val desiredResults: Int,
    val targetKinds: Set<String>,
)

// Radii: docs/03 §9 illustrative values. Kinds: docs/03 §9, normalised to snake_case.
val CategoryConfigs: Map<DiscoveryCategory, CategoryConfig> = mapOf(
    COFFEE to CategoryConfig(5_000, DESIRED_RECOMMENDATIONS, setOf("cafe", "coffee_shop")),
    FOOD to CategoryConfig(5_000, DESIRED_RECOMMENDATIONS, setOf("restaurant", "fast_food", "takeaway")),
    OUTDOORS to CategoryConfig(
        20_000,
        DESIRED_RECOMMENDATIONS,
        setOf("park", "trail", "forest", "beach", "hiking_area", "outdoor_attraction"),
    ),
    FAMILY to CategoryConfig(
        15_000,
        DESIRED_RECOMMENDATIONS,
        setOf("playground", "zoo", "aquarium", "family_attraction", "park"),
    ),
    SCENIC to CategoryConfig(
        30_000,
        DESIRED_RECOMMENDATIONS,
        setOf("viewpoint", "scenic_spot", "coastal_overlook", "landmark", "waterfall", "natural_attraction"),
    ),
    EXPLORE to CategoryConfig(
        15_000,
        DESIRED_RECOMMENDATIONS,
        setOf("tourist_attraction", "museum", "landmark", "heritage_site"),
    ),
)
```

- [ ] **Step 5: Run the tests, then detekt**

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "FAILED|BUILD"` → `BUILD SUCCESSFUL`; `GeoPointTest` 2/2, `CategoryConfigTest` 4/4.
Run: `./gradlew detekt --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|BUILD"`. If `[MagicNumber]` is reported for the radii in `CategoryConfig.kt`, add `@file:Suppress("MagicNumber") // Tuning data from docs/03 §9.` as the file's first line and record the ruling; rerun until `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/model app/src/main/java/com/kanyandula/discovernearby/discovery \
  app/src/test/java/com/kanyandula/discovernearby/model app/src/test/java/com/kanyandula/discovernearby/discovery
git commit -m "Add the domain model and data-driven category config

GeoPoint (haversine distance), PlaceAttribute with PROVIDED/DERIVED
provenance, PlaceSummary, PlaceDetails, Recommendation and
DiscoveryContext as in docs/03; per-category radius, desired results
and target kinds as a CategoryConfigs map."
```

---

### Task 2: Repository and location seams, and the fake places

**Interfaces:**
- Consumes: Task 1 types, `CategoryConfigs`.
- Produces: `interface PlacesRepository { suspend fun searchNearby(origin: GeoPoint, category: DiscoveryCategory, radiusMeters: Int): List<PlaceSummary>; suspend fun getPlaceDetails(placeId: String): PlaceDetails }`; `sealed class PlacesException`, `class ProviderFailure`, `class NetworkUnavailable`; `sealed interface LocationResult { Available(point), PermissionMissing, Unavailable }`; `interface LocationProvider { suspend fun currentLocation(): LocationResult }`; `class FakeLocationProvider(result: LocationResult = Available(TestLocation.GREYSTONES.point))`; `enum class TestLocation(label, point)` with `nearestTo(origin): TestLocation?`; `enum class FakeScenario { NORMAL, EMPTY, SPARSE, NULL_HEAVY, SLOW, PROVIDER_FAILURE, NETWORK_FAILURE, DETAILS_FAILURE }`; `class FakePlacesRepository(scenario: FakeScenario = NORMAL, slowDelayMillis: Long = SLOW_DELAY_MILLIS)`; `const val SLOW_DELAY_MILLIS = 10_000L`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kanyandula/discovernearby/places/fake/TestLocationTest.kt`:

```kotlin
package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestLocationTest {

    // docs/03 §7: stored once, never re-typed.
    @Test
    fun coordinatesMatchTheDocs() {
        assertEquals(GeoPoint(53.1440, -6.0633), TestLocation.GREYSTONES.point)
        assertEquals(GeoPoint(53.3498, -6.2603), TestLocation.DUBLIN.point)
        assertEquals(GeoPoint(53.2707, -9.0568), TestLocation.GALWAY.point)
    }

    @Test
    fun nearestToFindsTheTownAnOriginIsIn() {
        assertEquals(TestLocation.GREYSTONES, TestLocation.nearestTo(GeoPoint(53.15, -6.07)))
        assertEquals(TestLocation.GALWAY, TestLocation.nearestTo(GeoPoint(53.27, -9.05)))
    }

    @Test
    fun nearestToIsNullFarFromEveryTestLocation() {
        assertNull(TestLocation.nearestTo(GeoPoint(51.5074, -0.1278))) // London
    }
}
```

`app/src/test/java/com/kanyandula/discovernearby/places/fake/FakePlacesRepositoryTest.kt`:

```kotlin
package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.discovery.CategoryConfigs
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.AttributeSource
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.ProviderFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePlacesRepositoryTest {

    private val normal = FakePlacesRepository()

    private suspend fun FakePlacesRepository.at(location: TestLocation, category: DiscoveryCategory) =
        searchNearby(location.point, category, CategoryConfigs.getValue(category).radiusMeters)

    private suspend fun allNormal() = TestLocation.entries.flatMap { l ->
        DiscoveryCategory.entries.flatMap { c -> normal.at(l, c) }
    }

    @Test
    fun normalScenarioCoversEveryCategoryAtEveryTestLocation() = runTest {
        TestLocation.entries.forEach { location ->
            DiscoveryCategory.entries.forEach { category ->
                val places = normal.at(location, category)
                val cell = "$location × $category"
                val config = CategoryConfigs.getValue(category)
                assertTrue("$cell has at least three places", places.size >= 3)
                val withinRadius = places.all { location.point.distanceMetersTo(it.location) <= config.radiusMeters }
                assertTrue("$cell within radius", withinRadius)
                assertTrue("$cell fits the category", places.all { it.placeKinds.any { k -> k in config.targetKinds } })
            }
        }
    }

    @Test
    fun resultsAreDeterministic() = runTest {
        assertEquals(allNormal(), allNormal())
    }

    @Test
    fun idsAreUnique() = runTest {
        val ids = allNormal().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun resultsDifferByLocation() = runTest {
        val greystones = normal.at(TestLocation.GREYSTONES, DiscoveryCategory.COFFEE).map { it.name }.toSet()
        val dublin = normal.at(TestLocation.DUBLIN, DiscoveryCategory.COFFEE).map { it.name }.toSet()
        assertTrue(greystones.intersect(dublin).isEmpty())
    }

    @Test
    fun normalDataExercisesUnknownAndClosedAndProvenance() = runTest {
        val places = allNormal()
        assertTrue("some rating unknown", places.any { it.rating == null })
        assertTrue("some open state unknown", places.any { it.isOpenNow == null })
        assertTrue("some known closed", places.any { it.isOpenNow == false })
        assertTrue("some PROVIDED", places.any { p -> p.attributes.any { it.source == AttributeSource.PROVIDED } })
        assertTrue("some DERIVED", places.any { p -> p.attributes.any { it.source == AttributeSource.DERIVED } })
    }

    @Test
    fun originFarFromTestLocationsReturnsNothing() = runTest {
        assertTrue(normal.searchNearby(GeoPoint(51.5074, -0.1278), DiscoveryCategory.COFFEE, 30_000).isEmpty())
    }

    @Test
    fun radiusLimitsResults() = runTest {
        val origin = TestLocation.GREYSTONES.point
        val wide = normal.searchNearby(origin, DiscoveryCategory.COFFEE, 5_000)
        val narrow = normal.searchNearby(origin, DiscoveryCategory.COFFEE, 500)
        assertTrue(narrow.size < wide.size)
        assertTrue(narrow.all { origin.distanceMetersTo(it.location) <= 500 })
    }

    @Test
    fun emptyScenarioReturnsNothing() = runTest {
        assertTrue(FakePlacesRepository(FakeScenario.EMPTY).at(TestLocation.DUBLIN, DiscoveryCategory.FOOD).isEmpty())
    }

    @Test
    fun sparseScenarioReturnsOneOrTwo() = runTest {
        val size = FakePlacesRepository(FakeScenario.SPARSE).at(TestLocation.DUBLIN, DiscoveryCategory.SCENIC).size
        assertTrue(size in 1..2)
    }

    @Test
    fun nullHeavyScenarioDropsEveryOptionalField() = runTest {
        val places = FakePlacesRepository(FakeScenario.NULL_HEAVY).at(TestLocation.GALWAY, DiscoveryCategory.FAMILY)
        assertTrue(places.isNotEmpty())
        assertTrue(places.all { it.rating == null && it.ratingCount == null && it.isOpenNow == null })
        assertTrue(places.all { it.attributes.isEmpty() && it.primaryKind == null && it.travelTimeMinutes == null })
    }

    // Longer than docs/03 §15's ~8 s provider timeout, so DN-M0-004 can reach Error(Timeout).
    @Test
    fun slowScenarioTakesLongerThanTheProviderTimeout() = runTest {
        val slow = FakePlacesRepository(FakeScenario.SLOW)
        val result = async { slow.at(TestLocation.GREYSTONES, DiscoveryCategory.COFFEE) }
        advanceTimeBy(SLOW_DELAY_MILLIS - 1)
        assertFalse(result.isCompleted)
        advanceTimeBy(2)
        assertTrue(result.isCompleted)
        assertTrue(SLOW_DELAY_MILLIS > 8_000)
    }

    @Test
    fun failureScenariosThrowTheDomainExceptions() = runTest {
        suspend fun failureOf(scenario: FakeScenario) =
            runCatching { FakePlacesRepository(scenario).at(TestLocation.DUBLIN, DiscoveryCategory.COFFEE) }
                .exceptionOrNull()
        val provider = failureOf(FakeScenario.PROVIDER_FAILURE)
        val network = failureOf(FakeScenario.NETWORK_FAILURE)
        assertTrue(provider is ProviderFailure)
        assertTrue(network is NetworkUnavailable)
    }

    @Test
    fun detailsMatchTheSummary() = runTest {
        val summary = normal.at(TestLocation.GREYSTONES, DiscoveryCategory.FAMILY).first()
        assertEquals(summary, normal.getPlaceDetails(summary.id).summary)
    }

    @Test
    fun detailsFailureScenarioSearchesButFailsDetails() = runTest {
        val repo = FakePlacesRepository(FakeScenario.DETAILS_FAILURE)
        val summary = repo.at(TestLocation.GREYSTONES, DiscoveryCategory.FAMILY).first()
        assertTrue(runCatching { repo.getPlaceDetails(summary.id) }.exceptionOrNull() is ProviderFailure)
    }

    @Test
    fun unknownPlaceIdIsAProviderFailure() = runTest {
        assertTrue(runCatching { normal.getPlaceDetails("no-such-place") }.exceptionOrNull() is ProviderFailure)
    }
}
```

- [ ] **Step 2: Run and watch them fail**

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "^e: |BUILD" | head -3`
Expected: compile failure (`Unresolved reference 'TestLocation'`, `'FakePlacesRepository'`, …).

- [ ] **Step 3: Repository interface and domain exceptions**

`…/places/PlacesRepository.kt`:

```kotlin
package com.kanyandula.discovernearby.places

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary

/** Provider-neutral POI access (docs/03 §8). Implementations throw [PlacesException] subtypes on failure. */
interface PlacesRepository {
    suspend fun searchNearby(
        origin: GeoPoint,
        category: DiscoveryCategory,
        radiusMeters: Int,
    ): List<PlaceSummary>

    suspend fun getPlaceDetails(
        placeId: String,
    ): PlaceDetails
}
```

`…/places/PlacesException.kt`:

```kotlin
package com.kanyandula.discovernearby.places

/** Domain failures a [PlacesRepository] reports; the use case maps them to UI state (docs/03 §8, §16). */
sealed class PlacesException(message: String) : Exception(message)

class ProviderFailure(message: String = "Place provider failed") : PlacesException(message)

class NetworkUnavailable(message: String = "Network unavailable") : PlacesException(message)
```

- [ ] **Step 4: Location seam**

`…/location/LocationProvider.kt`:

```kotlin
package com.kanyandula.discovernearby.location

import com.kanyandula.discovernearby.model.GeoPoint

sealed interface LocationResult {
    data class Available(val point: GeoPoint) : LocationResult
    data object PermissionMissing : LocationResult
    data object Unavailable : LocationResult
}

/** Read only when the user requests discovery (docs/01 §14). AndroidLocationProvider arrives in DN-M0-006. */
interface LocationProvider {
    suspend fun currentLocation(): LocationResult
}
```

`…/location/fake/FakeLocationProvider.kt`:

```kotlin
package com.kanyandula.discovernearby.location.fake

import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.places.fake.TestLocation

/** Always returns [result]; the default stands at test location A until DN-M0-006 reads the real one. */
class FakeLocationProvider(
    private val result: LocationResult = LocationResult.Available(TestLocation.GREYSTONES.point),
) : LocationProvider {
    override suspend fun currentLocation(): LocationResult = result
}
```

- [ ] **Step 5: Test locations**

`…/places/fake/TestLocation.kt`:

```kotlin
package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.model.GeoPoint

/** docs/03 §7 test locations A–C, stored once. */
enum class TestLocation(val label: String, val point: GeoPoint) {
    GREYSTONES("Greystones", GeoPoint(53.1440, -6.0633)),
    DUBLIN("Dublin", GeoPoint(53.3498, -6.2603)),
    GALWAY("Galway", GeoPoint(53.2707, -9.0568)),
    ;

    companion object {
        private const val MATCH_RADIUS_METERS = 30_000

        /** The test location whose fake data serves [origin], or null when none is within reach. */
        fun nearestTo(origin: GeoPoint): TestLocation? =
            entries.minBy { it.point.distanceMetersTo(origin) }
                .takeIf { it.point.distanceMetersTo(origin) <= MATCH_RADIUS_METERS }
    }
}
```

- [ ] **Step 6: Fake data and repository**

`…/places/fake/FakePlaces.kt`:

```kotlin
package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC
import com.kanyandula.discovernearby.model.AttributeSource
import com.kanyandula.discovernearby.model.AttributeSource.DERIVED
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType
import com.kanyandula.discovernearby.model.AttributeType.BEACH
import com.kanyandula.discovernearby.model.AttributeType.CAFE
import com.kanyandula.discovernearby.model.AttributeType.DRIVE_THROUGH
import com.kanyandula.discovernearby.model.AttributeType.FAMILY_FRIENDLY
import com.kanyandula.discovernearby.model.AttributeType.MUSEUM
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.AttributeType.PLAYGROUND
import com.kanyandula.discovernearby.model.AttributeType.TOILETS
import com.kanyandula.discovernearby.model.AttributeType.TRAILS
import com.kanyandula.discovernearby.model.AttributeType.VIEWPOINT
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.PlaceSummary

/** One fictional place, placed at an offset (degrees) from a test location. */
private class Template(
    val name: String,
    val kinds: Set<String>,
    val dLat: Double,
    val dLng: Double,
    val attributes: Set<PlaceAttribute> = emptySet(),
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val isOpenNow: Boolean? = null,
)

private fun attrs(vararg pairs: Pair<AttributeType, AttributeSource>) =
    pairs.map { (type, source) -> PlaceAttribute(type, source) }.toSet()

// Mixes known and unknown ratings and open states, one known-closed place for time-sensitive categories,
// and PROVIDED vs DERIVED attributes, so later ranking meets every capability case (docs/03 §10).
private val Templates: Map<DiscoveryCategory, List<Template>> = mapOf(
    COFFEE to listOf(
        Template("Harbour Roasters", setOf("cafe"), 0.004, 0.003, attrs(PARKING to PROVIDED), 4.6, 212, true),
        Template(
            "The Daily Grind", setOf("coffee_shop"), -0.008, 0.010,
            attrs(DRIVE_THROUGH to PROVIDED), 4.4, 128, true,
        ),
        Template("Brew & Bloom", setOf("cafe"), 0.015, -0.012),
        Template("Corner Café", setOf("cafe"), 0.002, -0.004, rating = 4.1, ratingCount = 40, isOpenNow = false),
        Template(
            "Station Espresso", setOf("coffee_shop"), 0.030, 0.020,
            rating = 3.9, ratingCount = 15, isOpenNow = true,
        ),
    ),
    FOOD to listOf(
        Template("Seaview Kitchen", setOf("restaurant"), 0.006, -0.005, attrs(PARKING to PROVIDED), 4.5, 300, true),
        Template("Fish & Chips Co.", setOf("takeaway"), 0.012, 0.008, rating = 4.2, ratingCount = 90, isOpenNow = true),
        Template("Olive Tree Bistro", setOf("restaurant"), -0.010, -0.015),
        Template("Burger Barn", setOf("fast_food"), 0.020, 0.025, attrs(DRIVE_THROUGH to PROVIDED), 3.8, 60, false),
    ),
    OUTDOORS to listOf(
        Template("Riverside Park", setOf("park"), 0.020, 0.030, attrs(PARKING to PROVIDED), 4.5, 400, true),
        Template("Glen Forest Walk", setOf("forest", "trail"), 0.060, -0.040, attrs(TRAILS to DERIVED), 4.7, 150),
        Template("North Beach", setOf("beach"), -0.050, 0.070, attrs(BEACH to DERIVED, PARKING to PROVIDED), 4.3, 220),
        Template("Hilltop Trail", setOf("trail", "hiking_area"), 0.100, 0.050, attrs(TRAILS to DERIVED)),
    ),
    FAMILY to listOf(
        Template(
            "Adventure Playground", setOf("playground"), 0.010, 0.012,
            attrs(PLAYGROUND to DERIVED, TOILETS to PROVIDED, PARKING to PROVIDED), 4.6, 180, true,
        ),
        Template(
            "Seal Rescue Aquarium", setOf("aquarium"), 0.045, -0.030,
            attrs(FAMILY_FRIENDLY to DERIVED, CAFE to PROVIDED), 4.4, 520, true,
        ),
        Template("Meadow Park", setOf("park", "playground"), -0.030, 0.040, attrs(PLAYGROUND to DERIVED), 4.5, 400),
        Template("Little Acres Farm", setOf("family_attraction"), 0.070, 0.060, attrs(FAMILY_FRIENDLY to DERIVED)),
    ),
    SCENIC to listOf(
        Template(
            "Cliff Viewpoint", setOf("viewpoint"), 0.080, 0.090,
            attrs(VIEWPOINT to DERIVED, PARKING to PROVIDED), 4.8, 260,
        ),
        Template(
            "Waterfall Glen", setOf("waterfall", "natural_attraction"), 0.150, -0.100,
            rating = 4.7, ratingCount = 310,
        ),
        Template("Harbour Lighthouse", setOf("landmark", "coastal_overlook"), -0.120, 0.150),
    ),
    EXPLORE to listOf(
        Template("County Museum", setOf("museum"), 0.008, 0.010, attrs(MUSEUM to DERIVED), 4.5, 140, true),
        Template("Old Abbey", setOf("heritage_site"), 0.040, -0.035, rating = 4.3, ratingCount = 75),
        Template("Market Square", setOf("tourist_attraction"), -0.050, 0.030),
    ),
)

/**
 * The fake places for [category] around [location]. Offsets rotate by location so the nearest place
 * differs per town, and names carry the town so a location change is visible (docs/04 Scenario B).
 */
internal fun fakePlaces(location: TestLocation, category: DiscoveryCategory): List<PlaceSummary> {
    val templates = Templates.getValue(category)
    return templates.mapIndexed { i, template ->
        val offset = templates[(i + location.ordinal) % templates.size]
        PlaceSummary(
            id = "${location.name.lowercase()}-${category.name.lowercase()}-$i",
            name = "${template.name}, ${location.label}",
            location = GeoPoint(location.point.lat + offset.dLat, location.point.lng + offset.dLng),
            placeKinds = template.kinds,
            primaryKind = template.kinds.first(),
            attributes = template.attributes,
            rating = template.rating,
            ratingCount = template.ratingCount,
            isOpenNow = template.isOpenNow,
            travelTimeMinutes = null,
        )
    }
}
```

`…/places/fake/FakePlacesRepository.kt`:

```kotlin
package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.ProviderFailure
import kotlinx.coroutines.delay

/** Longer than docs/03 §15's ~8 s provider timeout, so the slow case reaches Error(Timeout). */
const val SLOW_DELAY_MILLIS = 10_000L

/** docs/03 §20 fake cases, plus a details-only failure for the summary-only fallback (docs/04 R). */
enum class FakeScenario { NORMAL, EMPTY, SPARSE, NULL_HEAVY, SLOW, PROVIDER_FAILURE, NETWORK_FAILURE, DETAILS_FAILURE }

/** Deterministic stand-in for the provider until ADR-001 (M1). */
class FakePlacesRepository(
    private val scenario: FakeScenario = FakeScenario.NORMAL,
    private val slowDelayMillis: Long = SLOW_DELAY_MILLIS,
) : PlacesRepository {

    private val byId: Map<String, PlaceSummary> by lazy {
        TestLocation.entries
            .flatMap { location -> DiscoveryCategory.entries.flatMap { fakePlaces(location, it) } }
            .associateBy { it.id }
    }

    override suspend fun searchNearby(
        origin: GeoPoint,
        category: DiscoveryCategory,
        radiusMeters: Int,
    ): List<PlaceSummary> {
        when (scenario) {
            FakeScenario.PROVIDER_FAILURE -> throw ProviderFailure()
            FakeScenario.NETWORK_FAILURE -> throw NetworkUnavailable()
            FakeScenario.SLOW -> delay(slowDelayMillis)
            else -> Unit
        }
        val location = TestLocation.nearestTo(origin) ?: return emptyList()
        val places = fakePlaces(location, category).filter { origin.distanceMetersTo(it.location) <= radiusMeters }
        return when (scenario) {
            FakeScenario.EMPTY -> emptyList()
            FakeScenario.SPARSE -> places.take(SPARSE_COUNT)
            FakeScenario.NULL_HEAVY -> places.map { it.withoutOptionalFields() }
            else -> places
        }
    }

    override suspend fun getPlaceDetails(placeId: String): PlaceDetails {
        if (scenario == FakeScenario.DETAILS_FAILURE) throw ProviderFailure("Details unavailable")
        val summary = byId[placeId] ?: throw ProviderFailure("Unknown place $placeId")
        return if (scenario == FakeScenario.NULL_HEAVY) {
            PlaceDetails(summary.withoutOptionalFields(), openingSummary = null, attribution = null)
        } else {
            val opening = if (summary.isOpenNow == true) "Open until 18:00" else null
            PlaceDetails(summary, openingSummary = opening, attribution = null)
        }
    }

    private fun PlaceSummary.withoutOptionalFields() = copy(
        primaryKind = null,
        attributes = emptySet(),
        rating = null,
        ratingCount = null,
        isOpenNow = null,
        travelTimeMinutes = null,
    )

    private companion object {
        const val SPARSE_COUNT = 2
    }
}
```

- [ ] **Step 7: Run tests and detekt**

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "FAILED|BUILD"` → `BUILD SUCCESSFUL`; `TestLocationTest` 3/3, `FakePlacesRepositoryTest` 15/15, all other suites green (incl. `ArchitectureRulesTest`).
Run: `./gradlew detekt --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|BUILD"`. For `[MagicNumber]` in `FakePlaces.kt` or `TestLocation.kt`, add `@file:Suppress("MagicNumber") // Fixture data: offsets, ratings and docs/03 §7 coordinates.` as that file's first line. For `[MaxLineLength]`, wrap the template onto named-argument lines as done for "Adventure Playground". Record each change as a ruling; rerun until `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/places app/src/main/java/com/kanyandula/discovernearby/location \
  app/src/test/java/com/kanyandula/discovernearby/places
git commit -m "Add PlacesRepository, LocationProvider and the fake places

Interfaces and domain exceptions as in docs/03 §7-8. FakePlacesRepository
returns deterministic places for all six categories at the three test
locations, filtered by radius, plus empty, sparse, null-heavy, slow,
provider/network failure and details-failure scenarios."
```

---

### Task 3: Wire the fakes into AppContainer

**Interfaces:**
- Produces: `AppContainer.placesRepository: PlacesRepository`, `AppContainer.locationProvider: LocationProvider` (DN-M0-004 reads both).

- [ ] **Step 1: Failing test** — add to `DiscoverApplicationTest`:

```kotlin
    @Test
    fun containerProvidesTheM0Fakes() {
        val container = (RuntimeEnvironment.getApplication() as DiscoverApplication).container
        assertTrue(container.placesRepository is FakePlacesRepository)
        assertTrue(container.locationProvider is FakeLocationProvider)
    }
```

with imports `org.junit.Assert.assertTrue`, `com.kanyandula.discovernearby.places.fake.FakePlacesRepository`, `com.kanyandula.discovernearby.location.fake.FakeLocationProvider`.

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "^e: |BUILD"` → compile failure (`Unresolved reference 'placesRepository'`).

- [ ] **Step 3: Implement** — replace `AppContainer.kt` with:

```kotlin
package com.kanyandula.discovernearby

import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository

/**
 * The single wiring point (docs/03 §3): every app-scoped dependency is constructed here by hand.
 */
class AppContainer {
    // ponytail: fakes until the provider (M1, after ADR-001) and AndroidLocationProvider (DN-M0-006).
    val placesRepository: PlacesRepository = FakePlacesRepository()
    val locationProvider: LocationProvider = FakeLocationProvider()
}
```

- [ ] **Step 4:** Run `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|Error:|^e: |FAILED|BUILD"` → `BUILD SUCCESSFUL`; `DiscoverApplicationTest` 2/2.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt \
  app/src/test/java/com/kanyandula/discovernearby/DiscoverApplicationTest.kt
git commit -m "Wire the fake places and location into AppContainer"
```

---

### Task 4: Close out

- [ ] **Step 1:** Update `~/.claude/projects/Discover Nearby/tickets/DN-M0-004-recommendations-flow.md`: add an acceptance criterion that it defines `RecommendationEngine` (docs/03 §10 interface) with a first deterministic implementation (category match, then distance; full scoring in DN-M2-001) and `DiscoverUseCase`, consuming `AppContainer.placesRepository` / `locationProvider` and `CategoryConfigs`.

- [ ] **Step 2:** In `CLAUDE.md` "Current state", replace `Next: DN-M0-003 / 010 / 011.` with `Domain model, CategoryConfigs and fakes (DN-M0-003). Next: DN-M0-004 / 010 / 011.`; commit.

- [ ] **Step 3:** Push; draft PR; CI `build` passes.

- [ ] **Step 4:** `simplify` on the branch diff; apply, re-run the full check, commit, push, confirm CI.

- [ ] **Step 5:** Ticket completion notes: open decisions, every check run with its result.

- [ ] **Step 6:** Final whole-branch review by a fresh reviewer; fix Critical/Important with a failing test first.

- [ ] **Step 7:** `pr-description` (Ticket ID, AC table, open decisions, no AI attribution); `gh pr ready`.

- [ ] **Step 8: After the user merges** — ticket `done`; DN-M0-004 `ready` once DN-M0-010 is also done (its other dependency; DN-M0-002 and DN-M0-003 are done); `NOW.md`; delete the branch.
