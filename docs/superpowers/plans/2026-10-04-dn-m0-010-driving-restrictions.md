# DN-M0-010 DrivingRestrictions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One platform-free `StateFlow<DrivingState>` (is distraction optimization required, and the list limit) backed by `CarUxRestrictionsManager`, connected only while the app is visible, safe on devices or tests without the Car service, and verified on the emulator in Park, idling and moving Drive.

**Architecture:** `car/DrivingRestrictions.kt` holds the interface, `DrivingState`, the safe fallback, the mirrored `LIMIT_CONTENT` flag and a pure mapping function — no `android.car`. `car/CarDrivingRestrictions.kt` is the only file that imports `android.car`: a `callbackFlow` that connects a `Car`, registers the restrictions listener and disconnects in `awaitClose`, shared with `stateIn(WhileSubscribed)` so the connection lives exactly as long as something collects. `MainActivity` collects while STARTED (and the state is logged), until DN-M0-004's ViewModel becomes the collector.

**Tech Stack:** `android.car` (`compileOnly`, platform `android-37.0`), kotlinx.coroutines `callbackFlow`/`stateIn`, `lifecycle-runtime-ktx` `repeatOnLifecycle` (already on the classpath via `lifecycle-runtime-compose`), Robolectric 4.17, JUnit.

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-010-driving-restrictions.md`; `docs/03-discover-nearby-engineering-implementation-plan.md` §3, §6 (Driving content limits), §7, §18, §20; `docs/04-discover-nearby-test-demo-plan.md` §2 (Simulating Park / Drive); `docs/05-discover-nearby-delivery-plan.md` §2; AOSP "Consume UX restrictions"; NyasaPlayer `CarUxRestrictionsHandler.kt` and `UxFlags.kt`.

## Global Constraints

- `DrivingRestrictions` exposes `StateFlow<DrivingState>`; `DrivingState(distractionOptimizationRequired: Boolean, listLimit: Int?)`, no `android.car` types (ticket AC; docs/03 §6).
- Nothing is named or documented as "parked": the app reports UX restrictions, not the gear (ticket AC; docs/03 §6, Rev 4.1).
- `distractionOptimizationRequired` = `isRequiresDistractionOptimization()`; `listLimit` = `maxCumulativeContentItems` only while `UX_RESTRICTIONS_LIMIT_CONTENT` (32 in platform 37) is active, else `null` (ticket AC). The platform reports `maxCumulativeContentItems` when unrestricted too, as a baseline (NyasaPlayer D36), so the bit, not the value, decides.
- Only `car/CarDrivingRestrictions.kt` imports `android.car` (`ArchitectureRulesTest` enforces it).
- Connect and disconnect cleanly; never crash when the Car service is unavailable; the fallback is recorded (ticket AC).
- Mapping unit-tested without the platform (ticket AC).
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes; never commit on local `main`; no AI attribution.

## Open decisions (recorded here, in the ticket and the PR)

1. **Fallback when restrictions are unknown** (before the Car service answers, without the automotive feature, or if connecting fails): `DrivingState(distractionOptimizationRequired = true, listLimit = null)` — assume restrictions apply, so parked-only actions stay hidden rather than appearing while driving; the list falls back to `DESIRED_RECOMMENDATIONS`. On the reference emulator the real state replaces it on connect.
2. **Connection lifetime:** `stateIn(scope, WhileSubscribed(5 s), fallback)` — connected while collected, disconnected 5 s after the last collector stops (survives rotation-style restarts without reconnecting). App-scoped flow in `AppContainer`, main-thread scope.
3. **Collector until DN-M0-004:** `MainActivity` collects while STARTED so the connection (and logcat evidence) exists now; DN-M0-004's ViewModel becomes the collector and this line can go then.
4. **No `<uses-library android:name="android.car">`:** NyasaPlayer uses the Car API on the same AAOS image without it. Task 3 confirms the class loads on the emulator.
5. **`FakeDrivingRestrictions` arrives with its first consumer** (DN-M0-004 / DN-M0-006): with the interface it is a three-line test class, and nothing here consumes it.
6. **Negative limits from the vehicle HAL are clamped to 0** (NyasaPlayer's trust-boundary rule): `List.take()` throws on a negative count.

## Review Focus

1. **Parked list silently truncated** because `maxCumulativeContentItems` is reported while unrestricted. Pinned by `DrivingStateMappingTest.unrestrictedIgnoresTheBaselineItemCount`.
2. **Crash where `android.car` is absent** (Robolectric, a non-automotive device) or the service is missing. Pinned by `CarDrivingRestrictionsTest.withoutAutomotiveFeatureReportsTheFallback` and `automotiveWithoutCarClassesReportsTheFallback`.
3. **Mirrored flag drifts from the platform.** Pinned by the on-connect `check()` against `CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT` and the emulator run (Task 3).
4. **Connection leaked** (never disconnected). Pinned by `awaitClose` + logcat "disconnected" after leaving the app (Task 3, Step 5).
5. **Idling assumed to be restricted or unrestricted.** Recorded as observed on the emulator (Task 3, Step 4), not assumed in code.

---

## Investigation findings (2026-10-04)

- Platform 37 `android.car.jar`: `UX_RESTRICTIONS_LIMIT_CONTENT = 32`, `UX_RESTRICTIONS_FULLY_RESTRICTED = 511` (the API 33 emulator reports 255 while moving, which includes 32); `CarUxRestrictions.isRequiresDistractionOptimization()`, `getActiveRestrictions()`, `getMaxCumulativeContentItems()`; `CarUxRestrictionsManager.getCurrentCarUxRestrictions()`, `registerListener(OnUxRestrictionsChangedListener)`, `unregisterListener()`; `Car.createCar(Context)` (returns `null` when the service is unavailable), `Car.CAR_UX_RESTRICTION_SERVICE`, `getCarManager(String)`, `disconnect()`.
- NyasaPlayer maps primitives through a pure function, mirrors flag values in `UxFlags` (the jar is `compileOnly`, absent from unit tests) and `check()`s the mirror against the platform on connect; it clamps limits at 0 and only caps while optimization is required. It connects in a ViewModel `init`, disconnects in `onCleared`, and declares no `uses-library`.
- `lifecycle-runtime-compose` 2.11.0 brings `lifecycle-runtime-ktx` (`repeatOnLifecycle`).
- Robolectric has no `FEATURE_AUTOMOTIVE` by default and no `android.car` classes; `ShadowPackageManager.setSystemFeature` can add the feature to exercise the missing-class path.

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `app/src/main/java/com/kanyandula/discovernearby/car/DrivingRestrictions.kt` | Create | Interface, `DrivingState`, fallback, `UxFlags`, `drivingState()` mapping |
| `…/car/CarDrivingRestrictions.kt` | Create | The only `android.car` user |
| `…/AppContainer.kt` | Modify | Takes `Context`; app scope; `drivingRestrictions` |
| `…/DiscoverApplication.kt` | Modify | `AppContainer(this)` |
| `…/ui/MainActivity.kt` | Modify | Collect while STARTED |
| `app/src/test/java/com/kanyandula/discovernearby/car/DrivingStateMappingTest.kt` | Create | Pure mapping |
| `…/car/CarDrivingRestrictionsTest.kt` | Create | Fallback paths under Robolectric |
| `…/DiscoverApplicationTest.kt` | Modify | Container provides `CarDrivingRestrictions` |

---

### Task 0: Start the ticket

- [ ] **Step 1:** In the ticket set `status: in_progress`, `branch: dn-m0-010-driving-restrictions`.

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-010-driving-restrictions
git add docs/superpowers/plans/2026-10-04-dn-m0-010-driving-restrictions.md
git commit -m "Add DN-M0-010 implementation plan"
```

---

### Task 1: The platform-free contract and mapping

**Interfaces:**
- Produces: `data class DrivingState(val distractionOptimizationRequired: Boolean, val listLimit: Int?)`; `interface DrivingRestrictions { val state: StateFlow<DrivingState> }`; `val UNKNOWN_DRIVING_STATE: DrivingState`; `internal object UxFlags { const val LIMIT_CONTENT = 32 }`; `internal fun drivingState(requiresDistractionOptimization: Boolean, activeRestrictions: Int, maxCumulativeContentItems: Int): DrivingState`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/kanyandula/discovernearby/car/DrivingStateMappingTest.kt`:

```kotlin
package com.kanyandula.discovernearby.car

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The platform type is compileOnly (absent here), so the mapping is tested through raw values;
 * CarDrivingRestrictions checks the mirrored flag against the platform when it connects.
 */
class DrivingStateMappingTest {

    private val fullyRestrictedOnApi33 = 255 // what the reference emulator reports while moving

    @Test
    fun unrestrictedIgnoresTheBaselineItemCount() {
        val state = drivingState(false, activeRestrictions = 0, maxCumulativeContentItems = 21)
        assertEquals(DrivingState(false, null), state)
    }

    @Test
    fun limitContentReportsTheLimit() {
        assertEquals(DrivingState(true, 21), drivingState(true, fullyRestrictedOnApi33, maxCumulativeContentItems = 21))
    }

    // NyasaPlayer saw only NO_VIDEO (16) while idling: optimization may be required with no list limit.
    @Test
    fun restrictedWithoutLimitContentHasNoLimit() {
        val state = drivingState(true, activeRestrictions = 16, maxCumulativeContentItems = 21)
        assertEquals(DrivingState(true, null), state)
    }

    // The value crosses a trust boundary (vehicle HAL); List.take() throws on a negative count.
    @Test
    fun negativeLimitIsClampedToZero() {
        assertEquals(DrivingState(true, 0), drivingState(true, UxFlags.LIMIT_CONTENT, maxCumulativeContentItems = -3))
    }

    @Test
    fun limitFollowsTheFlagNotTheOptimizationAnswer() {
        val state = drivingState(false, UxFlags.LIMIT_CONTENT, maxCumulativeContentItems = 10)
        assertEquals(DrivingState(false, 10), state)
    }

    // Unknown restrictions: assume they apply, so parked-only actions stay hidden.
    @Test
    fun unknownStateAssumesRestrictionsApply() {
        assertEquals(DrivingState(true, null), UNKNOWN_DRIVING_STATE)
    }

    @Test
    fun mirroredLimitContentFlagMatchesTheSdkValue() {
        assertEquals(32, UxFlags.LIMIT_CONTENT) // android.car.drivingstate.CarUxRestrictions, platform 37
    }
}
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "^e: |BUILD" | head -3` → compile failure (`Unresolved reference 'DrivingState'`).

- [ ] **Step 3: Implement**

`app/src/main/java/com/kanyandula/discovernearby/car/DrivingRestrictions.kt`:

```kotlin
package com.kanyandula.discovernearby.car

import kotlinx.coroutines.flow.StateFlow

/**
 * The car's UX restrictions as the UI needs them (docs/03 §6). This reports restrictions, not the
 * gear: AOSP tells apps to monitor UX restrictions rather than an absolute driving state.
 */
data class DrivingState(
    val distractionOptimizationRequired: Boolean,
    val listLimit: Int?, // null = no list limit in force
)

interface DrivingRestrictions {
    val state: StateFlow<DrivingState>
}

/** Before the Car service answers, or when it is unavailable: assume restrictions apply. */
val UNKNOWN_DRIVING_STATE = DrivingState(distractionOptimizationRequired = true, listLimit = null)

/**
 * Mirrors android.car.drivingstate.CarUxRestrictions values: android.car.jar is compileOnly and
 * absent from unit tests. CarDrivingRestrictions checks it against the platform on connect.
 */
internal object UxFlags {
    const val LIMIT_CONTENT = 32
}

/**
 * Pure mapping from the platform's raw values. The platform reports maxCumulativeContentItems while
 * unrestricted too (a baseline, not a limit), so the LIMIT_CONTENT bit decides whether it applies.
 * The value comes from the vehicle HAL, so it is clamped at zero.
 */
internal fun drivingState(
    requiresDistractionOptimization: Boolean,
    activeRestrictions: Int,
    maxCumulativeContentItems: Int,
): DrivingState = DrivingState(
    distractionOptimizationRequired = requiresDistractionOptimization,
    listLimit = maxCumulativeContentItems.coerceAtLeast(0)
        .takeIf { activeRestrictions and UxFlags.LIMIT_CONTENT != 0 },
)
```

- [ ] **Step 4:** Run `./gradlew detekt testDebugUnitTest --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|^e: |FAILED|BUILD"` → `BUILD SUCCESSFUL`; `DrivingStateMappingTest` 7/7. Wrap any test line detekt reports over 120 characters onto named-argument lines.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/car app/src/test/java/com/kanyandula/discovernearby/car
git commit -m "Add DrivingRestrictions, DrivingState and the restriction mapping

Platform-free contract (docs/03 §6): the list limit applies only while
LIMIT_CONTENT is active, since the platform reports a baseline item
count when unrestricted. Unknown restrictions are assumed to apply."
```

---

### Task 2: `CarDrivingRestrictions`, wiring and the collector

**Interfaces:**
- Consumes: Task 1 contract.
- Produces: `class CarDrivingRestrictions(context: Context, scope: CoroutineScope) : DrivingRestrictions`; `class AppContainer(context: Context)` with `val drivingRestrictions: DrivingRestrictions`; `MainActivity` collecting while STARTED.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kanyandula/discovernearby/car/CarDrivingRestrictionsTest.kt`:

```kotlin
package com.kanyandula.discovernearby.car

import android.content.pm.PackageManager
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CarDrivingRestrictionsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() = scope.cancel()

    /** Collect, let the main looper run the upstream, and return what the flow settled on. */
    private fun settledState(): DrivingState {
        val restrictions = CarDrivingRestrictions(app, scope)
        scope.launch { restrictions.state.collect {} }
        shadowOf(Looper.getMainLooper()).idle()
        return restrictions.state.value
    }

    @Test
    fun withoutAutomotiveFeatureReportsTheFallback() {
        assertEquals(UNKNOWN_DRIVING_STATE, settledState())
    }

    // Robolectric has no android.car classes: touching Car must not crash the app.
    @Test
    fun automotiveWithoutCarClassesReportsTheFallback() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true)
        assertEquals(UNKNOWN_DRIVING_STATE, settledState())
    }
}
```

Add to `DiscoverApplicationTest.containerProvidesTheM0Fakes` (rename it `containerProvidesItsDependencies`):

```kotlin
        assertTrue(container.drivingRestrictions is CarDrivingRestrictions)
```

with import `com.kanyandula.discovernearby.car.CarDrivingRestrictions`.

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "^e: |BUILD" | head -3` → compile failure (`Unresolved reference 'CarDrivingRestrictions'`).

- [ ] **Step 3: Implement**

`app/src/main/java/com/kanyandula/discovernearby/car/CarDrivingRestrictions.kt`:

```kotlin
package com.kanyandula.discovernearby.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * [DrivingRestrictions] from CarUxRestrictionsManager (NyasaPlayer's CarUxRestrictionsHandler pattern).
 * The only file that imports android.car. Connected while [state] is collected; disconnected
 * [STOP_TIMEOUT_MILLIS] after the last collector stops.
 */
class CarDrivingRestrictions(context: Context, scope: CoroutineScope) : DrivingRestrictions {

    private val appContext = context.applicationContext

    override val state: StateFlow<DrivingState> = callbackFlow {
        val car = connectOrNull()
        val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        if (car == null || manager == null) {
            Log.w(TAG, "UX restrictions unavailable; assuming restrictions apply")
            send(UNKNOWN_DRIVING_STATE)
            awaitClose { car?.disconnect() }
            return@callbackFlow
        }
        check(UxFlags.LIMIT_CONTENT == CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT) { "LIMIT_CONTENT drift" }
        Log.i(TAG, "connected")
        trySend(manager.currentCarUxRestrictions.toDrivingState())
        manager.registerListener { trySend(it.toDrivingState()) }
        awaitClose {
            manager.unregisterListener()
            car.disconnect()
            Log.i(TAG, "disconnected")
        }
    }
        // Anything the Car stack throws (including a missing class off-device) becomes the fallback.
        .catch { e ->
            Log.w(TAG, "UX restrictions failed; assuming restrictions apply", e)
            emit(UNKNOWN_DRIVING_STATE)
        }
        .onEach { Log.i(TAG, "UX restrictions: $it") }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UNKNOWN_DRIVING_STATE)

    /** Null off-device or when the Car service is unavailable (Car.createCar returns null then). */
    private fun connectOrNull(): Car? =
        if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Car.createCar(appContext)
        } else {
            null
        }

    private fun CarUxRestrictions.toDrivingState() =
        drivingState(isRequiresDistractionOptimization, activeRestrictions, maxCumulativeContentItems)

    private companion object {
        const val TAG = "DrivingRestrictions"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
```

Replace `AppContainer.kt` with:

```kotlin
package com.kanyandula.discovernearby

import android.content.Context
import com.kanyandula.discovernearby.car.CarDrivingRestrictions
import com.kanyandula.discovernearby.car.DrivingRestrictions
import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The single wiring point (docs/03 §3): every app-scoped dependency is constructed here by hand.
 */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ponytail: fakes until the provider (M1, after ADR-001) and AndroidLocationProvider (DN-M0-006).
    val placesRepository: PlacesRepository = FakePlacesRepository()
    val locationProvider: LocationProvider = FakeLocationProvider()
    val drivingRestrictions: DrivingRestrictions = CarDrivingRestrictions(context, appScope)
}
```

In `DiscoverApplication.kt` change `container = AppContainer()` to `container = AppContainer(this)`.

In `ui/MainActivity.kt`, add after `super.onCreate(savedInstanceState)`:

```kotlin
        // ponytail: keeps the restrictions connection open while visible (and logged) until
        // DN-M0-004's ViewModel collects DrivingRestrictions; remove this then.
        val restrictions = (application as DiscoverApplication).container.drivingRestrictions
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { restrictions.state.collect {} }
        }
```

with imports `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.lifecycleScope`, `androidx.lifecycle.repeatOnLifecycle`, `com.kanyandula.discovernearby.DiscoverApplication`, `kotlinx.coroutines.launch`.

- [ ] **Step 4: Run everything**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|Error:|^e: |^w: |FAILED|BUILD"` → `BUILD SUCCESSFUL`; `CarDrivingRestrictionsTest` 2/2; `DiscoverApplicationTest` 2/2; `ArchitectureRulesTest` 3/3 (only `CarDrivingRestrictions.kt` imports `android.car`).

If `automotiveWithoutCarClassesReportsTheFallback` fails with `NoClassDefFoundError` escaping the flow (thrown while loading `CarDrivingRestrictions` rather than inside the upstream), move the `android.car` calls into a private nested object only referenced inside `callbackFlow`, rerun, and record the ruling. If detekt flags the `check()` line length, wrap it.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Read UX restrictions from CarUxRestrictionsManager

CarDrivingRestrictions connects while its state is collected and
disconnects five seconds after the last collector stops. Without the
automotive feature or the Car service, or on any Car stack failure, it
reports the restrictions-apply fallback instead of crashing.
MainActivity collects while started until DN-M0-004's ViewModel does."
```

---

### Task 3: Verify on the AAOS userdebug emulator

- [ ] **Step 1: Emulator and install**

```bash
adb devices                                   # expect emulator-5554; start AAOS_AOSP_33_userdebug with -port 5554 if absent
adb -s emulator-5554 shell getprop ro.build.type    # userdebug
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain | grep -E "Installed on|BUILD"
adb -s emulator-5554 logcat -c
```

- [ ] **Step 2: Park**

```bash
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
adb -s emulator-5554 shell am start -S -n com.kanyandula.discovernearby/.ui.MainActivity
sleep 15   # cold start on the emulator debug build is slow
adb -s emulator-5554 logcat -d -s DrivingRestrictions | tail -5
adb -s emulator-5554 shell dumpsys car_service --services CarUxRestrictionsManagerService | grep '^Port:'
```

Expected: `connected`, then `UX restrictions: DrivingState(distractionOptimizationRequired=false, listLimit=null)`; dumpsys `DO: false UxR: 0`. No `NoClassDefFoundError` (the class loads without `uses-library`).

- [ ] **Step 3: Drive, moving**

```bash
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8
adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60 &
sleep 6
adb -s emulator-5554 logcat -d -s DrivingRestrictions | tail -3
adb -s emulator-5554 shell dumpsys car_service --services CarUxRestrictionsManagerService | grep '^Port:'
```

Expected: `DrivingState(distractionOptimizationRequired=true, listLimit=<N>)` with `N` the platform's limit; dumpsys `DO: true UxR: 255`. Record `N`.

- [ ] **Step 4: Idling (record, do not assume)**

After the speed events end (≈60 s), with the gear still in Drive:

```bash
sleep 60
adb -s emulator-5554 shell dumpsys car_service --services CarDrivingStateService | grep "Current Driving State"
adb -s emulator-5554 logcat -d -s DrivingRestrictions | tail -2
adb -s emulator-5554 shell dumpsys car_service --services CarUxRestrictionsManagerService | grep '^Port:'
```

Record the driving state, `DrivingState` and `UxR` exactly as observed.

- [ ] **Step 5: Back to Park, then leave the app**

```bash
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
sleep 2
adb -s emulator-5554 shell input keyevent KEYCODE_HOME
sleep 7
adb -s emulator-5554 logcat -d -s DrivingRestrictions | tail -3
```

Expected: `DrivingState(false, null)` after Park; `disconnected` about 5 s after Home.

---

### Task 4: Close out

- [ ] **Step 1:** Update `~/.claude/projects/Discover Nearby/tickets/DN-M0-004-recommendations-flow.md`: the ViewModel takes `container.drivingRestrictions`, and a `FakeDrivingRestrictions` (`MutableStateFlow<DrivingState>`) arrives in test sources with it; remove the `MainActivity` collector once the ViewModel collects.

- [ ] **Step 2:** `CLAUDE.md` "Current state": replace `Next: DN-M0-004 / 010 / 011.` with `UX restrictions via \`CarDrivingRestrictions\` (DN-M0-010; unknown = restrictions apply). Next: DN-M0-004 / 011.`; commit.

- [ ] **Step 3:** Push; draft PR; CI `build` passes.

- [ ] **Step 4:** `simplify`; apply; re-run; commit; push; CI.

- [ ] **Step 5:** Ticket completion notes: open decisions, emulator evidence (Park, moving Drive with `N`, idling as observed, disconnect), every check with its result.

- [ ] **Step 6:** Final whole-branch review by a fresh reviewer; fix Critical/Important test-first.

- [ ] **Step 7:** `pr-description`; `gh pr ready`.

- [ ] **Step 8: After the user merges** — ticket `done`; DN-M0-004 `ready` (its dependencies DN-M0-002, DN-M0-003 and DN-M0-010 are done); `NOW.md`; delete the branch.
