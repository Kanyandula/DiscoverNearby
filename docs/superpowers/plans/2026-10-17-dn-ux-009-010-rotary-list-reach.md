# DN-UX-009 and DN-UX-010: Rotary List Reach Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rotary reaches every list row, in order, in both directions and at any turn speed. Then F (rotary) is re-run
for M4.

**Architecture:** The rotary service moves focus only to rows on screen. Compose leaves fully clipped nodes out of the
accessibility tree, and a lazy list doesn't compose rows off screen. So at a list's visible edge, the next node in
order is outside the list: the Back arrow going up (D1), and the rail going down when a quick turn beats
`revealNextOnFocus`'s scroll (D2).

Android 13's `Navigator.findRotateTarget` has a rule for this. When the focused node is inside a container marked with
car-ui's content description `com.android.car.ui.utils.VERTICALLY_SCROLLABLE`, the container can scroll that way, and
the next node is outside it, the service keeps focus. It sends the app a rotary scroll instead: a `MotionEvent`,
`ACTION_SCROLL` from `SOURCE_ROTARY_ENCODER`, with `AXIS_VSCROLL` of −detents clockwise and +detents counter-clockwise.

Compose ignores that event, because it reads only `AXIS_SCROLL`. The service's own focus move after the scroll uses
`focusSearch`, which doesn't work on Compose's nodes. So the fix has two parts:
- Mark both lists (`Modifier.rotaryScrollable()`).
- In `MainActivity`, turn the service's scroll into Tab or Shift+Tab (`Activity.moveFocusForRotaryScroll`). Those are
  Compose's next and previous, and they compose the off-screen row and bring it into view.

Root cause and the platform source are in "Evidence" below.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2026.09.00, ui 1.12.1), Robolectric 4.17, Compose UI test; AAOS
`AAOS_AOSP_33_userdebug` emulator (`cmd car_service inject-rotary`).

**Spec:**
- Vault tickets `DN-UX-009-rotary-rows-above-screen.md` and `DN-UX-010-rotary-fast-turn-skips-rows.md`.
- D1 and D2 in `docs/validation/2026-10-17-m4-emulator.md`.
- docs/02 §16 ("no primary action is unreachable").
- docs/03 §17.
- ADR-002.

## Global Constraints

- **The Product decision.** ADR-002 allows no rotary change without one. The Product Lead decided on 2026-10-17 to
  "fix DN-UX-009 and DN-UX-010" (no ranking given). Record it in ADR-002.
- **Nothing else changes** (ticket): rotary order, `ReturnFocus`, and the rail rules (docs/03 §17) stay as they are,
  and each fix needs a test that fails first.
- **No car-ui-lib dependency and no Car App Library APIs.** Copy car-ui's constant string. Only `ui/` imports Compose,
  and only `car/CarDrivingRestrictions` imports `android.car`.
- **Keep `revealNextOnFocus`** (DN-UX-006). Normal turns then never reach the list's edge; the new path only covers
  turns that do.
- **Rotary runs:** no `uiautomator` at any point. Read focus from
  `dumpsys activity service com.android.car.rotary/.RotaryService` (`focusedNode`) and the `RotaryController` log.
  Screenshots only at checkpoints; disclose any polling.
- **Public repo:** no real place names, provider responses or raw coordinates. Live screenshots stay in the vault.
- **Checks:** `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` pass (detekt `maxIssues: 0`). Repo git hooks
  don't run on this machine.
- **No AI attribution** in commits or the PR. Never commit on local `main`. Subject ≤72 characters, and the body says
  why.

## Review Focus

1. **A detent at a list end that can still scroll** (the reveal animation is mid-way): focus leaves to the next item
   in order, the rail going down and Back going up, and is never trapped. Pinned by
   `rotaryListScrollsReachEveryRowAndLeaveTheList` (Task 1).
2. **An accelerated turn with several detents in one scroll** (`AXIS_VSCROLL` = −2): one row per detent. Pinned by
   `eachDetentMovesOneRow` (Task 1).
3. **Scrolls that aren't the service's list scroll:** a mouse wheel (`SOURCE_MOUSE`) or a rotary event carrying only
   `AXIS_SCROLL` (direct manipulation, projection). They're left alone and still reach the view tree. Pinned by
   `otherScrollsAreLeftAlone` (Task 1).
4. **The Saved list**, which is uncapped while parked: it's marked like Recommendations. Pinned by
   `savedRowsSitInAListMarkedForTheRotaryService` (Task 1).
5. **The service's view of focus after the app moves it:** the service follows the app's `TYPE_VIEW_FOCUSED`, and a
   late event must not pull focus back. There's no host test for this (it's platform behaviour). It's checked on the
   emulator: after each scripted run, the service's `focusedNode` matches the ring in a screenshot (Task 3).

## Evidence (Phase 1 of systematic debugging, 2026-10-17)

- **Reproduced with scripted turns** (`inject-rotary`, debug fakes, Outdoors and Coffee at location B, parked):
  - **D1:** turning up from the rail with the list scrolled to the end went row 4 → 3 → 2 → Back, skipping row 1.
    Turning down from Back then started at row 2. Seen in every run.
  - **D2:** turns back to back went row 1 → 2 → 3 → rail, skipping row 4, in 2 of 3 runs.
- **Platform source** (Android 13 RotaryController, read from AOSP; the marker string was confirmed in the emulator's
  `CarRotaryController.apk`):
  - **Off-screen nodes:** `Navigator.findRotateTarget` can focus them (`Utils.canPerformFocus`), but Compose doesn't
    expose them. `SemanticsOwner.getAllUncoveredSemanticsNodesToIntObjectMap` drops nodes whose clipped bounds don't
    intersect the window, and `LazyColumn` doesn't compose them.
  - **The scroll-container rule:** a marked container that can scroll in the turn's direction, with the next node
    outside it, makes the service stop and call `injectScrollEvent`. That's `AXIS_VSCROLL`, from
    `SOURCE_ROTARY_ENCODER`, sent to the focused window.
  - **Compose drops that scroll:** `AndroidComposeView.handleRotaryEvent` reads `AXIS_SCROLL` only, so the event
    carries 0 px.
  - **The service's follow-up move fails on Compose:** `handleViewScrolledEvent` then calls
    `findFocusableDescendantInDirection`, which uses `focusSearch`.
  - **App focus moves are accepted:** the service follows them (`handleViewFocusedEvent`, which ignores a late event
    when focus has moved since).
- **A throwaway spike** (the marker on Recommendations and the Tab translation in `MainActivity`) confirmed the cause:
  - D1 passed: rows 3 → 2 → 1 → Back, with one rotary scroll.
  - D2 passed: row 3 → 4 by the app's move, then the rail. One detent re-focused row 4 because the service hadn't seen
    the app's move yet. No row was skipped.
- **Known and out of scope (G1):** sometimes the first turn after a list opens goes from Back straight to the rail,
  even with every row on screen.
  - Seen on `main` (1 of 4 runs) and with the spike (about 1 in 3).
  - It starts outside the list, so the marker can't apply. A stale service-side tree after Loading → Content is
    likely, but not proven.
  - Task 4 records it if the hand-driven run meets it, and the vault ticket DN-UX-012 (backlog) carries it.

---

### Task 1: Rotary list scroll

**Files:**
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/RotaryScroll.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt` (the `LazyColumn`)
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/SavedScreen.kt` (the `LazyColumn`)
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/components/RecommendationRow.kt` (`revealNextOnFocus`'s
  KDoc only)
- Create: `app/src/test/java/com/kanyandula/discovernearby/ui/RotaryListScrollTest.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/TestScreens.kt` (helper `rotaryScroll`)
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNavigationTest.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/RotaryContractTest.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/SavedScreenTest.kt`

**Interfaces:**
- Produces:
  - `internal const val ROTARY_VERTICALLY_SCROLLABLE: String`
  - `fun Modifier.rotaryScrollable(): Modifier`
  - `fun Activity.moveFocusForRotaryScroll(event: MotionEvent): Boolean`
  - Test helper `fun rotaryScroll(detents: Float, source: Int = InputDevice.SOURCE_ROTARY_ENCODER, axis: Int =
    MotionEvent.AXIS_VSCROLL): MotionEvent`

- [ ] **Step 1: Add the test helper to `TestScreens.kt`**

Add the imports `android.view.InputDevice` and `android.view.MotionEvent`, then:

```kotlin
/**
 * A rotary scroll as Android 13's rotary service sends one to a list it can't leave (RotaryService.injectScrollEvent):
 * ACTION_SCROLL from the rotary encoder, AXIS_VSCROLL −detents clockwise.
 */
fun rotaryScroll(
    detents: Float,
    source: Int = InputDevice.SOURCE_ROTARY_ENCODER,
    axis: Int = MotionEvent.AXIS_VSCROLL,
): MotionEvent {
    val properties = MotionEvent.PointerProperties().apply { id = 0 }
    val coords = MotionEvent.PointerCoords().apply { setAxisValue(axis, detents) }
    return MotionEvent.obtain(
        0L, 0L, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, source, 0,
    )
}
```

- [ ] **Step 2: Write the failing screen-level tests: `RotaryListScrollTest.kt`**

```kotlin
package com.kanyandula.discovernearby.ui

import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.requestFocus
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.screens.RecommendationsScreen
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DN-UX-009, DN-UX-010: the rotary service moves only to rows on screen. At a marked list's visible edge it sends a
 * rotary scroll instead of leaving the list, and the app moves to the next or previous row, on screen or not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class RotaryListScrollTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val list = hasScrollAction()

    @Before
    fun setUp() {
        lateinit var inputModes: InputModeManager
        rule.setContent {
            inputModes = LocalInputModeManager.current
            DiscoverNearbyTheme {
                // The panel a destination gets beside the rail on the reference AVD (PlaceDetailsFitTest).
                Box(modifier = Modifier.size(width = ReferencePanelWidth, height = ReferencePanelHeight)) {
                    RecommendationsScreen(
                        category = DiscoveryCategory.COFFEE,
                        state = RecommendationsUiState.Content(
                            requestId = 1,
                            recommendations = List(ROWS) {
                                Recommendation(testPlace("Row $it", "cafe"), 1.0, 500, null, null, null)
                            },
                        ),
                        onRetry = {},
                        onBack = {},
                        onPlaceSelected = { _, _ -> },
                        onGrant = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
        rule.useRotaryInput(inputModes)
    }

    private fun scroll(event: MotionEvent): Boolean {
        var consumed = false
        rule.runOnUiThread { consumed = rule.activity.moveFocusForRotaryScroll(event) }
        rule.waitForIdle()
        return consumed
    }

    @Test
    fun theListIsMarkedForTheRotaryService() {
        rule.onNode(list and hasContentDescription(ROTARY_VERTICALLY_SCROLLABLE)).assertExists()
    }

    // D2: a quick turn from the last row on screen goes on to the row below it.
    @Test
    fun aClockwiseScrollMovesToTheRowBelow() {
        rule.onNodeWithText("Row 2").requestFocus()
        assertTrue(scroll(rotaryScroll(-1f)))
        rule.onNodeWithText("Row 3").assertIsFocused().assertIsDisplayed()
    }

    // D1: with the list scrolled down, a turn up from the top row on screen reaches the row above the screen.
    @Test
    fun aCounterClockwiseScrollMovesToTheRowAboveTheScreen() {
        rule.onNode(list).performScrollToIndex(2)
        rule.onNodeWithText("Row 1").assertDoesNotExist()
        rule.onNodeWithText("Row 2").requestFocus()
        assertTrue(scroll(rotaryScroll(1f)))
        rule.onNodeWithText("Row 1").assertIsFocused().assertIsDisplayed()
    }

    @Test
    fun eachDetentMovesOneRow() {
        rule.onNodeWithText("Row 0").requestFocus()
        scroll(rotaryScroll(-2f))
        rule.onNodeWithText("Row 2").assertIsFocused()
    }

    @Test
    fun otherScrollsAreLeftAlone() {
        rule.onNodeWithText("Row 0").requestFocus()
        assertFalse(scroll(rotaryScroll(-1f, source = InputDevice.SOURCE_MOUSE)))
        assertFalse(scroll(rotaryScroll(-1f, axis = MotionEvent.AXIS_SCROLL)))
        rule.onNodeWithText("Row 0").assertIsFocused()
    }

    private companion object {
        const val ROWS = 5
    }
}
```

- [ ] **Step 3: Write the failing full-app test in `DiscoverNavigationTest.kt`**

Add the imports `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.isFocused` and
`org.junit.Assert.assertTrue` if it's missing. Then add, after `aRotarySelectLeavesFocusOnTheNewScreen`:

```kotlin
    // DN-UX-009, DN-UX-010: the rotary service's list scroll moves through every row, off screen ones included, then
    // out of the list: to the rail going down, to Back going up. Never trapped.
    @Test
    fun rotaryListScrollsReachEveryRowAndLeaveTheList() {
        rotarySelect(rule.onNodeWithText("Outdoors"))
        fun focused() = rule.onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        fun scroll(detents: Float) {
            rule.runOnUiThread { rule.activity.moveFocusForRotaryScroll(rotaryScroll(detents)) }
            rule.waitForIdle()
        }
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) } // the service's turn from Back to row 1
        val down = mutableListOf(focused())
        repeat(OUTDOORS_ROWS - 1) {
            scroll(-1f)
            down += focused()
        }
        assertEquals(OUTDOORS_ROWS, down.distinct().size)
        assertTrue(down.all { it.endsWith(", Greystones") })
        scroll(-1f)
        tab("Discover").assertIsFocused()
        val up = mutableListOf<String>()
        repeat(OUTDOORS_ROWS) {
            scroll(1f)
            up += focused()
        }
        assertEquals(down.reversed(), up)
        scroll(1f)
        rule.onNodeWithContentDescription("Back").assertIsFocused()
    }
```

In the companion object, add `const val OUTDOORS_ROWS = 4` next to `SETTLE_MS`, with the comment `// the Outdoors
fakes (FakePlaces)`.

- [ ] **Step 4: Write the failing tests for the activity and Saved**

In `RotaryContractTest.kt`, add the import `org.junit.Assert.assertTrue`, then:

```kotlin
    // DN-UX-009, DN-UX-010: MainActivity takes the rotary service's list scroll (moveFocusForRotaryScroll).
    @Test
    fun mainActivityTakesTheRotaryServicesListScroll() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertTrue(activity.dispatchGenericMotionEvent(rotaryScroll(-1f)))
    }
```

In `SavedScreenTest.kt`, add the imports `androidx.compose.ui.test.hasContentDescription`,
`androidx.compose.ui.test.hasScrollAction` and `com.kanyandula.discovernearby.ui.ROTARY_VERTICALLY_SCROLLABLE`, then:

```kotlin
    // DN-UX-009, DN-UX-010: Saved's list is marked for the rotary service, as Recommendations' is.
    @Test
    fun savedRowsSitInAListMarkedForTheRotaryService() {
        state = SavedUiState.Content(listOf(SavedRow(cafe, 2_100), SavedRow(park, null)))
        rule.onNode(hasScrollAction() and hasContentDescription(ROTARY_VERTICALLY_SCROLLABLE)).assertExists()
    }
```

- [ ] **Step 5: Run the new tests; they fail**

Run:
`./gradlew testDebugUnitTest --tests '*RotaryListScrollTest' --tests '*DiscoverNavigationTest.rotaryListScrolls*'
--tests '*RotaryContractTest' --tests '*SavedScreenTest.savedRows*'`

Expected: FAIL, compilation: unresolved references `moveFocusForRotaryScroll` and `ROTARY_VERTICALLY_SCROLLABLE`.

- [ ] **Step 6: Implement `RotaryScroll.kt`**

```kotlin
package com.kanyandula.discovernearby.ui

import android.app.Activity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs

/** car-ui's `RotaryConstants.ROTARY_VERTICALLY_SCROLLABLE`: the content description the rotary service looks for. */
internal const val ROTARY_VERTICALLY_SCROLLABLE = "com.android.car.ui.utils.VERTICALLY_SCROLLABLE"

/**
 * Rotary (DN-UX-009, DN-UX-010): marks a list for the rotary service, which moves only to rows on screen. Unmarked, a
 * turn at the list's visible edge left it for the screen's Back or the app rail, skipping the rows off screen. Marked,
 * while the list can still scroll that way, the service keeps focus and sends a rotary scroll instead (Android 13's
 * Navigator.findRotateTarget), which [moveFocusForRotaryScroll] turns into a move to the next or previous row.
 */
fun Modifier.rotaryScrollable(): Modifier = semantics { contentDescription = ROTARY_VERTICALLY_SCROLLABLE }

/**
 * The rotary service's scroll of a [rotaryScrollable] list, as a move to the next row (clockwise) or the previous one,
 * a row per detent. Compose can't act on that scroll itself: it reads a rotary event's AXIS_SCROLL, not the
 * AXIS_VSCROLL the service sends, and the service's own move after it uses a focus search Compose doesn't support.
 * Tab and Shift+Tab are Compose's next and previous, which compose a row off screen and bring it into view. Returns
 * false, leaving [event] alone, when it is no such scroll.
 */
fun Activity.moveFocusForRotaryScroll(event: MotionEvent): Boolean {
    val detents = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toInt()
    val isListScroll = event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) &&
        event.actionMasked == MotionEvent.ACTION_SCROLL && detents != 0
    if (isListScroll) {
        val meta = if (detents > 0) KeyEvent.META_SHIFT_ON else 0 // clockwise, down the list, is negative
        repeat(abs(detents)) {
            dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0, meta))
            dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0, meta))
        }
    }
    return isListScroll
}
```

- [ ] **Step 7: Wire it in**

In `MainActivity.kt`, add the import `android.view.MotionEvent` and, after `onCreate`:

```kotlin
    // A marked list's rotary scroll is a move to the next or previous row (DN-UX-009, DN-UX-010).
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        moveFocusForRotaryScroll(event) || super.dispatchGenericMotionEvent(event)
```

In `RecommendationsScreen.kt` and `SavedScreen.kt`, add the import `com.kanyandula.discovernearby.ui.rotaryScrollable`
and change each list's modifier:

```kotlin
                LazyColumn(
                    modifier = Modifier.weight(1f).rotaryScrollable(),
                    verticalArrangement = Arrangement.spacedBy(RowGap),
                ) {
```

In `RecommendationRow.kt`, the last sentence of `revealNextOnFocus`'s KDoc changes. It now reads: "…so without that
glimpse a turn from the last visible row would jump to the rail instead of scrolling on (found on the emulator)." It
becomes:

```kotlin
 * Rotary (DN-UX-006): a focused row also brings the top of the next row into view. The rotary service moves only to
 * rows on screen, so the next turn finds the next row there; a turn quicker than this scroll reaches the list's edge,
 * where the list's rotary scroll takes over (rotaryScrollable, DN-UX-010).
```

- [ ] **Step 8: Run the new tests; they pass**

Run: the Step 5 command.

Expected: PASS, all eight tests.

- [ ] **Step 9: Run the full checks**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`

Expected: BUILD SUCCESSFUL. The tests number 349 on `main` plus 8 new, with no failures.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/RotaryScroll.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/screens/SavedScreen.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/components/RecommendationRow.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/
git commit -m "Let rotary reach list rows off screen" -m "The rotary service moves only to rows on screen, so at a list's visible edge a turn left the list: up to Back, skipping rows above (DN-UX-009), and down to the rail when it beat the reveal scroll (DN-UX-010). Marked as car-ui scroll containers, the lists keep the turn and get the service's rotary scroll, which MainActivity turns into Compose's next or previous row."
```

### Task 2: Docs and the Product decision

**Files:**
- Modify: `docs/03-discover-nearby-engineering-implementation-plan.md` (§17, the rail bullet)
- Modify: `docs/adr/0002-ui-stack-after-v7.md` (Decision, a dated note)
- Modify: `CLAUDE.md` (the architecture rule for the rail, and the M4 bullet)

- [ ] **Step 1: docs/03 §17.** Replace the rail bullet's "Two rules keep it there … instead of scrolling" with:

```markdown
- reach the app rail (DN-UX-006; four tabs since DN-UX-011, Profile last) after each screen's content: the panel is
  composed before the rail, so the first turn still lands on the screen's first item. Three rules keep it there and
  every list row reachable (found on the emulator): Android's initial focus after a selected item leaves (a downward
  search from the top) is handed from the rail to the screen panel; a focused list row also brings the top of the
  next row into view, since the rotary service moves only to rows on screen; and each list is marked as a rotary
  scroll container (car-ui's content description), so at the list's visible edge the service sends a rotary scroll
  instead of leaving the list, which the app turns into a move to the next or previous row (DN-UX-009, DN-UX-010)
```

- [ ] **Step 2: ADR-002.** In "Decision", before "**Prior decision (before the re-test; history)…**", add:

```markdown
**Rotary list reach (Product Lead, 2026-10-17):** fix DN-UX-009 and DN-UX-010, M4's F failures (a rotary change,
so a new decision under "Any further rotary change is a new decision").
- The lists are marked as rotary scroll containers; at a list's visible edge the rotary service sends a rotary
  scroll instead of leaving the list, and the app turns it into a move to the next or previous row (docs/03 §17).
- The V7 gate, the waiver and `ReturnFocus` are unchanged. F's re-run is recorded in
  `docs/validation/2026-10-17-m4-emulator.md`.
```

- [ ] **Step 3: CLAUDE.md.**
  - In "Architecture rules", replace "Keep its two rules: … (`revealNextOnFocus`), or the rotary service jumps to the
    rail instead of scrolling." with:
    "Keep its three rules: the rail hands a downward entry (Android's initial focus) to the screen panel; a focused
    list row shows the next row's top (`revealNextOnFocus`); and each list is a rotary scroll container
    (`rotaryScrollable`), whose rotary scroll MainActivity turns into the next or previous row
    (`moveFocusForRotaryScroll`, DN-UX-009/010), or rotary skips rows off screen."
  - In the "M4 validation (DN-M4-001)" bullet, append: "DN-UX-009/010 fixed (Product Lead, 2026-10-17): lists are
    rotary scroll containers (`ui/RotaryScroll.kt`)."

- [ ] **Step 4: Check and commit**

Run: `git diff --check && echo whitespace-clean`

Expected: `whitespace-clean`.

```bash
git add docs/03-discover-nearby-engineering-implementation-plan.md docs/adr/0002-ui-stack-after-v7.md CLAUDE.md
git commit -m "Record the rotary list-reach fix and its decision" -m "ADR-002 needs a Product decision for any rotary change; the Product Lead chose to fix DN-UX-009 and DN-UX-010. docs/03 §17 and CLAUDE.md gain the third list rule."
```

### Task 3: Scripted emulator check (debug fakes, parked and moving)

**Files:**
- Modify: `docs/validation/2026-10-17-m4-emulator.md` (a new section, "F re-run after DN-UX-009 and DN-UX-010", the
  scripted part)

The helpers are in the session scratchpad: `m4/h.sh` and `m4/r.sh`.
- `rot cw|ccw` → `cmd car_service inject-rotary -c true|false`
- `sel` → `cmd car_service inject-key 23`
- `foc` → the service's `focusedNode` bounds
- `moves` → each turn and focus move since `logcat -c`

- [ ] **Step 1: Install and confirm the image.**
  `adb -s emulator-5554 emu avd name` prints `AAOS_AOSP_33_userdebug`. Then run `./gradlew assembleDebug` and
  `adb -s emulator-5554 install -r --user 10 app/build/outputs/apk/debug/app-debug.apk`.

  Expected: `Success`.

- [ ] **Step 2: D2 (quick turns down), parked, 3 runs.** Launch with `--es scenario NORMAL` at location B. Turn three
  times to Outdoors, `sel`, and turn once onto row 1. Then, in one shell, take six turns back to back:
  `for i in 1 2 3 4 5 6; do cmd car_service inject-rotary -c true; done`. Then six with `sleep 0.2` between them.

  Expected: `moves` lists rows 1–4 in order, then the rail, with no row skipped. A detent may re-focus the row the app
  just moved to.

- [ ] **Step 3: D1 (turning up a scrolled list), parked, 3 runs.** From the rail, with the list scrolled to the end,
  take eight turns counter-clockwise, 1.5 s apart, then two clockwise.

  Expected: rows 4 → 3 → 2 → 1 → Back. Clockwise from Back reaches row 1, then row 2.

- [ ] **Step 4: Repeat Steps 2–3 in Drive.** Run `drive 300` from `h.sh` first; this image's moving list limit is 21,
  so the same rows show.

  Expected: as parked.

- [ ] **Step 5: Check the service against the screen.** After each run, take one screenshot. The ring must sit on the
  node `foc` reports (Review Focus 5).

  Expected: they match.

- [ ] **Step 6: Record G1 where it appears.** If the first turn after `sel` goes to the rail, that's G1. Log the run as
  G1, rerun it, and count G1 runs separately; they don't count against D1 or D2.

- [ ] **Step 7: Record and commit.** Write the scripted section in the M4 record:
  - the build;
  - the runs per arm, parked and moving;
  - each arm's focus sequence summary;
  - the detents that re-focused a row;
  - G1's count.

  No place names: fake names are the repo's own, but write "row 1–4" anyway.

```bash
git add docs/validation/2026-10-17-m4-emulator.md
git commit -m "Record the scripted rotary list-reach check" -m "Scripted inject-rotary runs reproduce D1 and D2 on main; on this branch every row is reached in order, parked and moving."
```

### Task 4: F re-run by hand (Extended Controls), parked and moving

**Files:**
- Modify: `docs/validation/2026-10-17-m4-emulator.md` (F re-run: the hand-driven part; Summary; Sign-off)
- Modify: `docs/05-discover-nearby-delivery-plan.md` §9 (the gates line and the V7 row)
- Modify: `docs/adr/0002-ui-stack-after-v7.md` ("Manual Extended Controls run" status)
- Modify: `CLAUDE.md` (Next)

The user drives Extended Controls → Car rotary, with no touch on the emulator window, live data, Outdoors at location
B. Engineering reads the service's focus after each step from `dumpsys` and the `RotaryController` log.

- [ ] **Step 1: Parked.** The journey:
  - Discover → Outdoors.
  - Turn down through every row to the rail at a normal pace.
  - Turn up through every row to Back.
  - Turn down from Back to row 1.
  - Make a quick pass down, about 5 turns a second.
  - Pick a row → Place Details → Save → Navigate → select. The stub navigation app receives the `geo:` hand-off.
  - Back ×3, to Discover.

  Expected:
  - Every row is reached in order both ways, at either pace.
  - ADR-002's four V7 gate items hold.

- [ ] **Step 2: Drive** (`drive 300`). Repeat the list passes and Back.

  Expected: as parked.

- [ ] **Step 3: Record** in the M4 record's F re-run section.
  - A table of steps, what was observed, and the check against docs/02 §16 and the ticket's acceptance criteria.
  - G1 if it is seen.
  - The F re-run's grade. The original F row stays as history.
  - The Summary and Sign-off lines updated.

  Keep the evidence (the log and key frames) in the vault under `evidence/2026-10-17-f-rerun/`.

- [ ] **Step 4: Status docs.**
  - docs/05 §9's gates line: the F re-run result, and that M4's exit is the Product Lead's call (with G1 if seen).
  - The V7 row: the F re-run appended.
  - ADR-002's "Manual Extended Controls run" status: point to the re-run.
  - CLAUDE.md's "Next": the M4 exit decision, then DN-M4-002.

- [ ] **Step 5: Check and commit**

Run: `git diff --check && echo whitespace-clean`

Expected: `whitespace-clean`.

```bash
git add docs/validation/2026-10-17-m4-emulator.md docs/05-discover-nearby-delivery-plan.md \
  docs/adr/0002-ui-stack-after-v7.md CLAUDE.md
git commit -m "Record F's hand-driven re-run after the list-reach fix" -m "M4 waited on F; the re-run is recorded beside the original, which stays as history, and the status docs point to it."
```
