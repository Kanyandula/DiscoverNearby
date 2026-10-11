# DN-UX-006 — App rail, Save and the Saved screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An app rail (Discover, Saved), a Save ↔ Saved toggle on Place Details, and a Saved screen listing saved places
nearest first, kept on the device in Room.

**Architecture:** A new `saved/` package holds Room (entity, DAO, database) behind `SavedPlacesRepository`, with JSON
columns through kotlinx.serialization; `AppContainer` opens separate live and fake databases (in memory under
Robolectric). The rail sits in `DiscoverNearbyApp`, composed after the panel so rotary reaches it after the content;
the selected tab follows the back stack. `SavedViewModel` combines the saved places, one location read and the driving
state; Place Details gains `isSaved`/`toggleSave`.

**Tech Stack:** Kotlin 2.2.10 (AGP 9.2.1 built-in), Compose (BOM 2026.09.00), Navigation Compose 2.10.2, Room 2.8.5
with KSP 2.3.12 (checked on 2026-10-11: generates Kotlin, in-memory round trip passes under Robolectric),
kotlinx.serialization 1.11.0, Robolectric 4.17.

**Spec:** `docs/superpowers/specs/2026-10-11-place-details-and-saved-design.md` (PR B). Ticket: DN-UX-006 (vault).

## Global Constraints

- Only `ui/` imports Compose; only `car/CarDrivingRestrictions` imports `android.car`; `saved/` has neither (CLAUDE.md).
- Room stays in `saved/`; the rest of the app sees `SavedPlacesRepository` (this plan's `ArchitectureRulesTest` rule).
- "No exported schema; a schema change wipes saved places (destructive migration). Acceptable for the POC." (spec)
- "Separate database files for live data and the `--es scenario` fakes, so made-up places never appear in a live list." (spec)
- "Built in `AppContainer`; `TestDiscoverApplication` uses an in-memory database." (spec)
- Rail: "Saved navigates to `SavedRoute` above Discover (single top); Discover pops to `DiscoverRoute`. Neither builds a stack." (spec)
- "Rotary: both tabs carry `focusRing`; the knob reaches them after the screen's content; initial focus stays on the screen's first item. `ReturnFocus` is unchanged." (spec)
- Save "Works while driving (one tap). Focus order Save → Navigate" (spec).
- Saved: "Nearest first from the current location; … without a location (permission refused, no fix), most recently saved first and no distances." "Driving: the existing list limit trims it." (spec)
- Copy: "No saved places yet" / "Tap Save on a place to keep it here." (spec). All user text in strings.xml.
- Every actionable control: 76 dp (`MinTouchTarget`), `focusRing`.
- Public repo: made-up place names only in tests; no keys, coordinates or provider responses in code, docs, commits.
- Tripadvisor calls cost money past 1,000 a month: develop on the fakes (`--es scenario`); keep live checks few.
- Commits: no AI attribution; subject ≤ 72 chars; body wrapped at 72.
- Filtered test runs: `./gradlew :app:testDebugUnitTest --tests '<pattern>'` (`:stub-navigation` has no matching tests).

## Review Focus

1. A saved row that no longer decodes (the summary's shape or a category changed) → that row is skipped; the list and
   the app keep working. Test: `RoomSavedPlacesTest.aRowThatNoLongerDecodesIsSkipped` (Task 1).
2. Saved without a location (permission refused, no fix) → newest first, no distances, no permission prompt. Test:
   `SavedViewModelTest.withoutALocationNewestFirstAndNoDistances` (Task 4).
3. Save tapped twice before the database answers → one row, never a duplicate or a crash. Test:
   `PlaceDetailsViewModelTest.aDoubleTapLeavesOneRow` (Task 3) and `RoomSavedPlacesTest.savingTwiceKeepsOneRow…` (Task 1).
4. A place removed on Details opened from Saved → gone from the list on Back, without a reload. Test:
   `SavedViewModelTest.aPlaceRemovedElsewhereLeavesTheList` (Task 4).
5. Driving limit on Saved → the list is trimmed; a limit of 0 asks to park. Tests:
   `SavedViewModelTest.theDrivingLimitTrimsTheList`, `…aLimitOfZeroAsksToPark` (Task 4).

---

### Task 1: Room storage in `saved/`

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
- Create: `app/src/main/java/com/kanyandula/discovernearby/saved/SavedPlace.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/saved/SavedPlacesDatabase.kt` (entity, DAO, database)
- Create: `app/src/main/java/com/kanyandula/discovernearby/saved/RoomSavedPlaces.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/saved/RoomSavedPlacesTest.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/saved/FakeSavedPlaces.kt` (for Tasks 3–4)
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ArchitectureRulesTest.kt`

**Interfaces:**
- Produces:
  - `data class SavedPlace(val summary: PlaceSummary, val category: DiscoveryCategory, val enrichment: PlaceEnrichment?, val savedAt: Long)`
  - `interface SavedPlacesRepository { fun savedPlaces(): Flow<List<SavedPlace>>; fun isSaved(placeId: String): Flow<Boolean>; suspend fun save(summary: PlaceSummary, category: DiscoveryCategory, enrichment: PlaceEnrichment?); suspend fun remove(placeId: String) }`
  - `fun openSavedPlaces(context: Context, name: String?): SavedPlacesRepository` (null name = in memory)
  - Test fake `FakeSavedPlaces` with `val places: MutableStateFlow<List<SavedPlace>>`, `savedAt` 1, 2, 3 … per save.

- [ ] **Step 1: Add Room and KSP to the build**

`gradle/libs.versions.toml` — under `[versions]` after `robolectric = "4.17"`:

```toml
# KSP runs Room's annotation processor; KSP 2.3+ versions no longer follow Kotlin's.
ksp = "2.3.12"
room = "2.8.5"
```

Under `[libraries]` before `robolectric = …`:

```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

Under `[plugins]`, last:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`build.gradle.kts`: add `alias(libs.plugins.ksp) apply false` after the serialization alias.
`app/build.gradle.kts`: add `alias(libs.plugins.ksp)` to `plugins`; after `implementation(libs.coil.network.okhttp)`:

```kotlin
    // Saved places on the device (DN-UX-006, ADR-004).
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
```

- [ ] **Step 2: Write the architecture rule**

In `ArchitectureRulesTest`, add the regex beside the others and the test after `onlyAppContainerReachesIntoTheHerePackage`:

```kotlin
    private val roomImport = Regex("""^import androidx\.room\.""")
```

```kotlin
    // DN-UX-006, ADR-004: Room stays in saved/; the rest of the app sees SavedPlacesRepository.
    @Test
    fun onlySavedImportsRoom() {
        assertEquals(emptyList<String>(), violations(roomImport) { inPackage(it, "saved") })
    }
```

A guard: it passes now (no Room import anywhere) and fails if Room leaks out of `saved/` later.

- [ ] **Step 3: Write the failing repository tests**

`app/src/test/java/com/kanyandula/discovernearby/saved/RoomSavedPlacesTest.kt`:

```kotlin
package com.kanyandula.discovernearby.saved

import androidx.room.Room
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.PriceLevel
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomSavedPlacesTest {

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SavedPlacesDatabase::class.java)
        .build()
    private var now = 1_000L
    private val saved = RoomSavedPlaces(db.savedPlaceDao(), clock = { now })

    private val cafe = testPlace("cafe-1", "cafe").copy(attribution = "HERE")
    private val park = testPlace("park-1", "park")

    @After
    fun close() = db.close()

    private suspend fun ids() = saved.savedPlaces().first().map { it.summary.id }

    @Test
    fun aSavedPlaceComesBackWithItsCategoryAndEnrichment() = runTest {
        val enrichment = PlaceEnrichment(priceLevel = PriceLevel.MID, description = "A made-up café.")
        saved.save(cafe, COFFEE, enrichment)
        assertEquals(listOf(SavedPlace(cafe, COFFEE, enrichment, savedAt = 1_000L)), saved.savedPlaces().first())
    }

    @Test
    fun newestFirst() = runTest {
        saved.save(cafe, COFFEE, null)
        now = 2_000L
        saved.save(park, OUTDOORS, null)
        assertEquals(listOf("park-1", "cafe-1"), ids())
    }

    // Review Focus 3: saving again keeps one row, now the newest.
    @Test
    fun savingTwiceKeepsOneRowAndMovesItToTheTop() = runTest {
        saved.save(cafe, COFFEE, null)
        now = 2_000L
        saved.save(park, OUTDOORS, null)
        now = 3_000L
        saved.save(cafe, COFFEE, null)
        assertEquals(listOf("cafe-1", "park-1"), ids())
    }

    @Test
    fun removeDropsIt() = runTest {
        saved.save(cafe, COFFEE, null)
        saved.save(park, OUTDOORS, null)
        saved.remove("cafe-1")
        assertEquals(listOf("park-1"), ids())
    }

    @Test
    fun isSavedFollowsSaveAndRemove() = runTest {
        val seen = mutableListOf<Boolean>()
        saved.isSaved("cafe-1").take(3).collectIndexed { index, value ->
            seen += value
            when (index) {
                0 -> saved.save(cafe, COFFEE, null)
                1 -> saved.remove("cafe-1")
            }
        }
        assertEquals(listOf(false, true, false), seen)
    }

    @Test
    fun theListUpdatesLive() = runTest {
        val seen = mutableListOf<List<String>>()
        saved.savedPlaces().take(2).collectIndexed { index, places ->
            seen += places.map { it.summary.id }
            if (index == 0) saved.save(cafe, COFFEE, null)
        }
        assertEquals(listOf(emptyList(), listOf("cafe-1")), seen)
    }

    // Review Focus 1: a row stored by an older build that no longer decodes is skipped, not a crash.
    @Test
    fun aRowThatNoLongerDecodesIsSkipped() = runTest {
        saved.save(cafe, COFFEE, null)
        val dao = db.savedPlaceDao()
        dao.upsert(SavedPlaceEntity("broken", "COFFEE", 1L, summaryJson = """{"id": 1}""", enrichmentJson = null))
        val parkJson = Json.encodeToString(PlaceSummary.serializer(), park)
        dao.upsert(SavedPlaceEntity("park-1", "BOWLING", 2L, summaryJson = parkJson, enrichmentJson = null))
        assertEquals(listOf("cafe-1"), ids())
    }
}
```

`app/src/test/java/com/kanyandula/discovernearby/saved/FakeSavedPlaces.kt`:

```kotlin
package com.kanyandula.discovernearby.saved

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Test stand-in: newest first, like Room; [places] can be read or changed directly; savedAt counts 1, 2, 3 … */
class FakeSavedPlaces : SavedPlacesRepository {
    val places = MutableStateFlow<List<SavedPlace>>(emptyList())
    private var clock = 0L

    override fun savedPlaces(): Flow<List<SavedPlace>> = places

    override fun isSaved(placeId: String): Flow<Boolean> = places.map { list -> list.any { it.summary.id == placeId } }

    override suspend fun save(summary: PlaceSummary, category: DiscoveryCategory, enrichment: PlaceEnrichment?) {
        val place = SavedPlace(summary, category, enrichment, ++clock)
        places.update { list -> listOf(place) + list.filterNot { it.summary.id == summary.id } }
    }

    override suspend fun remove(placeId: String) {
        places.update { list -> list.filterNot { it.summary.id == placeId } }
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RoomSavedPlacesTest*' --tests '*ArchitectureRulesTest*'`
Expected: compilation FAILS (unresolved `SavedPlacesDatabase`, `RoomSavedPlaces`, `SavedPlace`, `SavedPlacesRepository`).

- [ ] **Step 5: Write the storage**

`app/src/main/java/com/kanyandula/discovernearby/saved/SavedPlace.kt`:

```kotlin
package com.kanyandula.discovernearby.saved

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import kotlinx.coroutines.flow.Flow

/** A place the driver saved (DN-UX-006): as Place Details knew it, with the category it was found in. */
data class SavedPlace(
    val summary: PlaceSummary,
    val category: DiscoveryCategory, // for its artwork, as on the row it came from
    val enrichment: PlaceEnrichment?,
    val savedAt: Long, // epoch milliseconds
)

/** Saved places on the device (ADR-004). Flows update as places are saved and removed. */
interface SavedPlacesRepository {
    /** Every saved place, the most recently saved first. */
    fun savedPlaces(): Flow<List<SavedPlace>>

    fun isSaved(placeId: String): Flow<Boolean>

    /** Saves the place, or saves it again as the newest with what is known now. */
    suspend fun save(summary: PlaceSummary, category: DiscoveryCategory, enrichment: PlaceEnrichment?)

    suspend fun remove(placeId: String)
}
```

`app/src/main/java/com/kanyandula/discovernearby/saved/SavedPlacesDatabase.kt`:

```kotlin
package com.kanyandula.discovernearby.saved

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One saved place; the summary and enrichment as JSON, so the table needs no column per field (DN-UX-006). */
@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey val placeId: String,
    val category: String, // DiscoveryCategory.name
    val savedAt: Long,
    val summaryJson: String,
    val enrichmentJson: String?,
)

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_places WHERE placeId = :placeId)")
    fun observeIsSaved(placeId: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(place: SavedPlaceEntity)

    @Query("DELETE FROM saved_places WHERE placeId = :placeId")
    suspend fun delete(placeId: String)
}

// No exported schema: a schema change wipes saved places (destructive migration), which the POC accepts (ADR-004).
@Database(entities = [SavedPlaceEntity::class], version = 1, exportSchema = false)
abstract class SavedPlacesDatabase : RoomDatabase() {
    abstract fun savedPlaceDao(): SavedPlaceDao
}
```

`app/src/main/java/com/kanyandula/discovernearby/saved/RoomSavedPlaces.kt`:

```kotlin
package com.kanyandula.discovernearby.saved

import android.content.Context
import androidx.room.Room
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Opens the saved places in the database file [name], or in memory when [name] is null (Robolectric). */
fun openSavedPlaces(context: Context, name: String?): SavedPlacesRepository {
    val builder = if (name == null) {
        Room.inMemoryDatabaseBuilder(context, SavedPlacesDatabase::class.java)
    } else {
        Room.databaseBuilder(context, SavedPlacesDatabase::class.java, name)
    }
    return RoomSavedPlaces(builder.fallbackToDestructiveMigration(dropAllTables = true).build().savedPlaceDao())
}

/** [SavedPlacesRepository] on Room; [clock] stamps each save. */
class RoomSavedPlaces(
    private val dao: SavedPlaceDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : SavedPlacesRepository {

    override fun savedPlaces(): Flow<List<SavedPlace>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toSavedPlace() } }.distinctUntilChanged()

    override fun isSaved(placeId: String): Flow<Boolean> = dao.observeIsSaved(placeId).distinctUntilChanged()

    override suspend fun save(summary: PlaceSummary, category: DiscoveryCategory, enrichment: PlaceEnrichment?) =
        dao.upsert(
            SavedPlaceEntity(
                placeId = summary.id,
                category = category.name,
                savedAt = clock(),
                summaryJson = json.encodeToString(PlaceSummary.serializer(), summary),
                enrichmentJson = enrichment?.let { json.encodeToString(PlaceEnrichment.serializer(), it) },
            ),
        )

    override suspend fun remove(placeId: String) = dao.delete(placeId)
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * The saved place, or null when a row stored by an older build no longer decodes (a field or a category changed): the
 * list goes on without it. SerializationException is an IllegalArgumentException, as is an unknown category name.
 */
private fun SavedPlaceEntity.toSavedPlace(): SavedPlace? = try {
    SavedPlace(
        summary = json.decodeFromString(PlaceSummary.serializer(), summaryJson),
        category = DiscoveryCategory.valueOf(category),
        enrichment = enrichmentJson?.let { json.decodeFromString(PlaceEnrichment.serializer(), it) },
        savedAt = savedAt,
    )
} catch (ignored: IllegalArgumentException) {
    null
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*RoomSavedPlacesTest*' --tests '*ArchitectureRulesTest*'`
Expected: PASS, 7 + 5 tests.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts \
  app/src/main/java/com/kanyandula/discovernearby/saved app/src/test/java/com/kanyandula/discovernearby/saved \
  app/src/test/java/com/kanyandula/discovernearby/ArchitectureRulesTest.kt
git commit -m "Keep saved places in Room in a new saved/ package"
```

---

### Task 2: Wire the saved places into AppContainer

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/TestDiscoverApplication.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/AppContainerTest.kt`

**Interfaces:**
- Consumes: `openSavedPlaces(context, name)` (Task 1)
- Produces: `AppContainer(context, hereApiKey, tripadvisorApiKey = "", savedPlacesInMemory = false)`;
  `val savedPlaces: SavedPlacesRepository` (the fakes' store whenever the fakes are served)

- [ ] **Step 1: Write the failing test** (AppContainerTest, after the last test; imports `SavedPlace`, `first`)

```kotlin
    // DN-UX-006: made-up scenario places never land in a live Saved list: each has its own database.
    @Test
    fun aScenarioKeepsItsSavedPlacesApartFromLiveOnes() = runTest {
        val container = AppContainer(app, hereApiKey = "k", savedPlacesInMemory = true)
        container.savedPlaces.save(testPlace("live-1", "cafe"), DiscoveryCategory.COFFEE, null)
        container.useFakeScenario("NORMAL")
        assertEquals(emptyList<SavedPlace>(), container.savedPlaces.savedPlaces().first())
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppContainerTest*'`
Expected: compilation FAILS (no `savedPlacesInMemory`, no `savedPlaces`).

- [ ] **Step 3: Implement**

`AppContainer` constructor becomes:

```kotlin
class AppContainer(
    context: Context,
    private val hereApiKey: String,
    private val tripadvisorApiKey: String = "",
    private val savedPlacesInMemory: Boolean = false, // Robolectric (TestDiscoverApplication)
) {
```

After `placeEnricher`:

```kotlin
    private val appContext = context.applicationContext

    // Separate files, so made-up scenario places never show in a live Saved list (DN-UX-006, ADR-004).
    private val liveSaved by lazy { openSavedPlaces(appContext, LIVE_SAVED_DB.takeUnless { savedPlacesInMemory }) }
    private val fakeSaved by lazy { openSavedPlaces(appContext, FAKE_SAVED_DB.takeUnless { savedPlacesInMemory }) }

    /** The driver's saved places (ADR-004): the fakes' own database whenever the fakes are served. */
    val savedPlaces: SavedPlacesRepository
        get() = if (servesFakes) fakeSaved else liveSaved
```

In the companion object:

```kotlin
        const val LIVE_SAVED_DB = "saved-places.db"
        const val FAKE_SAVED_DB = "saved-places-fakes.db"
```

`TestDiscoverApplication.createContainer()` becomes
`AppContainer(this, hereApiKey = "", savedPlacesInMemory = true)`, and its KDoc gains: "Saved places are kept in
memory, so each test starts with none and writes no file."

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppContainerTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt \
  app/src/test/java/com/kanyandula/discovernearby/TestDiscoverApplication.kt \
  app/src/test/java/com/kanyandula/discovernearby/AppContainerTest.kt
git commit -m "Open separate saved-place databases for live data and fakes"
```

---

### Task 3: Save ↔ Saved on Place Details

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModel.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreen.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt`
- Create: `app/src/main/res/drawable/ic_heart.xml`, `app/src/main/res/drawable/ic_heart_filled.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `PlaceDetailsViewModelTest.kt`, `PlaceDetailsScreenTest.kt`, `PlaceDetailsFitTest.kt`, `ui/components/FocusRingTest.kt`

**Interfaces:**
- Consumes: `SavedPlacesRepository`, `FakeSavedPlaces` (Task 1); `AppContainer.savedPlaces` (Task 2)
- Produces: `PlaceDetailsViewModel(place, discover, navigation, saved: SavedPlacesRepository, category: DiscoveryCategory, enrichment: PlaceEnrichment? = null, now = …)`;
  `val isSaved: StateFlow<Boolean>`; `fun toggleSave()`;
  `PlaceDetailsScreen(…, onBack, saved: Boolean, onToggleSave: () -> Unit, modifier, enrichment, drivingRestricted)`;
  strings `save`, `saved`; drawables `ic_heart`, `ic_heart_filled`.

**Ruling (initial focus):** the spec's "initial focus on Navigate" adds no focus request. Nothing requests initial
focus today (the rotary service picks it); Save sits after the content and before Navigate, so it does not change
where the first turn lands. A new `FocusRequester` would be another rotary change next to V7 (ADR-002). Task 7 records
where the first turn lands. Cost if wrong: one `FocusRequester` on Navigate later.

- [ ] **Step 1: Write the failing ViewModel tests**

In `PlaceDetailsViewModelTest`: add `private val saved = FakeSavedPlaces()` and
`private val rowEnrichment = PlaceEnrichment(priceLevel = PriceLevel.MID)`; the factory becomes:

```kotlin
    private fun viewModel() = PlaceDetailsViewModel(
        place = place,
        discover = DiscoverUseCase(places, FakeLocationProvider(), BasicRecommendationEngine()),
        navigation = launcher,
        saved = saved,
        category = DiscoveryCategory.COFFEE,
        enrichment = rowEnrichment,
        now = { monday10am },
    )

    /** [vm] with the screen watching whether the place is saved, its first calls run as far as they can. */
    private fun TestScope.watched(vm: PlaceDetailsViewModel = viewModel()) = vm.also {
        backgroundScope.launch { vm.isSaved.collect {} }
        runCurrent()
    }
```

Tests:

```kotlin
    // DN-UX-006: Save keeps the place as Details knows it (the refreshed summary), with the row's category and enrichment.
    @Test
    fun saveKeepsTheRefreshedPlaceWithItsCategoryAndEnrichment() = runTest {
        places.details = { loaded }
        val vm = watched()
        vm.toggleSave()
        runCurrent()
        assertEquals(listOf(SavedPlace(loaded.summary, DiscoveryCategory.COFFEE, rowEnrichment, 1)), saved.places.value)
        assertEquals(true, vm.isSaved.value)
    }

    @Test
    fun saveAgainRemovesIt() = runTest {
        places.details = { loaded }
        val vm = watched()
        vm.toggleSave()
        runCurrent()
        vm.toggleSave()
        runCurrent()
        assertEquals(emptyList<SavedPlace>(), saved.places.value)
        assertEquals(false, vm.isSaved.value)
    }

    // Review Focus 3: a double tap before the database answers leaves one row.
    @Test
    fun aDoubleTapLeavesOneRow() = runTest {
        places.details = { loaded }
        val vm = watched()
        vm.toggleSave()
        vm.toggleSave()
        runCurrent()
        assertEquals(1, saved.places.value.size)
    }
```

(Imports: `DiscoveryCategory`, `PlaceEnrichment`, `PriceLevel`, `SavedPlace`, `FakeSavedPlaces`, `TestScope`, `launch`.)

- [ ] **Step 2: Write the failing screen tests**

`PlaceDetailsScreenTest`: add `private var isSaved by mutableStateOf(false)` and `private var saves = 0`; pass
`saved = isSaved, onToggleSave = { saves++ },` after `onBack`. Test:

```kotlin
    // DN-UX-006: Save beside Navigate, one tap, also while driving; the label follows the database.
    @Test
    fun saveTogglesAndSaysWhetherThePlaceIsSaved() {
        state = Content(PlaceDetails(place, openingSummary = null))
        restricted = true
        rule.onNodeWithText("Save").performClick()
        assertEquals(1, saves)
        isSaved = true
        rule.onNodeWithText("Saved").assertIsDisplayed()
        rule.onNodeWithText("Save").assertDoesNotExist()
    }
```

`PlaceDetailsFitTest`: `ReferencePanelWidth = 836.dp` with the KDoc "The panel's width on the reference AVD, 928 dp,
less DN-UX-006's rail (76) and its gap (16)."; pass `saved = false, onToggleSave = {},`; after the Navigate checks:

```kotlin
        val save = rule.onNodeWithText("Save")
        save.assertIsDisplayed()
        save.assertHeightIsAtLeast(MinTouchTarget)
        assertTrue(save.getUnclippedBoundsInRoot().right <= navigate.getUnclippedBoundsInRoot().left)
```

`FocusRingTest.focusedNavigateShowsALightRing`: pass `saved = false, onToggleSave = {},`. New test after it:

```kotlin
    // Save is filled with Raised grey: the Accent ring shows on it.
    @Test
    @Config(qualifiers = AUTOMOTIVE_1024P)
    fun focusedSaveShowsTheRing() {
        show {
            PlaceDetailsScreen(
                distanceMeters = 500,
                category = DiscoveryCategory.COFFEE,
                state = PlaceDetailsUiState.SummaryOnly(testPlace("cafe-1", "cafe")),
                onNavigate = {},
                onBack = {},
                saved = false,
                onToggleSave = {},
            )
        }
        val save = rule.onNodeWithText(text(R.string.save))
        save.requestFocus()
        assertArrayEquals(Accent.rgb(), save.edgePixel().rgb(), TOLERANCE)
    }
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaceDetails*' --tests '*FocusRingTest*'`
Expected: compilation FAILS (no `saved`/`category`/`enrichment` parameters, no `isSaved`, `toggleSave`, `R.string.save`).

- [ ] **Step 4: Implement**

Strings (after `navigate`):

```xml
    <string name="save">Save</string>
    <string name="saved">Saved</string>
```

`ic_heart.xml` (outline, the app's stroke style):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M19 14c1.49 -1.46 3 -3.21 3 -5.5a5.5 5.5 0 0 0 -5.5 -5.5c-1.76 0 -3 0.5 -4.5 2c-1.5 -1.5 -2.74 -2 -4.5 -2a5.5 5.5 0 0 0 -5.5 5.5c0 2.3 1.5 4.05 3 5.5l7 7z"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

`ic_heart_filled.xml`: the same path with `android:fillColor="#FFFFFFFF"` in place of the stroke attributes.

`PlaceDetailsViewModel`: constructor

```kotlin
class PlaceDetailsViewModel(
    private val place: PlaceSummary,
    discover: DiscoverUseCase,
    private val navigation: NavigationLauncher,
    private val saved: SavedPlacesRepository,
    private val category: DiscoveryCategory,
    private val enrichment: PlaceEnrichment? = null,
    now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
```

after `uiState`:

```kotlin
    /** Whether the place is saved, live from the database; false until it answers (DN-UX-006). */
    val isSaved: StateFlow<Boolean> = saved.isSaved(place.id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialValue = false)
```

after `navigate()`:

```kotlin
    /**
     * Save ↔ Saved (DN-UX-006): keeps the place as Details knows it now, with the row's category and enrichment. Both
     * writes are idempotent, so a double tap before the database answers leaves one row.
     */
    fun toggleSave() {
        val summary = state.value.summary
        val remove = isSaved.value
        viewModelScope.launch { if (remove) saved.remove(place.id) else saved.save(summary, category, enrichment) }
    }
```

`PlaceDetailsScreen`: add `@Suppress("LongParameterList") // the state plus one lambda per user action`; parameters
`saved: Boolean, onToggleSave: () -> Unit,` after `onBack`; KDoc: "Save's slot (DN-UX-006)" → "Save ↔ Saved
(DN-UX-006)". Replace `Spacer(Modifier.weight(1f)) // Save's slot (DN-UX-006)` with
`SaveButton(saved = saved, onClick = onToggleSave, modifier = Modifier.weight(1f))` and add:

```kotlin
/** Save ↔ Saved (DN-UX-006): Raised grey beside the primary Navigate, so the Accent ring shows on it. */
@Composable
private fun SaveButton(saved: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(NavigateRadius)
    Button(
        onClick = onClick,
        modifier = modifier.focusRing(shape).height(MinTouchTarget),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Raised, contentColor = OnSurface),
    ) {
        // Decorative: the label says the state.
        Icon(
            painter = painterResource(if (saved) R.drawable.ic_heart_filled else R.drawable.ic_heart),
            contentDescription = null,
            modifier = Modifier.size(NavigateIconSize),
        )
        Spacer(modifier = Modifier.width(NavigateIconGap))
        Text(
            text = stringResource(if (saved) R.string.saved else R.string.save),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}
```

`DiscoverNavHost`, the `PlaceDetailsRoute` destination:

```kotlin
            val viewModel = viewModel {
                PlaceDetailsViewModel(
                    place = route.place,
                    discover = container.discoverUseCase,
                    navigation = container.navigationLauncher,
                    saved = container.savedPlaces,
                    category = route.category,
                    enrichment = route.enrichment,
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val saved by viewModel.isSaved.collectAsStateWithLifecycle()
```

and pass `saved = saved, onToggleSave = viewModel::toggleSave,` to `PlaceDetailsScreen`.

- [ ] **Step 5: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaceDetails*' --tests '*FocusRingTest*' --tests '*DiscoverNavigationTest*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Add Save to Place Details, kept in the saved-places database"
```

---

### Task 4: The Saved screen

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/components/RecommendationRow.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreen.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt` (`PlaceDetailsRoute.distanceMeters: Int?`)
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/SavedViewModel.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/SavedScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `ui/screens/SavedViewModelTest.kt`, `ui/screens/SavedScreenTest.kt`, `PlaceDetailsScreenTest.kt`,
  `ui/components/FocusRingTest.kt`

**Interfaces:**
- Consumes: `SavedPlace`, `SavedPlacesRepository`, `FakeSavedPlaces` (Task 1); `LocationProvider`; `DrivingRestrictions`
- Produces: `RecommendationRow(place: PlaceSummary, distanceMeters: Int?, enrichment, category, onClick, modifier)`;
  `PlaceDetailsScreen(distanceMeters: Int?, …)`; `PlaceDetailsRoute(place, distanceMeters: Int?, category, enrichment)`;
  `sealed interface SavedUiState { Loading; Empty; ParkToSee; Content(rows: List<SavedRow>) }`;
  `data class SavedRow(val place: SavedPlace, val distanceMeters: Int?)`;
  `SavedViewModel(saved: SavedPlacesRepository, location: LocationProvider, drivingRestrictions: DrivingRestrictions)`;
  `SavedScreen(state: SavedUiState, onBack: () -> Unit, onPlaceSelected: (SavedRow) -> Unit, modifier)`;
  strings `tab_saved`, `saved_empty_title`, `saved_empty_body`; `internal` `LoadingState` and `ParkToSeeMessage`.

- [ ] **Step 1: Write the failing ViewModel tests**

`app/src/test/java/com/kanyandula/discovernearby/ui/screens/SavedViewModelTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import com.kanyandula.discovernearby.MainDispatcherRule
import com.kanyandula.discovernearby.car.DrivingState
import com.kanyandula.discovernearby.car.FakeDrivingRestrictions
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.ORIGIN
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.saved.FakeSavedPlaces
import com.kanyandula.discovernearby.ui.screens.SavedUiState.Content
import com.kanyandula.discovernearby.ui.screens.SavedUiState.Empty
import com.kanyandula.discovernearby.ui.screens.SavedUiState.Loading
import com.kanyandula.discovernearby.ui.screens.SavedUiState.ParkToSee
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy
class SavedViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val saved = FakeSavedPlaces()
    private val location = FakeLocationProvider(LocationResult.Available(ORIGIN))
    private val restrictions = FakeDrivingRestrictions()

    /** A Saved screen collecting its state, the location read as far as it can. */
    private fun TestScope.collected(provider: LocationProvider = location) =
        SavedViewModel(saved, provider, restrictions).also {
            backgroundScope.launch { it.uiState.collect {} }
            runCurrent()
        }

    /** Saves in order, so the last one is the newest. */
    private suspend fun save(vararg metersNorth: Int) =
        metersNorth.forEach { saved.save(testPlace("m$it", "cafe", metersNorth = it), COFFEE, null) }

    private val SavedViewModel.shown get() =
        (uiState.value as Content).rows.map { it.place.summary.id to it.distanceMeters }

    @Test
    fun emptyWithNothingSaved() = runTest {
        assertEquals(Empty, collected().uiState.value)
    }

    @Test
    fun loadingWhileLocating() = runTest {
        save(100)
        val slow = object : LocationProvider {
            override suspend fun currentLocation(): LocationResult {
                delay(1_000)
                return LocationResult.Available(ORIGIN)
            }
        }
        val vm = collected(slow)
        assertEquals(Loading, vm.uiState.value)
        advanceTimeBy(1_001)
        assertEquals(listOf("m100" to 100), vm.shown)
    }

    @Test
    fun nearestFirstFromWhereTheCarIsNow() = runTest {
        save(900, 100, 500)
        assertEquals(listOf("m100" to 100, "m500" to 500, "m900" to 900), collected().shown)
    }

    // Review Focus 2: without a location, the newest first and no distances; Saved never asks for permission.
    @Test
    fun withoutALocationNewestFirstAndNoDistances() = runTest {
        location.result = LocationResult.PermissionMissing
        save(100, 900)
        assertEquals(listOf("m900" to null, "m100" to null), collected().shown)
    }

    // Review Focus 5
    @Test
    fun theDrivingLimitTrimsTheList() = runTest {
        save(100, 200, 300)
        restrictions.state.value = DrivingState(distractionOptimizationRequired = true, listLimit = 2)
        assertEquals(listOf("m100" to 100, "m200" to 200), collected().shown)
    }

    @Test
    fun aLimitOfZeroAsksToPark() = runTest {
        save(100)
        restrictions.state.value = DrivingState(distractionOptimizationRequired = true, listLimit = 0)
        assertEquals(ParkToSee, collected().uiState.value)
    }

    // Review Focus 4: removed on Place Details, gone here on Back, without a reload.
    @Test
    fun aPlaceRemovedElsewhereLeavesTheList() = runTest {
        save(100, 200)
        val vm = collected()
        saved.remove("m100")
        runCurrent()
        assertEquals(listOf("m200" to 200), vm.shown)
    }
}
```

- [ ] **Step 2: Write the failing screen tests**

`app/src/test/java/com/kanyandula/discovernearby/ui/screens/SavedScreenTest.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.saved.SavedPlace
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.hereNotice
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class SavedScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf<SavedUiState>(SavedUiState.Loading)
    private val opened = mutableListOf<String>()

    private val cafe = SavedPlace(testPlace("Harbour Café", "cafe").copy(attribution = "HERE"), DiscoveryCategory.COFFEE, null, 2)
    private val park = SavedPlace(testPlace("Hill Park", "park"), DiscoveryCategory.OUTDOORS, null, 1)

    @Before
    fun setUp() {
        rule.setContent {
            DiscoverNearbyTheme {
                SavedScreen(state = state, onBack = {}, onPlaceSelected = { opened += it.place.summary.id })
            }
        }
    }

    @Test
    fun nothingSavedSaysHowToSave() {
        state = SavedUiState.Empty
        rule.onNodeWithText("No saved places yet").assertIsDisplayed()
        rule.onNodeWithText("Tap Save on a place to keep it here.").assertIsDisplayed()
    }

    @Test
    fun rowsShowTheDistanceOnlyWhenKnownAndOpenThePlace() {
        state = SavedUiState.Content(listOf(SavedRow(cafe, 2_100), SavedRow(park, null)))
        rule.onNodeWithText("2.1 km").assertIsDisplayed()
        rule.onAllNodesWithText("km", substring = true).assertCountEquals(1)
        rule.onNodeWithText("Hill Park").performClick()
        assertEquals(listOf("Hill Park"), opened)
    }

    @Test
    fun hereRowsCarryHeresNotice() {
        state = SavedUiState.Content(listOf(SavedRow(cafe, 2_100)))
        rule.onNodeWithText(hereNotice()).assertIsDisplayed()
    }
}
```

`PlaceDetailsScreenTest` (a place opened from Saved without a location):

```kotlin
    // DN-UX-006: opened from Saved without a location, the facts leave the distance out.
    @Test
    fun anUnknownDistanceIsLeftOut() {
        distance = null
        state = Content(PlaceDetails(place, openingSummary = null))
        rule.onNodeWithText("Café").assertIsDisplayed()
        rule.onAllNodesWithText("km", substring = true).assertCountEquals(0)
    }
```

with `private var distance by mutableStateOf<Int?>(2_100)` passed as `distanceMeters = distance`.

`FocusRingTest.focusedRecommendationRowShowsTheRing`: the call becomes
`RecommendationRow(place, distanceMeters = 500, enrichment = null, DiscoveryCategory.COFFEE, onClick = {})` (drop
the `Recommendation` value).

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*Saved*' --tests '*PlaceDetailsScreenTest*' --tests '*FocusRingTest*'`
Expected: compilation FAILS (no `SavedViewModel`, `SavedScreen`, `SavedRow`, `SavedUiState`; `distanceMeters` not nullable).

- [ ] **Step 4: Implement**

Strings (after `saved`):

```xml
    <string name="tab_saved">Saved</string>
    <string name="saved_empty_title">No saved places yet</string>
    <string name="saved_empty_body">Tap Save on a place to keep it here.</string>
```

`RecommendationRow`: parameters `place: PlaceSummary, distanceMeters: Int?,` replace `recommendation: Recommendation,`;
drop `val place = recommendation.place`; KDoc: "One place on a list (Recommendations, Saved): … distance when known."
The distance line becomes
`distanceMeters?.let { SupportingLine(stringResource(R.string.distance_km, kilometres(it))) }`.
`RecommendationsScreen` passes `recommendation.place, recommendation.distanceMeters,`.
In `RecommendationsScreen.kt`, `ParkToSeeMessage` and `LoadingState` become `internal` (Saved reuses them).

`PlaceDetailsRoute.distanceMeters: Int?` with the comment "null when opened from Saved without a location (DN-UX-006)".
`PlaceDetailsScreen(distanceMeters: Int?, …)`; `Summary(distanceMeters: Int?, …)`; in `facts` the distance becomes
`distanceMeters?.let { stringResource(R.string.distance_away, kilometres(it)) },`.

`SavedViewModel.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanyandula.discovernearby.car.DrivingRestrictions
import com.kanyandula.discovernearby.car.DrivingState
import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.saved.SavedPlace
import com.kanyandula.discovernearby.saved.SavedPlacesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Saved destination's states (DN-UX-006). All draw on one screen; none is pushed. */
sealed interface SavedUiState {
    data object Loading : SavedUiState
    data object Empty : SavedUiState

    /** Places are saved, but the driving list limit allows none to be shown (docs/02 §17). */
    data object ParkToSee : SavedUiState
    data class Content(val rows: List<SavedRow>) : SavedUiState
}

/** A saved place and how far it is from the car now; null without a location. */
data class SavedRow(val place: SavedPlace, val distanceMeters: Int?)

/**
 * Saved places nearest first (DN-UX-006). The location is read once, when the screen opens (docs/03 §13); without one
 * (permission refused, no fix) the newest come first, with no distances, and Saved never asks for permission. The
 * list follows the database, so a place removed on Place Details is gone on Back.
 */
class SavedViewModel(
    saved: SavedPlacesRepository,
    location: LocationProvider,
    drivingRestrictions: DrivingRestrictions,
) : ViewModel() {

    private val here = MutableStateFlow<LocationResult?>(null) // null while locating

    val uiState: StateFlow<SavedUiState> =
        combine(here, saved.savedPlaces(), drivingRestrictions.state, ::toUiState).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = SavedUiState.Loading,
        )

    init {
        viewModelScope.launch { here.value = location.currentLocation() }
    }
}

private fun toUiState(here: LocationResult?, places: List<SavedPlace>, driving: DrivingState): SavedUiState {
    if (places.isEmpty()) return SavedUiState.Empty
    if (here == null) return SavedUiState.Loading
    val origin = (here as? LocationResult.Available)?.point
    // A stable sort: without a location every distance is null, so the stored newest-first order stands.
    val rows = places.map { SavedRow(it, origin?.distanceMetersTo(it.summary.location)) }.sortedBy { it.distanceMeters }
    // docs/03 §6: the driving limit trims the list.
    val shown = rows.take(driving.listLimit ?: Int.MAX_VALUE)
    return if (shown.isEmpty()) SavedUiState.ParkToSee else SavedUiState.Content(shown)
}
```

`SavedScreen.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.components.Message
import com.kanyandula.discovernearby.ui.components.MessageState
import com.kanyandula.discovernearby.ui.components.ProviderAttribution
import com.kanyandula.discovernearby.ui.components.RecommendationRow
import com.kanyandula.discovernearby.ui.components.ScreenHeader
import com.kanyandula.discovernearby.ui.rememberReturnFocus
import com.kanyandula.discovernearby.ui.theme.Highlight
import com.kanyandula.discovernearby.ui.theme.RowGap

private val SavedEmptyMessage =
    Message(R.drawable.ic_heart, Highlight, R.string.saved_empty_title, R.string.saved_empty_body)

/** Saved places as Recommendations rows (DN-UX-006); every state draws on this one destination. */
@Composable
fun SavedScreen(
    state: SavedUiState,
    onBack: () -> Unit,
    onPlaceSelected: (SavedRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val returnFocus = rememberReturnFocus()
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        ScreenHeader(title = stringResource(R.string.tab_saved), onBack = onBack)
        val body = Modifier.weight(1f)
        when (state) {
            SavedUiState.Loading -> LoadingState(body)
            SavedUiState.Empty ->
                MessageState(SavedEmptyMessage, backLabel = R.string.back_to_discover, onBack = onBack, modifier = body)
            // Parking shows them on its own: the list follows the driving state.
            SavedUiState.ParkToSee ->
                MessageState(ParkToSeeMessage, backLabel = R.string.back, onBack = onBack, modifier = body)
            is SavedUiState.Content -> Column(modifier = body, verticalArrangement = Arrangement.spacedBy(RowGap)) {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    items(state.rows, key = { it.place.summary.id }) { row ->
                        val id = row.place.summary.id
                        RecommendationRow(
                            place = row.place.summary,
                            distanceMeters = row.distanceMeters,
                            enrichment = row.place.enrichment,
                            category = row.place.category,
                            onClick = {
                                returnFocus.selected(id)
                                onPlaceSelected(row)
                            },
                            modifier = returnFocus.item(id),
                        )
                    }
                }
                // HERE's notice with HERE's data (ADR-001 V6a); the fakes carry none.
                ProviderAttribution(state.rows.firstNotNullOfOrNull { it.place.summary.attribution })
            }
        }
    }
}
```

- [ ] **Step 5: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*Saved*' --tests '*PlaceDetails*' --tests '*Recommendations*' --tests '*FocusRingTest*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Add the Saved screen: saved places nearest first"
```

---

### Task 5: The app rail and the Saved destination

**Files:**
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/components/AppRail.kt`
- Create: `app/src/main/res/drawable/ic_grid.xml`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNearbyApp.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/DiscoverNavHost.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/theme/Dimens.kt`, `app/src/main/res/values/strings.xml`
- Test: `ui/DiscoverNavigationTest.kt`, `ui/components/FocusRingTest.kt`

**Interfaces:**
- Consumes: `SavedScreen`, `SavedViewModel`, `SavedRow` (Task 4); `AppContainer.savedPlaces` (Task 2)
- Produces: `@Serializable data object SavedRoute`; `enum class RailTab { DISCOVER, SAVED }`;
  `AppRail(selected: RailTab, onSelect: (RailTab) -> Unit, modifier)`; `NavHostController.selectedTab(): RailTab`;
  `NavHostController.openTab(tab: RailTab)`; `RailGap = 16.dp`, `RailIconSize = 32.dp`; string `tab_discover`.

- [ ] **Step 1: Write the failing navigation tests**

`DiscoverNavigationTest`: capture `focusManager = LocalFocusManager.current` beside `inputModes` in `setUp` (field
`private lateinit var focusManager: FocusManager`); helpers and tests:

```kotlin
    private fun tab(name: String) = rule.onNodeWithContentDescription(name)

    /** Room answers on its own threads: wait for [text] rather than assume one frame is enough. */
    private fun waitForText(text: String) =
        rule.waitUntil(DB_TIMEOUT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    // DN-UX-006: the rail's selection follows the back stack; Back from Saved returns to Discover.
    @Test
    fun savedOpensOnTopOfDiscoverAndBackReturns() {
        tab("Discover").assertIsSelected()
        tab("Saved").performClick()
        waitForText("No saved places yet")
        tab("Saved").assertIsSelected()
        tab("Discover").assertIsNotSelected()
        systemBack()
        onDiscover()
        tab("Discover").assertIsSelected()
    }

    @Test
    fun theDiscoverTabReturnsToTheGrid() {
        rule.onNodeWithText("Food").performClick()
        tab("Discover").performClick()
        onDiscover()
        systemBack()
        assertTrue(rule.activity.isFinishing) // nothing was stacked under the grid
    }

    @Test
    fun savedTappedTwiceStacksOneScreen() {
        tab("Saved").performClick()
        tab("Saved").performClick()
        systemBack()
        onDiscover()
    }

    // Save on Details, find it on Saved, open it: Saved stays selected, and Back walks Details → Saved → Discover.
    @Test
    fun aSavedPlaceOpensFromSavedWithTheTabSelected() {
        rule.onNodeWithText("Coffee").performClick()
        rule.onNodeWithText("Harbour Roasters, Greystones").performClick()
        rule.onNodeWithText("Save").performClick()
        waitForText("Saved")
        tab("Saved").performClick()
        waitForText("Harbour Roasters, Greystones")
        rule.onNodeWithText("Harbour Roasters, Greystones").performClick()
        rule.onNodeWithText("Navigate").assertIsDisplayed()
        tab("Saved").assertIsSelected()
        systemBack()
        rule.onNodeWithText("Harbour Roasters, Greystones").assertIsDisplayed()
        systemBack()
        onDiscover()
    }

    // DN-UX-006: rotary reaches the rail after the screen's content; the first turn still lands on the first tile.
    @Test
    fun rotaryReachesTheRailAfterTheContent() {
        rule.useRotaryInput(inputModes)
        fun turn() = rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }
        turn()
        rule.onNodeWithText("Coffee").assertIsFocused()
        repeat(5) { turn() }
        rule.onNodeWithText("Explore").assertIsFocused()
        turn()
        tab("Discover").assertIsFocused()
        turn()
        tab("Saved").assertIsFocused()
    }
```

In the companion: `const val DB_TIMEOUT_MS = 5_000L`.

`FocusRingTest`, beside the header test:

```kotlin
    @Test
    fun focusedRailTabShowsTheRing() {
        show { AppRail(selected = RailTab.DISCOVER, onSelect = {}) }
        val saved = rule.onNodeWithContentDescription(text(R.string.tab_saved))
        saved.requestFocus()
        assertArrayEquals(Accent.rgb(), saved.edgePixel().rgb(), TOLERANCE)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiscoverNavigationTest*' --tests '*FocusRingTest*'`
Expected: compilation FAILS (no `AppRail`, `RailTab`); after it compiles, the new navigation tests fail on the
missing rail.

- [ ] **Step 3: Implement**

Strings: `<string name="tab_discover">Discover</string>` before `tab_saved`.

`Dimens.kt`, after the Place Details block:

```kotlin
// The app rail (DN-UX-006): Discover and Saved, 76 dp targets, left of the panel.
val RailGap = 16.dp
val RailIconSize = 32.dp
```

`ic_grid.xml` (the concept's four squares, filled):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z M15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z" />
</vector>
```

`AppRail.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.OnSurface
import com.kanyandula.discovernearby.ui.theme.OnSurfaceVariant
import com.kanyandula.discovernearby.ui.theme.RailGap
import com.kanyandula.discovernearby.ui.theme.RailIconSize
import com.kanyandula.discovernearby.ui.theme.Raised
import com.kanyandula.discovernearby.ui.theme.RowRadius

/** The app's own tabs (DN-UX-006). The car's Music, Phone and Settings stay on its system rail. */
enum class RailTab(@param:DrawableRes val icon: Int, @param:StringRes val label: Int) {
    DISCOVER(R.drawable.ic_grid, R.string.tab_discover),
    SAVED(R.drawable.ic_heart_filled, R.string.tab_saved),
}

/** Icons only, as in the concept (03b-place-details-concept); the selected tab on Raised grey. */
@Composable
fun AppRail(selected: RailTab, onSelect: (RailTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RailGap)) {
        RailTab.entries.forEach { tab -> RailItem(tab, selected = tab == selected, onClick = { onSelect(tab) }) }
    }
}

@Composable
private fun RailItem(tab: RailTab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(RowRadius)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.focusRing(shape).size(MinTouchTarget).semantics { role = Role.Tab },
        shape = shape,
        color = if (selected) Raised else Color.Transparent,
        contentColor = if (selected) OnSurface else OnSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(tab.icon),
                contentDescription = stringResource(tab.label),
                modifier = Modifier.size(RailIconSize),
            )
        }
    }
}
```

`DiscoverNavHost.kt`: after `RecommendationsRoute`:

```kotlin
@Serializable
data object SavedRoute
```

after `isTop`:

```kotlin
/** Saved while Saved is on the back stack: on Saved and on a Place Details opened from it (DN-UX-006). */
internal fun NavHostController.selectedTab(): RailTab = try {
    getBackStackEntry<SavedRoute>()
    RailTab.SAVED
} catch (ignored: IllegalArgumentException) {
    RailTab.DISCOVER
}

/** Saved sits on Discover, single top; Discover pops back to the grid. Neither builds a stack (DN-UX-006). */
internal fun NavHostController.openTab(tab: RailTab) {
    when (tab) {
        RailTab.DISCOVER -> popBackStack<DiscoverRoute>(inclusive = false)
        RailTab.SAVED -> navigate(SavedRoute) {
            popUpTo<DiscoverRoute>()
            launchSingleTop = true
        }
    }
}
```

the destination, after `RecommendationsRoute`'s:

```kotlin
        composable<SavedRoute> { entry ->
            val viewModel = viewModel {
                SavedViewModel(container.savedPlaces, container.locationProvider, container.drivingRestrictions)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SavedScreen(
                state = state,
                onBack = { if (navController.isTop(entry)) navController.popBackStack() },
                onPlaceSelected = { row ->
                    if (navController.isTop(entry)) {
                        navController.navigate(
                            PlaceDetailsRoute(row.place.summary, row.distanceMeters, row.place.category, row.place.enrichment),
                        )
                    }
                },
            )
        }
```

`DiscoverNearbyApp`:

```kotlin
@Composable
fun DiscoverNearbyApp(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState() // read so the tab follows every move
    val tab = entry?.let { navController.selectedTab() } ?: RailTab.DISCOVER
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(start = PanelPadding, end = PanelPadding, bottom = PanelPadding),
        ) {
            AppHeader()
            // The panel is composed before the rail, so rotary reaches the rail after the screen's content and the
            // first turn still lands on the screen's first item (DN-UX-006); the rail is drawn on the left.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(start = MinTouchTarget + RailGap),
                    shape = RoundedCornerShape(PanelRadius),
                ) {
                    DiscoverNavHost(
                        container = container,
                        modifier = Modifier.padding(PanelPadding),
                        navController = navController,
                    )
                }
                AppRail(selected = tab, onSelect = navController::openTab)
            }
        }
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiscoverNavigationTest*' --tests '*FocusRingTest*' --tests '*DiscoverScreenTest*' --tests '*RotaryContractTest*'`
Expected: PASS. If `moveFocus(Next)` from no focus does not land on the first tile, rule on it in the ledger and
start the walk from `requestFocus()` on Coffee; the emulator run (Task 7) is the real check.

- [ ] **Step 5: Run the whole suite and the static checks**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Add the app rail with Discover and Saved"
```

---

### Task 6: Docs

**Files:**
- Modify: `docs/adr/0004-storing-data-in-the-poc.md`, `docs/03-discover-nearby-engineering-implementation-plan.md`,
  `docs/02-discover-nearby-ux-interaction-spec.md`, `docs/05-discover-nearby-delivery-plan.md`,
  `docs/design/README.md`, `CLAUDE.md`

- [ ] **Step 1: ADR-004** — under Decision, after "First use", add:

```markdown
- **How (DN-UX-006):** package `saved/`, Room 2.8 through KSP. One table, `saved_places`: the place ID (key), the
  category, when it was saved, the HERE summary and the Tripadvisor enrichment as JSON. No exported schema: a schema
  change wipes saved places, which the POC accepts. A row that no longer decodes is skipped. Live data and the
  `--es scenario` fakes keep separate files (`saved-places.db`, `saved-places-fakes.db`), so made-up places never
  show in a live list; Robolectric keeps them in memory.
```

- [ ] **Step 2: docs/03** — §3 tree: under `ui/` add `AppRail` to components and `SavedScreen`, `SavedViewModel`
  to screens; a new branch:

```text
├── saved/
│   ├── SavedPlace, SavedPlacesRepository (interface)
│   ├── SavedPlacesDatabase             (Room: entity, DAO, database; ADR-004)
│   └── RoomSavedPlaces                 (JSON columns; separate live and fake files)
```

  §6 Screens table: a `SavedScreen` row ("List (loading, content); message state (empty, park to see)", "Rail tab;
  nearest first, newest first without a location"); Back stack: "The app rail (DN-UX-006) has Discover and Saved:
  Saved sits on Discover, single top; Discover pops to the grid. Saved → Place Details is two destinations deep."
  Architecture rules (wherever docs/03 lists them): "Room stays in `saved/` (`ArchitectureRulesTest`)."

- [ ] **Step 3: docs/02** — §4 screen summary: add Saved; §7 Place Details: "**Save** ↔ **Saved** beside Navigate:
  keeps the place on the device (ADR-004); one tap, also while driving."; a short Saved section after §7 with the
  rail, the order (nearest first; without a location, newest first, no distances), the driving limit, the empty copy;
  §16 order:

```text
Discover:         Coffee → Food → Outdoors → Family → Scenic → Explore → [rail] Discover → Saved
Recommendations:  Result 1 → Result 2 → Result 3 … → [rail]
Place Details:    Content → Save → Navigate → [rail]
Saved:            Saved place 1 → Saved place 2 … → [rail]
```

- [ ] **Step 4: docs/05 V7 row** — append to the status cell: "The app rail (DN-UX-006) adds two focus targets after
  each screen's content, a Product-approved rotary change (design session, 2026-10-11); its emulator run is recorded
  in DN-UX-006."

- [ ] **Step 5: design README** — the rail: "The app's rail (Discover, Saved; DN-UX-006) sits left of the panel under
  the header. The concept's Music, Phone and Settings are the car's system rail, not the app's."; Save is no longer
  "hidden until DN-UX-006".

- [ ] **Step 6: CLAUDE.md** — Current state: a bullet "**Saved places (DN-UX-006, ADR-004):** …" (rail, Save,
  Saved nearest first, `saved/` Room, separate files, in memory under Robolectric); structure: `saved/`; Next line:
  DN-M2-004.

- [ ] **Step 7: Check and commit**

Run: `grep -rn "Save's slot\|Save is not shown\|hidden until DN-UX-006" docs CLAUDE.md app/src` — Expected: no hits
outside the spec and the DN-UX-005 plan.

```bash
git add docs CLAUDE.md
git commit -m "Record the app rail, Save and Saved in the docs"
```

---

### Task 7: Emulator check

- [ ] **Step 1: Install on the reference AVD**

```bash
adb -s emulator-5554 emu avd name   # must print AAOS_AOSP_33_userdebug
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug
```

- [ ] **Step 2: Fakes** — `adb -s emulator-5554 shell am start -S -n com.kanyandula.discovernearby/.ui.MainActivity --es scenario NORMAL`
  (the fakes' own database). Check with screenshots (scratchpad): the rail on every screen; Saved empty state; Save on
  a place → "Saved"; Saved lists it with its distance; open it → Saved stays selected; Remove → Back → gone; Drive
  (moving) → Save still works and the description hides; Park. The M0 smoke (docs/04 §10) on the fakes.

- [ ] **Step 3: Rotary** — `cmd car_service inject-rotary` turns on Discover, Recommendations, Details and Saved;
  read `focusedNode` from the RotaryService dumpsys. Record: where the first turn lands; content → rail order; Save →
  Navigate on Details; where focus lands after Back. No `uiautomator`.

- [ ] **Step 4: Live, a few calls** — a normal launch (live HERE): Coffee, save one place, open Saved (distance),
  restart the app (`am start -S`) → still saved; relaunch with `--es scenario NORMAL` → the live place is absent.
  About 10 Tripadvisor calls.

- [ ] **Step 5: Record** the results in the DN-UX-006 ticket (vault), with no place names or coordinates in the repo.
