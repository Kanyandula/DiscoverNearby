# Discover Nearby — AAOS POC Delivery Plan

**Status:** Proposed — Revision 4 (for kickoff agreement)  
**Platform:** Android Automotive OS  
**Phase:** Proof of Concept  
**Owners:** Product Lead + Android/Tech Lead  
**Last Updated:** 2026-10-02

> This document resolves the planning blockers identified after Revision 2. **Only live-provider integration (M1) is blocked by the provider decision.** Everything else starts now.
>
> Items marked **⚠ Verify** are assumptions that have not been confirmed against primary documentation or a licence. They are tracked in §9 and must not be treated as settled.
>
> **Revision 4:** The UI is built in Kotlin + Jetpack Compose as a distraction-optimized AAOS activity, replacing Car App Library templates. Decisions 1 and 4, the build and test baselines (§2, §3) and the verification register (§9: V1 closed, V2 restated, V7 and V8 added) change accordingly.
>
> **Revision 4.1:** "Parked" in the engineering sense now means *the UX restrictions don't require distraction optimization* (`DrivingState.distractionOptimizationRequired == false`). The app reads UX restrictions, not the gear; AOSP advises against inferring driving state from them ([AOSP](https://source.android.com/docs/automotive/driver_distraction/consume)). V8 is confirmed from the AAOS developer guide.

---

## 1. Kickoff Decisions

Seven decisions need agreement at kickoff. Once they are agreed, no project-level blocker remains, just one bounded investigation running alongside development.

1. Build the UI in **Kotlin + Jetpack Compose** as one distraction-optimized activity, on Android **API 29+**.
2. Use **fake POI data** for M0 while the provider spike runs.
3. The **Android/Tech Lead** owns the provider spike. **Product** owns data-quality approval. **Licensing** is confirmed by a Product/Legal/Business owner, not by engineering alone.
4. The test baseline is **Compose UI tests (Robolectric) + unit tests + emulator validation**.
5. **Product** owns the recommendation benchmark and the final go/no-go decision.
6. **M0 and the provider spike start in parallel.** Only M1 live integration waits for ADR-001.
7. **REST only, no provider SDK, for M1–M4.** The spike is **3 working days, no extensions**, ending in Selected, Provisionally selected, or No viable provider.

---

## 2. Build Baseline

| Area | Decision |
| --- | --- |
| Minimum Android SDK (automotive app) | API 29 |
| UI | Jetpack Compose (Compose BOM, Material 3), `activity-compose`, `navigation-compose`, `lifecycle-viewmodel-compose` |
| Car API | `android.car` (`optional/android.car.jar`, `compileOnly`) for `CarUxRestrictionsManager`, as in NyasaPlayer |
| Testing | JUnit + coroutines-test + Robolectric + Compose UI test (`ui-test-junit4`). Copy NyasaPlayer's proven setup: Robolectric 4.14.x and `testOptions.unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true }` |
| Emulator | `AAOS_AOSP_33_userdebug` AVD, as used for NyasaPlayer (Test & Demo Plan §2) |
| Distribution | Debug build, sideloaded from Android Studio. No Play submission, Play quality review or release signing in the POC: it needs to work first. |
| Language / async | Kotlin, Coroutines |
| HTTP | OkHttp |
| JSON | kotlinx.serialization |
| Architecture | Single application module, package-per-concern, manual constructor injection |
| DI framework | None |
| Database | None initially |
| Screen navigation | Navigation Compose |
| UI state | A `ViewModel` per content screen, `StateFlow`, created from `AppContainer` |
| Map | None |
| POI | Repository abstraction; `FakePlacesRepository` in M0 |
| Routing | Not until M5 |
| Stub navigation app | Separate tiny APK in `tools/stub-navigation/` |

### Version policy

- Pin Kotlin, AGP and the Compose BOM to current stable releases at M0, and record them here.
- No alpha or RC dependencies unless a concrete blocker appears that stable cannot solve. Any such change needs a short note in this document.
- No Car App Library dependency.

Pinned at M0 (DN-M0-009, DN-M0-012, DN-M0-001; checked 2026-10-02):

| Area | Version |
| --- | --- |
| AGP / built-in Kotlin / Gradle | 9.2.1 / 2.2.10 / 9.4.1 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 29 |
| Compose BOM | 2026.09.00 (ui 1.12.1, material3 1.4.0) |
| activity-compose / navigation-compose / lifecycle | 1.13.0 / 2.10.2 / 2.11.0 |
| core-ktx | 1.19.1 |
| kotlinx.coroutines / kotlinx.serialization | 1.11.0 / 1.11.0 |
| OkHttp | 5.5.0 |
| JUnit / Robolectric (runs at SDK 36) | 4.13.2 / 4.17 |
| detekt | 1.23.8 |

Lint reports newer AGP (9.4.1), Gradle (9.8.0) and Kotlin plugins (2.4.20). These are deferred toolchain upgrades, not M0 work; targetSdk 36 is deliberate.

### HTTP / JSON policy

**REST only for the core POC (M1–M4).** Provider APIs are called directly with OkHttp + kotlinx.serialization through our own client and mapper. No provider SDK: M1–M4 need no map renderer, navigation engine, positioning stack, offline maps, SDK UI or route guidance. If M5 later needs an SDK, it gets its own ADR at that point. This closes V5.

```text
AAOS app → PlacesRepository → provider REST client (OkHttp + kotlinx.serialization) → HTTP
```

### 2a. Provider defaults until ADR-001

**Caching.** Do not persist provider POI responses unless ADR-001 explicitly allows it:

```text
in-memory response → display → discard when the session ends
```

Providers differ. HERE, for example, restricts prefetching, caching or storing Location Services results except where caching headers allow it. ADR-001 records the chosen provider's rule.

**API key.**

```text
API key → local.properties → BuildConfig → never committed to Git
```

Use a dev-only key with low quota that is easy to revoke, and no production credentials. A client-side key can always be extracted, so this is not production security. If the provider requires a backend proxy, device authentication, signed tokens or an SDK credential mechanism, ADR-001 records that as additional scope.

**Attribution.** Record the exact rule in ADR-001 before designing any attribution UI. OSM, for example, requires attribution and ODbL notice.

### Driving restrictions

The app reads `CarUxRestrictions` through the Car API, using NyasaPlayer's `CarUxRestrictionsHandler` as the reference. The activity is declared `distractionOptimized`. `DrivingState` reports UX restrictions (`distractionOptimizationRequired`, `listLimit`), never the gear. That declaration is only honest once list limits, restriction-gated Grant, touch targets and rotary focus are in place.

---

## 3. Test Baseline

Three layers:

```text
┌───────────────────────────────┐
│ AAOS emulator                 │  touch, rotary, Back, permission,
│ (manual / scripted scenarios) │  Park/Drive, location change, full flow
└──────────────▲────────────────┘
               │
┌──────────────┴────────────────┐
│ Compose UI tests              │  screens → states, state transitions,
│ (Robolectric)                 │  navigation intents, gated Grant
└──────────────▲────────────────┘
               │
┌──────────────┴────────────────┐
│ Unit tests (JVM)              │  ranking, category mapping,
│                               │  provider → domain mapping, stale requests
└───────────────────────────────┘
```

**Automated**

- recommendation scoring and ordering
- category mapping
- provider → domain mapping (recorded fixtures, after ADR-001)
- loading / content / error transitions
- each screen renders each state
- navigation intent, checked via Robolectric `nextStartedActivity`
- Grant offered only when distraction optimization is not required
- stale-request handling

**Emulator**

- touch, rotary and Back
- the location permission flow
- Park / Drive
- location changes
- the full user flow
- visual sanity

Compose UI tests run as **local Robolectric tests** (V2, NyasaPlayer's setup). M0 includes one smoke test that renders `DiscoverScreen`, to verify our project configuration.

Robolectric does not replace the emulator. Rotary, focus, and Park/Drive remain **emulator-tested**.

---

## 4. Provider Spike

### Objective

> Select a POI provider that is legally usable in an embedded vehicle application and whose data is good enough to prove Coffee, Family and Scenic.

### Constraints

- **Time-boxed: 3 working days.** No extensions.
- **Three candidates, investigated in this order:**
  1. **TomTom.** Strongest technical fit: its Search API offers category/POI search and Along Route Search (relevant to M5). TomTom's newer Places Search API does not currently offer along-route search, and the older Search API remains supported where that is needed. Being an automotive company does **not** by itself prove that the specific plan we sign up for permits this AAOS use; that needs contractual confirmation.
  2. **HERE.** Its general Platform Terms permit integrating its APIs into applications, subject to the specific subscription plan and product terms. Plausible, but licensing confirmation is still required.
  3. **One named OSM-backed option**, chosen at kickoff: a specific hosted OSM-based service, or a self-hosted approach. "OSM-based" alone is not a provider decision. OSM *data* is reusable under ODbL with attribution; the question is the *service layer*. Public OSM services such as Nominatim have strict usage limits and are not a basis for the app. OSM is a useful control, but likely weaker on ratings and other quality signals.

  These are signals from public documentation reviewed during planning, not approvals.
- **Not a research project.** Stop when one provider clearly qualifies or none does.

### Test matrix (identical for every provider)

| | Greystones | Dublin | Galway |
| --- | --- | --- | --- |
| Coffee | Test | Test | Test |
| Family | Test | Test | Test |
| Scenic | Test | Test | Test |

For every query, capture:

```text
Place ID
Name
Coordinates
Place types
Distance
Rating?
Rating count?
Opening state?
Parking?
Toilets?
Family-related information?
Attribution requirement
Latency
```

Outputs go into ADR-001 (`adr/0001-poi-provider.md`). Recorded test fixtures are created only after confirming the chosen provider's terms allow storing responses for testing.

### Day-3 outcome (no extensions)

At the end of the time-box, exactly one of these is recorded in ADR-001:

| Outcome | Meaning | Next step |
| --- | --- | --- |
| **Selected** | Data fit and licence confirmed | M1 |
| **Provisionally selected** | Technical/data fit confirmed; licensing clarification pending | M1 only with explicit risk acceptance by the Product Lead |
| **No viable provider** | No candidate is both usable and good enough | Stop / rethink data strategy |

### One-sheet comparison

| Question | TomTom | HERE | OSM option |
| --- | --- | --- | --- |
| AAOS / embedded vehicle use permitted? | | | |
| Evidence / contractual source | | | |
| REST POI search | | | |
| Nearby / category search | | | |
| Along-route capability | | | |
| Coffee coverage | | | |
| Family coverage | | | |
| Scenic coverage | | | |
| Rating / count | | | |
| Opening hours | | | |
| Useful amenities | | | |
| Attribution | | | |
| Caching restrictions | | | |
| Authentication model | | | |
| POC cost | | | |
| Production path plausible | | | |


### Sign-off

| Question | Signs off |
| --- | --- |
| Is the data good enough to prove Coffee, Family and Scenic? | Product Lead |
| Is the licence suitable for embedded automotive use? | Product / Legal / Business owner |
| Is the API technically workable? | Android/Tech Lead |

If licensing cannot be formally confirmed within the time-box, ADR-001 may be recorded as:

> **Status: Provisionally selected, pending licensing confirmation**

M1 may begin against a provisionally selected provider. **Engineering must not interpret ambiguous licence language itself.** If confirmation later fails, the repository boundary limits the rework to the provider package and fixtures.

---

## 5. Ownership

Roles first; names are filled in at kickoff. One person may hold several roles.

| Responsibility | Role | Name |
| --- | --- | --- |
| Product scope | Product Lead | [NAME] |
| Provider technical investigation | Android/Tech Lead | [NAME] |
| Provider licensing confirmation | Product / Legal / Business owner | [NAME] |
| Data-quality assessment | Product Lead | [NAME] |
| M0 AAOS skeleton | Android Engineer | [NAME] |
| M1 provider integration | Android Engineer | [NAME] |
| Recommendation engine | Android Engineer | [NAME] |
| Relevance benchmark | Product Lead | [NAME] |
| Emulator validation | Android Engineer | [NAME] |
| Go / no-go decision | Product Lead | [NAME] |

Product Lead, benchmark reviewer and go/no-go owner may be the same person. **Keep licence confirmation separate from engineering wherever possible.**

---

## 6. Relevance Benchmark Ownership

- **Engineering** produces recommendations consistently and records them for each cell.
- **Product** decides whether they demonstrate the product proposition.

Recording format, per cell:

```text
Greystones × Family

1. Harbour Playground       ACCEPT
2. Family restaurant        ACCEPT
3. Petrol station           REJECT

Top result relevant?       YES
2 of top 3 useful?         YES
Obviously wrong #1?        NO
Notes:                     sparse amenity data
```

Rules are as defined in the Product Brief §11 and Test & Demo Plan §4. Product's sign-off is the **exit condition for M2**.

---

## 7. Go / Iterate / Stop

Decided by the Product Lead at the end of M4.

| Outcome | Conditions |
| --- | --- |
| **GO** — continue to M5 / real-hardware exploration | AAOS flow works **and** the provider is legally viable **and** recommendations are reasonably useful **and** the interaction feels appropriate in the emulator **and** no architectural blocker was found |
| **ITERATE** — tune categories, provider or ranking | AAOS flow works **but** recommendation quality is weak |
| **STOP** | No commercially usable data source, **or** AAOS constraints prevent the intended interaction, **or** recommendations don't provide meaningful value |

GO also names the distribution path (V8): Play, OEM/preinstall, or a template UI layer for Play. An unresolved V8 does not block GO, but it must be settled before any production planning.

---

## 8. Timeline and Dependencies

### Confidence levels

| Work | Estimate type | Notes |
| --- | --- | --- |
| M0 AAOS skeleton | **Firm date** | Set at kickoff: [DATE] |
| Provider spike → ADR-001 | **Firm date** | Time-box ends: [DATE] |
| M1 Live POI discovery | Range | Re-estimate after ADR-001 |
| M2 Categories + ranking + benchmark | Range | Re-estimate after ADR-001 |
| M3 Navigation handoff | Range | Largely provider-independent |
| M4 Automotive validation | Range | Largely provider-independent |
| M5 Along Route (stretch) | Not estimated | Only after GO |

No dates are fixed in this document; they are set at kickoff.

### Dependency graph

```text
                         START
                           │
             ┌─────────────┴──────────────┐
             ▼                            ▼
       M0 AAOS Skeleton             Provider Spike
   (FakePlacesRepository,                 │
    ranking on fake data,              ADR-001
    stub navigation app)                  │
             └────────────┬───────────────┘
                          ▼
                         M1  Live POI Discovery
                          ▼
                         M2  Categories + Ranking + Benchmark
                          ▼
                         M3  Navigation Handoff
                          ▼
                         M4  Automotive Validation
                          ▼
                 GO / ITERATE / STOP
                          ▼  (optional, on GO)
                         M5  Along Route
```

Work that does **not** wait for ADR-001: the Compose screens, domain models, the recommendation engine (against fake data), test fakes, the stub navigation app, permission flow, failure states, and emulator configuration.

---

## 9. Verification Register

**V4 is the only decision that blocks a milestone after M0.** V6a–c are facts collected while making it. V7 is a technical check inside M0. V8 is a confirmed constraint; it does not block the POC, but feeds the go/no-go.

Status key: 🔴 open · 🟡 captured by ADR-001 · 🟢 closed / resolved.

| ID | Decision / item | Status | Owner | Blocks |
| --- | --- | --- | --- | --- |
| **V4** | **The provider gate:** which provider/service combination is commercially and legally usable for an embedded AAOS application? Raw OSM data and an OSM-backed hosted service are different things; the question is about the combination we would actually use. | 🔴 **Open** — provider spike (§4) | Product/Legal/Business owner (licence); Tech Lead (technical) | **M1** |
| V5 | Provider SDK licence | 🟢 **Closed.** The core POC uses REST only, with no provider SDK (§2). Any SDK need in M5 gets its own ADR. | — | Nothing |
| V6a | Provider attribution requirements | 🟡 Captured by ADR-001 | Tech Lead | M1 completion |
| V6b | Provider caching/storage rules | 🟡 Captured by ADR-001. Until then: no persistence (§2a). | Tech Lead | M1 completion |
| V6c | Provider API-key/auth model | 🟡 Captured by ADR-001. Until then: dev-only key (§2a). | Tech Lead | M1 integration |
| V7 | Rotary focus in Compose on the reference AAOS image: does the rotary controller move focus through Compose elements in order, with visible focus and sensible restoration after Back? | 🔴 **Not workable as built** (DN-M0-011, 2026-10-05). On a fresh Discover grid rotation follows the UX order, an app-drawn ring shows focus, and select opens the category. After in-app navigation the rotary service can be left unable to move focus: on Place Details, rotation was stuck and Navigate was unreachable, and after Back twice, rotation stopped on Discover. Cause, from the RotaryController log: on each navigation the ComposeView host takes View focus again, and the service treats the host as the focused node and rotates through the previous screen's stale virtual nodes. Returning focus to the originating tile made rotation stop after Back, so it was dropped. Four app-side workarounds, time-boxed, behaved differently from run to run. Escalated to the Product Lead: [ADR-002](adr/0002-ui-stack-after-v7.md). M0 smoke (DN-M0-007): the full rotary journey completed in 2 of 3 runs (Navigate reached; after Back focus sat on the host until the next turn), and in the third two turns on Recommendations did nothing. Still unreliable. | Product Lead (ADR-002) | M0 exit; GO ("AAOS flow works") |
| V8 | Distribution path for a Compose POI app. **Confirmed (2026-10-02):** the AAOS developer guide allows `distractionOptimized` only on the Car App Library's `CarAppActivity`: "No other activities should be marked as distraction optimized - if one is, your app will be rejected when submitted to the Google Play Store." ([AAOS guide](https://developer.android.com/training/cars/platforms/automotive-os)). A Compose POI app therefore ships through an OEM/preinstall route, or needs a Car App Library template UI for Play. | 🟡 Constraint confirmed; route still to choose. Not blocking the POC | Product Lead | Go / Iterate / Stop |
| V1 | `minCarApiLevel = 4` supports AAOS | 🟢 Closed (Rev 4). No Car App Library, so no Car App API level. | — | Nothing |
| V2 | How automated UI tests run | 🟢 Resolved (restated in Rev 4). Compose UI tests under Robolectric, as on NyasaPlayer; one M0 smoke test checks project configuration; rotary and Park/Drive stay emulator-tested. | — | Nothing |
| V3 | Emulator image and driving-state tooling | 🟢 Resolved. NyasaPlayer's `AAOS_AOSP_33_userdebug` AVD and adb driving-state commands (Test & Demo Plan §2). | — | Nothing |

**V3 detail (from NyasaPlayer `docs/AAOS_DRIVING_STATE_TESTING.md`):**

- The `userdebug` AVD honours `distractionOptimized` for sideloaded debug builds, and accepts adb gear/speed commands, including continuous speed events for a sustained "moving" state.
- The Play image refuses those commands and blocks debug-signed apps while driving, so the POC doesn't use it.
- NyasaPlayer's car UI is a custom Compose activity, the same approach as Discover Nearby (Rev 4), so its driving-state findings apply directly.

The Rev 3 facts about Car App Library 1.7.0, `TestCarContext` and Car App API `LEVEL_4` are kept in `archive/rev3/`. They no longer apply.

Provider signals used to order the spike (from public documentation reviewed during planning; not approvals):

- [TomTom: choosing a Search API](https://docs.tomtom.com/search-api/documentation/product-information/choosing-a-search-api)
- [HERE Platform Terms](https://legal.here.com/us-en/terms/here-platform-terms)
- [HERE API keys](https://docs.here.com/identity-and-access-management/docs/plat-using-apikeys)
- [OSMF Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/)
- [OSM attribution](https://wiki.openstreetmap.org/wiki/Attribution)
