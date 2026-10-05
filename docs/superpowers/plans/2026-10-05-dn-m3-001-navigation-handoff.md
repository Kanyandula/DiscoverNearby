# DN-M3-001 Navigation Hand-off Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Navigate on Place Details hands the selected place to whatever navigation app the system resolves, through `ACTION_VIEW geo:lat,lng` with Locale.US and 6 decimals. Any hand-off failure shows "Navigation unavailable" with Back. This is proven on the reference emulator against the stub navigation app (docs/04 E and S).

**Architecture:**
- **`IntentNavigationLauncher`** (`navigation/`, Android, no Compose) replaces `FakeNavigationLauncher` in `AppContainer`. It formats the URI with a pure `geoUri(point)` and starts the intent from the application `Context` with `FLAG_ACTIVITY_NEW_TASK`. `runCatching` turns any failure into `Result.failure`.
- **`PlaceDetailsViewModel.navigate()`** maps a failure to a new `PlaceDetailsUiState.NavigationUnavailable(summary)`, which a late details response can't overwrite.
- **`PlaceDetailsScreen`** draws that state as the canvas message (design 11) under the place header.
- **`FakeNavigationLauncher`** moves to test sources and learns to fail.

**Tech Stack:** Kotlin, Compose (Material 3), Robolectric 4.17 (`shadowOf(application).nextStartedActivity`, `checkActivities(true)`), JUnit, coroutines-test; adb (`pm disable-user`, `logcat -s StubNav`).

**Spec:**
- Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M3-001-navigation-handoff.md`.
- `docs/02-discover-nearby-ux-interaction-spec.md` §4, §7, §14.
- `docs/03-discover-nearby-engineering-implementation-plan.md` §11 (Navigation handoff, stub), §15 (states), §20 (failure → state).
- `docs/04-discover-nearby-test-demo-plan.md` §5 E, §7 S, §11.
- `docs/design/11-navigation-unavailable.png`, `00-icons.png` (`ic_navigate_off`).

## Global Constraints

- `IntentNavigationLauncher` starts `Intent(Intent.ACTION_VIEW, geo:lat,lng)`, using the application `Context` plus `FLAG_ACTIVITY_NEW_TASK`, and is provided by `AppContainer` (ticket).
- Coordinates use `Locale.US` formatting to six decimal places (ticket; docs/03 §11: `String.format(Locale.US, "geo:%.6f,%.6f", …)`).
- `ActivityNotFoundException`, `SecurityException` and any other hand-off failure show the Navigation unavailable state (ticket; docs/03 §20).
- Never target a specific navigation app. No turn-by-turn guidance (ticket; CLAUDE.md).
- Copy (docs/02 §14): "Navigation unavailable" / "No compatible navigation app could open this destination." The action is Back. All user text goes in strings.xml.
- Loading, content and error are states of ONE destination; Back pops one (CLAUDE.md).
- Only `ui/` imports Compose. `navigation/` has no Compose imports (ArchitectureRulesTest).
- Never log raw coordinates from the app (CLAUDE.md; the stub is the exception).
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes. Never commit on local `main`. No AI attribution.

## Open decisions (recorded here, in the ticket and the PR)

1. **`NavigationUnavailable` carries the place summary.** docs/03 §15 sketches it as `data object`. The code's `PlaceDetailsUiState` gives every state a `summary` (DN-M0-005), and design 11 shows the place name in the header. So it is `data class NavigationUnavailable(override val summary: PlaceSummary)`, and docs/03 §15's sketch line is updated to match in this PR.
2. **Back from the message pops the destination.** The message's Back button, the header Back and system Back all return to Recommendations. That follows "states of one destination; Back pops one". UX §14 gives Back as the only action.
3. **A late details response doesn't replace the message.** If Navigate fails while details load, the message stays when they arrive. The driver asked to navigate; the facts can wait.
4. **No pre-check with `resolveActivity`.** `startActivity` throws `ActivityNotFoundException` when nothing resolves, and package visibility (API 30+) would make a pre-check lie without a `<queries>` entry.
5. **`FakeNavigationLauncher` moves to test sources,** as `FakeLocationProvider` did in DN-M0-006. Only tests use it once `AppContainer` has the real launcher.

## Review Focus

1. **Navigate fails while details are still loading,** then the details arrive: the "Navigation unavailable" message must stay. Pinned by `PlaceDetailsViewModelTest.detailsArrivingAfterAFailedHandOffKeepTheMessage` (Task 2).
2. **A device locale with comma decimals** (de_DE): the URI must still be `geo:53.148000,-6.060300`, or the navigation app gets nonsense. Pinned by `GeoUriTest.usesDotsWhateverTheDefaultLocale` (Task 1).
3. **No navigation app installed:** the message, no crash. Back returns to the list. Pinned by `DiscoverNavigationTest.navigateWithNoHandlerShowsNavigationUnavailableAndBackReturns` (Task 2) and emulator Scenario S (Task 3).
4. **The intent names no package or component,** so the system chooses (CLAUDE.md). Pinned by `IntentNavigationLauncherTest.startsAGeoViewIntentInANewTask` (Task 1).
5. **The coordinates the stub receives equal the selected place's,** to 5+ decimals (docs/04 E). The test pins the exact string `geo:53.148000,-6.060300` (Greystones + Harbour Roasters' offset 0.004, 0.003). Pinned by `DiscoverNavigationTest.navigateHandsTheSelectedPlaceToTheSystem` (Task 2), with the same string checked on the emulator (Task 3).

---

## File Structure

| File | Change |
| --- | --- |
| `app/src/main/java/com/kanyandula/discovernearby/navigation/IntentNavigationLauncher.kt` | Create: `geoUri(point)`, plus the launcher. |
| `app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt` | `IntentNavigationLauncher(context.applicationContext)`; drop the fake import and the ponytail comment. |
| `app/src/main/java/com/kanyandula/discovernearby/navigation/fake/FakeNavigationLauncher.kt` | Move to `app/src/test/java/com/kanyandula/discovernearby/navigation/fake/`, adding `failure`. |
| `app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModel.kt` | `NavigationUnavailable` state; `navigate()` maps failure; init keeps the message. |
| `app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreen.kt` | Draw `NavigationUnavailable` with `MessageState` under the header. |
| `app/src/main/res/drawable/ic_navigate_off.xml` | Create: the outlined, crossed-out navigate arrow (icon set). |
| `app/src/main/res/values/strings.xml` | `navigation_unavailable_title`, `navigation_unavailable_body`. |
| Tests | `navigation/GeoUriTest` (JVM), `navigation/IntentNavigationLauncherTest` (Robolectric), `PlaceDetailsViewModelTest`, `PlaceDetailsScreenTest`, `DiscoverNavigationTest`, `DiscoverApplicationTest`. |
| `docs/03-…` §15 | The `NavigationUnavailable` sketch line (Open decision 1). |
| `CLAUDE.md` | Current state: the real launcher, the new state. |

Scratchpad: `S=/private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad`. Helpers: `launch.sh`, `tap.sh`, `waitfor.sh`, `texts.sh`, `pause.sh`. In zsh, use `d() { adb -s emulator-5554 shell "$@"; }` rather than a `$D` variable.

---

### Task 0: Start the ticket

- [ ] **Step 1:** In the ticket set `status: in_progress`, `branch: dn-m3-001-navigation-handoff`.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m3-001-navigation-handoff
git add docs/superpowers/plans/2026-10-05-dn-m3-001-navigation-handoff.md
git commit -m "Add DN-M3-001 implementation plan"
```

---

### Task 1: `IntentNavigationLauncher` in `AppContainer`

**Files:**
- Create: `app/src/main/java/com/kanyandula/discovernearby/navigation/IntentNavigationLauncher.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/navigation/GeoUriTest.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/navigation/IntentNavigationLauncherTest.kt`
- Move: `navigation/fake/FakeNavigationLauncher.kt` from `app/src/main/java/…` to `app/src/test/java/…` (same package)
- Modify: `app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt:11-12,33-34`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/DiscoverApplicationTest.kt:5,29`

**Interfaces:**
- Produces:
  - `internal fun geoUri(point: GeoPoint): String`.
  - `class IntentNavigationLauncher(private val appContext: Context) : NavigationLauncher`.
  - `AppContainer.navigationLauncher` is an `IntentNavigationLauncher`.
  - Test-only `FakeNavigationLauncher` with `var failure: Throwable?` (Task 2 uses it).

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kanyandula/discovernearby/navigation/GeoUriTest.kt`:

```kotlin
package com.kanyandula.discovernearby.navigation

import com.kanyandula.discovernearby.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class GeoUriTest {

    @Test
    fun sixDecimalPlaces() {
        assertEquals("geo:53.148000,-6.060300", geoUri(GeoPoint(53.148, -6.0603)))
        assertEquals("geo:53.123457,-6.000001", geoUri(GeoPoint(53.1234567, -6.0000009)))
    }

    // A comma-decimal locale must not reach the URI (docs/03 §11).
    @Test
    fun usesDotsWhateverTheDefaultLocale() {
        val default = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals("geo:53.148000,-6.060300", geoUri(GeoPoint(53.148, -6.0603)))
        } finally {
            Locale.setDefault(default)
        }
    }
}
```

`app/src/test/java/com/kanyandula/discovernearby/navigation/IntentNavigationLauncherTest.kt`:

```kotlin
package com.kanyandula.discovernearby.navigation

import android.content.ContextWrapper
import android.content.Intent
import com.kanyandula.discovernearby.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private val HARBOUR_ROASTERS = GeoPoint(53.148, -6.0603)

@RunWith(RobolectricTestRunner::class)
class IntentNavigationLauncherTest {

    private val app = RuntimeEnvironment.getApplication()

    // The system picks the navigation app: no package or component is named (docs/03 §11).
    @Test
    fun startsAGeoViewIntentInANewTask() {
        assertTrue(IntentNavigationLauncher(app).navigateTo(HARBOUR_ROASTERS).isSuccess)
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("geo:53.148000,-6.060300", started.dataString)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(started.component)
        assertNull(started.`package`)
    }

    @Test
    fun noNavigationAppIsAFailure() {
        shadowOf(app).checkActivities(true) // startActivity now throws ActivityNotFoundException
        assertTrue(IntentNavigationLauncher(app).navigateTo(HARBOUR_ROASTERS).isFailure)
    }

    @Test
    fun anyOtherHandOffFailureIsAFailure() {
        listOf(SecurityException(), IllegalStateException()).forEach { failure ->
            val refusing = object : ContextWrapper(app) {
                override fun startActivity(intent: Intent) = throw failure
            }
            assertTrue(IntentNavigationLauncher(refusing).navigateTo(HARBOUR_ROASTERS).isFailure)
        }
    }
}
```

In `DiscoverApplicationTest.kt`, replace the `FakeNavigationLauncher` import with `import com.kanyandula.discovernearby.navigation.IntentNavigationLauncher`, and the assertion with:

```kotlin
        assertTrue(container.navigationLauncher is IntentNavigationLauncher)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*GeoUriTest' --tests '*IntentNavigationLauncherTest' --tests '*DiscoverApplicationTest' --console=plain -q`
Expected: compilation FAILS with `Unresolved reference 'geoUri'` / `'IntentNavigationLauncher'`, because the feature is missing.

- [ ] **Step 3: Implement**

`app/src/main/java/com/kanyandula/discovernearby/navigation/IntentNavigationLauncher.kt`:

```kotlin
package com.kanyandula.discovernearby.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kanyandula.discovernearby.model.GeoPoint
import java.util.Locale

/** `geo:lat,lng` to six decimals; Locale.US, so a comma-decimal locale can't reach the URI (docs/03 §11). */
internal fun geoUri(point: GeoPoint): String = String.format(Locale.US, "geo:%.6f,%.6f", point.lat, point.lng)

/**
 * Hands the destination to whatever navigation app the system resolves (docs/03 §11): ACTION_VIEW with a geo: URI,
 * never a named app. Takes the application Context, so starting needs FLAG_ACTIVITY_NEW_TASK. Any failure, from
 * ActivityNotFoundException (no navigation app) to SecurityException, comes back as Result.failure.
 */
class IntentNavigationLauncher(private val appContext: Context) : NavigationLauncher {
    override fun navigateTo(point: GeoPoint): Result<Unit> = runCatching {
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(geoUri(point))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
```

In `AppContainer.kt`:
- replace the import `com.kanyandula.discovernearby.navigation.fake.FakeNavigationLauncher` with `com.kanyandula.discovernearby.navigation.IntentNavigationLauncher`;
- replace the two lines `// ponytail: DN-M3-001 swaps in …` / `val navigationLauncher: NavigationLauncher = FakeNavigationLauncher()` with:

```kotlin
    val navigationLauncher: NavigationLauncher = IntentNavigationLauncher(context.applicationContext)
```

Move the fake into test sources and let it fail:

```bash
mkdir -p app/src/test/java/com/kanyandula/discovernearby/navigation/fake
git mv app/src/main/java/com/kanyandula/discovernearby/navigation/fake/FakeNavigationLauncher.kt app/src/test/java/com/kanyandula/discovernearby/navigation/fake/FakeNavigationLauncher.kt
```

Its new content:

```kotlin
package com.kanyandula.discovernearby.navigation.fake

import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.navigation.NavigationLauncher

/** Records the destination; fails with [failure] when set, as a missing or refusing navigation app would. */
class FakeNavigationLauncher : NavigationLauncher {
    var lastDestination: GeoPoint? = null
        private set
    var failure: Throwable? = null

    override fun navigateTo(point: GeoPoint): Result<Unit> {
        lastDestination = point
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
```

- [ ] **Step 4: Run them to verify they pass, then the full check**

Run: `./gradlew :app:testDebugUnitTest --tests '*GeoUriTest' --tests '*IntentNavigationLauncherTest' --tests '*DiscoverApplicationTest' --console=plain -q`. Expected: all PASS.

If `shadowOf(app).checkActivities(true)` doesn't exist on Robolectric 4.17's `ShadowApplication`, use `shadowOf(app).setCheckActivities(true)` or Robolectric's `ShadowInstrumentation`, and record a ruling.

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q`. Expected: green, 147 tests (142 + 2 + 3).

- [ ] **Step 5: Commit**

```bash
git add -A app/src
git commit -m "Hand Navigate to the system with a geo: intent"
```

---

### Task 2: The Navigation unavailable state

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModel.kt`, `PlaceDetailsScreen.kt`
- Create: `app/src/main/res/drawable/ic_navigate_off.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `docs/03-discover-nearby-engineering-implementation-plan.md` (§15 sketch line)
- Test: `PlaceDetailsViewModelTest.kt`, `PlaceDetailsScreenTest.kt`, `DiscoverNavigationTest.kt`

**Interfaces:**
- Consumes (Task 1): test `FakeNavigationLauncher.failure`; `IntentNavigationLauncher` wired in `AppContainer`.
- Produces: `PlaceDetailsUiState.NavigationUnavailable(override val summary: PlaceSummary)`.

- [ ] **Step 1: Write the failing tests**

In `PlaceDetailsViewModelTest.kt`, add the import `com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState.NavigationUnavailable`, then these tests:

```kotlin
    // docs/03 §20: any hand-off failure becomes NavigationUnavailable, keeping the place for the header.
    @Test
    fun aFailedHandOffShowsNavigationUnavailable() = runTest {
        places.details = { loaded }
        val vm = viewModel()
        runCurrent()
        launcher.failure = SecurityException()
        vm.navigate()
        assertEquals(NavigationUnavailable(loaded.summary), vm.uiState.value)
    }

    // The driver asked to navigate: details arriving afterwards must not replace the message.
    @Test
    fun detailsArrivingAfterAFailedHandOffKeepTheMessage() = runTest {
        places.details = slowDetails(1_000)
        launcher.failure = IllegalStateException()
        val vm = viewModel()
        runCurrent()
        vm.navigate()
        advanceTimeBy(1_001)
        assertEquals(NavigationUnavailable(place), vm.uiState.value)
    }
```

In `PlaceDetailsScreenTest.kt`, add this test. The file composes once in `setUp`, and each test sets the `state` property; it uses the file's `place` and `backs`.

```kotlin
    // docs/02 §14, design 11: the message under the place's header; Back is the only action.
    @Test
    fun navigationUnavailableShowsTheMessageWithBack() {
        state = PlaceDetailsUiState.NavigationUnavailable(place)
        rule.onNodeWithText(place.name).assertIsDisplayed()
        rule.onNodeWithText("Navigation unavailable").assertIsDisplayed()
        rule.onNodeWithText("No compatible navigation app could open this destination.").assertIsDisplayed()
        rule.onNodeWithText("Navigate").assertDoesNotExist()
        rule.onNodeWithText("Back").performClick()
        assertEquals(1, backs)
    }
```

In `DiscoverNavigationTest.kt`, add the imports `org.junit.Assert.assertEquals`, `org.robolectric.RuntimeEnvironment`, `org.robolectric.Shadows.shadowOf` and `android.content.Intent`, then:

```kotlin
    // docs/04 E: Navigate hands the selected place to the system — Greystones + Harbour Roasters' offset.
    @Test
    fun navigateHandsTheSelectedPlaceToTheSystem() {
        rule.onNodeWithText("Coffee").performClick()
        rule.onNodeWithText("Harbour Roasters, Greystones").performClick()
        rule.onNodeWithText("Navigate").performClick()
        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("geo:53.148000,-6.060300", started.dataString)
    }

    // docs/04 S: no navigation app → the message; Back returns to the list.
    @Test
    fun navigateWithNoHandlerShowsNavigationUnavailableAndBackReturns() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        rule.onNodeWithText("Coffee").performClick()
        rule.onNodeWithText("Harbour Roasters, Greystones").performClick()
        rule.onNodeWithText("Navigate").performClick()
        rule.onNodeWithText("Navigation unavailable").assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.onNodeWithText("The Daily Grind, Greystones").assertIsDisplayed()
        rule.onNodeWithText("Navigation unavailable").assertDoesNotExist()
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaceDetailsViewModelTest' --tests '*PlaceDetailsScreenTest' --tests '*DiscoverNavigationTest' --console=plain -q`

Expected:
- compilation FAILS on `Unresolved reference 'NavigationUnavailable'`.
- After a temporary stub state is added, `navigateHandsTheSelectedPlaceToTheSystem` already passes, because Task 1 wired the launcher.
- The rest fail.

Record that order: the hand-off test is green from Task 1; this task's RED comes from the missing state.

- [ ] **Step 3: Implement**

`PlaceDetailsViewModel.kt`:
- Remove the KDoc's `ponytail: NavigationUnavailable arrives with the real hand-off (DN-M3-001).` sentence.
- Add the state after `SummaryOnly`.
- Make the init keep the message.
- Map the failure.

```kotlin
    /** The hand-off failed (docs/02 §14): the message, under this place's header. */
    data class NavigationUnavailable(override val summary: PlaceSummary) : PlaceDetailsUiState
```

```kotlin
    init {
        viewModelScope.launch {
            val loaded = discover.details(place.id)?.let { PlaceDetailsUiState.Content(it) }
                ?: PlaceDetailsUiState.SummaryOnly(place)
            // A failed hand-off stays on screen: the driver asked to navigate, not to read the details.
            state.update { if (it is PlaceDetailsUiState.NavigationUnavailable) it else loaded }
        }
    }

    // Navigate never waits for details (docs/02 §7). Any hand-off failure shows NavigationUnavailable (docs/03 §20).
    fun navigate() {
        navigation.navigateTo(place.location).onFailure {
            state.value = PlaceDetailsUiState.NavigationUnavailable(state.value.summary)
        }
    }
```

Add the import `kotlinx.coroutines.flow.update`.

`strings.xml`, next to the other Place Details strings:

```xml
    <string name="navigation_unavailable_title">Navigation unavailable</string>
    <string name="navigation_unavailable_body">No compatible navigation app could open this destination.</string>
```

`app/src/main/res/drawable/ic_navigate_off.xml`: the icon set's "Nav unavailable", the navigate arrow outlined in the 1.8 stroke style with a slash, like `ic_location_off`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M12 2 4 21l8-4 8 4z M3 3l18 18"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

`PlaceDetailsScreen.kt`. Add the imports:
- `com.kanyandula.discovernearby.ui.components.Message`
- `com.kanyandula.discovernearby.ui.components.MessageState`
- `com.kanyandula.discovernearby.ui.theme.OnSurfaceVariant`

Add a top-level message:

```kotlin
private val NavigationUnavailableMessage = Message(
    R.drawable.ic_navigate_off,
    OnSurfaceVariant,
    R.string.navigation_unavailable_title,
    R.string.navigation_unavailable_body,
)
```

In `PlaceDetailsScreen`, after `ScreenHeader(...)`, branch:

```kotlin
        if (state is PlaceDetailsUiState.NavigationUnavailable) {
            // docs/02 §14, design 11: Back is the only action, and it leaves the destination like the header's.
            MessageState(NavigationUnavailableMessage, R.string.back, onBack = onBack, modifier = Modifier.weight(1f))
        } else {
            Row(...) { …existing content unchanged… }
        }
```

docs/03 §15 sketch: replace `    data object NavigationUnavailable : PlaceDetailsUiState` with `    data class NavigationUnavailable(val summary: PlaceSummary) : PlaceDetailsUiState  // keeps the header`.

- [ ] **Step 4: Run them to verify they pass, then the full check**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaceDetailsViewModelTest' --tests '*PlaceDetailsScreenTest' --tests '*DiscoverNavigationTest' --console=plain -q`. Expected: all PASS.

Then mutation-check `detailsArrivingAfterAFailedHandOffKeepTheMessage`: set the init back to `state.value = loaded`. It must FAIL; restore it.

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q`. Expected: green, 152 tests.

- [ ] **Step 5: Commit**

```bash
git add -A app/src docs/03-discover-nearby-engineering-implementation-plan.md
git commit -m "Show Navigation unavailable when the hand-off fails"
```

---

### Task 3: Scenarios E and S on the emulator; close out

**Files:** Modify `CLAUDE.md`; the ticket (vault).

- [ ] **Step 1: Install, known state**

```bash
S=/private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad; d() { adb -s emulator-5554 shell "$@"; }
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug :stub-navigation:installDebug --console=plain | grep "Installed on"
d cmd car_service inject-vhal-event 0x11400400 4
d cmd location set-location-enabled true --user 10
adb -s emulator-5554 emu geo fix -6.0633 53.1440; "$S/pause.sh" 12
d cmd package query-activities --user 10 -a android.intent.action.VIEW -d "geo:53.148000,-6.060300" | grep -E "activities found|packageName=" | sort -u
```

Expected: both installed; `1 activities found`, the stub only, so no chooser is expected.

- [ ] **Step 2: Scenario E: Navigate hands off to the stub**

```bash
adb -s emulator-5554 logcat -c
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" "Great coffee near you" 40 >/dev/null
"$S/tap.sh" Coffee; "$S/waitfor.sh" "Greystones" 25 >/dev/null
"$S/tap.sh" "Harbour Roasters, Greystones"; "$S/waitfor.sh" Navigate 20 >/dev/null
"$S/tap.sh" Navigate; "$S/pause.sh" 12
adb -s emulator-5554 logcat -d -s StubNav | tail -1
d dumpsys activity activities | grep -m1 topResumedActivity
adb -s emulator-5554 exec-out screencap -p > "$S/m3001-e-stub.png"
d input keyevent 4; "$S/pause.sh" 3; "$S/texts.sh" | head -4
```

Expected:
- `I StubNav : received geo:53.148000,-6.060300`, the same string as the Robolectric test.
- The top resumed activity is the stub (`m3001-e-stub.png`).
- No chooser appeared.
- Back returns to Discover Nearby's Place Details for Harbour Roasters. Record where Back actually lands.

The 12 s pause covers the stub's cold start (8–11 s, DN-M0-008).

- [ ] **Step 3: Scenario E while driving (stub and app are both distraction-optimised)**

```bash
d cmd car_service inject-vhal-event 0x11400400 8
(adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60 > /dev/null 2>&1 &); "$S/pause.sh" 3
adb -s emulator-5554 logcat -c
"$S/tap.sh" Navigate; "$S/pause.sh" 4
adb -s emulator-5554 logcat -d -s StubNav | tail -1; d dumpsys activity activities | grep -m1 topResumedActivity
d input keyevent 4; "$S/pause.sh" 2
d cmd car_service inject-vhal-event 0x11400400 4
```

Expected: in Drive (moving) Navigate still reaches the stub, with no block screen, and the log shows the same URI.

- [ ] **Step 4: Scenario S: no handler**

```bash
d pm disable-user --user 10 com.kanyandula.stubnavigation
d cmd package query-activities --user 10 -a android.intent.action.VIEW -d "geo:53.148000,-6.060300" | grep -E "activities found"
"$S/texts.sh" | head -3
"$S/tap.sh" Navigate; "$S/pause.sh" 2; "$S/texts.sh" | head -6
adb -s emulator-5554 exec-out screencap -p > "$S/m3001-s-unavailable.png"
"$S/tap.sh" Back; "$S/pause.sh" 2; "$S/texts.sh" | head -4
d pm enable --user 10 com.kanyandula.stubnavigation
d cmd package query-activities --user 10 -a android.intent.action.VIEW -d "geo:53.148000,-6.060300" | grep -E "activities found"
```

Expected:
- `0 activities found`.
- Navigate shows "Navigation unavailable" and "No compatible navigation app could open this destination." with Back, under the "Harbour Roasters, Greystones" header (`m3001-s-unavailable.png`; compare with design 11).
- Back returns to the Coffee list.
- No crash.
- After `pm enable`, the stub is back: `1 activities found`.

If Step 2's Back left the app on Place Details, the first `texts.sh` shows it. Otherwise re-open Harbour Roasters first.

- [ ] **Step 5: Record**

1. In CLAUDE.md "Current state", replace ``, `NavigationLauncher` is a fake until DN-M3-001`` with ``; Navigate hands off through `IntentNavigationLauncher` (DN-M3-001: `ACTION_VIEW geo:`, failure → `NavigationUnavailable`)``. Rewrap if needed.
2. Run `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q`. Expected: green.
3. Commit `Note the real navigation hand-off in CLAUDE.md`.
4. Append the ticket's completion notes. Include:
   - each AC with its result;
   - the Step 1–4 outputs;
   - whether a chooser appeared;
   - where Back from the stub landed;
   - Open decisions 1–5;
   - checks run.

- [ ] **Step 6: Close out**

1. Push and open a draft PR.
2. Run the `simplify` skill and fix its findings.
3. Do the final whole-branch review (executing-plans).
4. Write the PR description with the `pr-description` skill: ticket ID and acceptance criteria, no AI attribution.
5. Run `gh pr ready` once CI is green.
