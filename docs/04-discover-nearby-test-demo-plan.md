# Discover Nearby — AAOS Test & Demo Plan

**Status:** Draft — Revision 4  
**Platform:** Android Automotive OS  
**Phase:** Proof of Concept  
**Owners:** Engineering + Product  
**Primary Test Environment:** AAOS Emulator  
**Last Updated:** 2026-10-02

> **Revision 2:** This revision defines a reproducible emulator configuration and verifies navigation handoff with a stub navigation app. It adds a relevance benchmark, plus permission, timeout, stale-response, sparse-data, details-failure and deepest-path scenarios. Performance is now recorded as observations rather than used as a gate.
>
> **Revision 3:** This revision pins Car App Library 1.7.0 and adds the `app-testing` automated layer (§11). Product owns the relevance benchmark, and its sign-off is the M2 exit condition (§4). Core-flow scenarios can run on `FakePlacesRepository` from M0.
>
> **Revision 4:** The UI is built in Kotlin + Jetpack Compose as a distraction-optimized AAOS activity, replacing Car App Library templates. Automated UI tests are Compose UI tests under Robolectric (§11). The template-host checks are gone; focus is now app-built and verified as such (Scenario F, V7).
>
> **Revision 4.1:** "Parked" in the engineering sense now means *the UX restrictions don't require distraction optimization* (`DrivingState.distractionOptimizationRequired == false`). The app reads UX restrictions, not the gear; AOSP advises against inferring driving state from them ([AOSP](https://source.android.com/docs/automotive/driver_distraction/consume)). V8 is confirmed from the AAOS developer guide. Scenario names keep Park/Drive: the tests drive the gear and observe the restrictions it produces.

---

## 1. Purpose

This document defines how the Discover Nearby proof of concept is validated and demonstrated using the Android Automotive OS emulator.

The goal is not production qualification.

The goal is to prove:

- the product concept, including that recommendations are useful
- the core automotive interaction
- location-aware discovery
- recommendation behaviour
- navigation handoff
- basic AAOS interaction quality

---

## 2. Emulator Configuration

All scenarios run on one recorded configuration so results are repeatable. Fill in the **Recorded** column at M0 and update it whenever the image changes.

| Item | Planned | Recorded (M0) |
| --- | --- | --- |
| AVD | `AAOS_AOSP_33_userdebug` — the same AVD NyasaPlayer uses | `AAOS_AOSP_33_userdebug` (checked 2026-10-05) |
| Emulator image | `system-images;android-33;android-automotive;<abi>` (a `userdebug` build, no Play Store) | `system-images;android-33;android-automotive;x86_64`, revision 5, `userdebug` (`sdk_gcar_x86_64`, TEA1.250515.001). **It ships Play services (GmsCoreAuto 24.26.32) and the Play Store**, unlike planned; the `userdebug` build is what matters ("Why this AVD" below). |
| API level | 33 | 33 |
| ABI | `x86_64` on Intel (as on the NyasaPlayer machine); `arm64-v8a` on Apple Silicon | `x86_64` (Intel Mac) |
| Hardware profile | `automotive_1024p_landscape` | `automotive_1024p_landscape` |
| Android Studio version | Current stable | Android Studio Quail 2026.1 (AI-261.23567.138.2611.15483818); emulator 36.4.9.0 |
| Compose BOM version | Pinned at M0 | `2026.09.00` |
| `geo:` handler installed by default? | Record it | No. With the stub installed, `query-activities` (users 0 and 10) finds only the stub |
| Stub navigation app | `tools/stub-navigation` APK installed | Installed with `./gradlew :stub-navigation:installDebug` (DN-M0-008) |

**Why this AVD (proven on NyasaPlayer):** NyasaPlayer's `docs/AAOS_DRIVING_STATE_TESTING.md` established that:

- **The `userdebug` AOSP automotive image** honours `distractionOptimized` for a debug-signed, sideloaded app, and accepts driving-state commands over adb.
- **The Play image** (`user` build) refuses every `car_service` injection command (`requires non-user build`). It also ignores `distractionOptimized` for debug-signed third-party apps and shows "You can't use this feature while driving". A block screen there is expected and is not a regression.

The POC does not use the Play image. Discover Nearby is a debug build sideloaded from Android Studio; Play distribution is out of scope.

Create it once (Android Studio's Device Manager works too; this is NyasaPlayer's exact setup):

```bash
sdkmanager "system-images;android-33;android-automotive;x86_64"
avdmanager create avd -n AAOS_AOSP_33_userdebug \
  -k "system-images;android-33;android-automotive;x86_64" \
  -d automotive_1024p_landscape
$ANDROID_HOME/emulator/emulator -avd AAOS_AOSP_33_userdebug -no-snapshot-load &
```

Use `cmdline-tools/latest`, not the legacy `tools/bin/sdkmanager`. Always pass `-s emulator-5554` to adb if another device may be attached.

**Tooling:** Android Studio's standard AAOS tooling covers setup: Device Manager, and Extended Controls for location, car data and rotary. The adb commands below are the scriptable equivalents, proven on the NyasaPlayer AVD.

**What NyasaPlayer answers directly:** Discover Nearby takes the same approach as NyasaPlayer's AAOS launcher, a custom Compose activity declared `distractionOptimized`, so its driving-state findings apply as they stand. What NyasaPlayer does not cover is rotary focus in Compose (V7), which is checked in M0.

**No dependency on a commercial navigation app:** handoff is verified with the stub navigation app. A real navigation app, if one is present on some image, is an optional extra check and not a POC requirement.

### Recording commands

```bash
# Play services present?
adb shell pm list packages | grep com.google.android.gms

# Which apps can handle the navigation intent?
adb shell cmd package query-activities -a android.intent.action.VIEW -d "geo:53.1440,-6.0633"
```

### Injecting test locations

Use Extended controls → Location, or:

```bash
# Note: longitude first
adb emu geo fix -6.0633 53.1440    # Location A — Greystones
adb emu geo fix -6.2603 53.3498    # Location B — Dublin city centre
adb emu geo fix -9.0568 53.2707    # Location C — Galway city
```

Coordinates live in test configuration (Engineering Plan §7) and are not re-typed per run.

### Simulating Park / Drive

Use Extended controls → Car data (VHAL properties):

- **Park:** Gear = P
- **Drive:** Gear = D, speed > 0

The scriptable equivalent, proven on the NyasaPlayer userdebug AVD:

```bash
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8    # GEAR_SELECTION = DRIVE
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11600207 40   # PERF_VEHICLE_SPEED

# A single speed event decays back to IDLING; hold MOVING for 60 s at 5 Hz:
adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60

adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4    # GEAR_SELECTION = PARK
```

Check what the platform actually sees:

```bash
adb -s emulator-5554 shell dumpsys car_service --services CarDrivingStateService          # 0 parked · 1 idling · 2 moving
adb -s emulator-5554 shell dumpsys car_service --services CarUxRestrictionsManagerService | grep '^Port:'
adb -s emulator-5554 shell cmd car_service get-do-activities <applicationId>               # expect MainActivity listed
```

Notes from NyasaPlayer:

- **Idling is not moving.** Idling reports only `NO_VIDEO`, while moving reports `UxR: 255`. Run Drive scenarios in the **moving** state, using continuous speed events.
- **Gear dominates.** Speed 0 in Drive still counts as moving.
- **If the `Port:` timestamp doesn't change,** your input never reached the platform.
- **Don't use `adb reboot`.** It wedges the emulator's VHAL bridge. Kill and relaunch instead: `adb -s emulator-5554 emu kill`.

### Rotary input

Use Extended controls → Car rotary (rotate, nudge, select, Back). Rotary verification is done without touching the screen.

### Stub navigation app setup

1. Build and install `tools/stub-navigation`: `ANDROID_SERIAL=emulator-5554 ./gradlew :stub-navigation:installDebug`.
2. Confirm it appears in the navigation-intent query above.
3. Optional sanity check, independent of Discover Nearby: send a navigation intent from adb and confirm the stub displays it.
4. During tests, read the received destination on the stub screen or in logcat:

```bash
adb logcat -s StubNav
```

### Provider configuration

Provider key in `local.properties` (not committed). Network access enabled unless a scenario says otherwise.

---

## 3. Test Locations

| ID | Location | Why |
| --- | --- | --- |
| A | Greystones, Co. Wicklow | Coastal town; Scenic and Outdoors with less dense data |
| B | Dublin city centre | Dense urban data; diversity rule matters |
| C | Galway city | Different region; tests coverage outside Dublin |

---

## 4. Relevance Benchmark

Proves recommendations are **useful**, not just returned.

Run it at M2 and again before the demo. **Product owns the judgement**: engineering produces and records the recommendations, and the Product Lead marks each result ACCEPT or REJECT. Product's sign-off is the M2 exit condition.

Recording format, per cell:

```text
Greystones × Family

1. Harbour Playground       ACCEPT
2. Family restaurant        ACCEPT
3. Petrol station           REJECT

Top result relevant?       YES
2 of top 3 useful?         YES
Obviously wrong #1?        NO
Notes:
```

```text
                Coffee  Food  Outdoors  Family  Scenic  Explore
Location A
Location B
Location C
```

For each cell, record the top results (up to 5) and judge:

| Rule | Check |
| --- | --- |
| Top result | Is #1 reasonably relevant to the selected intent? |
| Top three | Where at least three credible places exist, are at least 2 of the top 3 useful? |
| Wrong category | Is an obviously wrong-category place ranked #1? (Must not be.) |
| Sparse | If only 1–2 strong results exist, are they shown without weak padding? |

Record per cell: result names, judgement per rule, and a short note. Sparse cells are **findings**, not failures. They tell product where the data cannot support a category.

The benchmark evaluates the concept. It is not a statistically rigorous ranking experiment.

---

## 5. Demo Scenarios — Core Flow

### A — Nearby Discovery

1. Set location A (Park).
2. Launch Discover Nearby.
3. Select **Outdoors**.
4. Confirm that up to 3–5 recommendations relevant to location A appear.
5. Open a recommendation.
6. Select **Navigate**.

**Expected:** app loads; recommendations ranked and capped; Place Details opens; the stub receives the destination; Back is stable.

### B — Location Context Change

1. Complete A.
2. Change location to B.
3. Return to Discover and select **Outdoors** again.

**Expected:** the recommendations reflect location B where the provider has different nearby places. No result from location A is shown as current.

### C — Intent Change

At one location: select **Coffee**, record; Back; **Family**, record; Back; **Outdoors**, record.

**Expected:** the sets are not consistently the same, each set fits its intent, and the category name is visible in the title.

### D — Place Details

Open a recommendation.

**Expected:** name, distance (or travel time, if calculated), rating if provided, only provided/derived attributes, attribution if required, and the Navigate action. No dense content.

### E — Navigation Handoff

Open a place and select **Navigate**.

**Expected:** the stub navigation app opens and shows or logs `geo:` coordinates equal to the selected place's coordinates (to 5 decimal places). Discover Nearby does not attempt guidance.

### F — Rotary Interaction

Without touch: navigate the categories → select → navigate the recommendations → open → focus Navigate → activate → Back.

**Expected:** the journey completes with rotary only. Verify the app's focus handling: focus is visible, nothing is unreachable, Back lands on a sensible element, and there are no traps.

### G — Touch Interaction

Select a category → a recommendation → Navigate → Back, by touch.

**Expected:** primary actions are easy to select; no swipe or drag is required; Back is consistent.

### H — Park / Drive

1. Park: launch and use the app.
2. Switch to Drive (speed > 0); repeat category selection and recommendation navigation.
3. Select a category and switch gear **while recommendations are loading**.
4. Return to Park.

**Expected:** stable throughout; no crash or corrupted state; driving restrictions respected; the visible count respects the restriction list limit.

---

## 6. Scenarios — Permission

### L — First launch while parked

Clear app data. In Park, launch and select a category.

**Expected:** the Permission Required message with **Grant Permission**. After granting, discovery continues for the selected category.

### M — First launch while driving

Clear app data. In Drive, launch and select a category.

**Expected:** a Permission Required message asking the user to park, with no permission prompt. After switching to Park, Grant becomes available and works.

### N — Permission denied, then granted

Deny the prompt, then retry and grant.

**Expected:** the denied message with Grant and Back; after the grant, discovery works. No crash.

---

## 7. Scenarios — Failure and Edge Cases

Use the live provider where practical. Use controlled fakes (Engineering Plan §20) where a condition can't be produced on demand.

### I — Empty Results

A narrow radius, a sparse location/category pair, or the fake's empty response.

**Expected:** “No good matches nearby / Try another category.” Back returns to Discover.

### J — Network Failure

Disable emulator networking, or use the failing fake.

**Expected:** “Unable to load places” with Retry and Back. No crash.

### K — Location Unavailable

Permission granted but no fix (fake `Unavailable`).

**Expected:** “Location unavailable” with Retry and Back.

### O — Provider Timeout

The slow fake exceeds the configured timeout.

**Expected:** the loading state ends and the Timeout/error message appears with Retry and Back. Loading never continues indefinitely.

### P — Late / Stale Response and Rapid Switching

Slow fake for Coffee: select **Coffee** → Back → **Family** before Coffee returns.

**Expected:** Family recommendations are shown, and the late Coffee response is dropped. Repeat with a location change during loading.

### Q — Sparse and Null-Heavy Data

The fake returns two credible places, plus places with no rating, opening state or attributes.

**Expected:** 1–2 recommendations shown without padding. Rows render cleanly without missing fields. No unknown attribute is displayed.

### R — Place Details Request Fails

The fake details call fails.

**Expected:** Place Details shows the known summary data, and Navigate still works.

### S — Navigation Handoff Error

Uninstall or disable the stub navigation app (and confirm no other handler exists), then select Navigate.

**Expected:** “Navigation unavailable” with Back. No crash.

### T — Deepest Allowed Flow

In Drive, take the deepest core path including a state change. For example: permission message → recommendations → details → Back → Back. Repeat for the stretch path, if it is implemented.

**Expected:** each Back returns one logical step and ends on Discover; focus lands sensibly after each Back; no crash.

---

## 8. Stretch Demo — Along Route

```text
Start:        Dublin (location B)
Destination:  Galway (preset)
Category:     Family
```

1. Set the start location.
2. Choose the **Galway** preset.
3. Calculate the route.
4. Search for family-friendly places along it.
5. Rank them, and show minutes ahead and detour.

**Expected:**

```text
Family Stops Ahead

Riverside Park
18 min ahead · +4 min detour

Visitor Centre
36 min ahead · +6 min detour
```

At least one recommendation shows meaningful route relevance. Detour values are calculated, not estimated by hand.

---

## 9. Performance Observations

Performance is a **POC target, not a release gate**. Record measured values; don't fail the POC on emulator debug-build latency.

| Measure | Target | Measured |
| --- | --- | --- |
| Launch → Discover visible | Appears quickly | |
| Category → recommendations | Normally within a few seconds | |
| Open place → details | Normally within a few seconds | |
| Provider timeout | Exists; ends loading (~8 s configured) | |

The hard requirements are:

- no infinite loading
- a stable loading UI
- clear timeout recovery
- no stale response overwriting current results

---

## 10. Functional Test Checklist

### Setup

- [ ] Emulator configuration recorded (§2)
- [ ] Stub navigation app installed and visible to the navigation intent

### Launch

- [ ] App launches
- [ ] Discover grid renders

### Permission

- [ ] First launch, parked: grant works
- [ ] First launch, driving: prompts the user to park
- [ ] Denied, then granted

### Discovery

- [ ] Coffee, Food, Outdoors, Family, Scenic, Explore each exercise the live provider
- [ ] Relevance benchmark recorded

### Recommendations

- [ ] Ranked
- [ ] Capped at min(5, restriction limit); not padded
- [ ] Readable with missing data
- [ ] Category name in title

### Details

- [ ] Opens
- [ ] Only provided/derived attributes shown
- [ ] Summary-only fallback works
- [ ] Navigate available
- [ ] Attribution shown where required

### Navigation

- [ ] Stub receives correct coordinates
- [ ] Failure state shown with no handler

### Input

- [ ] Touch journey
- [ ] Rotary journey without touch
- [ ] Back stable
- [ ] Focus verified (visible, reachable, no trap)

### Vehicle state

- [ ] Park
- [ ] Drive
- [ ] Gear change while loading

### Failure states

- [ ] Empty
- [ ] No network
- [ ] Location unavailable
- [ ] Provider failure
- [ ] Timeout
- [ ] Stale response dropped
- [ ] Navigation unavailable
- [ ] Deepest path: Back stable

---

## 11. Recommended Automated Test Coverage

Automated tests have two layers: JVM unit tests, and Compose UI tests run locally under Robolectric. Emulator scenarios (§5–§8) cover touch, rotary, Back, permission, Park/Drive and the full flow.

### Compose UI tests (Robolectric)

- each screen renders each of its states (Discover grid; Recommendations loading, content, empty, error, permission; Details content and summary-only)
- Recommendations: loading → content, loading → empty, loading → error, all on the same screen
- Navigate starts `ACTION_VIEW` with the expected `geo:` URI, asserted with Robolectric's `shadowOf(application).nextStartedActivity`
- Grant is hidden while `distractionOptimizationRequired` is true and shown when it is false (fake `DrivingRestrictions`)

These run as local tests with no emulator, using NyasaPlayer's setup (delivery plan V2). One M0 smoke test verifies the project configuration. Rotary, focus and Park/Drive on the real platform are covered by the emulator scenarios.

### RecommendationEngine (fixture-based; assert order, not individual weights)

- category match dominates
- known-closed excluded
- unknown fields neutral
- diversity cap applied
- score floor drops weak results; no padding
- stable ordering on ties
- detour penalty (stretch)

### Category mapping

- one table-driven test covering all six categories

### Provider mapper

- recorded fixtures from the spike, including null-heavy responses
- `PROVIDED` vs `DERIVED` attributes set correctly

### State and errors

- provider / network / timeout / location → UI state
- ViewModel shows min(desired, restriction limit), re-trimmed when the driving state changes
- empty → `Empty`
- stale `requestId` dropped

### Navigation

- `geo:` URI uses `Locale.US`, 6 decimal places
- exceptions → `NavigationUnavailable`

### Route calculations (stretch)

- base duration
- via-place duration
- detour = via − base
- ordering by route relevance

---

## 12. Demo Recording Script

One continuous flow on the recorded emulator configuration:

1. Show the AAOS emulator (Park).
2. Launch Discover Nearby.
3. Select **Family**.
4. Show real recommendations at location A.
5. Open one; show Place Details.
6. Select Navigate; show the stub receiving the destination.
7. Return to Discover Nearby.
8. Change location to B; select **Family** again; show the recommendations changed.
9. Show a sparse case (for example, Scenic at a location with little data) returning 1–2 strong results, not padding.
10. Switch to Drive; use rotary to choose another category and reach Navigate.
11. If implemented, demonstrate Along Route (Dublin → Galway, Family).

---

## 13. What the Demo Proves

- native AAOS execution (a distraction-optimized Compose app)
- intent-based discovery
- real location context
- real data from a provider licensed for in-vehicle use
- useful recommendations (relevance benchmark)
- driver-oriented interaction
- rotary support
- navigation handoff via a standard `geo:` intent
- emulator-based vehicle-state validation

---

## 14. What the Demo Does Not Prove

The demo must not be presented as evidence of:

- production readiness
- real-world driving safety
- OEM certification
- behaviour with OEM or commercial navigation apps
- physical head-unit performance
- real GNSS quality
- production network resilience
- real rotary ergonomics
- physical vehicle compatibility
- production API economics
- full accessibility compliance
- statistically validated ranking quality

---

## 15. POC Exit Criteria

The POC is complete when:

- all core scenarios (A–H), permission scenarios (L–N) and failure scenarios (I–K, O–T) pass
- the relevance benchmark has been run and its findings recorded
- the emulator configuration is recorded
- known limitations are documented
- the primary flow can be demonstrated reliably
- product can judge whether the concept is valuable enough for a follow-on phase
- engineering can name the major remaining risks for real-hardware validation and provider scale
