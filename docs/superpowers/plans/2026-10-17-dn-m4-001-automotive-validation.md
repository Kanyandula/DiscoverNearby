# DN-M4-001 Automotive Validation Run Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the docs/04 scenarios A–T that apply to the core scope on the reference AAOS emulator, record actual
outcomes and defects, and record the emulator configuration.

**Architecture:** A validation run, not a feature. No app code changes unless a scenario finds a defect, and defects
are recorded and ticketed, not fixed here (Product decides). Engineering drives touch, Park/Drive, permissions and
failure scenarios over adb; the rotary run is hand-driven by the user in Extended Controls while engineering reads
focus. Results go into one name-free run record in the repo.

**Tech Stack:** `AAOS_AOSP_33_userdebug` emulator, adb (`input`, `emu geo fix`, `car_service` VHAL injection, `pm`),
the debug build's `--es scenario <FakeScenario>` fakes, the `:stub-navigation` app.

**Spec:** DN-M4-001 (vault ticket); docs/04 §§2, 5–7, 9, 10; docs/02 §§15–17, 21; docs/03 §§17–22; docs/05 §9;
ADR-002.

## Global Constraints

- Always `adb -s emulator-5554`; first `adb -s emulator-5554 emu avd name` must print `AAOS_AOSP_33_userdebug`.
- Never `adb reboot`; kill and relaunch the emulator (`emu kill`).
- Rotary runs: no `uiautomator` at any point; read focus from
  `adb -s emulator-5554 shell dumpsys activity service com.android.car.rotary/.RotaryService` (`focusedNode`) and
  screenshots, never while the app is launching.
- The hand-driven Extended Controls rotary journey is recorded separately as supplementary evidence. It does not
  replace or overwrite the V7 re-test result (docs/04 §5 F, ADR-002). No rotary code change without a new Product
  decision.
- Public repo: no real place names, phone numbers or raw coordinates in the record, commits or the PR. Screenshots of
  live HERE data are never committed; they stay in session scratch.
- Drive scenarios run in the **moving** state (continuous speed events), not idling.
- A new `geo fix` can fail to reach the GPS: confirm each fix (the next result list's distances) before relying on
  it, and wait at least 12 s before re-querying (docs/04 §2).
- Tripadvisor: about 10 calls per live list opened (two per enriched row). Budget for the run: about 150 calls of the
  1,000 free a month. Use the fakes where the scenario allows.
- During engineering-driven tasks the user doesn't touch the emulator window (touches from it land in the run, as
  on 2026-10-17).
- Copy to expect (strings.xml): "No good matches nearby" / "Try another category."; "Unable to load places";
  "Location unavailable"; "Taking longer than expected"; "Navigation unavailable"; "Location permission required";
  "Grant Permission"; "Open Settings"; "More details unavailable right now"; "Park to see places"; "Try Again".

## Review Focus

1. **Process death on Place Details:** after the app's process is killed in the background, returning to it shows
   Place Details of the same place (the route carries it as JSON), not a crash or a blank screen. Task 4, Step 6.
2. **Recovery after a failure:** after "Unable to load places" (network off) or "Location unavailable" (location
   off), restoring the network or location and selecting Try Again loads results. Task 5, Steps 3 and 4.
3. **Navigate tapped twice quickly:** one `geo:` hand-off reaches the stub (singleTask), no crash, and Back from the
   stub returns to Place Details. Task 2, Step 6.
4. **Saved while driving:** the Saved screen applies the driving limit and stays usable in Drive. Task 4, Step 5.
5. **Drive while the permission dialog is open:** switching to Drive with the system permission dialog showing
   leaves the app stable; afterwards Grant is offered only while parked. Task 3, Step 4.

## Files

- Create: `docs/validation/2026-10-17-m4-emulator.md`, the name-free run record (configuration, one row per scenario
  with outcome and evidence, defects, performance).
- Modify: `docs/04-discover-nearby-test-demo-plan.md` §2 (the M4 configuration check) and §9 (measured values).
- Modify: `docs/adr/0002-ui-stack-after-v7.md`, one line pointing to the M4 hand-driven rotary journey.
- Modify: `docs/05-discover-nearby-delivery-plan.md` §9 (the M4 line), `CLAUDE.md` (state and Next).
- Vault: the ticket's completion notes; a defect ticket per defect found, `status: backlog`, for Product to rank.

## Reference coordinates (1024 × 768 screen)

Discover tiles: Coffee (273, 268), Food (558, 268), Outdoors (843, 268), Family (273, 512), Scenic (558, 512),
Explore (843, 512). Rail: Discover (62, 170), Saved (62, 262). Screen Back arrow (178, 194). List rows: first
(560, 314), second (560, 464). Place Details: Save (345, 561), Navigate (770, 561). Buttons on message screens
(Grant, Try Again, Open Settings): read their position from a screenshot first.

Shell helpers for every task (zsh: define a function, not a variable):

```bash
a() { adb -s emulator-5554 "$@"; }
S=<session scratchpad>/m4; mkdir -p $S
shot() { a exec-out screencap -p > "$S/$1.png"; date +%H:%M:%S; }
launch() { a shell am start -S -W -n com.kanyandula.discovernearby/.ui.MainActivity "$@" | grep -E "TotalTime|Status"; }
park()  { a shell cmd car_service inject-vhal-event 0x11400400 4; }
drive() { a shell cmd car_service inject-vhal-event 0x11400400 8; a shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 120 & }
state() { a shell dumpsys car_service --services CarDrivingStateService | grep "Current Driving State"; }  # 0 parked, 2 moving
stub()  { a logcat -d -s StubNav | tail -3; }
```

---

### Task 1: Configuration and the record

**Files:** Create `docs/validation/2026-10-17-m4-emulator.md`; modify `docs/04-discover-nearby-test-demo-plan.md` §2.

- [ ] **Step 1:** Cold-boot: `a emu kill`, wait for exit, then
  `$ANDROID_HOME/emulator/emulator -avd AAOS_AOSP_33_userdebug -port 5554 -no-snapshot-load &`; wait for
  `sys.boot_completed` = 1; `a emu avd name` prints `AAOS_AOSP_33_userdebug`.
- [ ] **Step 2:** Install both apps from this branch:
  `ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug :stub-navigation:installDebug`; location on for user 10
  (`a shell cmd location set-location-enabled true --user 10`); `park`; `state` prints 0.
- [ ] **Step 3:** Record the configuration: image revision and build (`a shell getprop ro.build.fingerprint`), emulator
  version (`$ANDROID_HOME/emulator/emulator -version | head -1`), Android Studio version (About dialog, or ask the
  user), Compose BOM (`gradle/libs.versions.toml`), `geo:` handlers
  (`a shell cmd package query-activities --user 10 -a android.intent.action.VIEW -d "geo:0,0"` lists only the stub),
  DO check (`a shell cmd car_service get-do-activities com.kanyandula.discovernearby` lists `MainActivity`), and
  the driving list limit while moving (from `dumpsys car_service --services CarUxRestrictionsManagerService`).
- [ ] **Step 4:** Write the record skeleton: Setup (the values above), a Results table with one row per scenario
  (A–T, plus the five Review Focus checks), Defects, Performance, Rotary journey, Sign-off. Add an "M4 (2026-10-17)"
  column or note to docs/04 §2 with any value that changed since M0.
- [ ] **Step 5: Commit** `git commit -m "Record the M4 emulator configuration"`.

### Task 2: Core flow by touch, live (A, B, C, D, E, G) and performance

**Files:** Modify `docs/validation/2026-10-17-m4-emulator.md`.

- [ ] **Step 1 (A):** Location A (`a emu geo fix -6.0633 53.1440`, twice, 3 s apart), `park`, `launch` (record
  TotalTime), shot. Tap Outdoors, shot at 3 s and 8 s (time to results). Expected: 1–5 ranked rows, title "Outdoors",
  distances from A.
- [ ] **Step 2 (D):** Tap the first row; shot. Expected: name, kind and distance, open state if HERE has hours, rating
  and photo only if Tripadvisor matched, "Car park nearby" only if derived, "© {year} HERE", Save and Navigate; no
  attribute HERE didn't provide.
- [ ] **Step 3 (E):** Clear the stub log (`a logcat -c`), tap Navigate; shot; `stub`. Expected: the stub opens and
  logs `geo:` with the place's coordinates to 6 decimals (compare with the details' position from the replayed
  `/browse`, in scratch only; record "match", not the numbers). Back returns to Place Details.
- [ ] **Step 4 (B):** Back twice to Discover. Location B (`geo fix -6.2603 53.3498`, twice), wait 12 s, tap Outdoors,
  shot. Expected: different places, distances from B; no row from A.
- [ ] **Step 5 (C):** At B: Coffee → shot → Back; Family → shot → Back; Outdoors → shot. Expected: three different
  sets, each fitting its intent, the category name in each title. Food, Scenic and Explore were exercised live in
  the M2 benchmark (docs/benchmarks/); record that for docs/04 §10's "all six live" item rather than re-running them.
- [ ] **Step 6 (G + Review Focus 3):** By touch: a category → a row → Navigate → Back → Back → Back. Then on Place
  Details tap Navigate twice within 300 ms (`a shell input tap 770 561 & a shell input tap 770 561`); `stub`; Back.
  Expected: no swipe needed; each Back one step; a double tap gives one stub screen, no crash.
- [ ] **Step 7 (performance, docs/04 §9):** From the timings above: launch → Discover (`TotalTime`), category →
  results, row → details. Record measured values; they are observations, not gates.
- [ ] **Step 8:** Record each scenario's outcome (PASS / FAIL / PASS WITH NOTE) and evidence (screenshot names kept in
  scratch, not committed). **Commit** `git commit -m "Record M4 core-flow results"`.

### Task 3: Permissions (L, M, N, the denial paths)

**Files:** Modify the record.

- [ ] **Step 1 (L):** `park`; `a shell pm clear --user 10 com.kanyandula.discovernearby`; `launch`; tap Coffee; shot.
  Expected: "Location permission required" with Grant Permission. Tap Grant; shot the system dialog; tap "While using
  the app"; shot after 8 s. Expected: Coffee results load without reselecting.
- [ ] **Step 2 (M):** `pm clear` again; `drive`; `state` prints 2; `launch`; tap Coffee; shot. Expected: the
  park-to-allow message ("Park the vehicle to allow…"), no Grant, no system prompt. `park`; shot. Expected: Grant
  appears; tap it and allow; results load.
- [ ] **Step 3 (N and the permanent denial):** `pm clear`; `park`; `launch`; Coffee; Grant; tap "Don't allow"; shot.
  Expected: the denied copy with Grant and Back. Grant again, deny again (Android 13 treats a second denial as
  permanent); shot. Expected: Open Settings replaces Grant (DN-UX-001). Tap Open Settings, allow location in the app's
  permission page, press Back; shot. Expected: the search runs again and results load.
- [ ] **Step 4 (Review Focus 5):** `pm clear`; `park`; `launch`; Coffee; Grant (dialog showing); `drive`; shot after
  3 s. Allow if the dialog is still there. Expected: no crash; if not granted, Grant is replaced by the park message
  while moving. `park`.
- [ ] **Step 5:** Restore: permissions granted, location on for user 10. Record; **commit**
  `git commit -m "Record M4 permission results"`.

### Task 4: Park/Drive, the list limit and the deepest path (H, T)

**Files:** Modify the record.

- [ ] **Step 1 (H, live):** `park`; open Family; Back. `drive`; `state` 2; open Family; shot; count rows. Expected:
  up to min(5, limit) rows (5 with this image's limit), a stable screen, DO honoured (no block screen). Open a row,
  Back.
- [ ] **Step 2 (H, gear change while loading):** `launch --es scenario SLOW`; tap Coffee; at 2 s `park`, at 4 s
  `drive`; shot at 6 s and 12 s. Expected: no crash or corrupted state; at 10 s past the 8 s timeout the timeout
  message with Try Again. A plain `launch` afterwards serves HERE again: it force-stops, and a scenario holds only
  until the process restarts.
- [ ] **Step 3 (list limit):** Record the moving limit the platform reports (Task 1) and that the list shows
  `min(5, limit)`. Limits below 5 and the zero limit ("Park to see places") can't be produced on this image; record
  them as covered by `RecommendationsViewModelTest` and `SavedViewModelTest`, naming the tests.
- [ ] **Step 4 (T, in Drive):** `drive`; Discover → a category → a row → Back → Back; shot after each step; one more
  Back. Expected: each Back one logical step, ending on Discover; Back on Discover leaves the app (launcher shown).
  Also the permission variant: `pm clear`, `drive`, launch, Coffee (park message) → Back → Discover. Restore the grant.
- [ ] **Step 5 (Review Focus 4):** Save two places while parked (Place Details → Save). `drive`; open the Saved tab;
  shot. Expected: the saved places within the limit, nearest first, rows open Place Details. `park`; remove the test
  saves.
- [ ] **Step 6 (Review Focus 1):** `park`; open a place's details; press Home (`a shell input keyevent 3`); kill the
  process (`a shell am kill --user 10 com.kanyandula.discovernearby`); relaunch from recents (`a shell am start -n
  …MainActivity` without `-S`); shot. Expected: Place Details of the same place, or Discover; no crash.
- [ ] **Step 7:** Record; **commit** `git commit -m "Record M4 Park/Drive and deepest-path results"`.

### Task 5: Failure and edge cases (I, J, K, O, P, Q, R, S)

**Files:** Modify the record.

- [ ] **Step 1 (I):** `launch --es scenario EMPTY`; Coffee; shot. Expected: "No good matches nearby" / "Try another
  category." with Back; Back returns to Discover.
- [ ] **Step 2 (Q):** `launch --es scenario SPARSE`; Coffee; shot. Then `NULL_HEAVY`. Expected: 1–2 rows, no padding,
  rows clean with missing fields, no unknown attribute shown.
- [ ] **Step 3 (J + Review Focus 2):** `launch --es scenario NETWORK_FAILURE`; Coffee; shot. Expected: "Unable to load
  places" with Try Again and Back. Also `PROVIDER_FAILURE`. Live: `launch`, airplane mode on
  (`a shell cmd connectivity airplane-mode enable`), Coffee; shot; airplane mode off, wait 10 s, Try Again; shot.
  Expected: the error, then results.
- [ ] **Step 4 (K + Review Focus 2):** Live: `a shell cmd location set-location-enabled false --user 10`; Coffee;
  shot after 10 s. Expected: "Location unavailable" with Try Again and Back. Location on, wait 12 s, Try Again; shot.
  Expected: results.
- [ ] **Step 5 (O):** `launch --es scenario SLOW`; Coffee; shots at 2 s, 9 s. Expected: loading, then "Taking longer
  than expected" with Try Again and Back; loading never continues.
- [ ] **Step 6 (P):** `launch --es scenario SLOW`; Coffee; Back at 1 s; Family; shots at 3 s and 12 s. Expected: Family
  results, still Family at 12 s (the late Coffee response dropped). Live variant: Outdoors at A, change to B at 1 s;
  shot at 12 s. Expected: one consistent list (A's or a fresh B search), never a mix; record which.
- [ ] **Step 7 (R):** `launch --es scenario DETAILS_FAILURE`; Coffee; a row; shot. Expected: the summary with "More
  details unavailable right now", Navigate works (`stub`).
- [ ] **Step 8 (S):** `a shell pm disable-user --user 10 com.kanyandula.stubnavigation`; confirm no `geo:` handler
  (query-activities empty); a place → Navigate; shot. Expected: "Navigation unavailable" with Back; no crash. Re-enable
  (`pm enable --user 10 …`).
- [ ] **Step 9:** Record; **commit** `git commit -m "Record M4 failure-scenario results"`.

### Task 6: Hand-driven rotary journey (F, and focus after Back in T)

**Files:** Modify the record; modify `docs/adr/0002-ui-stack-after-v7.md` (one pointer line).

- [ ] **Step 1:** Fresh state, parked, live, `launch` (cold), no further polling. The user opens Extended Controls →
  Car rotary and doesn't touch the screen for the rest of the task.
- [ ] **Step 2:** The user drives, one step at a time, and says when each is done; after each, engineering takes a
  screenshot and reads `focusedNode` from RotaryService:
  1. Rotate through the six tiles (and on to the rail); note where initial focus starts.
  2. Select Outdoors; rotate through the rows (below the fold too) and onto the rail.
  3. Select a row; rotate to Save and Navigate; select Navigate (the stub opens; `stub`).
  4. Back from the stub; Back to the list; Back to Discover. Note where focus lands after each Back, and whether
     the next turn moves it.
  5. In Drive (`drive`): repeat 2–4 once (scenario T with rotary).
- [ ] **Step 3:** Record against the V7 gate items (rotary reaches Navigate; select activates the focused control;
  Back returns to a usable screen without losing a turn; visible focus on every actionable control) and where focus
  lands after each Back (recorded, not gated). Note the known Back-to-Discover limitation if it shows (ADR-002
  waiver). This is supplementary evidence; the V7 row and the re-test result stay as they are.
- [ ] **Step 4:** ADR-002: one line under the re-test section linking the M4 record's rotary journey.
  **Commit** `git commit -m "Record the M4 hand-driven rotary journey"`.

### Task 7: Close-out

**Files:** docs/04 §9, docs/05 §9, CLAUDE.md, the record's sign-off; vault ticket and defect tickets.

- [ ] **Step 1:** docs/04 §9 Measured column from Task 2 Step 7.
- [ ] **Step 2:** Defects: one vault ticket per defect (`status: backlog`, the scenario, the observed vs expected
  result, screenshot names), listed in the record's Defects section without place names.
- [ ] **Step 3:** docs/05 §9: the M4 line (DN-M4-001 run recorded; DN-M4-002 next: demo and GO decision).
  CLAUDE.md: the state line and Next.
- [ ] **Step 4:** Name-leak check: HERE titles from the run's replays and screens against the diff and commit
  messages: 0 hits.
- [ ] **Step 5:** `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` (docs only, CI runs it anyway).
  **Commit**, push, open the PR with the `pr-description` skill (ticket ID and acceptance criteria; no AI
  attribution).
