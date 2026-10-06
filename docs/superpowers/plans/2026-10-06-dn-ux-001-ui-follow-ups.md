# DN-UX-001 UI Follow-ups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the three product decisions of 2026-10-05:
- a parked-retry message when the driving list limit is 0;
- the timeout wording in docs/02 matching the mockup;
- a Settings recovery path after a permanent location denial, offered only while parked.

**Architecture:** All three are states of the one Recommendations destination (docs/02 §6).
- `RecommendationsViewModel` gains a `ParkToSee` state, and remembers whether a refusal was permanent.
- `RecommendationsScreen` draws both through the existing `MessageState`.
- `DiscoverNavHost` detects a permanent refusal (no rationale after a denial), opens the app's settings page, and looks again on return.
- The timeout copy already matches `docs/design/10-timeout.png` in code and tests, so that item only updates docs/02 §12.

**Tech Stack:**
- Kotlin and Compose (Material 3); `androidx.activity.compose.LocalActivity` (activity-compose 1.13); `androidx.lifecycle.compose.LifecycleResumeEffect` (lifecycle 2.11).
- `ActivityCompat.shouldShowRequestPermissionRationale`; `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
- Tests: JUnit, coroutines-test, Robolectric 4.17, Compose UI test.

**Spec:**
- The ticket `~/.claude/projects/Discover Nearby/tickets/DN-UX-001-ui-follow-ups.md`.
- docs/02 §10 (Permission Required), §12 (Error/Timeout), §17 (Park vs Drive).
- `docs/design/10-timeout.png`.
- ADR-002 Decision (2026-10-06): Compose, with a product waiver; no rotary behaviour changes.

## Global Constraints

- **Copy:** all user text goes in `app/src/main/res/values/strings.xml`.
- **Destinations:** loading, content and error are states of ONE destination, never pushed destinations (docs/02 §6).
- **The Settings action only while parked:** offered only when `DrivingState.distractionOptimizationRequired == false`. While driving the denied message has no Settings action (ticket AC). Never call this "parked" in code: it reports UX restrictions, not the gear.
- **Imports:** only `ui/` imports Compose; `discovery/`, `model/`, `places/` and `location/` have no Compose or Car API imports.
- **Rotary:** no change to rotary behaviour (`ReturnFocus`, the restore timing). The new buttons get the focus ring through `MessageState` automatically (ADR-002 waiver).
- **Checks:** `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes; detekt `maxIssues: 0`, MaxLineLength 120.
- **Workflow:** branch `dn-ux-001-ui-follow-ups` from an updated `main`; never commit on `main`; no AI attribution in commits or PRs; commit subjects ≤ 72 characters.
- **Emulator:** always `adb -s emulator-5554`; never `adb reboot`; driver user 10.

## Review Focus

1. **Results arrive while the limit is 0, then the driver parks.** Expect the places to appear without a tap, and with no new request. Pinned by `RecommendationsViewModelTest.listLimitZeroAsksToParkAndParkingShowsTheResults`.
2. **No places at all while the limit is 0.** Expect the Empty message, not "park to see". Pinned by `noPlacesUnderALimitOfZeroIsEmpty`.
3. **Back from Settings with location still off.** Expect the Settings message again, with no loop and no dialog. Pinned by `retryAfterAPermanentRefusalStillOffersSettings`.
4. **Permanent refusal, then the car starts moving.** Expect the Settings action to disappear and the park-first copy to show. Pinned by `RecommendationsScreenTest.permanentRefusalOffersSettingsOnlyWhileRequestsAreAllowed`.
5. **No app on the system can show the app's settings.** Expect no crash; nothing happens. Pinned by `AppSettingsTest.openingWithNothingToShowItReturnsFalse`.

---

## File structure

| File | Change | Responsibility |
| --- | --- | --- |
| `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsViewModel.kt` | Modify | `ParkToSee` state; permanent-refusal memory |
| `…/ui/screens/RecommendationsScreen.kt` | Modify | Draw `ParkToSee`; the Settings action; new `onOpenSettings` |
| `…/ui/AppSettings.kt` | Create | `appSettingsIntent`, `openAppSettings`, `neverAsksAgain` |
| `…/ui/DiscoverNavHost.kt` | Modify | Permanent-refusal detection, opening Settings, retry on return |
| `app/src/main/res/values/strings.xml` | Modify | 4 strings |
| `app/src/test/java/…/ui/screens/RecommendationsViewModelTest.kt` | Modify | ViewModel tests |
| `…/ui/screens/RecommendationsScreenTest.kt` | Modify | Screen tests (and the new parameter) |
| `…/ui/screens/PermissionGrantTest.kt` | Modify | New parameter only |
| `app/src/test/java/…/ui/AppSettingsTest.kt` | Create | Helper tests |
| `docs/02-discover-nearby-ux-interaction-spec.md` | Modify | §10 Settings recovery, §12 timeout wording, §17 limit 0 |
| `docs/03-discover-nearby-engineering-implementation-plan.md` | Modify | §7 permission notes, §15 state sketch |

(`…` = `app/src/main/java/com/kanyandula/discovernearby` or the test equivalent.)

**Proposed copy** (no mockup exists for these two states):
- `park_to_see_title`: "Park to see places"
- `park_to_see_body`: "Results can't be shown while driving. Park the vehicle, then try again."
- `open_settings`: "Open Settings"
- `permission_body_settings`: "Location access is off for Discover Nearby. Turn it on in Settings."

---

### Task 1: Park to see places (list limit 0)

**Files:**
- Modify: `…/ui/screens/RecommendationsViewModel.kt`, `…/ui/screens/RecommendationsScreen.kt`, `app/src/main/res/values/strings.xml`, `docs/02-…` §17, `docs/03-…` (the §15 state sketch)
- Test: `…/ui/screens/RecommendationsViewModelTest.kt`, `…/ui/screens/RecommendationsScreenTest.kt`

**Interfaces:**
- Produces: `RecommendationsUiState.ParkToSee` (`data object`), drawn by `RecommendationsScreen` with Try Again (→ `onRetry`) and Back.

- [ ] **Step 1: Write the failing tests**

In `RecommendationsViewModelTest.kt`, add (import `com.kanyandula.discovernearby.ui.screens.RecommendationsUiState.ParkToSee` alongside the existing state imports):

```kotlin
    // DN-UX-001: places were found, but the driving list limit allows none, so ask to park. Parking shows them
    // without a new request.
    @Test
    fun listLimitZeroAsksToParkAndParkingShowsTheResults() = runTest {
        limitTo(0)
        places.reply = { cafes(7) }
        val vm = collected()
        assertEquals(ParkToSee, vm.uiState.value)
        limitTo(null)
        runCurrent()
        assertEquals(CategoryConfigs.getValue(COFFEE).desiredResults, vm.shown.size)
        assertEquals(1, places.searches)
    }

    // Review Focus 2: no places at all is still the Empty message, whatever the limit.
    @Test
    fun noPlacesUnderALimitOfZeroIsEmpty() = runTest {
        limitTo(0)
        places.reply = { emptyList() }
        assertEquals(Empty, collected().uiState.value)
    }

    // Ticket AC: Try Again works once parked.
    @Test
    fun retryOnceParkedShowsTheResults() = runTest {
        limitTo(0)
        places.reply = { cafes(3) }
        val vm = collected()
        limitTo(null)
        vm.retry()
        runCurrent()
        assertEquals(listOf("p0", "p1", "p2"), vm.shown)
    }

    @Test
    fun retryWhileTheLimitIsStillZeroAsksToParkAgain() = runTest {
        limitTo(0)
        places.reply = { cafes(3) }
        val vm = collected()
        vm.retry()
        runCurrent()
        assertEquals(ParkToSee, vm.uiState.value)
        assertEquals(2, places.searches)
    }
```

In `RecommendationsScreenTest.kt`, add:

```kotlin
    // DN-UX-001: a list limit of 0 shows a park-first message with Try Again and Back.
    @Test
    fun listLimitZeroAsksToPark() {
        state = ParkToSee
        rule.onNodeWithText("Park to see places").assertIsDisplayed()
        rule.onNodeWithText("Results can't be shown while driving. Park the vehicle, then try again.").assertIsDisplayed()
        rule.onNodeWithText("Try Again").performClick()
        assertEquals(1, retries)
        rule.onNodeWithText("Back").assertIsDisplayed()
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsViewModelTest' --tests '*RecommendationsScreenTest' --console=plain -q 2>&1 | grep -E "e: |FAILED" | head -5`

Expected: compilation fails with `Unresolved reference 'ParkToSee'`.

- [ ] **Step 3: Implement**

`RecommendationsViewModel.kt`, in `RecommendationsUiState`, after `Empty`:

```kotlin
    /** Places were found, but the driving list limit allows none to be shown (docs/02 §17). */
    data object ParkToSee : RecommendationsUiState
```

In `toUiState`, replace the `is DiscoverResult.Success` branch:

```kotlin
        is DiscoverResult.Success -> {
            val shown = result.recommendations.take(visibleCount(driving))
            when {
                result.recommendations.isEmpty() -> RecommendationsUiState.Empty
                shown.isEmpty() -> RecommendationsUiState.ParkToSee
                else -> RecommendationsUiState.Content(requestId = result.context.requestId, recommendations = shown)
            }
        }
```

`strings.xml`, next to the other message strings:

```xml
    <string name="park_to_see_title">Park to see places</string>
    <string name="park_to_see_body">Results can\'t be shown while driving. Park the vehicle, then try again.</string>
```

`RecommendationsScreen.kt`:
- add `private val ParkToSeeMessage = Message(R.drawable.ic_info, Highlight, R.string.park_to_see_title, R.string.park_to_see_body)` beside the other `Message` constants;
- in the `when`, after `Empty`:

```kotlin
            // docs/02 §17: places were found, but the driving list limit allows none; parking shows them.
            RecommendationsUiState.ParkToSee -> MessageState(
                message = ParkToSeeMessage,
                backLabel = R.string.back,
                onBack = onBack,
                modifier = body,
                onPrimary = onRetry,
                primaryLabel = R.string.try_again,
            )
```

docs/02 §17, after "The number of visible recommendations may be lower if the driving restrictions lower the list limit.":

```markdown
- If the limit allows no results at all, the list is replaced by a message (DN-UX-001):

  ```text
  Park to see places

  Results can't be shown while driving.
  Park the vehicle, then try again.

  [Try Again]   [Back]
  ```

  Parking shows the places on its own; Try Again searches again.
```

docs/03, in the `RecommendationsUiState` sketch (§15), after `data object Empty : RecommendationsUiState`, add
`data object ParkToSee : RecommendationsUiState // the driving list limit allows none (docs/02 §17)`.

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsViewModelTest' --tests '*RecommendationsScreenTest' --console=plain -q && echo pass`

Expected: `pass`.

- [ ] **Step 5: Full check and commit**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q && echo pass` → `pass`.

```bash
git add app docs/02-discover-nearby-ux-interaction-spec.md docs/03-discover-nearby-engineering-implementation-plan.md
git commit -m "Ask to park when the driving list limit allows no results"
```

---

### Task 2: Settings recovery after a permanent refusal, only while parked

**Files:**
- Create: `…/ui/AppSettings.kt`, `app/src/test/java/…/ui/AppSettingsTest.kt`
- Modify: `…/ui/screens/RecommendationsViewModel.kt`, `…/ui/screens/RecommendationsScreen.kt`, `…/ui/DiscoverNavHost.kt`, `strings.xml`, `…/ui/screens/PermissionGrantTest.kt`, docs/02 §10, docs/03 (§7 notes, §15 sketch)
- Test: `RecommendationsViewModelTest.kt`, `RecommendationsScreenTest.kt`, `AppSettingsTest.kt`

**Interfaces:**
- Consumes: `RecommendationsUiState` from Task 1.
- Produces:
  - `RecommendationsUiState.PermissionRequired(canRequest: Boolean, denied: Boolean = false, permanentlyDenied: Boolean = false)`;
  - `RecommendationsViewModel.onPermissionResult(granted: Boolean, permanent: Boolean = false)`;
  - `RecommendationsScreen(…, onGrant: () -> Unit, onOpenSettings: () -> Unit, modifier)`;
  - `internal fun appSettingsIntent(packageName: String): Intent`, `internal fun openAppSettings(context: Context): Boolean` and `internal fun neverAsksAgain(activity: Activity): Boolean`, in package `com.kanyandula.discovernearby.ui`.

- [ ] **Step 1: Write the failing tests**

`RecommendationsViewModelTest.kt`:

```kotlin
    // DN-UX-001: a permanent refusal is remembered, so the screen can offer Settings.
    @Test
    fun permanentRefusalIsRemembered() = runTest {
        location.result = LocationResult.PermissionMissing
        val vm = collected()
        vm.onPermissionResult(granted = false, permanent = true)
        runCurrent()
        assertEquals(PermissionRequired(canRequest = true, denied = true, permanentlyDenied = true), vm.uiState.value)
    }

    // Review Focus 3: back from Settings with location still off, the Settings message stays.
    @Test
    fun retryAfterAPermanentRefusalStillOffersSettings() = runTest {
        location.result = LocationResult.PermissionMissing
        val vm = collected()
        vm.onPermissionResult(granted = false, permanent = true)
        vm.retry()
        runCurrent()
        assertEquals(PermissionRequired(canRequest = true, denied = true, permanentlyDenied = true), vm.uiState.value)
    }
```

`RecommendationsScreenTest.kt`:
- add `private var settings = 0`;
- pass `onOpenSettings = { settings++ }` in `setUp`'s `RecommendationsScreen(…)`;
- add:

```kotlin
    // DN-UX-001: after a permanent refusal, Settings replaces Grant, but only while requests are allowed (parked).
    @Test
    fun permanentRefusalOffersSettingsOnlyWhileRequestsAreAllowed() {
        state = PermissionRequired(canRequest = true, denied = true, permanentlyDenied = true)
        rule.onNodeWithText("Location access is off for Discover Nearby. Turn it on in Settings.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").assertDoesNotExist()
        rule.onNodeWithText("Open Settings").performClick()
        assertEquals(1, settings)

        state = PermissionRequired(canRequest = false, denied = true, permanentlyDenied = true)
        rule.onNodeWithText("Park the vehicle to allow Discover Nearby to access your location.").assertIsDisplayed()
        rule.onNodeWithText("Open Settings").assertDoesNotExist()
    }
```

Create `app/src/test/java/com/kanyandula/discovernearby/ui/AppSettingsTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import android.Manifest
import android.provider.Settings
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test
    fun opensThisAppsDetailsPage() {
        assertTrue(openAppSettings(activity))
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:${activity.packageName}", started.dataString)
    }

    // Review Focus 5: nothing on the system can show it, so return false and don't crash.
    @Test
    fun openingWithNothingToShowItReturnsFalse() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        assertFalse(openAppSettings(activity))
    }

    @Test
    fun neverAsksAgainWhenNoRationaleIsOffered() {
        assertTrue(neverAsksAgain(activity))
        shadowOf(activity.packageManager)
            .setShouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION, true)
        assertFalse(neverAsksAgain(activity))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsViewModelTest' --tests '*RecommendationsScreenTest' --tests '*AppSettingsTest' --console=plain -q 2>&1 | grep -E "e: " | head -5`

Expected: compilation fails with `Unresolved reference` for `permanentlyDenied`, `onOpenSettings` and `openAppSettings`.

- [ ] **Step 3: Implement**

`RecommendationsViewModel.kt`:
- `PermissionRequired` gains a third field: `val permanentlyDenied: Boolean = false`, documented as "Android won't ask again; only Settings can allow location (docs/02 §10)".
- Replace `permissionDenied` with:

```kotlin
    private val denial = MutableStateFlow(Denial.NONE)
```

```kotlin
    /** The answer to the location permission request: a grant resumes discovery; [permanent] means no dialog again. */
    fun onPermissionResult(granted: Boolean, permanent: Boolean = false) {
        denial.value = when {
            granted -> Denial.NONE
            permanent -> Denial.PERMANENT
            else -> Denial.ONCE
        }
        if (granted) load()
    }
```

- Use `combine(result, drivingRestrictions.state, denial, ::toUiState)`.
- `toUiState`'s third parameter becomes `denial: Denial`, and the permission branch becomes:

```kotlin
        DiscoverResult.PermissionRequired -> RecommendationsUiState.PermissionRequired(
            canRequest = !driving.distractionOptimizationRequired,
            denied = denial != Denial.NONE,
            permanentlyDenied = denial == Denial.PERMANENT,
        )
```

- At file level: `private enum class Denial { NONE, ONCE, PERMANENT }`.

`strings.xml`:

```xml
    <string name="open_settings">Open Settings</string>
    <string name="permission_body_settings">Location access is off for Discover Nearby. Turn it on in Settings.</string>
```

`RecommendationsScreen.kt`:
- add the parameter `onOpenSettings: () -> Unit` after `onGrant`;
- replace the `PermissionRequired` branch:

```kotlin
            // Grant only while restrictions allow a permission dialog; otherwise ask the user to park (docs/02 §10).
            // After a permanent refusal, Android shows no dialog, so Settings takes Grant's place, under the same rule.
            is RecommendationsUiState.PermissionRequired -> {
                val settings = state.permanentlyDenied
                MessageState(
                    message = Message(
                        icon = R.drawable.ic_location,
                        tint = Highlight,
                        title = R.string.permission_title,
                        body = when {
                            !state.canRequest -> R.string.permission_body_restricted
                            settings -> R.string.permission_body_settings
                            state.denied -> R.string.permission_body_denied
                            else -> R.string.permission_body
                        },
                    ),
                    backLabel = R.string.back,
                    onBack = onBack,
                    modifier = body,
                    onPrimary = (if (settings) onOpenSettings else onGrant).takeIf { state.canRequest },
                    primaryLabel = if (settings) R.string.open_settings else R.string.grant_permission,
                )
            }
```

Create `app/src/main/java/com/kanyandula/discovernearby/ui/AppSettings.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import com.kanyandula.discovernearby.location.LOCATION_PERMISSIONS

/** The system's details page for this app, where location can be allowed again (docs/02 §10). */
internal fun appSettingsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** Opens [appSettingsIntent]; false when nothing on the system can show it. */
internal fun openAppSettings(context: Context): Boolean =
    runCatching { context.startActivity(appSettingsIntent(context.packageName)) }.isSuccess

/**
 * After a refusal: whether Android will no longer show the permission dialog. It stops after two refusals, and
 * then offers no rationale for either location permission.
 */
internal fun neverAsksAgain(activity: Activity) =
    LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
```

`DiscoverNavHost.kt`, in the `RecommendationsRoute` destination:
- add imports `androidx.activity.compose.LocalActivity`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.platform.LocalContext` and `androidx.lifecycle.compose.LifecycleResumeEffect`;
- replace the permission launcher and add the Settings wiring:

```kotlin
            val activity = LocalActivity.current
            val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                val granted = true in it.values
                viewModel.onPermissionResult(granted, permanent = !granted && activity != null && neverAsksAgain(activity))
            }
            val context = LocalContext.current
            var openedSettings by rememberSaveable { mutableStateOf(false) }
            // Back from Settings: search again, in case location was allowed there (docs/02 §10). Keyed on Unit and
            // reading the flag on each resume: a key on the flag would rerun at the tap, while still in front.
            LifecycleResumeEffect(Unit) {
                if (openedSettings) {
                    openedSettings = false
                    viewModel.retry()
                }
                onPauseOrDispose { }
            }
```

and pass to `RecommendationsScreen`:

```kotlin
                onOpenSettings = { openedSettings = openAppSettings(context) },
```

`PermissionGrantTest.kt`: add `onOpenSettings = {},` after `onGrant = { grants++ },`.

docs/02 §10, after the "Permission denied" block:

```markdown
### Permission denied permanently

After the user has refused twice, Android no longer shows the permission dialog. While parked, Settings takes
Grant's place (DN-UX-001):

```text
Location permission required

Location access is off for Discover Nearby.
Turn it on in Settings.

[Open Settings]   [Back]
```

While driving, the park-first copy is shown, with Back only. Back from Settings searches again.
```

docs/03:
- §7: after "On denial, show the denied message with Grant and Back.", add "After a permanent refusal (no rationale for either permission), offer Open Settings instead of Grant, only while `distractionOptimizationRequired` is false; return from Settings searches again (DN-UX-001)."
- §15 sketch: `PermissionRequired(val canRequest: Boolean, val denied: Boolean = false, val permanentlyDenied: Boolean = false)`.

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsViewModelTest' --tests '*RecommendationsScreenTest' --tests '*AppSettingsTest' --tests '*PermissionGrantTest' --console=plain -q && echo pass`

Expected: `pass`.

If `setShouldShowRequestPermissionRationale` isn't on Robolectric 4.17's package-manager shadow, use the shadow that owns it, and ledger the ruling.

- [ ] **Step 5: Full check and commit**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q && echo pass` → `pass`.

If detekt flags `RecommendationsScreen`'s parameter count, the existing `@Suppress("LongParameterList")` already covers it.

```bash
git add app docs/02-discover-nearby-ux-interaction-spec.md docs/03-discover-nearby-engineering-implementation-plan.md
git commit -m "Offer Settings after a permanent location refusal, only while parked"
```

---

### Task 3: Timeout wording in docs/02 §12

**Files:**
- Modify: `docs/02-discover-nearby-ux-interaction-spec.md` §12
- Test: the existing `RecommendationsScreenTest.timeoutHasItsOwnMessage`, which already asserts the mockup copy

- [ ] **Step 1: Confirm the code already matches the mockup**

Run: `grep -n 'timeout_title\|timeout_body' app/src/main/res/values/strings.xml`

Expected: "Taking longer than expected" and "Please try again.", matching `docs/design/10-timeout.png` (the title, the body, then Try Again and Back).

- [ ] **Step 2: Update docs/02 §12**

Replace the paragraph "The same message covers network failure, provider failure and timeout. Engineering distinguishes them internally for logging." with:

```markdown
Network failure and provider failure share this message. A timeout has its own, as in the timeout mockup
(`docs/design/10-timeout.png`):

```text
Taking longer than expected

Please try again.
```

Actions: Try Again, Back. Engineering still distinguishes the causes internally for logging.
```

- [ ] **Step 3: Verify and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsScreenTest' --console=plain -q && echo pass` → `pass`.

```bash
git add docs/02-discover-nearby-ux-interaction-spec.md
git commit -m "Give the timeout its own message in docs/02 §12"
```

---

### Task 4: Emulator check

**Files:** none (this task records results in the ticket).

The emulator can't produce a list limit of 0 (its moving limit is 21), so Task 1 is covered by Robolectric only. Record that.

- [ ] **Step 1: Install**

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain -q
```

Location must be on for user 10, fixed at Greystones (`adb -s emulator-5554 emu geo fix -6.0633 53.1440`), with the car in Park.

- [ ] **Step 2: Timeout matches the mockup**

```bash
d() { adb -s emulator-5554 shell "$@"; }
d am start -S -n com.kanyandula.discovernearby/.ui.MainActivity --es scenario SLOW; sleep 12
d input tap 196 268; sleep 12          # Coffee; SLOW outlasts the 8 s timeout
adb -s emulator-5554 exec-out screencap -p > /tmp/dn-ux-001-timeout.png
```

Expected: the screenshot shows the clock icon, "Taking longer than expected", "Please try again.", and Try Again and Back, as in `docs/design/10-timeout.png`.

- [ ] **Step 3: A permanent refusal offers Settings only while parked**

```bash
P=com.kanyandula.discovernearby
for perm in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
  d pm revoke --user 10 $P android.permission.$perm
  d pm set-permission-flags --user 10 $P android.permission.$perm user-fixed   # Android won't ask again
done
d am start -S -n $P/.ui.MainActivity; sleep 12
d input tap 196 268; sleep 6           # Coffee → "Location permission required" with Grant Permission
```

1. Tap Grant Permission. Its coordinates come from a screenshot; the system returns the denial without a dialog.
2. Expected: "Location access is off for Discover Nearby. Turn it on in Settings." with Open Settings and Back.
3. Drive: `d cmd car_service inject-vhal-event 0x11400400 8`, plus the continuous speed command from CLAUDE.md.
   - Expected: the park-first copy, and no Open Settings.
4. Park: `d cmd car_service inject-vhal-event 0x11400400 4`.
   - Expected: Open Settings is back.
5. Tap Open Settings.
   - Expected: `dumpsys activity activities | grep -m1 topResumedActivity` shows `com.android.car.settings`.
6. Then:

   ```bash
   d pm clear-permission-flags --user 10 $P android.permission.ACCESS_FINE_LOCATION user-fixed
   d pm clear-permission-flags --user 10 $P android.permission.ACCESS_COARSE_LOCATION user-fixed
   d pm grant --user 10 $P android.permission.ACCESS_FINE_LOCATION
   d input keyevent 4; sleep 6
   ```

   - Expected: Discover Nearby is back on top, with Coffee's recommendations: the return from Settings searched
     again.

Leave the permission granted afterwards, so later runs start from the normal state.

- [ ] **Step 4: Record**

Add the results to the DN-UX-001 ticket's completion notes:
- timeout: screenshot vs mockup;
- the Settings path: parked, driving, return;
- limit 0: Robolectric only, and why.

---

## After the tasks

- The final whole-branch review on the most capable model (executing-plans), then the `simplify` skill on the `app/` diff.
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`, then push and open the PR with `pr-description`: DN-UX-001, its acceptance criteria, and the copy for the user to confirm.
- Step 6 after merge: verify MERGED in its own call before deleting the branch.
