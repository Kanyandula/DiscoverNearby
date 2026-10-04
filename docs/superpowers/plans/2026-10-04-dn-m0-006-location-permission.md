# DN-M0-006 Location Permission and Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Discovery reads the vehicle's real location only when asked; without permission the Recommendations screen asks for it (Grant only while restrictions allow, "park first" otherwise, a denied message after a refusal), a grant resumes discovery, and no fix or location off ends in the Location unavailable message.

**Architecture:** `location/AndroidLocationProvider` checks fine-or-coarse permission, takes the first enabled provider (GPS, then network) and waits up to `LOCATION_TIMEOUT_MILLIS` for one fix through `LocationManagerCompat.getCurrentLocation`. `RecommendationsViewModel` tracks a denial and exposes `PermissionRequired(canRequest, denied)`; `onPermissionResult(granted)` resumes discovery. The Recommendations destination owns a `RequestMultiplePermissions` launcher (fine and coarse together); the screen offers Grant only when `canRequest`.

**Tech Stack:** `android.location.LocationManager` + `androidx.core.location.LocationManagerCompat` (core 1.19.1), `activity-compose` `rememberLauncherForActivityResult`, kotlinx-coroutines, Robolectric 4.17 (`ShadowLocationManager`), Compose UI tests.

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-006-location-permission-and-recovery.md`; `docs/02-discover-nearby-ux-interaction-spec.md` §10–§12; `docs/03-discover-nearby-engineering-implementation-plan.md` §7, §15, §16, §18; `docs/04-discover-nearby-test-demo-plan.md` §2 (Injecting test locations), §6 (L, M, N), §7 K; `docs/01` §14; design `docs/design/05a-permission-driving.png`, `05b-permission-parked.png`, `06-location-unavailable.png` and their canvas artboards.

## Global Constraints

- Location is read only after the user requests discovery (ticket AC; docs/01 §14) — `DiscoverUseCase` already reads it per request.
- Missing permission shows `PermissionRequired(canRequest = !distractionOptimizationRequired)` on the Recommendations destination; `canRequest` updates live (ticket AC; docs/03 §7).
- Grant is offered only when `distractionOptimizationRequired` is false; while it is true the screen asks the user to park and issues no permission request (ticket AC; docs/02 §10).
- The runtime request asks for `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` together and works with an approximate-only grant (ticket AC).
- Denial, unavailable location, Retry and Back never crash; a grant resumes discovery for the selected category (ticket AC).
- Copy verbatim from docs/02 §10–§11 and the 05a/05b/06 artboards; nothing named "parked" in code; never log raw coordinates (CLAUDE.md "Secrets").
- Only `ui/` imports Compose; only `car/CarDrivingRestrictions.kt` imports `android.car` (`ArchitectureRulesTest`).
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes; never commit on local `main`; no AI attribution.

## Open decisions (recorded here, in the ticket and the PR)

1. **`RequestMultiplePermissions`, not `RequestPermission`.** The AC names `RequestPermission()` but also requires fine and coarse "together" and an approximate-only grant; only the multiple-permission contract can ask for both in one dialog. Either permission granted counts as a grant.
2. **`PermissionRequired` gains `denied: Boolean = false`** (docs/03 §15 has only `canRequest`) so the screen can show docs/02 §10's "Permission denied" copy. While driving the "park first" copy wins over the denied copy.
3. **Location source:** `LocationManager`, GPS then network, first one enabled. The reference image has `gps` and Play-services `fused` providers and no `network` provider; docs/03 §7 asks for Fused only with a clear benefit, and one fix gives none. Recorded in the ticket.
4. **A location read gives up after `LOCATION_TIMEOUT_MILLIS = 8_000`** and reports `Unavailable` (docs/02 §11, Retry and Back), so Loading never waits on a fix that does not come.
5. **`FakeLocationProvider` moves to test sources** (`result` becomes a `var` there): the app now reads `AndroidLocationProvider`. On the emulator, location comes from `adb emu geo fix` and must be switched on for the driver user (u10).
6. **No "open Settings" path.** After a permanent denial Android answers Grant with "denied" at once; the denied message stays, without a crash. The spec does not ask for a Settings link.
7. **Permission message icon:** the blue location pin from 05a/05b, replacing DN-M0-004's grey location-off placeholder.
8. **`MessageState`'s primary action is generalised** to `onPrimary` + `primaryLabel` (Try Again or Grant Permission); `RecommendationsScreen` takes `onGrant` and carries `@Suppress("LongParameterList")` (state plus one lambda per user action).

> **Changed during execution:** on the emulator an approximate-only grant never got a fix — the platform rewrites a coarse app's request as low-power (`Request[@+10m LOW_POWER]`), which GPS never serves. `AndroidLocationProvider` now answers approximate-only reads from the platform's recent fix (≤ 10 min, already coarsened) at once, or `Unavailable`; precise reads wait up to 8 s for a current fix and fall back to the recent one. The timeout is no longer a constructor parameter. Permission messages are built inline from the state; `CLAUDE.md`'s rule now names `RequestMultiplePermissions()` (docs/03 §7 still says `RequestPermission()` — flagged for a doc fix). The task steps below are the plan as first executed.

## Review Focus

1. **The restrictions change while the permission message shows:** Grant appears in Park and disappears in Drive without leaving the screen. Pinned by `PermissionGrantTest.grantAppearsOnlyWhileRestrictionsAllow` (fake `DrivingRestrictions`).
2. **Denied, then the car moves:** the "park first" copy replaces the denied copy and Grant disappears. Pinned by `RecommendationsScreenTest.restrictedCopyWinsOverDenied`.
3. **An approximate-only grant:** discovery still finds places. Pinned by `AndroidLocationProviderTest.approximateOnlyStillReadsTheLocation` and emulator Step 5.
4. **Location on but no fix:** `Unavailable` at `LOCATION_TIMEOUT_MILLIS`, not an endless Loading. Pinned by `AndroidLocationProviderTest.noFixInTimeIsUnavailable`.
5. **A grant after a denial resumes discovery for the same category** without leaving the screen. Pinned by `RecommendationsViewModelTest.deniedPermissionSaysSoAndGrantResumesDiscovery` and emulator Scenario N.

---

## Investigation findings (2026-10-04)

- Emulator (`AAOS_AOSP_33_userdebug`): current user 10; location setting off for u0 and u10; providers `passive`, `fused` (Play services) and `gps`, no `network`. `adb shell cmd location set-location-enabled true|false --user 10`; `adb emu geo fix <lng> <lat>` feeds GPS.
- core 1.19.1: `LocationManagerCompat.getCurrentLocation(LocationManager, String, android.os.CancellationSignal?, Executor, androidx.core.util.Consumer<Location>)`, `isLocationEnabled`, `hasProvider`.
- Robolectric spike (deleted): with a recent `simulateLocation` the callback fires at once with the fix; with location off it fires at once with `null`; with location on and no fix it does not fire until a later fix — so the provider needs its own timeout, and tests drive the main looper and virtual time.
- Canvas: permission artboards use `ic_location` (the pin) in `#6FA8F5`, title "Location permission required"; driving body "Park the vehicle to allow Discover Nearby to access your location." with Back only; parked body "Discover Nearby needs your location to find places around you." with "Grant Permission" (blue) and Back. Denied copy (docs/02 §10, no artboard): "Discover Nearby can't find places without location access." with Grant and Back. Location unavailable (06) already matches DN-M0-004's rendering.
- The manifest declares both permissions (DN-M0-001; `ManifestContractTest`).
- Today the nav tests rely on `FakeLocationProvider` in `AppContainer`; with `AndroidLocationProvider` they need a granted permission and a simulated fix.

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `…/location/AndroidLocationProvider.kt` | Create | `LOCATION_PERMISSIONS`, `LOCATION_TIMEOUT_MILLIS`, the provider |
| `…/location/fake/FakeLocationProvider.kt` | Move to `…/test/…/location/fake/` | Test fake; `result` becomes `var` |
| `…/ui/screens/RecommendationsViewModel.kt` | Modify | `PermissionRequired(canRequest, denied)`, `onPermissionResult` |
| `…/ui/components/MessageState.kt` | Modify | `onPrimary` + `primaryLabel` |
| `…/ui/screens/RecommendationsScreen.kt` | Modify | Permission messages, Grant, `onGrant` |
| `res/values/strings.xml` | Modify | `grant_permission`, `permission_body_denied` |
| `…/AppContainer.kt`, `…/ui/DiscoverNavHost.kt` | Modify | `AndroidLocationProvider`; the permission launcher |
| `…/test/…/location/TestDevice.kt` | Create | Robolectric device: permission, location on/off, a fix |
| `…/test/…/location/AndroidLocationProviderTest.kt` | Create | Permission, fix, approximate, off, timeout |
| `…/test/…/ui/screens/PermissionGrantTest.kt` | Create | ViewModel + screen with a fake `DrivingRestrictions` |
| `…/test/…/ui/screens/RecommendationsViewModelTest.kt`, `RecommendationsScreenTest.kt`, `ui/DiscoverNavigationTest.kt`, `DiscoverApplicationTest.kt` | Modify | Denial and grant; copy; device location; container |

`…` = `app/src/main/java/com/kanyandula/discovernearby`; `…/test/…` = `app/src/test/java/com/kanyandula/discovernearby`.

---

### Task 0: Start the ticket

- [ ] **Step 1:** In the ticket set `status: in_progress`, `branch: dn-m0-006-location-permission`.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-006-location-permission
git add docs/superpowers/plans/2026-10-04-dn-m0-006-location-permission.md
git commit -m "Add DN-M0-006 implementation plan"
```

---

### Task 1: `AndroidLocationProvider`

**Files:**
- Create: `app/src/test/java/com/kanyandula/discovernearby/location/TestDevice.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/location/AndroidLocationProviderTest.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/location/AndroidLocationProvider.kt`

**Interfaces:**
- Consumes: `LocationProvider`, `LocationResult`, `GeoPoint`, `TestLocation` (DN-M0-003).
- Produces: `val LOCATION_PERMISSIONS: Array<String>`; `const val LOCATION_TIMEOUT_MILLIS = 8_000L`; `class AndroidLocationProvider(context: Context, timeoutMillis: Long = LOCATION_TIMEOUT_MILLIS) : LocationProvider`; test helper `fun deviceAt(point: GeoPoint?, permissions: Array<String> = LOCATION_PERMISSIONS, locationOn: Boolean = true)`.

- [ ] **Step 1: Test device helper**

`app/src/test/java/com/kanyandula/discovernearby/location/TestDevice.kt`:

```kotlin
package com.kanyandula.discovernearby.location

import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.kanyandula.discovernearby.model.GeoPoint
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Sets up Robolectric's device: the granted location [permissions], location on or off, and a fresh GPS fix at
 * [point] (none when null).
 */
fun deviceAt(point: GeoPoint?, permissions: Array<String> = LOCATION_PERMISSIONS, locationOn: Boolean = true) {
    val app = RuntimeEnvironment.getApplication()
    shadowOf(app).grantPermissions(*permissions)
    val locationManager = shadowOf(app.getSystemService(LocationManager::class.java))
    locationManager.setLocationEnabled(locationOn)
    locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, locationOn)
    point?.let {
        locationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = it.lat
                longitude = it.lng
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            },
        )
    }
}
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/location/AndroidLocationProviderTest.kt`:

```kotlin
package com.kanyandula.discovernearby.location

import android.Manifest
import android.os.Looper
import com.kanyandula.discovernearby.places.fake.TestLocation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy, getCompleted
@RunWith(RobolectricTestRunner::class)
class AndroidLocationProviderTest {

    private val provider = AndroidLocationProvider(RuntimeEnvironment.getApplication())
    private val greystones = TestLocation.GREYSTONES.point

    /** Starts a read and lets the location callback run; the result is there unless it is still waiting. */
    private fun TestScope.read(): Deferred<LocationResult> = async { provider.currentLocation() }.also {
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    @Test
    fun withoutPermissionItIsPermissionMissing() = runTest {
        deviceAt(greystones, permissions = emptyArray())
        assertEquals(LocationResult.PermissionMissing, read().getCompleted())
    }

    @Test
    fun readsTheCurrentFix() = runTest {
        deviceAt(greystones)
        assertEquals(LocationResult.Available(greystones), read().getCompleted())
    }

    // Android 12+ lets the user grant approximate location only; discovery must still work.
    @Test
    fun approximateOnlyStillReadsTheLocation() = runTest {
        deviceAt(greystones, permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(LocationResult.Available(greystones), read().getCompleted())
    }

    @Test
    fun locationOffIsUnavailable() = runTest {
        deviceAt(greystones, locationOn = false)
        assertEquals(LocationResult.Unavailable, read().getCompleted())
    }

    // docs/02 §11: no fix ends in Location unavailable (Retry, Back), never an endless Loading.
    @Test
    fun noFixInTimeIsUnavailable() = runTest {
        deviceAt(point = null)
        val result = read()
        assertFalse(result.isCompleted)
        advanceTimeBy(LOCATION_TIMEOUT_MILLIS + 1)
        assertEquals(LocationResult.Unavailable, result.getCompleted())
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*AndroidLocationProviderTest' --console=plain -q 2>&1 | grep -E "^e:" | head -3`
Expected: compilation fails, `Unresolved reference 'LOCATION_PERMISSIONS'` / `'AndroidLocationProvider'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/kanyandula/discovernearby/location/AndroidLocationProvider.kt`:

```kotlin
package com.kanyandula.discovernearby.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.kanyandula.discovernearby.model.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Asked for together; either is enough, so an approximate-only grant (Android 12+) still finds places. */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** How long a read waits for a fix before reporting Unavailable (docs/02 §11: Retry and Back, never endless). */
const val LOCATION_TIMEOUT_MILLIS = 8_000L

/**
 * The vehicle's location from LocationManager (docs/03 §7), read only when discovery asks (docs/01 §14). GPS
 * first, network where a car has one; the reference image's Play-services fused provider adds nothing for one fix.
 */
class AndroidLocationProvider(
    private val context: Context,
    private val timeoutMillis: Long = LOCATION_TIMEOUT_MILLIS,
) : LocationProvider {

    private val locationManager = context.getSystemService(LocationManager::class.java)

    override suspend fun currentLocation(): LocationResult {
        val granted = LOCATION_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val provider = PROVIDERS.firstOrNull { locationManager.isProviderEnabled(it) }
        val fix = if (granted && provider != null) withTimeoutOrNull(timeoutMillis) { currentFix(provider) } else null
        return when {
            !granted -> LocationResult.PermissionMissing
            fix == null -> LocationResult.Unavailable
            else -> LocationResult.Available(GeoPoint(fix.latitude, fix.longitude))
        }
    }

    // currentLocation() checks the permission first; revoking it ends the process, so nothing can race it.
    @SuppressLint("MissingPermission")
    private suspend fun currentFix(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val cancel = CancellationSignal()
        continuation.invokeOnCancellation { cancel.cancel() }
        LocationManagerCompat.getCurrentLocation(locationManager, provider, cancel, Runnable::run) {
            continuation.resume(it)
        }
    }

    private companion object {
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*AndroidLocationProviderTest' --console=plain -q && echo pass`
Expected: `pass` (5 tests). Then `./gradlew detekt lintDebug --console=plain -q && echo clean` → `clean` (the `MissingPermission` suppression is the only lint touch).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/location/AndroidLocationProvider.kt \
  app/src/test/java/com/kanyandula/discovernearby/location
git commit -m "Add AndroidLocationProvider with a fix timeout"
```

---

### Task 2: Denial and grant in the ViewModel

**Files:**
- Move: `app/src/main/java/com/kanyandula/discovernearby/location/fake/FakeLocationProvider.kt` → `app/src/test/java/com/kanyandula/discovernearby/location/fake/FakeLocationProvider.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsViewModel.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/RecommendationsViewModelTest.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt`, `app/src/test/java/com/kanyandula/discovernearby/DiscoverApplicationTest.kt`

**Interfaces:**
- Consumes: `AndroidLocationProvider` (Task 1); `RecommendationsViewModel`, `RecommendationsUiState` (DN-M0-004).
- Produces: `RecommendationsUiState.PermissionRequired(canRequest: Boolean, denied: Boolean = false)`; `fun RecommendationsViewModel.onPermissionResult(granted: Boolean)`; `FakeLocationProvider(var result: LocationResult = …)` in test sources; `AppContainer.locationProvider` is an `AndroidLocationProvider`.

- [ ] **Step 1: Move the fake to test sources**

```bash
mkdir -p app/src/test/java/com/kanyandula/discovernearby/location/fake
git mv app/src/main/java/com/kanyandula/discovernearby/location/fake/FakeLocationProvider.kt \
  app/src/test/java/com/kanyandula/discovernearby/location/fake/FakeLocationProvider.kt
```

In the moved file, the constructor parameter becomes `var result: LocationResult = LocationResult.Available(TestLocation.GREYSTONES.point),` and the KDoc becomes `/** Returns [result], which a test may change; the app reads AndroidLocationProvider. */`.

`AppContainer.kt`: replace the import `com.kanyandula.discovernearby.location.fake.FakeLocationProvider` with `com.kanyandula.discovernearby.location.AndroidLocationProvider`; the fakes comment becomes `// ponytail: fake places until the provider (M1, after ADR-001).`; `val locationProvider: LocationProvider = AndroidLocationProvider(context)`.

`DiscoverApplicationTest.kt`: `assertTrue(container.locationProvider is AndroidLocationProvider)` (import `com.kanyandula.discovernearby.location.AndroidLocationProvider` instead of the fake).

- [ ] **Step 2: Write the failing test**

`RecommendationsViewModelTest.kt`, add (imports `com.kanyandula.discovernearby.location.fake.FakeLocationProvider` exists already):

```kotlin
    // docs/02 §10: a refusal shows the denied copy; a later grant resumes discovery for the same category.
    @Test
    fun deniedPermissionSaysSoAndGrantResumesDiscovery() = runTest {
        val location = FakeLocationProvider(LocationResult.PermissionMissing)
        val discover = DiscoverUseCase(places, location, BasicRecommendationEngine())
        val vm = collected(RecommendationsViewModel(COFFEE, discover, restrictions))
        vm.onPermissionResult(granted = false)
        runCurrent()
        assertEquals(PermissionRequired(canRequest = true, denied = true), vm.uiState.value)

        places.reply = { cafes(2) }
        location.result = LocationResult.Available(ORIGIN)
        vm.onPermissionResult(granted = true)
        runCurrent()
        assertEquals(listOf("p0", "p1"), vm.shown)
    }
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsViewModelTest' --console=plain -q 2>&1 | grep -E "^e:" | head -3`
Expected: compilation fails, `Unresolved reference 'onPermissionResult'` / `No parameter with name 'denied'`.

- [ ] **Step 4: Implement**

`RecommendationsViewModel.kt`:

- `data class PermissionRequired(val canRequest: Boolean) : RecommendationsUiState` becomes `data class PermissionRequired(val canRequest: Boolean, val denied: Boolean = false) : RecommendationsUiState`.
- After `private var request: Job? = null` add `private val permissionDenied = MutableStateFlow(false)`.
- `combine(result, drivingRestrictions.state, ::toUiState)` becomes `combine(result, drivingRestrictions.state, permissionDenied, ::toUiState)`.
- After `fun retry() = load()` add:

```kotlin

    /** The answer to the location permission request (fine or coarse counts): a grant resumes discovery. */
    fun onPermissionResult(granted: Boolean) {
        permissionDenied.value = !granted
        if (granted) load()
    }
```

- `toUiState` takes the denial and passes it on:

```kotlin
    private fun toUiState(
        result: DiscoverResult?,
        driving: DrivingState,
        denied: Boolean,
    ): RecommendationsUiState = when (result) {
```

with the permission branch

```kotlin
        DiscoverResult.PermissionRequired -> RecommendationsUiState.PermissionRequired(
            canRequest = !driving.distractionOptimizationRequired,
            denied = denied,
        )
```

- [ ] **Step 5: Run the suite**

Run: `./gradlew :app:testDebugUnitTest --console=plain -q 2>&1 | tail -15`
Expected: `RecommendationsViewModelTest` passes (14 tests); the navigation tests that expect places now fail, because the app reads `AndroidLocationProvider` and Robolectric has no permission or fix yet. Task 4 gives them a device; note the failures and go on.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/java/com/kanyandula/discovernearby app/src/test/java/com/kanyandula/discovernearby
git commit -m "Track a location permission denial and resume on grant"
```

---

### Task 3: Grant and the permission messages

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/components/MessageState.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt` (temporary `onGrant = {}`)
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreenTest.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/PermissionGrantTest.kt`

**Interfaces:**
- Consumes: `PermissionRequired(canRequest, denied)`, `RecommendationsViewModel` (Task 2); `FakeDrivingRestrictions`, `FakeLocationProvider`, `FakePlacesRepository`.
- Produces: `MessageState(message, backLabel, onBack, modifier, onPrimary: (() -> Unit)? = null, @StringRes primaryLabel: Int = R.string.try_again)`; `RecommendationsScreen(category, state, onRetry, onBack, onPlaceSelected, onGrant: () -> Unit, modifier)`.

- [ ] **Step 1: Write the failing tests**

`RecommendationsScreenTest.kt`: add `private var grants = 0`; in `setUp` pass `onGrant = { grants++ }`; replace `permissionCopyFollowsTheRestrictions` with:

```kotlin
    // docs/02 §10: Grant only while restrictions allow it; otherwise ask the user to park.
    @Test
    fun permissionCopyFollowsTheRestrictions() {
        state = PermissionRequired(canRequest = false)
        rule.onNodeWithText("Location permission required").assertIsDisplayed()
        rule.onNodeWithText("Park the vehicle to allow Discover Nearby to access your location.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").assertDoesNotExist()
        state = PermissionRequired(canRequest = true)
        rule.onNodeWithText("Discover Nearby needs your location to find places around you.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").performClick()
        assertEquals(1, grants)
        rule.onNodeWithText("Try Again").assertDoesNotExist()
    }

    @Test
    fun deniedOffersGrantAgain() {
        state = PermissionRequired(canRequest = true, denied = true)
        rule.onNodeWithText("Discover Nearby can't find places without location access.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").assertIsDisplayed()
        rule.onNodeWithText("Back").assertIsDisplayed()
    }

    // While driving the park-first copy wins over the denied copy, and no request can be made.
    @Test
    fun restrictedCopyWinsOverDenied() {
        state = PermissionRequired(canRequest = false, denied = true)
        rule.onNodeWithText("Park the vehicle to allow Discover Nearby to access your location.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").assertDoesNotExist()
    }
```

`app/src/test/java/com/kanyandula/discovernearby/ui/screens/PermissionGrantTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanyandula.discovernearby.car.DrivingState
import com.kanyandula.discovernearby.car.FakeDrivingRestrictions
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// docs/04 §11: Grant hidden while distraction optimization is required, shown when it is not (fake restrictions),
// through the real ViewModel and screen.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class PermissionGrantTest {

    @get:Rule
    val rule = createComposeRule()

    private val restrictions = FakeDrivingRestrictions(
        MutableStateFlow(DrivingState(distractionOptimizationRequired = true, listLimit = null)),
    )
    private var grants = 0

    @Before
    fun setUp() {
        val discover = DiscoverUseCase(
            FakePlacesRepository(),
            FakeLocationProvider(LocationResult.PermissionMissing),
            BasicRecommendationEngine(),
        )
        val viewModel = RecommendationsViewModel(COFFEE, discover, restrictions)
        rule.setContent {
            DiscoverNearbyTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                RecommendationsScreen(
                    category = COFFEE,
                    state = state,
                    onRetry = {},
                    onBack = {},
                    onPlaceSelected = {},
                    onGrant = { grants++ },
                )
            }
        }
    }

    @Test
    fun grantAppearsOnlyWhileRestrictionsAllow() {
        rule.onNodeWithText("Park the vehicle to allow Discover Nearby to access your location.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").assertDoesNotExist()

        restrictions.state.value = DrivingState(distractionOptimizationRequired = false, listLimit = null)
        rule.onNodeWithText("Grant Permission").performClick()
        assertEquals(1, grants)

        restrictions.state.value = DrivingState(distractionOptimizationRequired = true, listLimit = 21)
        rule.onNodeWithText("Grant Permission").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsScreenTest' --tests '*PermissionGrantTest' --console=plain -q 2>&1 | grep -E "^e:" | head -3`
Expected: compilation fails, `No parameter with name 'onGrant'`.

- [ ] **Step 3: Copy**

`strings.xml`, before `</resources>`:

```xml
    <string name="grant_permission">Grant Permission</string>
    <string name="permission_body_denied">Discover Nearby can\'t find places without location access.</string>
```

- [ ] **Step 4: `MessageState` primary action**

`MessageState.kt`: the parameter `onRetry: (() -> Unit)? = null,` becomes

```kotlin
    onPrimary: (() -> Unit)? = null,
    @StringRes primaryLabel: Int = R.string.try_again,
```

the KDoc becomes `/** A centred message with a Back action and, when [onPrimary] is given, a primary action first (docs/02 §9–§13). */`, and the button line becomes

```kotlin
            if (onPrimary != null) MessageButton(primaryLabel, onPrimary, container = Action, content = Color.White)
```

- [ ] **Step 5: The screen**

`RecommendationsScreen.kt`:

- Replace the two permission messages with:

```kotlin
private val PermissionMessage =
    Message(R.drawable.ic_location, Highlight, R.string.permission_title, R.string.permission_body)
private val PermissionRestrictedMessage =
    Message(R.drawable.ic_location, Highlight, R.string.permission_title, R.string.permission_body_restricted)
private val PermissionDeniedMessage =
    Message(R.drawable.ic_location, Highlight, R.string.permission_title, R.string.permission_body_denied)
```

- Above `@Composable fun RecommendationsScreen(` add `@Suppress("LongParameterList") // the state plus one lambda per user action`, and add the parameter `onGrant: () -> Unit,` after `onPlaceSelected`.
- The Error branch passes `onPrimary = onRetry` instead of `onRetry = onRetry`.
- The permission branch (and its ponytail comment) becomes:

```kotlin
            // Grant only while restrictions allow a permission dialog; otherwise ask the user to park (docs/02 §10).
            is RecommendationsUiState.PermissionRequired -> MessageState(
                message = when {
                    !state.canRequest -> PermissionRestrictedMessage
                    state.denied -> PermissionDeniedMessage
                    else -> PermissionMessage
                },
                backLabel = R.string.back,
                onBack = onBack,
                modifier = body,
                onPrimary = onGrant.takeIf { state.canRequest },
                primaryLabel = R.string.grant_permission,
            )
```

`DiscoverNavHost.kt`: in the `RecommendationsScreen(…)` call add `onGrant = {},` after `onPlaceSelected = …` (Task 4 connects the launcher).

- [ ] **Step 6: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsScreenTest' --tests '*PermissionGrantTest' --console=plain -q && echo pass`
Expected: `pass` (RecommendationsScreenTest 11, PermissionGrantTest 1).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res app/src/main/java/com/kanyandula/discovernearby/ui \
  app/src/test/java/com/kanyandula/discovernearby/ui/screens
git commit -m "Offer Grant Permission only while restrictions allow it"
```

---

### Task 4: The permission request, and the device in navigation tests

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNavigationTest.kt`

**Interfaces:**
- Consumes: `LOCATION_PERMISSIONS` (Task 1); `onPermissionResult` (Task 2); `onGrant` (Task 3); `deviceAt` (Task 1).
- Produces: the Recommendations destination's `RequestMultiplePermissions` launcher.

- [ ] **Step 1: Give the navigation tests a device**

`DiscoverNavigationTest.setUp`, before `rule.setContent`:

```kotlin
        deviceAt(TestLocation.GREYSTONES.point) // the app reads the real location since DN-M0-006
```

(imports `com.kanyandula.discovernearby.location.deviceAt`, `com.kanyandula.discovernearby.places.fake.TestLocation`).

- [ ] **Step 2: Run the suite**

Run: `./gradlew :app:testDebugUnitTest --console=plain -q && echo pass`
Expected: `pass` — the places-based navigation tests that failed after Task 2 pass again.

- [ ] **Step 3: The launcher**

`DiscoverNavHost.kt`: imports `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `com.kanyandula.discovernearby.location.LOCATION_PERMISSIONS`. In the Recommendations destination, after `val state by …`:

```kotlin
            // Fine and coarse in one dialog; either answer counts, so an approximate-only grant works too.
            val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                viewModel.onPermissionResult(granted = it.values.any { granted -> granted })
            }
```

and `onGrant = {},` becomes `onGrant = { permissions.launch(LOCATION_PERMISSIONS) },`.

- [ ] **Step 4: Full check**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain -q && echo pass`
Expected: `pass`; lint shows only the 6 pre-existing warnings.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNavigationTest.kt
git commit -m "Request location permission from the Recommendations screen"
```

---

### Task 5: Verify on the AAOS userdebug emulator (Scenarios L, M, N, K, approximate)

Helpers from DN-M0-004/005 live in this session's scratchpad (`tap.sh`, `waitfor.sh`, `launch.sh`, `shot.sh`, `texts.sh`, `pause.sh`; `shot.sh` prefix `m0006-`); every block sets `S=<scratchpad>` first. The permission dialog is the system's: find its buttons with `texts.sh` (expected labels "While using the app", "Only this time", "Don't allow"; record the actual ones).

- [ ] **Step 1: Install; location on at Greystones; Park**

```bash
S=<scratchpad>
sed -i '' 's/m0005-/m0006-/' "$S/shot.sh"
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain | grep -E "Installed on|BUILD"
adb -s emulator-5554 shell cmd location set-location-enabled true --user 10
adb -s emulator-5554 emu geo fix -6.0633 53.1440
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
adb -s emulator-5554 logcat -b crash -c
```

- [ ] **Step 2: Scenario L — first launch in Park (compare `05b-permission-parked.png`)**

```bash
S=<scratchpad>
adb -s emulator-5554 shell pm clear com.kanyandula.discovernearby
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
"$S/tap.sh" Coffee; "$S/waitfor.sh" "Grant Permission" 10 >/dev/null; "$S/pause.sh" 1; "$S/shot.sh" permission-parked
"$S/tap.sh" "Grant Permission"; "$S/pause.sh" 2; "$S/texts.sh" | tr '\n' ' '; echo
```

Then tap the allow button the dialog shows (e.g. `"$S/tap.sh" "While using the app"`), and:

```bash
S=<scratchpad>
"$S/waitfor.sh" ", Greystones" 15 >/dev/null && "$S/texts.sh" | head -4
```

Expected: parked copy with Grant Permission and Back; the system dialog; after allowing, Coffee places at Greystones on the same screen.

- [ ] **Step 3: Scenario M — first launch in Drive (compare `05a-permission-driving.png`)**

```bash
S=<scratchpad>
adb -s emulator-5554 shell pm clear com.kanyandula.discovernearby
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8
nohup adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 40 >/dev/null 2>&1 &
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
"$S/tap.sh" Coffee; "$S/waitfor.sh" "Park the vehicle" 10 >/dev/null; "$S/pause.sh" 1; "$S/shot.sh" permission-driving
"$S/texts.sh" | grep -c "Grant Permission"
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
"$S/waitfor.sh" "Grant Permission" 10 && echo "Grant appeared in Park"
```

Expected: the park-first copy, Back only, count 0 for Grant, no dialog; after Park, Grant appears on the same screen. Grant and allow as in L; Coffee places appear.

- [ ] **Step 4: Scenario N — deny, then grant**

```bash
S=<scratchpad>
adb -s emulator-5554 shell pm clear com.kanyandula.discovernearby
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
"$S/tap.sh" Coffee; "$S/waitfor.sh" "Grant Permission" 10 >/dev/null; "$S/tap.sh" "Grant Permission"; "$S/pause.sh" 2
```

Tap the deny button (e.g. `"$S/tap.sh" "Don’t allow"`; take the exact label from `texts.sh`), then:

```bash
S=<scratchpad>
"$S/waitfor.sh" "can.t find places without location access" 10 >/dev/null && "$S/shot.sh" permission-denied
"$S/tap.sh" "Grant Permission"; "$S/pause.sh" 2; "$S/texts.sh" | tr '\n' ' '; echo
```

Allow in the second dialog; expect Coffee places. Record what the second dialog offers. Crash buffer: `adb -s emulator-5554 logcat -d -b crash | wc -l` → 0.

- [ ] **Step 5: Approximate only, and Scenario K (location off)**

```bash
S=<scratchpad>
adb -s emulator-5554 shell pm clear com.kanyandula.discovernearby
adb -s emulator-5554 shell pm grant com.kanyandula.discovernearby android.permission.ACCESS_COARSE_LOCATION
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
"$S/tap.sh" Coffee; "$S/waitfor.sh" ", Greystones" 15 >/dev/null && echo "approximate grant: places shown"
adb -s emulator-5554 shell cmd location set-location-enabled false --user 10
"$S/tap.sh" Back; "$S/waitfor.sh" Food 5 >/dev/null; "$S/tap.sh" Food
"$S/waitfor.sh" "Location unavailable" 15 >/dev/null && "$S/shot.sh" location-unavailable
adb -s emulator-5554 shell cmd location set-location-enabled true --user 10
"$S/tap.sh" "Try Again"; "$S/waitfor.sh" ", Greystones" 15 >/dev/null && echo "Try Again: places shown"
adb -s emulator-5554 logcat -d -b crash | wc -l
```

Expected: with coarse only, places appear (no `SecurityException`); with location off, "Location unavailable" with Try Again and Back (compare `06-location-unavailable.png`); Try Again after switching location back on shows places. Crash buffer 0. If the coarse-only read throws, rule on it in the ledger (catch `SecurityException` → `Unavailable`) with a test, before going on.

- [ ] **Step 6:** View the `m0006-*` screenshots against 05a, 05b and 06; record differences in the ticket notes.

---

### Task 6: Close out

- [ ] **Step 1:** `CLAUDE.md` "Current state": replace `Next: DN-M0-006 / 011.` with ``Location (DN-M0-006): `AndroidLocationProvider` (GPS/network, 8 s fix timeout), Grant only while restrictions allow it, denied copy, approximate-only grant works; emulator location via `adb emu geo fix` with location on for user 10. Next: DN-M0-011.``; commit.

- [ ] **Step 2:** Push; draft PR; CI `build` passes.

- [ ] **Step 3:** `simplify`; apply; re-run the full check; commit; push; CI.

- [ ] **Step 4:** Ticket completion notes: open decisions (including the Fused finding, docs/03 §7), the dialog's actual labels, emulator evidence for L, M, N, approximate and K, every check, deferred items.

- [ ] **Step 5:** Final whole-branch review by a fresh reviewer (opus); fix Critical/Important test-first.

- [ ] **Step 6:** `pr-description` (ticket ID and acceptance criteria met); `gh pr ready`.

- [ ] **Step 7: After the user merges** — ticket `done`; `NOW.md` (next: DN-M0-011); delete the branch locally and remotely.
