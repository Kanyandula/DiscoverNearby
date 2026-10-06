# DN-M0-011 Bounded V7 Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Compose UI meet the V7 gate on the reference emulator with one bounded fix: a visible
focus ring on every actionable control, and no lost turn after Back. Then re-test cleanly and record the
result, pass or fail.

**Architecture:** Two small helpers in `ui/`:
- **`Modifier.focusRing(shape, color)`:** draws the existing 4 dp ring while its element has focus. It replaces
  the tile's private ring and goes on rows, header Back, Navigate and the message buttons.
- **`ReturnFocus`:** remembers the item selected by rotary and requests focus on it one frame after its screen
  returns.

The lost turn happens because, after Back to Recommendations, Compose focuses the header Back in the screen's
first frame without reporting it, so the rotary service keeps the ComposeView host. Moving focus one frame
later is a change Compose does report. When it opened Details, the RotaryController log showed exactly that:
host first, then the node.

**Tech Stack:**
- Compose UI 1.12.1: `FocusRequester`, `onFocusChanged`, `LocalInputModeManager`, `withFrameNanos`.
- Robolectric 4.17, with native graphics for the pixel checks.
- `adb … cmd car_service inject-rotary` / `inject-key` on `AAOS_AOSP_33_userdebug`.

**Spec:**
- ADR-002 Decision (2026-10-06): `docs/adr/0002-ui-stack-after-v7.md#decision`.
- The DN-M0-011 ticket (`~/.claude/projects/Discover Nearby/tickets/DN-M0-011-rotary-focus-proof.md`, notes
  of 2026-10-06).
- UX spec §16 (`docs/02-discover-nearby-ux-interaction-spec.md`).
- E1 record: `docs/adr/0002/e1-2026-10-06/README.md`.

## Global Constraints

- **V7 gate (Product, 2026-10-06).** V7 passes only when:
  - rotary navigation reaches Navigate;
  - selection activates the focused control;
  - Back returns to a usable screen without losing a turn;
  - every actionable control shows visible focus.
- **Where focus lands after Back** is recorded, not gated.
- **Scope:** controller rotation on the current Android 13 image. Nudging is not a POC requirement.
- **One bounded Compose fix,** then a re-test. If V7 still fails, record V7 as failed and reopen ADR-002. Never
  mark V7 complete, or switch to the Car App Library, on assumption.
- **Evidence rules:**
  - behavioural emulator runs are free of `uiautomator`;
  - a `uiautomator` control is reported separately;
  - adb injection is not the manual Extended Controls run, which is recorded separately.
- **Rotary state:** read only from `dumpsys activity service com.android.car.rotary/.RotaryService`
  (`focusedNode`) and screenshots.
- **Architecture:**
  - only `ui/` imports Compose;
  - all user text is in `strings.xml` (this plan adds none);
  - `FocusRingWidth = 4.dp`, ring colour `Accent` (theme primary).
- **Every run uses** `adb -s emulator-5554`. Never `adb reboot`.
- **Done means:** `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes.
- **Workflow:**
  - branch `dn-m0-011-bounded-v7-fix` from an updated `main`;
  - never commit on `main`;
  - no AI attribution in commits or PRs;
  - commit subjects ≤ 72 characters.

## Review Focus

1. **The restore target is gone after Back.** For example, the driving limit trimmed the list while Details was
   open. Expect no crash and no restore. `FocusRequester.requestFocus` returns `false` when nothing is attached
   (Compose UI 1.12.1). Pinned by `ReturnFocusTest.returnToAKeyNoLongerShownDoesNothing`.
2. **A touch selection.** Expect nothing remembered, so no ring appears after Back. Pinned by
   `DiscoverNavigationTest.touchSelectionLeavesNoFocusBehind`.
3. **Rotary after touch, or touch after rotary.** Only the last selection counts, and only if it was by rotary.
   Pinned by `ReturnFocusTest.touchSelectionReplacesARotarySelection`.
4. **A ring on a fill of nearly the same colour** (Navigate and Retry are `Action` blue). Expect the ring to use
   `OnSurface` there. Pinned by `FocusRingTest.focusedNavigateShowsALightRing` and
   `focusedMessageButtonsShowTheirRings`.
5. **Error and permission states** have Retry, Grant and Back buttons that the journey never reaches. Expect
   them to show the ring too. Pinned by `focusedMessageButtonsShowTheirRings`; emulator Task 3 Step 4 shows it
   on a real error state.

---

## File structure

| File | Change | Responsibility |
| --- | --- | --- |
| `app/src/main/java/com/kanyandula/discovernearby/ui/components/FocusRing.kt` | Create | `Modifier.focusRing(shape, color)` |
| `…/ui/components/CategoryTile.kt` | Modify | use `focusRing`; drop its private ring |
| `…/ui/components/RecommendationRow.kt` | Modify | `focusRing` |
| `…/ui/components/ScreenHeader.kt` | Modify | `focusRing(CircleShape)` on Back |
| `…/ui/components/MessageState.kt` | Modify | `focusRing` on both buttons |
| `…/ui/screens/PlaceDetailsScreen.kt` | Modify | `focusRing(…, OnSurface)` on Navigate |
| `app/src/main/java/com/kanyandula/discovernearby/ui/ReturnFocus.kt` | Create | `ReturnFocus`, `rememberReturnFocus()` |
| `…/ui/screens/DiscoverScreen.kt` | Modify | tiles use `ReturnFocus` |
| `…/ui/screens/RecommendationsScreen.kt` | Modify | rows use `ReturnFocus` |
| `app/src/test/java/…/ui/components/FocusRingTest.kt` | Modify | ring pixel checks for every control |
| `app/src/test/java/…/ui/ReturnFocusTest.kt` | Create | helper behaviour |
| `app/src/test/java/…/ui/DiscoverNavigationTest.kt` | Modify | rotary restore through real navigation |
| `docs/adr/0002/v7-retest-2026-10-06/` | Create | re-test scripts, readings, results, key frames |
| `docs/adr/0002-ui-stack-after-v7.md`, `docs/05-…` §9 V7, `CLAUDE.md` | Modify | the outcome |

(`…` = `app/src/main/java/com/kanyandula/discovernearby`.)

---

### Task 1: A visible ring on every actionable control

**Files:**
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/components/FocusRing.kt`
- Modify: `CategoryTile.kt`, `RecommendationRow.kt`, `ScreenHeader.kt`, `MessageState.kt`, `PlaceDetailsScreen.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/ui/components/FocusRingTest.kt`

**Interfaces:**
- Produces: `fun Modifier.focusRing(shape: Shape, color: Color = Accent): Modifier`, in package
  `com.kanyandula.discovernearby.ui.components`.

- [ ] **Step 1: Write the failing tests**

In `FocusRingTest.kt`:
- Add a shared `show` helper.
- Add four tests.
- Keep the two existing tile tests unchanged.

Add these imports:
- `androidx.compose.runtime.Composable`
- `androidx.compose.ui.test.onNodeWithContentDescription`
- `com.kanyandula.discovernearby.R`
- `com.kanyandula.discovernearby.discovery.testPlace`
- `com.kanyandula.discovernearby.model.Recommendation`
- `com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P`
- `com.kanyandula.discovernearby.ui.screens.PlaceDetailsScreen`
- `com.kanyandula.discovernearby.ui.screens.PlaceDetailsUiState`
- `com.kanyandula.discovernearby.ui.theme.Highlight`
- `com.kanyandula.discovernearby.ui.theme.OnSurface`
- `org.robolectric.RuntimeEnvironment`
- `org.robolectric.annotation.Config`

```kotlin
    private lateinit var inputModes: InputModeManager

    /** Rotary puts Compose in keyboard mode, where a clickable takes focus; touch never focuses one. */
    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            inputModes = LocalInputModeManager.current
            DiscoverNearbyTheme { content() }
        }
        rule.runOnIdle { inputModes.requestInputMode(InputMode.Keyboard) }
    }

    private fun text(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun focusedRecommendationRowShowsTheRing() {
        val place = testPlace("cafe-1", "cafe")
        show { RecommendationRow(Recommendation(place, score = 1.0, distanceMeters = 500), onClick = {}) }
        val row = rule.onNodeWithText(place.name)
        row.requestFocus()
        assertArrayEquals(Accent.rgb(), row.edgePixel().rgb(), TOLERANCE)
    }

    @Test
    fun focusedHeaderBackShowsTheRing() {
        show { ScreenHeader(title = "Coffee", onBack = {}) }
        val back = rule.onNodeWithContentDescription(text(R.string.back))
        back.requestFocus()
        assertArrayEquals(Accent.rgb(), back.edgePixel().rgb(), TOLERANCE)
    }

    // Navigate is filled with Action blue, so an Accent ring would barely show; it gets a light one.
    @Test
    @Config(qualifiers = AUTOMOTIVE_1024P)
    fun focusedNavigateShowsALightRing() {
        val place = testPlace("cafe-1", "cafe")
        show {
            PlaceDetailsScreen(
                distanceMeters = 500,
                state = PlaceDetailsUiState.SummaryOnly(place),
                onNavigate = {},
                onBack = {},
            )
        }
        val navigate = rule.onNodeWithText(text(R.string.navigate))
        navigate.requestFocus()
        assertArrayEquals(OnSurface.rgb(), navigate.edgePixel().rgb(), TOLERANCE)
    }

    // Error and permission states: Retry/Grant are Action blue (light ring), Back is Raised (Accent ring).
    @Test
    fun focusedMessageButtonsShowTheirRings() {
        show {
            MessageState(
                message = Message(R.drawable.ic_empty, Highlight, R.string.empty_title, R.string.empty_body),
                backLabel = R.string.back,
                onBack = {},
                onPrimary = {},
                primaryLabel = R.string.try_again,
            )
        }
        val retry = rule.onNodeWithText(text(R.string.try_again))
        retry.requestFocus()
        assertArrayEquals(OnSurface.rgb(), retry.edgePixel().rgb(), TOLERANCE)
        val back = rule.onNodeWithText(text(R.string.back))
        back.requestFocus()
        assertArrayEquals(Accent.rgb(), back.edgePixel().rgb(), TOLERANCE)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*FocusRingTest' --console=plain -q 2>&1 | grep -E "FocusRingTest > .* FAILED|tests completed" | head`

Expected:
- the four new tests FAIL with `arrays first differed at element`: M3's state layer is only a faint tint;
- the two tile tests pass.

If a test fails to compile because a name differs (for example the `testPlace` fixture's `name`, or a string
id), fix the test to the real name. Ledger it as a ruling.

- [ ] **Step 3: Implement**

Create `FocusRing.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.kanyandula.discovernearby.ui.theme.Accent
import com.kanyandula.discovernearby.ui.theme.FocusRingWidth

/**
 * Rotary focus (docs/02 §16, V7): a [FocusRingWidth] outline while the element after it in the chain has focus.
 * Touch never focuses a clickable, so touch never shows it. Pick a [color] that stands out from the fill.
 */
fun Modifier.focusRing(shape: Shape, color: Color = Accent): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(FocusRingWidth, color, shape) else Modifier)
}
```

`CategoryTile.kt`:
- delete `interactions`, `focused`, the `border =` and `interactionSource =` arguments, and their imports
  (`BorderStroke`, `MutableInteractionSource`, `collectIsFocusedAsState`, `getValue`, `remember`,
  `FocusRingWidth`);
- keep the comment's intent on the new line.

```kotlin
    val visual = category.visual
    val shape = RoundedCornerShape(TileRadius)
    // Rotary focus must be clearly visible (docs/02 §16).
    Surface(
        onClick = onClick,
        modifier = modifier.focusRing(shape).defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
```

`RecommendationRow.kt`:

```kotlin
    val place = recommendation.place
    val shape = RoundedCornerShape(RowRadius)
    Surface(
        onClick = onClick,
        modifier = modifier.focusRing(shape).fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
```

`ScreenHeader.kt`: add import `androidx.compose.foundation.shape.CircleShape`, then:

```kotlin
        IconButton(onClick = onBack, modifier = Modifier.focusRing(CircleShape).size(MinTouchTarget)) {
```

`MessageState.kt`, in `MessageButton`:
- add imports `com.kanyandula.discovernearby.ui.theme.Action` (if not already imported) and
  `com.kanyandula.discovernearby.ui.theme.OnSurface`;
- `focusRing` is in the same package.

```kotlin
private fun MessageButton(@StringRes label: Int, onClick: () -> Unit, container: Color, content: Color) {
    val shape = RoundedCornerShape(ButtonRadius)
    Button(
        onClick = onClick,
        // A primary (Action blue) button gets a light ring; an Accent ring would barely show on it.
        modifier = Modifier
            .focusRing(shape, if (container == Action) OnSurface else Accent)
            .defaultMinSize(minWidth = ButtonMinWidth, minHeight = MinTouchTarget),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
```

(Add import `com.kanyandula.discovernearby.ui.theme.Accent` too.)

`PlaceDetailsScreen.kt`, in `NavigateButton`: add imports `com.kanyandula.discovernearby.ui.components.focusRing`
and `com.kanyandula.discovernearby.ui.theme.OnSurface`, then:

```kotlin
private fun NavigateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(NavigateRadius)
    Button(
        onClick = onClick,
        // Action blue fill: a light ring stands out where an Accent one would not.
        modifier = modifier.focusRing(shape, OnSurface).height(NavigateHeight),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Action, contentColor = Color.White),
    ) {
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*FocusRingTest' --console=plain -q && echo pass`
Expected: `pass`, with all six FocusRingTest tests passing.

- [ ] **Step 5: Full check and commit**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q && echo pass`
Expected: `pass`.

If lint flags `composed`, use the `onFocusChanged` + `border` pair in a `Modifier.Node` instead, and ledger the
ruling. The tests stay the same.

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui app/src/test/java/com/kanyandula/discovernearby/ui/components/FocusRingTest.kt
git commit -m "Show the rotary focus ring on every actionable control"
```

---

### Task 2: Return rotary focus after Back, one frame late

**Files:**
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/ReturnFocus.kt`
- Modify: `…/ui/screens/DiscoverScreen.kt`, `…/ui/screens/RecommendationsScreen.kt`
- Test: create `app/src/test/java/com/kanyandula/discovernearby/ui/ReturnFocusTest.kt`; modify
  `…/ui/DiscoverNavigationTest.kt`

**Interfaces:**
- Consumes: `CategoryTile` and `RecommendationRow`, which take a `modifier` (Task 1 keeps that).
- Produces, in package `com.kanyandula.discovernearby.ui`:
  - `class ReturnFocus` with `fun item(key: String): Modifier` and `fun selected(key: String)`;
  - `@Composable fun rememberReturnFocus(): ReturnFocus`.

- [ ] **Step 1: Write the failing navigation tests**

In `DiscoverNavigationTest.kt`, add these imports:
- `androidx.compose.ui.input.InputMode`
- `androidx.compose.ui.input.InputModeManager`
- `androidx.compose.ui.input.key.Key`
- `androidx.compose.ui.platform.LocalInputModeManager`
- `androidx.compose.ui.test.assertIsFocused`
- `androidx.compose.ui.test.assertIsNotFocused`
- `androidx.compose.ui.test.performKeyInput`
- `androidx.compose.ui.test.pressKey`
- `androidx.compose.ui.test.requestFocus`

Then make the content capture the input-mode manager. Change `setUp`'s `setContent` to:

```kotlin
        rule.setContent {
            inputModes = LocalInputModeManager.current
            DiscoverNearbyTheme { DiscoverNearbyApp(appContainer()) }
        }
```

and add, next to `systemBack()`:

```kotlin
    private lateinit var inputModes: InputModeManager

    /** A rotary select: keyboard mode, focus on the node, then the centre key, as RotaryService injects it. */
    private fun rotarySelect(node: SemanticsNodeInteraction) {
        rule.runOnIdle { inputModes.requestInputMode(InputMode.Keyboard) }
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    // docs/02 §16, V7: after Back, rotary focus returns to the tile it left from.
    @Test
    fun rotaryFocusReturnsToTheOriginatingTile() {
        rotarySelect(rule.onNodeWithText("Family"))
        systemBack()
        rule.onNodeWithText("Family").assertIsFocused()
    }

    // V7: after Back from Details, focus returns to the row, so the next turn moves on from it.
    @Test
    fun rotaryFocusReturnsToTheOriginatingRow() {
        rotarySelect(rule.onNodeWithText("Family"))
        rotarySelect(rule.onNodeWithText("Adventure Playground, Greystones"))
        systemBack()
        rule.onNodeWithText("Adventure Playground, Greystones").assertIsFocused()
    }

    // Review Focus 2: a touch selection leaves no focus, so no ring appears on return.
    @Test
    fun touchSelectionLeavesNoFocusBehind() {
        rule.onNodeWithText("Family").performClick()
        systemBack()
        rule.onNodeWithText("Family").assertIsNotFocused()
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiscoverNavigationTest' --console=plain -q 2>&1 | grep -E "FAILED|tests completed" | head`

Expected:
- `rotaryFocusReturnsToTheOriginatingTile` and `rotaryFocusReturnsToTheOriginatingRow` FAIL
  (`Failed to assert … Focused = 'true'`);
- `touchSelectionLeavesNoFocusBehind` passes, because it guards the restore from leaking to touch.

- [ ] **Step 3: Write the helper's failing tests**

Create `ReturnFocusTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// V7 (ADR-002): Back gives rotary focus back to the item selected by rotary, a frame after the screen returns.
@RunWith(RobolectricTestRunner::class)
class ReturnFocusTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var inputModes: InputModeManager
    private var keys by mutableStateOf(listOf("A", "B", "C"))
    private var shown by mutableStateOf(true)

    @Composable
    private fun Items() {
        val returnFocus = rememberReturnFocus()
        Column {
            keys.forEach { key ->
                Button(onClick = { returnFocus.selected(key) }, modifier = returnFocus.item(key)) { Text(key) }
            }
        }
    }

    /** The screen leaves and comes back with its saved state, as a NavHost destination does. */
    private fun showItems() {
        rule.setContent {
            inputModes = LocalInputModeManager.current
            val holder = rememberSaveableStateHolder()
            if (shown) holder.SaveableStateProvider("screen") { Items() }
        }
    }

    private fun rotarySelect(text: String) {
        rule.runOnIdle { inputModes.requestInputMode(InputMode.Keyboard) }
        rule.onNodeWithText(text).requestFocus()
        rule.onNodeWithText(text).performKeyInput { pressKey(Key.DirectionCenter) }
    }

    private fun leaveAndReturn(newKeys: List<String> = keys) {
        rule.runOnIdle { shown = false }
        rule.runOnIdle {
            keys = newKeys
            shown = true
        }
        rule.waitForIdle()
    }

    @Test
    fun rotarySelectionGetsFocusBack() {
        showItems()
        rotarySelect("B")
        leaveAndReturn()
        rule.onNodeWithText("B").assertIsFocused()
    }

    @Test
    fun touchSelectionReplacesARotarySelection() {
        showItems()
        rotarySelect("B")
        rule.onNodeWithText("C").performClick() // touch: switches to touch mode, so nothing is remembered
        leaveAndReturn()
        rule.onNodeWithText("B").assertIsNotFocused()
        rule.onNodeWithText("C").assertIsNotFocused()
    }

    // Review Focus 1: the item is gone when the screen returns (e.g. the driving limit trimmed the list).
    @Test
    fun returnToAKeyNoLongerShownDoesNothing() {
        showItems()
        rotarySelect("C")
        leaveAndReturn(listOf("A", "B"))
        rule.onNodeWithText("A").assertIsNotFocused()
        rule.onNodeWithText("B").assertIsNotFocused()
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReturnFocusTest' --console=plain -q 2>&1 | grep -E "error:|FAILED" | head -3`
Expected: compilation fails with `Unresolved reference: rememberReturnFocus`.

- [ ] **Step 5: Implement**

Create `ReturnFocus.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * Rotary focus after Back (docs/02 §16, V7). Remembers the item last selected by rotary and gives it focus again
 * one frame after its screen returns.
 *
 * Why a frame late: on 2026-10-06 (ADR-002, E1), focus that Compose set in a returning screen's first frame was
 * never reported to the rotary service. The service kept the ComposeView host, and the driver's next turn was
 * spent finding focus. A change a frame later is reported, as it is when a screen opens.
 *
 * A touch selection is not remembered, so touch never leaves a ring behind.
 */
class ReturnFocus internal constructor(
    private val target: MutableState<String?>,
    private val inputModes: InputModeManager,
) {
    private val requester = FocusRequester()

    /** The modifier for the item identified by [key]. */
    fun item(key: String): Modifier = if (key == target.value) Modifier.focusRequester(requester) else Modifier

    /** Call from the item's onClick: rotary (keyboard mode) selections are remembered, touch ones clear it. */
    fun selected(key: String) {
        target.value = key.takeIf { inputModes.inputMode == InputMode.Keyboard }
    }

    // ponytail: requestFocus returns false when the item is gone (e.g. trimmed by the driving limit); nothing moves.
    internal suspend fun restore() {
        if (target.value == null) return
        withFrameNanos { }
        requester.requestFocus()
    }
}

@Composable
fun rememberReturnFocus(): ReturnFocus {
    val target = rememberSaveable { mutableStateOf<String?>(null) }
    val inputModes = LocalInputModeManager.current
    val returnFocus = remember { ReturnFocus(target, inputModes) }
    LaunchedEffect(returnFocus) { returnFocus.restore() }
    return returnFocus
}
```

`DiscoverScreen.kt`: add import `com.kanyandula.discovernearby.ui.rememberReturnFocus`, then:

```kotlin
@Composable
fun DiscoverScreen(onCategorySelected: (DiscoveryCategory) -> Unit, modifier: Modifier = Modifier) {
    val returnFocus = rememberReturnFocus()
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GridGap)) {
        Rows.forEach { row ->
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                row.forEach { category ->
                    CategoryTile(
                        category = category,
                        onClick = {
                            returnFocus.selected(category.name)
                            onCategorySelected(category)
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight().then(returnFocus.item(category.name)),
                    )
                }
            }
        }
    }
}
```

`RecommendationsScreen.kt`: add import `com.kanyandula.discovernearby.ui.rememberReturnFocus`. In
`RecommendationsScreen`, call `val returnFocus = rememberReturnFocus()` as the first line of the function body,
outside the `when`, so it survives state changes on the one destination. Then:

```kotlin
                    items(state.recommendations, key = { it.place.id }) { recommendation ->
                        RecommendationRow(
                            recommendation,
                            onClick = {
                                returnFocus.selected(recommendation.place.id)
                                onPlaceSelected(recommendation)
                            },
                            modifier = returnFocus.item(recommendation.place.id),
                        )
                    }
```

- [ ] **Step 6: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReturnFocusTest' --tests '*DiscoverNavigationTest' --console=plain -q && echo pass`
Expected: `pass`.

- [ ] **Step 7: Full check and commit**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q && echo pass`
Expected: `pass`.

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui app/src/test/java/com/kanyandula/discovernearby/ui
git commit -m "Return rotary focus to the selected item after Back"
```

- [ ] **Step 8: Quick emulator check before the re-test (no `uiautomator`)**

This is one journey, to confirm the fix acts on the real service before Task 3 spends a cold boot.

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain -q
R=docs/adr/0002/v7-retest-2026-10-06; mkdir -p $R/scripts
cp docs/adr/0002/e1-2026-10-06/scripts/{setup.sh,waitfor.sh} $R/scripts/
```

Write `$R/scripts/v7gate.sh`. It is `v7bar.sh` with three changes:
- it opens a **middle** row, so a restored focus and an absorbed turn can be told apart;
- the turn after the first Back is counter-clockwise (Navigate is the last element on Details);
- it has an optional `uiautomator` control wait.

```bash
#!/bin/bash
# V7 gate journey (DN-M0-011 re-test). $1 = run id, $2 = park|drive, $3 = control (optional: uiautomator wait).
# Discover: cw ×7, ccw ×2 → Family, select. Recommendations: cw ×6 (every element + end), ccw ×2 → row 2,
# select. Details: cw ×2, ccw, cw → Navigate, select (wait for the stub). Back → Details, ccw; Back →
# Recommendations, cw; Back → Discover, cw.
E=$(dirname "$0"); O="$E/../runs/$1"; mkdir -p "$O"
d() { adb -s emulator-5554 shell "$@"; }
top() { d dumpsys activity activities | grep -m1 topResumedActivity | grep -oE 'u10 [^ /]+' | cut -c 5-; }
foc() {
  d dumpsys activity service com.android.car.rotary/.RotaryService | grep -E '^\s+focusedNode=' \
    | grep -oE 'boundsInScreen: Rect\([^)]*\)' | head -1 | sed 's/boundsInScreen: //'
}
n=0
rec() {
  n=$((n + 1)); local f; f=$(foc)
  printf '%02d %-14s %-28s %s\n' "$n" "$1" "${f:-none}" "$(top)" >> "$O/steps.txt"
  adb -s emulator-5554 exec-out screencap -p > "$O/$(printf %02d "$n")-$1.png"
}
cw() { d cmd car_service inject-rotary -c true >/dev/null; sleep 1; rec "$1"; }
ccw() { d cmd car_service inject-rotary -c false >/dev/null; sleep 1; rec "$1"; }
key() { d cmd car_service inject-key "$1" >/dev/null; sleep 3; rec "$2"; }

echo "v7gate $1 ($2${3:+, $3}) $(date '+%F %T')" > "$O/steps.txt"
if [ "$2" = drive ]; then
  d cmd car_service inject-vhal-event 0x11400400 8 >/dev/null
  d cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 240 >/dev/null 2>&1 &
  sleep 3
  echo "driving: $(d dumpsys car_service --services CarDrivingStateService | grep 'changed from' | tail -1 | tr -s ' ')" >> "$O/steps.txt"
fi
adb -s emulator-5554 logcat -c
d am start -S -n com.kanyandula.discovernearby/.ui.MainActivity >/dev/null 2>&1
if [ "$3" = control ]; then "$E/waitfor.sh" Coffee 40 >/dev/null; sleep 2; else sleep 14; fi
rec start
for i in 1 2 3 4 5 6 7; do cw "discover-cw$i"; done
ccw discover-ccw1; ccw discover-ccw2
key 23 recs-open
for i in 1 2 3 4 5 6; do cw "recs-cw$i"; done
ccw recs-ccw1; ccw recs-ccw2
key 23 details-open
cw details-cw1; cw details-cw2; ccw details-ccw1; cw details-cw3
d cmd car_service inject-key 23 >/dev/null
for _ in $(seq 1 15); do sleep 1; [ "$(top)" = com.kanyandula.stubnavigation ] && break; done
rec navigate
key 4 back-1; ccw back-1-turn
key 4 back-2; cw back-2-turn
key 4 back-3; cw back-3-turn
echo "stub received: $(adb -s emulator-5554 logcat -d -s StubNav:I | grep -c 'received')" >> "$O/steps.txt"
adb -s emulator-5554 logcat -d -v time -s RotaryController:V > "$O/rotary.log"
if [ "$2" = drive ]; then
  echo "driving at end: $(d dumpsys car_service --services CarDrivingStateService | grep 'changed from' | tail -1 | tr -s ' ')" >> "$O/steps.txt"
  d cmd car_service inject-vhal-event 0x11400400 4 >/dev/null
fi
```

Then run one journey and read it (`R` as above):

```bash
chmod +x $R/scripts/*.sh
adb -s emulator-5554 shell am force-stop --user 10 com.kanyandula.calprobe
$R/scripts/v7gate.sh check-1 park
sed -n '/recs-ccw2/,$p' $R/runs/check-1/steps.txt
```

**Expected** (bounds are compared with earlier steps, because the list scrolls):
- `back-2` reads the same bounds as `recs-ccw2`, the row that was opened; the service follows the restored row;
- `back-2-turn` reads different bounds, neither the host nor the header Back;
- `back-3` reads Family (`Rect(48, 400 - 344, 624)`, as `discover-ccw2`), and `back-3-turn` reads Scenic
  (`Rect(364, 400 - 660, 624)`);
- the screenshots show the ring on the opened row and on Family.

**If `back-2` is still the host** (`Rect(0, 76 - 1024, 672)`) and `back-2-turn` lands on the header Back, the
fix did not reach the service. **Stop.** Ledger it, and go straight to Task 4's **fail** branch: one bounded
fix, no second attempt (ADR-002). Do not try other mechanisms without the user.

---

### Task 3: Clean re-test against the V7 gate

**Files:**
- Create: `docs/adr/0002/v7-retest-2026-10-06/` (`runs/`, `results.md`, `shots/`, `rotary-logs.tar.gz`)

**Interfaces:**
- Consumes: the installed build from Task 2 Step 8, and `scripts/v7gate.sh`.

- [ ] **Step 1: Cold boot (the same conditions as E1)**

Start the emulator as a background task (not `&` in a foreground shell):

```bash
cd ~/AndroidStudioProjects/DiscoverNearby; R=docs/adr/0002/v7-retest-2026-10-06
adb -s emulator-5554 emu kill; sleep 15
~/Library/Android/sdk/emulator/emulator -avd AAOS_AOSP_33_userdebug -port 5554 -no-snapshot-load   # background task
$R/scripts/setup.sh
```

Wait until `adb -s emulator-5554 shell cat /proc/loadavg` reads below 2.5.

Expected: `user 10`, `focusedNode=null`, `inRotaryMode=false`, driving `changed from -1 to 0`.

- [ ] **Step 2: Behavioural runs (no `uiautomator`): three parked, one in Drive**

```bash
for r in 1 2 3; do $R/scripts/v7gate.sh "park$r" park; done
$R/scripts/v7gate.sh drive1 drive
```

- [ ] **Step 3: The `uiautomator` control (reported separately)**

```bash
$R/scripts/v7gate.sh control1 park control
```

- [ ] **Step 4: Visible focus on an error state's buttons**

```bash
d() { adb -s emulator-5554 shell "$@"; }; O=$R/runs/error1; mkdir -p $O
d am start -S -n com.kanyandula.discovernearby/.ui.MainActivity --es scenario NETWORK_FAILURE >/dev/null; sleep 14
d cmd car_service inject-rotary -c true >/dev/null; sleep 1   # Coffee
d cmd car_service inject-key 23 >/dev/null; sleep 6           # Recommendations: the network-error message
for i in 1 2 3; do d cmd car_service inject-rotary -c true >/dev/null; sleep 1
  adb -s emulator-5554 exec-out screencap -p > $O/0$i-turn.png; done
```

Expected: across the three shots, the ring appears on the header Back, then "Try again" (light ring), then "Back"
(Accent ring). That order is the layout order; record the order actually seen.

- [ ] **Step 5: Score each run against the gate**

For each of `park1–3` and `drive1` (and separately `control1`), read `runs/<id>/steps.txt` and the key frames.

| Gate condition | Pass when |
| --- | --- |
| Rotary reaches Navigate | some `details-*` step reads `Rect(576, 536 - 976, 624)` |
| Selection activates the focused control | `recs-open` shows the Family list (screenshot title); `navigate` top is `com.kanyandula.stubnavigation` and `stub received` ≥ 1 |
| Back without losing a turn | each `back-N-turn` reads different bounds from its `back-N`, no `back-N` reads the host `Rect(0, 76 - 1024, 672)`, and the screenshot after each turn shows the ring moved |
| Visible focus on every actionable control | the ring is visible in the screenshots on a tile (`discover-cw4`), a row (`recs-cw2`), header Back (`details-ccw1`), Navigate (`details-cw1`), and on Try again and Back (`error1`) |

Record where focus lands after Back (row 2? Family?) as recorded-only.

Write `results.md`:
- conditions;
- a table with one row per run and one column per gate condition;
- the control on its own;
- the error-state shots;
- rulings;
- the limitation: adb injection, not the manual Extended Controls run.

Then file the record:

```bash
cd $R/runs && tar -czf ../rotary-logs.tar.gz */rotary.log && cd -
mkdir -p $R/shots
for r in park1 drive1 control1; do
  for s in discover-cw4 recs-open recs-cw2 recs-ccw2 details-ccw1 details-cw1 back-2 back-2-turn back-3 back-3-turn; do
    for f in $R/runs/$r/*-$s.png; do [ -f "$f" ] && cp "$f" "$R/shots/$r-$(basename "$f")"; done
  done
done
for f in $R/runs/error1/*.png; do cp "$f" "$R/shots/error1-$(basename "$f")"; done
rm -f $R/runs/*/*.png   # the full set stays out of the repo (E1 precedent); key frames are in shots/
```

**V7 passes** only if all four behavioural runs meet all four conditions. The control and the manual run do not
change this result; both are recorded beside it.

---

### Task 4: Record the outcome

**Files:**
- Modify:
  - `docs/adr/0002-ui-stack-after-v7.md` (status, a new "V7 re-test after the bounded fix" subsection, Consequences);
  - `docs/05-discover-nearby-delivery-plan.md` §9 V7 row and the header's current gates;
  - `CLAUDE.md` (the rotary line, and the no-restore rule);
  - `docs/04-discover-nearby-test-demo-plan.md` Scenario F (no change unless the outcome changes the method);
  - the vault: the DN-M0-011 ticket, `NOW.md`, `BACKLOG.md`.

- [ ] **Step 1: Write the ADR-002 subsection**

Add `### V7 re-test after the bounded fix (DN-M0-011, <run date>)`, linking `0002/v7-retest-2026-10-06/results.md`. It
covers:
- what changed (the ring helper; restore one frame late);
- the gate table;
- the control, separately;
- the manual Extended Controls run: still its own section, unchanged unless the user has done it.

- [ ] **Step 2a: Pass branch** (all four behavioural runs pass every condition)

- **ADR-002 status:** "Accepted for the emulator POC: B, Compose. V7 passed on the clean adb-driven re-test
  (<run date>); the manual Extended Controls run is recorded separately."
- **docs/05:**
  - V7 → 🟢 **Passed (adb-driven re-test, <run date>)**, with the same note;
  - current gates: M0 exit no longer waits on V7.
- **CLAUDE.md:**
  - replace "No in-app focus restore after Back for now…" with "Back returns rotary focus to the item selected
    by rotary, one frame late (`ReturnFocus`; V7, ADR-002)";
  - the rotary line → V7 passed (adb), with the gate unchanged.
- **Ticket:** DN-M0-011 → `done` after merge. Add completion notes: the gate table and the PR.
- **Follow-up, not in this PR:** propose deleting `tools/cal-rotary-probe/` (ADR-002 Consequences), and starting
  DN-UX-001.

- [ ] **Step 2b: Fail branch** (any behavioural run misses any condition, or Task 2 Step 8 stopped)

- **ADR-002:**
  - status → "**Reopened (<run date>):** V7 failed after the one bounded Compose fix";
  - the Decision section stays as history, with a dated note under it;
  - Consequences: the decision returns to the Product Lead with both options' evidence.
- **docs/05:** V7 → 🔴 **Failed (re-test, <run date>)**; ADR-002 reopened.
- **CLAUDE.md:** the same facts.
- **Ticket:** DN-M0-011 → `blocked` on ADR-002.
- **The code:** keep it if it improved anything measurable (for example the ring), and say so in the PR. Never
  mark V7 complete, and make no further fix attempts.

- [ ] **Step 3: Commit the record**

```bash
git add docs CLAUDE.md
git commit -m "Record the V7 re-test after the bounded fix"
```

---

## After the tasks

- Final whole-branch review, on the most capable model (executing-plans). Then the `simplify` skill on the
  `app/` diff, fixing its findings.
- Run `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`, then push and open the PR with
  `pr-description` (DN-M0-011, the gate and its result).
- Step 6 after merge: update the tickets, `NOW.md` and `BACKLOG.md`, and delete the branch.
