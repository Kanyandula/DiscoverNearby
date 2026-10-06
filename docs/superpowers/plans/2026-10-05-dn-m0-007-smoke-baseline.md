# DN-M0-007 Automated and Emulator Smoke Baseline Implementation Plan

> **Historical run note (2026-10-06):** The rotary observations below are from the 2026-10-05 smoke and remain
> a record of that run. Its rotary runs used the `uiautomator`-waited harness that E1 (DN-SP-003) later
> found reproduces V7's failure. Current V7 status:
> [the register](../../05-discover-nearby-delivery-plan.md#9-verification-register) and [ADR-002](../../adr/0002-ui-stack-after-v7.md).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish M0's test baseline:
- the reference emulator configuration is recorded in docs/04 §2;
- its Park/Drive and location commands are confirmed;
- a repeatable M0 emulator smoke is defined in docs/04 and run, with every observation recorded;
- the one Robolectric smoke test that renders `DiscoverScreen` is confirmed running locally and in CI.

**Architecture:** No production code. AC 1 is already met by `ui/screens/DiscoverScreenTest` (DN-M0-002), so this ticket confirms it rather than adding a duplicate. The rest is documentation and recorded emulator runs:
- the docs/04 §2 "Recorded (M0)" column;
- a short "M0 smoke" list in docs/04 §10 that later milestones re-run;
- the ticket's completion notes, holding what each smoke step showed.

**Tech Stack:** adb against `emulator-5554` (`AAOS_AOSP_33_userdebug`, driver user 10): `cmd car_service`, `adb emu geo fix`, `dumpsys location`, `cmd package query-activities`, `uiautomator dump`, `screencap`; Gradle `testDebugUnitTest`; `gh run view`.

**Spec:**
- Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-007-test-baseline-and-emulator.md`.
- `docs/04-discover-nearby-test-demo-plan.md` §2 (configuration, Park/Drive, location, rotary, stub), §5 A/D/E/F/H, §7 T, §10–11.
- `docs/03-discover-nearby-engineering-implementation-plan.md` §21 (M0 list).
- `docs/05-discover-nearby-delivery-plan.md` §3 (test baseline), §9 (V7).

## Global Constraints

- **AC 1:** One Compose UI Robolectric smoke test renders `DiscoverScreen` and runs in `testDebugUnitTest` and in CI.
- **AC 2:** The reference configuration is recorded in the docs/04 §2 "Recorded" column through this ticket's PR. That covers AVD `AAOS_AOSP_33_userdebug`, ABI, Android Studio version, Compose BOM version, and whether a `geo:` handler is installed by default.
- **AC 3:** The Park/Drive and location-injection commands (docs/04 §2) are confirmed working on the reference emulator.
- **AC 4:** The M0 emulator smoke covers launch, the Discover → Recommendations → Details flow, rotary (per DN-M0-011), Park/Drive, Back on the deepest path, and the installed stub navigation app.
- **Verification:** "Record actual observations and any deviations" (ticket).
- Always `adb -s emulator-5554`. Never `adb reboot`.
- Never commit or log keys or raw coordinates. The docs/04 §3 test locations are fixed test data, not user data.
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes. Never commit on local `main`. No AI attribution.

## Open decisions (recorded here, in the ticket and the PR)

1. **Rotary is covered, not passed.** At the time of this 2026-10-05 smoke, DN-M0-011 was **blocked** on ADR-002 after V7 was recorded Not workable. For the current V7 disposition, see the note above.
   - The smoke re-runs DN-M0-011's rotary checks on the current build and records the result next to V7, whether it matches or differs.
   - M0 exit stays blocked by V7 (docs/05 §9) whatever this ticket shows. The ticket can be `done` (baseline established) while M0 stays open. That is the user's call at merge time.
2. **No new Robolectric test.** `DiscoverScreenTest` already renders `DiscoverScreen` (6 tests) in `testDebugUnitTest`, which CI runs. A dedicated "smoke" test would duplicate it. The plan names it as the M0 smoke test in docs/04 §11.
3. **The M0 smoke is a documented checklist, not a script.** Coordinate taps and uiautomator dumps are tied to this AVD's layout and break with the UI. ADR-002 may replace the UI layer, so a scripted smoke now would be rewritten. Scripting can come in M4 (automotive validation) if wanted.
4. **The smoke runs from a known state:**
   - app reinstalled from the branch build;
   - `pm clear --user 10`, then location granted through the app's own flow;
   - location on for user 10, emulator at Greystones;
   - parked;
   - stub installed.

## Outcome (2026-10-05)

Read with the PR and ticket notes. Where the run differed from this plan:
- `DiscoverScreenTest` has 4 tests, not 6.
- SLOW Coffee ends in the timeout message, not a list. Its 10 s delay outlasts the 8 s provider timeout (Scenario O).
- Leaving the app lands on `com.android.car.mapsplaceholder`.
- `D="adb … shell"` doesn't word-split in zsh, so a function was used instead.
- `dumpsys location` is stale until an app requests location, so location was checked through the app.
- The image ships Play services and the Play Store.
- The smoke found a regression from DN-M0-008: the stub had no `versionCode`, so installs failed as a downgrade. It is fixed in this branch.

## Review Focus

1. **The wrong configuration gets recorded,** for example from another running emulator, an arm image or a `user` build. Expect values read from the running device (`getprop`) and the AVD's `config.ini`, not from the plan's "Planned" column. Pinned by Task 1 Step 1.
2. **"Drive works" judged from the gear alone,** while the platform is idling (`NO_VIDEO` only) and not moving (`UxR: 255`). Expect state 2 plus a new `Port:` timestamp. Pinned by Task 1 Step 3.
3. **A location fix that the platform got but the app can't use** (location off for user 10, or permission missing) shows up as an app error rather than a command failure. Expect both the platform's last location and the app's Recommendations for that place. Pinned by Task 1 Step 4 and Task 2 Step 2.
4. **"`geo:` handler installed by default" answered with the stub installed.** Expect the answer to count handlers other than the stub. Pinned by Task 1 Step 2.
5. **Two Android Studio installs** (`Android Studio.app` and `Android Studio Quail.app`). Expect the version of the one actually running this project. Pinned by Task 1 Step 1.

---

## Files

| File | Change |
| --- | --- |
| `docs/04-discover-nearby-test-demo-plan.md` | §2: fill the "Recorded (M0)" column, and correct any command that doesn't work as written (recorded as a deviation). §10: add "M0 smoke". §11: name `DiscoverScreenTest` as the M0 smoke test. |
| `CLAUDE.md` | "Current state": one sentence for DN-M0-007. |
| Ticket (vault) | Completion notes: configuration, command checks, smoke results, deviations. |

Scratchpad: `S=/private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad`. Helpers:
- `launch.sh [am extras]`: force-stop, then start `MainActivity`.
- `tap.sh <text>`: tap the first node with that text or content-desc.
- `waitfor.sh <text> [s]`: wait for a node containing the text.
- `texts.sh`: print every on-screen text.
- `pause.sh N`: wait N seconds (foreground `sleep` is blocked in this harness).
- `rfocus.sh`: the tile, or bounds, the RotaryService has focused.
- `deepflow.sh <tag>`: DN-M0-011's rotary journey: Discover → Recommendations → Details and back, rotating on every screen.

Shell state doesn't persist between commands, so start every step's block with:

```bash
S=/private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad
D="adb -s emulator-5554 shell"
state() { $D dumpsys car_service --services CarDrivingStateService | grep "changed from" | tail -1; }
port() { $D dumpsys car_service --services CarUxRestrictionsManagerService | grep '^Port:' | head -1; }
```

---

### Task 0: Start the ticket

- [ ] **Step 1:** In the ticket set `status: in_progress`, `branch: dn-m0-007-smoke-baseline`.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-007-smoke-baseline
git add docs/superpowers/plans/2026-10-05-dn-m0-007-smoke-baseline.md
git commit -m "Add DN-M0-007 implementation plan"
```

---

### Task 1: Record the reference configuration and confirm its commands

**Files:** Modify `docs/04-discover-nearby-test-demo-plan.md` §2 (the table, and any command that needs a correction).

**Interfaces:**
- Produces, for Task 2: a parked emulator at Greystones, location on for user 10, and the app and stub installed from this branch.

- [ ] **Step 1: Read the configuration from the device and the AVD (Review Focus 1, 5)**

```bash
adb devices
adb -s emulator-5554 emu avd name
grep -E "^(image.sysdir.1|abi.type|hw.device.name|target|tag.id)=" ~/.android/avd/AAOS_AOSP_33_userdebug.avd/config.ini
grep -E "^Pkg.Revision" ~/Library/Android/sdk/system-images/android-33/android-automotive/x86_64/source.properties
adb -s emulator-5554 shell getprop ro.build.type
adb -s emulator-5554 shell getprop ro.build.version.sdk
adb -s emulator-5554 shell getprop ro.product.cpu.abi
adb -s emulator-5554 shell getprop ro.build.fingerprint
~/Library/Android/sdk/emulator/emulator -version | head -1
ps -Ao command | grep -E "Android Studio[^/]*\.app/Contents/MacOS" | grep -v grep | sed -E 's|.*/(Android Studio[^/]*\.app)/.*|\1|' | sort -u
for app in "Android Studio" "Android Studio Quail"; do echo "$app: $(defaults read "/Applications/$app.app/Contents/Info" CFBundleShortVersionString 2>/dev/null) ($(defaults read "/Applications/$app.app/Contents/Info" CFBundleVersion 2>/dev/null))"; done
grep -E "^composeBom" gradle/libs.versions.toml
adb -s emulator-5554 shell pm list packages | grep -c com.google.android.gms
```

Expected:
- Exactly one device, `emulator-5554`; `emu avd name` prints `AAOS_AOSP_33_userdebug`.
- `config.ini`: `system-images/android-33/android-automotive/x86_64/`, `x86_64`, `automotive_1024p_landscape`, `android-33`.
- `getprop`: `userdebug`, `33`, `x86_64`.
- Exactly one Android Studio running. Record its name and version; if none is running, record the default `Android Studio.app` and say so.
- `composeBom = "2026.09.00"`.
- The GMS count (Play services present?) goes in the ticket notes.

- [ ] **Step 2: Is a `geo:` handler installed by default? (Review Focus 4)**

```bash
adb -s emulator-5554 shell cmd package query-activities --user 10 -a android.intent.action.VIEW -d "geo:53.1440,-6.0633" | grep -E "activities found|packageName=" | sort -u
```

Expected: `1 activities found:` and only `packageName=com.kanyandula.stubnavigation`, so the image itself installs **no** `geo:` handler. Any other package means it does: record it, and whether a chooser would appear (docs/03 §11).

- [ ] **Step 3: Park/Drive commands, exactly as docs/04 §2 writes them (Review Focus 2)**

```bash
D="adb -s emulator-5554 shell"
port() { $D dumpsys car_service --services CarUxRestrictionsManagerService | grep '^Port:' | head -1; }
state() { $D dumpsys car_service --services CarDrivingStateService | grep "changed from" | tail -1; }
echo "before: $(state) | $(port)"
$D cmd car_service inject-vhal-event 0x11400400 8
$D cmd car_service inject-vhal-event 0x11600207 40; "$S/pause.sh" 2
echo "single speed event: $(state) | $(port)"
($D cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60 > /dev/null 2>&1 &); "$S/pause.sh" 4
echo "continuous: $(state) | $(port)"
$D dumpsys car_service --services CarUxRestrictionsManagerService | grep -m3 -E "UxR:|Port:"
$D cmd car_service get-do-activities com.kanyandula.discovernearby
$D cmd car_service inject-vhal-event 0x11400400 4; "$S/pause.sh" 2
echo "park: $(state) | $(port)"
```

Expected:
- **Single speed event:** the state reaches 2 (moving) and may then decay back to idling, as docs/04 notes.
- **Continuous:** state 2, a newer `Port:` timestamp, and `UxR: 255`.
- **get-do-activities:** lists `com.kanyandula.discovernearby.ui.MainActivity`.
- **Park:** state 0.

If the service name in docs/04 (`CarUxRestrictionsManagerService`) prints nothing, find the right name with `dumpsys car_service --services 2>&1 | grep -i restriction`. Correct docs/04 §2 and record it as a deviation.

- [ ] **Step 4: Location injection (Review Focus 3)**

```bash
$D cmd location set-location-enabled true --user 10
$D cmd location is-location-enabled --user 10
adb -s emulator-5554 emu geo fix -6.2603 53.3498; "$S/pause.sh" 2
$D dumpsys location | grep -m2 -E "last location|Location\[gps"
adb -s emulator-5554 emu geo fix -6.0633 53.1440; "$S/pause.sh" 2
$D dumpsys location | grep -m2 -E "last location|Location\[gps"
```

Expected:
- Location is enabled for user 10.
- The platform's last gps location follows each fix: Dublin (53.3498, -6.2603), then Greystones (53.1440, -6.0633).
- The app's side of this is checked in Task 2 Step 2.
- Record whether `emu geo fix` worked as written, longitude first.

- [ ] **Step 5: Fill the "Recorded (M0)" column**

In docs/04 §2, each row gets what Steps 1–2 printed, with the date, in this form:

| Item | Recorded (M0) |
| --- | --- |
| AVD | `AAOS_AOSP_33_userdebug` (checked 2026-10-05) |
| Emulator image | `system-images;android-33;android-automotive;x86_64`, revision N, `userdebug`, no Play Store (GMS: present/absent) |
| API level | 33 |
| ABI | `x86_64` (Intel Mac) |
| Hardware profile | `automotive_1024p_landscape` |
| Android Studio version | `<app name> <CFBundleShortVersionString> (<CFBundleVersion>)`; emulator `<version>` |
| Compose BOM version | `2026.09.00` |
| `geo:` handler installed by default? | No — the stub is the only handler (`query-activities`, user 10) |
| Stub navigation app | Installed: `./gradlew :stub-navigation:installDebug` (DN-M0-008) |

Add a note under "Simulating Park / Drive" only if Step 3 or 4 found a deviation.

Run `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q`. Expected: green, 142 tests.

```bash
git add docs/04-discover-nearby-test-demo-plan.md
git commit -m "Record the M0 reference emulator configuration"
```

---

### Task 2: Define and run the M0 emulator smoke

**Files:** Modify `docs/04-discover-nearby-test-demo-plan.md` §10 (add "M0 smoke") and §11 (name the smoke test); the ticket notes.

**Interfaces:**
- Consumes (Task 1): the emulator state, parked at Greystones, with location on.

- [ ] **Step 1: Write the smoke into docs/04 §10**

Directly under `## 10. Functional Test Checklist`, before `### Setup`, add:

```markdown
### M0 smoke (DN-M0-007)

Run on the §2 configuration, from a known state: app and stub installed from the build under test, `pm clear --user 10`, location on for user 10, Location A, parked. Record what each step shows.

1. **Launch** — the Discover grid appears; no block screen.
2. **Core flow (touch)** — Coffee → Recommendations (location granted through the app's own flow on first use) → first row → Place Details; Navigate is shown.
3. **Stub** — the stub navigation app answers the `geo:` query (§2) and an adb hand-off shows on it (§2 "Stub navigation app setup", step 3). The app's own Navigate hand-off joins this check once `IntentNavigationLauncher` lands (DN-M3-001).
4. **Rotary (V7)** — without touch: rotate through the grid, select, then rotate on Recommendations and Place Details and Back twice (Scenario F). Record against the docs/05 §9 V7 row.
5. **Park / Drive** — in Drive (moving): open a category, Back; with `--es scenario SLOW`, select Coffee and switch gear while it loads; return to Park (Scenario H). No block screen, no crash.
6. **Deepest path and Back** — in Drive: Discover → Recommendations → Place Details → Back → Back ends on Discover, and Back on Discover leaves the app (Scenario T).
```

In §11, replace the sentence `One M0 smoke test verifies the project configuration.` with:

```markdown
One M0 smoke test verifies the project configuration: `DiscoverScreenTest` renders `DiscoverScreen` in `testDebugUnitTest`, which CI runs on every PR.
```

- [ ] **Step 2: Known state, Launch and Core flow (smoke 1–2; Review Focus 3)**

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug :stub-navigation:installDebug --console=plain | grep "Installed on"
$D pm clear --user 10 com.kanyandula.discovernearby
$D cmd car_service inject-vhal-event 0x11400400 4
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40
adb -s emulator-5554 exec-out screencap -p > "$S/m0007-1-discover.png"
"$S/tap.sh" Coffee; "$S/pause.sh" 3; "$S/texts.sh" | head -12
```

Expected: the Discover grid (`m0007-1-discover.png`), then the Recommendations permission message with a "Grant Permission" action.

```bash
"$S/tap.sh" "Grant Permission"; "$S/pause.sh" 2; "$S/texts.sh" | head -8
```

Expected: the system permission dialog. Choose precise ("While using the app" / "Only this time"):

```bash
"$S/tap.sh" "While using the app" || "$S/tap.sh" "Only this time"; "$S/waitfor.sh" "km" 20
adb -s emulator-5554 exec-out screencap -p > "$S/m0007-2-recommendations.png"
"$S/texts.sh" | head -14
```

Expected: Greystones Coffee recommendations, 3–5 rows with distances (`m0007-2-recommendations.png`). Recommendations for Greystones prove the location fix reached the app.

```bash
first=$("$S/texts.sh" | sed -n 3p | tr -d '"'); echo "opening: $first"
"$S/tap.sh" "$first"; "$S/waitfor.sh" "Navigate" 20
adb -s emulator-5554 exec-out screencap -p > "$S/m0007-3-details.png"
```

Expected: Place Details with the name, facts and a Navigate button (`m0007-3-details.png`). If the third text line isn't the first row's name, pick the first row's name from the `texts.sh` output above.

- [ ] **Step 3: Stub (smoke 3)**

```bash
adb -s emulator-5554 logcat -c
$D am start -W -a android.intent.action.VIEW -d "geo:53.144000,-6.063300" | grep -E "Status|Activity:"
"$S/pause.sh" 2; adb -s emulator-5554 logcat -d -s StubNav | tail -1
adb -s emulator-5554 exec-out screencap -p > "$S/m0007-4-stub.png"
$D input keyevent 4; "$S/pause.sh" 2; "$S/texts.sh" | head -3
```

Expected:
- `Status: ok`, `Activity: com.kanyandula.stubnavigation/.StubNavigationActivity`.
- `StubNav: received geo:53.144000,-6.063300`, shown on screen.
- Back returns to Discover Nearby's Place Details.
- Record that the app's Navigate still goes to `FakeNavigationLauncher` (no hand-off) until DN-M3-001.

- [ ] **Step 4: Rotary, per DN-M0-011 (smoke 4; Open decision 1)**

```bash
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
for i in 1 2 3 4 5 6; do $D cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; echo "turn $i: $("$S/rfocus.sh")"; done
"$S/deepflow.sh" m0007
```

Expected (V7 as recorded):
- Turns 1–6 go Coffee → Food → Outdoors → Family → Scenic → Explore, with the ring visible.
- In the deep flow, rotation works on Discover and on Recommendations at first. On Place Details, or after Back, it may stop: focus sits on the host `Rect(0, 76 - 1024, 672)` and turns do nothing.
- Record the actual lines and whether they match the V7 row. A difference (better or worse) is a finding for ADR-002, not something to fix here.

- [ ] **Step 5: Park / Drive (smoke 5)**

```bash
$D cmd car_service inject-vhal-event 0x11400400 8
($D cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60 > /dev/null 2>&1 &); "$S/pause.sh" 3
echo "$(state)"
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null
"$S/tap.sh" Food; "$S/waitfor.sh" "km" 20 >/dev/null; "$S/texts.sh" | grep -c "km"
adb -s emulator-5554 exec-out screencap -p > "$S/m0007-5-drive-recs.png"
$D input keyevent 4; "$S/pause.sh" 2
"$S/launch.sh" --es scenario SLOW >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null
"$S/tap.sh" Coffee; "$S/pause.sh" 2
$D cmd car_service inject-vhal-event 0x11400400 4; "$S/pause.sh" 1
$D cmd car_service inject-vhal-event 0x11400400 8; "$S/waitfor.sh" "km" 20 >/dev/null; "$S/texts.sh" | head -6
$D cmd car_service inject-vhal-event 0x11400400 4; "$S/pause.sh" 2; echo "$(state)"
$D dumpsys activity activities | grep -m1 topResumedActivity
```

Expected:
- **Drive:** the app shows, not the block screen; Food lists at most 5 rows (`m0007-5-drive-recs.png`).
- **SLOW Coffee with a Park → Drive switch mid-load:** the list arrives with no crash and no stale or mixed state.
- **Return to Park:** state 0, and the app is still the top resumed activity.

The `--es scenario` extra only works on debug builds (DN-M0-004).

- [ ] **Step 6: Deepest path and Back, in Drive (smoke 6)**

```bash
$D cmd car_service inject-vhal-event 0x11400400 8
($D cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60 > /dev/null 2>&1 &); "$S/pause.sh" 3
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null
"$S/tap.sh" Coffee; "$S/waitfor.sh" "km" 20 >/dev/null
first=$("$S/texts.sh" | sed -n 3p | tr -d '"'); "$S/tap.sh" "$first"; "$S/waitfor.sh" "Navigate" 20 >/dev/null
$D input keyevent 4; "$S/pause.sh" 2; echo "back 1: $("$S/texts.sh" | head -2 | tr '\n' ' ')"
$D input keyevent 4; "$S/pause.sh" 2; echo "back 2: $("$S/texts.sh" | head -2 | tr '\n' ' ')"
$D input keyevent 4; "$S/pause.sh" 2; $D dumpsys activity activities | grep -m1 topResumedActivity
$D cmd car_service inject-vhal-event 0x11400400 4
```

Expected:
- Back 1 lands on Recommendations (same category, same list).
- Back 2 lands on Discover.
- Back 3 leaves the app: the top resumed activity is the launcher, not `com.kanyandula.discovernearby`.
- No crash anywhere.

Back is sent as the system key (`input keyevent 4`). The in-app header Back is checked by `DiscoverNavigationTest`.

- [ ] **Step 7: Confirm AC 1 (the Robolectric smoke test locally and in CI)**

```bash
./gradlew :app:testDebugUnitTest --tests '*DiscoverScreenTest' --console=plain -q && grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' app/build/test-results/testDebugUnitTest/TEST-com.kanyandula.discovernearby.ui.screens.DiscoverScreenTest.xml
run=$(gh run list --branch main --workflow CI --limit 1 --json databaseId --jq '.[0].databaseId'); [ -n "$run" ] || run=$(gh run list --limit 1 --json databaseId --jq '.[0].databaseId')
gh run view "$run" --log | grep -m3 -E "testDebugUnitTest|BUILD SUCCESSFUL"
```

Expected:
- Local: `tests="6" skipped="0" failures="0"`.
- CI: the latest run's log shows `testDebugUnitTest` and `BUILD SUCCESSFUL`. CI runs only on PRs, so if `main` has no runs, use the latest PR run.

- [ ] **Step 8: Commit the docs**

Run `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q`. Expected: green.

```bash
git add docs/04-discover-nearby-test-demo-plan.md
git commit -m "Define the M0 emulator smoke"
```

---

### Task 3: Record and close out

**Files:** Modify `CLAUDE.md`; the ticket (vault).

- [ ] **Step 1:** In CLAUDE.md "Current state", before "Next:", add: `Smoke baseline (DN-M0-007): reference configuration in docs/04 §2, the M0 smoke in docs/04 §10 (re-run it when the image or UI changes); the Robolectric smoke test is DiscoverScreenTest.` Commit `Note the M0 smoke baseline in CLAUDE.md`.

- [ ] **Step 2: Ticket completion notes.** Include:
  - each AC with its result;
  - the Task 1 Step 1 values, and GMS present/absent;
  - the Park/Drive and location outputs, plus any deviation;
  - smoke steps 1–6 with what each showed, and the screenshots taken;
  - the rotary result against V7;
  - Open decisions 1–4;
  - checks run.

- [ ] **Step 3: Close out.**
  1. Push and open a draft PR.
  2. Run the `simplify` skill. The diff is docs only, so say so if it finds nothing.
  3. Do the final whole-branch review (executing-plans).
  4. Write the PR description with the `pr-description` skill: ticket ID, acceptance criteria, the M0-exit note from Open decision 1, no AI attribution.
  5. Run `gh pr ready` once CI is green.
