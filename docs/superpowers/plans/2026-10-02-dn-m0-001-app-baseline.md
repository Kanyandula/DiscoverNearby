# DN-M0-001 Revision 4 App Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:app` passes `lintDebug` (now also run by CI), carries the full Revision 4 dependency set, a working Robolectric + Compose UI test setup, the single wiring point (`DiscoverApplication` → `AppContainer`), the canvas Material 3 theme, and an automated check of the package dependency rule.

**Architecture:** `DiscoverApplication` creates one `AppContainer` in `onCreate` (manual constructor injection; empty until later tickets add dependencies). `MainActivity` renders `DiscoverNearbyApp()` inside `DiscoverNearbyTheme`, whose dark colour scheme comes from the design canvas. A JVM `ArchitectureRulesTest` enforces docs/03 §2's import rule over every main source file, so later tickets cannot break it silently.

**Tech Stack:** Navigation Compose 2.10.2, Lifecycle 2.11.0 (`viewmodel-compose`, `runtime-compose`), kotlinx.coroutines 1.11.0, OkHttp 5.5.0, kotlinx.serialization 1.11.0 + Kotlin serialization plugin 2.2.10, Robolectric 4.17, Compose `ui-test-junit4` / `ui-test-manifest` (BOM 2026.09.00), JUnit 4.13.2.

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-001-project-baseline.md`, plus `docs/03-discover-nearby-engineering-implementation-plan.md` §2, §3, §6.1, §20, `docs/05-discover-nearby-delivery-plan.md` §1–3, `docs/02-discover-nearby-ux-interaction-spec.md` §22, and the design canvas Discover artboard.

## Global Constraints

- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes (ticket AC plus CLAUDE.md's "lint must pass"). Task 4 adds `lintDebug` to the CI `build` check.
- Versions pinned to current stable and recorded in docs/05 §2 Version policy (ticket AC).
- Dependency rule (docs/03 §2): only `ui/` imports Compose; only `car/` imports `android.car`; `discovery/`, `model/`, `places/`, `location/` import neither (ticket AC).
- No Car App Library; no DI framework; no database (docs/03 §6.1).
- Robolectric configured as in NyasaPlayer: `isIncludeAndroidResources = true`, `isReturnDefaultValues = true`; the SDK choice is recorded (ticket AC).
- One Compose UI test under Robolectric with `createComposeRule` (ticket AC). Use `androidx.compose.ui.test.junit4.v2.createComposeRule`: the non-v2 one is deprecated in this BOM.
- All user-facing text in `strings.xml`.
- Missing baseline details are recorded as open decisions before code assumes them (ticket AC). See "Open decisions" below.
- Commits: never on local `main`; no AI attribution.

## Open decisions (recorded here, in the ticket and in the PR)

1. **No light palette exists.** The canvas is dark only. `DiscoverNearbyTheme` uses the canvas dark scheme for both day and night until Design supplies a day palette. Owner: Product/Design.
2. **Typeface.** The canvas uses Figtree; the docs do not specify a font. The baseline keeps the Material 3 default typeface. Adding Figtree (downloadable or bundled font) is a separate decision.
3. **Empty packages are not created.** Git cannot hold empty directories and placeholder files would be dead code. `car/`, `discovery/`, `places/`, `location/`, `navigation/`, `model/` are created by the tickets that add their first type (DN-M0-003, DN-M0-005, DN-M0-010). `ArchitectureRulesTest` enforces the rule for each package as soon as it exists.
4. **Robolectric runs at SDK 36 (targetSdk)**, not pinned lower. Android 16's runtime needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` on JDK 21, set on the test task.
5. **Lint version warnings are not acted on here.** `lintDebug` reports newer AGP (9.4.1), Gradle (9.8.0) and Kotlin Compose plugin (2.4.20), and `OldTargetApi` for targetSdk 36. These are toolchain upgrades with their own risk (Kotlin 2.4 changes the built-in compiler) and targetSdk 36 is deliberate (DN-M0-009). They stay as warnings; `core-ktx` 1.19.0 → 1.19.1 is a patch and is taken (Task 1).

## Review Focus

1. **Dependency rule broken by a later ticket.** Pinned by `ArchitectureRulesTest` (Task 3), proven with a violating probe file.
2. **Robolectric silently on the wrong SDK or JVM.** Pinned by `DiscoverApplicationTest` failing first, then passing with the JVM flag (Task 1).
3. **`./gradlew test` fails on the release variant** because `ui-test-manifest` is debug-only. Pinned by disabling release host tests (Task 1, Step 4) and running `./gradlew test` once.
4. **Theme drifts from the canvas.** Pinned by `DiscoverNearbyThemeTest.usesCanvasPalette` (Task 2).
5. **App class not registered in the manifest**, so `container` is never created. Pinned by `DiscoverApplicationTest` (Task 1).
6. **Lint regresses unseen.** `lintDebug` failed on `main` (`CoarseFineLocation`) because CI never ran it. Pinned by adding `lintDebug` to CI (Task 4) and the manifest test asserting both location permissions.

---

## Investigation findings (2026-10-02)

- Latest stable versions: navigation-compose 2.10.2; lifecycle 2.11.0; coroutines 1.11.0 (built with Kotlin 2.2.20); OkHttp 5.5.0 (Kotlin 2.1.21); kotlinx.serialization 1.11.0 (Kotlin 2.3.20); Robolectric 4.17.
- Spike in a throwaway worktree with this exact set on Kotlin 2.2.10: compiles with no metadata warnings; a `@Serializable` round-trip and a Compose `Text` assertion pass under Robolectric.
- Robolectric 4.17 + targetSdk 36 fails on JDK 21 with `IllegalAccessException … does not export jdk.internal.access` (from Android 16's `ApplicationSharedMemory`). `jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")` fixes it: 2/2 pass at SDK 36.
- `createComposeRule` (non-v2) is deprecated in BOM 2026.09.00; the replacement is `androidx.compose.ui.test.junit4.v2.createComposeRule`.
- Canvas Discover artboard palette: background `#0B0E11`, surface `#15191D`, tile `#1F252B`, text `#EEF1F3`, secondary text `#AEB6BD`, focus/accent blue `#4C8DF6`, brand green `#5BD68A`, font Figtree.
- detekt's default `MagicNumber` does not ignore top-level property declarations, so colour constants will be flagged (handled in Task 2).
- `./gradlew lintDebug` on `main` (`fc98d31`) fails: 1 error, `CoarseFineLocation` at `AndroidManifest.xml:8` (fine location declared without coarse; Android requires both and lets users grant approximate only). 5 warnings: `OldTargetApi` (targetSdk 36), newer AGP / Gradle / Compose plugin / `core-ktx` 1.19.1. CI runs detekt, tests and the build, not lint. Raised by an external review of DN-M0-012.

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `gradle/libs.versions.toml` | Modify | New versions, libraries, serialization plugin |
| `build.gradle.kts` (root) | Modify | Serialization plugin `apply false` |
| `app/build.gradle.kts` | Modify | Plugin, dependencies, `testOptions`, JVM flag, release host tests off |
| `app/src/main/AndroidManifest.xml` | Modify | `android:name=".DiscoverApplication"` |
| `app/src/main/java/com/kanyandula/discovernearby/DiscoverApplication.kt` | Create | Creates the `AppContainer` |
| `app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt` | Create | Single wiring point (empty for now) |
| `app/src/main/java/com/kanyandula/discovernearby/ui/theme/Color.kt` | Create | Canvas colours |
| `app/src/main/java/com/kanyandula/discovernearby/ui/theme/Theme.kt` | Create | `DiscoverNearbyTheme` |
| `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNearbyApp.kt` | Create | Root composable (placeholder until DN-M0-002) |
| `app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt` | Modify | Renders `DiscoverNearbyApp()` in the theme |
| `config/detekt/detekt.yml` | Modify | `MagicNumber` ignores property declarations |
| `app/src/test/java/com/kanyandula/discovernearby/DiscoverApplicationTest.kt` | Create | Robolectric: app class + container |
| `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNearbyThemeTest.kt` | Create | Compose UI tests |
| `app/src/test/java/com/kanyandula/discovernearby/ArchitectureRulesTest.kt` | Create | docs/03 §2 import rule |
| `app/src/test/java/com/kanyandula/discovernearby/ManifestContractTest.kt` | Modify | Assert coarse location too |
| `.github/workflows/ci.yml` | Modify | Add `lintDebug` to the `build` check |
| `docs/03-discover-nearby-engineering-implementation-plan.md` | Modify | §6 Manifest essentials lists coarse location |
| `docs/05-discover-nearby-delivery-plan.md` | Modify | Record pinned versions |
| `CLAUDE.md` | Modify | Current state; test command note |

---

### Task 0: Start the ticket

**Files:**
- Modify: ticket frontmatter
- Commit: `docs/superpowers/plans/2026-10-02-dn-m0-001-app-baseline.md`

**Interfaces:**
- Produces: branch `dn-m0-001-app-baseline`

- [ ] **Step 1:** In `~/.claude/projects/Discover Nearby/tickets/DN-M0-001-project-baseline.md` set `status: in_progress` and `branch: dn-m0-001-app-baseline`.

- [ ] **Step 2: Branch from the latest main**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-001-app-baseline
git status --short
```

Expected: only `?? docs/superpowers/plans/2026-10-02-dn-m0-001-app-baseline.md`.

- [ ] **Step 3: Commit the plan**

```bash
git add docs/superpowers/plans/2026-10-02-dn-m0-001-app-baseline.md
git commit -m "Add DN-M0-001 implementation plan"
```

---

### Task 1: Dependency set, Robolectric, and the wiring point

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `DiscoverApplication.kt`, `AppContainer.kt`, `DiscoverApplicationTest.kt`

**Interfaces:**
- Produces: `class AppContainer` (no members yet); `class DiscoverApplication : Application` with `lateinit var container: AppContainer` (`private set`), assigned in `onCreate`. Later tickets add constructor-built dependencies to `AppContainer` and read them via `(application as DiscoverApplication).container`.

- [ ] **Step 1: Version catalog**

In `gradle/libs.versions.toml`, change `coreKtx = "1.19.0"` to `coreKtx = "1.19.1"` (lint `GradleDependency`; patch release). Then add under `[versions]` after `detekt`:

```toml
navigationCompose = "2.10.2"
lifecycle = "2.11.0"
coroutines = "1.11.0"
okhttp = "5.5.0"
serialization = "1.11.0"
robolectric = "4.17"
```

Add under `[libraries]` before `junit`:

```toml
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
```

Add under `[plugins]` before `kotlin-compose`:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Root build file** — add `alias(libs.plugins.kotlin.serialization) apply false` to the `plugins` block.

- [ ] **Step 3: `app/build.gradle.kts`**

Add `alias(libs.plugins.kotlin.serialization)` to `plugins`. Inside `android { … }`, after `buildFeatures`, add:

```kotlin
    testOptions {
        unitTests {
            // Robolectric reads merged resources and the manifest; createComposeRule needs both.
            isIncludeAndroidResources = true
            // Unmocked android.* calls (e.g. Log) return defaults instead of throwing.
            isReturnDefaultValues = true
        }
    }
```

Extend the existing `tasks.withType<Test>().configureEach { … }` block so it reads:

```kotlin
// ManifestContractTest reads the source manifest; without this a manifest-only change leaves the test UP-TO-DATE.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/AndroidManifest.xml")
    // Robolectric at SDK 36 (Android 16) touches jdk.internal.access on JDK 21.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
```

Replace `testImplementation(libs.junit)` in `dependencies` with:

```kotlin
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
```

- [ ] **Step 4: Disable release host tests** (`ui-test-manifest` is debug-only, so release Robolectric tests cannot launch). Append to `app/build.gradle.kts`:

```kotlin
// ui-test-manifest is debugImplementation; release unit tests would have no test activity to launch.
androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable = false
    }
}
```

If AGP 9 rejects `hostTests`, find the replacement in the AGP error, apply it, and record it as a ruling.

- [ ] **Step 5: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/DiscoverApplicationTest.kt`:

```kotlin
package com.kanyandula.discovernearby

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DiscoverApplicationTest {

    @Test
    fun applicationCreatesTheAppContainer() {
        val app = RuntimeEnvironment.getApplication() as DiscoverApplication
        assertNotNull(app.container)
    }
}
```

- [ ] **Step 6: Run it and watch it fail**

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "^e: |DiscoverApplicationTest|BUILD"`
Expected: compile failure, `Unresolved reference 'DiscoverApplication'`.

- [ ] **Step 7: Implement**

`app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt`:

```kotlin
package com.kanyandula.discovernearby

/**
 * The single wiring point (docs/03 §3): every app-scoped dependency is constructed here
 * by hand. Empty until DN-M0-003 adds the first repositories.
 */
class AppContainer
```

`app/src/main/java/com/kanyandula/discovernearby/DiscoverApplication.kt`:

```kotlin
package com.kanyandula.discovernearby

import android.app.Application

class DiscoverApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
```

In `app/src/main/AndroidManifest.xml`, add `android:name=".DiscoverApplication"` as the first attribute of `<application>`.

- [ ] **Step 8: Run all checks**

Run: `./gradlew detekt testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|^e: |FAILED|BUILD"` and `grep -h -o -E 'testsuite name="[^"]*"|tests="[0-9]+"|failures="[0-9]+"' app/build/test-results/testDebugUnitTest/*.xml`
Expected: `BUILD SUCCESSFUL`; `DiscoverApplicationTest` 1/1 and `ManifestContractTest` 4/4, no failures.

- [ ] **Step 9: `./gradlew test` works too**

Run: `./gradlew test --console=plain 2>&1 | grep -E "testReleaseUnitTest|BUILD"`
Expected: `BUILD SUCCESSFUL`, with `testReleaseUnitTest` absent or `SKIPPED`.

- [ ] **Step 10: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml \
  app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt \
  app/src/main/java/com/kanyandula/discovernearby/DiscoverApplication.kt \
  app/src/test/java/com/kanyandula/discovernearby/DiscoverApplicationTest.kt
git commit -m "Add Revision 4 dependency set, Robolectric and AppContainer

DiscoverApplication creates the single AppContainer. Robolectric 4.17
runs at targetSdk 36 with the jdk.internal.access export Android 16
needs on JDK 21. Release host tests are off because ui-test-manifest
is debug-only."
```

---

### Task 2: Canvas theme and the root composable

**Files:**
- Create: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/DiscoverNearbyApp.kt`, `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNearbyThemeTest.kt`
- Modify: `ui/MainActivity.kt`, `config/detekt/detekt.yml`

**Interfaces:**
- Consumes: Compose test dependencies (Task 1)
- Produces: `@Composable fun DiscoverNearbyTheme(content: @Composable () -> Unit)`; `@Composable fun DiscoverNearbyApp()`; colour vals `Background`, `Surface`, `SurfaceVariant`, `OnSurface`, `OnSurfaceVariant`, `Accent`, `Brand` in package `…ui.theme`. DN-M0-002 replaces `DiscoverNearbyApp`'s body with `DiscoverNavHost`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNearbyThemeTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kanyandula.discovernearby.ui.theme.Accent
import com.kanyandula.discovernearby.ui.theme.Background
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.OnSurface
import com.kanyandula.discovernearby.ui.theme.Surface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiscoverNearbyThemeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun appShowsItsTitle() {
        rule.setContent { DiscoverNearbyTheme { DiscoverNearbyApp() } }
        rule.onNodeWithText("Discover Nearby").assertIsDisplayed()
    }

    @Test
    fun usesCanvasPalette() {
        lateinit var scheme: ColorScheme
        rule.setContent { DiscoverNearbyTheme { scheme = MaterialTheme.colorScheme } }
        rule.waitForIdle()
        assertEquals(Background, scheme.background)
        assertEquals(Surface, scheme.surface)
        assertEquals(OnSurface, scheme.onSurface)
        assertEquals(Accent, scheme.primary)
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "^e: |BUILD"`
Expected: compile failure, `Unresolved reference 'theme'` / `'DiscoverNearbyApp'`.

- [ ] **Step 3: Colours**

`app/src/main/java/com/kanyandula/discovernearby/ui/theme/Color.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.theme

import androidx.compose.ui.graphics.Color

// From the design canvas Discover artboard (docs/02 §22). Dark only: no day palette exists yet.
val Background = Color(0xFF0B0E11)
val Surface = Color(0xFF15191D)
val SurfaceVariant = Color(0xFF1F252B)
val OnSurface = Color(0xFFEEF1F3)
val OnSurfaceVariant = Color(0xFFAEB6BD)
val Accent = Color(0xFF4C8DF6)
val Brand = Color(0xFF5BD68A)
```

- [ ] **Step 4: Theme**

`app/src/main/java/com/kanyandula/discovernearby/ui/theme/Theme.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CanvasColors = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    secondary = Brand,
    onSecondary = Background,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
)

// ponytail: one scheme for day and night until Design supplies a day palette (open decision 1).
@Composable
fun DiscoverNearbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CanvasColors, content = content)
}
```

- [ ] **Step 5: Root composable and MainActivity**

`app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNearbyApp.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.R

// ponytail: placeholder; DN-M0-002 replaces the body with DiscoverNavHost.
@Composable
fun DiscoverNearbyApp() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        }
    }
}
```

Replace the body of `MainActivity.kt` with:

```kotlin
package com.kanyandula.discovernearby.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiscoverNearbyTheme {
                DiscoverNearbyApp()
            }
        }
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD"` and check `DiscoverNearbyThemeTest` in `app/build/test-results/testDebugUnitTest/`.
Expected: `BUILD SUCCESSFUL`; `DiscoverNearbyThemeTest` 2/2.

- [ ] **Step 7: detekt**

Run: `./gradlew detekt --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|BUILD"`
Expected: `[MagicNumber]` on each colour in `Color.kt` and `BUILD FAILED`. Add to `config/detekt/detekt.yml` (NyasaPlayer's setting):

```yaml
style:
  MagicNumber:
    # Named property declarations (colours, dimensions) are the fix MagicNumber asks for.
    ignorePropertyDeclaration: true
```

Rerun: no rule lines, `BUILD SUCCESSFUL`. If detekt reports nothing in the first run, skip the config change.

- [ ] **Step 8: Full check and commit**

Run: `./gradlew detekt testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src config/detekt/detekt.yml
git commit -m "Add canvas Material 3 theme and DiscoverNearbyApp

Dark scheme from the design canvas, used for day and night until a day
palette exists. MainActivity renders DiscoverNearbyApp in the theme;
Compose UI tests run under Robolectric with the v2 createComposeRule."
```

---

### Task 3: Enforce the package dependency rule

**Files:**
- Create: `app/src/test/java/com/kanyandula/discovernearby/ArchitectureRulesTest.kt`
- Temporary probe: `app/src/main/java/com/kanyandula/discovernearby/model/ArchitectureProbe.kt` (deleted in Step 4)

**Interfaces:**
- Consumes: main sources under `app/src/main/java/com/kanyandula/discovernearby/`
- Produces: a failing test whenever docs/03 §2's import rule is broken

- [ ] **Step 1: Write the test**

```kotlin
package com.kanyandula.discovernearby

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * docs/03 §2: only ui/ imports Compose; only car/ imports android.car. Local unit tests
 * run with the module directory as the working directory.
 */
class ArchitectureRulesTest {

    private val root = File("src/main/java/com/kanyandula/discovernearby")

    private fun violations(forbiddenImport: String, allowedPackage: String): List<String> =
        root.walkTopDown()
            .filter { it.extension == "kt" }
            .filterNot { it.relativeTo(root).path.startsWith("$allowedPackage/") }
            .flatMap { file ->
                file.readLines()
                    .filter { it.startsWith("import $forbiddenImport") }
                    .map { "${file.relativeTo(root)}: $it" }
            }
            .toList()

    @Test
    fun onlyUiImportsCompose() {
        assertEquals(emptyList<String>(), violations("androidx.compose.", allowedPackage = "ui"))
    }

    @Test
    fun onlyCarImportsAndroidCar() {
        assertEquals(emptyList<String>(), violations("android.car.", allowedPackage = "car"))
    }
}
```

- [ ] **Step 2: Prove it catches a violation**

Create `app/src/main/java/com/kanyandula/discovernearby/model/ArchitectureProbe.kt`:

```kotlin
package com.kanyandula.discovernearby.model

import androidx.compose.runtime.Composable
import android.car.Car

internal val probe = listOf(Composable::class, Car::class)
```

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "ArchitectureRulesTest > .* FAILED|BUILD"`
Expected: `onlyUiImportsCompose FAILED`, `onlyCarImportsAndroidCar FAILED`, each naming `model/ArchitectureProbe.kt`.

- [ ] **Step 3: Remove the probe**

```bash
rm app/src/main/java/com/kanyandula/discovernearby/model/ArchitectureProbe.kt
rmdir app/src/main/java/com/kanyandula/discovernearby/model
```

- [ ] **Step 4: Run and commit**

Run: `./gradlew detekt testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`; `ArchitectureRulesTest` 2/2.

```bash
git add app/src/test/java/com/kanyandula/discovernearby/ArchitectureRulesTest.kt
git commit -m "Enforce the docs/03 package import rule with a JVM test"
```

---

### Task 4: Lint green, and lint in CI

**Files:**
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ManifestContractTest.kt`, `app/src/main/AndroidManifest.xml`, `.github/workflows/ci.yml`, `CLAUDE.md`, `docs/03-discover-nearby-engineering-implementation-plan.md` (§6 Manifest essentials)

**Interfaces:**
- Produces: manifest declares `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`; CI `build` runs `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`. DN-M0-006 still owns requesting both at runtime.

- [ ] **Step 1: Write the failing assertion**

In `ManifestContractTest.declaresLocationAndInternetPermissions`, replace

```kotlin
        val required = setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.INTERNET")
```

with

```kotlin
        // Fine location requires coarse alongside it (lint CoarseFineLocation; users may grant approximate only).
        val required = setOf(
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.INTERNET",
        )
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "ManifestContractTest > .* FAILED|BUILD"`
Expected: `declaresLocationAndInternetPermissions FAILED`.

- [ ] **Step 3: Add the permission**

In `app/src/main/AndroidManifest.xml`, add above the fine-location line:

```xml
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

- [ ] **Step 4: Run tests and lint**

Run: `./gradlew lintDebug testDebugUnitTest --console=plain 2>&1 | grep -E "Lint found|Error:|FAILED|BUILD"`
Expected: no `Error:` lines, `BUILD SUCCESSFUL`. Lint may still print warnings (open decision 5).

- [ ] **Step 5: Lint in CI and in the documented command**

In `.github/workflows/ci.yml`, change the last step to:

```yaml
      - name: Detekt, lint, unit tests, debug build
        run: ./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain
```

In `CLAUDE.md`, change both occurrences of `./gradlew detekt testDebugUnitTest assembleDebug` (Commands CI line, Ticket workflow step 3) to `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`.

In `docs/03-discover-nearby-engineering-implementation-plan.md` §6 Manifest essentials, change `` - `ACCESS_FINE_LOCATION`, `INTERNET` `` to `` - `ACCESS_FINE_LOCATION` with `ACCESS_COARSE_LOCATION` (Android requires both; users may grant approximate only), `INTERNET` ``.

- [ ] **Step 6: Full check and commit**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|Error:|FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/AndroidManifest.xml app/src/test/java/com/kanyandula/discovernearby/ManifestContractTest.kt \
  .github/workflows/ci.yml CLAUDE.md docs/03-discover-nearby-engineering-implementation-plan.md
git commit -m "Declare coarse location and run lint in CI

lintDebug failed on main (CoarseFineLocation) because CI never ran it.
The manifest now declares coarse with fine, the manifest test asserts
both, and the build check runs lintDebug."
```

---

### Task 5: Record versions, update CLAUDE.md, close out

**Files:**
- Modify: `docs/05-discover-nearby-delivery-plan.md` (§2 Version policy), `CLAUDE.md`
- Modify: ticket completion notes (vault)

- [ ] **Step 1: Record the pinned versions in docs/05 §2**

Under the "### Version policy" bullets, add:

```markdown
Pinned at M0 (DN-M0-009, DN-M0-012, DN-M0-001; checked 2026-10-02):

| Area | Version |
| --- | --- |
| AGP / built-in Kotlin / Gradle | 9.2.1 / 2.2.10 / 9.4.1 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 29 |
| Compose BOM | 2026.09.00 (ui 1.12.1, material3 1.4.0) |
| activity-compose / navigation-compose / lifecycle | 1.13.0 / 2.10.2 / 2.11.0 |
| core-ktx | 1.19.1 |
| kotlinx.coroutines / kotlinx.serialization | 1.11.0 / 1.11.0 |
| OkHttp | 5.5.0 |
| JUnit / Robolectric (runs at SDK 36) | 4.13.2 / 4.17 |
| detekt | 1.23.8 |
```

- [ ] **Step 2: CLAUDE.md**

Replace the "Current state" paragraph's last sentence `detekt + CI in place (DN-M0-012). Next: DN-M0-001.` with:

```markdown
detekt + CI in place (DN-M0-012). Baseline (DN-M0-001): `DiscoverApplication` → `AppContainer`, canvas theme
(`ui/theme`), Robolectric 4.17 at SDK 36, `ArchitectureRulesTest`. Next: DN-M0-002 / 003 / 010.
```

In Commands, replace `- Unit + Robolectric/Compose tests: \`./gradlew test\`` with `- Unit + Robolectric/Compose tests: \`./gradlew testDebugUnitTest\` (release host tests are disabled).`

- [ ] **Step 3: Commit**

```bash
git add docs/05-discover-nearby-delivery-plan.md CLAUDE.md
git commit -m "Record pinned M0 versions and update CLAUDE.md for the baseline"
```

- [ ] **Step 4: Push and open a draft PR** so CI runs:

```bash
git push -u origin dn-m0-001-app-baseline
gh pr create --draft --base main --title "DN-M0-001: Revision 4 app baseline" --body "Ticket: DN-M0-001. Description follows via pr-description."
gh pr checks --watch --interval 20
```

Expected: `build  pass`, and the CI log shows `> Task :app:lintDebug`.

- [ ] **Step 5:** Run `simplify` on the branch diff; apply findings, rerun the full check, commit, push, and confirm CI.

- [ ] **Step 6:** Write the ticket completion notes: versions, the five open decisions, the lint fix, the Robolectric JVM flag, and every check actually run with its result.

- [ ] **Step 7:** Final whole-branch review (fresh reviewer); fix Critical/Important with a failing test first.

- [ ] **Step 8:** Use the `pr-description` skill (Ticket ID, acceptance criteria table, open decisions, no AI attribution), then `gh pr ready`.

- [ ] **Step 9: After the user merges** — ticket `done`; `NOW.md` next = DN-M0-002 / DN-M0-003 / DN-M0-010 (set each to `ready`); delete the branch locally and on origin.
