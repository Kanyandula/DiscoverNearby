# Discover Nearby — AAOS Engineering Implementation Plan

**Status:** Proposed — Revision 4  
**Platform:** Android Automotive OS  
**Phase:** Proof of Concept  
**Owner:** Engineering  
**Primary Test Environment:** AAOS Emulator  
**Last Updated:** 2026-10-06

> **Revision 2:** The plan no longer assumes a provider; a provider spike and ADR now come before live POI integration. It names the templates and documents the template-step budget, and makes navigation handoff explicit via `ACTION_NAVIGATE`, verified with a stub navigation app. It adds a permission flow, timeout, stale-request protection, attribute provenance and the `PlaceSummary` / `PlaceDetails` split. Ranking now works with whatever data the provider has, and `RouteRepository` is deferred to M5.
>
> **Revision 3:** This revision locks the build baseline (§6.1) and adds the three-layer test baseline (§20), including `app-testing`. M0 uses `FakePlacesRepository`, and only M1 waits for ADR-001. The provider spike is bounded (§5), and the milestones gain a dependency graph (§21). Growth seams are in §2 ("Built to grow, not built for scale"). Ownership, timeline and the verification register are in `05-discover-nearby-delivery-plan.md`.
>
> **Revision 4:** The UI is built in Kotlin + Jetpack Compose as a distraction-optimized AAOS activity, replacing Car App Library templates. This revision replaces the Car App Library layer with a Compose UI (§3, §6): `MainActivity` + Navigation Compose, a `ViewModel` per screen, `AppContainer` as the wiring point, driving restrictions from `CarUxRestrictionsManager` (NyasaPlayer pattern), `ACTION_VIEW` + `geo:` handoff, app-built rotary focus (§17) and Compose UI tests under Robolectric (§20). The template-step budget is gone. Domain, provider, ranking and state model are unchanged.
>
> **Revision 4.1:** "Parked" in the engineering sense now means *the UX restrictions don't require distraction optimization* (`DrivingState.distractionOptimizationRequired == false`). The app reads UX restrictions, not the gear; AOSP advises against inferring driving state from them ([AOSP](https://source.android.com/docs/automotive/driver_distraction/consume)). V8 is confirmed from the AAOS developer guide.

> **Current status (2026-10-06):** The POC target is a sideloaded debug build on `AAOS_AOSP_33_userdebug`; production distribution remains undecided and outside this POC. V7 failed its clean re-test after the one bounded Compose fix; ADR-002 keeps Compose for the emulator POC with a product waiver (the focus jump after Back to Discover is a known limitation), and M0 exits under it. The [implementation plan](superpowers/plans/2026-10-06-dn-m0-011-bounded-v7-fix.md) records the attempt, while the [re-test record](adr/0002/v7-retest-2026-10-06/results.md), [verification register](05-discover-nearby-delivery-plan.md#9-verification-register) and [ADR-002](adr/0002-ui-stack-after-v7.md) give the current outcome and gate.

---

## 1. Objective

Implement an AAOS proof of concept that demonstrates:

```text
Vehicle / emulator location
        ↓
Intent/category selection
        ↓
POI discovery (selected provider)
        ↓
Recommendation ranking
        ↓
AAOS presentation (Compose UI)
        ↓
Place selection
        ↓
Navigation handoff
```

The implementation should prove technical feasibility while avoiding unnecessary production complexity.

---

## 2. Engineering Principles

### POC-first

Prefer the smallest technically credible implementation that proves the product hypothesis.

### AAOS-native

One distraction-optimized activity with a Jetpack Compose UI, reading driving restrictions from the Car API (`CarUxRestrictionsManager`), as NyasaPlayer does.

### Emulator-validatable

Every critical POC requirement must be testable without physical vehicle hardware.

### Provider-neutral

No provider is assumed. POI and routing APIs sit behind repository interfaces. Provider models never leak past a provider mapper.

### Capability-driven ranking

Ranking must work when the provider lacks ratings, review counts, opening state or amenities.

### Avoid premature production architecture

Every addition must answer at least one of these questions:

- Does it help prove the product hypothesis?
- Does it prevent building on an invalid technical assumption?
- Does it make emulator validation repeatable?
- Does it prevent the demo from being misleading?

If the answer to all four is no, defer it.

### Built to grow, not built for scale

The POC should grow into a product without a rewrite. That means putting **seams** in the places growth is likely: cheap boundaries now, not the features behind them. Defer the feature, keep the seam.

| Likely growth | Seam in the POC (cheap now) | Deferred until needed |
| --- | --- | --- |
| Different or second POI provider | `PlacesRepository` + per-provider mapper; provider types never leave the provider package | A second provider implementation |
| Mobile companion, phone↔car handoff | `discovery/`, `model/`, `places/`, `location/` have **no Compose or Car API imports**. Only `ui/` depends on Compose and only `car/` on `android.car`. | Extracting `:core:*` modules and building `:app` (mobile) |
| Personalisation, weather, time of day, EV state | `DiscoveryContext` is the single input to ranking; new signals are new optional fields | The signals themselves |
| Smarter ranking (tuned, remote, or ML) | `RecommendationEngine` interface; weights and radii live in a `CategoryConfig` **data** object, not in code branches | Remote config, ML |
| Along-route, trip planning | `RouteRepository` interface reserved for M5; `Recommendation` already carries `minutesAhead` / `detourMinutes` | Routing implementation |
| Maps | Grid/List/Details now; a map composable can be added to a screen later without touching domain code | Map rendering |
| Localisation | All user-facing text in `strings.xml`; icons as vector drawables | Translations |
| Hilt / multi-module (as in NyasaPlayer) | Constructor injection with a single wiring point (`AppContainer`), so adopting Hilt later is mechanical | Hilt, module split |
| Play distribution (later) | Nothing; the POC is a sideloaded debug build | Flavors (NyasaPlayer's `oem` / `playstore` split is the reference), release signing, quality review, and a Car App Library template UI layer for Play (V8, confirmed) |

**Dependency rule that keeps extraction mechanical:**

```text
ui/  ──►  discovery/  ──►  model/
 │              │
 └──► car/      ├──►  places/   (interface + provider impls)
                ├──►  location/
                └──►  navigation/

Only ui/ imports Compose. Only car/CarDrivingRestrictions imports android.car.
```

When the POC becomes an MVP, the package boundaries become Gradle modules, following NyasaPlayer's `:core:*` + `:automotive` shape. No domain code should need to change.

---

## 3. Proposed Project Structure

One application module, organised as one package per concern. The stub navigation app is a separate, tiny test tool.

```text
app/
│
├── DiscoverApplication                 (creates AppContainer)
├── AppContainer                        (single wiring point; manual constructor injection)
│
├── ui/
│   ├── MainActivity                    (distractionOptimized; setContent)
│   ├── DiscoverNavHost                 (Navigation Compose)
│   ├── theme/                          (Material 3 theme, day and night)
│   ├── components/                     (CategoryTile, PlaceRow, MessageState, …)
│   └── screens/
│       ├── DiscoverScreen              (category grid)
│       ├── RecommendationsScreen       (list / message states)
│       ├── RecommendationsViewModel
│       ├── PlaceDetailsScreen          (details pane / message states)
│       └── PlaceDetailsViewModel
│
├── car/
│   ├── DrivingRestrictions             (interface + DrivingState; no android.car imports)
│   └── CarDrivingRestrictions          (CarUxRestrictionsManager → StateFlow; NyasaPlayer pattern)
│
├── discovery/
│   ├── DiscoverUseCase
│   ├── RecommendationEngine
│   ├── DiscoveryCategory
│   └── DiscoveryContext
│
├── places/
│   ├── PlacesRepository                (interface)
│   ├── fake/
│   │   └── FakePlacesRepository        (M0: all six categories, fixed data per test location)
│   └── <provider>/                     (added after provider ADR)
│       ├── <Provider>PlacesRepository
│       └── <Provider>PlaceMapper
│
├── location/
│   ├── LocationProvider                (interface)
│   └── AndroidLocationProvider
│
├── navigation/
│   ├── NavigationLauncher              (interface)
│   └── IntentNavigationLauncher
│
└── model/
    ├── GeoPoint
    ├── PlaceSummary
    ├── PlaceDetails
    ├── PlaceAttribute
    └── Recommendation

tools/
└── stub-navigation/                    (separate test APK; see §11)

docs/
└── adr/
    └── 0001-poi-provider.md            (output of provider spike; template currently in the docs pack at adr/0001-poi-provider.md)
```

`routing/RouteRepository` is added only at Milestone 5 (Along Route).

Dependencies are wired by manual constructor injection in `AppContainer`. No DI framework.

---

## 4. Core Architecture

```text
┌──────────────────────────────┐
│   Compose screens            │  grid / list / details / message states
└──────────────┬───────────────┘
               │
          ViewModels (per screen)
               │
        DiscoverUseCase
               │
       ┌───────┴────────┐
       │                │
PlacesRepository   RecommendationEngine
       │
 Provider Mapper
       │
 Selected POI Provider
```

The boundary that must hold:

```text
Provider API
    ↓
Provider Mapper
    ↓
Domain PlaceSummary / PlaceDetails
    ↓
Recommendation Engine
    ↓
AAOS UI
```

Provider SDK or response models must not appear in `discovery/`, `model/` or `ui/`.

Dependency direction: `ui/` → `discovery/` → interfaces in `places/`, `location/`, `navigation/`. Provider implementations depend on the domain model, never the reverse.

Stretch route-aware architecture (M5):

```text
Current location + destination preset
       │
       ├──────────────┐
       ▼              ▼
PlacesRepository   RouteRepository
       │              │
       └──────┬───────┘
              ▼
    RecommendationEngine
              ▼
        AAOS Presentation
```

---

## 5. Provider Selection Spike

Runs **in parallel with M0**. It does not block the AAOS skeleton, domain models, recommendation engine, or test fakes.

**Bounds (Revision 3):**

- **Objective:** select a provider that is legally usable in an embedded vehicle app and whose data is good enough to prove Coffee, Family and Scenic.
- **Three candidates, in order:** TomTom, then HERE, then one *named* OSM-backed service or self-host approach (chosen at kickoff). None is approved; licensing must be confirmed contractually (delivery plan §4, V4).
- **Matrix:** Coffee, Family and Scenic × Greystones, Dublin and Galway, the same for every provider. For each query capture place ID, name, coordinates, types, distance, rating, rating count, opening state, parking, toilets, family information, attribution requirement and latency.
- **Time-box:** 3 working days, no extensions. The outcome is one of: Selected → M1; Provisionally selected → M1 only with explicit risk acceptance; No viable provider → stop and rethink the data strategy.
- **Owner:** Android/Tech Lead. Product signs off data quality; a Product/Legal/Business owner signs off licensing.
- If licensing can't be confirmed in time, ADR-001 may be **"Provisionally selected, pending licensing confirmation"** and M1 may proceed. Engineering does not interpret ambiguous licence terms itself.

### Why

Some widely used place APIs restrict use in applications embedded in vehicles. Building the POC on such a provider would validate a path that cannot ship, and would tune ranking to data the product cannot keep.

### Evaluate

| Area | Question |
| --- | --- |
| Licence | Is use in an embedded / in-vehicle application permitted? |
| AAOS rights | Any restrictions on in-car display, or on use alongside other maps? |
| Coverage | POI coverage at Greystones, Dublin city centre, Galway |
| Place types | Can all six categories be mapped? |
| Family data | Playground, zoo, family attraction; toilets/café/parking fields? |
| Scenic data | Viewpoint, scenic spot, waterfall, natural attraction? |
| Quality signals | Ratings? Review counts? Coverage? |
| Opening state | Open-now available? Coverage? |
| Amenities | Parking, toilets, café-on-site? Coverage? |
| Distance / time | Travel time or distance from origin in the search response? |
| Along route | Native along-route search? |
| Routing | Route calculation and via-point duration? |
| Cost / quota | Free tier, per-request cost, rate limits |
| Attribution | Wording, logo, placement requirements |
| Caching | What may be stored, and for how long? Place IDs? |
| API keys | Key model; package/signing restrictions; is a proxy required? |

Candidates may include TomTom, HERE, Foursquare, OpenStreetMap-based data, or another appropriately licensed provider. None is selected without evidence.

### Output

`docs/adr/0001-poi-provider.md`, covering:

- selected provider and why
- licence evidence (links, relevant clauses)
- category → provider type mapping
- field availability table (provided / derivable / unavailable)
- whether travel time is available cheaply (decides distance vs time in M1)
- attribution requirements
- caching/storage limits
- key handling and whether a proxy is needed
- recorded fixture responses for the three test locations

---

## 6. AAOS App Layer

### Responsibilities

- Provide one launcher activity, declared distraction-optimized
- Render Compose screens; handle navigation and Back
- Observe UX restrictions and apply them (list limit; actions allowed only when distraction optimization is not required)
- Request location permission (only when distraction optimization is not required)
- Support touch and rotary focus

### 6.1 Build baseline

| Area | Decision |
| --- | --- |
| Minimum Android SDK | API 29 |
| UI | Jetpack Compose (Compose BOM, Material 3), `activity-compose`, `navigation-compose`, `lifecycle-viewmodel-compose`. Versions pinned to current stable at M0. |
| Car API | `android.car` from the SDK's `optional/android.car.jar`, `compileOnly` (provided by AAOS at runtime), as in NyasaPlayer |
| State | A Jetpack `ViewModel` per content screen, exposing `StateFlow` |
| Testing | JUnit, coroutines-test, Robolectric 4.17, Compose UI test (`ui-test-junit4`) |
| Kotlin / async | Kotlin, Coroutines |
| HTTP / JSON | **REST only:** OkHttp + kotlinx.serialization through our own provider client. No provider SDK in M1–M4 (V5 closed); any SDK need in M5 gets its own ADR. |
| DI | Manual constructor injection; no framework |
| Persistence | None initially |
| Navigation between screens | Navigation Compose |

There is no Car App Library dependency, so there is no Car App API level. `minSdk 29` is the only platform floor.

### Manifest essentials

- `uses-feature android.hardware.type.automotive` (required)
- `MainActivity` with the launcher intent filter and `distractionOptimized` meta-data. Without it, AAOS replaces the app with its own block screen while driving (proven on NyasaPlayer). The declaration is only honest once list limits, restriction-gated Grant, touch targets and rotary focus are in place. It is acceptable for this sideloaded POC only: Play rejects `distractionOptimized` on any activity other than the Car App Library's `CarAppActivity` (V8).
- `ACCESS_FINE_LOCATION` with `ACCESS_COARSE_LOCATION` (Android requires both; users may grant approximate only), `INTERNET`
- No `CarAppService`, `automotive_app_desc.xml` or `minCarApiLevel`

### Screens

| Screen | Layout | Notes |
| --- | --- | --- |
| `DiscoverScreen` | 2 × 3 category grid | Six category tiles |
| `RecommendationsScreen` | List (loading, content); message state (permission, empty, errors) | One destination; state changes recompose it |
| `PlaceDetailsScreen` | Details pane (loading, content) | Details failure falls back to summary data on the same screen |

Stretch: `AlongRouteScreen` (list of destination presets), `StopsAheadScreen` (list).

### Back stack

There is no template-step budget; that was a template-host limit. The rules it produced still hold, for UX reasons:

- Loading, content and error are **states of one screen**, not pushed destinations.
- The core path is at most three destinations deep (Discover → Recommendations → Details); the stretch path is four.
- Back pops one destination. Back on Discover leaves the app.

### Driving content limits

Do not assume five rows always fit while driving. The platform limit reaches the UI through a small interface with no `android.car` types, so Robolectric tests can fake it (`android.car.jar` is `compileOnly` and absent in local tests; NyasaPlayer's `UxFlags` mirror exists for the same reason):

```kotlin
data class DrivingState(
    val distractionOptimizationRequired: Boolean,   // CarUxRestrictions.isRequiresDistractionOptimization()
    val listLimit: Int?,     // maxCumulativeContentItems when UX_RESTRICTIONS_LIMIT_CONTENT is active; null = no limit
)

interface DrivingRestrictions {
    val state: StateFlow<DrivingState>
}
```

`CarDrivingRestrictions` is the only implementation that touches `CarUxRestrictionsManager`.

`DrivingState` reports **UX restrictions, not the gear**. AOSP tells apps to "monitor restrictions exposed by the CarUxRestrictionsManager and not an absolute driving state", and `isRequiresDistractionOptimization()` returning `false` only means "an app can safely run any activity" ([AOSP](https://source.android.com/docs/automotive/driver_distraction/consume)). Mapping driving state to restrictions is the platform's job and varies by market. Never name or treat this field as "parked" in code.

The **ViewModel** applies the limit; the recommendation engine never sees driving state (§10):

```kotlin
val uxLimit = drivingRestrictions.state.value.listLimit ?: Int.MAX_VALUE
val visible = ranked.take(minOf(DESIRED_RECOMMENDATIONS, uxLimit))
```

`DESIRED_RECOMMENDATIONS = 5`. Fewer is fine. The ViewModel re-trims when `state` changes, so a gear change takes effect without a new request.

### State holding

Each content screen has a Jetpack `ViewModel` that:

- runs the use case in `viewModelScope`
- holds the current UI state in a `StateFlow`

The screen collects it with `collectAsStateWithLifecycle()` and recomposes on change. ViewModels are created with `viewModelFactory { initializer { … } }` from `AppContainer`. No DI framework.

---

## 7. Location and Permission

### Location source

Use `LocationManager`: GPS first, then network where the car has one. Location is read only when discovery asks (docs/01 §14). Either location permission is enough, so an approximate-only grant still finds places (DN-M0-006):

- **Precise (fine granted):** a fresh fix, waiting up to 8 s (`LOCATION_TIMEOUT_MILLIS`); failing that, a cached fix at most 2 minutes old.
- **Approximate only:** the platform's cached coarse fix, at most 15 minutes old. A fresh request would not help: the platform turns it into a low-power request that GPS never serves, and it refreshes an approximate-only app's fix only about every 10 minutes.
- **No fix:** `Unavailable`.

The reference image has a Play-services fused provider, but for a single fix it adds nothing, so it isn't used.

```kotlin
sealed interface LocationResult {
    data class Available(val point: GeoPoint) : LocationResult
    data object PermissionMissing : LocationResult
    data object Unavailable : LocationResult
}

interface LocationProvider {
    suspend fun currentLocation(): LocationResult
}
```

### Permission flow

- Permission is requested with `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`, fine and coarse together in one dialog; either grant counts.
- Grant is offered **only when `distractionOptimizationRequired` is false** (in practice, Park on the reference emulator). Templates enforced this with `ParkedOnlyOnClickListener`; in Compose the app enforces it, from `DrivingRestrictions`.
- `RecommendationsScreen` shows `PermissionRequired(canRequest = !distractionOptimizationRequired, denied)` as a message state. `canRequest` updates live when the restrictions change; `denied` switches to the denied copy after the user declines.
- On grant, discovery continues for the selected category. On denial, show the denied message with Grant and Back.
- After a permanent refusal (no rationale for either permission), offer Open Settings instead of Grant, only while
  `distractionOptimizationRequired` is false; return from Settings searches again (DN-UX-001). An empty result
  (a cancelled or overlapping request) is not a refusal. Known limit: on Android 11+, dismissing the first dialog
  without answering also leaves no rationale, so it shows Open Settings where Grant would still work.

### Test locations

Stored in test configuration, not re-typed:

| ID | Location | Lat, Lng |
| --- | --- | --- |
| A | Greystones, Co. Wicklow | 53.1440, -6.0633 |
| B | Dublin city centre | 53.3498, -6.2603 |
| C | Galway city | 53.2707, -9.0568 |

---

## 8. Places / POI Provider

### Interface

```kotlin
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

Errors are thrown as domain exceptions (`ProviderFailure`, `NetworkUnavailable`) by the provider implementation. The use case maps them to UI state.

### Domain model

```kotlin
data class GeoPoint(val lat: Double, val lng: Double)

enum class AttributeType {
    PARKING, TOILETS, CAFE, PLAYGROUND, TRAILS, BEACH,
    VIEWPOINT, MUSEUM, FAMILY_FRIENDLY, DRIVE_THROUGH,
}

enum class AttributeSource {
    PROVIDED,   // provider states it
    DERIVED,    // reliably inferred from place type
}
// "Unavailable" = attribute absent from the set. It is never displayed.

data class PlaceAttribute(
    val type: AttributeType,
    val source: AttributeSource,
)

data class PlaceSummary(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val placeKinds: Set<String>,          // normalised kinds, e.g. "park", "playground"
    val primaryKind: String?,
    val attributes: Set<PlaceAttribute>,
    val rating: Double?,                  // null if provider lacks it
    val ratingCount: Int?,                // null if provider lacks it
    val isOpenNow: Boolean?,              // null = unknown
    val travelTimeMinutes: Int?,          // only if provider supplies it
)

data class PlaceDetails(
    val summary: PlaceSummary,
    val openingSummary: String?,
    val attribution: String?,
)
```

`PlaceSummary` holds only list-level fields. This keeps search requests small and makes the cost of each field visible. `PlaceDetails` holds richer fields fetched only when a place is opened.

### Mapper

Each provider implementation has a mapper from provider responses to domain models. Mapper tests use recorded fixture responses from the provider spike.

---

## 9. Discovery Categories

Product definitions (provider mappings come from the ADR):

| Category | Intent | Target place kinds |
| --- | --- | --- |
| Coffee | Quick practical stop | café, coffee shop |
| Food | Somewhere to eat | restaurant, fast casual, takeaway |
| Outdoors | *Do* something outdoors | park, trail, forest, beach, hiking area, outdoor attraction |
| Family | Somewhere the kids can enjoy | playground, zoo, aquarium, family attraction, park |
| Scenic | *Look at* something nice | viewpoint, scenic spot, coastal overlook, landmark, waterfall, natural attraction |
| Explore | Something interesting | tourist attraction, museum, landmark, heritage site |

Search radius per category is tunable configuration. Illustrative values: Coffee and Food 5 km; Family and Explore 15 km; Outdoors 20 km; Scenic 30 km.

---

## 10. Recommendation Engine

### Goal

Turn provider results into a short list of strong recommendations.

### Interface

```kotlin
interface RecommendationEngine {
    fun rank(
        places: List<PlaceSummary>,
        context: DiscoveryContext,
    ): List<Recommendation>
}

data class Recommendation(
    val place: PlaceSummary,
    val score: Double,
    val distanceMeters: Int,
    val travelTimeMinutes: Int?,   // provider-supplied or calculated
    val minutesAhead: Int?,        // stretch
    val detourMinutes: Int?,       // stretch
)
```

The engine is pure Kotlin with no Android imports, so it runs on the JVM.

### Scoring

```text
score =
    categoryMatch            (primary kind matches intent > secondary kind matches)
  + locationRelevance        (closer is better, normalised to the category's radius)
  + qualitySignal            (only if rating is present; otherwise 0)
  + amenitySignal            (only PROVIDED/DERIVED attributes; otherwise 0)
  + openNowBonus             (only if isOpenNow == true; null = 0)
  - detourPenalty            (stretch only)
```

Rules:

- **Exclude** places known to be closed (`isOpenNow == false` for time-sensitive categories such as Coffee and Food, or permanently closed). No large negative score.
- **Unknown is neutral.** A null rating, open state or missing attribute adds nothing and subtracts nothing.
- **Light diversity.** At most two results with the same primary kind in the visible set (tunable). For example, Outdoors should not return five city parks.
- **Score floor.** Results below a minimum category-match threshold are dropped, not shown as filler.
- **Stable ordering.** Ties break deterministically (for example, by distance, then id).

Illustrative Family weights (tunable, not requirements):

```text
Primary kind playground / zoo / family attraction   +30
Park                                                 +15
PROVIDED or DERIVED toilets                          +10
PROVIDED parking                                     +10
PROVIDED café                                        +5
Rating ≥ 4.5 (if rating present)                     +5
```

Rating aggregation (for example, Bayesian averaging of rating and count) is **not** a POC requirement. Investigate it only if the provider ADR shows that ratings and counts exist with useful coverage.

### Output

Every place that passes the score floor and diversity rule, in ranked order. The engine is pure Kotlin and has no display limit; the ViewModel shows the first `min(5, uxLimit)` (§6). Fewer if fewer pass the score floor. Never padded.

---

## 11. Navigation Handoff

### Mechanism

```kotlin
class IntentNavigationLauncher(private val appContext: Context) : NavigationLauncher {
    override fun navigateTo(point: GeoPoint): Result<Unit> = runCatching {
        val uri = Uri.parse(
            String.format(Locale.US, "geo:%.6f,%.6f", point.lat, point.lng)
        )
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
```

- It takes the **application** `Context`, so `AppContainer` can own it and pass it to `PlaceDetailsViewModel` without holding an `Activity`. Starting from a non-activity context requires `FLAG_ACTIVITY_NEW_TASK`. The app is in the foreground when the user taps Navigate, so background-start limits don't apply.

- The **system** resolves the intent. Discover Nearby does not target Google Maps or any specific app. If several apps handle `geo:`, the system may show a chooser.
- `ActivityNotFoundException`, `SecurityException`, or any other failure maps to `NavigationUnavailable`.
- Car App Library navigation apps listen for `androidx.car.app.action.NAVIGATE`, which only a template app can send. Whether a real navigation app on a given image also accepts `ACTION_VIEW` + `geo:` is an optional check; the POC proves handoff with the stub.
- `Locale.US` formatting prevents comma decimal separators in some locales.

### Stub navigation app (emulator verification)

A minimal separate APK in `tools/stub-navigation/`. Its only jobs are to:

1. register as a navigation handler: one activity with an `ACTION_VIEW` intent filter for the `geo` scheme, declared `distractionOptimized` so it shows while driving
2. receive the navigation request
3. display the received coordinates on a simple Compose screen and log them (`StubNav: received geo:…`)

This makes “correct destination handed off” observable and repeatable without a production navigation app.

It is the Gradle module `:stub-navigation` in the root build (`projectDir = tools/stub-navigation`), so CI builds, lints and tests it with the app; it shares the project's one compile SDK (`android-compileSdk` in the version catalog). Install: `./gradlew :stub-navigation:installDebug`.

Verify in M3 that the system resolves the `geo:` intent to the stub on the chosen image. If another handler exists, record whether a chooser appears.

### Out of scope

- turn-by-turn navigation
- route guidance UI
- traffic handling
- rerouting
- navigation voice guidance

---

## 12. Along-Route Discovery — Stretch (M5)

Only after the core POC is credible.

```text
Current location + destination preset
      ↓
Route calculation (selected routing provider)
      ↓
Route geometry
      ↓
POI search along route (native, if the provider supports it; otherwise sampled points)
      ↓
Minutes ahead / detour calculation
      ↓
Recommendation ranking
```

```kotlin
interface RouteRepository {
    suspend fun route(origin: GeoPoint, destination: GeoPoint): RouteSummary
    suspend fun routeVia(origin: GeoPoint, via: GeoPoint, destination: GeoPoint): RouteSummary
}

data class RouteSummary(
    val durationMinutes: Int,
    val distanceMeters: Long,
    val encodedPolyline: String?,
)
```

### Detour

```text
detour = duration(origin → place → destination) − duration(origin → destination)
```

Calculate it for the top candidates only (for example, the top 5 after the first ranking pass) to limit cost and latency.

Destinations are **fixed presets**. Free-text destination entry is out of scope.

---

## 13. Data Fetching Strategy

- **Recommendations:** request only the fields needed for ranking and list rows (`PlaceSummary`).
- **Place Details:** fetch richer data only when the user opens a place (`PlaceDetails`).
- **Discovery trigger:** location is read and sent only when the user selects a category.

Goals: avoid unnecessary data use, reduce API cost, reduce latency, keep the implementation understandable.

---

## 14. Caching

Production caching is out of scope.

**Default until ADR-001 says otherwise: do not persist provider POI responses.**

```text
in-memory response → display → discard when the session ends
```

- Place identifiers are stored only if the provider's terms allow it.
- Recorded test fixtures are created only after confirming the provider permits storing responses for testing.
- Do not build a local mirror of third-party POI data.

---

## 15. State Model

```kotlin
data class DiscoveryContext(
    val requestId: Long,
    val origin: GeoPoint,
    val category: DiscoveryCategory,
    val createdAtMillis: Long,
)

sealed interface RecommendationsUiState {
    data object Loading : RecommendationsUiState
    data class Content(
        val requestId: Long,
        val recommendations: List<Recommendation>,
    ) : RecommendationsUiState
    data object Empty : RecommendationsUiState
    data object ParkToSee : RecommendationsUiState // the driving list limit allows none (docs/02 §17)
    data class PermissionRequired(
        val canRequest: Boolean,
        val denied: Boolean = false,
        val permanentlyDenied: Boolean = false,
    ) : RecommendationsUiState
    data class Error(val type: DiscoverError) : RecommendationsUiState
}

enum class DiscoverError {
    LocationUnavailable,
    NetworkUnavailable,
    ProviderFailure,
    Timeout,
}

sealed interface PlaceDetailsUiState {
    data class Loading(val summary: PlaceSummary) : PlaceDetailsUiState
    data class Content(val details: PlaceDetails) : PlaceDetailsUiState
    data class SummaryOnly(val summary: PlaceSummary) : PlaceDetailsUiState  // details failed
    data class NavigationUnavailable(val summary: PlaceSummary) : PlaceDetailsUiState  // keeps the header
}
```

### Stale-request protection

Example:

```text
Coffee request
    ↓
user returns
    ↓
Family request
    ↓
Coffee response arrives late  →  dropped
```

The ViewModel:

- assigns each discovery a new `requestId` (with category and origin in `DiscoveryContext`)
- cancels the previous in-flight job when a new request starts
- applies a response only if its `requestId` matches the current one

This also matters when the emulator location changes during a demo.

### Timeout

Each provider call is wrapped in a timeout (configurable; around 8 s as a starting value). On expiry it becomes `Error(Timeout)`. Loading never continues indefinitely.

---

## 16. Failure Handling

The app must not crash, or stay in loading, when:

| Failure | Resulting state |
| --- | --- |
| Permission not granted | `PermissionRequired` |
| Location unavailable | `Error(LocationUnavailable)` |
| Network unavailable | `Error(NetworkUnavailable)` |
| Provider error | `Error(ProviderFailure)` |
| Provider timeout | `Error(Timeout)` |
| No results / all below score floor | `Empty` |
| Fewer than 3 credible results | `Content` with 1–2 items |
| Null-heavy place data | `Content`; rows omit missing fields |
| Place details fail | `SummaryOnly` (Navigate still available) |
| Navigation handoff fails | `NavigationUnavailable` |
| Stale response | Dropped silently |

Every failure state exposes a recovery path (Retry, Grant, or Back).

---

## 17. Rotary and Focus

With a Compose UI, **the app owns rotary support**. On the Android 13 POC image, controller rotation needs app-side support; nudging is not a POC requirement. Engineering's job is to:

- make every actionable element focusable (buttons, clickable rows), with a clearly visible focus indicator
- match the focus order in the UX spec (§16), and put initial focus on the first item of each screen
- after Back, return to a usable screen without losing a turn (V7 gate). Where focus lands is recorded, not gated (ADR-002, 2026-10-06). Rotary focus after Back now returns through `ReturnFocus` (DN-M0-011), re-tested cleanly on 2026-10-06. On Back to Discover it was not always reported to the rotary service (ADR-002 re-test)
- keep the focused row scrolled into view in lists
- **verify** with the emulator rotary control that focus order is sensible, focus is visible, Back restores a sensible target, nothing is unreachable, and there are no traps

How well AAOS rotary drives Compose focus on the reference image is **V7** (delivery plan §9). V7 covers the full Discover → Recommendations → Place Details → Navigate → Back journey, not only the Discover grid. Its gate and current status are in docs/05 §9 and ADR-002: controller rotation on Android 13 (nudging is not a POC requirement). It failed its clean re-test after one bounded Compose fix (2026-10-06); ADR-002 keeps Compose with a product waiver for this iteration.

---

## 18. Park / Drive Simulation

Validate with the emulator's VHAL gear and speed controls (see Test & Demo Plan §2):

- Park
- Drive
- speed changes
- gear change while recommendations are loading

The app observes `CarUxRestrictions` through `DrivingRestrictions` (NyasaPlayer's `CarUxRestrictionsHandler` is the reference) and reacts live: list limit, Grant availability.

The POC does not claim real-world driver-distraction validation.

---

## 19. Privacy, Keys and Attribution

### Privacy and keys

- Provider keys come from `local.properties` (git-ignored) via `BuildConfig`. They are never committed and never logged.
- Use a **dev-only key**: low quota, easy to revoke, no production credentials. A client-side key can always be extracted, so this is not production security.
- If the provider requires a proxy, device authentication, signed tokens or an SDK credential mechanism, ADR-001 records it as additional scope.
- Restrict keys to the app package and signing certificate, if the provider supports it.
- Do not log precise coordinates unnecessarily. Where useful for debugging, round to 3 decimal places.
- Location is sent only when discovery is requested.
- Store only what the provider's terms allow.
- No backend proxy unless the provider or licence requires one (decided in the ADR).

### Attribution

The provider ADR records:

```text
Provider attribution
→ required placement
→ required wording / logo
→ applicable screens
```

The attribution UI is implemented in M1, after the ADR. It is not designed earlier.

---

## 20. Testing Strategy

Three layers:

| Layer | Covers |
| --- | --- |
| Unit tests (JVM) | Ranking, category mapping, provider → domain mapping, stale requests, error mapping |
| Compose UI tests (Robolectric) | Each screen renders each state, loading/content/error transitions, Navigate starts an `ACTION_VIEW` `geo:` intent (Robolectric `shadowOf(application).nextStartedActivity`), Grant hidden while distraction optimization is required and shown when it is not |
| AAOS emulator | Touch, rotary, Back, permission flow, Park/Drive, location changes, full flow, visual sanity |

**Resolved (V2):** Compose UI tests run as **local Robolectric tests** with `createComposeRule`, using NyasaPlayer's setup (its ticket T1, automotive Compose test tooling). M0 includes one smoke test that renders `DiscoverScreen`. Rotary, focus, and Park/Drive on the real platform remain **emulator-tested**.

### Unit tests (JVM)

- category → provider kind mapping (one table-driven test, after ADR-001)
- recommendation ranking against fixture sets: order, closed-place exclusion, unknown-is-neutral, diversity, score floor, stable ties
- provider mapper against recorded fixtures, including null-heavy responses
- error mapping (provider, network, timeout, location → UI state)
- stale-request dropping
- navigation URI formatting (`Locale.US`, 6 dp)
- ViewModel visible count = `min(5, uxLimit)` with and without a restriction list limit, re-trimmed on a state change
- detour calculation (stretch)

### Fakes

- `PlacesRepository` (success, empty, sparse, null-heavy, slow, failing)
- `LocationProvider` (available, permission missing, unavailable)
- `NavigationLauncher` (success, failure)
- `DrivingRestrictions` (no optimization required; optimization required with a list limit), via the interface, never `android.car`
- `RouteRepository` (stretch)

### Emulator checks

- screen transitions and Back
- Back stack on the deepest path
- loading, empty, permission and error states
- rotary verification
- Park/Drive, including gear change while loading
- navigation handoff via stub app
- relevance benchmark (Test & Demo Plan §4)

---

## 21. Delivery Milestones

### Dependencies

```text
                         START
             ┌─────────────┴──────────────┐
             ▼                            ▼
       M0 AAOS Skeleton             Provider Spike
             │                         ADR-001
             └────────────┬───────────────┘
                          ▼
            M1 → M2 → M3 → M4 → GO / ITERATE / STOP → (M5 on GO)
```

M0 and the provider spike have firm dates; M1–M4 are range-estimated and re-estimated after ADR-001 (see delivery plan §8).

### M0 — AAOS Skeleton (starts immediately)

- Build baseline applied (§6.1)
- Compose activity, declared distraction-optimized, running on the reference emulator image in Park and Drive
- `FakePlacesRepository` covering all six categories
- Discover → Recommendations → Place Details → Navigate, end to end on fake data
- screen stack and Back
- rotary smoke test on the Discover grid (V7)
- Park/Drive smoke test
- emulator configuration recorded (Test & Demo Plan §2)
- domain models, recommendation engine on fake data, test fakes
- one Compose UI Robolectric smoke test (verifies project configuration)

### Provider Spike — in parallel with M0

Investigate licence, POI coverage, category coverage, data fields, attribution, cost and routing capability.

Output: **Provider ADR** (`docs/adr/0001-poi-provider.md`) plus recorded fixtures.

### M1 — Nearby Discovery

Starts when ADR-001 is Accepted, or Provisionally selected pending licensing confirmation.

- selected provider replaces `FakePlacesRepository` behind `PlacesRepository`
- emulator location and permission flow
- one category
- live POI data
- Recommendations list (distance, or provider travel time per ADR)
- Place Details
- timeout and error handling
- stale-request protection
- attribution per ADR

### M2 — Category Model

- Coffee, Food, Outdoors, Family, Scenic, Explore
- deterministic recommendation ranking
- relevance benchmark run and recorded; **Product sign-off is the M2 exit condition**

### M3 — Navigation Handoff

- `ACTION_VIEW` + `geo:`
- stub navigation app
- failure state
- destination verification

### M4 — Automotive Validation

- touch
- rotary
- Back
- Park and Drive
- permission flow
- UX restrictions (list limit, restriction-gated Grant)
- failure scenarios
- demo validation

### M5 — Stretch: Along Route

Only after the core POC is credible:

- fixed origin / destination presets
- `RouteRepository` and route calculation
- POIs along route
- journey relevance
- detour
- Stops Ahead

---

## 22. POC Definition of Done

- The AAOS app launches on the reference emulator image.
- A provider ADR exists, and the provider is licensed for in-vehicle use.
- Emulator location drives nearby discovery.
- All six categories exercise the live provider.
- Recommendations are ranked, capped by `min(5, uxLimit)`, and never padded.
- The relevance benchmark has been run and its results recorded.
- Only provided or derived attributes are displayed.
- Place Details works, including summary-only fallback.
- The stub navigation app receives the selected coordinates.
- Navigation failure is handled.
- The permission flow works in Park and in Drive.
- The touch flow works.
- The rotary flow works (app focus handling verified).
- Back is stable on the deepest path.
- Park/Drive states have been exercised, including a gear change while loading.
- Loading, empty, permission, error and timeout states exist; there is no endless loading.
- Demo scenarios in the Test & Demo Plan pass.
- Known emulator-only limitations are documented.

---

## 23. Deferred Work

- maps
- generic voice discovery
- free-text destination entry
- real vehicle testing
- OEM compatibility testing
- production security review
- production observability
- production analytics
- production backend (unless the provider requires one)
- account system
- mobile app
- personalization
- AI recommendations
- rating aggregation models (for example, Bayesian), pending provider data
- offline-first architecture
- production-scale caching
- accessibility certification
- performance tuning on physical head units
