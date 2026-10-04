# DN-M0-004 Recommendations Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Selecting a category shows a short, ranked list of fake places on the one Recommendations destination, with Loading, Content, Empty and Error states, trimmed live by the driving list limit, with timeout and stale-request protection, and every docs/04 §7 fake scenario selectable on the emulator.

**Architecture:** `discovery/` gains the pure-Kotlin `RecommendationEngine` (M0: category match, then distance) and `DiscoverUseCase` (location → provider search with timeout → rank → `DiscoverResult`). `ui/screens/RecommendationsViewModel` runs the use case in `viewModelScope` and `combine`s its result with `DrivingRestrictions.state` into `StateFlow<RecommendationsUiState>` (`WhileSubscribed`), so the restrictions connection lives only while the screen collects. The stateless `RecommendationsScreen` draws each state with two new components (`RecommendationRow`, `MessageState`). `AppContainer` builds the use case; `DiscoverNavHost` creates the ViewModel per back-stack entry. Debug launches choose a `FakeScenario` with an intent extra.

**Tech Stack:** Kotlin coroutines (`withTimeoutOrNull`, `combine`, `stateIn`), `lifecycle-viewmodel-compose` `viewModel { }`, `lifecycle-runtime-compose` `collectAsStateWithLifecycle()` (both already declared), Compose Material 3, Robolectric 4.17, kotlinx-coroutines-test.

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-004-recommendations-flow.md`; `docs/02-discover-nearby-ux-interaction-spec.md` §6, §9–§13; `docs/03-discover-nearby-engineering-implementation-plan.md` §6 (Screens, Back stack, Driving content limits, State holding), §10, §13, §15, §16; `docs/04-discover-nearby-test-demo-plan.md` §2, §7 (I, J, O, P, Q, R), §11; design PNGs `docs/design/02, 07, 08, 09, 10, 12` and the canvas artboards they were exported from.

## Global Constraints

- The ViewModel takes `container.drivingRestrictions` and collects its `state` only through the UI's collection of `uiState` (`combine` + `stateIn(WhileSubscribed)`): never `.value`, never from `init` (ticket AC; DN-M0-010 final review).
- Visible rows = first `min(CategoryConfigs.getValue(category).desiredResults, listLimit ?: ∞)`, re-trimmed when the driving state changes; the engine returns the full ranked list; never padded (ticket AC; docs/03 §6, §10).
- `PROVIDER_TIMEOUT_MILLIS = 8_000` lives in this ticket's code, is a constructor parameter of the use case, and stays below `SLOW_DELAY_MILLIS` (10 s) (ticket AC).
- Loading, Content, Empty, Error (and PermissionRequired) are states of the one Recommendations destination, never pushed destinations; Back returns to Discover (ticket AC; docs/03 §6).
- Each request gets a new `requestId`; the previous job is cancelled; a late response is dropped (ticket AC; docs/03 §15).
- Nothing is named "parked" in code or resource names; the app reports UX restrictions (DN-M0-010 rule).
- Only `ui/` imports `androidx.*.compose`; only `car/CarDrivingRestrictions.kt` imports `android.car` (`ArchitectureRulesTest`).
- Copy is verbatim from the design PNGs and docs/02; every action is at least `MinTouchTarget` (76 dp, docs/02 §15).
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes; never commit on local `main`; no AI attribution in commits or PRs.

## Open decisions (recorded here, in the ticket and the PR)

1. **Timeout has its own copy.** `10-timeout.png` ("Taking longer than expected" / "Please try again.", clock icon) is followed over docs/02 §12's "the same message covers network failure, provider failure and timeout": the design README calls the mockups the visual spec for copy and states, and the ticket AC lists `10-timeout.png`. Network and provider failures share "Unable to load places". docs/02 §12 is not edited here; the discrepancy goes in the ticket notes for a doc fix.
2. **No photo placeholder and no attribution footer.** `PlaceSummary` has no photo, and ADR-001 decides both (design README: "Photos and attribution are placeholders until ADR-001"). Rows show name; rating and up to three attributes; distance; chevron.
3. **The row's middle line is rating + attributes, no place-kind label.** The canvas "Café" reads as the `CAFE` attribute; kind strings are provider vocabulary until ADR-001.
4. **Rows are not clickable yet.** DN-M0-005 adds selection; the chevron is drawn now because the design has it.
5. **Late responses are dropped by cancelling the previous job.** On the main thread a cancelled coroutine never resumes past its suspension point (prompt cancellation), so an extra `requestId` comparison would be unreachable code. `requestId` still identifies each request (`DiscoveryContext`, `Content`). Back clears the destination's ViewModel, which cancels its request (Scenario P).
6. **`PermissionRequired` and `Error(LocationUnavailable)` render now**, because the state is exhaustive: docs/02 §10–§11 copy, Back (and Try Again for location). DN-M0-006 adds Grant, the launcher and layouts 05a/05b/06. The fakes never produce them at run time.
7. **Runtime scenario:** debug builds pass `--es scenario <FakeScenario>`; `MainActivity` forwards it to `AppContainer.useFakeScenario`, which applies it only when the app is `FLAG_DEBUGGABLE` (moved there from `MainActivity` in the simplify pass, so `ui/` never touches the fake). Use `am start -S` per scenario. An unknown name crashes the debug launch (`valueOf`), which is the clearest signal for a debug tool.
8. **Type scale:** canvas secondary text is 22 px (rows) and 24 px (message body); both use `titleMedium` 22 sp, so the message body is 2 sp under the canvas. New roles: `headlineLarge` 36 (message title), `headlineSmall` 28 (place name), `labelLarge` 24 (buttons).
9. **Restrictions connect only while Recommendations is collected** (the `MainActivity` collector goes). On the emulator Park and moving Drive both show 5 rows, because the moving limit 21 exceeds 5; trimming is proven by unit tests. On reconnect the first state is DN-M0-010's unknown fallback (`listLimit = null`), replaced as soon as the Car service answers.
10. **Empty offers only "Back to Discover".** The AC groups empty results with the failures that have Retry and Back, but docs/02 §13, `08-empty.png` and docs/04 I give Empty a single Back to Discover: retrying the same search returns the same nothing.
11. **Known-closed places are excluded** (ruling during execution, superseding the original "still rank"): the project CLAUDE.md architecture rule says known-closed places are excluded. The engine drops `isOpenNow == false` in every category; today that removes only Corner Café (Coffee) and Burger Barn (Food). DN-M2-001 narrows it to time-sensitive categories through `CategoryConfig`.
12. **Back switches screens at once** (ruling during execution): on a pop NavHost draws the outgoing screen on top for its 700 ms fade, and the new list rows swallowed a tile tap made right after Back (`DiscoverNavigationTest.tileTappedRightAfterBackOpensIt`). `popEnterTransition`/`popExitTransition` are `None`; the forward fade is kept.
13. **The engine keeps one copy of a repeated place id** (final review): the list keys rows by id, so a provider listing a place twice would crash it (docs/03 §16).

## Review Focus

1. **A second request while one is in flight** (Try Again, rapid re-entry): only the newest request's result may show. Pinned by `RecommendationsViewModelTest.retryCancelsTheRequestInFlightAndItsLateResponseIsDropped`.
2. **The list limit changes while loading:** the result arrives trimmed by the latest limit, not the one at request time. Pinned by `limitChangedWhileLoadingAppliesToTheResult`.
3. **Leaving Recommendations must release the restrictions connection.** Pinned by `drivingStateIsCollectedOnlyWhileTheUiCollects` and the emulator "disconnected" check (Task 7).
4. **A rating without a count, or more than three attributes:** "4.1 ★" alone; at most three attributes. Pinned by `RecommendationsScreenTest.rowsShowOnlyWhatIsKnown`.
5. **The timeout boundary:** still Loading just before `PROVIDER_TIMEOUT_MILLIS`, `Error(Timeout)` at it. Pinned by `timeoutEndsLoadingWithAnError`.

---

## Investigation findings (2026-10-04)

- Canvas artboards (1408 × 792, 1:1 as dp): panel header 88 high, back button, title 32 semibold; rows 14 apart, radius 18, padding 14/24, fill `#1F252B`, name 28 semibold, two 22 px `#AEB6BD` lines 6 apart, chevron 28 (`m9 6 6 6-6 6`); focused row outline `#4C8DF6` (rotary is DN-M0-011). Message states: icon 88 (stroke 1.6), gap 18, title 36 semibold, body 24 `#AEB6BD`, buttons min 260 × 72, radius 16, 16 apart, 16 above; primary `#2563EB` with white label, secondary `#2A3138`; label 24 semibold. Loading: 88 spinner, track `#2A3138`, arc `#6FA8F5`, "Finding good places nearby…" 32 semibold. Icons: empty and timeout `#6FA8F5`, network error `#AEB6BD`.
- Icon paths (canvas Icons artboard): `ic_empty` circle(11,11,7) + `m20 20-4-4`; `ic_error_network` `M7 18a5 5 0 1 1 1-9.9A6 6 0 0 1 19 10a4 4 0 0 1-1 7.9` + `M12 11v4M12 18h.01`; `ic_timeout` circle(12,12,9) + `M12 7v5l3 2`; `ic_location_off` `M12 22s7-6.2 7-12a7 7 0 0 0-11.5-5.4` + `M5.3 7.5A7 7 0 0 0 5 10c0 5.8 7 12 7 12` + `M3 3l18 18`.
- DN-M2-001 owns quality, amenity and diversity scoring (and narrowing the closed exclusion to time-sensitive categories); this ticket's engine is category match, then distance, with known-closed places excluded (decision 11).
- detekt defaults that shape the code: `ReturnCount` max 2, `SwallowedException`, `InstanceOfCheckForException` (inside catch), `LongParameterList` 6 including defaults, `MagicNumber` (properties exempt). Hence `DiscoverUseCase` returns from `when`/`try` expressions, maps exceptions in an extension outside the catch, and `MessageState` takes a `Message`.
- The debugUnitTest packaged manifest carries `android:debuggable`, so Robolectric sees `FLAG_DEBUGGABLE`.
- Fake data at Greystones: Coffee 5 places, Food 4, Outdoors 4, Family 4, Scenic 3, Explore 3; `SPARSE` keeps 2, `NULL_HEAVY` drops rating, attributes and open state.
- Scenario R (details failure) needs Place Details (DN-M0-005); here it only has to be selectable.

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `app/src/main/java/com/kanyandula/discovernearby/discovery/RecommendationEngine.kt` | Create | Interface + `BasicRecommendationEngine` |
| `…/discovery/DiscoverUseCase.kt` | Create | `PROVIDER_TIMEOUT_MILLIS`, `DiscoverError`, `DiscoverResult`, `DiscoverUseCase` |
| `…/ui/screens/RecommendationsViewModel.kt` | Create | `RecommendationsUiState`, `RecommendationsViewModel` |
| `…/ui/components/RecommendationRow.kt` | Create | One row; rating/attribute/distance text |
| `…/ui/components/MessageState.kt` | Create | `Message`, `MessageState` (icon, title, body, actions) |
| `…/ui/screens/RecommendationsScreen.kt` | Rewrite | Header + one body per state |
| `…/ui/theme/Color.kt`, `Dimens.kt`, `Type.kt` | Modify | Canvas tokens for rows, messages, buttons |
| `app/src/main/res/drawable/ic_{empty,error_network,timeout,location_off,chevron}.xml` | Create | Canvas icons |
| `app/src/main/res/values/strings.xml` | Modify | State copy, rating, distance, attribute labels |
| `…/AppContainer.kt` | Modify | `fakePlaces`, `discoverUseCase` |
| `…/ui/DiscoverNavHost.kt`, `ui/DiscoverNearbyApp.kt` | Modify | Take `AppContainer`; create the ViewModel |
| `…/ui/MainActivity.kt` | Modify | Drop the interim collector; debug scenario extra |
| `…/places/fake/FakePlacesRepository.kt` | Modify | `scenario` becomes `var` |
| `app/src/test/java/com/kanyandula/discovernearby/discovery/TestPlaces.kt` | Create | `ORIGIN`, `testPlace`, `testContext` |
| `…/test/…/places/ScriptedPlaces.kt` | Create | Scriptable `PlacesRepository` that counts searches |
| `…/test/…/car/FakeDrivingRestrictions.kt` | Create | `MutableStateFlow<DrivingState>` stand-in |
| `…/test/…/MainDispatcherRule.kt` | Create | `Dispatchers.Main` on a test dispatcher |
| `…/test/…/discovery/BasicRecommendationEngineTest.kt` | Create | Ranking |
| `…/test/…/discovery/DiscoverUseCaseTest.kt` | Create | Location, radius, errors, timeout |
| `…/test/…/ui/screens/RecommendationsViewModelTest.kt` | Create | Trim, re-trim, errors, timeout, stale, lifecycle |
| `…/test/…/ui/screens/RecommendationsScreenTest.kt` | Create | Every state renders; actions |
| `…/test/…/ui/ScenarioExtraTest.kt` | Create | Debug-only scenario extra |
| `…/test/…/ui/TestScreens.kt` | Modify | `appContainer()` |
| `…/test/…/ui/DiscoverNavigationTest.kt`, `ui/DiscoverNearbyThemeTest.kt` | Modify | Pass the container; new type/colour assertions; content via navigation |
| `…/test/…/places/fake/FakePlacesRepositoryTest.kt` | Modify | Scenario changes at run time |

`…` = `app/src/main/java/com/kanyandula/discovernearby`; `…/test/…` = `app/src/test/java/com/kanyandula/discovernearby`.

---

### Task 0: Start the ticket

- [ ] **Step 1:** In the ticket set `status: in_progress`, `branch: dn-m0-004-recommendations-flow`.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-004-recommendations-flow
git add docs/superpowers/plans/2026-10-04-dn-m0-004-recommendations-flow.md
git commit -m "Add DN-M0-004 implementation plan"
```

---

### Task 1: `RecommendationEngine`

**Files:**
- Create: `app/src/test/java/com/kanyandula/discovernearby/discovery/TestPlaces.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/discovery/BasicRecommendationEngineTest.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/discovery/RecommendationEngine.kt`

**Interfaces:**
- Consumes: `CategoryConfigs` / `CategoryConfig.targetKinds`, `DiscoveryContext`, `PlaceSummary`, `Recommendation`, `GeoPoint.distanceMetersTo` (DN-M0-003).
- Produces: `interface RecommendationEngine { fun rank(places: List<PlaceSummary>, context: DiscoveryContext): List<Recommendation> }`; `class BasicRecommendationEngine : RecommendationEngine`; test helpers `ORIGIN: GeoPoint`, `testPlace(id: String, vararg kinds: String, metersNorth: Int = 100, primaryKind: String? = kinds.firstOrNull()): PlaceSummary`, `testContext(category: DiscoveryCategory, requestId: Long = 1): DiscoveryContext`.

- [ ] **Step 1: Test helpers**

`app/src/test/java/com/kanyandula/discovernearby/discovery/TestPlaces.kt`:

```kotlin
package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceSummary

val ORIGIN = GeoPoint(lat = 53.0, lng = -6.0)

// One degree of latitude on GeoPoint's sphere, so metersNorth comes back exactly from distanceMetersTo.
private const val METERS_PER_DEGREE_LAT = 111_195.08

/** A place [metersNorth] of [ORIGIN]; every optional field is unknown unless a test sets it. */
fun testPlace(
    id: String,
    vararg kinds: String,
    metersNorth: Int = 100,
    primaryKind: String? = kinds.firstOrNull(),
) = PlaceSummary(
    id = id,
    name = id,
    location = GeoPoint(ORIGIN.lat + metersNorth / METERS_PER_DEGREE_LAT, ORIGIN.lng),
    placeKinds = kinds.toSet(),
    primaryKind = primaryKind,
    attributes = emptySet(),
    rating = null,
    ratingCount = null,
    isOpenNow = null,
    travelTimeMinutes = null,
)

fun testContext(category: DiscoveryCategory, requestId: Long = 1) =
    DiscoveryContext(requestId = requestId, origin = ORIGIN, category = category, createdAtMillis = 0)
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/discovery/BasicRecommendationEngineTest.kt`:

```kotlin
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

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*BasicRecommendationEngineTest' --console=plain -q 2>&1 | tail -5`
Expected: compilation fails, `Unresolved reference 'BasicRecommendationEngine'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/kanyandula/discovernearby/discovery/RecommendationEngine.kt`:

```kotlin
package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation

/** Turns provider results into ranked recommendations (docs/03 §10). Pure Kotlin, no display limit. */
interface RecommendationEngine {
    fun rank(places: List<PlaceSummary>, context: DiscoveryContext): List<Recommendation>
}

/**
 * M0 ranking: keep places with a kind the category targets; primary-kind matches first, then nearest, then
 * id. ponytail: DN-M2-001 replaces this with docs/03 §10 scoring (quality, amenities, known-closed
 * exclusion, diversity).
 */
class BasicRecommendationEngine : RecommendationEngine {

    override fun rank(places: List<PlaceSummary>, context: DiscoveryContext): List<Recommendation> {
        val kinds = CategoryConfigs.getValue(context.category).targetKinds
        return places
            .filter { place -> place.placeKinds.any { it in kinds } }
            .map { place ->
                Recommendation(
                    place = place,
                    score = if (place.primaryKind in kinds) PRIMARY_MATCH else SECONDARY_MATCH,
                    distanceMeters = context.origin.distanceMetersTo(place.location),
                    travelTimeMinutes = place.travelTimeMinutes,
                    minutesAhead = null,
                    detourMinutes = null,
                )
            }
            .sortedWith(
                compareByDescending<Recommendation> { it.score }.thenBy { it.distanceMeters }.thenBy { it.place.id },
            )
    }

    private companion object {
        const val PRIMARY_MATCH = 1.0
        const val SECONDARY_MATCH = 0.5
    }
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*BasicRecommendationEngineTest' --console=plain -q 2>&1 | tail -5`
Expected: no output (6 tests pass).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/discovery/RecommendationEngine.kt \
  app/src/test/java/com/kanyandula/discovernearby/discovery/TestPlaces.kt \
  app/src/test/java/com/kanyandula/discovernearby/discovery/BasicRecommendationEngineTest.kt
git commit -m "Add RecommendationEngine with category-then-distance ranking"
```

---

### Task 2: `DiscoverUseCase`

**Files:**
- Create: `app/src/test/java/com/kanyandula/discovernearby/places/ScriptedPlaces.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/discovery/DiscoverUseCaseTest.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/discovery/DiscoverUseCase.kt`

**Interfaces:**
- Consumes: `RecommendationEngine`, `BasicRecommendationEngine` (Task 1); `PlacesRepository`, `PlacesException` / `NetworkUnavailable` / `ProviderFailure`, `LocationProvider`, `LocationResult`, `FakePlacesRepository`, `FakeScenario`, `SLOW_DELAY_MILLIS`, `SLOW_CATEGORY`, `FakeLocationProvider`, `TestLocation` (DN-M0-003).
- Produces: `const val PROVIDER_TIMEOUT_MILLIS = 8_000L`; `enum class DiscoverError { LocationUnavailable, NetworkUnavailable, ProviderFailure, Timeout }`; `sealed interface DiscoverResult { Success(context: DiscoveryContext, recommendations: List<Recommendation>); data object PermissionRequired; Failure(error: DiscoverError) }`; `class DiscoverUseCase(places, location, engine, timeoutMillis = PROVIDER_TIMEOUT_MILLIS) { suspend operator fun invoke(requestId: Long, category: DiscoveryCategory): DiscoverResult }`; test helper `ScriptedPlaces(var reply: suspend () -> List<PlaceSummary> = { emptyList() })` with read-only `searches`, `running`, `lastRadius`.

- [ ] **Step 1: Test helper**

`app/src/test/java/com/kanyandula/discovernearby/places/ScriptedPlaces.kt`:

```kotlin
package com.kanyandula.discovernearby.places

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary

/** Answers every search with [reply]; records what it was asked and how many searches are still running. */
class ScriptedPlaces(var reply: suspend () -> List<PlaceSummary> = { emptyList() }) : PlacesRepository {
    var searches = 0
        private set
    var running = 0
        private set
    var lastRadius: Int? = null
        private set

    override suspend fun searchNearby(
        origin: GeoPoint,
        category: DiscoveryCategory,
        radiusMeters: Int,
    ): List<PlaceSummary> {
        searches++
        lastRadius = radiusMeters
        running++
        try {
            return reply()
        } finally {
            running--
        }
    }

    override suspend fun getPlaceDetails(placeId: String): PlaceDetails = error("not used")
}
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/discovery/DiscoverUseCaseTest.kt`:

```kotlin
package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.ScriptedPlaces
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import com.kanyandula.discovernearby.places.fake.FakeScenario
import com.kanyandula.discovernearby.places.fake.SLOW_CATEGORY
import com.kanyandula.discovernearby.places.fake.SLOW_DELAY_MILLIS
import com.kanyandula.discovernearby.places.fake.TestLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class) // currentTime
class DiscoverUseCaseTest {

    private fun useCase(
        places: PlacesRepository = FakePlacesRepository(),
        location: LocationResult = LocationResult.Available(TestLocation.GREYSTONES.point),
    ) = DiscoverUseCase(places, FakeLocationProvider(location), BasicRecommendationEngine())

    private fun failing(scenario: FakeScenario) = useCase(FakePlacesRepository(scenario))

    // The slow fake must outlast the timeout, or Scenario O never times out.
    @Test
    fun timeoutStaysBelowTheSlowFake() {
        assertTrue(PROVIDER_TIMEOUT_MILLIS < SLOW_DELAY_MILLIS)
    }

    @Test
    fun ranksTheCategoryAroundTheCurrentLocation() = runTest {
        val result = useCase().invoke(7, FAMILY) as DiscoverResult.Success
        val expected = DiscoveryContext(7, TestLocation.GREYSTONES.point, FAMILY, result.context.createdAtMillis)
        assertEquals(expected, result.context)
        val distances = result.recommendations.map { it.distanceMeters }
        assertTrue(distances.isNotEmpty())
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun searchesTheCategoryRadius() = runTest {
        val places = ScriptedPlaces()
        useCase(places).invoke(1, OUTDOORS)
        assertEquals(CategoryConfigs.getValue(OUTDOORS).radiusMeters, places.lastRadius)
    }

    @Test
    fun noMatchesIsAnEmptySuccess() = runTest {
        val result = failing(FakeScenario.EMPTY).invoke(1, COFFEE) as DiscoverResult.Success
        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun providerFailuresKeepTheirCause() = runTest {
        val network = failing(FakeScenario.NETWORK_FAILURE).invoke(1, COFFEE)
        assertEquals(DiscoverResult.Failure(DiscoverError.NetworkUnavailable), network)
        val provider = failing(FakeScenario.PROVIDER_FAILURE).invoke(1, COFFEE)
        assertEquals(DiscoverResult.Failure(DiscoverError.ProviderFailure), provider)
    }

    @Test
    fun slowProviderTimesOut() = runTest {
        val result = failing(FakeScenario.SLOW).invoke(1, SLOW_CATEGORY)
        assertEquals(DiscoverResult.Failure(DiscoverError.Timeout), result)
        assertEquals(PROVIDER_TIMEOUT_MILLIS, currentTime)
    }

    // Location is read first and the provider is never asked without one (docs/03 §13).
    @Test
    fun locationProblemsNeverReachTheProvider() = runTest {
        val places = ScriptedPlaces()
        val permission = useCase(places, LocationResult.PermissionMissing).invoke(1, COFFEE)
        assertEquals(DiscoverResult.PermissionRequired, permission)
        val unavailable = useCase(places, LocationResult.Unavailable).invoke(1, COFFEE)
        assertEquals(DiscoverResult.Failure(DiscoverError.LocationUnavailable), unavailable)
        assertEquals(0, places.searches)
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiscoverUseCaseTest' --console=plain -q 2>&1 | tail -5`
Expected: compilation fails, `Unresolved reference 'DiscoverUseCase'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/kanyandula/discovernearby/discovery/DiscoverUseCase.kt`:

```kotlin
package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.PlacesException
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.ProviderFailure
import kotlinx.coroutines.withTimeoutOrNull

/** Provider calls give up after this long (docs/03 §15); below the fake's SLOW_DELAY_MILLIS (docs/04 O). */
const val PROVIDER_TIMEOUT_MILLIS = 8_000L

/** Why a request produced no list (docs/03 §15). Network, provider and timeout stay distinct for logging. */
enum class DiscoverError { LocationUnavailable, NetworkUnavailable, ProviderFailure, Timeout }

/** The outcome of one discovery request; the ViewModel turns it into UI state. */
sealed interface DiscoverResult {
    data class Success(val context: DiscoveryContext, val recommendations: List<Recommendation>) : DiscoverResult
    data object PermissionRequired : DiscoverResult
    data class Failure(val error: DiscoverError) : DiscoverResult
}

/**
 * One discovery: read the location (only now, docs/03 §13), search the category's radius, rank. The
 * ranked list is complete; the ViewModel decides how much of it to show.
 */
class DiscoverUseCase(
    private val places: PlacesRepository,
    private val location: LocationProvider,
    private val engine: RecommendationEngine,
    private val timeoutMillis: Long = PROVIDER_TIMEOUT_MILLIS,
) {
    suspend operator fun invoke(requestId: Long, category: DiscoveryCategory): DiscoverResult =
        when (val fix = location.currentLocation()) {
            is LocationResult.Available ->
                search(DiscoveryContext(requestId, fix.point, category, System.currentTimeMillis()))
            LocationResult.PermissionMissing -> DiscoverResult.PermissionRequired
            LocationResult.Unavailable -> DiscoverResult.Failure(DiscoverError.LocationUnavailable)
        }

    private suspend fun search(context: DiscoveryContext): DiscoverResult = try {
        val radius = CategoryConfigs.getValue(context.category).radiusMeters
        val found = withTimeoutOrNull(timeoutMillis) {
            places.searchNearby(context.origin, context.category, radius)
        }
        if (found == null) {
            DiscoverResult.Failure(DiscoverError.Timeout)
        } else {
            DiscoverResult.Success(context, engine.rank(found, context))
        }
    } catch (e: PlacesException) {
        DiscoverResult.Failure(e.toDiscoverError())
    }
}

private fun PlacesException.toDiscoverError() = when (this) {
    is NetworkUnavailable -> DiscoverError.NetworkUnavailable
    is ProviderFailure -> DiscoverError.ProviderFailure
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiscoverUseCaseTest' --console=plain -q 2>&1 | tail -5`
Expected: no output (7 tests pass).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/discovery/DiscoverUseCase.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/ScriptedPlaces.kt \
  app/src/test/java/com/kanyandula/discovernearby/discovery/DiscoverUseCaseTest.kt
git commit -m "Add DiscoverUseCase with provider timeout and error mapping"
```

---

### Task 3: `RecommendationsViewModel`

**Files:**
- Create: `app/src/test/java/com/kanyandula/discovernearby/MainDispatcherRule.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/car/FakeDrivingRestrictions.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/RecommendationsViewModelTest.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsViewModel.kt`

**Interfaces:**
- Consumes: `DiscoverUseCase`, `DiscoverResult`, `DiscoverError`, `PROVIDER_TIMEOUT_MILLIS` (Task 2); `BasicRecommendationEngine`, `ORIGIN`, `testPlace` (Task 1); `ScriptedPlaces` (Task 2); `DrivingRestrictions`, `DrivingState` (DN-M0-010); `CategoryConfigs` (DN-M0-003).
- Produces: `sealed interface RecommendationsUiState { data object Loading; data class Content(requestId: Long, recommendations: List<Recommendation>); data object Empty; data class PermissionRequired(canRequest: Boolean); data class Error(type: DiscoverError) }`; `internal const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L`; `class RecommendationsViewModel(category: DiscoveryCategory, discover: DiscoverUseCase, drivingRestrictions: DrivingRestrictions) : ViewModel` with `val uiState: StateFlow<RecommendationsUiState>` and `fun retry()`; test helpers `MainDispatcherRule`, `FakeDrivingRestrictions(override val state: MutableStateFlow<DrivingState>)`.

- [ ] **Step 1: Test helpers**

`app/src/test/java/com/kanyandula/discovernearby/MainDispatcherRule.kt`:

```kotlin
package com.kanyandula.discovernearby

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Runs Dispatchers.Main (so viewModelScope) on a test dispatcher; runTest then shares its virtual clock. */
@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(StandardTestDispatcher())

    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

`app/src/test/java/com/kanyandula/discovernearby/car/FakeDrivingRestrictions.kt`:

```kotlin
package com.kanyandula.discovernearby.car

import kotlinx.coroutines.flow.MutableStateFlow

/** Test stand-in: set `state.value` to change the driving state; `state.subscriptionCount` shows collectors. */
class FakeDrivingRestrictions(
    override val state: MutableStateFlow<DrivingState> =
        MutableStateFlow(DrivingState(distractionOptimizationRequired = false, listLimit = null)),
) : DrivingRestrictions
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/ui/screens/RecommendationsViewModelTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import com.kanyandula.discovernearby.MainDispatcherRule
import com.kanyandula.discovernearby.car.DrivingState
import com.kanyandula.discovernearby.car.FakeDrivingRestrictions
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.CategoryConfigs
import com.kanyandula.discovernearby.discovery.DiscoverError
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.ORIGIN
import com.kanyandula.discovernearby.discovery.PROVIDER_TIMEOUT_MILLIS
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.ProviderFailure
import com.kanyandula.discovernearby.places.ScriptedPlaces
import com.kanyandula.discovernearby.places.fake.SLOW_DELAY_MILLIS
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Content
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Empty
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Error
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Loading
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.PermissionRequired
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy
class RecommendationsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val places = ScriptedPlaces()
    private val restrictions = FakeDrivingRestrictions()

    private fun cafes(count: Int) = List(count) { testPlace("p$it", "cafe", metersNorth = 100 * (it + 1)) }

    private fun newViewModel(location: LocationResult = LocationResult.Available(ORIGIN)) = RecommendationsViewModel(
        category = COFFEE,
        discover = DiscoverUseCase(places, FakeLocationProvider(location), BasicRecommendationEngine()),
        drivingRestrictions = restrictions,
    )

    /** A view model whose state the UI is collecting, after its first request ran as far as it can. */
    private fun TestScope.collected(location: LocationResult = LocationResult.Available(ORIGIN)) =
        newViewModel(location).also { vm ->
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
        }

    private val RecommendationsViewModel.shown: List<String>
        get() = (uiState.value as Content).recommendations.map { it.place.id }

    private fun limitTo(listLimit: Int?) {
        restrictions.state.value = DrivingState(distractionOptimizationRequired = listLimit != null, listLimit)
    }

    @Test
    fun loadingUntilTheResultArrives() = runTest {
        places.reply = {
            delay(1_000)
            cafes(2)
        }
        val vm = collected()
        assertEquals(Loading, vm.uiState.value)
        advanceTimeBy(1_001)
        assertEquals(listOf("p0", "p1"), vm.shown)
    }

    @Test
    fun showsTheCategoryCountWithoutALimit() = runTest {
        places.reply = { cafes(7) }
        val vm = collected()
        assertEquals(CategoryConfigs.getValue(COFFEE).desiredResults, vm.shown.size)
        assertEquals(listOf("p0", "p1", "p2", "p3", "p4"), vm.shown)
    }

    @Test
    fun listLimitCapsTheCount() = runTest {
        limitTo(3)
        places.reply = { cafes(7) }
        assertEquals(listOf("p0", "p1", "p2"), collected().shown)
    }

    @Test
    fun retrimsWhenTheDrivingStateChangesWithoutANewRequest() = runTest {
        places.reply = { cafes(7) }
        val vm = collected()
        limitTo(2)
        runCurrent()
        assertEquals(2, vm.shown.size)
        limitTo(null)
        runCurrent()
        assertEquals(5, vm.shown.size)
        assertEquals(1, places.searches)
    }

    @Test
    fun limitChangedWhileLoadingAppliesToTheResult() = runTest {
        places.reply = {
            delay(1_000)
            cafes(7)
        }
        val vm = collected()
        limitTo(2)
        advanceTimeBy(1_001)
        assertEquals(2, vm.shown.size)
    }

    @Test
    fun fewerResultsAreShownWithoutPadding() = runTest {
        places.reply = { cafes(2) }
        assertEquals(2, collected().shown.size)
    }

    @Test
    fun noResultsIsEmpty() = runTest {
        assertEquals(Empty, collected().uiState.value)
    }

    @Test
    fun failuresAreErrorStates() = runTest {
        places.reply = { throw NetworkUnavailable() }
        assertEquals(Error(DiscoverError.NetworkUnavailable), collected().uiState.value)
        places.reply = { throw ProviderFailure() }
        assertEquals(Error(DiscoverError.ProviderFailure), collected().uiState.value)
        assertEquals(Error(DiscoverError.LocationUnavailable), collected(LocationResult.Unavailable).uiState.value)
    }

    @Test
    fun timeoutEndsLoadingWithAnError() = runTest {
        places.reply = {
            delay(SLOW_DELAY_MILLIS)
            cafes(1)
        }
        val vm = collected()
        advanceTimeBy(PROVIDER_TIMEOUT_MILLIS - 1)
        assertEquals(Loading, vm.uiState.value)
        advanceTimeBy(2)
        assertEquals(Error(DiscoverError.Timeout), vm.uiState.value)
    }

    @Test
    fun retryShowsLoadingThenANewRequest() = runTest {
        places.reply = { throw NetworkUnavailable() }
        val vm = collected()
        places.reply = {
            delay(1_000)
            cafes(1)
        }
        vm.retry()
        runCurrent()
        assertEquals(Loading, vm.uiState.value)
        advanceTimeBy(1_001)
        assertEquals(2L, (vm.uiState.value as Content).requestId)
    }

    @Test
    fun retryCancelsTheRequestInFlightAndItsLateResponseIsDropped() = runTest {
        places.reply = {
            delay(1_000)
            cafes(1)
        }
        val vm = collected()
        places.reply = { cafes(3) }
        vm.retry()
        runCurrent()
        assertEquals(0, places.running) // the first search was cancelled, not left to finish
        advanceTimeBy(PROVIDER_TIMEOUT_MILLIS + 1)
        assertEquals(2L, (vm.uiState.value as Content).requestId)
        assertEquals(3, vm.shown.size)
    }

    @Test
    fun permissionRequiredFollowsTheRestrictions() = runTest {
        restrictions.state.value = DrivingState(distractionOptimizationRequired = true, listLimit = null)
        val vm = collected(LocationResult.PermissionMissing)
        assertEquals(PermissionRequired(canRequest = false), vm.uiState.value)
        restrictions.state.value = DrivingState(distractionOptimizationRequired = false, listLimit = null)
        runCurrent()
        assertEquals(PermissionRequired(canRequest = true), vm.uiState.value)
    }

    // DN-M0-010: the restrictions connection exists only while collected, so the screen must be the collector.
    @Test
    fun drivingStateIsCollectedOnlyWhileTheUiCollects() = runTest {
        val vm = newViewModel()
        runCurrent()
        assertEquals(0, restrictions.state.subscriptionCount.value)
        val ui = backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(1, restrictions.state.subscriptionCount.value)
        ui.cancel()
        advanceTimeBy(UI_STATE_STOP_TIMEOUT_MILLIS + 1)
        runCurrent()
        assertEquals(0, restrictions.state.subscriptionCount.value)
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsViewModelTest' --console=plain -q 2>&1 | tail -5`
Expected: compilation fails, `Unresolved reference 'RecommendationsViewModel'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsViewModel.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanyandula.discovernearby.car.DrivingRestrictions
import com.kanyandula.discovernearby.car.DrivingState
import com.kanyandula.discovernearby.discovery.CategoryConfigs
import com.kanyandula.discovernearby.discovery.DiscoverError
import com.kanyandula.discovernearby.discovery.DiscoverResult
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.Recommendation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Recommendations destination's states (docs/03 §15). All draw on one screen; none is pushed. */
sealed interface RecommendationsUiState {
    data object Loading : RecommendationsUiState
    data class Content(val requestId: Long, val recommendations: List<Recommendation>) : RecommendationsUiState
    data object Empty : RecommendationsUiState
    data class PermissionRequired(val canRequest: Boolean) : RecommendationsUiState
    data class Error(val type: DiscoverError) : RecommendationsUiState
}

/** How long the UI state keeps its upstreams after the screen stops collecting. */
internal const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L

class RecommendationsViewModel(
    private val category: DiscoveryCategory,
    private val discover: DiscoverUseCase,
    drivingRestrictions: DrivingRestrictions,
) : ViewModel() {

    private val result = MutableStateFlow<DiscoverResult?>(null) // null while a request runs
    private var requestId = 0L
    private var request: Job? = null

    // The driving state is combined in, not read, so the restrictions connection is open only while the
    // screen collects, and a change re-trims the list without a new request (docs/03 §6).
    val uiState: StateFlow<RecommendationsUiState> = combine(result, drivingRestrictions.state, ::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
            initialValue = RecommendationsUiState.Loading,
        )

    init {
        load()
    }

    fun retry() = load()

    // Cancelling the previous request is what drops its late response: a cancelled coroutine never resumes
    // to assign its result (docs/03 §15). Back clears this ViewModel, which cancels the request the same way.
    private fun load() {
        request?.cancel()
        result.value = null
        val id = ++requestId
        request = viewModelScope.launch { result.value = discover(id, category) }
    }

    private fun toUiState(result: DiscoverResult?, driving: DrivingState): RecommendationsUiState = when (result) {
        null -> RecommendationsUiState.Loading
        is DiscoverResult.Success -> if (result.recommendations.isEmpty()) {
            RecommendationsUiState.Empty
        } else {
            RecommendationsUiState.Content(
                requestId = result.context.requestId,
                recommendations = result.recommendations.take(visibleCount(driving)),
            )
        }
        DiscoverResult.PermissionRequired ->
            RecommendationsUiState.PermissionRequired(canRequest = !driving.distractionOptimizationRequired)
        is DiscoverResult.Failure -> RecommendationsUiState.Error(result.error)
    }

    // docs/03 §6: the first min(desired, uxLimit); the engine never sees the driving state.
    private fun visibleCount(driving: DrivingState) =
        minOf(CategoryConfigs.getValue(category).desiredResults, driving.listLimit ?: Int.MAX_VALUE)
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsViewModelTest' --console=plain -q 2>&1 | tail -5`
Expected: no output (13 tests pass).

- [ ] **Step 6: Prove the two guards**

Temporarily delete `request?.cancel()` and re-run the class: `retryCancelsTheRequestInFlightAndItsLateResponseIsDropped` fails (`running` is 1). Restore it. Temporarily replace `drivingRestrictions.state` in `combine` with `flowOf(drivingRestrictions.state.value)`: `retrimsWhenTheDrivingStateChangesWithoutANewRequest` and `drivingStateIsCollectedOnlyWhileTheUiCollects` fail. Restore; re-run; all pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsViewModel.kt \
  app/src/test/java/com/kanyandula/discovernearby/MainDispatcherRule.kt \
  app/src/test/java/com/kanyandula/discovernearby/car/FakeDrivingRestrictions.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/screens/RecommendationsViewModelTest.kt
git commit -m "Add RecommendationsViewModel with live list limit and stale-request cancel"
```

---

### Task 4: Recommendations screen states

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/theme/Color.kt`, `Dimens.kt`, `Type.kt`
- Create: `app/src/main/res/drawable/ic_empty.xml`, `ic_error_network.xml`, `ic_timeout.xml`, `ic_location_off.xml`, `ic_chevron.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/components/MessageState.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/components/RecommendationRow.kt`
- Rewrite: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNearbyThemeTest.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreenTest.kt`

**Interfaces:**
- Consumes: `RecommendationsUiState` (Task 3), `DiscoverError` (Task 2), `testPlace` (Task 1), `category.visual` (DN-M0-002).
- Produces: `fun RecommendationsScreen(category: DiscoveryCategory, state: RecommendationsUiState, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)`; tokens `Raised`, `Action`, `Highlight`, `RowRadius`, `RowGap`, `RowPadding`, `RowVerticalPadding`, `RowLineGap`, `ChevronSize`, `MessageIconSize`, `MessageGap`, `SpinnerStroke`, `ButtonMinWidth`, `ButtonRadius`, `ButtonGap`.

- [ ] **Step 1: Write the failing tests**

`DiscoverNearbyThemeTest.kt`: replace the comment and body of `usesCanvasTypeScale`, and add `messageTokensMatchTheCanvas`:

```kotlin
    // Canvas text roles: message title 36, tile label and screen title 32, place name 28, header 20, buttons 24
    // (all semibold); row details and message body 22; tile subtitle 20.
    @Test
    fun usesCanvasTypeScale() {
        lateinit var type: Typography
        rule.setContent { DiscoverNearbyTheme { type = MaterialTheme.typography } }
        assertEquals(36.sp, type.headlineLarge.fontSize)
        assertEquals(FontWeight.SemiBold, type.headlineLarge.fontWeight)
        assertEquals(32.sp, type.headlineMedium.fontSize)
        assertEquals(FontWeight.SemiBold, type.headlineMedium.fontWeight)
        assertEquals(28.sp, type.headlineSmall.fontSize)
        assertEquals(FontWeight.SemiBold, type.headlineSmall.fontWeight)
        assertEquals(20.sp, type.titleLarge.fontSize)
        assertEquals(FontWeight.SemiBold, type.titleLarge.fontWeight)
        assertEquals(22.sp, type.titleMedium.fontSize)
        assertEquals(FontWeight.Normal, type.titleMedium.fontWeight)
        assertEquals(20.sp, type.bodyLarge.fontSize)
        assertEquals(24.sp, type.labelLarge.fontSize)
        assertEquals(FontWeight.SemiBold, type.labelLarge.fontWeight)
    }

    // Literal hex values from the canvas message and loading artboards.
    @Test
    fun messageTokensMatchTheCanvas() {
        assertEquals(Color(0xFF2A3138), Raised)
        assertEquals(Color(0xFF2563EB), Action)
        assertEquals(Color(0xFF6FA8F5), Highlight)
    }
```

Add imports `com.kanyandula.discovernearby.ui.theme.Action`, `…ui.theme.Highlight`, `…ui.theme.Raised`.

`app/src/test/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreenTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.discovery.DiscoverError
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.AttributeSource.DERIVED
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType.CAFE
import com.kanyandula.discovernearby.model.AttributeType.DRIVE_THROUGH
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.AttributeType.TOILETS
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Content
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Empty
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Error
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.Loading
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.PermissionRequired
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The screen is stateless: each test sets the state and reads what is drawn. One composition per test, so a
// state change redraws the same screen (docs/02 §6).
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class RecommendationsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf<RecommendationsUiState>(Loading)
    private var retries = 0
    private var backs = 0

    @Before
    fun setUp() {
        rule.setContent {
            DiscoverNearbyTheme {
                RecommendationsScreen(category = COFFEE, state = state, onRetry = { retries++ }, onBack = { backs++ })
            }
        }
    }

    private fun row(place: PlaceSummary, meters: Int) = Recommendation(
        place = place,
        score = 1.0,
        distanceMeters = meters,
        travelTimeMinutes = null,
        minutesAhead = null,
        detourMinutes = null,
    )

    @Test
    fun loadingKeepsTheCategoryTitle() {
        rule.onNodeWithText("Coffee").assertIsDisplayed()
        rule.onNodeWithText("Finding good places nearby…").assertIsDisplayed()
    }

    @Test
    fun rowsShowOnlyWhatIsKnown() {
        val full = testPlace("Harbour Roasters", "cafe").copy(
            rating = 4.6,
            ratingCount = 212,
            attributes = setOf(
                PlaceAttribute(PARKING, PROVIDED),
                PlaceAttribute(TOILETS, DERIVED),
                PlaceAttribute(CAFE, PROVIDED),
                PlaceAttribute(DRIVE_THROUGH, PROVIDED),
            ),
        )
        val noCount = testPlace("Corner Café", "cafe").copy(rating = 4.1)
        val bare = testPlace("Brew & Bloom", "cafe")
        state = Content(requestId = 1, recommendations = listOf(row(full, 2_100), row(noCount, 900), row(bare, 400)))

        rule.onNodeWithText("Harbour Roasters").assertIsDisplayed()
        rule.onNodeWithText("4.6 ★ (212) · Parking · Toilets · Café").assertIsDisplayed() // at most three
        rule.onNodeWithText("2.1 km").assertIsDisplayed()
        rule.onNodeWithText("4.1 ★").assertIsDisplayed()
        rule.onNodeWithText("0.9 km").assertIsDisplayed()
        rule.onNodeWithText("Brew & Bloom").assertIsDisplayed()
        rule.onNodeWithText("0.4 km").assertIsDisplayed()
        rule.onAllNodesWithText("★", substring = true).assertCountEquals(2) // nothing blank on the bare row
    }

    @Test
    fun emptyOffersOnlyBackToDiscover() {
        state = Empty
        rule.onNodeWithText("No good matches nearby").assertIsDisplayed()
        rule.onNodeWithText("Try another category.").assertIsDisplayed()
        rule.onNodeWithText("Try Again").assertDoesNotExist()
        rule.onNodeWithText("Back to Discover").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun networkAndProviderFailuresShareOneMessageWithRetryAndBack() {
        listOf(DiscoverError.NetworkUnavailable, DiscoverError.ProviderFailure).forEach { error ->
            state = Error(error)
            rule.onNodeWithText("Unable to load places").assertIsDisplayed()
            rule.onNodeWithText("Check your connection and try again.").assertIsDisplayed()
        }
        rule.onNodeWithText("Try Again").performClick()
        rule.onNodeWithText("Back").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun timeoutHasItsOwnMessage() {
        state = Error(DiscoverError.Timeout)
        rule.onNodeWithText("Taking longer than expected").assertIsDisplayed()
        rule.onNodeWithText("Please try again.").assertIsDisplayed()
        rule.onNodeWithText("Try Again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun locationUnavailableOffersRetry() {
        state = Error(DiscoverError.LocationUnavailable)
        rule.onNodeWithText("Location unavailable").assertIsDisplayed()
        rule.onNodeWithText("Try Again").assertIsDisplayed()
    }

    @Test
    fun permissionCopyFollowsTheRestrictions() {
        state = PermissionRequired(canRequest = false)
        rule.onNodeWithText("Park the vehicle to allow Discover Nearby to access your location.").assertIsDisplayed()
        state = PermissionRequired(canRequest = true)
        rule.onNodeWithText("Discover Nearby needs your location to find places around you.").assertIsDisplayed()
        rule.onNodeWithText("Try Again").assertDoesNotExist()
    }

    @Test
    fun actionsMeetTheTouchTarget() {
        state = Error(DiscoverError.NetworkUnavailable)
        rule.onNodeWithText("Try Again").assertHeightIsAtLeast(MinTouchTarget)
        rule.onNodeWithText("Back").assertHeightIsAtLeast(MinTouchTarget)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsScreenTest' --tests '*DiscoverNearbyThemeTest' --console=plain -q 2>&1 | tail -5`
Expected: compilation fails (`Unresolved reference 'Raised'`, no `state` parameter on `RecommendationsScreen`).

- [ ] **Step 3: Tokens**

`Color.kt`, append:

```kotlin

// Recommendations and message artboards.
val Raised = Color(0xFF2A3138) // secondary button, spinner track
val Action = Color(0xFF2563EB) // primary button, white label
val Highlight = Color(0xFF6FA8F5) // loading arc, empty and timeout icons
```

`Dimens.kt`: change the comment to `// Canvas artboard sizes (1408 × 792 frame), taken 1:1 as dp. Text sizes live in Type.kt.` and append:

```kotlin

// Recommendations and message artboards.
val RowRadius = 18.dp
val RowGap = 14.dp
val RowPadding = 24.dp
val RowVerticalPadding = 14.dp
val RowLineGap = 6.dp
val ChevronSize = 28.dp
val MessageIconSize = 88.dp
val MessageGap = 18.dp
val SpinnerStroke = 7.dp // the canvas arc: stroke 2 on a 24 viewBox, drawn at 88
val ButtonMinWidth = 260.dp
val ButtonRadius = 16.dp
val ButtonGap = 16.dp
```

`Type.kt`: replace the comment and `copy(...)` with:

```kotlin
// Canvas text roles: headlineLarge = message title, headlineMedium = tile label and screen title,
// headlineSmall = place name, titleLarge = app header, titleMedium = row details and message body,
// bodyLarge = tile subtitle, labelLarge = buttons. The rest of the Material 3 scale is unchanged.
internal val CanvasTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
        bodyLarge = bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
        labelLarge = labelLarge.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    )
}
```

- [ ] **Step 4: Icons**

Same format as `ic_back.xml` (white, stroke 1.8, round caps and joins, tinted in code). Circles become two arcs; the chevron mirrors in RTL like `ic_back`.

`app/src/main/res/drawable/ic_empty.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0 M20 20l-4 -4"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

`app/src/main/res/drawable/ic_error_network.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M7 18a5 5 0 1 1 1-9.9A6 6 0 0 1 19 10a4 4 0 0 1-1 7.9 M12 11v4 M12 18h.01"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

`app/src/main/res/drawable/ic_timeout.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0 M12 7v5l3 2"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

`app/src/main/res/drawable/ic_location_off.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M12 22s7-6.2 7-12a7 7 0 0 0-11.5-5.4 M5.3 7.5A7 7 0 0 0 5 10c0 5.8 7 12 7 12 M3 3l18 18"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

`app/src/main/res/drawable/ic_chevron.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24" android:autoMirrored="true">
    <path android:pathData="M9 6l6 6-6 6"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

- [ ] **Step 5: Strings**

`strings.xml`, before `</resources>`:

```xml
    <string name="loading">Finding good places nearby…</string>
    <string name="empty_title">No good matches nearby</string>
    <string name="empty_body">Try another category.</string>
    <string name="back_to_discover">Back to Discover</string>
    <string name="try_again">Try Again</string>
    <string name="load_failed_title">Unable to load places</string>
    <string name="load_failed_body">Check your connection and try again.</string>
    <string name="timeout_title">Taking longer than expected</string>
    <string name="timeout_body">Please try again.</string>
    <string name="no_location_title">Location unavailable</string>
    <string name="no_location_body">Discover Nearby needs a location to find places around you.</string>
    <string name="permission_title">Location permission required</string>
    <string name="permission_body">Discover Nearby needs your location to find places around you.</string>
    <string name="permission_body_restricted">Park the vehicle to allow Discover Nearby to access your location.</string>
    <string name="rating">%1$.1f ★</string>
    <string name="rating_with_count">%1$.1f ★ (%2$d)</string>
    <string name="distance_km">%1$.1f km</string>
    <string name="attribute_parking">Parking</string>
    <string name="attribute_toilets">Toilets</string>
    <string name="attribute_cafe">Café</string>
    <string name="attribute_playground">Playground</string>
    <string name="attribute_trails">Trails</string>
    <string name="attribute_beach">Beach</string>
    <string name="attribute_viewpoint">Viewpoint</string>
    <string name="attribute_museum">Museum</string>
    <string name="attribute_family_friendly">Family-friendly</string>
    <string name="attribute_drive_through">Drive-through</string>
```

- [ ] **Step 6: `MessageState`**

`app/src/main/java/com/kanyandula/discovernearby/ui/components/MessageState.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.theme.Action
import com.kanyandula.discovernearby.ui.theme.ButtonGap
import com.kanyandula.discovernearby.ui.theme.ButtonMinWidth
import com.kanyandula.discovernearby.ui.theme.ButtonRadius
import com.kanyandula.discovernearby.ui.theme.MessageGap
import com.kanyandula.discovernearby.ui.theme.MessageIconSize
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.Raised

/** What a message state says (canvas message artboards). */
internal class Message(
    @param:DrawableRes val icon: Int,
    val tint: Color,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
)

/** A centred message with a Back action and, when [onRetry] is given, Try Again first (docs/02 §9–§13). */
@Composable
internal fun MessageState(
    message: Message,
    @StringRes backLabel: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MessageGap, Alignment.CenterVertically),
    ) {
        // Decorative: the title says what happened.
        Icon(
            painter = painterResource(message.icon),
            contentDescription = null,
            tint = message.tint,
            modifier = Modifier.size(MessageIconSize),
        )
        Text(
            text = stringResource(message.title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(message.body),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(modifier = Modifier.padding(top = ButtonGap), horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
            if (onRetry != null) MessageButton(R.string.try_again, onRetry, container = Action, content = Color.White)
            MessageButton(backLabel, onBack, container = Raised, content = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MessageButton(@StringRes label: Int, onClick: () -> Unit, container: Color, content: Color) {
    Button(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = ButtonMinWidth, minHeight = MinTouchTarget),
        shape = RoundedCornerShape(ButtonRadius),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}
```

- [ ] **Step 7: `RecommendationRow`**

`app/src/main/java/com/kanyandula/discovernearby/ui/components/RecommendationRow.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.model.AttributeType
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.theme.ChevronSize
import com.kanyandula.discovernearby.ui.theme.RowLineGap
import com.kanyandula.discovernearby.ui.theme.RowPadding
import com.kanyandula.discovernearby.ui.theme.RowRadius
import com.kanyandula.discovernearby.ui.theme.RowVerticalPadding

private const val MAX_ROW_ATTRIBUTES = 3 // docs/02 §6: 1–3 provided or derived attributes
private const val METERS_PER_KM = 1_000.0
private const val SEPARATOR = " · "

/**
 * One recommendation (canvas Recommendations artboard): name; rating and attributes when known; distance.
 * Missing fields are left out, never shown blank. ponytail: no photo or attribution until ADR-001 says what
 * the provider allows; DN-M0-005 makes the row open Place Details.
 */
@Composable
fun RecommendationRow(recommendation: Recommendation, modifier: Modifier = Modifier) {
    val place = recommendation.place
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RowRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RowPadding, vertical = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RowPadding),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowLineGap)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                detailsLine(place)?.let { SupportingLine(it) }
                SupportingLine(stringResource(R.string.distance_km, recommendation.distanceMeters / METERS_PER_KM))
            }
            // Decorative, as on the canvas.
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                modifier = Modifier.size(ChevronSize),
            )
        }
    }
}

/** Rating, only when the provider gives one, and up to three attributes; null when there is neither. */
@Composable
private fun detailsLine(place: PlaceSummary): String? {
    val rating = place.rating?.let { rating ->
        place.ratingCount?.let { stringResource(R.string.rating_with_count, rating, it) }
            ?: stringResource(R.string.rating, rating)
    }
    val attributes = place.attributes.map { it.type }.distinct().take(MAX_ROW_ATTRIBUTES)
        .map { stringResource(it.label) }
    return (listOfNotNull(rating) + attributes).joinToString(SEPARATOR).ifEmpty { null }
}

@Composable
private fun SupportingLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@get:StringRes
private val AttributeType.label: Int
    get() = when (this) {
        AttributeType.PARKING -> R.string.attribute_parking
        AttributeType.TOILETS -> R.string.attribute_toilets
        AttributeType.CAFE -> R.string.attribute_cafe
        AttributeType.PLAYGROUND -> R.string.attribute_playground
        AttributeType.TRAILS -> R.string.attribute_trails
        AttributeType.BEACH -> R.string.attribute_beach
        AttributeType.VIEWPOINT -> R.string.attribute_viewpoint
        AttributeType.MUSEUM -> R.string.attribute_museum
        AttributeType.FAMILY_FRIENDLY -> R.string.attribute_family_friendly
        AttributeType.DRIVE_THROUGH -> R.string.attribute_drive_through
    }
```

- [ ] **Step 8: `RecommendationsScreen`**

Replace `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt` with:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.discovery.DiscoverError
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.components.Message
import com.kanyandula.discovernearby.ui.components.MessageState
import com.kanyandula.discovernearby.ui.components.RecommendationRow
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.Highlight
import com.kanyandula.discovernearby.ui.theme.MessageGap
import com.kanyandula.discovernearby.ui.theme.MessageIconSize
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.OnSurfaceVariant
import com.kanyandula.discovernearby.ui.theme.Raised
import com.kanyandula.discovernearby.ui.theme.RowGap
import com.kanyandula.discovernearby.ui.theme.SpinnerStroke
import com.kanyandula.discovernearby.ui.visual

private val EmptyMessage = Message(R.drawable.ic_empty, Highlight, R.string.empty_title, R.string.empty_body)
private val LoadFailedMessage =
    Message(R.drawable.ic_error_network, OnSurfaceVariant, R.string.load_failed_title, R.string.load_failed_body)
private val TimeoutMessage = Message(R.drawable.ic_timeout, Highlight, R.string.timeout_title, R.string.timeout_body)
private val NoLocationMessage =
    Message(R.drawable.ic_location_off, OnSurfaceVariant, R.string.no_location_title, R.string.no_location_body)

/** Every state draws on this one destination; a state change never pushes a screen (docs/02 §6). */
@Composable
fun RecommendationsScreen(
    category: DiscoveryCategory,
    state: RecommendationsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ContentGap)) {
            // The AOSP car system bar has no Back button, so the screen provides one (docs/02 §3.6).
            IconButton(onClick = onBack, modifier = Modifier.size(MinTouchTarget)) {
                Icon(painter = painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
            Text(text = stringResource(category.visual.label), style = MaterialTheme.typography.headlineMedium)
        }
        val body = Modifier.weight(1f)
        when (state) {
            RecommendationsUiState.Loading -> LoadingState(body)
            is RecommendationsUiState.Content ->
                LazyColumn(modifier = body, verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    items(state.recommendations, key = { it.place.id }) { RecommendationRow(it) }
                }
            RecommendationsUiState.Empty ->
                MessageState(EmptyMessage, backLabel = R.string.back_to_discover, onBack = onBack, modifier = body)
            is RecommendationsUiState.Error ->
                MessageState(state.type.message, R.string.back, onBack = onBack, modifier = body, onRetry = onRetry)
            // ponytail: Back only; DN-M0-006 adds Grant Permission and its launcher (docs/02 §10).
            is RecommendationsUiState.PermissionRequired ->
                MessageState(permissionMessage(state.canRequest), R.string.back, onBack = onBack, modifier = body)
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MessageGap, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(MessageIconSize),
            color = Highlight,
            strokeWidth = SpinnerStroke,
            trackColor = Raised,
        )
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
    }
}

// docs/02 §12 lets one message cover all three; the timeout design (10-timeout.png) gives it its own.
private val DiscoverError.message: Message
    get() = when (this) {
        DiscoverError.NetworkUnavailable, DiscoverError.ProviderFailure -> LoadFailedMessage
        DiscoverError.Timeout -> TimeoutMessage
        DiscoverError.LocationUnavailable -> NoLocationMessage
    }

private fun permissionMessage(canRequest: Boolean) = Message(
    icon = R.drawable.ic_location_off,
    tint = OnSurfaceVariant,
    title = R.string.permission_title,
    body = if (canRequest) R.string.permission_body else R.string.permission_body_restricted,
)
```

- [ ] **Step 9: Keep `DiscoverNavHost` compiling until Task 5 connects the ViewModel**

In `DiscoverNavHost.kt` import `com.kanyandula.discovernearby.ui.screens.RecommendationsUiState` and change the call:

```kotlin
            RecommendationsScreen(
                category = entry.toRoute<RecommendationsRoute>().category,
                state = RecommendationsUiState.Loading, // Task 5 replaces this with the ViewModel's state
                onRetry = {},
                onBack = { if (navController.isTop(entry)) navController.popBackStack() },
            )
```

- [ ] **Step 10: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsScreenTest' --tests '*DiscoverNearbyThemeTest' --console=plain -q 2>&1 | tail -15`
Expected: no output (8 + 5 tests pass).

- [ ] **Step 11: Commit**

```bash
git add app/src/main/res app/src/main/java/com/kanyandula/discovernearby/ui \
  app/src/test/java/com/kanyandula/discovernearby/ui
git commit -m "Draw Recommendations loading, list and message states"
```

---

### Task 5: Wire the destination

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNearbyApp.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/TestScreens.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNavigationTest.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNearbyThemeTest.kt`

**Interfaces:**
- Consumes: `RecommendationsViewModel`, `uiState`, `retry()` (Task 3); `RecommendationsScreen(category, state, onRetry, onBack)` (Task 4); `DiscoverUseCase`, `BasicRecommendationEngine` (Tasks 1–2).
- Produces: `AppContainer.discoverUseCase: DiscoverUseCase`; `DiscoverNearbyApp(container: AppContainer, modifier: Modifier = Modifier)`; `DiscoverNavHost(container: AppContainer, modifier: Modifier = Modifier, navController: NavHostController = rememberNavController())`; test helper `appContainer(): AppContainer`.

- [ ] **Step 1: Write the failing test**

`TestScreens.kt`, append (imports `org.robolectric.RuntimeEnvironment`, `com.kanyandula.discovernearby.DiscoverApplication`):

```kotlin

/** The app's own container, as MainActivity passes it (Robolectric creates DiscoverApplication). */
fun appContainer() = (RuntimeEnvironment.getApplication() as DiscoverApplication).container
```

`DiscoverNavigationTest.setUp`: `rule.setContent { DiscoverNearbyTheme { DiscoverNearbyApp(appContainer()) } }`. Add:

```kotlin
    // Loading then content draw on the one destination: a single Back returns to Discover.
    @Test
    fun recommendationsShowFakePlacesOnOneDestination() {
        rule.onNodeWithText("Family").performClick()
        rule.onNodeWithText("Adventure Playground, Greystones").assertIsDisplayed()
        systemBack()
        onDiscover()
    }
```

`DiscoverNearbyThemeTest.appShowsItsTitle`: `rule.setContent { DiscoverNearbyTheme { DiscoverNearbyApp(appContainer()) } }`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiscoverNavigationTest' --console=plain -q 2>&1 | tail -5`
Expected: compilation fails, `Too many arguments` for `DiscoverNearbyApp`.

- [ ] **Step 3: Implement**

`AppContainer.kt`: add the import `com.kanyandula.discovernearby.discovery.BasicRecommendationEngine` and `…discovery.DiscoverUseCase`, and after `drivingRestrictions`:

```kotlin
    val discoverUseCase = DiscoverUseCase(placesRepository, locationProvider, BasicRecommendationEngine())
```

`DiscoverNavHost.kt`: drop Task 4's `RecommendationsUiState` import; add imports `androidx.compose.runtime.getValue`, `androidx.lifecycle.compose.collectAsStateWithLifecycle`, `androidx.lifecycle.viewmodel.compose.viewModel`, `com.kanyandula.discovernearby.AppContainer`, `com.kanyandula.discovernearby.ui.screens.RecommendationsViewModel`; change the signature and the Recommendations destination:

```kotlin
@Composable
fun DiscoverNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
```

```kotlin
        composable<RecommendationsRoute> { entry ->
            val category = entry.toRoute<RecommendationsRoute>().category
            // Scoped to this back-stack entry: Back clears it, which cancels its request (docs/04 Scenario P).
            val viewModel = viewModel {
                RecommendationsViewModel(category, container.discoverUseCase, container.drivingRestrictions)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            RecommendationsScreen(
                category = category,
                state = state,
                onRetry = viewModel::retry,
                onBack = { if (navController.isTop(entry)) navController.popBackStack() },
            )
        }
```

`DiscoverNearbyApp.kt`: import `com.kanyandula.discovernearby.AppContainer`; `fun DiscoverNearbyApp(container: AppContainer, modifier: Modifier = Modifier)`; `DiscoverNavHost(container = container, modifier = Modifier.padding(PanelPadding))`.

`MainActivity.kt` becomes (the interim collector goes: the ViewModel now collects):

```kotlin
package com.kanyandula.discovernearby.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kanyandula.discovernearby.DiscoverApplication
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DiscoverApplication).container
        setContent {
            DiscoverNearbyTheme {
                DiscoverNearbyApp(container)
            }
        }
    }
}
```

- [ ] **Step 4: Run the whole suite**

Run: `./gradlew :app:testDebugUnitTest --console=plain -q 2>&1 | tail -15`
Expected: no output (all tests pass, including `recommendationsShowFakePlacesOnOneDestination` and the existing navigation tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby app/src/test/java/com/kanyandula/discovernearby/ui
git commit -m "Wire Recommendations to its ViewModel and drop the interim collector"
```

---

### Task 6: Pick the fake scenario at run time

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/places/fake/FakePlacesRepository.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/places/fake/FakePlacesRepositoryTest.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/ui/ScenarioExtraTest.kt`

**Interfaces:**
- Consumes: `FakeScenario`, `FakePlacesRepository` (DN-M0-003); `MainActivity` (Task 5).
- Produces: `FakePlacesRepository(var scenario: FakeScenario = FakeScenario.NORMAL)`; `AppContainer.fakePlaces: FakePlacesRepository`; `internal const val EXTRA_SCENARIO = "scenario"`.

- [ ] **Step 1: Write the failing tests**

`FakePlacesRepositoryTest.kt`, add:

```kotlin
    // Debug launches switch the scenario on the running app's repository (docs/04 §7).
    @Test
    fun scenarioCanChangeAtRunTime() = runTest {
        val repository = FakePlacesRepository()
        assertTrue(repository.at(TestLocation.GREYSTONES, DiscoveryCategory.COFFEE).isNotEmpty())
        repository.scenario = FakeScenario.EMPTY
        assertTrue(repository.at(TestLocation.GREYSTONES, DiscoveryCategory.COFFEE).isEmpty())
    }
```

`app/src/test/java/com/kanyandula/discovernearby/ui/ScenarioExtraTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import com.kanyandula.discovernearby.DiscoverApplication
import com.kanyandula.discovernearby.places.fake.FakeScenario
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ScenarioExtraTest {

    private val app = RuntimeEnvironment.getApplication() as DiscoverApplication

    private fun launch(scenario: String) {
        val intent = Intent(app, MainActivity::class.java).putExtra(EXTRA_SCENARIO, scenario)
        Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    }

    @Test
    fun debugLaunchSelectsTheFakeScenario() {
        launch("SLOW")
        assertEquals(FakeScenario.SLOW, app.container.fakePlaces.scenario)
    }

    @Test
    fun nonDebuggableBuildIgnoresTheExtra() {
        app.applicationInfo.flags = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        launch("SLOW")
        assertEquals(FakeScenario.NORMAL, app.container.fakePlaces.scenario)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*FakePlacesRepositoryTest' --tests '*ScenarioExtraTest' --console=plain -q 2>&1 | tail -5`
Expected: compilation fails (`'val' cannot be reassigned`, `Unresolved reference 'EXTRA_SCENARIO'`).

- [ ] **Step 3: Implement**

`FakePlacesRepository.kt`: the constructor parameter becomes `var scenario: FakeScenario = FakeScenario.NORMAL,` (public, mutable).

`AppContainer.kt`: replace the fakes block with:

```kotlin
    // ponytail: fakes until the provider (M1, after ADR-001) and AndroidLocationProvider (DN-M0-006).
    // Debug launches choose fakePlaces.scenario for the docs/04 §7 emulator scenarios (MainActivity).
    val fakePlaces = FakePlacesRepository()
    val placesRepository: PlacesRepository = fakePlaces
```

`MainActivity.kt`: add imports `android.content.pm.ApplicationInfo`, `com.kanyandula.discovernearby.places.fake.FakeScenario`; above the class:

```kotlin
/** Debug builds only: the [FakeScenario] to serve, for the docs/04 §7 emulator scenarios. */
internal const val EXTRA_SCENARIO = "scenario"
```

and after `val container = …`:

```kotlin
        // adb shell am start -S -n com.kanyandula.discovernearby/.ui.MainActivity --es scenario SLOW
        if (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            intent.getStringExtra(EXTRA_SCENARIO)?.let { container.fakePlaces.scenario = FakeScenario.valueOf(it) }
        }
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*FakePlacesRepositoryTest' --tests '*ScenarioExtraTest' --console=plain -q 2>&1 | tail -5`
Expected: no output. If `nonDebuggableBuildIgnoresTheExtra` fails because the activity sees a different `ApplicationInfo`, rule on it in the ledger (the gate must read the flag the test can clear); do not drop the test.

- [ ] **Step 5: Full check and commit**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL`-equivalent silence; no detekt or lint findings.

```bash
git add app/src/main/java/com/kanyandula/discovernearby app/src/test/java/com/kanyandula/discovernearby
git commit -m "Select the fake scenario with a debug-only launch extra"
```

---

### Task 7: Verify on the AAOS userdebug emulator

Each Bash call starts from a fresh shell, so the helpers are scripts and every block below sets `S` to this session's scratchpad directory first. Write them once:

```bash
S=<scratchpad>
cat > "$S/tap.sh" <<'EOF'
#!/bin/bash
# Tap the centre of the first UI node whose text or content-desc is exactly $1.
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
read -r x1 y1 x2 y2 < <(adb -s emulator-5554 shell cat /sdcard/ui.xml | tr '>' '\n' \
  | grep -m1 -E "(text|content-desc)=\"$1\"" \
  | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
adb -s emulator-5554 shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
EOF
cat > "$S/waitfor.sh" <<'EOF'
#!/bin/bash
# Wait up to ${2:-30} s for a UI node whose text or content-desc contains $1; print it. Exits 1 on timeout,
# so `waitfor.sh never-appears N` doubles as an N-second pause.
for _ in $(seq 1 "${2:-30}"); do
  adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb -s emulator-5554 shell cat /sdcard/ui.xml | tr '>' '\n' \
    | grep -m1 -E "(text|content-desc)=\"[^\"]*$1" && exit 0
  sleep 1
done
echo "timed out: $1" >&2; exit 1
EOF
cat > "$S/launch.sh" <<'EOF'
#!/bin/bash
# Force-stop, then start the app with any extra arguments (e.g. --es scenario SLOW).
adb -s emulator-5554 shell am start -S -n com.kanyandula.discovernearby/.ui.MainActivity "$@"
EOF
cat > "$S/shot.sh" <<'EOF'
#!/bin/bash
adb -s emulator-5554 exec-out screencap -p > "$(dirname "$0")/m0004-$1.png"
EOF
chmod +x "$S"/{tap,waitfor,launch,shot}.sh
```

- [ ] **Step 1: Emulator, install, Park**

```bash
S=<scratchpad>
adb devices                                  # expect emulator-5554; start AAOS_AOSP_33_userdebug with -port 5554 if absent
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain | grep -E "Installed on|BUILD"
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
adb -s emulator-5554 logcat -c
```

- [ ] **Step 2: Scenario A, and the restrictions connection follows the screen**

```bash
S=<scratchpad>
"$S/launch.sh" && "$S/waitfor.sh" Outdoors 30
adb -s emulator-5554 logcat -d -s DrivingRestrictions | tail -3   # expect nothing: Discover does not collect
"$S/tap.sh" Outdoors && "$S/waitfor.sh" ", Greystones" 10 && "$S/shot.sh" outdoors
adb -s emulator-5554 logcat -d -s DrivingRestrictions | tail -3   # connected, DrivingState(false, null)
"$S/tap.sh" Back && "$S/waitfor.sh" Coffee 5
"$S/waitfor.sh" "never-appears" 7; adb -s emulator-5554 logcat -d -s DrivingRestrictions | tail -2
```

Expected: no restriction logs on Discover; `connected` once Outdoors opens; four Outdoors rows ending ", Greystones", nearest first, matching `02-recommendations.png` (no photo, no attribution: open decision 2); `disconnected` about 5 s after Back.

- [ ] **Step 3: Scenario I (empty)**

```bash
S=<scratchpad>
"$S/launch.sh" --es scenario EMPTY && "$S/waitfor.sh" Scenic 30
"$S/tap.sh" Scenic && "$S/waitfor.sh" "No good matches nearby" 10 && "$S/shot.sh" empty
"$S/tap.sh" "Back to Discover" && "$S/waitfor.sh" Coffee 5
```

Expected: matches `08-empty.png`; Back to Discover returns to the grid.

- [ ] **Step 4: Scenario J (network and provider failure)**

```bash
S=<scratchpad>
"$S/launch.sh" --es scenario NETWORK_FAILURE && "$S/waitfor.sh" Coffee 30
"$S/tap.sh" Coffee && "$S/waitfor.sh" "Unable to load places" 10 && "$S/shot.sh" network-error
"$S/tap.sh" "Try Again" && "$S/waitfor.sh" "Unable to load places" 10
"$S/tap.sh" Back && "$S/waitfor.sh" Food 5
"$S/launch.sh" --es scenario PROVIDER_FAILURE && "$S/waitfor.sh" Coffee 30
"$S/tap.sh" Coffee && "$S/waitfor.sh" "Unable to load places" 10
```

Expected: matches `09-network-error.png`; Try Again reloads into the same message; Back (button) returns to Discover; no crash.

- [ ] **Step 5: Scenario O (timeout)**

```bash
S=<scratchpad>
"$S/launch.sh" --es scenario SLOW && "$S/waitfor.sh" Coffee 30
"$S/tap.sh" Coffee && "$S/waitfor.sh" "Finding good places" 5 && "$S/shot.sh" loading
start=$(date +%s); "$S/waitfor.sh" "Taking longer than expected" 15 && echo "after $(( $(date +%s) - start )) s" && "$S/shot.sh" timeout
"$S/tap.sh" "Try Again" && "$S/waitfor.sh" "Finding good places" 5
```

Expected: loading matches `07-loading.png`; the timeout message appears about 8 s after opening (the measured figure includes uiautomator latency) and matches `10-timeout.png`; Try Again shows loading again.

- [ ] **Step 6: Scenario P (slow Coffee overtaken by Family)**

```bash
S=<scratchpad>
"$S/launch.sh" --es scenario SLOW && "$S/waitfor.sh" Coffee 30
"$S/tap.sh" Coffee && "$S/waitfor.sh" "Finding good places" 5
"$S/tap.sh" Back && "$S/waitfor.sh" Family 5
"$S/tap.sh" Family && "$S/waitfor.sh" "Adventure Playground" 10
"$S/waitfor.sh" "Taking longer than expected" 12; "$S/waitfor.sh" "Adventure Playground" 2 && "$S/shot.sh" scenario-p
```

Expected: Family rows show at once; 12 s later (past Coffee's 10 s delay and 8 s timeout) Family rows are still shown and no timeout message appeared. The "location change during loading" half of P needs a real location (DN-M0-006); record that.

- [ ] **Step 7: Scenario Q (sparse, null-heavy) and R (selectable)**

```bash
S=<scratchpad>
"$S/launch.sh" --es scenario SPARSE && "$S/waitfor.sh" Scenic 30
"$S/tap.sh" Scenic && "$S/waitfor.sh" ", Greystones" 10 && "$S/shot.sh" sparse
"$S/launch.sh" --es scenario NULL_HEAVY && "$S/waitfor.sh" Coffee 30
"$S/tap.sh" Coffee && "$S/waitfor.sh" ", Greystones" 10 && "$S/shot.sh" null-heavy
"$S/launch.sh" --es scenario DETAILS_FAILURE && "$S/waitfor.sh" Coffee 30
"$S/tap.sh" Coffee && "$S/waitfor.sh" ", Greystones" 10
```

Expected: SPARSE shows exactly two Scenic rows, no padding, like `12-sparse-results.png`; NULL_HEAVY rows show name and distance only (no ★, no blank line); DETAILS_FAILURE launches and lists normally (its failure belongs to Place Details, DN-M0-005).

- [ ] **Step 8: Drive (moving) and Park on the list**

```bash
S=<scratchpad>
"$S/launch.sh" && "$S/waitfor.sh" Coffee 30 && "$S/tap.sh" Coffee && "$S/waitfor.sh" ", Greystones" 10
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8
adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 30 &
"$S/waitfor.sh" "never-appears" 5; adb -s emulator-5554 logcat -d -s DrivingRestrictions | tail -2; "$S/shot.sh" drive
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
"$S/waitfor.sh" "never-appears" 3; adb -s emulator-5554 logcat -d -s DrivingRestrictions | tail -1
```

Expected: `DrivingState(distractionOptimizationRequired=true, listLimit=21)` while moving, then `(false, null)` in Park; the list stays on screen without a reload and keeps its five Coffee rows (min(5, 21) = 5; open decision 9).

- [ ] **Step 9:** View every `m0004-*.png` against its design PNG; record differences (expected: no photo or attribution, 22 sp message body) in the ticket notes.

---

### Task 8: Close out

- [ ] **Step 1:** `CLAUDE.md` "Current state": replace `Next: DN-M0-004 / 011.` with ``Recommendations flow on fake data (DN-M0-004): `RecommendationEngine`, `DiscoverUseCase`, `RecommendationsViewModel`; debug launches take `--es scenario <FakeScenario>`. Next: DN-M0-005 / 006 / 011.``; commit.

- [ ] **Step 2:** If `graphify-out/` exists, `graphify update .` (not committed; it is gitignored).

- [ ] **Step 3:** Push; draft PR; CI `build` passes.

- [ ] **Step 4:** `simplify`; apply; re-run the full check; commit; push; CI.

- [ ] **Step 5:** Ticket completion notes: open decisions (including the docs/02 §12 vs `10-timeout.png` discrepancy for a doc fix), emulator evidence per scenario, every check with its result, deferred items (photo, attribution, row selection, Grant, location change during loading).

- [ ] **Step 6:** Final whole-branch review by a fresh reviewer (opus); fix Critical/Important test-first.

- [ ] **Step 7:** `pr-description` (ticket ID and acceptance criteria met); `gh pr ready`.

- [ ] **Step 8: After the user merges** — ticket `done`; DN-M0-005 and DN-M0-006 `ready` (their dependencies are done); `NOW.md`; delete the branch locally and remotely.
