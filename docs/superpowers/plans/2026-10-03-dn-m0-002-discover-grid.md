# DN-M0-002 Launch and Discover Grid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app opens on a Discover screen showing the six categories as a 2 × 3 grid from the design canvas; choosing one opens a Recommendations placeholder for that category; Back (in-app or system) returns to Discover; Back on Discover leaves the app.

**Architecture:** `DiscoverNearbyApp` draws the app header (brand pin + name) and the rounded panel, and hosts `DiscoverNavHost` (Navigation Compose, type-safe routes). `DiscoverScreen` lays out one `CategoryTile` per `DiscoveryCategory` entry. The enum lives in `discovery/` (pure Kotlin); everything visual about a category (label, subtitle, icon, tint) is mapped in `ui/`. Recommendations is a placeholder screen with an in-app back button until DN-M0-004.

**Tech Stack:** Jetpack Compose (BOM 2026.09.00, Material 3 1.4.0), Navigation Compose 2.10.2 type-safe routes (`@Serializable`, kotlinx.serialization plugin already applied), vector drawables converted from the canvas icon set, Robolectric 4.17 + Compose `v2` test rules.

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-002-aaos-app-shell.md`; `docs/02-discover-nearby-ux-interaction-spec.md` §3.6, §4, §5, §15, §22; `docs/03-discover-nearby-engineering-implementation-plan.md` §3, §6 (Screens, Back stack); `docs/04-discover-nearby-test-demo-plan.md` §2, §5 G; design canvas artboards Discover (`Main.dc.html`) and Icon set (`Icons.dc.html`); `docs/design/01-discover.png`, `02-recommendations.png`.

## Global Constraints

- Six categories in this order: Coffee, Food, Outdoors, Family, Scenic, Explore (docs/02 §5).
- 2 × 3 grid; icons are `ic_cat_*` vector drawables from the icon set, never emoji (docs/02 §5, ticket AC).
- Every primary touch target is at least 76 dp; no gesture other than tap is required (docs/02 §15, ticket AC).
- All user-facing text in `strings.xml`.
- Back from Recommendations returns to Discover; Back on Discover leaves the app (docs/03 §6 Back stack).
- Loading/content/error stay states of one destination (later tickets); the core path is at most three destinations deep.
- `ArchitectureRulesTest` must stay green: `discovery/` imports neither Compose nor `android.car`.
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes; never commit on local `main`; no AI attribution.

## Open decisions (recorded here, in the ticket and the PR)

1. **What the app draws vs the vehicle.** The mockups' clock, left rail and bottom bar are vehicle chrome. The app draws the header (brand pin + "Discover Nearby") and the rounded panel, as in the Discover artboard.
2. **In-app Back button.** The AOSP car system bar on the reference emulator has no Back button (seen in the DN-M0-009 screenshots), so Recommendations shows the canvas's ← button (76 dp target). System/rotary Back also works.
3. **Tile subtitles** ("Great coffee near you", …) come from the canvas; docs/02 §5 lists only labels. The canvas is the visual spec (docs/02 §22), so they are included.
4. **Focus ring.** The canvas shows a 4 dp blue outline on the focused tile. Rotary and focus are DN-M0-011; this ticket uses Material's default focus indication.
5. **`DiscoveryCategory` is created here**, in `discovery/`, because the route needs it. DN-M0-003 (domain) extends rather than creates it; its ticket is updated in Task 4.
6. **Launcher icon** stays the template icon; the canvas `ic_launcher` is a separate follow-up.

## Review Focus

1. **Double tap on a tile pushes two Recommendations screens**, so one Back lands on another Recommendations instead of Discover. Pinned by `DiscoverNavigationTest.doubleTapOnATileOpensOneScreen` (Task 2).
2. **Double tap on ← pops Discover too**, leaving a blank NavHost. Pinned by `DiscoverNavigationTest.doubleTapOnBackStaysOnDiscover` (Task 2).
3. **Grid order or shape wrong at the emulator's size** (1024 × 768 landscape). Pinned by `DiscoverScreenTest.showsSixCategoriesInTwoRowsOfThree` at `w1024dp-h768dp-land` (Task 1).
4. **A target under 76 dp** (tiles, or the ← button whose Material default is 48 dp). Pinned by `tilesMeetTheMinimumTouchTarget` and `backButtonMeetsTheMinimumTouchTarget`.
5. **Drawn under system bars** on newer AAOS images (targetSdk 36 is edge-to-edge). Pinned by `safeDrawingPadding()` at the root and checked on the emulator (Task 3).

---

## Investigation findings (2026-10-03)

- Canvas Discover artboard (1408 × 792): background `#0B0E11`; header 56 px with a `#5BD68A` pin (`ic_location`) and "Discover Nearby" 20 px semibold; panel `#15191D`, radius 24, padding 24; grid 3 × 2, gap 20; tile `#1F252B`, radius 20, icon 72 px, label 32 px semibold, subtitle 20 px `#AEB6BD`, gap 12.
- Category tints: Coffee `#E0A15A`, Food `#F28B6B`, Outdoors `#6CC48A`, Family `#B79CF2`, Scenic `#6FA8F5`, Explore `#C3CCD4`.
- Icon set: 24 × 24 grid, 1.8 stroke, round caps/joins; names `ic_cat_coffee` … `ic_cat_explore`, `ic_location`, `ic_back`. SVG `<circle>` has no VectorDrawable equivalent; each becomes a two-arc path.
- `02-recommendations.png`: Recommendations has an in-app ← and the category name as title inside the panel.
- `ui-test-junit4` 1.12.1 has `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule`, so system Back and `isFinishing` can be tested without new dependencies.
- Robolectric's default screen is phone-sized; UI tests pin `@Config(qualifiers = "w1024dp-h768dp-land-mdpi")` to match `automotive_1024p_landscape`.
- detekt `MagicNumber` ignores property declarations (DN-M0-001) but not literals inside calls, so all sizes live as named values in `ui/theme/Dimens.kt`.

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `app/src/main/java/com/kanyandula/discovernearby/discovery/DiscoveryCategory.kt` | Create | The six intents, in grid order |
| `app/src/main/java/com/kanyandula/discovernearby/ui/theme/Color.kt` | Modify | Category tints |
| `app/src/main/java/com/kanyandula/discovernearby/ui/theme/Dimens.kt` | Create | Canvas sizes as named values |
| `app/src/main/java/com/kanyandula/discovernearby/ui/CategoryVisual.kt` | Create | Label, subtitle, icon, tint per category |
| `app/src/main/java/com/kanyandula/discovernearby/ui/components/CategoryTile.kt` | Create | One clickable tile |
| `app/src/main/java/com/kanyandula/discovernearby/ui/screens/DiscoverScreen.kt` | Create | The 2 × 3 grid |
| `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt` | Create | Placeholder: ← and category title |
| `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt` | Create | Routes and NavHost |
| `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNearbyApp.kt` | Modify | Header + panel + NavHost |
| `app/src/main/res/drawable/ic_cat_{coffee,food,outdoors,family,scenic,explore}.xml`, `ic_location.xml`, `ic_back.xml` | Create | Vector icons |
| `app/src/main/res/values/strings.xml` | Modify | Labels, subtitles, Back |
| `app/src/test/java/com/kanyandula/discovernearby/ui/screens/DiscoverScreenTest.kt` | Create | Grid order, shape, targets, selection |
| `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNavigationTest.kt` | Create | Navigation and Back |

---

### Task 0: Start the ticket

- [ ] **Step 1:** In `~/.claude/projects/Discover Nearby/tickets/DN-M0-002-aaos-app-shell.md` set `status: in_progress` and `branch: dn-m0-002-discover-grid`.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-002-discover-grid
git status --short   # expect the plan file (and Studio's untracked .idea files, which stay out of commits)
git add docs/superpowers/plans/2026-10-03-dn-m0-002-discover-grid.md
git commit -m "Add DN-M0-002 implementation plan"
```

---

### Task 1: The Discover grid

**Files:** Create `discovery/DiscoveryCategory.kt`, `ui/theme/Dimens.kt`, `ui/CategoryVisual.kt`, `ui/components/CategoryTile.kt`, `ui/screens/DiscoverScreen.kt`, the six `ic_cat_*` drawables, `ui/screens/DiscoverScreenTest.kt`; modify `ui/theme/Color.kt`, `strings.xml`.

**Interfaces:**
- Produces: `enum class DiscoveryCategory { COFFEE, FOOD, OUTDOORS, FAMILY, SCENIC, EXPLORE }` (package `…discovery`); `internal val DiscoveryCategory.visual: CategoryVisual` with `label`, `subtitle` (`@StringRes`), `icon` (`@DrawableRes`), `tint: Color`; `@Composable fun DiscoverScreen(onCategorySelected: (DiscoveryCategory) -> Unit, modifier: Modifier = Modifier)`; Dimens `MinTouchTarget`, `HeaderHeight`, `HeaderIconSize`, `ContentGap`, `PanelRadius`, `PanelPadding`, `TileRadius`, `GridGap`, `CategoryIconSize`, `CategoryLabelSize`, `CategorySubtitleSize`, `HeaderTitleSize`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/ui/screens/DiscoverScreenTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1024dp-h768dp-land-mdpi") // automotive_1024p_landscape
class DiscoverScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val selected = mutableListOf<DiscoveryCategory>()

    private fun show() = rule.setContent {
        DiscoverNearbyTheme { DiscoverScreen(onCategorySelected = { selected += it }) }
    }

    private fun tiles() = rule.onAllNodes(hasClickAction())

    @Test
    fun showsSixCategoriesInTwoRowsOfThree() {
        show()
        val nodes = tiles().fetchSemanticsNodes()
        val labels = nodes.map { it.config.getOrNull(SemanticsProperties.Text)?.first()?.text }
        assertEquals(listOf("Coffee", "Food", "Outdoors", "Family", "Scenic", "Explore"), labels)

        val tops = nodes.map { it.boundsInRoot.top }
        val lefts = nodes.map { it.boundsInRoot.left }
        assertEquals("row 1 shares a top", 1, tops.take(3).distinct().size)
        assertEquals("row 2 shares a top", 1, tops.drop(3).distinct().size)
        assertEquals("row 2 is below row 1", true, tops[3] > tops[0])
        assertEquals("columns line up", lefts.take(3), lefts.drop(3))
    }

    @Test
    fun showsEachSubtitle() {
        show()
        listOf(
            "Great coffee near you", "Places to eat", "Parks, trails and more",
            "Family-friendly places", "Beautiful views near you", "Hidden gems and local spots",
        ).forEach { rule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
    }

    @Test
    fun tilesMeetTheMinimumTouchTarget() {
        show()
        repeat(DiscoveryCategory.entries.size) { i ->
            tiles()[i].assertHeightIsAtLeast(MinTouchTarget).assertWidthIsAtLeast(MinTouchTarget)
        }
    }

    @Test
    fun eachTileSelectsItsCategory() {
        show()
        listOf("Coffee", "Food", "Outdoors", "Family", "Scenic", "Explore")
            .forEach { rule.onNodeWithText(it).performClick() }
        assertEquals(DiscoveryCategory.entries, selected)
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "^e: |BUILD" | head -3`
Expected: compile failure (`Unresolved reference 'DiscoveryCategory'`, `'DiscoverScreen'`, `'MinTouchTarget'`).

- [ ] **Step 3: The category enum**

`app/src/main/java/com/kanyandula/discovernearby/discovery/DiscoveryCategory.kt`:

```kotlin
package com.kanyandula.discovernearby.discovery

/** The six discovery intents, in Discover grid order (docs/02 §5). Pure Kotlin: no Android or Compose. */
enum class DiscoveryCategory { COFFEE, FOOD, OUTDOORS, FAMILY, SCENIC, EXPLORE }
```

- [ ] **Step 4: Tints and sizes**

Append to `ui/theme/Color.kt`:

```kotlin

// Category icon tints (canvas icon set).
val CoffeeTint = Color(0xFFE0A15A)
val FoodTint = Color(0xFFF28B6B)
val OutdoorsTint = Color(0xFF6CC48A)
val FamilyTint = Color(0xFFB79CF2)
val ScenicTint = Color(0xFF6FA8F5)
val ExploreTint = Color(0xFFC3CCD4)
```

`app/src/main/java/com/kanyandula/discovernearby/ui/theme/Dimens.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Canvas Discover artboard sizes (1408 × 792 frame), taken 1:1 as dp/sp.
val MinTouchTarget = 76.dp // docs/02 §15
val HeaderHeight = 56.dp
val HeaderIconSize = 22.dp
val HeaderTitleSize = 20.sp
val ContentGap = 12.dp
val PanelRadius = 24.dp
val PanelPadding = 24.dp
val TileRadius = 20.dp
val GridGap = 20.dp
val CategoryIconSize = 72.dp
val CategoryLabelSize = 32.sp
val CategorySubtitleSize = 20.sp
```

- [ ] **Step 5: Strings**

Add inside `<resources>` in `app/src/main/res/values/strings.xml`:

```xml
    <string name="category_coffee">Coffee</string>
    <string name="category_coffee_subtitle">Great coffee near you</string>
    <string name="category_food">Food</string>
    <string name="category_food_subtitle">Places to eat</string>
    <string name="category_outdoors">Outdoors</string>
    <string name="category_outdoors_subtitle">Parks, trails and more</string>
    <string name="category_family">Family</string>
    <string name="category_family_subtitle">Family-friendly places</string>
    <string name="category_scenic">Scenic</string>
    <string name="category_scenic_subtitle">Beautiful views near you</string>
    <string name="category_explore">Explore</string>
    <string name="category_explore_subtitle">Hidden gems and local spots</string>
    <string name="back">Back</string>
```

- [ ] **Step 6: Category icons** (canvas icon set; white strokes, tinted at use; circles as two arcs)

`app/src/main/res/drawable/ic_cat_coffee.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M4 8h12v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V8z M16 9h2a2 2 0 0 1 0 4h-2 M8 2v3 M12 2v3"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

`ic_cat_food.xml` — same wrapper, `pathData="M7 2v20 M4 2v6a3 3 0 0 0 6 0V2 M17 22V2c-2 1.5-3 4-3 7v4h3"`.

`ic_cat_outdoors.xml` — `pathData="M12 2 6 10h3l-4 6h14l-4-6h3z M12 16v6"`.

`ic_cat_family.xml` — `pathData="M5 7a3 3 0 1 0 6 0a3 3 0 1 0-6 0 M15 9a2 2 0 1 0 4 0a2 2 0 1 0-4 0 M2 21v-2a5 5 0 0 1 10 0v2 M14 21v-1.5a3.5 3.5 0 0 1 7 0V21"`.

`ic_cat_scenic.xml` — `pathData="M2 20 9 8l4 6 3-4 6 10z M15 5a2 2 0 1 0 4 0a2 2 0 1 0-4 0"`.

`ic_cat_explore.xml` — `pathData="M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0 M16 8l-2 6-6 2 2-6z"`.

Each uses exactly the `<vector>` wrapper and stroke attributes shown for `ic_cat_coffee.xml`.

- [ ] **Step 7: Category visuals**

`app/src/main/java/com/kanyandula/discovernearby/ui/CategoryVisual.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.CoffeeTint
import com.kanyandula.discovernearby.ui.theme.ExploreTint
import com.kanyandula.discovernearby.ui.theme.FamilyTint
import com.kanyandula.discovernearby.ui.theme.FoodTint
import com.kanyandula.discovernearby.ui.theme.OutdoorsTint
import com.kanyandula.discovernearby.ui.theme.ScenicTint

/** How a category looks. Kept in ui/ so discovery/ stays free of Android resources. */
internal class CategoryVisual(
    @StringRes val label: Int,
    @StringRes val subtitle: Int,
    @DrawableRes val icon: Int,
    val tint: Color,
)

internal val DiscoveryCategory.visual: CategoryVisual
    get() = when (this) {
        DiscoveryCategory.COFFEE ->
            CategoryVisual(R.string.category_coffee, R.string.category_coffee_subtitle, R.drawable.ic_cat_coffee, CoffeeTint)
        DiscoveryCategory.FOOD ->
            CategoryVisual(R.string.category_food, R.string.category_food_subtitle, R.drawable.ic_cat_food, FoodTint)
        DiscoveryCategory.OUTDOORS ->
            CategoryVisual(R.string.category_outdoors, R.string.category_outdoors_subtitle, R.drawable.ic_cat_outdoors, OutdoorsTint)
        DiscoveryCategory.FAMILY ->
            CategoryVisual(R.string.category_family, R.string.category_family_subtitle, R.drawable.ic_cat_family, FamilyTint)
        DiscoveryCategory.SCENIC ->
            CategoryVisual(R.string.category_scenic, R.string.category_scenic_subtitle, R.drawable.ic_cat_scenic, ScenicTint)
        DiscoveryCategory.EXPLORE ->
            CategoryVisual(R.string.category_explore, R.string.category_explore_subtitle, R.drawable.ic_cat_explore, ExploreTint)
    }
```

- [ ] **Step 8: The tile**

`app/src/main/java/com/kanyandula/discovernearby/ui/components/CategoryTile.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.CategoryIconSize
import com.kanyandula.discovernearby.ui.theme.CategoryLabelSize
import com.kanyandula.discovernearby.ui.theme.CategorySubtitleSize
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.TileRadius
import com.kanyandula.discovernearby.ui.visual

@Composable
fun CategoryTile(category: DiscoveryCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val visual = category.visual
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget),
        shape = RoundedCornerShape(TileRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ContentGap, Alignment.CenterVertically),
        ) {
            // Decorative: the label names the category.
            Icon(
                painter = painterResource(visual.icon),
                contentDescription = null,
                tint = visual.tint,
                modifier = Modifier.size(CategoryIconSize),
            )
            Text(text = stringResource(visual.label), fontSize = CategoryLabelSize, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(visual.subtitle),
                fontSize = CategorySubtitleSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

If the compiler reports `Surface(onClick)` as experimental, add `@OptIn(ExperimentalMaterial3Api::class)` to the function and record it.

- [ ] **Step 9: The screen**

`app/src/main/java/com/kanyandula/discovernearby/ui/screens/DiscoverScreen.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.components.CategoryTile
import com.kanyandula.discovernearby.ui.theme.GridGap

private const val GRID_COLUMNS = 3

@Composable
fun DiscoverScreen(onCategorySelected: (DiscoveryCategory) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GridGap)) {
        DiscoveryCategory.entries.chunked(GRID_COLUMNS).forEach { row ->
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                row.forEach { category ->
                    CategoryTile(
                        category = category,
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 10: Run tests, detekt, lint**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|Error:|^e: |FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`; `DiscoverScreenTest` 4/4; all other suites still green.

- [ ] **Step 11: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Add the Discover category grid

DiscoveryCategory (discovery/, pure Kotlin) in grid order; CategoryTile
and DiscoverScreen draw the canvas 2 x 3 grid with ic_cat_* vector
icons, tints, labels and subtitles from strings.xml."
```

---

### Task 2: Navigation, header and Back

**Files:** Create `ui/DiscoverNavHost.kt`, `ui/screens/RecommendationsScreen.kt`, `ic_location.xml`, `ic_back.xml`, `ui/DiscoverNavigationTest.kt`; modify `ui/DiscoverNearbyApp.kt`.

**Interfaces:**
- Consumes: `DiscoverScreen`, `DiscoveryCategory`, `visual`, Dimens (Task 1)
- Produces: `@Serializable data object DiscoverRoute`; `@Serializable data class RecommendationsRoute(val category: DiscoveryCategory)`; `@Composable fun DiscoverNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController())`; `@Composable fun RecommendationsScreen(category: DiscoveryCategory, onBack: () -> Unit, modifier: Modifier = Modifier)`. DN-M0-004 replaces the placeholder body.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNavigationTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1024dp-h768dp-land-mdpi")
class DiscoverNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        rule.setContent { DiscoverNearbyTheme { DiscoverNearbyApp() } }
    }

    private fun systemBack() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    private fun onDiscover() = rule.onNodeWithText("Coffee").assertIsDisplayed()

    @Test
    fun showsHeaderAndStartsOnDiscover() {
        rule.onNodeWithText("Discover Nearby").assertIsDisplayed()
        onDiscover()
    }

    @Test
    fun tileOpensRecommendationsForThatCategory() {
        rule.onNodeWithText("Food").performClick()
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
        rule.onNodeWithText("Food").assertIsDisplayed()
        rule.onNodeWithText("Coffee").assertDoesNotExist()
    }

    @Test
    fun backButtonReturnsToDiscover() {
        rule.onNodeWithText("Food").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        onDiscover()
    }

    @Test
    fun systemBackReturnsToDiscover() {
        rule.onNodeWithText("Food").performClick()
        systemBack()
        onDiscover()
    }

    @Test
    fun systemBackOnDiscoverLeavesTheApp() {
        systemBack()
        assertTrue(rule.activity.isFinishing)
    }

    @Test
    fun backButtonMeetsTheMinimumTouchTarget() {
        rule.onNodeWithText("Food").performClick()
        rule.onNodeWithContentDescription("Back")
            .assertHeightIsAtLeast(MinTouchTarget).assertWidthIsAtLeast(MinTouchTarget)
    }

    @Test
    fun doubleTapOnATileOpensOneScreen() {
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Food").performClick()
        rule.onNodeWithText("Food").performClick()
        rule.mainClock.autoAdvance = true
        systemBack()
        onDiscover()
    }

    @Test
    fun doubleTapOnBackStaysOnDiscover() {
        rule.onNodeWithText("Food").performClick()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.mainClock.autoAdvance = true
        onDiscover()
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "DiscoverNavigationTest > .* (FAILED|PASSED)|BUILD"`
Expected: every navigation test except `showsHeaderAndStartsOnDiscover` and `systemBackOnDiscoverLeavesTheApp` FAILS (no tiles, no NavHost yet); those two may pass or fail depending on the placeholder. A test that passes before the feature exists must be explained in the ledger.

- [ ] **Step 3: Icons**

`app/src/main/res/drawable/ic_location.xml` — the `ic_cat_coffee.xml` wrapper with `pathData="M12 22s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12z M9.5 10a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0"`.

`app/src/main/res/drawable/ic_back.xml` — the same wrapper with `pathData="M19 12H5 M12 19l-7-7 7-7"` and `android:autoMirrored="true"` on `<vector>`.

- [ ] **Step 4: Recommendations placeholder**

`app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.CategoryLabelSize
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.visual

// ponytail: placeholder header only; DN-M0-004 adds the list and its states.
@Composable
fun RecommendationsScreen(category: DiscoveryCategory, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ContentGap)) {
            // The AOSP car system bar has no Back button, so the screen provides one (docs/02 §3.6).
            IconButton(onClick = onBack, modifier = Modifier.size(MinTouchTarget)) {
                Icon(painter = painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
            Text(
                text = stringResource(category.visual.label),
                fontSize = CategoryLabelSize,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
```

- [ ] **Step 5: Routes and NavHost**

`app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.screens.DiscoverScreen
import com.kanyandula.discovernearby.ui.screens.RecommendationsScreen
import kotlinx.serialization.Serializable

@Serializable
data object DiscoverRoute

@Serializable
data class RecommendationsRoute(val category: DiscoveryCategory)

/** A destination acts only while resumed, so a double tap during a transition does nothing. */
private fun NavBackStackEntry.isResumed() = lifecycle.currentState == Lifecycle.State.RESUMED

@Composable
fun DiscoverNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = DiscoverRoute, modifier = modifier) {
        composable<DiscoverRoute> { entry ->
            DiscoverScreen(
                onCategorySelected = { category ->
                    if (entry.isResumed()) navController.navigate(RecommendationsRoute(category))
                },
            )
        }
        composable<RecommendationsRoute> { entry ->
            RecommendationsScreen(
                category = entry.toRoute<RecommendationsRoute>().category,
                onBack = { if (entry.isResumed()) navController.popBackStack() },
            )
        }
    }
}
```

If navigation rejects the enum argument at runtime, change the route field to `val category: String` (`category.name`) and read it back with `DiscoveryCategory.valueOf`, and record it as a ruling.

- [ ] **Step 6: Header, panel, NavHost**

Replace `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNearbyApp.kt` with:

```kotlin
package com.kanyandula.discovernearby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.HeaderHeight
import com.kanyandula.discovernearby.ui.theme.HeaderIconSize
import com.kanyandula.discovernearby.ui.theme.HeaderTitleSize
import com.kanyandula.discovernearby.ui.theme.PanelPadding
import com.kanyandula.discovernearby.ui.theme.PanelRadius

@Composable
fun DiscoverNearbyApp(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(start = PanelPadding, end = PanelPadding, bottom = PanelPadding),
        ) {
            AppHeader()
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(PanelRadius),
                color = MaterialTheme.colorScheme.surface,
            ) {
                DiscoverNavHost(modifier = Modifier.padding(PanelPadding))
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.height(HeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_location),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(HeaderIconSize),
        )
        Text(text = stringResource(R.string.app_name), fontSize = HeaderTitleSize, fontWeight = FontWeight.SemiBold)
    }
}
```

- [ ] **Step 7: Run everything**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|Error:|^e: |FAILED|BUILD"` and list suites from `app/build/test-results/testDebugUnitTest/*.xml`.
Expected: `BUILD SUCCESSFUL`; `DiscoverNavigationTest` 8/8; `DiscoverScreenTest` 4/4; `DiscoverNearbyThemeTest` 3/3 (its title test now finds the header); `ArchitectureRulesTest` 3/3.

If a double-tap test cannot be made to fail against a version without the `isResumed()` guard (check by temporarily removing the guard), record that the test does not exercise the race in Robolectric and keep the guard.

- [ ] **Step 8: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Add Discover navigation, app header and Back

Type-safe NavHost from Discover to a Recommendations placeholder with
an in-app Back button (the AOSP car bar has none). Destinations act
only while resumed, so double taps cannot stack screens or pop
Discover. The app draws its header and panel; the root respects
safe-drawing insets."
```

---

### Task 3: Verify on the AAOS userdebug emulator

- [ ] **Step 1: Boot the reference emulator if it is not running**

```bash
adb devices
~/Library/Android/sdk/emulator/emulator -avd AAOS_AOSP_33_userdebug -port 5554 -no-snapshot-load &   # only if emulator-5554 is absent
adb -s emulator-5554 wait-for-device
adb -s emulator-5554 shell getprop ro.build.type    # expect userdebug
```

- [ ] **Step 2: Install and launch in Park; compare with the design**

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain | grep -E "Installed on|BUILD"
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
adb -s emulator-5554 shell am start -n com.kanyandula.discovernearby/.ui.MainActivity
adb -s emulator-5554 exec-out screencap -p > /private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad/m0002-discover-park.png
```

Expected: header with green pin and "Discover Nearby"; rounded panel; 2 × 3 tiles in order with tinted icons, labels and subtitles; nothing drawn under the system bars. Compare with `docs/design/01-discover.png` and record differences in the ticket.

- [ ] **Step 3: Tap Food, then the in-app Back**

Tap targets are read at run time: `adb -s emulator-5554 exec-out uiautomator dump /dev/tty` lists each node's `bounds`; `<foodX> <foodY>` is the centre of the node with text "Food", `<backX> <backY>` the centre of the node with content-desc "Back". Then:

```bash
adb -s emulator-5554 shell input tap <foodX> <foodY>
adb -s emulator-5554 exec-out screencap -p > /private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad/m0002-recs.png
adb -s emulator-5554 shell input tap <backX> <backY>
```

Expected: Recommendations shows ← and "Food"; after ←, Discover.

- [ ] **Step 4: System Back in Drive**

```bash
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8
adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60 &
adb -s emulator-5554 shell input tap <foodX> <foodY>
adb -s emulator-5554 shell input keyevent KEYCODE_BACK
adb -s emulator-5554 shell dumpsys activity activities | grep topResumedActivity | head -1
adb -s emulator-5554 exec-out screencap -p > /private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad/m0002-discover-drive.png
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
```

Expected: in moving Drive the app stays in front (no block screen), opens Food, and `KEYCODE_BACK` returns to Discover.

---

### Task 4: Close out

- [ ] **Step 1:** Update `~/.claude/projects/Discover Nearby/tickets/DN-M0-003-domain-and-fake-data.md`: in its acceptance criteria, note that `DiscoveryCategory` already exists in `discovery/` (DN-M0-002) and is extended, not re-created.

- [ ] **Step 2:** In `CLAUDE.md` "Current state", replace `Next: DN-M0-002 / 003 / 010.` with `Discover grid and navigation (DN-M0-002). Next: DN-M0-003 / 010 / 011.`; commit.

- [ ] **Step 3:** Push; open a draft PR; confirm CI `build` passes with `lintDebug` in the log.

- [ ] **Step 4:** `simplify` on the branch diff; apply, re-run the full check, commit, push, confirm CI.

- [ ] **Step 5:** Ticket completion notes: open decisions, emulator evidence (screenshots taken, Park/Drive results, differences from the design), every check run with its result.

- [ ] **Step 6:** Final whole-branch review by a fresh reviewer; fix Critical/Important with a failing test first.

- [ ] **Step 7:** `pr-description` (Ticket ID, acceptance-criteria table, open decisions, no AI attribution); `gh pr ready`.

- [ ] **Step 8: After the user merges** — ticket `done`; DN-M0-011 `ready` (its dependency is met); `NOW.md`; delete the branch.
