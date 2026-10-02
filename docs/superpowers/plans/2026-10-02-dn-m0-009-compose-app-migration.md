# DN-M0-009 Compose App Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Car App Library template scaffold (`:automotive` + `:shared`) with one Compose `:app` module that builds, passes a unit test, and launches on the AAOS userdebug emulator in Park and Drive.

**Architecture:** One Android application module, `:app`, namespace `com.kanyandula.discovernearby`. A single `ui/MainActivity` (Compose, Material 3) is the launcher and is declared `distractionOptimized`. The Car API (`android.car.jar`) is on the compile classpath only. The whole project compiles against SDK 37 (decision A), which removes the `core-ktx` 1.19.0 conflict and matches the current Compose BOM.

**Tech Stack:** AGP 9.2.1 with built-in Kotlin 2.2.10, Kotlin Compose compiler plugin 2.2.10, Compose BOM 2026.09.00 (ui 1.12.1, material3 1.4.0), `activity-compose` 1.13.0, `core-ktx` 1.19.0, JUnit 4.13.2, Gradle 9.4.1, JDK toolchain 21.

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-009-compose-app-migration.md`, plus `docs/03-discover-nearby-engineering-implementation-plan.md` §2, §3, §6, §6.1 and `docs/05-discover-nearby-delivery-plan.md` §1–2.

## Global Constraints

- Single application module, package-per-concern; no `:automotive`, no `:shared` (docs/03 §3, docs/05 §2).
- minSdk 29 (docs/03 §6.1). **compileSdk 37 everywhere** (decision A). targetSdk 36 (unchanged; raising it opts into new runtime behaviour and is not part of this ticket).
- No Car App Library: no `androidx.car.app` dependency, `CarAppService`, `automotive_app_desc.xml`, `templates_host` feature or `minCarApiLevel` (docs/03 §6, Manifest essentials).
- `android.car` from the SDK's `optional/android.car.jar`, `compileOnly` only (docs/03 §6.1).
- One launcher activity, `MainActivity`, in package `…discovernearby.ui`, declared `distractionOptimized` (docs/03 §3, §6).
- Manifest permissions: `ACCESS_FINE_LOCATION`, `INTERNET`. `uses-feature android.hardware.type.automotive` required.
- No DI framework, no database, no Robolectric yet (DN-M0-001), no detekt or CI yet (DN-M0-012).
- Every adb command uses `-s emulator-5554`; a Fire TV is also attached over adb. Gradle install tasks run with `ANDROID_SERIAL=emulator-5554`.
- Commits: never on local `main`; no AI attribution (`Co-Authored-By: Claude`, "Generated with Claude Code") in commits or the PR.

## Review Focus

1. **App blocked while driving.** If `distractionOptimized` is missing or sits on the wrong element, AAOS replaces the app with its block screen in Drive. Pinned by `ManifestContractTest.launcherActivityIsDistractionOptimized` (Task 2) and the Drive check (Task 3).
2. **Car API jar path silently wrong.** `compileOnly(files(...))` on a missing file does not fail the build until code uses `android.car` (DN-M0-010). Pinned by the `ls` check in Task 1, Step 2 and Step 8.
3. **Scaffold leftovers.** A stray `androidx.car.app` reference or Car App manifest entry would contradict Revision 4. Pinned by `ManifestContractTest.declaresNoCarAppLibraryComponents` (Task 2) and the grep in Task 1, Step 9.
4. **Wrong emulator image.** The Google Play `user` image ignores `distractionOptimized` for debug builds and refuses gear injection, so a Drive check there gives a false failure. Pinned by the `ro.build.type` check in Task 0, Step 5.
5. **Install lands on the Fire TV.** `installDebug` installs on every attached device. Pinned by `ANDROID_SERIAL=emulator-5554` in Task 3.

---

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `settings.gradle.kts` | Modify | Include only `:app` |
| `build.gradle.kts` (root) | Modify | Declare plugins `apply false` |
| `gradle/libs.versions.toml` | Replace | Versions, libraries and plugins for this ticket only |
| `app/build.gradle.kts` | Create | The single application module |
| `app/src/main/AndroidManifest.xml` | Create | Automotive feature, permissions, `MainActivity` |
| `app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt` | Create | Compose launcher activity with a placeholder screen |
| `app/src/main/res/**` | Move from `automotive/src/main/res/**` | Launcher icons, `strings.xml`, `themes.xml` |
| `app/src/main/keepRules/rules.keep` | Move from `automotive/src/main/keepRules/rules.keep` | R8 keep rules placeholder (AGP 9 layout) |
| `app/src/test/java/com/kanyandula/discovernearby/ManifestContractTest.kt` | Create | JVM test pinning the manifest contract |
| `automotive/`, `shared/` | Delete | The template scaffold |
| `CLAUDE.md`, `.gitignore` | Commit pending edits | Ticket workflow, attribution rule, Rev 4.1 Grant wording, ignores |
| This plan | Commit | Travels with the PR |

`ExampleUnitTest` and `ExampleInstrumentedTest` are deleted with `automotive/`; `ManifestContractTest` replaces the trivial test with one that checks the ticket's key contract.

---

### Task 0: Start the ticket and prepare the machine

**Files:**
- Modify: `~/.claude/projects/Discover Nearby/tickets/DN-M0-009-compose-app-migration.md` (frontmatter)
- Commit: `CLAUDE.md`, `.gitignore`, `docs/superpowers/plans/2026-10-02-dn-m0-009-compose-app-migration.md`

**Interfaces:**
- Consumes: nothing
- Produces: branch `dn-m0-009-compose-app`; SDK platform `android-37.0` with `optional/android.car.jar`; `AAOS_AOSP_33_userdebug` running as `emulator-5554`

- [ ] **Step 1: Mark the ticket in progress**

In the ticket frontmatter set:

```yaml
status: in_progress
branch: dn-m0-009-compose-app
```

- [ ] **Step 2: Update main and create the branch**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-009-compose-app
git status --short
```

Expected: `M .gitignore`, `M CLAUDE.md`, `?? docs/superpowers/` (the pending edits ride along).

- [ ] **Step 3: Commit the pending setup edits and this plan**

```bash
git add CLAUDE.md .gitignore
git commit -m "Add ticket workflow and Rev 4.1 rules to CLAUDE.md

Merged ticket/branch/PR workflow, no-AI-attribution rule, Grant gated on
distractionOptimizationRequired, and ignores for .remember/, .kotlin/,
_to_delete/ and graphify-out/."
git add docs/superpowers/plans/2026-10-02-dn-m0-009-compose-app-migration.md
git commit -m "Add DN-M0-009 implementation plan"
```

- [ ] **Step 4: Install SDK platform 37**

```bash
~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-37.0"
ls ~/Library/Android/sdk/platforms/
ls ~/Library/Android/sdk/platforms/android-37.0/optional/android.car.jar
```

Expected: `android-37.0` listed; `android.car.jar` exists. If the directory has a different name, use that name in Task 1, Step 5 and record it in the ticket.

- [ ] **Step 5: Switch to the userdebug emulator**

```bash
adb -s emulator-5554 emu kill            # stops the Google Play image if it is running
~/Library/Android/sdk/emulator/emulator -avd AAOS_AOSP_33_userdebug -no-snapshot-load &
adb -s emulator-5554 wait-for-device
adb -s emulator-5554 shell getprop ro.build.type
```

Expected: `userdebug`. If it prints `user`, the wrong AVD is running; stop and fix before Task 3.

---

### Task 1: Replace the scaffold with a single `:app` module that builds

**Files:**
- Replace: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`, `settings.gradle.kts`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` (minimal, no activity yet)
- Move: `automotive/src/main/res` → `app/src/main/res`; `automotive/src/main/keepRules` → `app/src/main/keepRules`
- Modify: `app/src/main/res/values/themes.xml`
- Delete: `automotive/`, `shared/`

**Interfaces:**
- Consumes: SDK platform `android-37.0` (Task 0)
- Produces: catalog aliases `libs.plugins.android.application`, `libs.plugins.kotlin.compose`, `libs.androidx.core.ktx`, `libs.androidx.activity.compose`, `libs.androidx.compose.bom`, `libs.androidx.compose.ui`, `libs.androidx.compose.material3`, `libs.junit`; resources `@string/app_name`, `@style/Theme.DiscoverNearby`, `@mipmap/ic_launcher`, `@mipmap/ic_launcher_round`

- [ ] **Step 1: Confirm the starting failure**

Run: `./gradlew assembleDebug --console=plain 2>&1 | grep -E "FAILED|compile against version 37"`
Expected: `:automotive:checkDebugAarMetadata FAILED` and "compile against version 37 or later".

- [ ] **Step 2: Replace the version catalog**

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.2.1"
# Must equal the Kotlin version built into AGP (./gradlew buildEnvironment → kotlin-gradle-plugin).
kotlin = "2.2.10"
coreKtx = "1.19.0"
activityCompose = "1.13.0"
composeBom = "2026.09.00"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 3: Root build file**

`build.gradle.kts`:

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 4: Settings — only `:app`**

In `settings.gradle.kts`, replace the two `include` lines with:

```kotlin
include(":app")
```

- [ ] **Step 5: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kanyandula.discovernearby"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kanyandula.discovernearby"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Car API (CarUxRestrictionsManager), provided by AAOS at runtime (docs/03 §6.1).
    // ponytail: hard-coded platform dir; update it with compileSdk.
    compileOnly(files("${android.sdkDirectory}/platforms/android-37.0/optional/android.car.jar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
```

- [ ] **Step 6: Minimal manifest so the module builds**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature
        android:name="android.hardware.type.automotive"
        android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.DiscoverNearby" />

</manifest>
```

- [ ] **Step 7: Move resources and keep rules; delete the scaffold**

```bash
mkdir -p app/src/main
git mv automotive/src/main/res app/src/main/res
git mv automotive/src/main/keepRules app/src/main/keepRules
git rm -r -q automotive shared
rm -rf automotive shared _to_delete    # untracked build output and the old backup folder
```

Then replace `app/src/main/res/values/themes.xml` (Compose draws its own UI; no action bar):

```xml
<resources>

    <style name="Theme.DiscoverNearby" parent="android:Theme.Material.NoActionBar" />
</resources>
```

- [ ] **Step 8: Build**

Run: `./gradlew assembleDebug --console=plain 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`.

If AGP reports it cannot find the platform for `release(37)`, read the platform name it asks for, make `compileSdk` and the `android.car.jar` path in Step 5 agree with the directory from Task 0, Step 4, and record the change in the ticket.

- [ ] **Step 9: Confirm no Car App Library remains**

Run: `git grep -n -E "androidx\.car\.app|CarAppService|minCarApiLevel|templates_host|automotive_app_desc" -- . ':!docs' ':!CLAUDE.md'`
Expected: no output.

- [ ] **Step 10: Commit**

```bash
git add -A
git status --short     # expect only app/, build files, catalog, settings; no local.properties
git commit -m "Replace Car App scaffold with a single Compose app module

Removes :automotive and :shared and the Car App Library. Compiles
against SDK 37, which current core-ktx (1.19.0) and the Compose BOM
(2026.09.00, ui 1.12.1) both require; targetSdk stays 36."
```

---

### Task 2: Compose `MainActivity`, pinned by a manifest contract test

**Files:**
- Create: `app/src/test/java/com/kanyandula/discovernearby/ManifestContractTest.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: catalog aliases and resources from Task 1
- Produces: `com.kanyandula.discovernearby.ui.MainActivity` (launcher, `distractionOptimized`). DN-M0-001 replaces its placeholder content with `DiscoverNavHost`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/ManifestContractTest.kt`:

```kotlin
package com.kanyandula.discovernearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Pins the manifest contract from docs/03 §6. Local unit tests run with the module
 * directory as the working directory, so the source manifest is read directly.
 */
class ManifestContractTest {

    private val manifest = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))

    private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

    private fun Element.androidAttr(name: String): String = getAttributeNS(ANDROID_NS, name)

    private fun elements(tag: String): List<Element> = manifest.getElementsByTagName(tag).elements()

    @Test
    fun launcherActivityIsDistractionOptimized() {
        val launchers = elements("activity").filter { activity ->
            activity.getElementsByTagName("category").elements()
                .any { it.androidAttr("name") == "android.intent.category.LAUNCHER" }
        }
        assertEquals("exactly one launcher activity", 1, launchers.size)
        assertEquals(".ui.MainActivity", launchers.single().androidAttr("name"))

        val optimized = launchers.single().getElementsByTagName("meta-data").elements().any {
            it.androidAttr("name") == "distractionOptimized" && it.androidAttr("value") == "true"
        }
        assertTrue("launcher activity must declare distractionOptimized=true", optimized)
    }

    @Test
    fun requiresAutomotiveHardware() {
        val automotive = elements("uses-feature").single { it.androidAttr("name") == "android.hardware.type.automotive" }
        assertEquals("true", automotive.androidAttr("required"))
    }

    @Test
    fun declaresLocationAndInternetPermissions() {
        val permissions = elements("uses-permission").map { it.androidAttr("name") }.toSet()
        assertTrue(permissions.containsAll(setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.INTERNET")))
    }

    @Test
    fun declaresNoCarAppLibraryComponents() {
        val names = (elements("service") + elements("meta-data") + elements("uses-feature") + elements("action"))
            .map { it.androidAttr("name") }
        assertFalse(names.any { it.startsWith("androidx.car.app") || it == "android.software.car.templates_host" })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --console=plain 2>&1 | grep -E "ManifestContractTest > .* (PASSED|FAILED)|tests completed"`
Expected: `launcherActivityIsDistractionOptimized FAILED` and `declaresLocationAndInternetPermissions FAILED`; the other two pass.

- [ ] **Step 3: Write `MainActivity`**

`app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt`:

```kotlin
package com.kanyandula.discovernearby.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.R

// ponytail: placeholder screen; DN-M0-001 adds the theme and DN-M0-002 the Discover grid.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Complete the manifest**

Replace `app/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature
        android:name="android.hardware.type.automotive"
        android:required="true" />

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.DiscoverNearby">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true">

            <!--
                Without this, AAOS replaces the app with its block screen while driving.
                Acceptable for this sideloaded POC only: Play rejects distractionOptimized
                on any activity other than CarAppActivity (docs/05 §9, V8).
            -->
            <meta-data
                android:name="distractionOptimized"
                android:value="true" />

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 5: Run the tests and the build**

Run: `./gradlew testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "ManifestContractTest > .* (PASSED|FAILED)|BUILD"`
Expected: four `PASSED`, `BUILD SUCCESSFUL`.

If the tests fail with `FileNotFoundException`, the working directory is not the module directory; replace the path with `File(System.getProperty("user.dir"), "src/main/AndroidManifest.xml")`, rerun, and note it in the ticket.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "Add distraction-optimized Compose MainActivity

ManifestContractTest pins the launcher, distractionOptimized, the
automotive feature, the permissions, and the absence of Car App
Library components."
```

---

### Task 3: Verify on the AAOS userdebug emulator in Park and Drive

**Files:**
- Modify: ticket completion notes only (Task 4)

**Interfaces:**
- Consumes: `MainActivity` (Task 2); userdebug emulator (Task 0)
- Produces: recorded evidence for the ticket's emulator criterion

- [ ] **Step 1: Install on the emulator only**

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain 2>&1 | grep -E "Installing|Installed on|BUILD"
```

Expected: `Installed on 1 device.` and `BUILD SUCCESSFUL`.

- [ ] **Step 2: Confirm it is a launcher entry and is distraction-optimized**

```bash
adb -s emulator-5554 shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | grep discovernearby
adb -s emulator-5554 shell cmd car_service get-do-activities com.kanyandula.discovernearby
```

Expected: the first prints `com.kanyandula.discovernearby/.ui.MainActivity`; the second lists `com.kanyandula.discovernearby.ui.MainActivity`.

- [ ] **Step 3: Launch in Park**

```bash
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4      # GEAR_SELECTION = PARK
adb -s emulator-5554 shell am start -n com.kanyandula.discovernearby/.ui.MainActivity
adb -s emulator-5554 shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity" | head -2
```

Expected: the resumed activity is `com.kanyandula.discovernearby/.ui.MainActivity`. Also confirm "Discover Nearby" appears in the AAOS launcher grid.

- [ ] **Step 4: Switch to Drive (moving) with the app in front**

```bash
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8      # GEAR_SELECTION = DRIVE
adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60 &
sleep 5
adb -s emulator-5554 shell dumpsys car_service --services CarDrivingStateService | grep -i -E "state|moving"
adb -s emulator-5554 shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity" | head -2
```

Expected: driving state `2` (moving); the resumed activity is still `…/.ui.MainActivity`, not `ActivityBlockingActivity`.

- [ ] **Step 5: Return to Park**

```bash
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
```

Expected: no crash; the app stays in front.

---

### Task 4: Close out the ticket and open the PR

**Files:**
- Modify: `~/.claude/projects/Discover Nearby/tickets/DN-M0-009-compose-app-migration.md` (completion notes)

**Interfaces:**
- Consumes: Tasks 0–3
- Produces: PR into `main`

- [ ] **Step 1: Final local check**

Run: `./gradlew testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "BUILD|FAILED"`
Expected: `BUILD SUCCESSFUL`. (`detekt` arrives with DN-M0-012.)

- [ ] **Step 2: Run `simplify` on the branch diff and apply its findings**

Re-run Step 1 after any change, then commit:

```bash
git add -A && git commit -m "Apply simplify review findings"
```

Skip the commit if `simplify` changed nothing.

- [ ] **Step 3: Write the ticket completion notes**

Append to the ticket under "Completion notes":

```markdown
- 2026-10-02 — Branch `dn-m0-009-compose-app`.
- compileSdk: 37 (platform `android-37.0`), targetSdk 36. Reason: core-ktx 1.19.0 and the current Compose BOM 2026.09.00 (ui 1.12.1) both require minCompileSdk 37; pinning core-ktx 1.18.0 alone would still leave Compose on an old BOM.
- Changed: removed :automotive and :shared; added :app (MainActivity, manifest, resources); version catalog rewritten.
- Checks run: each command from Task 1 Step 8, Task 2 Step 5 and Task 3 Steps 1–5, with its actual output line (BUILD result, test PASSED lines, resumed activity in Park and in Drive).
- Remaining: detekt + CI (DN-M0-012); Robolectric and the full dependency set (DN-M0-001).
```

- [ ] **Step 4: Push and open the PR with the `pr-description` skill**

```bash
git push -u origin dn-m0-009-compose-app
```

Then run the `pr-description` skill. The body must include `Ticket: DN-M0-009`, list each acceptance criterion with how it was met, and contain no AI attribution.

- [ ] **Step 5: After the user merges**

Set the ticket `status: done`, update `NOW.md` (next: DN-M0-012), and delete the branch:

```bash
git switch main && git pull --ff-only
git branch -d dn-m0-009-compose-app
git push origin --delete dn-m0-009-compose-app
```
