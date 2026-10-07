# DN-M1-002 Live Nearby Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app searches live HERE data after a category is chosen. Coffee is the first live category, verified on
the emulator at the three recorded test locations. Tests and debug scenarios stay on the fakes.

**Architecture:**
- **Choosing the source:** `AppContainer` takes the HERE key and picks the places source on first use:
  - HERE when a key is set;
  - the fakes without one (CI) and when a debug launch names a `scenario`.
- **Robolectric:** it runs a `TestDiscoverApplication`, set in `robolectric.properties`, whose container gets no key.
  So local test runs never reach the network, whatever `local.properties` holds.
- **Fixes:** the DN-M1-001 review follow-ups that affect live Coffee results go in here (primary kind; explicit
  nulls).
- **Unchanged:** the use case, the ViewModel and the UI already meet the ticket's state and limit rules (DN-M0-004),
  and stay as they are.

**Tech Stack:** Kotlin, Robolectric 4.17 (`robolectric.properties`), OkHttp (DN-M1-001), the AAOS emulator.

**Spec:**
- the ticket `~/.claude/projects/Discover Nearby/tickets/DN-M1-002-live-nearby-discovery.md` and its notes;
- docs/03 §M1 ("selected provider replaces `FakePlacesRepository`… one category… live POI data"), §15, §16;
- ADR-001: Decision, the Test matrix verdicts, and Field availability (travel time is unavailable, so distance only).

## Global Constraints

- **Ticket AC: the live search.**
  - A live search runs after category selection, once location permission is available.
  - The first live category is chosen from ADR-001's data fit, and recorded.
- **Ticket AC: the list and its states.**
  - The list shows distance. HERE has no travel time (ADR-001 Field availability).
  - At most min(5, driving list limit) rows; unknown fields omitted; no padding.
  - Loading ends on content, empty, a location, network or provider error, or the timeout.
  - A new request cancels the old one, and stale responses are dropped.
- **Recommendation for review: all six categories go live.** docs/03 §M1 says "selected provider replaces
  `FakePlacesRepository`". Coffee is the first live category: verified here and recorded in ADR-001. The other five
  run live but are accepted in M2 (DN-M2-001/002).
- **Keys:** `BuildConfig.HERE_API_KEY` is never logged. No test may reach live HERE.
- **Public repo:** no provider content (place names, IDs, coordinates of results) goes into commits or tracked docs.
  Emulator results are recorded as counts and states.
- **Logging:** no logging of raw coordinates. `adb emu geo fix` uses the recorded test locations.
- **Emulator:** always `adb -s emulator-5554`. Never `adb reboot`. No `uiautomator`.
- **Checks:** `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`, plus the import-order check.
- **Attribution:** none, in commits or the PR.

## Review Focus

1. **A developer runs the unit tests with a real key in `local.properties`.** Expect the fakes, with no network
   call. Pinned by `AppContainerTest.robolectricTestsUseTheFakes` (Task 1).
2. **A debug launch with `--es scenario SLOW` on a build with a key** (the docs/04 timeout and Park/Drive scenarios).
   Expect the fakes and the timeout, not live data. Pinned by
   `AppContainerTest.aDebugScenarioServesTheFakesEvenWithAKey` (Task 1), and the emulator check in Task 3.
3. **A HERE item whose primary category we don't search** (a petrol forecourt with a coffee counter). Expect it
   listed as a secondary match, not ranked like a café. Pinned by
   `HereMapperTest.aPrimaryCategoryWeDoNotSearchLeavesNoPrimaryKind` (Task 2).
4. **HERE sends an explicit `null` for a list field.** Expect that item to map and the response to succeed. Pinned by
   `HerePlacesRepositoryTest.explicitNullsDoNotFailTheResponse` (Task 2).
5. **The network drops between taps.** Expect the network error with Try Again, then results once it's back. Pinned by
   the airplane-mode check in Task 3.

---

## File structure

| File | Change | Responsibility |
| --- | --- | --- |
| `app/src/main/.../AppContainer.kt` | Modify | the key parameter; the places source chosen on first use; a lazy use case |
| `app/src/main/.../DiscoverApplication.kt` | Modify | `open`; `createContainer()` passes `BuildConfig.HERE_API_KEY` |
| `app/src/test/.../TestDiscoverApplication.kt` | Create | Robolectric's application: container without a key |
| `app/src/test/resources/robolectric.properties` | Create | `application=…TestDiscoverApplication` |
| `app/src/test/.../AppContainerTest.kt` | Create | source selection |
| `app/src/main/.../places/here/HereMapper.kt` | Modify | primary kind only from HERE's own primary |
| `app/src/main/.../places/here/HerePlacesRepository.kt` | Modify | `coerceInputValues = true` |
| `app/src/test/.../places/here/HereMapperTest.kt`, `HerePlacesRepositoryTest.kt`, `HereKeyTest.kt` | Modify | tests for the above; the key test can't print the key |
| `CLAUDE.md`, `docs/03-…` (§3 tree), `docs/adr/0001-poi-provider.md` | Modify | state; tree; first live category |

`…` is `java/com/kanyandula/discovernearby`.

---

### Task 1: Live data in the app, fakes in tests and scenarios

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/DiscoverApplication.kt`
- Create: `app/src/test/java/com/kanyandula/discovernearby/TestDiscoverApplication.kt`
- Create: `app/src/test/resources/robolectric.properties`
- Test: `app/src/test/java/com/kanyandula/discovernearby/AppContainerTest.kt`

**Interfaces:**
- Consumes:
  - `HerePlacesRepository(apiKey: String, client: OkHttpClient = OkHttpClient())` (DN-M1-001);
  - `BuildConfig.HERE_API_KEY`.
- Produces:
  - `class AppContainer(context: Context, hereApiKey: String)`, with `placesRepository` and `discoverUseCase` now
    `by lazy`;
  - `open class DiscoverApplication` with `protected open fun createContainer(): AppContainer`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kanyandula/discovernearby/AppContainerTest.kt`:

```kotlin
package com.kanyandula.discovernearby

import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import com.kanyandula.discovernearby.places.here.HerePlacesRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {

    private val app = RuntimeEnvironment.getApplication()

    // DN-M1-002: live HERE data whenever a key is configured (ADR-001, provisionally selected).
    @Test
    fun aKeyMakesTheDataLive() {
        assertTrue(AppContainer(app, hereApiKey = "k").placesRepository is HerePlacesRepository)
    }

    @Test
    fun noKeyServesTheFakes() {
        assertTrue(AppContainer(app, hereApiKey = "").placesRepository is FakePlacesRepository)
    }

    // Review Focus 2: the docs/04 scenarios (timeout, Park/Drive) need the fakes, key or not.
    @Test
    fun aDebugScenarioServesTheFakesEvenWithAKey() {
        val container = AppContainer(app, hereApiKey = "k").apply { useFakeScenario("SLOW") }
        assertTrue(container.placesRepository is FakePlacesRepository)
    }

    // Review Focus 1: Robolectric runs TestDiscoverApplication, so no test reaches HERE, whatever the local key.
    @Test
    fun robolectricTestsUseTheFakes() {
        assertTrue(app is TestDiscoverApplication)
        assertTrue((app as DiscoverApplication).container.placesRepository is FakePlacesRepository)
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppContainerTest' --console=plain`
Expected: compilation FAILS. `AppContainer` has no `hereApiKey` parameter, and `TestDiscoverApplication` is
unresolved.

- [ ] **Step 3: Choose the source in AppContainer**

In `AppContainer.kt`, add the import (sorted after `places.fake.FakeScenario`):

```kotlin
import com.kanyandula.discovernearby.places.here.HerePlacesRepository
```

Replace:

```kotlin
class AppContainer(context: Context) {
```

with:

```kotlin
class AppContainer(context: Context, private val hereApiKey: String) {
```

Replace:

```kotlin
    // ponytail: fake places until the provider (M1, after ADR-001).
    private val fakePlaces = FakePlacesRepository()
    val placesRepository: PlacesRepository = fakePlaces
```

with:

```kotlin
    private val fakePlaces = FakePlacesRepository()
    private var fakesRequested = false

    /**
     * Live HERE data when a key is configured (DN-M1-002; ADR-001: provisionally selected). The fakes when there is
     * no key (CI, Robolectric) or when a debug launch names a scenario. Chosen on first use, after MainActivity has
     * applied any scenario: `am start -S` starts a fresh process for each scenario run.
     */
    val placesRepository: PlacesRepository by lazy {
        if (hereApiKey.isBlank() || fakesRequested) fakePlaces else HerePlacesRepository(hereApiKey)
    }
```

Replace:

```kotlin
    val discoverUseCase = DiscoverUseCase(placesRepository, locationProvider, BasicRecommendationEngine())
```

with:

```kotlin
    val discoverUseCase by lazy { DiscoverUseCase(placesRepository, locationProvider, BasicRecommendationEngine()) }
```

In `useFakeScenario`, replace:

```kotlin
            fakePlaces.scenario = FakeScenario.valueOf(name)
```

with:

```kotlin
            fakePlaces.scenario = FakeScenario.valueOf(name)
            fakesRequested = true
```

- [ ] **Step 4: The application and Robolectric's**

Replace the body of `DiscoverApplication.kt` after the imports with:

```kotlin
open class DiscoverApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    /** The wiring point (docs/03 §3) with the dev-only HERE key; Robolectric's application builds it keyless. */
    protected open fun createContainer() = AppContainer(this, BuildConfig.HERE_API_KEY)
}
```

Create `app/src/test/java/com/kanyandula/discovernearby/TestDiscoverApplication.kt`:

```kotlin
package com.kanyandula.discovernearby

/**
 * Robolectric's application (src/test/resources/robolectric.properties): a container without a key, so every test
 * serves the fakes and none reaches live HERE, whatever local.properties holds. Matches CI, which has no key.
 */
class TestDiscoverApplication : DiscoverApplication() {
    override fun createContainer() = AppContainer(this, hereApiKey = "")
}
```

Create `app/src/test/resources/robolectric.properties`:

```properties
application=com.kanyandula.discovernearby.TestDiscoverApplication
```

- [ ] **Step 5: Run the tests and see them pass, then the whole suite**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppContainerTest' --tests '*DiscoverApplicationTest' --tests '*ScenarioExtraTest' --console=plain`
Expected: PASS.

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: PASS, 204 tests (200 + 4). The fake-data UI tests, such as `DiscoverNavigationTest`, are unchanged and
still green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt \
  app/src/main/java/com/kanyandula/discovernearby/DiscoverApplication.kt \
  app/src/test/java/com/kanyandula/discovernearby/TestDiscoverApplication.kt \
  app/src/test/resources/robolectric.properties \
  app/src/test/java/com/kanyandula/discovernearby/AppContainerTest.kt
git commit -m "Serve live HERE places when a key is set; fakes in tests"
```

---

### Task 2: HERE follow-ups that touch live Coffee results

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/places/here/HereMapper.kt` (`primaryKind`)
- Modify: `app/src/main/java/com/kanyandula/discovernearby/places/here/HerePlacesRepository.kt` (`Json`)
- Modify: `app/src/test/java/com/kanyandula/discovernearby/places/here/HereMapperTest.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/places/here/HerePlacesRepositoryTest.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/places/here/HereKeyTest.kt`
- Modify: `docs/03-discover-nearby-engineering-implementation-plan.md` (§3 tree under `here/`)

**Interfaces:**
- Consumes: DN-M1-001's `HereItem.toSummary()` and `HerePlacesRepository`.
- Produces: no signature changes.

- [ ] **Step 1: Write the failing tests**

Add to `HereMapperTest` (before the closing brace):

```kotlin
    // Review Focus 3 (DN-M1-001 review): a petrol forecourt (primary, not searched) with a coffee counter is a place
    // that serves coffee, not a café; it must not score as a primary match.
    @Test
    fun aPrimaryCategoryWeDoNotSearchLeavesNoPrimaryKind() {
        val place = checkNotNull(item("""{"id": "x", "title": "A", "position": {"lat": 1.0, "lng": 2.0},
            "categories": [{"id": "700-7600-0116", "primary": true}, {"id": "100-1100-0010"}]}""").toSummary())
        assertEquals(setOf("coffee_shop"), place.placeKinds)
        assertNull(place.primaryKind)
    }
```

Add to `HerePlacesRepositoryTest` (before the closing brace):

```kotlin
    // Review Focus 4 (DN-M1-001 review): an explicit null where a list is expected must not fail the whole response.
    @Test
    fun explicitNullsDoNotFailTheResponse() = runBlocking {
        respond = {
            reply(
                it,
                200,
                """{"items": [{"id": "x", "title": "A", "position": {"lat": 1.0, "lng": 2.0},
                    "categories": null, "openingHours": [{"text": null, "isOpen": null}]}]}""",
            )
        }
        assertEquals(listOf("A"), repository.searchNearby(GREYSTONES, DiscoveryCategory.COFFEE, 1).map { it.name })
    }
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*HereMapperTest' --tests '*HerePlacesRepositoryTest' --console=plain`
Expected:
- `aPrimaryCategoryWeDoNotSearchLeavesNoPrimaryKind` FAILS: expected null, got `coffee_shop`;
- `explicitNullsDoNotFailTheResponse` FAILS with `ProviderFailure`.

- [ ] **Step 3: Fix both**

In `HereMapper.kt`, replace:

```kotlin
    val kinds = categories.mapNotNull { kindFor(it.id) }
```

with:

```kotlin
    val kinds = categories.mapNotNull { kindFor(it.id) }
    val primary = categories.firstOrNull { it.primary }
```

and replace:

```kotlin
        primaryKind = categories.firstOrNull { it.primary }?.let { kindFor(it.id) } ?: kinds.firstOrNull(),
```

with:

```kotlin
        // HERE's own primary decides; without one flagged, the first mapped kind leads.
        primaryKind = if (primary != null) kindFor(primary.id) else kinds.firstOrNull(),
```

In `HerePlacesRepository.kt`, replace:

```kotlin
    private val json = Json { ignoreUnknownKeys = true }
```

with:

```kotlin
    // An explicit null where a list is expected becomes the empty default instead of failing the response.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
```

- [ ] **Step 4: The key test can't print the key**

In `HereKeyTest.kt`, replace `import org.junit.Assert.assertEquals` with `import org.junit.Assert.assertTrue`, and:

```kotlin
        assertEquals(key.trim(), key)
```

with:

```kotlin
        assertTrue("HERE_API_KEY has surrounding whitespace", key == key.trim()) // never print the key itself
```

- [ ] **Step 5: The docs/03 tree**

In `docs/03-discover-nearby-engineering-implementation-plan.md`, replace:

```text
│       ├── <Provider>PlacesRepository
│       └── <Provider>PlaceMapper
```

with:

```text
│       ├── HerePlacesRepository        (/browse, /lookup over OkHttp)
│       ├── HereMapper, HereModels
│       └── HereCategories              (request codes; HERE category → kind)
```

- [ ] **Step 6: Run the tests and see them pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*Here*' --console=plain`
Expected: PASS (HereKey 1, HereCategories 3, HereMapper 10, HerePlacesRepository 13).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/places/here/HereMapper.kt \
  app/src/main/java/com/kanyandula/discovernearby/places/here/HerePlacesRepository.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/here/ \
  docs/03-discover-nearby-engineering-implementation-plan.md
git commit -m "Take the primary kind from HERE's primary; tolerate explicit nulls"
```

---

### Task 3: Live Coffee on the emulator

**Files:** none committed. The results go into the DN-M1-002 ticket (local vault) as counts and states, with no place
names.

**Interfaces:**
- Consumes: Tasks 1–2 installed on the emulator.
- Produces: the emulator evidence Task 4 records.

Setup, once:

```bash
d() { adb -s emulator-5554 shell "$@"; }; P=com.kanyandula.discovernearby
S=/private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain -q
d cmd car_service inject-vhal-event 0x11400400 4                     # Park
d dumpsys package $P | awk '/User 10:/{f=1} f&&/LOCATION/{print} /User 0:/{f=0}' | tr -s ' ' | cut -c 1-70 | head -2
launch() { d am start -S --user 10 --activity-clear-task -n $P/.ui.MainActivity "$@" >/dev/null; sleep 16; }
```

Expected: both location permissions `granted=true`. If not, grant them: `d pm grant --user 10 $P android.permission.ACCESS_FINE_LOCATION`,
and the same for COARSE.

- [ ] **Step 1: Live Coffee at the three recorded locations**

For each location (Greystones `-6.0633 53.1440`, Dublin `-6.2603 53.3498`, Galway `-9.0568 53.2707`; `geo fix` takes
longitude first):

```bash
adb -s emulator-5554 emu geo fix <lng> <lat>; sleep 3
launch; d input tap 196 268; sleep 10                                 # Coffee
adb -s emulator-5554 exec-out screencap -p > $S/m1002-<location>.png
```

Read each screenshot. Expected:
- the Coffee list with 1–5 rows, each with a distance;
- the names are live: the fakes all end in ", Greystones", ", Dublin" or ", Galway";
- no error message.

Record per location: row count, and whether every row has a distance.

- [ ] **Step 2: A live row opens Place Details**

From the Greystones list, tap the first row (`d input tap 512 313`), wait 8 s, and take a screenshot.
Expected: Place Details with the place's name and Navigate. Record whether an opening-hours line appears; the
`/lookup` call is new. DN-M1-003 owns acceptance of details.

- [ ] **Step 3: Rapid switching**

At Greystones, read the Food tile's position from a Discover screenshot, then:

```bash
launch; d input tap 196 268; d input keyevent KEYCODE_BACK; d input tap <food x> <food y>
d input keyevent KEYCODE_BACK; d input tap 196 268; sleep 10
adb -s emulator-5554 exec-out screencap -p > $S/m1002-switch.png
```

Expected: the Coffee screen with café rows, no crash and no Food rows.

- [ ] **Step 4: Network failure and recovery** (Review Focus 5)

```bash
d cmd connectivity airplane-mode enable; sleep 3
launch; d input tap 196 268; sleep 10
adb -s emulator-5554 exec-out screencap -p > $S/m1002-offline.png
d cmd connectivity airplane-mode disable; sleep 10
d input tap 373 550; sleep 10                                         # Try Again
adb -s emulator-5554 exec-out screencap -p > $S/m1002-online.png
```

Expected: offline, the network error message (`docs/design/09-network-error.png`) with Try Again and Back. After
Try Again, the Coffee list. Read Try Again's position from the offline screenshot if (373, 550) misses it.

- [ ] **Step 5: Empty, sparse and the scenario path**

```bash
adb -s emulator-5554 emu geo fix -5.6000 53.3000; sleep 3             # Irish Sea, ~40 km offshore
launch; d input tap 196 268; sleep 10; adb -s emulator-5554 exec-out screencap -p > $S/m1002-empty.png
adb -s emulator-5554 emu geo fix -6.3270 53.0110; sleep 3             # rural Wicklow
launch; d input tap 196 268; sleep 10; adb -s emulator-5554 exec-out screencap -p > $S/m1002-sparse.png
adb -s emulator-5554 emu geo fix -6.0633 53.1440; sleep 3             # back to Greystones
launch --es scenario SLOW; d input tap 196 268; sleep 13
adb -s emulator-5554 exec-out screencap -p > $S/m1002-slow.png
```

Expected:
- **Offshore:** the Empty message (`docs/design/08-empty.png`).
- **Rural:** record the row count. 1–2 rows shown without padding counts as sparse. If it shows more, record that
  the deterministic sparse case stays the fake `--es scenario SPARSE`.
- **SLOW:** "Taking longer than expected" (`docs/design/10-timeout.png`). That proves a scenario serves the fakes
  even with a key (Review Focus 2).

- [ ] **Step 6: Record**

Leave the emulator at Greystones, Park, airplane mode off. Add to the DN-M1-002 ticket's notes, with counts and
states only:
- per location, the row count and whether every row has a distance;
- details;
- rapid switch;
- offline and recovery;
- empty, sparse and SLOW;
- the number of live calls made.

---

### Task 4: Record the first live category; docs; full check

**Files:**
- Modify: `docs/adr/0001-poi-provider.md` (Category mapping → Selected provider)
- Modify: `CLAUDE.md` (HERE client bullet; "Next")

**Interfaces:**
- Consumes: Task 3's results.
- Produces: nothing.

- [ ] **Step 1: ADR-001**

After the "Selected provider" category table, the one whose last row starts `| Explore | 300-3000-0023`, add a
blank line and:

```markdown
**First live category (DN-M1-002): Coffee.** It is HERE's only category rated Good at all three test locations
(Test matrix). It was verified live on the emulator at Greystones, Dublin and Galway on 2026-10-07. The other five
categories also run live from DN-M1-002, but are accepted in M2 (DN-M2-001, DN-M2-002).
```

- [ ] **Step 2: CLAUDE.md**

Replace:

```markdown
- **HERE client (DN-M1-001):** `places/here/HerePlacesRepository` (OkHttp + kotlinx.serialization; `/browse`,
  `/lookup`), key `here.apiKey` → `BuildConfig.HERE_API_KEY`. Not wired yet: `AppContainer` still serves the fakes.
```

with:

```markdown
- **HERE client (DN-M1-001, wired in DN-M1-002):** `places/here/HerePlacesRepository` (OkHttp +
  kotlinx.serialization; `/browse`, `/lookup`), key `here.apiKey` → `BuildConfig.HERE_API_KEY`.
  - **Source:** `AppContainer` serves HERE when the key is set. It serves the fakes without a key (CI) and for
    `--es scenario`.
  - **Tests:** Robolectric runs `TestDiscoverApplication` (`robolectric.properties`), so tests always get the fakes.
  - **Live categories:** Coffee is the first live category (ADR-001); the others are live but are accepted in M2.
```

Replace the paragraph that starts `Next: DN-M1-002 (live nearby discovery) wires the HERE client` (through `is P3.`)
with:

```markdown
Next: DN-M1-003 (live place details: verify `/lookup`; attribution once HERE's brand guidance is read). ADR-001
provisionally selects HERE (Legal sign-off on provider terms pending before production). DN-TD-002 (Gradle/CI
tuning) is P3.
```

- [ ] **Step 3: The full check**

```bash
./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain
for f in $(git ls-files '*.kt'); do
  grep '^import ' "$f" > /tmp/dn-actual
  awk '{k=0} /^import java\./{k=1} /^import javax\./{k=2} /^import kotlin\./{k=3} {print k" "$0}' /tmp/dn-actual \
    | LC_ALL=C sort | cut -d' ' -f2- > /tmp/dn-expected
  diff -q /tmp/dn-actual /tmp/dn-expected > /dev/null || echo "UNSORTED $f"
done
awk 'length > 120 {print FILENAME":"NR}' CLAUDE.md
v=$(awk -F= '$1=="here.apiKey" {sub(/^[^=]*=/, ""); gsub(/[ \t\r]/, ""); print}' local.properties)
git diff main -- . | grep -cF -- "$v"
```

Expected:
- BUILD SUCCESSFUL, with 206 tests;
- no `UNSORTED` lines;
- no CLAUDE.md lines printed;
- `0`.

- [ ] **Step 4: Commit**

```bash
git add docs/adr/0001-poi-provider.md CLAUDE.md
git commit -m "Record Coffee as the first live category"
```

---

## After the tasks

- The final whole-branch review on the most capable model (executing-plans), then `simplify` on the `app/` diff.
- PR with `pr-description`: DN-M1-002, its acceptance criteria, and the emulator results as counts and states.
- Step 6 after merge:
  - verify MERGED in its own call;
  - DN-M1-002 `done`;
  - DN-M1-003 stays `ready`;
  - NOW.md and BACKLOG.md.
