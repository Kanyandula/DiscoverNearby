# DN-M0-005 Place Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Selecting a recommendation opens a Place Details destination that shows what is known about the place, offers Navigate at once, fetches richer details without ever blocking on them, and falls back to the summary when they fail; Back returns to the list.

**Architecture:** The route carries the selected `Recommendation` as JSON through a small `JsonNavType`, so Details shows the summary immediately and keeps it after a failed details call or process death. `PlaceDetailsViewModel` asks `DiscoverUseCase.details()` (same provider timeout) for richer data and exposes `Loading(summary)` → `Content(details)` or `SummaryOnly(summary)`; `navigate()` hands the summary's location to a `NavigationLauncher` from `AppContainer` (an M0 fake; DN-M3-001 brings the intent hand-off). One stateless `PlaceDetailsScreen` draws all three states with Navigate in a fixed place.

**Tech Stack:** Navigation Compose 2.10.2 type-safe routes with a custom `NavType`, kotlinx.serialization 1.11.0, Compose Material 3, kotlinx-coroutines-test, Robolectric 4.17.

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-005-place-details-and-navigation-seam.md`; `docs/02-discover-nearby-ux-interaction-spec.md` §7, §14; `docs/03-discover-nearby-engineering-implementation-plan.md` §3, §6, §8, §11, §15, §16; `docs/04-discover-nearby-test-demo-plan.md` §7 R; design PNGs `docs/design/03-place-details.png`, `13-details-fallback.png` and their canvas artboards.

## Global Constraints

- Place Details is one destination backed by `PlaceDetailsViewModel`; Loading, Content and SummaryOnly are its states, never pushed destinations (ticket AC; docs/03 §6).
- Details show the available summary fields and only PROVIDED or DERIVED attributes; unknown fields are left out, never blank (ticket AC; docs/02 §7).
- A failed (or timed-out) details request shows `SummaryOnly`: the summary stays visible and Navigate stays available; Navigate never waits for details (ticket AC; docs/02 §7; docs/03 §16).
- `NavigationLauncher` is an interface provided by `AppContainer`, `fun navigateTo(point: GeoPoint): Result<Unit>` (docs/03 §11); M0 uses a fake (ticket AC).
- Provider calls have a timeout (`PROVIDER_TIMEOUT_MILLIS`, docs/03 §15).
- Back returns to Recommendations (ticket AC).
- No raw coordinates or keys in logs (CLAUDE.md "Secrets"); all user text in strings.xml; touch targets ≥ 76 dp (docs/02 §15); nothing named "parked".
- Only `ui/` imports Compose; only `car/CarDrivingRestrictions.kt` imports `android.car` (`ArchitectureRulesTest`).
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes; never commit on local `main`; no AI attribution.

## Open decisions (recorded here, in the ticket and the PR)

1. **The route carries the whole `Recommendation` as JSON.** `GeoPoint`, `PlaceAttribute`, `PlaceSummary` and `Recommendation` become `@Serializable`; `JsonNavType` encodes the value for the route and the saved state. Details then has the summary (and the list's distance) at once, after a failed details call, and after process death, with no shared "selected place" holder. Cost: the domain model carries kotlinx.serialization annotations (pure Kotlin; the plugin is already applied).
2. **One layout for every state, Navigate fixed at the bottom of the right column** (the fallback artboard's position). The content artboard puts Navigate under the photo; with no photo (decision 5) it would otherwise jump when details arrive.
3. **SummaryOnly shows the same sections as Content, built from the summary, plus "More details unavailable right now".** The fallback artboard rearranges rating and attributes; keeping one arrangement means nothing moves when the state changes. Reviews read "(342 reviews)" in both.
4. **Opening line:** the details' `openingSummary` when present (fake: "Open until 18:00"), else "Open now" / "Closed now" from the summary's `isOpenNow`; green when open (`#6CD69A`, Details artboard).
5. **No photo, attribution or place-kind label** ("Café" under the distance). Photo and attribution wait for ADR-001 (DN-M1-003); the kind vocabulary is fixed when categories map to provider types (DN-M2-001). docs/02 §7 lists all three as "where available".
6. **Navigate does nothing visible in M0.** `FakeNavigationLauncher` records the destination and succeeds; the ViewModel does not yet map a failed `Result` (the fake cannot fail). DN-M3-001 adds `IntentNavigationLauncher`, the stub-app check and the Navigation unavailable state.
7. **The details call goes through `DiscoverUseCase.details()`** (ui → discovery → places, docs/03 §4) with the same 8 s timeout; failure or timeout → `null` → `SummaryOnly`.
8. **detekt `LongParameterList` ignores `@Composable` functions**: a screen takes its state plus one lambda per event (`RecommendationsScreen` reaches six with `onPlaceSelected`).
9. **Shared UI pieces move, not copy:** `ScreenHeader` (Back + title) is extracted from `RecommendationsScreen`; attribute labels, `METERS_PER_KM` and the `" · "` separator move from `RecommendationRow` to `ui/PlaceText.kt`.

## Review Focus

1. **A place name with route-unsafe characters** (`& , / ? # % "`) must reach Details intact. Pinned by `JsonNavTypeTest.survivesTheRouteString` and `DiscoverNavigationTest.rowOpensPlaceDetailsAndBackReturnsToTheList` ("Brew & Bloom, Greystones").
2. **Navigate tapped before details arrive, or when they never do,** hands over the summary's location at once. Pinned by `PlaceDetailsViewModelTest.navigateHandsOverThePlaceWhileDetailsLoad` and `PlaceDetailsScreenTest.navigateIsAvailableWhileDetailsLoad`.
3. **Details slower than the timeout** end in `SummaryOnly` at `PROVIDER_TIMEOUT_MILLIS`, not an endless Loading. Pinned by `slowDetailsFallBackAtTheTimeout`.
4. **A double tap on a row** opens one Details screen; one Back returns to the list. Pinned by `DiscoverNavigationTest.doubleTappedRowOpensOneDetailsScreen`.
5. **A summary with nothing optional** shows name and distance only: no ★, no Amenities, no opening line. Pinned by `PlaceDetailsScreenTest.unknownFieldsAreLeftOut`.

---

## Investigation findings (2026-10-04)

- Canvas Place Details artboards (1:1 dp): panel padding 0/32/28/24; header 88 with Back and the place name (32 semibold); body row, gap 40, inset 16. Left column: sections with 18 vertical padding and 1 px `#2A3138` dividers, primary line 28 semibold, secondary 22 `#AEB6BD`, 6 apart: "2.1 km away" / "Café"; "4.6 ★" + "(342 reviews)" in regular grey / "Open now" `#6CD69A`; "Parking · Outdoor seating · Family friendly" / "Amenities". Right column 400 wide: photo 240 high (placeholder), Navigate 88 high, radius 20, `#2563EB`, white 28 semibold, filled arrow icon 28 (`M12 2 4 21l8-4 8 4z`), icon gap 14. Fallback: same first section, "4.6 ★ (342)" / "Parking", then an info row (`ic_info` 24, gap 12, "More details unavailable right now" 22 grey); Navigate at the bottom of the right column.
- `ic_info`: circle(12,12,9) + `M12 8v5M12 16h.01`. `ic_navigate`: `M12 2 4 21l8-4 8 4z`, filled.
- Navigation 2.10.2 `NavType<T>`: `put(Bundle, String, T)`, `get(Bundle, String): T?`, `parseValue(String)`, `serializeAsValue(T)`; custom route types register through `composable<T>(typeMap = …)`, and `NavBackStackEntry.toRoute<T>()` reads them back.
- `FakePlacesRepository.getPlaceDetails`: `openingSummary = "Open until 18:00"` when `isOpenNow == true`; `DETAILS_FAILURE` throws `ProviderFailure`. Coffee at Greystones lists Harbour Roasters, The Daily Grind, Brew & Bloom, Station Espresso (Corner Café is known-closed).
- DN-M3-001 owns the intent hand-off and the Navigation unavailable state; DN-M1-003 owns attribution. After this ticket nothing new becomes ready (DN-M0-007 and DN-M3-001 also need DN-M0-008 / DN-M0-011).

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `…/model/GeoPoint.kt`, `Place.kt`, `PlaceAttribute.kt`, `Recommendation.kt` | Modify | `@Serializable` |
| `…/ui/JsonNavType.kt` | Create | A `@Serializable` value in a route and saved state |
| `…/navigation/NavigationLauncher.kt` | Create | Interface (docs/03 §11) |
| `…/navigation/fake/FakeNavigationLauncher.kt` | Create | Records the destination, succeeds |
| `…/discovery/DiscoverUseCase.kt` | Modify | `details(placeId)` with the provider timeout |
| `…/ui/screens/PlaceDetailsViewModel.kt` | Create | `PlaceDetailsUiState`, the ViewModel |
| `…/ui/PlaceText.kt` | Create | Attribute labels, `METERS_PER_KM`, `SEPARATOR` (moved) |
| `…/ui/components/ScreenHeader.kt` | Create | Back + title (moved out of `RecommendationsScreen`) |
| `…/ui/screens/PlaceDetailsScreen.kt` | Create | Facts, the unavailable note, Navigate |
| `…/ui/components/RecommendationRow.kt`, `ui/screens/RecommendationsScreen.kt` | Modify | Clickable rows; `ScreenHeader`; `onPlaceSelected` |
| `…/ui/DiscoverNavHost.kt`, `…/AppContainer.kt` | Modify | Details destination; `navigationLauncher` |
| `…/ui/theme/Color.kt`, `Dimens.kt`; `res/drawable/ic_navigate.xml`, `ic_info.xml`; `res/values/strings.xml` | Modify/Create | Canvas tokens, icons, copy |
| `config/detekt/detekt.yml` | Modify | `LongParameterList` ignores `@Composable` |
| `…/test/…/ui/JsonNavTypeTest.kt`, `ui/screens/PlaceDetailsViewModelTest.kt`, `ui/screens/PlaceDetailsScreenTest.kt` | Create | Route, ViewModel, screen |
| `…/test/…/places/ScriptedPlaces.kt`, `discovery/DiscoverUseCaseTest.kt`, `ui/screens/RecommendationsScreenTest.kt`, `ui/DiscoverNavigationTest.kt`, `DiscoverApplicationTest.kt` | Modify | Scriptable details; details tests; row selection; navigation; container |

`…` = `app/src/main/java/com/kanyandula/discovernearby`; `…/test/…` = `app/src/test/java/com/kanyandula/discovernearby`.

---

### Task 0: Start the ticket

- [ ] **Step 1:** In the ticket set `status: in_progress`, `branch: dn-m0-005-place-details`.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-005-place-details
git add docs/superpowers/plans/2026-10-04-dn-m0-005-place-details.md
git commit -m "Add DN-M0-005 implementation plan"
```

---

### Task 1: Carry a `Recommendation` in a route

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/model/GeoPoint.kt`, `Place.kt`, `PlaceAttribute.kt`, `Recommendation.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/JsonNavType.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/ui/JsonNavTypeTest.kt`

**Interfaces:**
- Consumes: `testPlace` (`discovery/TestPlaces.kt`, test sources).
- Produces: `@Serializable` on `GeoPoint`, `PlaceAttribute`, `PlaceSummary`, `Recommendation` (so `Recommendation.serializer()` exists); `internal class JsonNavType<T : Any>(serializer: KSerializer<T>) : NavType<T>`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/ui/JsonNavTypeTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import android.net.Uri
import android.os.Bundle
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JsonNavTypeTest {

    private val type = JsonNavType(Recommendation.serializer())

    // Characters a route string must survive, in the place name and the JSON around it.
    private val recommendation = Recommendation(
        place = testPlace("brew-1", "cafe").copy(
            name = "Brew & Bloom, Greystones /?#% \"1\"",
            rating = 4.6,
            ratingCount = 128,
            attributes = setOf(PlaceAttribute(PARKING, PROVIDED)),
        ),
        score = 1.0,
        distanceMeters = 1_900,
        travelTimeMinutes = null,
        minutesAhead = null,
        detourMinutes = null,
    )

    // Navigation decodes the route value before parseValue.
    @Test
    fun survivesTheRouteString() {
        assertEquals(recommendation, type.parseValue(Uri.decode(type.serializeAsValue(recommendation))))
    }

    // The back stack's saved state: Details keeps its place after process death.
    @Test
    fun survivesSavedState() {
        val bundle = Bundle()
        type.put(bundle, "recommendation", recommendation)
        assertEquals(recommendation, type.get(bundle, "recommendation"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*JsonNavTypeTest' --console=plain -q 2>&1 | grep -E "^e:" | head -3`
Expected: compilation fails, `Unresolved reference 'JsonNavType'` / `'serializer'`.

- [ ] **Step 3: Make the model serializable**

In each file add `import kotlinx.serialization.Serializable` and put `@Serializable` on the line above the class:
- `model/GeoPoint.kt`: `data class GeoPoint`
- `model/PlaceAttribute.kt`: `data class PlaceAttribute` (the two enums need no annotation)
- `model/Place.kt`: `data class PlaceSummary` (not `PlaceDetails`)
- `model/Recommendation.kt`: `data class Recommendation`

- [ ] **Step 4: `JsonNavType`**

`app/src/main/java/com/kanyandula/discovernearby/ui/JsonNavType.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Carries a @Serializable value in a type-safe route as JSON (routes otherwise take only primitives). The value
 * also lives in the back stack's saved state, so the destination keeps it across process death.
 */
internal class JsonNavType<T : Any>(private val serializer: KSerializer<T>) : NavType<T>(isNullableAllowed = false) {

    override fun put(bundle: Bundle, key: String, value: T) =
        bundle.putString(key, Json.encodeToString(serializer, value))

    override fun get(bundle: Bundle, key: String): T? = bundle.getString(key)?.let(::parseValue)

    override fun parseValue(value: String): T = Json.decodeFromString(serializer, value)

    // Navigation builds a route string from this: the JSON's quotes and braces and the name need encoding.
    override fun serializeAsValue(value: T): String = Uri.encode(Json.encodeToString(serializer, value))
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*JsonNavTypeTest' --console=plain -q && echo pass`
Expected: `pass` (2 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/model app/src/main/java/com/kanyandula/discovernearby/ui/JsonNavType.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/JsonNavTypeTest.kt
git commit -m "Carry a Recommendation in a navigation route as JSON"
```

---

### Task 2: Navigation seam and the details call

**Files:**
- Create: `app/src/main/java/com/kanyandula/discovernearby/navigation/NavigationLauncher.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/navigation/fake/FakeNavigationLauncher.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/discovery/DiscoverUseCase.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/places/ScriptedPlaces.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/discovery/DiscoverUseCaseTest.kt`

**Interfaces:**
- Consumes: `DiscoverUseCase`, `PROVIDER_TIMEOUT_MILLIS`, `PlacesException` (DN-M0-004); `FakePlacesRepository`, `FakeScenario.DETAILS_FAILURE`, `SLOW_DELAY_MILLIS` (DN-M0-003).
- Produces: `interface NavigationLauncher { fun navigateTo(point: GeoPoint): Result<Unit> }`; `class FakeNavigationLauncher : NavigationLauncher` with `val lastDestination: GeoPoint?`; `suspend fun DiscoverUseCase.details(placeId: String): PlaceDetails?`; `ScriptedPlaces.details: suspend (String) -> PlaceDetails`.

- [ ] **Step 1: Scriptable details in the test repository**

`ScriptedPlaces.kt`: replace `override suspend fun getPlaceDetails(placeId: String): PlaceDetails = error("not used")` with:

```kotlin
    var details: suspend (String) -> PlaceDetails = { error("not used") }

    override suspend fun getPlaceDetails(placeId: String): PlaceDetails = details(placeId)
```

and change the class KDoc to `/** Answers searches with [reply] and details with [details]; records what it was asked and what still runs. */`.

- [ ] **Step 2: Write the failing tests**

`DiscoverUseCaseTest.kt`, add (imports `kotlinx.coroutines.delay`, `org.junit.Assert.assertNull`):

```kotlin
    @Test
    fun detailsComeFromTheProvider() = runTest {
        assertEquals("greystones-coffee-0", useCase().details("greystones-coffee-0")?.summary?.id)
    }

    // docs/02 §7: a failed or slow details call never blocks the screen; the caller keeps the summary.
    @Test
    fun failedOrSlowDetailsAreNull() = runTest {
        assertNull(withScenario(FakeScenario.DETAILS_FAILURE).details("greystones-coffee-0"))
        val slow = ScriptedPlaces().apply {
            details = {
                delay(SLOW_DELAY_MILLIS)
                error("answered after the timeout")
            }
        }
        assertNull(useCase(slow).details("any"))
        assertEquals(PROVIDER_TIMEOUT_MILLIS, currentTime)
    }
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiscoverUseCaseTest' --console=plain -q 2>&1 | grep -E "^e:" | head -3`
Expected: compilation fails, `Unresolved reference 'details'` on `DiscoverUseCase`.

- [ ] **Step 4: Implement**

`DiscoverUseCase.kt`: add `import com.kanyandula.discovernearby.model.PlaceDetails` and, inside the class after `invoke`:

```kotlin
    /** The opened place's richer details, or null when they fail or time out: the screen keeps the summary. */
    suspend fun details(placeId: String): PlaceDetails? = try {
        withTimeoutOrNull(timeoutMillis) { places.getPlaceDetails(placeId) }
    } catch (ignored: PlacesException) {
        null
    }
```

`app/src/main/java/com/kanyandula/discovernearby/navigation/NavigationLauncher.kt`:

```kotlin
package com.kanyandula.discovernearby.navigation

import com.kanyandula.discovernearby.model.GeoPoint

/** Hands a destination to the vehicle's navigation app (docs/03 §11); never targets a specific app. */
interface NavigationLauncher {
    fun navigateTo(point: GeoPoint): Result<Unit>
}
```

`app/src/main/java/com/kanyandula/discovernearby/navigation/fake/FakeNavigationLauncher.kt`:

```kotlin
package com.kanyandula.discovernearby.navigation.fake

import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.navigation.NavigationLauncher

/** Records the destination and reports success. ponytail: DN-M3-001 swaps in IntentNavigationLauncher. */
class FakeNavigationLauncher : NavigationLauncher {
    var lastDestination: GeoPoint? = null
        private set

    override fun navigateTo(point: GeoPoint): Result<Unit> {
        lastDestination = point
        return Result.success(Unit)
    }
}
```

- [ ] **Step 5: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiscoverUseCaseTest' --console=plain -q && echo pass`
Expected: `pass` (9 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/navigation \
  app/src/main/java/com/kanyandula/discovernearby/discovery/DiscoverUseCase.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/ScriptedPlaces.kt \
  app/src/test/java/com/kanyandula/discovernearby/discovery/DiscoverUseCaseTest.kt
git commit -m "Add NavigationLauncher and a timed details call"
```

---

### Task 3: `PlaceDetailsViewModel`

**Files:**
- Create: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModelTest.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModel.kt`

**Interfaces:**
- Consumes: `DiscoverUseCase.details`, `PROVIDER_TIMEOUT_MILLIS`, `NavigationLauncher`, `FakeNavigationLauncher` (Task 2); `ScriptedPlaces`, `MainDispatcherRule`, `testPlace`, `BasicRecommendationEngine`, `FakeLocationProvider` (existing).
- Produces: `sealed interface PlaceDetailsUiState { Loading(summary: PlaceSummary); Content(details: PlaceDetails); SummaryOnly(summary: PlaceSummary) }`; `class PlaceDetailsViewModel(place: PlaceSummary, discover: DiscoverUseCase, navigation: NavigationLauncher) : ViewModel` with `val uiState: StateFlow<PlaceDetailsUiState>` and `fun navigate()`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModelTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import com.kanyandula.discovernearby.MainDispatcherRule
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.PROVIDER_TIMEOUT_MILLIS
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.navigation.fake.FakeNavigationLauncher
import com.kanyandula.discovernearby.places.ProviderFailure
import com.kanyandula.discovernearby.places.ScriptedPlaces
import com.kanyandula.discovernearby.places.fake.SLOW_DELAY_MILLIS
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Content
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Loading
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.SummaryOnly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy
class PlaceDetailsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val places = ScriptedPlaces()
    private val launcher = FakeNavigationLauncher()
    private val place = testPlace("p1", "cafe", metersNorth = 500)
    private val loaded = PlaceDetails(place.copy(rating = 4.6), openingSummary = "Open until 18:00", attribution = null)

    private fun viewModel() = PlaceDetailsViewModel(
        place = place,
        discover = DiscoverUseCase(places, FakeLocationProvider(), BasicRecommendationEngine()),
        navigation = launcher,
    )

    private fun slowDetails(millis: Long): suspend (String) -> PlaceDetails = {
        delay(millis)
        loaded
    }

    @Test
    fun showsTheSummaryWhileDetailsLoad() = runTest {
        places.details = slowDetails(1_000)
        val vm = viewModel()
        runCurrent()
        assertEquals(Loading(place), vm.uiState.value)
        advanceTimeBy(1_001)
        assertEquals(Content(loaded), vm.uiState.value)
    }

    @Test
    fun detailsFailureFallsBackToTheSummary() = runTest {
        places.details = { throw ProviderFailure() }
        val vm = viewModel()
        runCurrent()
        assertEquals(SummaryOnly(place), vm.uiState.value)
    }

    @Test
    fun slowDetailsFallBackAtTheTimeout() = runTest {
        places.details = slowDetails(SLOW_DELAY_MILLIS)
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(PROVIDER_TIMEOUT_MILLIS - 1)
        assertEquals(Loading(place), vm.uiState.value)
        advanceTimeBy(2)
        assertEquals(SummaryOnly(place), vm.uiState.value)
    }

    // docs/02 §7: Navigate never waits for the optional details call.
    @Test
    fun navigateHandsOverThePlaceWhileDetailsLoad() = runTest {
        places.details = slowDetails(SLOW_DELAY_MILLIS)
        val vm = viewModel()
        runCurrent()
        vm.navigate()
        assertEquals(place.location, launcher.lastDestination)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaceDetailsViewModelTest' --console=plain -q 2>&1 | grep -E "^e:" | head -3`
Expected: compilation fails, `Unresolved reference 'PlaceDetailsUiState'`.

- [ ] **Step 3: Implement**

`app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModel.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.navigation.NavigationLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Place Details destination's states (docs/03 §15). ponytail: NavigationUnavailable arrives with the real
 * hand-off (DN-M3-001).
 */
sealed interface PlaceDetailsUiState {
    data class Loading(val summary: PlaceSummary) : PlaceDetailsUiState
    data class Content(val details: PlaceDetails) : PlaceDetailsUiState
    data class SummaryOnly(val summary: PlaceSummary) : PlaceDetailsUiState
}

class PlaceDetailsViewModel(
    private val place: PlaceSummary,
    discover: DiscoverUseCase,
    private val navigation: NavigationLauncher,
) : ViewModel() {

    private val state = MutableStateFlow<PlaceDetailsUiState>(PlaceDetailsUiState.Loading(place))
    val uiState: StateFlow<PlaceDetailsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = discover.details(place.id)?.let { PlaceDetailsUiState.Content(it) }
                ?: PlaceDetailsUiState.SummaryOnly(place)
        }
    }

    // Navigate never waits for details (docs/02 §7). ponytail: the M0 fake cannot fail; DN-M3-001 maps a failed
    // hand-off to NavigationUnavailable.
    fun navigate() {
        navigation.navigateTo(place.location)
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaceDetailsViewModelTest' --console=plain -q && echo pass`
Expected: `pass` (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModel.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModelTest.kt
git commit -m "Add PlaceDetailsViewModel with a summary-only fallback"
```

---

### Task 4: Place Details screen

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/theme/Color.kt`, `Dimens.kt`
- Create: `app/src/main/res/drawable/ic_navigate.xml`, `ic_info.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/PlaceText.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/components/RecommendationRow.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/components/ScreenHeader.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreen.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreenTest.kt`

**Interfaces:**
- Consumes: `PlaceDetailsUiState` (Task 3); `Action`, `Raised`, `RowGap`, `RowLineGap`, `MinTouchTarget` (theme); `testPlace`.
- Produces: `fun PlaceDetailsScreen(recommendation: Recommendation, state: PlaceDetailsUiState, onNavigate: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)`; `fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier)`; `internal val AttributeType.label: Int`, `internal const val METERS_PER_KM`, `internal const val SEPARATOR` in `ui/PlaceText.kt`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreenTest.kt`:

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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.AttributeSource.DERIVED
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType.FAMILY_FRIENDLY
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Content
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.Loading
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.SummaryOnly
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Stateless screen: each test sets the state; one composition per test, so a state change redraws in place.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class PlaceDetailsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val place = testPlace("The Daily Grind", "cafe").copy(
        rating = 4.6,
        ratingCount = 342,
        isOpenNow = true,
        attributes = setOf(PlaceAttribute(PARKING, PROVIDED), PlaceAttribute(FAMILY_FRIENDLY, DERIVED)),
    )
    private val recommendation = Recommendation(
        place = place,
        score = 1.0,
        distanceMeters = 2_100,
        travelTimeMinutes = null,
        minutesAhead = null,
        detourMinutes = null,
    )
    private var state by mutableStateOf<PlaceDetailsUiState>(Loading(place))
    private var navigations = 0
    private var backs = 0

    @Before
    fun setUp() {
        rule.setContent {
            DiscoverNearbyTheme {
                PlaceDetailsScreen(
                    recommendation = recommendation,
                    state = state,
                    onNavigate = { navigations++ },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun contentShowsWhatIsKnown() {
        state = Content(PlaceDetails(place, openingSummary = "Open until 18:00", attribution = null))
        rule.onNodeWithText("The Daily Grind").assertIsDisplayed()
        rule.onNodeWithText("2.1 km away").assertIsDisplayed()
        rule.onNodeWithText("4.6 ★ (342 reviews)").assertIsDisplayed()
        rule.onNodeWithText("Open until 18:00").assertIsDisplayed()
        rule.onNodeWithText("Parking · Family-friendly").assertIsDisplayed()
        rule.onNodeWithText("Amenities").assertIsDisplayed()
        rule.onNodeWithText("More details unavailable right now").assertDoesNotExist()
    }

    // docs/02 §7: a failed details call keeps the summary and Navigate.
    @Test
    fun summaryOnlyKeepsTheSummaryAndNavigate() {
        state = SummaryOnly(place)
        rule.onNodeWithText("4.6 ★ (342 reviews)").assertIsDisplayed()
        rule.onNodeWithText("Open now").assertIsDisplayed()
        rule.onNodeWithText("More details unavailable right now").assertIsDisplayed()
        rule.onNodeWithText("Navigate").performClick()
        assertEquals(1, navigations)
    }

    @Test
    fun navigateIsAvailableWhileDetailsLoad() {
        rule.onNodeWithText("2.1 km away").assertIsDisplayed()
        rule.onNodeWithText("More details unavailable right now").assertDoesNotExist()
        rule.onNodeWithText("Navigate").performClick()
        assertEquals(1, navigations)
    }

    @Test
    fun unknownFieldsAreLeftOut() {
        state = SummaryOnly(testPlace("Brew & Bloom", "cafe"))
        rule.onNodeWithText("Brew & Bloom").assertIsDisplayed()
        rule.onNodeWithText("2.1 km away").assertIsDisplayed()
        rule.onAllNodesWithText("★", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Amenities").assertDoesNotExist()
        rule.onNodeWithText("Open now").assertDoesNotExist()
    }

    @Test
    fun oneReviewIsSingular() {
        state = SummaryOnly(place.copy(ratingCount = 1))
        rule.onNodeWithText("4.6 ★ (1 review)").assertIsDisplayed()
    }

    @Test
    fun navigateMeetsTheTouchTargetAndBackWorks() {
        rule.onNodeWithText("Navigate").assertHeightIsAtLeast(MinTouchTarget)
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaceDetailsScreenTest' --console=plain -q 2>&1 | grep -E "^e:" | head -3`
Expected: compilation fails, `Unresolved reference 'PlaceDetailsScreen'`.

- [ ] **Step 3: Tokens, icons, copy**

`Color.kt`, append:

```kotlin

// Place Details artboard.
val OpenNow = Color(0xFF6CD69A) // "Open now"
```

`Dimens.kt`, append:

```kotlin

// Place Details artboards.
val DetailsInset = 16.dp
val DetailsColumnGap = 40.dp
val SectionPadding = 18.dp
val ActionColumnWidth = 400.dp
val NavigateHeight = 88.dp
val NavigateRadius = 20.dp
val NavigateIconSize = 28.dp
val NavigateIconGap = 14.dp
val InfoIconSize = 24.dp
val InfoIconGap = 12.dp
```

`app/src/main/res/drawable/ic_navigate.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M12 2 4 21l8-4 8 4z" android:fillColor="#FFFFFFFF" />
</vector>
```

`app/src/main/res/drawable/ic_info.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0 M12 8v5 M12 16h.01"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

`strings.xml`, before `</resources>`:

```xml
    <string name="distance_away">%1$.1f km away</string>
    <plurals name="reviews">
        <item quantity="one">(%d review)</item>
        <item quantity="other">(%d reviews)</item>
    </plurals>
    <string name="open_now">Open now</string>
    <string name="closed_now">Closed now</string>
    <string name="amenities">Amenities</string>
    <string name="details_unavailable">More details unavailable right now</string>
    <string name="navigate">Navigate</string>
```

- [ ] **Step 4: Move shared place text to `ui/PlaceText.kt`**

`app/src/main/java/com/kanyandula/discovernearby/ui/PlaceText.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import androidx.annotation.StringRes
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.model.AttributeType

internal const val METERS_PER_KM = 1_000.0
internal const val SEPARATOR = " · "

@get:StringRes
internal val AttributeType.label: Int
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

`RecommendationRow.kt`: delete `private const val METERS_PER_KM`, `private const val SEPARATOR`, the `@get:StringRes private val AttributeType.label` block and the imports `androidx.annotation.StringRes` and `com.kanyandula.discovernearby.model.AttributeType`; add imports `com.kanyandula.discovernearby.ui.METERS_PER_KM`, `com.kanyandula.discovernearby.ui.SEPARATOR`, `com.kanyandula.discovernearby.ui.label`. `MAX_ROW_ATTRIBUTES` stays.

- [ ] **Step 5: Extract `ScreenHeader`**

`app/src/main/java/com/kanyandula/discovernearby/ui/components/ScreenHeader.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget

/** Back and the screen title. The AOSP car system bar has no Back button, so each screen has one (docs/02 §3.6). */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(MinTouchTarget)) {
            Icon(painter = painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

`RecommendationsScreen.kt`: replace the header `Row { … }` (the `IconButton` and the category `Text`, with its comment) with

```kotlin
        ScreenHeader(title = stringResource(category.visual.label), onBack = onBack)
```

add `import com.kanyandula.discovernearby.ui.components.ScreenHeader`, and remove the imports this leaves unused: `androidx.compose.foundation.layout.Row`, `androidx.compose.material3.Icon`, `androidx.compose.material3.IconButton`, `androidx.compose.ui.res.painterResource`, `com.kanyandula.discovernearby.ui.theme.ContentGap`, `com.kanyandula.discovernearby.ui.theme.MinTouchTarget`.

- [ ] **Step 6: `PlaceDetailsScreen`**

`app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreen.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.METERS_PER_KM
import com.kanyandula.discovernearby.ui.SEPARATOR
import com.kanyandula.discovernearby.ui.components.ScreenHeader
import com.kanyandula.discovernearby.ui.label
import com.kanyandula.discovernearby.ui.theme.Action
import com.kanyandula.discovernearby.ui.theme.ActionColumnWidth
import com.kanyandula.discovernearby.ui.theme.DetailsColumnGap
import com.kanyandula.discovernearby.ui.theme.DetailsInset
import com.kanyandula.discovernearby.ui.theme.InfoIconGap
import com.kanyandula.discovernearby.ui.theme.InfoIconSize
import com.kanyandula.discovernearby.ui.theme.NavigateHeight
import com.kanyandula.discovernearby.ui.theme.NavigateIconGap
import com.kanyandula.discovernearby.ui.theme.NavigateIconSize
import com.kanyandula.discovernearby.ui.theme.NavigateRadius
import com.kanyandula.discovernearby.ui.theme.OpenNow
import com.kanyandula.discovernearby.ui.theme.Raised
import com.kanyandula.discovernearby.ui.theme.RowGap
import com.kanyandula.discovernearby.ui.theme.RowLineGap
import com.kanyandula.discovernearby.ui.theme.SectionPadding

/**
 * Place Details (canvas Place Details artboards): what is known about the place, with Navigate always there and
 * never waiting on the optional details call (docs/02 §7). One layout serves every state, so nothing moves when
 * details arrive. ponytail: no photo, attribution or place-kind label until ADR-001 (DN-M1-003, DN-M2-001).
 */
@Composable
fun PlaceDetailsScreen(
    recommendation: Recommendation,
    state: PlaceDetailsUiState,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = when (state) {
        is PlaceDetailsUiState.Loading -> state.summary
        is PlaceDetailsUiState.Content -> state.details.summary
        is PlaceDetailsUiState.SummaryOnly -> state.summary
    }
    val openingSummary = (state as? PlaceDetailsUiState.Content)?.details?.openingSummary
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        ScreenHeader(title = place.name, onBack = onBack)
        Row(
            modifier = Modifier.weight(1f).padding(start = DetailsInset),
            horizontalArrangement = Arrangement.spacedBy(DetailsColumnGap),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Facts(place, recommendation.distanceMeters, openingSummary)
                if (state is PlaceDetailsUiState.SummaryOnly) DetailsUnavailable()
            }
            NavigateButton(onClick = onNavigate, modifier = Modifier.width(ActionColumnWidth).align(Alignment.Bottom))
        }
    }
}

private class Fact(val primary: AnnotatedString?, val secondary: String?, val secondaryColor: Color)

/** Distance; rating and opening state; amenities. Each only when known, with dividers between. */
@Composable
private fun Facts(place: PlaceSummary, distanceMeters: Int, openingSummary: String?) {
    facts(place, distanceMeters, openingSummary).forEachIndexed { index, fact ->
        if (index > 0) HorizontalDivider(color = Raised)
        Column(
            modifier = Modifier.padding(vertical = SectionPadding),
            verticalArrangement = Arrangement.spacedBy(RowLineGap),
        ) {
            fact.primary?.let { Text(text = it, style = MaterialTheme.typography.headlineSmall) }
            fact.secondary?.let {
                Text(text = it, style = MaterialTheme.typography.titleMedium, color = fact.secondaryColor)
            }
        }
    }
}

@Composable
private fun facts(place: PlaceSummary, distanceMeters: Int, openingSummary: String?): List<Fact> {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val distance = AnnotatedString(stringResource(R.string.distance_away, distanceMeters / METERS_PER_KM))
    val opening = openingSummary ?: when (place.isOpenNow) {
        true -> stringResource(R.string.open_now)
        false -> stringResource(R.string.closed_now)
        null -> null
    }
    val rating = place.rating?.let { rating ->
        buildAnnotatedString {
            append(stringResource(R.string.rating, rating))
            place.ratingCount?.let { count ->
                withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) {
                    append(" ")
                    append(pluralStringResource(R.plurals.reviews, count, count))
                }
            }
        }
    }
    val amenities = place.attributes.map { it.type }.distinct().map { stringResource(it.label) }
    return listOfNotNull(
        Fact(distance, secondary = null, secondaryColor = muted),
        if (rating != null || opening != null) {
            Fact(rating, opening, if (place.isOpenNow == true) OpenNow else muted)
        } else {
            null
        },
        if (amenities.isNotEmpty()) {
            Fact(AnnotatedString(amenities.joinToString(SEPARATOR)), stringResource(R.string.amenities), muted)
        } else {
            null
        },
    )
}

@Composable
private fun DetailsUnavailable() {
    HorizontalDivider(color = Raised)
    Row(
        modifier = Modifier.padding(vertical = SectionPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InfoIconGap),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(InfoIconSize),
        )
        Text(
            text = stringResource(R.string.details_unavailable),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavigateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(NavigateHeight),
        shape = RoundedCornerShape(NavigateRadius),
        colors = ButtonDefaults.buttonColors(containerColor = Action, contentColor = Color.White),
    ) {
        // Decorative: the label says what it does.
        Icon(
            painter = painterResource(R.drawable.ic_navigate),
            contentDescription = null,
            modifier = Modifier.size(NavigateIconSize),
        )
        Spacer(modifier = Modifier.width(NavigateIconGap))
        Text(text = stringResource(R.string.navigate), style = MaterialTheme.typography.headlineSmall)
    }
}
```

- [ ] **Step 7: Run the screen tests and the whole suite**

Run: `./gradlew :app:testDebugUnitTest --console=plain -q && echo pass`
Expected: `pass`; `PlaceDetailsScreenTest` 6/6 and the Recommendations tests unchanged by the `ScreenHeader` extraction.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res app/src/main/java/com/kanyandula/discovernearby/ui \
  app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreenTest.kt
git commit -m "Draw Place Details with a summary-only fallback"
```

---

### Task 5: Rows open Place Details

**Files:**
- Modify: `config/detekt/detekt.yml`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/components/RecommendationRow.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreenTest.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNavigationTest.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/DiscoverApplicationTest.kt`

**Interfaces:**
- Consumes: `JsonNavType` (Task 1); `NavigationLauncher`, `FakeNavigationLauncher` (Task 2); `PlaceDetailsViewModel` (Task 3); `PlaceDetailsScreen` (Task 4).
- Produces: `RecommendationRow(recommendation: Recommendation, onClick: () -> Unit, modifier: Modifier = Modifier)`; `RecommendationsScreen(…, onBack, onPlaceSelected: (Recommendation) -> Unit, modifier)`; `@Serializable data class PlaceDetailsRoute(val recommendation: Recommendation)`; `AppContainer.navigationLauncher: NavigationLauncher`.

- [ ] **Step 1: Write the failing tests**

`RecommendationsScreenTest.kt`: add `private var selected: Recommendation? = null`; pass `onPlaceSelected = { selected = it }` in `setUp`'s `RecommendationsScreen(…)` call; add:

```kotlin
    @Test
    fun tappingARowSelectsIt() {
        val chosen = row(testPlace("Harbour Roasters", "cafe"), 500)
        state = Content(requestId = 1, recommendations = listOf(chosen))
        rule.onNodeWithText("Harbour Roasters").performClick()
        assertEquals(chosen, selected)
    }
```

`DiscoverNavigationTest.kt`, add:

```kotlin
    // The route carries the place as JSON: a name with "&" and "," must arrive intact; Back returns to the list.
    @Test
    fun rowOpensPlaceDetailsAndBackReturnsToTheList() {
        rule.onNodeWithText("Coffee").performClick()
        rule.onNodeWithText("Brew & Bloom, Greystones").performClick()
        rule.onNodeWithText("Navigate").assertIsDisplayed()
        rule.onNodeWithText("Brew & Bloom, Greystones").assertIsDisplayed()
        systemBack()
        rule.onNodeWithText("Harbour Roasters, Greystones").assertIsDisplayed()
        rule.onNodeWithText("Navigate").assertDoesNotExist()
    }

    @Test
    fun doubleTappedRowOpensOneDetailsScreen() {
        rule.onNodeWithText("Coffee").performClick()
        doubleTap(rule.onNodeWithText("Harbour Roasters, Greystones"))
        systemBack()
        rule.onNodeWithText("Brew & Bloom, Greystones").assertIsDisplayed()
    }
```

`DiscoverApplicationTest.containerProvidesItsDependencies`: add `assertTrue(container.navigationLauncher is FakeNavigationLauncher)` (import `com.kanyandula.discovernearby.navigation.fake.FakeNavigationLauncher`).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsScreenTest' --tests '*DiscoverNavigationTest' --tests '*DiscoverApplicationTest' --console=plain -q 2>&1 | grep -E "^e:" | head -3`
Expected: compilation fails (`No parameter with name 'onPlaceSelected'`, `Unresolved reference 'navigationLauncher'`).

- [ ] **Step 3: detekt**

`config/detekt/detekt.yml`, append:

```yaml

complexity:
  LongParameterList:
    # A screen takes its state plus one lambda per event (Compose convention); grouping them only hides the API.
    ignoreAnnotated:
      - "Composable"
```

- [ ] **Step 4: Clickable rows**

`RecommendationRow.kt`: signature `fun RecommendationRow(recommendation: Recommendation, onClick: () -> Unit, modifier: Modifier = Modifier)`; the `Surface(` gains `onClick = onClick,` as its first argument; in the KDoc replace `ponytail: no photo or attribution until ADR-001 says what the provider allows; DN-M0-005 makes the row open Place Details.` with `ponytail: no photo or attribution until ADR-001 says what the provider allows.`

`RecommendationsScreen.kt`: add the parameter `onPlaceSelected: (Recommendation) -> Unit,` after `onBack`; the list item becomes `RecommendationRow(it, onClick = { onPlaceSelected(it) })`; import `com.kanyandula.discovernearby.model.Recommendation`.

- [ ] **Step 5: Container and destination**

`AppContainer.kt`: imports `com.kanyandula.discovernearby.navigation.NavigationLauncher`, `com.kanyandula.discovernearby.navigation.fake.FakeNavigationLauncher`; after `drivingRestrictions`:

```kotlin
    // ponytail: DN-M3-001 swaps in IntentNavigationLauncher (geo: intent, application Context).
    val navigationLauncher: NavigationLauncher = FakeNavigationLauncher()
```

`DiscoverNavHost.kt`: imports `com.kanyandula.discovernearby.model.Recommendation`, `com.kanyandula.discovernearby.ui.screens.PlaceDetailsScreen`, `com.kanyandula.discovernearby.ui.screens.PlaceDetailsViewModel`, `kotlin.reflect.typeOf`; after `RecommendationsRoute`:

```kotlin
@Serializable
data class PlaceDetailsRoute(val recommendation: Recommendation)

// The route carries the whole Recommendation, so Details shows the summary at once and keeps it after a failed
// details call or process death (docs/02 §7).
private val PlaceDetailsTypes = mapOf(typeOf<Recommendation>() to JsonNavType(Recommendation.serializer()))
```

In the Recommendations destination add, after `onBack = …,`:

```kotlin
                onPlaceSelected = { if (navController.isTop(entry)) navController.navigate(PlaceDetailsRoute(it)) },
```

and after that destination:

```kotlin
        composable<PlaceDetailsRoute>(typeMap = PlaceDetailsTypes) { entry ->
            val recommendation = entry.toRoute<PlaceDetailsRoute>().recommendation
            val viewModel = viewModel {
                PlaceDetailsViewModel(recommendation.place, container.discoverUseCase, container.navigationLauncher)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PlaceDetailsScreen(
                recommendation = recommendation,
                state = state,
                onNavigate = viewModel::navigate,
                onBack = { if (navController.isTop(entry)) navController.popBackStack() },
            )
        }
```

- [ ] **Step 6: Full check**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q && echo pass`
Expected: `pass`; lint adds no new warnings beyond the 6 pre-existing ones (a `MissingKeepAnnotation`-style warning on the new route type is the same pre-existing kind; record it if it appears).

- [ ] **Step 7: Commit**

```bash
git add config/detekt/detekt.yml app/src/main/java/com/kanyandula/discovernearby app/src/test/java/com/kanyandula/discovernearby
git commit -m "Open Place Details from a recommendation"
```

---

### Task 6: Verify on the AAOS userdebug emulator

Helpers from DN-M0-004 live in this session's scratchpad (`tap.sh`, `waitfor.sh`, `launch.sh`, `shot.sh`, `texts.sh`, `pause.sh`); every block sets `S=<scratchpad>` first. Taps go to settled screens (2 s pause after launch).

- [ ] **Step 1: Install, Park**

```bash
S=<scratchpad>
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain | grep -E "Installed on|BUILD"
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
```

- [ ] **Step 2: Content (compare `03-place-details.png`), Navigate, Back**

```bash
S=<scratchpad>
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
"$S/tap.sh" Coffee; "$S/waitfor.sh" "Harbour Roasters, Greystones" 10 >/dev/null; "$S/pause.sh" 1
"$S/tap.sh" "Harbour Roasters, Greystones"; "$S/waitfor.sh" Navigate 10 >/dev/null; "$S/pause.sh" 1
"$S/shot.sh" details; "$S/texts.sh" | tr '\n' ' '; echo
"$S/tap.sh" Navigate; "$S/pause.sh" 1; "$S/texts.sh" | grep -c Navigate
"$S/tap.sh" Back; "$S/waitfor.sh" "Brew & Bloom" 5 && echo "Back -> list"
adb -s emulator-5554 logcat -d -b crash | wc -l
```

Expected: title "Harbour Roasters, Greystones"; "0.5 km away"; "4.6 ★ (212 reviews)" / "Open until 18:00" (green); "Parking" / "Amenities"; Navigate bottom-right. Navigate leaves the screen as it is (fake, decision 6). Back shows the list at once. Crash buffer empty.

- [ ] **Step 3: Scenario R, summary only (compare `13-details-fallback.png`)**

```bash
S=<scratchpad>
"$S/launch.sh" --es scenario DETAILS_FAILURE >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
"$S/tap.sh" Coffee; "$S/waitfor.sh" "The Daily Grind, Greystones" 10 >/dev/null; "$S/pause.sh" 1
"$S/tap.sh" "The Daily Grind, Greystones"; "$S/waitfor.sh" "More details unavailable" 10 >/dev/null; "$S/pause.sh" 1
"$S/shot.sh" details-fallback; "$S/texts.sh" | tr '\n' ' '; echo
adb -s emulator-5554 shell input keyevent KEYCODE_BACK; "$S/waitfor.sh" "Harbour Roasters" 5 && echo "system Back -> list"
```

Expected: "1.1 km away"; "4.4 ★ (128 reviews)" / "Open now"; "Drive-through" / "Amenities"; "More details unavailable right now"; Navigate available. System Back returns to the list.

- [ ] **Step 4: Null-heavy and moving Drive**

```bash
S=<scratchpad>
"$S/launch.sh" --es scenario NULL_HEAVY >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
"$S/tap.sh" Coffee; "$S/waitfor.sh" "Corner Café, Greystones" 10 >/dev/null; "$S/pause.sh" 1
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8
nohup adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 20 >/dev/null 2>&1 &
"$S/tap.sh" "Corner Café, Greystones"; "$S/waitfor.sh" Navigate 10 >/dev/null; "$S/pause.sh" 1
"$S/shot.sh" details-null-heavy; "$S/texts.sh" | tr '\n' ' '; echo
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
```

Expected: while moving, Details opens and shows only the name, "0.3 km away" and Navigate (no ★, no Amenities, no opening line).

- [ ] **Step 5:** View each `m0005-*` screenshot against its design PNG; record the differences (no photo, attribution or kind label; one arrangement for both states) in the ticket notes.

---

### Task 7: Close out

- [ ] **Step 1:** `CLAUDE.md` "Current state": replace `Next: DN-M0-005 / 006 / 011.` with ``Place Details (DN-M0-005): rows open `PlaceDetailsRoute(recommendation)` (JSON route via `JsonNavType`), `PlaceDetailsViewModel` falls back to the summary, `NavigationLauncher` is a fake until DN-M3-001. Next: DN-M0-006 / 011.``; commit.

- [ ] **Step 2:** Push; draft PR; CI `build` passes.

- [ ] **Step 3:** `simplify`; apply; re-run the full check; commit; push; CI.

- [ ] **Step 4:** Ticket completion notes: open decisions, emulator evidence, every check with its result, deferred items (photo, attribution, kind label, real hand-off and Navigation unavailable).

- [ ] **Step 5:** Final whole-branch review by a fresh reviewer (opus); fix Critical/Important test-first.

- [ ] **Step 6:** `pr-description` (ticket ID and acceptance criteria met); `gh pr ready`.

- [ ] **Step 7: After the user merges** — ticket `done`; nothing new becomes ready (DN-M0-007 and DN-M3-001 also wait on DN-M0-008, DN-M0-011); `NOW.md` (next: DN-M0-006, DN-M0-011); delete the branch locally and remotely.
