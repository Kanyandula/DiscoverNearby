# DN-M0-008 Stub Navigation App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A tiny separate APK in `tools/stub-navigation/` that any app's `ACTION_VIEW` `geo:` intent resolves to on the reference emulator. It shows the received coordinates, logs `StubNav: received geo:…` and does nothing else, so navigation hand-off becomes observable without a commercial navigation app.

**Architecture:**
- **Build.** The stub is the Gradle module `:stub-navigation` in the root build, with its project directory at `tools/stub-navigation`, so CI builds, lints, runs detekt and tests it with no new workflow step. Both modules read one compile SDK, which moves into the version catalog.
- **Activity.** One `ComponentActivity`, `singleTask`, whose filter is VIEW + DEFAULT + scheme `geo`. It shows the received URI verbatim on a Compose screen and logs it. A new-intent listener makes every later hand-off replace the shown and logged destination.

**Tech Stack:** AGP 9.2.1, Kotlin 2.2.10, Compose BOM 2026.09.00 (Material 3), `activity-compose` 1.13.0, Robolectric 4.17 at SDK 36, compose-test v2 `createEmptyComposeRule`, `ShadowLog`; adb (`cmd package query-activities`, `am start`, `logcat -s StubNav`, `cmd car_service`, `cmd uimode`).

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-008-stub-navigation-app.md`; `docs/03-discover-nearby-engineering-implementation-plan.md` §11 (Stub navigation app); `docs/04-discover-nearby-test-demo-plan.md` §2 (Stub navigation app setup), §5 E, §7 S.

## Global Constraints

- A separate tiny APK in `tools/stub-navigation/`: one activity with an `ACTION_VIEW` intent filter for the `geo` scheme, declared `distractionOptimized` so it shows while driving (ticket AC, docs/03 §11).
- It displays the received coordinates on a simple Compose screen and logs `StubNav: received geo:…` (ticket AC).
- It appears in `adb shell cmd package query-activities -a android.intent.action.VIEW -d "geo:53.1440,-6.0633"` (ticket AC).
- It contains no routing or guidance behaviour (ticket AC).
- How it is built (separate Gradle module or separate build) is recorded (ticket AC).
- One compileSdk for the whole project; minSdk 29; targetSdk 36 (CLAUDE.md).
- The app never targets a specific navigation app (CLAUDE.md), so the stub must be reachable by an implicit intent alone.
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes. Never commit on local `main`. No AI attribution.

## Open decisions (recorded here, in the ticket and the PR)

1. **Built as a module of the root build, not a separate build.** `include(":stub-navigation")` with `projectDir = tools/stub-navigation`.
   - CI's `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` then covers it unchanged.
   - A separate build would need its own settings, catalog import and CI step.
   - The stub is not a dependency of `:app`, and the app's `ArchitectureRulesTest` scans only `app/src`.
2. **One compileSdk moves into the catalog** as `android-compileSdk = "37"` in `gradle/libs.versions.toml`.
   - `:app` derives `compileApi` (and its `android.car.jar` path) from it, `:stub-navigation` reads the same value, and CI's platform install reads it there.
   - The alternative, a second literal `37` in the stub, breaks "one compileSdk".
3. **`singleTask` plus a new-intent listener.**
   - The app launches with `FLAG_ACTIVITY_NEW_TASK`. With the default launch mode, a repeat hand-off whose intent equals the stub task's root intent only brings the old screen forward, with no new log line.
   - With `singleTask`, every hand-off reaches the one instance through `onNewIntent`. The listener also calls `setIntent`, so a recreated activity (day/night switch) shows the latest destination.
4. **The URI is shown verbatim.** No parsing, so the tester compares exactly what the app sent (`geo:%.6f,%.6f`, Locale.US) against the place's coordinates (docs/04 E: to 5 decimal places).
5. **Package and identity.**
   - `applicationId` / namespace `com.kanyandula.stubnavigation`.
   - No launcher entry, because it is only a handler.
   - The platform theme is `Theme.DeviceDefault.NoActionBar`, with a Compose dark colour scheme on top.
   - Log tag `StubNav`, at INFO.

## Review Focus

1. **A second hand-off while the stub is still open** (the driver went Home instead of Back) shows and logs the new destination, not the old one. Pinned by `StubNavigationActivityTest.aLaterHandOffReplacesTheFirst` (Task 2), `StubManifestContractTest.keepsOneInstance` (Task 1) and emulator Task 3 Step 3.
2. **A hand-off while the car is moving:** the stub appears, not AAOS's blocking screen. Pinned by `StubManifestContractTest.isDistractionOptimized` (Task 1) and emulator Task 3 Step 5.
3. **Implicit resolution from another app** with no package named. This needs `exported="true"` and the DEFAULT category; without them Discover Nearby gets `ActivityNotFoundException` and shows "Navigation unavailable". Pinned by `StubManifestContractTest.oneExportedActivityHandlesGeoViewIntents` (Task 1) and the query-activities check in Task 3 Step 2.
4. **A day/night switch after a second hand-off** still shows the latest destination. Robolectric's `ActivityController.recreate()` re-attaches the controller's original intent, not the activity's `setIntent` one, so it cannot test this. Pinned by emulator Task 3 Step 4 (`cmd uimode night`).
5. **Opened without a destination** (`am start -n`): no crash, and the screen says so. Pinned by `StubNavigationActivityTest.openedWithoutADestinationSaysSo` (Task 2).

> **After the simplify pass:** `StubManifestContractTest` was replaced. Its pins are now `StubNavigationActivityTest.anImplicitGeoViewIntentResolvesToTheStub` and `.showsWhileDrivingAndKeepsOneInstance`, which check the merged manifest through Robolectric's PackageManager. Each manifest mutation fails one of them.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `gradle/libs.versions.toml` (modify) | Adds `android-compileSdk = "37"`, the project's one compile SDK. |
| `app/build.gradle.kts` (modify) | `compileApi` comes from the catalog; nothing else changes. |
| `.github/workflows/ci.yml` (modify) | The platform install reads `android-compileSdk` from the catalog. |
| `settings.gradle.kts` (modify) | Includes `:stub-navigation` at `tools/stub-navigation`. |
| `tools/stub-navigation/build.gradle.kts` (create) | Android application module: Compose, detekt, Robolectric host tests. |
| `tools/stub-navigation/src/main/AndroidManifest.xml` (create) | The one activity, its geo filter, `singleTask`, `distractionOptimized`. |
| `tools/stub-navigation/src/main/res/values/strings.xml` (create) | Screen text. |
| `tools/stub-navigation/src/main/java/com/kanyandula/stubnavigation/StubNavigationActivity.kt` (create) | Receives, logs and shows the `geo:` URI; private screen composable. |
| `tools/stub-navigation/src/test/java/com/kanyandula/stubnavigation/StubManifestContractTest.kt` (create) | Pins the manifest contract (docs/03 §11). |
| `tools/stub-navigation/src/test/java/com/kanyandula/stubnavigation/StubNavigationActivityTest.kt` (create) | Pins show + log, a later hand-off, no destination. |
| `docs/03-…` §11, `docs/04-…` §2, `CLAUDE.md` (modify, Task 3) | Record how the stub is built and installed. |

Scratchpad `S=/private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad`. `"$S/pause.sh" N` waits N seconds, because foreground `sleep` is blocked in this harness. The emulator `emulator-5554` (`AAOS_AOSP_33_userdebug`) is running; the driver is user 10.

---

### Task 0: Start the ticket

- [ ] **Step 1:** In the ticket set `status: in_progress`, `branch: dn-m0-008-stub-navigation`.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-008-stub-navigation
git add docs/superpowers/plans/2026-10-05-dn-m0-008-stub-navigation.md
git commit -m "Add DN-M0-008 implementation plan"
```

---

### Task 1: The `:stub-navigation` module and its manifest contract

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts:1-2,14`, `.github/workflows/ci.yml:29-33`, `settings.gradle.kts`
- Create: `tools/stub-navigation/build.gradle.kts`, `tools/stub-navigation/src/main/AndroidManifest.xml`, `tools/stub-navigation/src/main/res/values/strings.xml`, `tools/stub-navigation/src/main/java/com/kanyandula/stubnavigation/StubNavigationActivity.kt`
- Test: `tools/stub-navigation/src/test/java/com/kanyandula/stubnavigation/StubManifestContractTest.kt`

**Interfaces:**
- Produces:
  - Gradle project `:stub-navigation`, with `applicationId` `com.kanyandula.stubnavigation`.
  - Activity class `com.kanyandula.stubnavigation.StubNavigationActivity : ComponentActivity`.
  - String resources `R.string.app_name` ("Stub navigation"), `R.string.received_destination` ("Received destination") and `R.string.no_destination` ("No destination received").
  - Catalog version `libs.versions.android.compileSdk`.

- [ ] **Step 1: One compile SDK in the catalog (refactor; the existing suite is the guard)**

In `gradle/libs.versions.toml`, first lines under `[versions]`:

```toml
[versions]
# The one compile SDK for every module (CLAUDE.md); CI installs this platform.
android-compileSdk = "37"
agp = "9.2.1"
```

In `app/build.gradle.kts`, delete the first two lines (`// Single source …` and `val compileApi = 37`). Directly after the `plugins { … }` block, add:

```kotlin
// The project's one compile SDK (gradle/libs.versions.toml); the android.car.jar path below derives from it.
val compileApi = libs.versions.android.compileSdk.get().toInt()
```

In `.github/workflows/ci.yml`, replace the comment and the two `api=` / `test -n` lines of "Install Android SDK platform":

```yaml
      # compileSdk for every module (and the app's android.car.jar path) comes from gradle/libs.versions.toml.
      - name: Install Android SDK platform
        run: |
          api=$(sed -n 's/^android-compileSdk = "\([0-9][0-9]*\)"$/\1/p' gradle/libs.versions.toml)
          test -n "$api" || { echo "android-compileSdk not found in gradle/libs.versions.toml"; exit 1; }
          yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-$api.0" > /dev/null
```

Run:

```bash
sed -n 's/^android-compileSdk = "\([0-9][0-9]*\)"$/\1/p' gradle/libs.versions.toml
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain -q
```

Expected: `37`, then a green build with the suite unchanged (137 tests).

- [ ] **Step 2: Module scaffolding**

`settings.gradle.kts`, replacing `include(":app")`:

```kotlin
include(":app")
// Emulator stand-in for a navigation app (docs/03 §11); built, linted and tested with the app.
include(":stub-navigation")
project(":stub-navigation").projectDir = file("tools/stub-navigation")
```

`tools/stub-navigation/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.kanyandula.stubnavigation"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.kanyandula.stubnavigation"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Robolectric reads merged resources and the manifest.
            isIncludeAndroidResources = true
        }
    }
}

// Defaults plus config/detekt/detekt.yml (picked up from the root by convention), as in :app.
detekt {
    buildUponDefaultConfig = true
}

tasks.withType<Test>().configureEach {
    // StubManifestContractTest reads the source manifest; without this a manifest-only change leaves it UP-TO-DATE.
    inputs.file("src/main/AndroidManifest.xml")
    // Robolectric at SDK 36 (Android 16) touches jdk.internal.access on JDK 21.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
```

`tools/stub-navigation/src/main/AndroidManifest.xml`, with no activity yet:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar" />

</manifest>
```

`tools/stub-navigation/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Stub navigation</string>
    <string name="received_destination">Received destination</string>
    <string name="no_destination">No destination received</string>
</resources>
```

- [ ] **Step 3: Write the failing test**

`tools/stub-navigation/src/test/java/com/kanyandula/stubnavigation/StubManifestContractTest.kt`:

```kotlin
package com.kanyandula.stubnavigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Pins docs/03 §11: one activity that any app's ACTION_VIEW geo: intent resolves to, shown while driving.
 * Local unit tests run in the module directory, so the source manifest is read directly.
 */
class StubManifestContractTest {

    private companion object {
        // Parsed once per class; JUnit creates a new instance for every test.
        val activity: Element by lazy {
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(File("src/main/AndroidManifest.xml"))
                .getElementsByTagName("activity").elements().single()
        }
    }

    @Test
    fun oneExportedActivityHandlesGeoViewIntents() {
        assertEquals(".StubNavigationActivity", activity.androidAttr("name"))
        // Reachable from another app's implicit intent; the app never names a navigation package.
        assertEquals("true", activity.androidAttr("exported"))
        val filter = activity.getElementsByTagName("intent-filter").elements().single()
        assertEquals(listOf("android.intent.action.VIEW"), filter.named("action"))
        // startActivity resolves implicit intents only to activities in the DEFAULT category.
        assertEquals(listOf("android.intent.category.DEFAULT"), filter.named("category"))
        assertEquals(listOf("geo"), filter.getElementsByTagName("data").elements().map { it.androidAttr("scheme") })
    }

    @Test
    fun isDistractionOptimized() {
        val optimized = activity.getElementsByTagName("meta-data").elements().any {
            it.androidAttr("name") == "distractionOptimized" && it.androidAttr("value") == "true"
        }
        assertTrue("the stub must show while driving", optimized)
    }

    // A later hand-off must reach the open stub (onNewIntent), not just bring the old screen forward.
    @Test
    fun keepsOneInstance() {
        assertEquals("singleTask", activity.androidAttr("launchMode"))
    }
}

private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

private fun Element.androidAttr(name: String): String = getAttributeNS(ANDROID_NS, name)

private fun Element.named(tag: String): List<String> = getElementsByTagName(tag).elements().map { it.androidAttr("name") }
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./gradlew :stub-navigation:testDebugUnitTest --console=plain -q`
Expected: 3 tests FAIL, each with `NoSuchElementException` ("List is empty"), because the manifest has no activity yet.

- [ ] **Step 5: The activity in the manifest, plus its class**

Replace the `<application … />` element in `tools/stub-navigation/src/main/AndroidManifest.xml`:

```xml
    <application
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar">

        <!--
            The emulator's stand-in for a navigation app (docs/03 §11): any app's ACTION_VIEW geo: intent
            resolves here. singleTask, so a later hand-off reaches the open stub instead of being dropped.
        -->
        <activity
            android:name=".StubNavigationActivity"
            android:exported="true"
            android:launchMode="singleTask">

            <!-- Shown while driving, like a real navigation app. -->
            <meta-data
                android:name="distractionOptimized"
                android:value="true" />

            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:scheme="geo" />
            </intent-filter>
        </activity>
    </application>
```

`tools/stub-navigation/src/main/java/com/kanyandula/stubnavigation/StubNavigationActivity.kt`, a class only, so lint's `MissingClass` passes. Task 2 gives it behaviour.

```kotlin
package com.kanyandula.stubnavigation

import androidx.activity.ComponentActivity

class StubNavigationActivity : ComponentActivity()
```

- [ ] **Step 6: Run the tests and the full check**

Run: `./gradlew :stub-navigation:testDebugUnitTest --console=plain -q`. Expected: 3/3 PASS.

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q`. Expected: green. The suite is 140 (137 + 3). Lint may report new *warnings* for the stub, such as `MissingApplicationIcon`; record their names in the ledger. Errors fail the step.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts .github/workflows/ci.yml settings.gradle.kts tools/stub-navigation
git commit -m "Add the stub navigation module and its manifest contract"
```

---

### Task 2: Receive, show and log the destination

**Files:**
- Modify: `tools/stub-navigation/src/main/java/com/kanyandula/stubnavigation/StubNavigationActivity.kt`
- Test: `tools/stub-navigation/src/test/java/com/kanyandula/stubnavigation/StubNavigationActivityTest.kt`

**Interfaces:**
- Consumes (Task 1): `StubNavigationActivity` (declared, `singleTask`), the strings `received_destination` and `no_destination`.
- Produces (for Task 3):
  - Log tag `StubNav`, message `received <uri>` at INFO, one per delivered intent that has data.
  - The screen shows the URI string verbatim.

- [ ] **Step 1: Write the failing tests**

`tools/stub-navigation/src/test/java/com/kanyandula/stubnavigation/StubNavigationActivityTest.kt`:

```kotlin
package com.kanyandula.stubnavigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLog

// The app hands off geo:%.6f,%.6f (Locale.US); the stub must show and log exactly that (docs/04 E).
private const val GREYSTONES = "geo:53.144000,-6.063300"
private const val WICKLOW = "geo:52.980000,-6.044000"

@RunWith(RobolectricTestRunner::class)
class StubNavigationActivityTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private fun intent(uri: String? = null) =
        Intent(RuntimeEnvironment.getApplication(), StubNavigationActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri?.let(Uri::parse)
        }

    private fun launch(uri: String?) = Robolectric.buildActivity(StubNavigationActivity::class.java, intent(uri)).setup()

    private fun logged() = ShadowLog.getLogsForTag("StubNav").map { it.msg }

    @Test
    fun showsAndLogsTheReceivedDestination() {
        launch(GREYSTONES)
        rule.onNodeWithText(GREYSTONES).assertIsDisplayed()
        assertEquals(listOf("received $GREYSTONES"), logged())
    }

    // A second hand-off while the stub is still open replaces the first, on screen and in the log.
    @Test
    fun aLaterHandOffReplacesTheFirst() {
        launch(GREYSTONES).newIntent(intent(WICKLOW))
        rule.onNodeWithText(WICKLOW).assertIsDisplayed()
        rule.onNodeWithText(GREYSTONES).assertDoesNotExist()
        assertEquals(listOf("received $GREYSTONES", "received $WICKLOW"), logged())
    }

    @Test
    fun openedWithoutADestinationSaysSo() {
        launch(uri = null)
        rule.onNodeWithText("No destination received").assertIsDisplayed()
        assertEquals(emptyList<String>(), logged())
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :stub-navigation:testDebugUnitTest --tests '*StubNavigationActivityTest' --console=plain -q`
Expected: 3 FAIL. The display assertions fail because no compose hierarchy is found (the activity has no content), and the log assertions get an empty list.

- [ ] **Step 3: Implement**

Replace `tools/stub-navigation/src/main/java/com/kanyandula/stubnavigation/StubNavigationActivity.kt`:

```kotlin
package com.kanyandula.stubnavigation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private const val TAG = "StubNav"
private val ScreenPadding = 48.dp
private val LineGap = 16.dp

/**
 * The emulator's stand-in for a navigation app (docs/03 §11): shows and logs the geo: URI it is handed,
 * verbatim. No routing or guidance.
 */
class StubNavigationActivity : ComponentActivity() {

    private var destination by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receive(intent)
        // singleTask: a later hand-off arrives here. setIntent so a recreated activity (day/night) shows it.
        addOnNewIntentListener {
            setIntent(it)
            receive(it)
        }
        setContent { StubNavigationScreen(destination) }
    }

    // ponytail: logs again when the activity is recreated (e.g. a day/night switch); harmless for a test tool.
    private fun receive(intent: Intent) {
        destination = intent.data
        intent.data?.let { Log.i(TAG, "received $it") }
    }
}

@Composable
private fun StubNavigationScreen(destination: Uri?) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(LineGap),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.received_destination), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = destination?.toString() ?: stringResource(R.string.no_destination),
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :stub-navigation:testDebugUnitTest --console=plain -q`
Expected: 6/6 PASS (3 manifest + 3 activity).

If the compose rule finds no hierarchy in an `ActivityController`-built activity, record a ruling and switch `launch` to `ActivityScenario.launch<StubNavigationActivity>(intent(uri))`. In that case, test the new intent through `ActivityController` and assert only the log. That is the documented `createEmptyComposeRule` use case.

- [ ] **Step 5: Full check, then commit**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q`. Expected: green, suite 143.

```bash
git add tools/stub-navigation/src
git commit -m "Show and log the geo: destination the stub receives"
```

---

### Task 3: Prove it on the emulator, record how it is built, close out

**Files:**
- Modify: `docs/03-discover-nearby-engineering-implementation-plan.md` (§11 "Stub navigation app"), `docs/04-discover-nearby-test-demo-plan.md` (§2 "Stub navigation app setup", step 1), `CLAUDE.md`; the ticket (vault).

**Interfaces:**
- Consumes:
  - Task 1: Gradle project `:stub-navigation` and `applicationId` `com.kanyandula.stubnavigation`.
  - Task 2: log tag `StubNav`, message `received <uri>`.

- [ ] **Step 1: Install**

```bash
S=/private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad
ANDROID_SERIAL=emulator-5554 ./gradlew :stub-navigation:installDebug --console=plain | grep "Installed on"
adb -s emulator-5554 shell pm list packages --user 10 | grep stubnavigation
```

Expected: `Installed on 1 device.` and `package:com.kanyandula.stubnavigation`.

- [ ] **Step 2: It answers the navigation intent (ticket AC; Review Focus 3)**

```bash
adb -s emulator-5554 shell cmd package query-activities -a android.intent.action.VIEW -d "geo:53.1440,-6.0633" | grep -E "packageName=|name="
adb -s emulator-5554 shell cmd package query-activities --user 10 -a android.intent.action.VIEW -d "geo:53.1440,-6.0633" | grep -E "packageName=|name="
```

Expected: `com.kanyandula.stubnavigation` / `.StubNavigationActivity` in both. Record any **other** handler listed: docs/03 §11 asks whether a chooser would appear.

- [ ] **Step 3: Hand-off, then a second hand-off while it is open (ticket Verification; Review Focus 1)**

```bash
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell am start -a android.intent.action.VIEW -d "geo:53.1440,-6.0633"; "$S/pause.sh" 3
adb -s emulator-5554 exec-out screencap -p > "$S/m0008-handoff-1.png"
adb -s emulator-5554 shell am start -a android.intent.action.VIEW -d "geo:52.980000,-6.044000"; "$S/pause.sh" 3
adb -s emulator-5554 exec-out screencap -p > "$S/m0008-handoff-2.png"
adb -s emulator-5554 logcat -d -s StubNav
```

Expected:
- `m0008-handoff-1.png` shows `geo:53.1440,-6.0633` under "Received destination".
- `m0008-handoff-2.png` shows `geo:52.980000,-6.044000`.
- logcat has `I StubNav: received geo:53.1440,-6.0633`, then `I StubNav: received geo:52.980000,-6.044000`.
- If `am start` reports `Warning: Activity not started, intent has been delivered to currently running top-most instance`, that is singleTask working.

- [ ] **Step 4: A day/night switch keeps the latest destination (Review Focus 4)**

```bash
night=$(adb -s emulator-5554 shell cmd uimode night | awk '{print $NF}'); echo "was: $night"   # "Night mode: no" → no
adb -s emulator-5554 shell cmd uimode night yes; "$S/pause.sh" 3
adb -s emulator-5554 exec-out screencap -p > "$S/m0008-night.png"
adb -s emulator-5554 shell cmd uimode night "$night"
```

Expected: `m0008-night.png` still shows `geo:52.980000,-6.044000`. If the switch does not recreate the activity on this image (the screen and log don't change), record that and how it was checked; the point is that it never shows the first destination.

- [ ] **Step 5: Shows while driving (ticket AC; Review Focus 2)**

```bash
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8
adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60 &
"$S/pause.sh" 3
adb -s emulator-5554 shell dumpsys car_service --services CarDrivingStateService | grep -i "state"
adb -s emulator-5554 shell cmd car_service get-do-activities com.kanyandula.stubnavigation
adb -s emulator-5554 shell am start -a android.intent.action.VIEW -d "geo:53.200000,-6.100000"; "$S/pause.sh" 3
adb -s emulator-5554 exec-out screencap -p > "$S/m0008-driving.png"
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
```

Expected:
- The driving state reads moving.
- `get-do-activities` lists `com.kanyandula.stubnavigation.StubNavigationActivity`.
- `m0008-driving.png` shows the stub with `geo:53.200000,-6.100000`, not AAOS's blocking screen.
- Park restores the gear. Wait for the continuous events to end, or let them run out.

- [ ] **Step 6: Record how it is built**

In docs/03 §11, after "A minimal separate APK in `tools/stub-navigation/`.", add:

```markdown
It is the Gradle module `:stub-navigation` in the root build (`projectDir = tools/stub-navigation`), so CI builds, lints and tests it with the app; it shares the project's one compile SDK (`android-compileSdk` in the version catalog). Install: `./gradlew :stub-navigation:installDebug`.
```

In docs/04 §2 "Stub navigation app setup", replace step 1 with:

```markdown
1. Build and install `tools/stub-navigation`: `ANDROID_SERIAL=emulator-5554 ./gradlew :stub-navigation:installDebug`.
```

In `CLAUDE.md`:

1. Replace ``compileSdk 37 (one `compileApi` value in `app/build.gradle.kts`, which also derives the `android.car.jar` path)`` with ``compileSdk 37 (one `android-compileSdk` in `gradle/libs.versions.toml`; the app derives its `android.car.jar` path from it)``. Rewrap the paragraph if needed.
2. Before the "Next:" sentence of "Current state", add: ``Stub navigation app (DN-M0-008): module `:stub-navigation` in `tools/stub-navigation/`, a `geo:` VIEW handler (`singleTask`, distractionOptimized) that shows the URI and logs `StubNav: received geo:…`.``
3. Under "Commands", add: ``- Stub navigation app: `ANDROID_SERIAL=emulator-5554 ./gradlew :stub-navigation:installDebug`; watch hand-offs with `adb -s emulator-5554 logcat -s StubNav`.``

Run `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q`. Expected: green.

```bash
git add docs/03-discover-nearby-engineering-implementation-plan.md docs/04-discover-nearby-test-demo-plan.md CLAUDE.md
git commit -m "Record how the stub navigation app is built and installed"
```

- [ ] **Step 7: Close out**

1. Append completion notes to the ticket. Include:
   - each AC with its result
   - the outputs of Steps 2–5, including any other `geo:` handler and the lint warnings
   - the build decision (Open decisions 1–2)
   - checks run
   - remaining decisions
2. Push and open a draft PR.
3. Run the `simplify` skill and fix its findings.
4. Do the final whole-branch review (executing-plans).
5. Write the PR description with the `pr-description` skill: ticket ID and acceptance criteria, no AI attribution.
6. Run `gh pr ready`.
