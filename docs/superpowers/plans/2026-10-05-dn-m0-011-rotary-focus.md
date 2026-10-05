# DN-M0-011 Rotary Focus Proof (V7) Implementation Plan

> **Superseded in part — read [Outcome (2026-10-05)](#outcome-2026-10-05-v7-not-workable) first.** The emulator
> disproved the investigation's premise (the ComposeView was already in the accessibility tree; the "stuck"
> baseline was a uiautomator artefact), Task 3 was dropped and V7 is Not workable.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove V7 on the reference emulator: the AAOS rotary controller moves focus through the six Discover tiles in UX order, focus is clearly visible, select opens the focused category, Back returns focus to the originating tile, and nothing is unreachable or trapped — then record the outcome in the ticket and the docs/05 §9 register.

**Architecture:** Three small app changes, each found by the investigation below. `MainActivity` hosts its own `ComposeView` marked important for accessibility, so Android 13's RotaryController finds the Compose root and walks its focusable nodes. `CategoryTile` draws a focus ring from its interaction source. `DiscoverScreen` remembers which tile had focus when it was selected and gives it focus back when the user returns.

**Tech Stack:** Compose focus APIs (`FocusRequester`, `onFocusChanged`, `MutableInteractionSource.collectIsFocusedAsState`), Robolectric 4.17 (native graphics for a pixel check), `adb shell cmd car_service inject-rotary` / `inject-key`, RotaryService `dumpsys`.

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-011-rotary-focus-proof.md`; `docs/02-discover-nearby-ux-interaction-spec.md` §16; `docs/03-discover-nearby-engineering-implementation-plan.md` §17; `docs/04-discover-nearby-test-demo-plan.md` §2 (Rotary input), §5 F; `docs/05-discover-nearby-delivery-plan.md` §9 (V7); AOSP `packages/apps/Car/RotaryController` (android13-release) `Navigator.java`, `Utils.java`.

## Global Constraints

- Discover order: Coffee → Food → Outdoors → Family → Scenic → Explore (docs/02 §16).
- Focus clearly visible on each tile; select opens the focused category; Back returns to Discover with focus on the originating tile; nothing unreachable, never trapped or lost (ticket AC).
- Verified on `AAOS_AOSP_33_userdebug` with rotary input only, no touch (ticket AC). Scripted rotary goes through the same path as Extended Controls → Car rotary: `cmd car_service inject-rotary` / `inject-key` → CarInputService → RotaryService.
- The outcome is one of **Proven**, **Proven with workarounds** (listed), **Not workable**; recorded in the ticket and the docs/05 §9 V7 row through this PR (ticket AC).
- Only `ui/` imports Compose; `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes; never commit on local `main`; no AI attribution.

## Open decisions (recorded here, in the ticket and the PR)

1. **The workaround is one attribute, not a rotary implementation.** Android 13's RotaryController already supports Compose: `Navigator.findVirtualViewAncestor` looks for a node whose class name is `androidx.compose.ui.platform.ComposeView` and then walks focusable descendants depth-first, focusing each with `ACTION_FOCUS`. Our `ComposeView` reports that class name, but a `ViewGroup` with no content is left out of the accessibility tree, so the service never saw it. Marking it `IMPORTANT_FOR_ACCESSIBILITY_YES` is enough. `MainActivity` builds the `ComposeView` itself (instead of `setContent`) so the attribute sits next to the content it serves.
2. **Focus ring:** a 4 dp outline in the theme's primary colour (`#4C8DF6`, the canvas Recommendations focus outline), drawn over the tile only while it has focus. Touch never focuses a tile, so touch users never see it.
3. **Focus returns after Back only to a tile that had focus when selected** (a rotary selection). A touch selection leaves no focus behind, so no ring appears for touch users on return.
4. **Scope is the Discover grid** (the ticket's V7 proof). The attribute also lets rotary reach the other screens; Task 4 records what it finds there (rows, message buttons, Navigate) without fixing it — follow-ups go to the ticket notes.

## Review Focus

1. **Rotation at either end of the grid** (past Explore, before Coffee): focus stays on a tile, not lost. Pinned by emulator Task 4 Step 2.
2. **A touch selection, then Back:** no tile shows a focus ring. Pinned by `DiscoverNavigationTest.touchSelectionLeavesNoFocusBehind`.
3. **Rotation right after Back** continues from the restored tile rather than jumping to Coffee (the rotary service's own idea of focus). Pinned by emulator Task 3 Step 6; recorded either way.
4. **Select opens the focused tile, not the first.** Pinned by emulator Task 1 Step 5 (select on Family opens Family).
5. **The ring is visible against the dark tile:** the edge pixel changes from the tile colour to the primary colour on focus. Pinned by `FocusRingTest.focusedTileShowsTheRing`.

---

## Investigation findings (2026-10-05)

All on `AAOS_AOSP_33_userdebug` (emulator-5554), with `com.android.car.rotary/.RotaryService` enabled for user 10.

- **Baseline (today's code):** the first `inject-rotary -c true` focuses Coffee; further clockwise/counter-clockwise turns and the four nudges (`inject-key 280–283`) leave focus on Coffee; select (`inject-key 23`) does nothing; Back (`inject-key 4`) works as system Back. RotaryService `dumpsys` shows its `focusedNode` as one `android.view.View` spanning the panel — it cannot see inside Compose.
- **Compose itself is fine:** plain D-pad keys (`input keyevent 22/20/21`) move focus Coffee → Food → Scenic → Family and `23` opens the focused category.
- **Spike (not committed):** with the root `ComposeView` marked important for accessibility, five clockwise turns moved real Compose focus (logged with `onFocusChanged`) Coffee → Food → Outdoors → Family → Scenic, the RotaryService `focusedNode` followed (bounds per tile), and select opened Scenic. The `ComposeView`'s `accessibilityClassName` is already `androidx.compose.ui.platform.ComposeView`.
- **After Back** (spike, no restoration): Compose focus lands on Coffee, not the originating Family.
- `uiautomator dump`'s `focused` attribute lags Compose focus; trust RotaryService `dumpsys` (`focusedNode` bounds) and screenshots instead.
- Tile bounds on the emulator (px): Coffee `[48,156][344,380]`, Food `[364,156][660,380]`, Outdoors `[680,156][976,380]`, Family `[48,400][344,624]`, Scenic `[364,400][660,624]`, Explore `[680,400][976,624]`.

## Outcome (2026-10-05): V7 Not workable

- **Pass criteria revised.** The full journey (Discover → Recommendations → Place Details → Navigate) and its Back path must work by rotary (UX spec §16). Failures on Recommendations and Details count against V7; they are not follow-ups, as Task 4 Step 3 first assumed.
- **Task 1: no production change.** Compose's root already qualifies for the rotary service, so MainActivity is unchanged; `RotaryContractTest` pins that precondition. Open decision 1 and the Architecture line about marking the ComposeView no longer apply.
- **Review Focus 2** (touch leaves no ring) is pinned by `FocusRingTest.touchedTileShowsNoRing`, since Task 3's test went with it.
- **Task 3 dropped.** Restoring focus to the originating tile left the rotary service unable to move after Back. Without it, Back focuses Coffee.
- **Task 4.**
  - Grid ends hold: Explore and Coffee, with no wrap.
  - Nudges are no-ops, because the window is one implicit focus area.
  - On Place Details, focus isn't visible, rotation is stuck and Navigate is unreachable.
  - After Back twice, rotation is stuck on Discover.
- **Cause and the three time-boxed workarounds:** see ADR-002. Each workaround varied from run to run.
- **Shipped:** the focus ring (Task 2) and `RotaryContractTest` (Task 1). Escalated: [ADR-002](../../adr/0002-ui-stack-after-v7.md).

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `…/ui/MainActivity.kt` | Modify | Own `ComposeView`, important for accessibility |
| `…/ui/components/CategoryTile.kt` | Modify | Focus ring from the interaction source |
| `…/ui/theme/Dimens.kt` | Modify | `FocusRingWidth` |
| `…/ui/screens/DiscoverScreen.kt` | Modify | Remember the focused tile; restore after Back |
| `…/test/…/ui/RotaryContractTest.kt` | Create | The root is visible to the rotary service |
| `…/test/…/ui/components/FocusRingTest.kt` | Create | Ring pixel on focus (native graphics) |
| `…/test/…/ui/DiscoverNavigationTest.kt` | Modify | Focus restored after Back; none after a touch selection |
| `docs/05-discover-nearby-delivery-plan.md` | Modify | V7 row |

`…` = `app/src/main/java/com/kanyandula/discovernearby`; `…/test/…` = `app/src/test/java/com/kanyandula/discovernearby`.

---

### Task 0: Start the ticket

- [ ] **Step 1:** In the ticket set `status: in_progress`, `branch: dn-m0-011-rotary-focus`.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-011-rotary-focus
git add docs/superpowers/plans/2026-10-05-dn-m0-011-rotary-focus.md
git commit -m "Add DN-M0-011 implementation plan"
```

---

### Task 1: Let the rotary service see the Compose root

**Files:**
- Create: `app/src/test/java/com/kanyandula/discovernearby/ui/RotaryContractTest.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt`

**Interfaces:**
- Produces: `MainActivity`'s content view is a `ComposeView` with `importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_YES`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/ui/RotaryContractTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

// V7: Android 13's RotaryController (Navigator.findVirtualViewAncestor) moves focus through Compose only below a
// node with exactly this class name, and a ViewGroup stays out of the accessibility tree unless marked important.
@RunWith(RobolectricTestRunner::class)
class RotaryContractTest {

    @Test
    fun composeRootIsVisibleToTheRotaryService() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, root.importantForAccessibility)
        assertEquals("androidx.compose.ui.platform.ComposeView", root.accessibilityClassName.toString())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RotaryContractTest' --console=plain -q 2>&1 | grep -E "tests completed|AssertionError" | head -2`
Expected: 1 failed — `expected:<1> but was:<0>` (`IMPORTANT_FOR_ACCESSIBILITY_AUTO`).

- [ ] **Step 3: Implement**

`MainActivity.kt`: replace the `setContent { … }` block with:

```kotlin
        // V7: Android 13's rotary service moves focus through Compose only below a ComposeView node in the
        // accessibility tree (RotaryController Navigator.findVirtualViewAncestor), and a ViewGroup is left out of
        // that tree unless marked important. Without this, rotation focuses the first tile and stops there.
        setContentView(
            ComposeView(this).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                setContent {
                    DiscoverNearbyTheme {
                        DiscoverNearbyApp(container)
                    }
                }
            },
        )
```

Imports: add `android.view.View` and `androidx.compose.ui.platform.ComposeView`; remove `androidx.activity.compose.setContent`.

- [ ] **Step 4: Run the suite**

Run: `./gradlew :app:testDebugUnitTest --console=plain -q && echo pass`
Expected: `pass` (`RotaryContractTest` 1/1; `ScenarioExtraTest` and the navigation tests unchanged).

- [ ] **Step 5: Rotation order and select on the emulator**

Helpers from earlier tickets live in this session's scratchpad (`launch.sh`, `waitfor.sh`, `pause.sh`, `texts.sh`); every block sets `S=<scratchpad>`. Add `rfocus.sh` once:

```bash
S=<scratchpad>
cat > "$S/rfocus.sh" <<'EOF'
#!/bin/bash
# Print the tile (or bounds) the rotary service believes is focused.
b=$(adb -s emulator-5554 shell dumpsys activity service com.android.car.rotary/.RotaryService 2>/dev/null \
  | grep -E "^\s+focusedNode=" | grep -oE "boundsInScreen: Rect\([^)]*\)" | head -1)
case "$b" in
  *"48, 156 - 344, 380"*) echo Coffee ;; *"364, 156 - 660, 380"*) echo Food ;;
  *"680, 156 - 976, 380"*) echo Outdoors ;; *"48, 400 - 344, 624"*) echo Family ;;
  *"364, 400 - 660, 624"*) echo Scenic ;; *"680, 400 - 976, 624"*) echo Explore ;;
  *) echo "${b:-none}" ;;
esac
EOF
chmod +x "$S/rfocus.sh"
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain | grep -E "Installed on|BUILD"
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
for i in 1 2 3 4 5 6; do
  adb -s emulator-5554 shell cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; echo "cw $i: $("$S/rfocus.sh")"
done
for i in 1 2; do
  adb -s emulator-5554 shell cmd car_service inject-rotary -c false >/dev/null; "$S/pause.sh" 1; echo "ccw $i: $("$S/rfocus.sh")"
done
adb -s emulator-5554 shell cmd car_service inject-key 23 >/dev/null; "$S/pause.sh" 2; "$S/texts.sh" | sed -n 2p
```

Expected: cw 1–6 → Coffee, Food, Outdoors, Family, Scenic, Explore; ccw 1–2 → Scenic, Family; select opens **Family** (the title reads "Family"). Record the output.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/RotaryContractTest.kt
git commit -m "Let the rotary service move focus through Compose"
```

---

### Task 2: A visible focus ring on the tiles

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/theme/Dimens.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/components/CategoryTile.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/ui/components/FocusRingTest.kt`

**Interfaces:**
- Produces: `val FocusRingWidth = 4.dp`; `CategoryTile` draws a `FocusRingWidth` outline in `MaterialTheme.colorScheme.primary` while focused.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/ui/components/FocusRingTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.Accent
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.SurfaceVariant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

// docs/02 §16: focus must be clearly visible. Native graphics, so the capture draws real pixels.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusRingTest {

    @get:Rule
    val rule = createComposeRule()

    /** The colour 2 px inside the tile's left edge, halfway down: inside the ring when it is drawn. */
    private fun SemanticsNodeInteraction.edgePixel(): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[2, pixels.height / 2]
    }

    private fun assertNear(expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < TOLERANCE &&
            abs(expected.green - actual.green) < TOLERANCE &&
            abs(expected.blue - actual.blue) < TOLERANCE
        assertTrue("expected about $expected, was $actual", close)
    }

    @Test
    fun focusedTileShowsTheRing() {
        rule.setContent {
            DiscoverNearbyTheme {
                CategoryTile(DiscoveryCategory.COFFEE, onClick = {}, modifier = Modifier.size(300.dp, 200.dp))
            }
        }
        val tile = rule.onNodeWithText("Coffee")
        assertNear(SurfaceVariant, tile.edgePixel())
        tile.requestFocus()
        assertNear(Accent, tile.edgePixel())
    }

    private companion object {
        const val TOLERANCE = 0.02f
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*FocusRingTest' --console=plain -q 2>&1 | grep -E "tests completed|expected about" | head -2`
Expected: 1 failed — `expected about Color(0.298…, 0.552…, 0.964…)` (the ring is not drawn yet).

- [ ] **Step 3: Implement**

`Dimens.kt`, append:

```kotlin

// Rotary focus (docs/02 §16): the canvas marks the focused row with a 4 px primary outline.
val FocusRingWidth = 4.dp
```

`CategoryTile.kt`:

```kotlin
@Composable
fun CategoryTile(category: DiscoveryCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val visual = category.visual
    val shape = RoundedCornerShape(TileRadius)
    val interactions = remember { MutableInteractionSource() }
    // Rotary focus must be clearly visible (docs/02 §16); touch never focuses a tile, so touch never shows it.
    val focused by interactions.collectIsFocusedAsState()
    val ring = if (focused) Modifier.border(FocusRingWidth, MaterialTheme.colorScheme.primary, shape) else Modifier
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget).then(ring),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interactions,
    ) {
```

(the rest of the function is unchanged). Imports: `androidx.compose.foundation.border`, `androidx.compose.foundation.interaction.MutableInteractionSource`, `androidx.compose.foundation.interaction.collectIsFocusedAsState`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.remember`, `com.kanyandula.discovernearby.ui.theme.FocusRingWidth`.

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --console=plain -q && echo pass`
Expected: `pass` (`FocusRingTest` 1/1).

- [ ] **Step 5: See it on the emulator**

```bash
S=<scratchpad>
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain | grep -E "Installed on"
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
for i in 1 2 3 4 5 6; do
  adb -s emulator-5554 shell cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1
  adb -s emulator-5554 exec-out screencap -p > "$S/m0011-focus-$i.png"
done
```

Expected: each screenshot shows the blue outline on the next tile in order (view `m0011-focus-1.png` and `m0011-focus-6.png` at least).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/theme/Dimens.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/components/CategoryTile.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/components/FocusRingTest.kt
git commit -m "Draw a focus ring on the Discover tiles"
```

---

### Task 3: Back returns focus to the originating tile

**Files:**
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNavigationTest.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/DiscoverScreen.kt`

**Interfaces:**
- Consumes: `CategoryTile` (Task 2).
- Produces: `DiscoverScreen` restores focus to the tile selected while focused.

- [ ] **Step 1: Write the failing tests**

`DiscoverNavigationTest.kt`, add (imports `androidx.compose.ui.test.assertIsFocused`, `androidx.compose.ui.test.assertIsNotFocused`, `androidx.compose.ui.test.requestFocus`):

```kotlin
    // docs/02 §16 (V7): Back returns rotary focus to the tile it left from.
    @Test
    fun rotaryFocusReturnsToTheOriginatingTile() {
        rule.onNodeWithText("Family").requestFocus()
        rule.onNodeWithText("Family").performClick()
        systemBack()
        rule.onNodeWithText("Family").assertIsFocused()
    }

    // A touch selection leaves no focus, so no ring appears on return.
    @Test
    fun touchSelectionLeavesNoFocusBehind() {
        rule.onNodeWithText("Family").performClick()
        systemBack()
        rule.onNodeWithText("Family").assertIsNotFocused()
    }
```

- [ ] **Step 2: Run them to verify the first fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiscoverNavigationTest' --console=plain -q 2>&1 | grep -E "tests completed|FAILED" | head -3`
Expected: `rotaryFocusReturnsToTheOriginatingTile` fails (Family not focused after Back); `touchSelectionLeavesNoFocusBehind` passes — it guards the restoration from leaking to touch.

- [ ] **Step 3: Implement**

`DiscoverScreen.kt`:

```kotlin
@Composable
fun DiscoverScreen(onCategorySelected: (DiscoveryCategory) -> Unit, modifier: Modifier = Modifier) {
    // docs/02 §16 (V7): after Back, rotary focus returns to the tile it left from. Only a tile that had focus when
    // selected gets it back, so a touch selection leaves no focus ring behind.
    var focused by remember { mutableStateOf<DiscoveryCategory?>(null) }
    var returnFocusTo by rememberSaveable { mutableStateOf<DiscoveryCategory?>(null) }
    val requesters = remember { DiscoveryCategory.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { returnFocusTo?.let { requesters.getValue(it).requestFocus() } }
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GridGap)) {
        Rows.forEach { row ->
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                row.forEach { category ->
                    CategoryTile(
                        category = category,
                        onClick = {
                            returnFocusTo = category.takeIf { it == focused }
                            onCategorySelected(category)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusRequester(requesters.getValue(category))
                            .onFocusChanged { if (it.isFocused) focused = category },
                    )
                }
            }
        }
    }
}
```

Imports: `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.focus.FocusRequester`, `androidx.compose.ui.focus.focusRequester`, `androidx.compose.ui.focus.onFocusChanged`.

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --console=plain -q && echo pass`
Expected: `pass` (both new tests, the rest unchanged).

- [ ] **Step 5: Full check and commit**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q && echo pass` → `pass`.

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/screens/DiscoverScreen.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNavigationTest.kt
git commit -m "Return rotary focus to the originating tile after Back"
```

- [ ] **Step 6: Restoration on the emulator**

```bash
S=<scratchpad>
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain | grep -E "Installed on"
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
for i in 1 2 3 4; do adb -s emulator-5554 shell cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; done
echo "before select: $("$S/rfocus.sh")"
adb -s emulator-5554 shell cmd car_service inject-key 23 >/dev/null; "$S/pause.sh" 2; "$S/texts.sh" | sed -n 2p
adb -s emulator-5554 shell cmd car_service inject-key 4 >/dev/null; "$S/pause.sh" 2
adb -s emulator-5554 exec-out screencap -p > "$S/m0011-after-back.png"
adb -s emulator-5554 shell cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1
echo "one turn after Back: $("$S/rfocus.sh")"; adb -s emulator-5554 exec-out screencap -p > "$S/m0011-after-back-turn.png"
```

Expected: "before select: Family"; Recommendations opens with title "Family"; after Back, `m0011-after-back.png` shows the ring on **Family**; one clockwise turn moves to **Scenic** (Review Focus 3). If the turn lands elsewhere (the rotary service kept its pre-navigation node), record exactly where in the ledger as a finding — the AC (focus on the originating tile after Back; never lost) still holds as long as focus stays on a tile.

---

### Task 4: Nothing unreachable, nothing trapped (record)

No code. Record every observation in the ledger; anything that fails the AC on the Discover grid becomes a finding fixed test-first before Task 5.

- [ ] **Step 1: Nudges**

```bash
S=<scratchpad>
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
adb -s emulator-5554 shell cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1
for k in "283 right" "283 right" "281 down" "282 left" "280 up"; do
  set -- $k; adb -s emulator-5554 shell cmd car_service inject-key $1 >/dev/null; "$S/pause.sh" 1; echo "nudge $2: $("$S/rfocus.sh")"
done
```

Expected (record actual): Food, Outdoors, Explore, Scenic, Food — or whatever the service's geometry picks; note any nudge that leaves the grid or does nothing.

- [ ] **Step 2: The ends of the grid**

```bash
S=<scratchpad>
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
for i in 1 2 3 4 5 6 7 8; do adb -s emulator-5554 shell cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; done
echo "8 turns clockwise: $("$S/rfocus.sh")"
for i in 1 2 3 4 5 6 7 8; do adb -s emulator-5554 shell cmd car_service inject-rotary -c false >/dev/null; "$S/pause.sh" 1; done
echo "8 turns back: $("$S/rfocus.sh")"
```

Expected: focus stays on Explore past the end and on Coffee before the start (Review Focus 1). Record if it moves outside the app's window (system bars are other windows; rotary is per window).

- [ ] **Step 3: Recommendations and Place Details (record only, decision 4)**

```bash
S=<scratchpad>
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
adb -s emulator-5554 shell cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1
adb -s emulator-5554 shell cmd car_service inject-key 23 >/dev/null; "$S/pause.sh" 2
for i in 1 2 3; do
  adb -s emulator-5554 shell cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1
  adb -s emulator-5554 exec-out screencap -p > "$S/m0011-recs-$i.png"
done
adb -s emulator-5554 shell cmd car_service inject-key 23 >/dev/null; "$S/pause.sh" 2; "$S/texts.sh" | sed -n 2p
adb -s emulator-5554 shell cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1
adb -s emulator-5554 exec-out screencap -p > "$S/m0011-details.png"
adb -s emulator-5554 shell cmd car_service inject-key 4 >/dev/null; "$S/pause.sh" 2
adb -s emulator-5554 shell cmd car_service inject-key 4 >/dev/null; "$S/pause.sh" 2; "$S/texts.sh" | sed -n 2p
```

Record: which element each turn focuses on Recommendations (header Back, rows), whether focus is visible there (rows and buttons have no app ring yet), what select opens, whether Navigate is reachable on Place Details, and that two Backs return to Discover. These are follow-ups for the screens' own focus work, not V7 failures.

---

### Task 5: Record the outcome and close out

- [ ] **Step 1: The V7 register row**

`docs/05-discover-nearby-delivery-plan.md`, replace the V7 row's status cell `🔴 Open — prove in M0 on the Discover grid` with the outcome. If Tasks 1–4 behaved as expected:

```text
🟢 **Closed (DN-M0-011): Proven with workarounds.** On `AAOS_AOSP_33_userdebug`, rotation moves focus through the six Discover tiles in order, select opens the focused category and Back restores focus to the originating tile, with: (1) the root `ComposeView` marked important for accessibility (Android 13's RotaryController walks Compose nodes only below that node); (2) an app-drawn focus ring; (3) focus restored after Back by the app.
```

and the "Blocks" cell `M0 exit` with `Nothing`. If a step failed and could not be fixed, use **Not workable** and escalate to the Product Lead (docs/05 §7) instead.

- [ ] **Step 2:** `CLAUDE.md` "Current state": replace `Next: DN-M0-011.` with ``Rotary (DN-M0-011, V7 proven with workarounds): root `ComposeView` important for accessibility, focus ring on tiles, focus restored after Back. Next: DN-M0-008.``; commit both docs together:

```bash
git add docs/05-discover-nearby-delivery-plan.md CLAUDE.md
git commit -m "Record the V7 rotary outcome"
```

- [ ] **Step 3:** Push; draft PR; CI `build` passes.

- [ ] **Step 4:** `simplify`; apply; re-run the full check; commit; push; CI.

- [ ] **Step 5:** Ticket completion notes: the outcome and its workarounds; the investigation (baseline, cause with the AOSP reference, the spike); what Compose needed (ticket AC); emulator evidence per step (Tasks 1–4); follow-ups for Recommendations/Details focus; every check.

- [ ] **Step 6:** Final whole-branch review by a fresh reviewer (opus); fix Critical/Important test-first.

- [ ] **Step 7:** `pr-description` (ticket ID, acceptance criteria met, the V7 outcome); `gh pr ready`.

- [ ] **Step 8: After the user merges** — ticket `done`; `NOW.md` (next: DN-M0-008, then DN-M0-007); delete the branch locally and remotely.
