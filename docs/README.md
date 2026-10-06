# Discover Nearby — AAOS POC Documentation Pack

**Baseline revision:** 4.1 (2026-10-02) · **Current status checked:** 2026-10-06

This folder contains the product and engineering documentation for the Discover Nearby Android Automotive OS proof of concept.

The pack is deliberately scoped for an **emulator-first proof of concept**, not a production AAOS release.

## Current Status (2026-10-06)

- **POC target:** a sideloaded debug build on the `AAOS_AOSP_33_userdebug` emulator.
- **Production distribution:** undecided and outside this POC. Product must choose a supported route before
  production planning; any OEM-preinstall route needs OEM confirmation.
- **UI stack:** ADR-002 (Product Lead, 2026-10-06, after the V7 re-test) keeps **Compose for the emulator POC,
  with a product waiver**. Production distribution and OEM validation stay open.
- **V7 (after the re-test, 2026-10-06):** **failed under its original criteria, and waived by Product for this
  iteration**; M0 exits under the waiver. The one bounded Compose fix removed the
  lost turn after Back to Recommendations and made focus visible on every control. But in one of four clean runs,
  Back to Discover left the rotary service on the host, so the next turn jumped to Coffee. See
  [ADR-002](adr/0002-ui-stack-after-v7.md#v7-re-test-after-the-bounded-fix-dn-m0-011-2026-10-06).
- **Before the V7 re-test (historical):** Product allowed one bounded Compose fix after E1. E1's pre-registered
  classification was inconclusive, though its pattern strongly implicated launch-time `uiautomator` polling
  (no-poll 20/20; launch-poll failures 14/19; mid-journey dump 9/9). It did not establish the cause or pass V7.
  The bounded fix and clean re-test are complete; the re-test failed, as recorded above. See the
  [implementation plan](superpowers/plans/2026-10-06-dn-m0-011-bounded-v7-fix.md),
  [verification register](05-discover-nearby-delivery-plan.md#9-verification-register) and
  [ADR-002](adr/0002-ui-stack-after-v7.md).
- **Current gates:** M0 has exited under the waiver (V7 failed and waived). The provider decision V4 blocks M1;
  its product evaluation owner is assigned, with Legal sign-off on terms separate. V8 does not block the POC.

The verification register and ADRs are the status sources. Older implementation plans and revision summaries
record what was true when those tasks ran; they are historical evidence, not current status.

## Documents

1. **01-discover-nearby-poc-brief.md**  
   Problem, hypothesis, scope, principles, provider-selection approach, relevance benchmark, acceptance criteria and limitations.

2. **02-discover-nearby-ux-interaction-spec.md**  
   Screens and their layouts, flows, permission/loading/empty/error states, touch, rotary, and Park/Drive behaviour.

3. **03-discover-nearby-engineering-implementation-plan.md**  
   Architecture, provider spike, domain model, Compose UI layer and driving restrictions, recommendation engine, navigation handoff, state model, milestones and Definition of Done.

4. **04-discover-nearby-test-demo-plan.md**  
   Emulator configuration, relevance benchmark, demo and failure scenarios, checklist, demo script and exit criteria.

5. **05-discover-nearby-delivery-plan.md**  
   Kickoff decisions, build baseline, test baseline, provider spike bounds, ownership, go/iterate/stop, timeline and the verification register.

6. **adr/0001-poi-provider.md** — ADR-001 provider comparison and open licensing gate.

7. **adr/0002-ui-stack-after-v7.md** and **adr/0002/** — ADR-002 and its rotary evidence, screenshots, run record, and retained harness scripts.

`archive/rev1/`, `archive/rev2/` and `archive/rev3/` hold earlier revisions for reference.

## Recommended Order

```text
Product Brief
    ↓
UX & Interaction Spec
    ↓
Engineering Implementation Plan
    ↓
Test & Demo Plan
    ↓
Delivery Plan
```

---

## Revision 4.1 Change Summary

Two corrections to Revision 4; no scope change.

- **UX restrictions, not driving state.** `DrivingState.isParked` is renamed `distractionOptimizationRequired` and maps directly to `CarUxRestrictions.isRequiresDistractionOptimization()`. Grant is offered when it is `false`. AOSP tells apps to monitor UX restrictions "and not an absolute driving state" ([AOSP](https://source.android.com/docs/automotive/driver_distraction/consume)); `false` means "an app can safely run any activity", not that the car is in Park. UX copy still says "park" because that is the driver's action. Changed: 02 §10, §17; 03 §6, §7, §20, M4; 04 §11; 05 §2, §3.
- **V8 confirmed.** The AAOS developer guide allows `distractionOptimized` only on `CarAppActivity`: "No other activities should be marked as distraction optimized - if one is, your app will be rejected when submitted to the Google Play Store." ([AAOS guide](https://developer.android.com/training/cars/platforms/automotive-os)). Changed: 03 §2, §6; 05 §2, §9. The production route remains a Product decision.

---

## Revision 4 Change Summary

Revision 4 changes one decision: **the UI is built in Kotlin + Jetpack Compose**, not with Car App Library templates. Product scope, flows, states, the provider spike, the domain model, ranking and the relevance benchmark are unchanged.

### What changes

- **UI:** one distraction-optimized `MainActivity` with Compose (Material 3), Navigation Compose and a `ViewModel` per content screen. `AppContainer` replaces `DiscoverSession` as the single wiring point. This is the same approach NyasaPlayer takes on AAOS.
- **The app now owns what the template host used to do:** layout, touch targets, rotary focus, and driving restrictions. Driving restrictions come from `CarUxRestrictionsManager` (NyasaPlayer's `CarUxRestrictionsHandler` pattern): the list limit while driving, and Grant offered only when distraction optimization is not required (Revision 4.1 wording).
- **Removed:** Car App Library 1.7.0, `minCarApiLevel`, the template-step budget, `ScreenManager`, `app-testing`/`TestCarContext`.
- **Navigation handoff:** `ACTION_VIEW` + `geo:` from the activity; the stub navigation app becomes a plain `geo:` handler.
- **Tests:** Compose UI tests under Robolectric replace `app-testing` (NyasaPlayer's setup).
- **Design canvas:** now the visual spec, not just an illustration of content.

### Verification register changes

| ID | Item |
| --- | --- |
| V1 | 🟢 **Closed:** no Car App Library, so no Car App API level. |
| V2 | 🟢 **Restated:** automated UI tests are Compose UI tests under Robolectric. |
| V7 | At Revision 4 adoption: new, open. Current disposition: see docs/05 §9. |
| V8 | At Revision 4 adoption: constraint confirmed, route open. Current target and production scope: see Current Status above and docs/05 §2/§9. |

At the time Revision 4 was adopted, V4 was the only decision identified as blocking a post-M0 milestone. Current gates are in docs/05 §9.

*The Revision 3 summary below is kept as history. Its Car App Library baseline (1.7.0, `minCarApiLevel = 4`, `app-testing`) is superseded by Revision 4.*

---

## Revision 3 Change Summary

Revision 3 resolves the planning blockers left after Revision 2. **Only live-provider integration (M1) waits for the provider decision. Everything else starts now.**

### Changes

- **Build baseline locked:** Car App Library 1.7.0 (stable), Android API 29+, `minCarApiLevel = 4`, OkHttp + kotlinx.serialization, single module, no DI framework.
- **Test baseline:** unit tests + `androidx.car.app:app-testing:1.7.0` + emulator scenarios. Navigation intents and permission requests are now checked automatically.
- **M0 uses `FakePlacesRepository`**, so the full flow is buildable before ADR-001.
- **Provider spike bounded:** 2–3 providers, a Coffee/Family/Scenic × 3-location matrix, a time-box, and an ADR-001 template. ADR-001 may be *provisionally* selected pending licensing confirmation.
- **Ownership by role:** Product owns data quality, the benchmark (M2 exit) and go/no-go. Licensing is confirmed by a Product/Legal/Business owner, not engineering alone.
- **Go / Iterate / Stop** criteria defined for the end of M4.
- **Timeline:** firm dates for M0 and the spike; ranges for M1–M4, re-estimated after ADR-001.
- **Emulator:** reuses NyasaPlayer's proven `AAOS_AOSP_33_userdebug` AVD and adb driving-state commands, replacing Revision 2's API 35 Google APIs image recommendation.
- **Distribution:** debug build sideloaded from Android Studio. No Play submission, Play quality review or Play image in the POC.
- **Built to grow:** the engineering plan defines growth seams (provider boundary, Car-free domain packages, `DiscoveryContext`, data-driven `CategoryConfig`, single wiring point) so the POC can become an MVP without a rewrite.
- **Design references:** the UX spec (§22) links the design canvas, overview diagram and icon set.
- **One provider gate:** V4 is the only real decision before M1. V5 is closed (REST only), and V6 is split into ADR-001 fields (V6a attribution, V6b caching, V6c auth). The spike has a fixed order, a one-sheet comparison and three possible outcomes.

### Items flagged for verification, not silently adopted

See the verification register in `05-discover-nearby-delivery-plan.md` §9.

| ID | Item |
| --- | --- |
| V1 | **Resolved:** Car App API `LEVEL_4` "Includes AAOS support" (current `CarAppApiLevels` reference); `minCarApiLevel = 4` confirmed. |
| V2 | **Resolved:** `app-testing` is used for local Robolectric-based tests; one M0 smoke test checks project configuration; host, rotary and Park/Drive stay emulator-tested. |
| V3 | **Resolved — NyasaPlayer setup reused:** `AAOS_AOSP_33_userdebug` AVD with proven adb driving-state commands (see NyasaPlayer `docs/AAOS_DRIVING_STATE_TESTING.md`). This **replaces** the Revision 2 recommendation of an API 35 Google APIs image. Template-host behaviour (step budget, stub-navigation routing) remains a normal test (Scenarios T, E). |
| V4 | 🔴 **Open — the one provider gate:** which provider/service combination is commercially and legally usable for an embedded AAOS app? Spike order: TomTom → HERE → one named OSM-backed option. 3 days, no extensions. Blocks M1 only. |
| V5 | 🟢 **Closed:** core POC uses REST only (OkHttp + kotlinx.serialization), no provider SDK. Any M5 SDK need gets its own ADR. |
| V6a–c | 🟡 **Captured by ADR-001**, not separate decisions: attribution, caching/storage (default: no persistence), API-key/auth model (default: dev-only key). |

The UX & Interaction Spec (02) changes only by adding design references (§22).

*The Revision 2 summary below is kept as history. Its emulator recommendation (a Google APIs image) is superseded by V3.*

---

## Revision 2 Change Summary

Revision 2 does not make the POC more sophisticated. It makes it more technically accurate and more credible while keeping it small. Every addition answers at least one of these questions:

- Does it help prove the product hypothesis?
- Does it prevent building on an invalid technical assumption?
- Does it make emulator validation repeatable?
- Does it prevent the demo from being misleading?

### Major changes

- **No assumed provider.** `GooglePlacesRepository` and `GoogleRouteRepository` are removed. A provider-selection spike, run in parallel with M0, evaluates licensing for in-vehicle use, coverage, data fields, attribution, cost and caching, and produces an ADR. Some widely used place APIs restrict use in vehicle-embedded apps, so the provider has to be chosen on evidence.
- **Templates named; no map.** Discover = `GridTemplate`, Recommendations = `ListTemplate`, Place Details = `PaneTemplate`, permission/errors = `MessageTemplate`.
- **Relevance benchmark** (3 locations × 6 categories) with product-oriented rules, so the POC shows recommendations are useful, not just returned.
- **"Up to 3–5", never padded.** Sparse results are valid and recorded as learning.
- **Attribute provenance** (`PROVIDED` / `DERIVED`; unknown is not shown).
- **Permission Required state**, including first launch while driving.
- **Explicit navigation handoff** via `ACTION_NAVIGATE` + `geo:`, verified with a stub navigation app.
- **Reproducible emulator configuration** on a Google APIs image.
- **Milestones restructured:** M0 skeleton, provider spike in parallel, then M1–M5.

### Review recommendations accepted

- Remove Google Places / Routes as the assumed provider; add a spike and ADR.
- Name the templates; defer maps.
- Specify `ACTION_NAVIGATE` handoff, map handoff exceptions to `NavigationUnavailable`, and add a stub navigation app.
- Use a Google APIs emulator image for Park/Drive testing, and remove dependence on a commercial navigation app.
- Request permission only while parked; add a Permission Required state.
- Document the template-step budget; make Loading/Error states of the same screen.
- Read host list limits at runtime: `min(desired, hostLimit)`.
- Show distance in M1 unless the provider supplies travel time; no promised drive-times.
- Stale-request protection (request id, cancel, drop).
- Provider timeout; no endless loading.
- Split `PlaceSummary` / `PlaceDetails`.
- Keep the single module, manual constructor injection, a presenter per screen, and `RouteRepository` deferred to M5.
- Lightweight privacy and key-handling rules; no proxy unless required.
- Record provider attribution requirements in the ADR before designing attribution UI.
- Define Outdoors (do something) vs Scenic (look at something).
- Use "Recommendations" consistently.
- Treat rotary focus as host behaviour to verify, not app-built behaviour.
- Exclude known-closed places; treat unknown data as neutral; add a light diversity rule.
- Add the missing scenarios: first launch in Drive, timeout, stale response, rapid switching, null-heavy data, sparse results, details failure, handoff error, deepest path, gear change while loading.

### Review recommendations modified

- **Relevance benchmark:** kept the 3 × 6 grid, but replaced the numeric threshold with product rules (relevant #1; 2 of the top 3 useful where data allows; no wrong-category #1; sparse is acceptable).
- **Performance:** kept indicative targets and a timeout, but as recorded observations, not acceptance gates.
- **Ranking:** kept deterministic scoring, but made every quality, amenity and open-state signal optional and capability-driven.
- **Real data bar:** all six categories exercise the live provider, but a sparse location/category cell is a finding, not a POC failure.
- **Demo:** added a sparse-data step to the script to prevent overselling; did not add a separate "three searches" comparison demo.
- **Details failure:** handled in the same `PaneTemplate` with summary data and Navigate, to avoid a template-type change.

### Review recommendations not adopted

- **Treating `PlaceListMapTemplate` as deprecated.** Current AndroidX release notes deprecate `MapTemplate`, `PlaceListNavigationTemplate` and `RoutePreviewNavigationTemplate`, not `PlaceListMapTemplate`. The POI guide still documents both host-rendered (`PlaceListMapTemplate`) and app-rendered (`MapWithContentTemplate`) maps. Maps are deferred because they don't help prove the hypothesis and add complexity, not because the template is obsolete.
- **Requiring Bayesian rating aggregation.** It is premature before the provider ADR shows whether ratings and counts exist and how complete they are. It is listed as a possible later investigation.
- **The "≥ 14 of 18 cells × 3 of 3" relevance threshold.** It was arbitrary and gave false precision for a small, human-judged benchmark. It is replaced by the product rules above.
- **Requiring 3–5 results in every category.** Padding with weak results contradicts the reduce-choice principle. Showing 1–2 strong results is preferred.
- **Making exact emulator latency figures (p50/p90) hard gates.** Debug builds on an emulator are not representative. The POC records timings and requires only no infinite loading, a timeout, and no stale overwrite.
- **Implying POI apps have no voice capabilities.** POI apps can be voice-enabled for specific use cases through App Actions for Cars. Generic conversational discovery is deferred as a scope decision, not because the platform lacks voice support.
