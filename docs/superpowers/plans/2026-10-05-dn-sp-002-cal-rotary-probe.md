# DN-SP-002 Car App Library Rotary Probe Implementation Plan

> **Completed investigation; current interpretation updated 2026-10-06:** DN-SP-002 did not establish that
> templates solve rotary. Cold boots showed two successful first-launch journeys and a repeatable entry
> failure on later launches. See [ADR-002](../../adr/0002-ui-stack-after-v7.md) for the full evidence and
> current comparison with Compose. This plan's conditional “if it passes” steps were not taken.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Answer, on the reference emulator, the question ADR-002's Option A rests on: does a Car App Library template app give rotary a working grid → list → details → Navigate → Back journey where the Compose app failed V7? Record the result in ADR-002. If it passes, add an engineering recommendation for templates; the decision itself stays with the Product Lead.

**Architecture:** A throwaway probe in `tools/cal-rotary-probe/`, set up as a **standalone Gradle build**: its own `settings.gradle.kts`, with the root version catalog imported. It is not included in the root build, so `:app` and CI never see Car App Library APIs.

It holds:
- one `CarAppService` (category POI);
- three template screens, using static data and no location, network or permissions:
  - `GridTemplate`: the six categories;
  - `ListTemplate`: five rows;
  - `PaneTemplate`: facts and a Navigate action.

Navigate logs `ProbeNav: navigate selected`, then tries the template hand-off and logs what happens. The journey is driven by `cmd car_service inject-rotary` / `inject-key`, exactly as for V7.

**Tech Stack:** `androidx.car.app:app` + `androidx.car.app:app-automotive` **1.7.0** (latest stable on Google Maven, 2026-10-05; 1.9.0 is alpha). AGP 9.2.1 via the root catalog; template host `com.google.android.apps.automotive.templates.host` 1.007 on the image; adb rotary injection; RotaryService `dumpsys`.

**Spec:**
- `docs/adr/0002-ui-stack-after-v7.md` (Options A/B, Evidence, Decision).
- `docs/05-discover-nearby-delivery-plan.md` §9 V7, V8.
- `docs/02-discover-nearby-ux-interaction-spec.md` §16 (rotary order and checks).
- `docs/04-discover-nearby-test-demo-plan.md` §5 F (Rotary Interaction).
- User approval 2026-10-05: "half-day, isolated Car App Library rotary probe. Keep it out of :app and test the full grid → list → details → Navigate → Back flow on the reference image. Use the result before finalizing ADR-002; if the flow passes, recommend Car App Library templates."

## Global Constraints

- **Time-box: half a day.** Stop at the time-box with whatever the runs show.
- **Isolated:** nothing in `:app`. The root `settings.gradle.kts`, the root catalog and CI are unchanged. CLAUDE.md's "No Car App Library; do not add its APIs" keeps holding for the app.
- **The full flow, on the reference image** (`AAOS_AOSP_33_userdebug`, `emulator-5554`, driver user 10): grid → list → details → Navigate → Back, by rotary only (docs/04 F: "the journey completes with rotary only").
- **The same pass bar V7 was held to:**
  - focus always visible;
  - every element reachable in order;
  - select works;
  - Back returns one step at a time;
  - no trap;
  - repeated runs give the same result.
- **ADR-002 gets the result.** If the flow passes, it gets an engineering recommendation for Option A. The **Decision** line stays blank for the Product Lead.
- Never commit on local `main`. No AI attribution. Don't log raw coordinates from app code: the probe logs only fixed probe strings.

## Open decisions (recorded here, in the ticket and the PR)

1. **A standalone build, committed under `tools/`.** It isn't in the root `settings.gradle.kts`, so CI, detekt and lint don't build it, and it adds no dependency to the project. It stays in the repo so the evidence can be reproduced. If ADR-002 picks Compose, delete it; if templates, it becomes a reference. Build it with `./gradlew -p tools/cal-rotary-probe assembleDebug`.
2. **No automated tests for the probe.** It is a throwaway spike whose only output is emulator evidence; TDD's prototype exception applies, and you approved a probe, not a feature. The verification is the recorded rotary runs.
3. **Navigate's hand-off is informational.** The pass criterion is that Navigate is reachable and activatable by rotary (the `ProbeNav` log). The template-world hand-off (`CarContext.startCarApp` with `ACTION_NAVIGATE`) is tried and its outcome recorded. The stub navigation app handles `ACTION_VIEW geo:`, not `ACTION_NAVIGATE`, so a failed hand-off there isn't a probe failure; it goes into ADR-002 as a template-path consequence.
4. **Three runs.** V7 varied from run to run, so the probe runs the full journey three times and passes only if all three pass.
5. **No simplify pass.** The probe is throwaway evaluation code; the final review still runs.

## Review Focus

1. **Focus visibility in templates.** "Reachable" isn't enough: each focused element must show a visible highlight. Pinned by screenshots at every step (Task 2).
2. **The host may refuse a sideloaded template app.** If the probe doesn't launch, or shows an error instead of the grid, that is a probe result ("templates not runnable here"), not something to work around. Pinned by Task 2, Step 1.
3. **Back depth:** Back from details goes to the list, from the list to the grid, and from the grid out of the app, one step each. Pinned by Task 2, Step 3.
4. **Driving restrictions change the templates:** list limits, and some actions disabled while moving. One run in Drive records what changes. Pinned by Task 2, Step 4.
5. **Run-to-run variance**, as V7 had. Pinned by three full runs (Task 2, Step 3).

---

## Files

| File | Purpose |
| --- | --- |
| `tools/cal-rotary-probe/settings.gradle.kts` | Standalone build; imports `../../gradle/libs.versions.toml` as `libs`. |
| `tools/cal-rotary-probe/gradle.properties` | `android.useAndroidX=true`, the root's JVM args. |
| `tools/cal-rotary-probe/build.gradle.kts` | Application module: CAL 1.7.0, compileSdk from the catalog. |
| `tools/cal-rotary-probe/src/main/AndroidManifest.xml` | `CarAppService` (POI), `CarAppActivity` (launcher, distractionOptimized), automotive features, `minCarApiLevel`. |
| `tools/cal-rotary-probe/src/main/java/com/kanyandula/calprobe/ProbeService.kt` | Service, session and the three screens. |
| `tools/cal-rotary-probe/README.md` | What it is, how to build and run it, and that it's throwaway. |
| `docs/adr/0002-ui-stack-after-v7.md` | Probe evidence; the recommendation if it passes. |
| `CLAUDE.md` | One line: the probe exists, is isolated and not built by CI. |

Scratchpad: `S=/private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad`. Helpers: `pause.sh`, `rfocus.sh` (focused bounds), `texts.sh`. Use `d() { adb -s emulator-5554 shell "$@"; }`; zsh doesn't word-split a `$D` variable.

---

### Task 0: Start the ticket

- [ ] **Step 1:** Create the ticket `~/.claude/projects/Discover Nearby/tickets/DN-SP-002-cal-rotary-probe.md`:
  - **Frontmatter:** `id: DN-SP-002`, `type: spike`, `milestone: M0`, `status: in_progress`, `priority: P0`, `owner_role: Android Engineer`, `estimate: half a day`, `depends_on: [DN-M0-011]`, `branch: dn-sp-002-cal-rotary-probe`.
  - **Goal:** the probe question above.
  - **Acceptance criteria:**
    - isolated (not in `:app` or CI);
    - full flow by rotary on the reference image, 3 runs, plus one run in Drive;
    - result recorded in ADR-002;
    - recommendation for templates if it passes;
    - the decision is left to the Product Lead.
  - **Source:** the user's approval quote.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-sp-002-cal-rotary-probe
git add docs/superpowers/plans/2026-10-05-dn-sp-002-cal-rotary-probe.md
git commit -m "Add DN-SP-002 Car App Library rotary probe plan"
```

---

### Task 1: Build the probe

**Files:** everything under `tools/cal-rotary-probe/`.

- [ ] **Step 1: Standalone build files**

`tools/cal-rotary-probe/settings.gradle.kts`:

```kotlin
// Throwaway Car App Library rotary probe (DN-SP-002). A standalone build: the root build, :app and CI never see it.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "cal-rotary-probe"
```

`tools/cal-rotary-probe/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
```

`tools/cal-rotary-probe/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.kanyandula.calprobe"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }
    defaultConfig {
        applicationId = "com.kanyandula.calprobe"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Car App Library, latest stable on Google Maven (2026-10-05). Kept out of the root catalog on purpose.
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-automotive:1.7.0")
}
```

`tools/cal-rotary-probe/local.properties` (git-ignored): `sdk.dir=/Users/admin/Library/Android/sdk`.

- [ ] **Step 2: Manifest**

`tools/cal-rotary-probe/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.automotive" android:required="true" />
    <uses-feature android:name="android.software.car.templates_host" android:required="true" />

    <application android:label="CAL rotary probe">

        <meta-data android:name="androidx.car.app.minCarApiLevel" android:value="1" />

        <service
            android:name=".ProbeService"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.car.app.CarAppService" />
                <category android:name="androidx.car.app.category.POI" />
            </intent-filter>
        </service>

        <activity
            android:name="androidx.car.app.activity.CarAppActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <meta-data android:name="distractionOptimized" android:value="true" />
        </activity>
    </application>
</manifest>
```

- [ ] **Step 3: Service, session, screens**

`tools/cal-rotary-probe/src/main/java/com/kanyandula/calprobe/ProbeService.kt`:

```kotlin
package com.kanyandula.calprobe

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator

private const val TAG = "ProbeNav"
private val CATEGORIES = listOf("Coffee", "Food", "Outdoors", "Family", "Scenic", "Explore")

/** Throwaway rotary probe (DN-SP-002): the Discover journey as Car App Library templates, static data only. */
class ProbeService : CarAppService() {
    // Debug probe only: any host may bind. A real app validates hosts.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = GridScreen(carContext)
    }
}

private class GridScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        CATEGORIES.forEach { category ->
            items.addItem(
                GridItem.Builder()
                    .setTitle(category)
                    .setImage(CarIcon.APP_ICON)
                    .setOnClickListener { screenManager.push(ListScreen(carContext, category)) }
                    .build(),
            )
        }
        return GridTemplate.Builder()
            .setTitle("Discover Nearby (probe)")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items.build())
            .build()
    }
}

private class ListScreen(carContext: CarContext, private val category: String) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val rows = ItemList.Builder()
        (1..5).forEach { n ->
            rows.addItem(
                Row.Builder()
                    .setTitle("$category place $n")
                    .addText("${n * 0.4} km")
                    .setOnClickListener { screenManager.push(DetailsScreen(carContext, "$category place $n")) }
                    .build(),
            )
        }
        return ListTemplate.Builder()
            .setTitle(category)
            .setHeaderAction(Action.BACK)
            .setSingleList(rows.build())
            .build()
    }
}

private class DetailsScreen(carContext: CarContext, private val name: String) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val navigate = Action.Builder()
            .setTitle("Navigate")
            .setOnClickListener {
                Log.i(TAG, "navigate selected")
                // Informational: the template-world hand-off. The stub only handles ACTION_VIEW geo:.
                runCatching {
                    carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:0,0?q=probe")))
                }.onSuccess { Log.i(TAG, "startCarApp returned") }
                    .onFailure { Log.i(TAG, "startCarApp failed: ${it.javaClass.simpleName}") }
            }
            .build()
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("1.2 km away").build())
            .addRow(Row.Builder().setTitle("4.5 ★ (120 reviews)").build())
            .addAction(navigate)
            .build()
        return PaneTemplate.Builder(pane)
            .setTitle(name)
            .setHeaderAction(Action.BACK)
            .build()
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew -p tools/cal-rotary-probe assembleDebug --console=plain -q`

Expected: BUILD SUCCESSFUL. Deprecation warnings (for example `setTitle`/`setHeaderAction` in favour of `Header` in 1.7) are acceptable for a probe; errors are not. If an API in the code above doesn't exist in 1.7.0, use the 1.7 equivalent and record a ruling.

Also run `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q` in the root. Expected: green and unchanged (142 tests), which proves the probe stayed out of the root build.

- [ ] **Step 5: README and commit**

`tools/cal-rotary-probe/README.md`:

```markdown
# Car App Library rotary probe (DN-SP-002)

Throwaway. A standalone Gradle build, not part of the root build, `:app` or CI. It exists to answer one question for ADR-002: does a Car App Library template app get a working rotary journey (grid → list → details → Navigate → Back) on the reference emulator?

- Build: `./gradlew -p tools/cal-rotary-probe assembleDebug` (needs `tools/cal-rotary-probe/local.properties` with `sdk.dir`).
- Install: `adb -s emulator-5554 install -r tools/cal-rotary-probe/build/outputs/apk/debug/cal-rotary-probe-debug.apk`
- Logs: `adb -s emulator-5554 logcat -s ProbeNav`

Delete it if ADR-002 chooses Compose.
```

```bash
git add tools/cal-rotary-probe
git commit -m "Add an isolated Car App Library rotary probe"
```

---

### Task 2: Run the rotary journey on the reference image

- [ ] **Step 1: Install and launch (Review Focus 2)**

```bash
S=…; d() { adb -s emulator-5554 shell "$@"; }
d cmd car_service inject-vhal-event 0x11400400 4
adb -s emulator-5554 install -r tools/cal-rotary-probe/build/outputs/apk/debug/cal-rotary-probe-debug.apk
d cmd package query-services -a androidx.car.app.CarAppService | grep -E "calprobe|services found"
d am start --user 10 -n com.kanyandula.calprobe/androidx.car.app.activity.CarAppActivity; "$S/pause.sh" 8
adb -s emulator-5554 exec-out screencap -p > "$S/sp002-0-launch.png"
d dumpsys activity activities | grep -m1 topResumedActivity
```

Expected: the probe's grid ("Discover Nearby (probe)" with six tiles) is on screen (`sp002-0-launch.png`), and the top resumed activity is `CarAppActivity`. If the host shows an error or nothing renders, record it verbatim and stop: that is the result.

- [ ] **Step 2: Rotary on the grid**

```bash
d cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1
for i in 1 2 3 4 5 6; do echo "turn $i: $("$S/rfocus.sh" | cut -c 1-70)"; adb -s emulator-5554 exec-out screencap -p > "$S/sp002-grid-$i.png"; d cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; done
```

Expected: each turn moves focus to the next tile, and every screenshot shows a visible focus highlight. Record the order, including whether the header's app icon takes focus.

- [ ] **Step 3: The full journey, three runs (Review Focus 1, 3, 5)**

For each run, n = 1..3:
1. Relaunch (`am start -S …CarAppActivity`).
2. Turn to "Family" and select (`inject-key 23`).
3. Turn through the list rows (screenshot each) and select row 2.
4. On details, turn until "Navigate" is focused, screenshot it, and select it.
5. Check `logcat -s ProbeNav`.
6. Press Back (`inject-key 4`): to the list, then the grid, then out of the app.

After each Back, record the focused element (`rfocus.sh`) and take a screenshot; after the third Back, record the top resumed activity.

```bash
run() {
  d am start -S --user 10 -n com.kanyandula.calprobe/androidx.car.app.activity.CarAppActivity >/dev/null; "$S/pause.sh" 8
  adb -s emulator-5554 logcat -c
  for i in 1 2 3 4; do d cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; done
  echo "grid: $("$S/rfocus.sh" | cut -c 1-70)"; d cmd car_service inject-key 23 >/dev/null; "$S/pause.sh" 3
  for i in 1 2 3; do d cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; echo "list $i: $("$S/rfocus.sh" | cut -c 1-70)"; adb -s emulator-5554 exec-out screencap -p > "$S/sp002-r$1-list-$i.png"; done
  d cmd car_service inject-key 23 >/dev/null; "$S/pause.sh" 3
  for i in 1 2 3 4; do d cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; echo "details $i: $("$S/rfocus.sh" | cut -c 1-70)"; adb -s emulator-5554 exec-out screencap -p > "$S/sp002-r$1-details-$i.png"; done
  d cmd car_service inject-key 23 >/dev/null; "$S/pause.sh" 3; adb -s emulator-5554 logcat -d -s ProbeNav | tail -2
  for b in 1 2 3; do d cmd car_service inject-key 4 >/dev/null; "$S/pause.sh" 2; echo "back $b: $("$S/rfocus.sh" | cut -c 1-70)"; adb -s emulator-5554 exec-out screencap -p > "$S/sp002-r$1-back-$b.png"; done
  d dumpsys activity activities | grep -m1 topResumedActivity
}
for n in 1 2 3; do echo "== run $n"; run $n; done
```

Expected for a pass, in all three runs:
- **Grid:** "Family" is focused after four turns.
- **List:** turns move row by row with a visible highlight.
- **Details:** a turn reaches Navigate with a visible highlight; select logs `ProbeNav: navigate selected` and the `startCarApp` outcome.
- **Back:** Back 1 → the list, Back 2 → the grid, each with focus on a visible element; Back 3 leaves the app (top resumed is not `CarAppActivity`).
- **No trap anywhere:** a turn always moves focus, or stays at a list end.

Any run that misses one of these fails the probe; record exactly where. If Navigate's selection leaves the probe (the host started something), record what came to the front, and press Back once more to return before continuing.

The four turns to "Family" assume one focus stop per tile. If Step 2 showed the header icon taking focus first, adjust the count to land on Family and record the ruling.

- [ ] **Step 4: One run in Drive (Review Focus 4)**

```bash
d cmd car_service inject-vhal-event 0x11400400 8
(adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60 > /dev/null 2>&1 &); "$S/pause.sh" 3
run drive
d cmd car_service inject-vhal-event 0x11400400 4
```

Expected: record what the host changes while moving: list row limits, disabled actions, any block. A journey that completes in Drive with fewer rows is still a pass. A host that blocks Navigate or the list while driving is recorded as a template constraint for ADR-002.

---

### Task 3: Record in ADR-002 and close out

**Files:** `docs/adr/0002-ui-stack-after-v7.md`, `CLAUDE.md`, the ticket.

- [ ] **Step 1: ADR-002 evidence.** Under "## Evidence", add a subsection "### Car App Library rotary probe (DN-SP-002, 2026-10-05)" with:
  - what was built (link `tools/cal-rotary-probe/README.md`), CAL 1.7.0, and the template host version on the image;
  - a results table, one row per run (runs 1–3, plus Drive), with columns: grid, list, details/Navigate, Back ×3, trap?, focus visible?;
  - the `ProbeNav` and `startCarApp` outcome;
  - 4–6 key screenshots copied to `docs/adr/0002/` as `5-cal-grid-focus.png`, `6-cal-list-focus.png`, `7-cal-navigate-focus.png`, plus any failure shot.

- [ ] **Step 2: Recommendation.** If all three runs (and Drive, for completion) pass:
  - Add under "## Options", Option A: "**Rotary probe: passed** (DN-SP-002) — the host gives rotary a working journey on the reference image."
  - Add a section **"## Engineering recommendation"**: "Option A, Car App Library templates. The probe shows the platform-rendered UI meets docs/02 §16 on the reference image, where the Compose UI did not (V7). This also opens the Play route (V8). The Decision line remains the Product Lead's."

  If the probe fails: record where, and add no recommendation; Options A and B stand as written. Either way, leave the Decision line as it is.

- [ ] **Step 3:** In CLAUDE.md "Current state", before "Next:", add one line: ``Car App Library rotary probe (DN-SP-002): `tools/cal-rotary-probe/`, a standalone build (not in the root build or CI), evidence for ADR-002; delete it if ADR-002 chooses Compose.`` Commit `Record the Car App Library rotary probe in ADR-002` with the docs and screenshots.

- [ ] **Step 4: Close out.**
  1. Write the ticket's completion notes: the results table, the rulings and the recommendation.
  2. Run the root `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`.
  3. Push and open a draft PR.
  4. Do the final whole-branch review (executing-plans); no simplify (Open decision 5).
  5. Write the PR description with the `pr-description` skill.
  6. Run `gh pr ready` once CI is green.
