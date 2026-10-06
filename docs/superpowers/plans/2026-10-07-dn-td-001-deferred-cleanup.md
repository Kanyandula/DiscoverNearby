# DN-TD-001 Deferred Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clear the small, non-behavioural items deferred from DN-M0-011, DN-UX-001 and earlier tickets: comments,
one colour parameter, import order, two SDK numbers in the version catalog, a test fixture's home, a testable
Settings-return helper, and CLAUDE.md.

**Architecture:** No behaviour changes. Each task is a refactor guarded by an existing test, or by a new test for
code that had none (the return from Settings). Rotary code (`ReturnFocus`, `focusRing`) is untouched apart from
test comments.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric 4.17, Compose UI test (`v2` rules), Gradle version catalog.

**Spec:** the ticket `~/.claude/projects/Discover Nearby/tickets/DN-TD-001-deferred-cleanup.md` (acceptance
criteria and "Not in scope"), plus CLAUDE.md's architecture rules.

## Global Constraints

- Rotary behaviour does not change (ADR-002). `ReturnFocus.kt` and `FocusRing.kt` are not edited.
- Not in scope: `ReturnFocus.selected` vs NavHost's `isTop` guard, `composed {}` → `Modifier.Node`, and remembering a
  permanent refusal across visits.
- Only `ui/` imports Compose. All user text stays in strings.xml (this ticket adds none).
- detekt 1.23.8 with `maxIssues: 0`: MaxLineLength 120, LongMethod 60.
- Import layout (Android Studio's Kotlin default): every other import in ASCII order, then `java.*`, `javax.*`,
  `kotlin.*`. `kotlinx.*` is "every other".
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes at the end.
- `.idea/vcs.xml` stays an uncommitted local change.
- No AI attribution in commits or the PR.

## Review Focus

1. **The app's UI is recreated while Settings is in front** (process death in the background). Expect the search
   to run once on return. Pinned by `AppSettingsTest.recreatedWhileInSettingsStillRunsOnReturn` (Task 4).
2. **Open Settings fails** (nothing can show the page). Expect no search on a later resume. Pinned by
   `AppSettingsTest.settingsThatDidNotOpenRunNothingOnReturn` (Task 4).
3. **A later, unrelated resume** (back from the navigation app). Expect no second search. Pinned by the second
   resume in `AppSettingsTest.returnFromSettingsRunsOnReturnOnce` (Task 4).
4. **The message buttons' ring colours.** Expect them unchanged: light on Try Again/Grant, Accent on Back. Pinned by
   `FocusRingTest.focusedMessageButtonsShowTheirRings`, with a swap check that proves it catches a change (Task 1).
5. **Installing over the previous build,** for both APKs. Expect minSdk 29, targetSdk 36 and versionCode 1, as now.
   Pinned by the `aapt2 dump badging` comparison (Task 3).

---

## File structure

| File | Change | Task |
| --- | --- | --- |
| `app/src/main/java/com/kanyandula/discovernearby/ui/theme/Dimens.kt` | focus-ring comment | 1 |
| `app/src/main/java/com/kanyandula/discovernearby/ui/components/MessageState.kt` | `MessageButton(ring = …)` | 1 |
| `app/src/test/java/com/kanyandula/discovernearby/ui/ReturnFocusTest.kt` | comments, one test name | 1 |
| `GeoPoint.kt`, `DiscoverScreen.kt`, `RecommendationsScreen.kt`, `DiscoverNearbyThemeTest.kt`, `FocusRingTest.kt` | import order | 2 |
| `app/src/test/java/com/kanyandula/discovernearby/navigation/TestPoints.kt` | Create: `HARBOUR_ROASTERS` | 2 |
| `app/src/test/java/com/kanyandula/discovernearby/navigation/GeoUriTest.kt` | fixture moved out | 2 |
| `gradle/libs.versions.toml`, `app/build.gradle.kts`, `tools/stub-navigation/build.gradle.kts` | SDK numbers, comment | 3 |
| `app/src/main/java/com/kanyandula/discovernearby/ui/AppSettings.kt` | `rememberOpenAppSettings`, KDoc | 4 |
| `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt` | uses it | 4 |
| `app/src/test/java/com/kanyandula/discovernearby/ui/AppSettingsTest.kt` | compose rule, 4 tests | 4 |
| `docs/02-discover-nearby-ux-interaction-spec.md` | §10 wording | 4 |
| `CLAUDE.md` | rewrap, current state, Next | 5 |

---

### Task 1: Rotary comments and the message buttons' ring colour

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/theme/Dimens.kt:41-42`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/components/MessageState.kt:70-90`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/ReturnFocusTest.kt:23,78-82`
- Test: `app/src/test/java/com/kanyandula/discovernearby/ui/components/FocusRingTest.kt` (existing, unchanged)

**Interfaces:**
- Consumes: nothing.
- Produces: `private fun MessageButton(@StringRes label: Int, onClick: () -> Unit, container: Color, content: Color, ring: Color)`; private, nothing else uses it.

- [ ] **Step 1: Run the guard test first**

Run: `./gradlew :app:testDebugUnitTest --tests '*FocusRingTest' --console=plain`
Expected: PASS (6 tests).

- [ ] **Step 2: Pass the ring colour from the call sites**

In `MessageState.kt`, replace the button row:

```kotlin
        Row(modifier = Modifier.padding(top = ButtonGap), horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
            if (onPrimary != null) MessageButton(primaryLabel, onPrimary, container = Action, content = Color.White)
            MessageButton(backLabel, onBack, container = Raised, content = MaterialTheme.colorScheme.onSurface)
        }
```

with:

```kotlin
        Row(modifier = Modifier.padding(top = ButtonGap), horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
            // The Action-blue primary gets a light ring; an Accent ring would barely show on it.
            if (onPrimary != null) {
                MessageButton(primaryLabel, onPrimary, container = Action, content = Color.White, ring = OnSurface)
            }
            MessageButton(
                backLabel,
                onBack,
                container = Raised,
                content = MaterialTheme.colorScheme.onSurface,
                ring = Accent,
            )
        }
```

and the button itself:

```kotlin
@Composable
private fun MessageButton(@StringRes label: Int, onClick: () -> Unit, container: Color, content: Color) {
    val shape = RoundedCornerShape(ButtonRadius)
    Button(
        onClick = onClick,
        // A primary (Action blue) button gets a light ring; an Accent ring would barely show on it.
        modifier = Modifier
            .focusRing(shape, if (container == Action) OnSurface else Accent)
```

with:

```kotlin
@Composable
private fun MessageButton(@StringRes label: Int, onClick: () -> Unit, container: Color, content: Color, ring: Color) {
    val shape = RoundedCornerShape(ButtonRadius)
    Button(
        onClick = onClick,
        modifier = Modifier
            .focusRing(shape, ring)
```

- [ ] **Step 3: Prove the guard catches a colour change**

Temporarily swap the two values at the call sites (`ring = Accent` on the primary, `ring = OnSurface` on Back).
Run: `./gradlew :app:testDebugUnitTest --tests '*FocusRingTest' --console=plain`
Expected: FAIL in `focusedMessageButtonsShowTheirRings` (the colour assertion). Swap them back.

- [ ] **Step 4: Fix the Dimens comment and its citation**

In `Dimens.kt`, replace:

```kotlin
// Rotary focus (docs/02 §16): a 4 px primary outline on the focused element.
val FocusRingWidth = 4.dp
```

with:

```kotlin
// Rotary focus ring (DN-M0-011): a 4.dp outline in Accent, or light (OnSurface) on Action-blue fills. docs/02 §16
// asks only for a clearly visible indicator; the width and colours are the app's choice.
val FocusRingWidth = 4.dp
```

- [ ] **Step 5: Fix ReturnFocusTest's stale comments**

Line 23, replace:

```kotlin
// V7 (ADR-002): Back gives rotary focus back to the item selected by rotary, a frame after the screen returns.
```

with:

```kotlin
// V7 (ADR-002): Back gives rotary focus back to the item selected by rotary, once ReturnFocus's wait has passed.
```

And the touch test, replace:

```kotlin
    @Test
    fun touchSelectionReplacesARotarySelection() {
        showItems()
        rotarySelect("B")
        rule.onNodeWithText("C").performClick() // touch: switches to touch mode, so nothing is remembered
```

with:

```kotlin
    @Test
    fun touchOnAnUnfocusedItemForgetsTheRotarySelection() {
        showItems()
        rotarySelect("B")
        rule.onNodeWithText("C").performClick() // a touch on C, which isn't focused (B is): nothing is remembered
```

- [ ] **Step 6: Run the task's tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*FocusRingTest' --tests '*ReturnFocusTest' --tests '*RotaryContractTest' --console=plain`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/theme/Dimens.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/components/MessageState.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/ReturnFocusTest.kt
git commit -m "Pass the message buttons' ring colour; fix rotary comments"
```

---

### Task 2: Import order and the HARBOUR_ROASTERS fixture

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/model/GeoPoint.kt`,
  `app/src/main/java/com/kanyandula/discovernearby/ui/screens/DiscoverScreen.kt`,
  `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt`,
  `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNearbyThemeTest.kt`,
  `app/src/test/java/com/kanyandula/discovernearby/ui/components/FocusRingTest.kt` (imports only)
- Create: `app/src/test/java/com/kanyandula/discovernearby/navigation/TestPoints.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/navigation/GeoUriTest.kt:8-9`

**Interfaces:**
- Consumes: nothing.
- Produces: `internal val HARBOUR_ROASTERS: GeoPoint` in package `com.kanyandula.discovernearby.navigation`
  (test source set), same name and value as today.

- [ ] **Step 1: Run the import check and see it fail**

```bash
check_imports() {
  for f in $(git ls-files '*.kt'); do
    grep '^import ' "$f" > /tmp/dn-actual
    awk '{k=0} /^import java\./{k=1} /^import javax\./{k=2} /^import kotlin\./{k=3} {print k" "$0}' /tmp/dn-actual \
      | LC_ALL=C sort | cut -d' ' -f2- > /tmp/dn-expected
    diff -q /tmp/dn-actual /tmp/dn-expected > /dev/null || echo "UNSORTED $f"
  done
}
check_imports
```

Expected: five `UNSORTED` lines: `GeoPoint.kt`, `DiscoverScreen.kt`, `RecommendationsScreen.kt`,
`DiscoverNearbyThemeTest.kt`, `FocusRingTest.kt`.

- [ ] **Step 2: Sort those five import blocks**

Each block is contiguous. This rewrites only the import lines:

```bash
python3 - <<'PY'
files = [
    "app/src/main/java/com/kanyandula/discovernearby/model/GeoPoint.kt",
    "app/src/main/java/com/kanyandula/discovernearby/ui/screens/DiscoverScreen.kt",
    "app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt",
    "app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNearbyThemeTest.kt",
    "app/src/test/java/com/kanyandula/discovernearby/ui/components/FocusRingTest.kt",
]
def key(line):
    name = line[len("import "):]
    group = 1 if name.startswith("java.") else 2 if name.startswith("javax.") else 3 if name.startswith("kotlin.") else 0
    return (group, line)
for path in files:
    lines = open(path).read().split("\n")
    idx = [i for i, l in enumerate(lines) if l.startswith("import ")]
    assert idx == list(range(idx[0], idx[-1] + 1)), path + ": imports not contiguous"
    lines[idx[0]:idx[-1] + 1] = sorted(lines[idx[0]:idx[-1] + 1], key=key)
    open(path, "w").write("\n".join(lines))
PY
check_imports
```

Expected: `check_imports` prints nothing. `git diff --stat` shows only those five files, each with as many lines
added as removed.

- [ ] **Step 3: Move the fixture to its own file**

Create `app/src/test/java/com/kanyandula/discovernearby/navigation/TestPoints.kt`:

```kotlin
package com.kanyandula.discovernearby.navigation

import com.kanyandula.discovernearby.model.GeoPoint

// Harbour Roasters, Greystones in the fake data: TestLocation.GREYSTONES + its (0.004, 0.003) offset (FakePlaces).
internal val HARBOUR_ROASTERS = GeoPoint(53.148, -6.0603)
```

In `GeoUriTest.kt`, delete these two lines and the blank line after them:

```kotlin
// Harbour Roasters, Greystones in the fake data: TestLocation.GREYSTONES + its (0.004, 0.003) offset (FakePlaces).
internal val HARBOUR_ROASTERS = GeoPoint(53.148, -6.0603)
```

`GeoUriTest` keeps its `GeoPoint` import (`roundsToSixDecimalPlaces` uses it).

- [ ] **Step 4: Run the affected tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*GeoUriTest' --tests '*IntentNavigationLauncherTest' --tests '*FocusRingTest' --tests '*DiscoverNearbyThemeTest' --tests '*DiscoverScreenTest' --tests '*RecommendationsScreenTest' --tests '*GeoPointTest' --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/model/GeoPoint.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/screens/DiscoverScreen.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNearbyThemeTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/components/FocusRingTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/navigation/TestPoints.kt \
  app/src/test/java/com/kanyandula/discovernearby/navigation/GeoUriTest.kt
git commit -m "Sort imports; give HARBOUR_ROASTERS its own fixture file"
```

---

### Task 3: minSdk and targetSdk in the version catalog

**Files:**
- Modify: `gradle/libs.versions.toml:2-3`
- Modify: `app/build.gradle.kts:19-20`
- Modify: `tools/stub-navigation/build.gradle.kts:15-17`

**Interfaces:**
- Consumes: nothing.
- Produces: catalog versions `android-minSdk = "29"` and `android-targetSdk = "36"`
  (`libs.versions.android.minSdk`, `libs.versions.android.targetSdk`). Task 5's CLAUDE.md text names them.

- [ ] **Step 1: Record the APKs' current SDK levels**

```bash
./gradlew assembleDebug --console=plain -q
BT=$(ls -d ~/Library/Android/sdk/build-tools/* | tail -1)
badging() {
  for a in app/build/outputs/apk/debug/app-debug.apk \
      tools/stub-navigation/build/outputs/apk/debug/stub-navigation-debug.apk; do
    $BT/aapt2 dump badging "$a" | grep -oE "^package: name='[^']*' versionCode='[^']*'|^(minSdk|targetSdk)Version:'[^']*'"
  done
}
badging > /tmp/dn-badging-before.txt; cat /tmp/dn-badging-before.txt
```

Expected: for each package, `versionCode='1'`, `minSdkVersion:'29'` and `targetSdkVersion:'36'`.

- [ ] **Step 2: Move the numbers into the catalog**

In `gradle/libs.versions.toml`, replace:

```toml
# The one compile SDK for every module (CLAUDE.md); CI installs this platform.
android-compileSdk = "37"
```

with:

```toml
# The one compile SDK for every module (CLAUDE.md); CI installs this platform.
android-compileSdk = "37"
android-minSdk = "29"
android-targetSdk = "36"
```

In `app/build.gradle.kts`, replace:

```kotlin
        minSdk = 29
        targetSdk = 36
```

with:

```kotlin
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
```

In `tools/stub-navigation/build.gradle.kts`, replace:

```kotlin
        minSdk = 29
        targetSdk = 36
        // Unset, the version code is 0 and installs over an earlier stub fail as a downgrade.
        versionCode = 1
```

with:

```kotlin
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Without it the version code is 0, and installing over an earlier stub fails as a downgrade.
        versionCode = 1
```

- [ ] **Step 3: Compare the rebuilt APKs**

```bash
./gradlew assembleDebug --console=plain -q
badging > /tmp/dn-badging-after.txt; diff /tmp/dn-badging-before.txt /tmp/dn-badging-after.txt && echo same
```

Expected: `same`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts tools/stub-navigation/build.gradle.kts
git commit -m "Take minSdk and targetSdk from the version catalog"
```

---

### Task 4: A testable return from Settings

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/AppSettings.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt` (imports, `rememberLocationActions`)
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/AppSettingsTest.kt`
- Modify: `docs/02-discover-nearby-ux-interaction-spec.md` (§10, "Permission denied permanently")

**Interfaces:**
- Consumes: `internal fun openAppSettings(context: Context): Boolean` (AppSettings.kt, unchanged).
- Produces: `@Composable internal fun rememberOpenAppSettings(onReturn: () -> Unit): () -> Unit` in package
  `com.kanyandula.discovernearby.ui`. Task 5's CLAUDE.md text names it.

- [ ] **Step 1: Write the failing tests**

Replace the top of `AppSettingsTest.kt`, from `package` down to the `activity` property, so the class uses a compose
rule's activity:

```kotlin
package com.kanyandula.discovernearby.ui

import android.Manifest
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.kanyandula.discovernearby.location.LOCATION_PERMISSIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val activity: ComponentActivity get() = rule.activity
```

The existing tests stay as they are. Add these before the closing brace, next to `refused`:

```kotlin
    private var returns = 0
    private lateinit var openSettings: () -> Unit

    private fun showOpenSettings() = rule.setContent {
        openSettings = rememberOpenAppSettings(onReturn = { returns++ })
    }

    /** The app goes behind Settings and comes back to the front, as Back from App info does. */
    private fun comeBack() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
    }

    // Review Focus 3: the second resume is unrelated (e.g. back from the navigation app), so nothing runs again.
    @Test
    fun returnFromSettingsRunsOnReturnOnce() {
        showOpenSettings()
        rule.runOnIdle { openSettings() }
        comeBack()
        assertEquals(1, returns)
        comeBack()
        assertEquals(1, returns)
    }

    @Test
    fun aResumeWithoutSettingsRunsNothing() {
        showOpenSettings()
        comeBack()
        assertEquals(0, returns)
    }

    // Review Focus 2: Settings didn't open, so the next resume isn't a return from it.
    @Test
    fun settingsThatDidNotOpenRunNothingOnReturn() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        showOpenSettings()
        rule.runOnIdle { openSettings() }
        comeBack()
        assertEquals(0, returns)
    }

    // Review Focus 1: the UI is rebuilt from saved state while Settings is in front (e.g. process death).
    @Test
    fun recreatedWhileInSettingsStillRunsOnReturn() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { openSettings = rememberOpenAppSettings(onReturn = { returns++ }) }
        rule.runOnIdle { openSettings() }
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        restoration.emulateSavedInstanceStateRestore()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        assertEquals(1, returns)
    }
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppSettingsTest' --console=plain`
Expected: compilation FAILS with `Unresolved reference 'rememberOpenAppSettings'`.

- [ ] **Step 3: Add `rememberOpenAppSettings` to AppSettings.kt**

Add these imports, keeping the block sorted (Task 2's layout):

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
```

Add after `openAppSettings`:

```kotlin
/**
 * Open Settings for a composable: opens [appSettingsIntent], and runs [onReturn] once when the app resumes after it,
 * in case location was allowed there. Nothing runs on return if Settings didn't open. The flag is saveable, so a
 * UI rebuilt from saved state while Settings is in front still runs [onReturn].
 */
@Composable
internal fun rememberOpenAppSettings(onReturn: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var opened by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (opened) {
            opened = false
            onReturn()
        }
    }
    return { opened = openAppSettings(context) }
}
```

And widen `refusedForGood`'s KDoc to cover Android 10, replacing:

```kotlin
 * location permission (Android stops asking after two refusals). An empty result is a cancelled or overlapping
 * request, not a refusal.
```

with:

```kotlin
 * location permission (after two refusals on Android 11+, or "Deny & don't ask again" on Android 10). An empty
 * result is a cancelled or overlapping request, not a refusal.
```

- [ ] **Step 4: Use it in DiscoverNavHost**

Replace `rememberLocationActions`'s body after the permissions launcher:

```kotlin
    val context = LocalContext.current
    var openedSettings by rememberSaveable { mutableStateOf(false) }
    // Back from Settings: search again, in case location was allowed there.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (openedSettings) {
            openedSettings = false
            viewModel.retry()
        }
    }
    return LocationActions(
        grant = { permissions.launch(LOCATION_PERMISSIONS) },
        openSettings = { openedSettings = openAppSettings(context) },
    )
```

with:

```kotlin
    return LocationActions(
        grant = { permissions.launch(LOCATION_PERMISSIONS) },
        openSettings = rememberOpenAppSettings(onReturn = viewModel::retry),
    )
```

Then delete the imports DiscoverNavHost no longer uses:

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
```

Keep `getValue` (the `by` state delegates) and `remember` (Place Details' route).

- [ ] **Step 5: Run the tests and see them pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppSettingsTest' --console=plain`
Expected: PASS (9 tests).

- [ ] **Step 6: Prove the tests catch a broken return**

Temporarily replace `onReturn()` in `rememberOpenAppSettings` with `Unit`.
Run: `./gradlew :app:testDebugUnitTest --tests '*AppSettingsTest' --console=plain`
Expected: FAIL in `returnFromSettingsRunsOnReturnOnce` and `recreatedWhileInSettingsStillRunsOnReturn`.
Restore `onReturn()`.

- [ ] **Step 7: Widen docs/02 §10's wording**

In `docs/02-discover-nearby-ux-interaction-spec.md`, under "### Permission denied permanently", replace:

```text
After the user has refused twice, Android no longer shows the permission dialog. While parked, Settings takes
Grant's place (DN-UX-001):
```

with:

```text
Once Android stops showing the permission dialog (after two refusals on Android 11 and later, or "Deny & don't ask
again" on Android 10), Settings takes Grant's place while parked (DN-UX-001):
```

- [ ] **Step 8: Run the import check and the related tests**

Run the import check (the same function as Task 2, Step 1):

```bash
check_imports() {
  for f in $(git ls-files '*.kt'); do
    grep '^import ' "$f" > /tmp/dn-actual
    awk '{k=0} /^import java\./{k=1} /^import javax\./{k=2} /^import kotlin\./{k=3} {print k" "$0}' /tmp/dn-actual \
      | LC_ALL=C sort | cut -d' ' -f2- > /tmp/dn-expected
    diff -q /tmp/dn-actual /tmp/dn-expected > /dev/null || echo "UNSORTED $f"
  done
}
check_imports
```

Expected: no output. Then run:
`./gradlew :app:testDebugUnitTest --tests '*AppSettingsTest' --tests '*DiscoverNavigationTest' --tests '*Recommendations*' --tests '*PermissionGrantTest' --console=plain`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/AppSettings.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/AppSettingsTest.kt \
  docs/02-discover-nearby-ux-interaction-spec.md
git commit -m "Test the return from Settings through rememberOpenAppSettings"
```

---

### Task 5: CLAUDE.md

**Files:**
- Modify: `CLAUDE.md` (the "Current state" section, two Commands/Emulator bullets)

**Interfaces:**
- Consumes: Task 3's catalog keys (`android-minSdk`, `android-targetSdk`), Task 4's `rememberOpenAppSettings`.
- Produces: nothing.

- [ ] **Step 1: See the long lines**

Run: `awk 'length > 120 {print FILENAME":"NR": "length}' CLAUDE.md`
Expected: five lines (24, 25, 29, 115, 124).

- [ ] **Step 2: Replace the "Current state" section**

Replace everything from `## Current state of the code (read this first)` up to, but not including,
`## Stack (Revision 4)` with:

```markdown
## Current state of the code (read this first)
Single Compose `:app` module (DN-M0-009): the M0 fake-data flow is implemented across Discover, Recommendations,
Place Details and the `geo:` navigation handoff; location permission and UX-restriction handling are also in place.
The stub navigation app is separate. Every actionable control shows the rotary focus ring (`focusRing`); V7 failed
its clean re-test (2026-10-06); ADR-002 keeps Compose with a product waiver, and M0 exited under it (docs/05 §9).
The activity is declared `distractionOptimized`, with `ManifestContractTest`. compileSdk 37, minSdk 29 and
targetSdk 36 are in `gradle/libs.versions.toml` (`android-compileSdk`, `android-minSdk`, `android-targetSdk`); the
app derives its `android.car.jar` path from compileSdk. No Car App Library; do not add its APIs. detekt + lint + CI
in place (DN-M0-012, DN-M0-001).

- **Baseline (DN-M0-001):** `DiscoverApplication` → `AppContainer`, canvas theme (`ui/theme`), Robolectric 4.17 at
  SDK 36, `ArchitectureRulesTest`.
- **Discover grid and navigation (DN-M0-002):** `DiscoverNavHost` and in-app Back (AOSP car bar has none).
- **Domain model, `CategoryConfigs` and fakes (DN-M0-003):** `model/`, `places/` (+ `fake/`), `location/`
  (+ `fake/`), wired in `AppContainer`.
- **UX restrictions (DN-M0-010):** `CarDrivingRestrictions`; unknown = restrictions apply; emulator moving limit 21.
- **Recommendations on fake data (DN-M0-004):** `RecommendationEngine`, `DiscoverUseCase`,
  `RecommendationsViewModel` (collects the driving state; Back switches without a fade); debug launches take
  `--es scenario <FakeScenario>`.
- **Place Details (DN-M0-005):** rows open `PlaceDetailsRoute(place, distanceMeters)` (JSON route via
  `JsonNavType`); `PlaceDetailsViewModel` falls back to the summary; Navigate hands off through
  `IntentNavigationLauncher` (DN-M3-001: `ACTION_VIEW geo:`, failure → `NavigationUnavailable`).
- **Location (DN-M0-006):** `AndroidLocationProvider` (GPS/network, 8 s fix timeout; approximate-only uses the
  platform's recent coarse fix), Grant only while restrictions allow it, denied copy; emulator location via
  `adb emu geo fix`, location on for user 10, `pm clear --user 10`.
- **UI follow-ups (DN-UX-001):** `ParkToSee` when the driving limit allows no results; after a permanent refusal
  (`Denial.PERMANENT`), Open Settings replaces Grant while restrictions allow it, and the return from Settings
  searches again (`rememberOpenAppSettings`).
- **Rotary (DN-M0-011):** `focusRing` on every actionable control; `ReturnFocus` (after Back, rotary focus returns
  to the item selected by rotary once 250 ms have passed; the wait rests on a working hypothesis about Compose's
  semantics snapshot); `RotaryContractTest`. ADR-002 (Product Lead, 2026-10-06, after the re-test): **Compose for
  the emulator POC, with a product waiver**.
- **Stub navigation app (DN-M0-008):** module `:stub-navigation` in `tools/stub-navigation/`, a `geo:` VIEW handler
  (`singleTask`, distractionOptimized) that shows the URI and logs `StubNav: received geo:…`.
- **Smoke baseline (DN-M0-007):** reference configuration in docs/04 §2, the M0 smoke in docs/04 §10 (re-run it
  when the image or UI changes); the Robolectric smoke test is `DiscoverScreenTest`.
- **Car App Library rotary probe (DN-SP-002):** removed in DN-M0-015 once ADR-002 kept Compose; its evidence is in
  `docs/adr/0002/`, its source in the DN-SP-002 plan and git history.

V7 **failed** its clean re-test after the one bounded fix (2026-10-06):
- Navigate reached and activated 4/4; visible focus everywhere; no lost turn after Back to Details or Back to
  Recommendations.
- In 1 of 4 runs, after Back to Discover, the service stayed on the host and the turn jumped to Coffee.

E1's pre-registered classification was inconclusive; its run pattern strongly implicated launch-time
`uiautomator` polling but did not prove the underlying cause. The gate stays: rotation reaches Navigate, select
activates the focused control, Back loses no turn, visible focus on every actionable control; controller rotation
on Android 13 only. V7 stays recorded as failed. The waiver accepts that Back to Discover jump as a known
limitation for this iteration, and M0 exits under it. Further rotary changes need a new decision.
Record: `docs/adr/0002/v7-retest-2026-10-06/`.

Next: no build ticket is ready. DN-SP-001 phase 2 waits on the permitted evaluations, the terms and dev-only keys
(product evaluation owner: the Product Lead; Legal sign-off separate); M1 waits on ADR-001. DN-TD-002 (Gradle/CI
tuning) is P3.

```

- [ ] **Step 3: Rewrap the two long bullets**

In "## Commands", replace:

```markdown
- CI runs `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` on every PR; the `build` check is required on `main`.
```

with:

```markdown
- CI runs `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` on every PR; the `build` check is required
  on `main`.
```

In "## Emulator", replace:

```markdown
- Distraction-optimised check: `adb -s emulator-5554 shell cmd car_service get-do-activities com.kanyandula.discovernearby`
```

with:

```markdown
- Distraction-optimised check:
  `adb -s emulator-5554 shell cmd car_service get-do-activities com.kanyandula.discovernearby`
```

- [ ] **Step 4: Check the result**

Run: `awk 'length > 120 {print FILENAME":"NR": "length}' CLAUDE.md`
Expected: no output.
Read the new section once: every fact from the old section is still there, and no line outside a list item starts
with spaces.

- [ ] **Step 5: Full check and commit**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

```bash
git add CLAUDE.md
git commit -m "Rewrap CLAUDE.md and record DN-UX-001 in its current state"
```

---

## After the tasks

- The final whole-branch review on the most capable model (executing-plans), then the `simplify` skill on the
  branch's diff.
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`, then push and open the PR with `pr-description`:
  DN-TD-001 and its acceptance criteria.
- Step 6 after merge: verify MERGED in its own call before deleting the branch.
