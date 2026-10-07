# DN-M1-001 HERE REST Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put HERE Geocoding & Search v7 (`/browse`, `/lookup`) behind `PlacesRepository`, with a mapper to the domain
models and the dev-only key from `local.properties` through `BuildConfig`. The client is not wired into the app yet.

**Architecture:** A new package, `places/here/`:
- `@Serializable` response models (internal; all fields optional);
- the category mapping in both directions: request codes per `DiscoveryCategory`, and response category → domain
  kind;
- the mapper;
- `HerePlacesRepository` (OkHttp `enqueue` behind a cancellable suspend, errors mapped to `PlacesException`).

`AppContainer` keeps the fakes. DN-M1-002 wires the client and runs the first live search.

**Tech Stack:** Kotlin, OkHttp 5.5.0 (already a dependency), kotlinx.serialization 1.11.0, coroutines 1.11.0, JUnit,
Robolectric 4.17, Gradle Kotlin DSL `buildConfigField`.

**Spec:**
- the ticket `~/.claude/projects/Discover Nearby/tickets/DN-M1-001-provider-rest-client.md`;
- ADR-001 (`docs/adr/0001-poi-provider.md`): Decision, Field availability, Category mapping (Selected provider),
  V6a–c;
- docs/03 §8 (interface, mapper), §14 (caching), §15 (timeout), §16 (failures), §19 (keys, attribution).

## Global Constraints

- REST through OkHttp and kotlinx.serialization. No provider SDK (ADR-001; ticket AC).
- **Key:** `here.apiKey` from `local.properties` → `BuildConfig.HERE_API_KEY`. Never committed or logged. Empty
  without `local.properties` (CI).
- **Caching (ADR-001 V6b):** no persistence. No OkHttp cache, and no fixtures recorded from HERE (public repo). Test
  responses are synthetic.
- **Provider models (CLAUDE.md, docs/03 §8):** never leave `places/here/`. Only `AppContainer` may import from it.
- **Unknown data is neutral:**
  - fields HERE lacks (rating, rating count, parking, toilets, travel time) map to null or an empty set;
  - nothing is invented;
  - attribution stays null until HERE's brand guidance is read (ADR-001 V6a, → Legal).
- **Errors (docs/03 §8, §16):** `ProviderFailure` or `NetworkUnavailable`, with no message from the call. The URL
  carries the key and the origin.
- **Timeout (docs/03 §15):** the use case's 8 s `withTimeoutOrNull` cancels the coroutine, so the HTTP call must
  cancel with it.
- **Imports:** the Android Studio layout (DN-TD-001): ASCII order, then `java.*`, `javax.*`, `kotlin.*`.
- **Checks:** detekt defaults (ReturnCount 2, ThrowsCount 2, MaxLineLength 120), lint, tests and build:
  `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`.
- No changes to `AppContainer`, the UI or the use case.
- No AI attribution.

## Review Focus

1. **A result in the requested category gets a kind the engine rejects.** The category would then show Empty on
   live data. Expect every requested HERE code to map to a kind the category's `CategoryConfig.targetKinds` accepts.
   Pinned by `HereCategoriesTest.everyRequestedCategoryMapsToAKindItsCategoryAccepts` (Task 2).
2. **One malformed item** (no `lng`, no title) **among good ones.** Expect the good ones to still map, and the bad one
   to be dropped without failing the response. Pinned by `HereMapperTest.aMalformedItemDoesNotFailTheResponse` and
   `anItemWithoutAnIdNameOrPositionIsDropped` (Task 2).
3. **The driver backs out, or switches category, mid-request.** Expect the HTTP call to be cancelled, not left
   running. Pinned by `HerePlacesRepositoryTest.cancellingTheSearchCancelsTheCall` (Task 3).
4. **No key** (CI, a fresh clone). Expect `ProviderFailure` (the Error state with Retry) and no request sent. Pinned
   by `aMissingKeyFailsWithoutARequest` (Task 3).
5. **An error response echoes the request** (key, origin). Expect nothing of it in the exception. Pinned by
   `failuresCarryNoKeyOrOrigin` (Task 3).

---

## File structure

| File | Change | Responsibility |
| --- | --- | --- |
| `app/build.gradle.kts` | Modify | `HERE_API_KEY` build config field; `buildConfig = true` |
| `app/src/test/.../places/here/HereKeyTest.kt` | Create | the key reaches `BuildConfig`, trimmed |
| `app/src/main/.../places/here/HereModels.kt` | Create | `/browse` and `/lookup` response shapes |
| `app/src/main/.../places/here/HereCategories.kt` | Create | request codes; HERE category → domain kind |
| `app/src/main/.../places/here/HereMapper.kt` | Create | `HereItem` → `PlaceSummary` / `PlaceDetails` |
| `app/src/main/.../places/here/HerePlacesRepository.kt` | Create | requests, cancellation, error mapping |
| `app/src/test/.../places/here/HereCategoriesTest.kt` | Create | mapping tests |
| `app/src/test/.../places/here/HereMapperTest.kt` | Create | mapper tests (synthetic JSON) |
| `app/src/test/.../places/here/HerePlacesRepositoryTest.kt` | Create | repository tests (an OkHttp interceptor stands in for HERE) |
| `app/src/test/.../ArchitectureRulesTest.kt` | Modify | only `AppContainer` reaches into `places.here` |
| `CLAUDE.md`, `docs/03-…`, `docs/adr/0001-poi-provider.md` | Modify | state, structure, mapper note, V6a note |

`…` is `java/com/kanyandula/discovernearby`.

---

### Task 1: The key, through BuildConfig

**Files:**
- Modify: `app/build.gradle.kts` (top import; after `val compileApi`; `defaultConfig`; `buildFeatures`)
- Test: `app/src/test/java/com/kanyandula/discovernearby/places/here/HereKeyTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `BuildConfig.HERE_API_KEY: String`, in package `com.kanyandula.discovernearby`. It is empty when
  `local.properties` has no `here.apiKey`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class HereKeyTest {

    // docs/03 §19: the dev-only key comes from local.properties through BuildConfig. Properties keeps trailing
    // spaces, which HERE would reject, so the build trims it. Empty on CI, where there is no local.properties.
    @Test
    fun theKeyComesFromBuildConfigTrimmed() {
        val key: String = BuildConfig.HERE_API_KEY
        assertEquals(key.trim(), key)
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*HereKeyTest' --console=plain`
Expected: compilation FAILS: `Unresolved reference 'BuildConfig'` (or `'HERE_API_KEY'`).

- [ ] **Step 3: Add the field**

In `app/build.gradle.kts`, add this as the first line of the file, above `plugins {`:

```kotlin
import java.util.Properties
```

After `val compileApi = libs.versions.android.compileSdk.get().toInt()`, add:

```kotlin

// Dev-only provider key (docs/03 §19): local.properties → BuildConfig, never committed or logged. Empty without
// local.properties (CI); the HERE repository then fails without making a request.
val hereApiKey: String = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { load(it.reader()) }
}.getProperty("here.apiKey", "").trim()
```

In `defaultConfig { … }`, after `versionName = "1.0"`, add:

```kotlin
        buildConfigField("String", "HERE_API_KEY", "\"$hereApiKey\"")
```

In `buildFeatures { … }`, after `compose = true`, add:

```kotlin
        buildConfig = true
```

- [ ] **Step 4: Run it and see it pass; check the generated file without printing the key**

```bash
./gradlew :app:testDebugUnitTest --tests '*HereKeyTest' --console=plain
F=$(find app/build/generated -name BuildConfig.java -path '*debug*' | head -1)
awk -F'"' '/HERE_API_KEY/ {print "HERE_API_KEY length:", length($2)}' "$F"
git check-ignore -q "$F" && echo "generated BuildConfig is git-ignored"
```

Expected: PASS; `HERE_API_KEY length: 43`; `generated BuildConfig is git-ignored`.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/test/java/com/kanyandula/discovernearby/places/here/HereKeyTest.kt
git commit -m "Read the HERE key from local.properties into BuildConfig"
```

---

### Task 2: Response models, category mapping and the mapper

**Files:**
- Create: `app/src/main/java/com/kanyandula/discovernearby/places/here/HereModels.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/places/here/HereCategories.kt`
- Create: `app/src/main/java/com/kanyandula/discovernearby/places/here/HereMapper.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/places/here/HereCategoriesTest.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/places/here/HereMapperTest.kt`

**Interfaces:**
- Consumes: `PlaceSummary`, `PlaceDetails`, `GeoPoint` (model), `DiscoveryCategory`, `CategoryConfigs` (discovery).
- Produces, all `internal`, in package `com.kanyandula.discovernearby.places.here`:
  - `HereBrowseResponse(items: List<HereItem>)`;
  - `HereItem(id, title, position, categories, openingHours)`;
  - `HerePosition(lat: Double?, lng: Double?)`;
  - `HereCategory(id: String, primary: Boolean)`;
  - `HereOpeningHours(text: List<String>, isOpen: Boolean?)`;
  - `HERE_CATEGORIES: Map<DiscoveryCategory, String>`;
  - `kindFor(hereCategoryId: String): String?`;
  - `HereItem.toSummary(): PlaceSummary?`;
  - `HereItem.toDetails(): PlaceDetails?`.

- [ ] **Step 1: Write the failing tests**

`HereCategoriesTest.kt`:

```kotlin
package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.discovery.CategoryConfigs
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HereCategoriesTest {

    // Review Focus 1: the engine keeps only places whose kinds the category targets. A requested code that maps to
    // nothing, or to another category's kind, would show Empty on live data.
    @Test
    fun everyRequestedCategoryMapsToAKindItsCategoryAccepts() {
        assertEquals(DiscoveryCategory.entries.toSet(), HERE_CATEGORIES.keys)
        HERE_CATEGORIES.forEach { (category, codes) ->
            codes.split(",").forEach { code ->
                val kind = kindFor(code)
                assertTrue("$category $code -> $kind", kind in CategoryConfigs.getValue(category).targetKinds)
            }
        }
    }

    @Test
    fun theMostSpecificCategoryWins() {
        assertEquals("coffee_shop", kindFor("100-1100-0010"))
        assertEquals("cafe", kindFor("100-1100-0331"))
        assertEquals("family_attraction", kindFor("300-3100-0027"))
        assertEquals("museum", kindFor("300-3100-0000"))
    }

    @Test
    fun aCategoryWeDoNotSearchHasNoKind() {
        assertNull(kindFor("700-7300-0000"))
        assertNull(kindFor("100-11000"))
    }
}
```

`HereMapperTest.kt` (synthetic responses shaped like the documented ones; nothing here comes from HERE):

```kotlin
package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.model.GeoPoint
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Synthetic: invented values in the shape of HERE's documented /browse item (ADR-001 [H2]). The spike recorded no
// fixtures (public repo; ADR-001 V6b).
private const val FULL_ITEM = """
{"title": "Test Coffee", "id": "here:pds:place:test-1", "resultType": "place",
 "position": {"lat": 53.1445, "lng": -6.0631}, "distance": 60,
 "categories": [{"id": "100-1000-0000", "name": "Restaurant"},
                {"id": "100-1100-0010", "name": "Coffee Shop", "primary": true}],
 "contacts": [{"phone": [{"value": "+353 1 000 0000"}]}],
 "openingHours": [{"text": ["Mon-Sat: 07:30 - 18:00", "Sun: 09:00 - 17:00"], "isOpen": true, "structured": []}]}
"""

class HereMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun item(text: String) = json.decodeFromString<HereItem>(text)

    @Test
    fun aFullItemMapsEveryKnownField() {
        val place = checkNotNull(item(FULL_ITEM).toSummary())
        assertEquals("here:pds:place:test-1", place.id)
        assertEquals("Test Coffee", place.name)
        assertEquals(GeoPoint(53.1445, -6.0631), place.location)
        assertEquals(setOf("restaurant", "coffee_shop"), place.placeKinds)
        assertEquals("coffee_shop", place.primaryKind)
        assertEquals(true, place.isOpenNow)
    }

    // ADR-001 Field availability: /browse has no rating, parking, toilets or travel time. Unknown stays unknown.
    @Test
    fun fieldsHereLacksStayUnknown() {
        val place = checkNotNull(item(FULL_ITEM).toSummary())
        assertNull(place.rating)
        assertNull(place.ratingCount)
        assertNull(place.travelTimeMinutes)
        assertTrue(place.attributes.isEmpty())
    }

    // Review Focus 2
    @Test
    fun anItemWithoutAnIdNameOrPositionIsDropped() {
        assertNull(item("""{"title": "A", "position": {"lat": 1.0, "lng": 2.0}}""").toSummary())
        assertNull(item("""{"id": "x", "title": "  ", "position": {"lat": 1.0, "lng": 2.0}}""").toSummary())
        assertNull(item("""{"id": "x", "title": "A"}""").toSummary())
        assertNull(item("""{"id": "x", "title": "A", "position": {"lat": 1.0}}""").toSummary())
    }

    // Review Focus 2: one bad item must not fail the whole response.
    @Test
    fun aMalformedItemDoesNotFailTheResponse() {
        val body = """{"items": [{"id": "bad", "title": "Bad", "position": {"lat": 1.0}}, $FULL_ITEM]}"""
        val places = json.decodeFromString<HereBrowseResponse>(body).items.mapNotNull { it.toSummary() }
        assertEquals(listOf("Test Coffee"), places.map { it.name })
    }

    @Test
    fun aSparseItemKeepsWhatItHas() {
        val place = checkNotNull(item("""{"id": "x", "title": "Somewhere", "position": {"lat": 1.0, "lng": 2.0}}""")
            .toSummary())
        assertTrue(place.placeKinds.isEmpty())
        assertNull(place.primaryKind)
        assertNull(place.isOpenNow)
    }

    @Test
    fun withoutAPrimaryFlagTheFirstMappedKindLeads() {
        val place = checkNotNull(item("""{"id": "x", "title": "A", "position": {"lat": 1.0, "lng": 2.0},
            "categories": [{"id": "700-7300-0000"}, {"id": "550-5510-0242"}]}""").toSummary())
        assertEquals("viewpoint", place.primaryKind)
    }

    @Test
    fun detailsAddTheOpeningHoursText() {
        val details = checkNotNull(item(FULL_ITEM).toDetails())
        assertEquals("Mon-Sat: 07:30 - 18:00; Sun: 09:00 - 17:00", details.openingSummary)
        assertNull(details.attribution)
    }

    @Test
    fun detailsWithoutHoursHaveNoOpeningSummary() {
        val details = checkNotNull(item("""{"id": "x", "title": "A", "position": {"lat": 1.0, "lng": 2.0}}""")
            .toDetails())
        assertNull(details.openingSummary)
    }
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*HereCategoriesTest' --tests '*HereMapperTest' --console=plain`
Expected: compilation FAILS: `Unresolved reference 'HERE_CATEGORIES'`, `'kindFor'`, `'HereItem'`.

- [ ] **Step 3: Write the models**

`HereModels.kt`:

```kotlin
package com.kanyandula.discovernearby.places.here

import kotlinx.serialization.Serializable

// HERE Geocoding & Search v7 response shapes (ADR-001 [H2]), only the fields we map. Provider models never leave
// this package (docs/03 §8). Every field is optional, so one sparse item drops out instead of failing the response.

@Serializable
internal data class HereBrowseResponse(val items: List<HereItem> = emptyList())

@Serializable
internal data class HereItem(
    val id: String? = null,
    val title: String? = null,
    val position: HerePosition? = null,
    val categories: List<HereCategory> = emptyList(),
    val openingHours: List<HereOpeningHours> = emptyList(),
)

@Serializable
internal data class HerePosition(val lat: Double? = null, val lng: Double? = null)

@Serializable
internal data class HereCategory(val id: String = "", val primary: Boolean = false)

@Serializable
internal data class HereOpeningHours(val text: List<String> = emptyList(), val isOpen: Boolean? = null)
```

- [ ] **Step 4: Write the category mapping**

`HereCategories.kt`:

```kotlin
package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC

/** What `/browse` asks for per category: ADR-001 "Selected provider" mapping (HERE Places category system [H3]). */
internal val HERE_CATEGORIES: Map<DiscoveryCategory, String> = mapOf(
    COFFEE to "100-1100",
    FOOD to "100-1000",
    OUTDOORS to "550-5510-0202,550-5510-0205,350-3522-0239",
    FAMILY to "550-5520-0208,550-5520-0207,550-5520-0357,300-3100-0027",
    SCENIC to "550-5510-0242,350-3510-0238",
    EXPLORE to "300-3000-0023,300-3000,300-3100",
)

// A HERE category, or a parent category, → the normalised kind CategoryConfigs targets. The most specific match
// wins: a Coffee Shop (100-1100-0010) is "coffee_shop", any other Coffee-Tea place "cafe".
private val KINDS = mapOf(
    "100-1100-0010" to "coffee_shop",
    "100-1100" to "cafe",
    "100-1000" to "restaurant",
    "550-5510-0202" to "park",
    "550-5510-0205" to "beach",
    "350-3522-0239" to "forest",
    "550-5520-0208" to "zoo",
    "550-5520-0207" to "family_attraction",
    "550-5520-0357" to "family_attraction",
    "300-3100-0027" to "family_attraction",
    "550-5510-0242" to "viewpoint",
    "350-3510-0238" to "natural_attraction",
    "300-3000-0023" to "tourist_attraction",
    "300-3000" to "landmark",
    "300-3100" to "museum",
)

/** The domain kind for a HERE category ID, or null when no category we search covers it. */
internal fun kindFor(hereCategoryId: String): String? =
    KINDS.keys
        .filter { hereCategoryId == it || hereCategoryId.startsWith("$it-") }
        .maxByOrNull { it.length }
        ?.let(KINDS::getValue)
```

- [ ] **Step 5: Write the mapper**

`HereMapper.kt`:

```kotlin
package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary

/**
 * A HERE item as a domain place, or null without an ID, a name or a full position. `/browse` has no rating, parking,
 * toilets or travel time (ADR-001 Field availability), so those stay unknown; unknown data is neutral.
 */
internal fun HereItem.toSummary(): PlaceSummary? {
    val placeId = id?.takeIf { it.isNotBlank() }
    val name = title?.trim()?.takeIf { it.isNotEmpty() }
    val lat = position?.lat
    val lng = position?.lng
    if (placeId == null || name == null || lat == null || lng == null) return null
    val kinds = categories.mapNotNull { kindFor(it.id) }
    return PlaceSummary(
        id = placeId,
        name = name,
        location = GeoPoint(lat, lng),
        placeKinds = kinds.toSet(),
        primaryKind = categories.firstOrNull { it.primary }?.let { kindFor(it.id) } ?: kinds.firstOrNull(),
        attributes = emptySet(),
        rating = null,
        ratingCount = null,
        isOpenNow = openingHours.firstNotNullOfOrNull { it.isOpen },
        travelTimeMinutes = null,
    )
}

/** Details add the opening-hours text. Attribution stays null until HERE's brand guidance is read (ADR-001 V6a). */
internal fun HereItem.toDetails(): PlaceDetails? = toSummary()?.let { summary ->
    PlaceDetails(
        summary = summary,
        openingSummary = openingHours.flatMap { it.text }.joinToString("; ").ifEmpty { null },
        attribution = null,
    )
}
```

- [ ] **Step 6: Run the tests and see them pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*HereCategoriesTest' --tests '*HereMapperTest' --console=plain`
Expected: PASS (3 + 8 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/places/here/ \
  app/src/test/java/com/kanyandula/discovernearby/places/here/HereCategoriesTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/here/HereMapperTest.kt
git commit -m "Map HERE browse items to domain places"
```

---

### Task 3: The repository, and the package rule

**Files:**
- Create: `app/src/main/java/com/kanyandula/discovernearby/places/here/HerePlacesRepository.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/places/here/HerePlacesRepositoryTest.kt`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ArchitectureRulesTest.kt`

**Interfaces:**
- Consumes:
  - Task 2's `HERE_CATEGORIES`, `HereBrowseResponse`, `HereItem`, `toSummary()` and `toDetails()`;
  - `PlacesRepository`, `ProviderFailure` and `NetworkUnavailable` (places).
- Produces: `class HerePlacesRepository(apiKey: String, client: OkHttpClient = OkHttpClient()) : PlacesRepository`.
  DN-M1-002 constructs it in `AppContainer` with `BuildConfig.HERE_API_KEY`.

- [ ] **Step 1: Write the failing tests**

`HerePlacesRepositoryTest.kt`. It runs under Robolectric, because the app resolves OkHttp's Android variant. An
interceptor stands in for HERE, so no network is used:

```kotlin
package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.PlacesException
import com.kanyandula.discovernearby.places.ProviderFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val KEY = "TEST-KEY-not-real"
private val GREYSTONES = GeoPoint(53.144, -6.0633)
private const val ITEM = """{"id": "here:pds:place:test-1", "title": "Test Coffee",
    "position": {"lat": 53.1445, "lng": -6.0631}, "categories": [{"id": "100-1100-0010", "primary": true}],
    "openingHours": [{"text": ["Mon-Sun: 08:00 - 18:00"], "isOpen": true}]}"""

@RunWith(RobolectricTestRunner::class)
class HerePlacesRepositoryTest {

    private val requests = mutableListOf<Request>()
    private var respond: (Request) -> Response = { reply(it, 200, """{"items": [$ITEM]}""") }
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain -> chain.request().also { requests += it }.let(respond) }
        .build()

    private fun reply(request: Request, code: Int, body: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private fun failure(block: suspend () -> Unit): PlacesException? = runBlocking {
        try {
            block()
            null
        } catch (e: PlacesException) {
            e
        }
    }

    private fun searchFailure(key: String = KEY) = failure {
        HerePlacesRepository(key, client).searchNearby(GREYSTONES, DiscoveryCategory.COFFEE, 1)
    }

    @Test
    fun browseAsksForTheCategoryAroundTheOrigin() = runBlocking {
        HerePlacesRepository(KEY, client).searchNearby(GREYSTONES, DiscoveryCategory.FAMILY, 15_000)
        val url = requests.single().url
        assertEquals("browse.search.hereapi.com", url.host)
        assertEquals("53.144,-6.0633", url.queryParameter("at"))
        assertEquals("circle:53.144,-6.0633;r=15000", url.queryParameter("in"))
        assertEquals("550-5520-0208,550-5520-0207,550-5520-0357,300-3100-0027", url.queryParameter("categories"))
        assertEquals("20", url.queryParameter("limit"))
        assertEquals(KEY, url.queryParameter("apiKey"))
    }

    // A comma-decimal locale must not reach the coordinates.
    @Test
    fun coordinatesUseDotsWhateverTheDefaultLocale() = runBlocking {
        val default = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            HerePlacesRepository(KEY, client).searchNearby(GREYSTONES, DiscoveryCategory.COFFEE, 5_000)
        } finally {
            Locale.setDefault(default)
        }
        assertEquals("53.144,-6.0633", requests.single().url.queryParameter("at"))
    }

    @Test
    fun browseItemsBecomePlaces() = runBlocking {
        val places = HerePlacesRepository(KEY, client).searchNearby(GREYSTONES, DiscoveryCategory.COFFEE, 5_000)
        assertEquals(listOf("Test Coffee"), places.map { it.name })
    }

    @Test
    fun detailsLookUpThePlaceById() = runBlocking {
        respond = { reply(it, 200, ITEM) }
        val details = HerePlacesRepository(KEY, client).getPlaceDetails("here:pds:place:test-1")
        val url = requests.single().url
        assertEquals("lookup.search.hereapi.com", url.host)
        assertEquals("here:pds:place:test-1", url.queryParameter("id"))
        assertEquals("Mon-Sun: 08:00 - 18:00", details.openingSummary)
    }

    @Test
    fun detailsWithoutAUsablePlaceAreAProviderFailure() {
        respond = { reply(it, 200, """{"id": "x"}""") }
        assertTrue(failure { HerePlacesRepository(KEY, client).getPlaceDetails("x") } is ProviderFailure)
    }

    // Review Focus 4
    @Test
    fun aMissingKeyFailsWithoutARequest() {
        assertTrue(searchFailure(key = "") is ProviderFailure)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun httpErrorsAreProviderFailures() {
        listOf(400, 401, 403, 429, 500, 503).forEach { code ->
            respond = { reply(it, code, """{"error": "x"}""") }
            val error = searchFailure()
            assertTrue("HTTP $code -> $error", error is ProviderFailure)
        }
    }

    @Test
    fun anUnreadableBodyIsAProviderFailure() {
        respond = { reply(it, 200, "<html>not json</html>") }
        assertTrue(searchFailure() is ProviderFailure)
    }

    @Test
    fun aCallThatCannotCompleteIsNetworkUnavailable() {
        respond = { throw IOException("timeout") }
        assertTrue(searchFailure() is NetworkUnavailable)
    }

    // Review Focus 5: the URL holds the key and the origin; an error that echoes it must not carry them on.
    @Test
    fun failuresCarryNoKeyOrOrigin() {
        respond = { reply(it, 401, """{"error": "Unauthorized", "request": "${it.url}"}""") }
        val error = checkNotNull(searchFailure())
        assertFalse(error.toString().contains(KEY))
        assertFalse(error.toString().contains("53.144"))
        assertNull(error.cause)
    }

    // Review Focus 3: Back or a category switch cancels the use case's coroutine; the HTTP call must stop too.
    @Test
    fun cancellingTheSearchCancelsTheCall() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val call = AtomicReference<Call>()
        val blocking = OkHttpClient.Builder().addInterceptor { chain ->
            call.set(chain.call())
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            reply(chain.request(), 200, """{"items": []}""")
        }.build()
        val search = launch(Dispatchers.IO) {
            HerePlacesRepository(KEY, blocking).searchNearby(GREYSTONES, DiscoveryCategory.COFFEE, 5_000)
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        search.cancelAndJoin()
        assertTrue(call.get().isCanceled())
        release.countDown()
    }
}
```

Add to `ArchitectureRulesTest`, next to the other `Regex` fields:

```kotlin
    private val hereImport = Regex("""^import com\.kanyandula\.discovernearby\.places\.here\.""")
```

and after `onlyCarDrivingRestrictionsImportsAndroidCar`:

```kotlin
    // docs/03 §8: provider response models never leave the provider package; only the wiring point may name it.
    @Test
    fun onlyAppContainerReachesIntoTheHerePackage() {
        val allowed = { s: Source -> inPackage(s, "places.here") || s.file.name == "AppContainer.kt" }
        assertEquals(emptyList<String>(), violations(hereImport, allowed))
    }
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*HerePlacesRepositoryTest' --console=plain`
Expected: compilation FAILS: `Unresolved reference 'HerePlacesRepository'`.

- [ ] **Step 3: Write the repository**

`HerePlacesRepository.kt`:

```kotlin
package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.ProviderFailure
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

private const val SEARCH_LIMIT = 20
private val BROWSE_URL = "https://browse.search.hereapi.com/v1/browse".toHttpUrl()
private val LOOKUP_URL = "https://lookup.search.hereapi.com/v1/lookup".toHttpUrl()

/**
 * HERE Geocoding & Search v7 behind [PlacesRepository] (ADR-001: provisionally selected; Legal before production).
 * REST through OkHttp, no SDK. Nothing is cached or stored (ADR-001 V6b). The URL carries [apiKey] and the origin,
 * so it is never logged, and failures carry nothing from the call.
 */
class HerePlacesRepository(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : PlacesRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun searchNearby(
        origin: GeoPoint,
        category: DiscoveryCategory,
        radiusMeters: Int,
    ): List<PlaceSummary> {
        val url = BROWSE_URL.newBuilder()
            .addQueryParameter("at", "${origin.lat},${origin.lng}")
            .addQueryParameter("in", "circle:${origin.lat},${origin.lng};r=$radiusMeters")
            .addQueryParameter("categories", HERE_CATEGORIES.getValue(category))
            .addQueryParameter("limit", "$SEARCH_LIMIT")
            .build()
        return get<HereBrowseResponse>(url).items.mapNotNull { it.toSummary() }
    }

    override suspend fun getPlaceDetails(placeId: String): PlaceDetails =
        get<HereItem>(LOOKUP_URL.newBuilder().addQueryParameter("id", placeId).build()).toDetails()
            ?: throw ProviderFailure()

    /** The decoded body. No key, an HTTP error or an unreadable body is a [ProviderFailure]. */
    private suspend inline fun <reified T> get(url: HttpUrl): T {
        val body = if (apiKey.isBlank()) null else fetch(url.newBuilder().addQueryParameter("apiKey", apiKey).build())
        return body?.let { decode<T>(it) } ?: throw ProviderFailure()
    }

    private inline fun <reified T> decode(body: String): T? = try {
        json.decodeFromString<T>(body)
    } catch (ignored: SerializationException) {
        null
    }

    /** A successful response's body, or null for an HTTP error. A call that can't complete is [NetworkUnavailable]. */
    private suspend fun fetch(url: HttpUrl): String? = try {
        client.newCall(Request(url)).await().use { if (it.isSuccessful) it.body.string() else null }
    } catch (ignored: IOException) {
        throw NetworkUnavailable()
    }
}

/** Suspends until the call completes; cancelling the coroutine cancels the call (stale requests, docs/03 §15). */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
}
```

- [ ] **Step 4: Run the tests and see them pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*HerePlacesRepositoryTest' --tests '*ArchitectureRulesTest' --console=plain`
Expected: PASS: 11 repository tests, plus the architecture tests including `onlyAppContainerReachesIntoTheHerePackage`.

- [ ] **Step 5: Prove the package rule bites**

Temporarily add `import com.kanyandula.discovernearby.places.here.HerePlacesRepository` to
`app/src/main/java/com/kanyandula/discovernearby/ui/screens/DiscoverScreen.kt`, below its last import.
Run: `./gradlew :app:testDebugUnitTest --tests '*ArchitectureRulesTest' --console=plain`
Expected: FAIL in `onlyAppContainerReachesIntoTheHerePackage`, naming `DiscoverScreen.kt`. Remove the import.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/places/here/HerePlacesRepository.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/here/HerePlacesRepositoryTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/ArchitectureRulesTest.kt
git commit -m "Add the HERE places repository over OkHttp"
```

---

### Task 4: Docs and the full check

**Files:**
- Modify: `CLAUDE.md` (Structure `places/` line; "Current state" list; "Next")
- Modify: `docs/03-discover-nearby-engineering-implementation-plan.md` (§3 tree line 147; §8 Mapper, line 495)
- Modify: `docs/adr/0001-poi-provider.md` (a note under the V6a table)

**Interfaces:**
- Consumes: Tasks 1–3 (names only).
- Produces: nothing.

- [ ] **Step 1: CLAUDE.md**

Replace:

```markdown
- `places/` — PlacesRepository; `fake/FakePlacesRepository` (M0); `<provider>/` client + mapper (after ADR-001)
```

with:

```markdown
- `places/` — PlacesRepository; `fake/FakePlacesRepository` (M0); `here/` HERE client + mapper (DN-M1-001, ADR-001)
```

In "Current state", add after the "Provider evaluation (DN-SP-001)" bullet:

```markdown
- **HERE client (DN-M1-001):** `places/here/HerePlacesRepository` (OkHttp + kotlinx.serialization; `/browse`,
  `/lookup`), key `here.apiKey` → `BuildConfig.HERE_API_KEY`. Not wired yet: `AppContainer` still serves the fakes.
```

Replace:

```markdown
Next: M1 (DN-M1-001, the HERE REST client) is ready: ADR-001 provisionally selects HERE (2026-10-07; Legal sign-off
on provider terms pending before production). DN-TD-002 (Gradle/CI tuning) is P3.
```

with:

```markdown
Next: DN-M1-002 (live nearby discovery) wires the HERE client into `AppContainer`; DN-M1-003 (live place details)
follows. ADR-001 provisionally selects HERE (Legal sign-off on provider terms pending before production).
DN-TD-002 (Gradle/CI tuning) is P3.
```

- [ ] **Step 2: docs/03**

Line 147, replace `│   └── <provider>/                     (added after provider ADR)` with
`│   └── here/                           HERE client + mapper (DN-M1-001, ADR-001)`.

Line 495, replace `Mapper tests use recorded fixture responses from the provider spike.` with
`Mapper tests use synthetic responses shaped like the documented ones: the spike recorded no fixtures (public repo; ADR-001 V6b).`

- [ ] **Step 3: ADR-001, under the V6a table**

After the row that starts `| Required on Place Details? |`, add a blank line and:

```markdown
DN-M1-001: `PlaceDetails.attribution` stays null and no attribution UI is shown until the brand guidance is read
(→ Legal); `/browse` and `/lookup` responses carry no copyright notice of their own in the fields mapped.
```

- [ ] **Step 4: The full check**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL. Then run the import check from DN-TD-001:

```bash
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

Expected: no `UNSORTED` lines, no CLAUDE.md lines printed, and `0` (the key isn't in the diff).

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md docs/03-discover-nearby-engineering-implementation-plan.md docs/adr/0001-poi-provider.md
git commit -m "Record the HERE client in CLAUDE.md, docs/03 and ADR-001"
```

---

## After the tasks

- The final whole-branch review on the most capable model (executing-plans), then the `simplify` skill on the
  `app/` diff.
- PR with `pr-description`: DN-M1-001, its acceptance criteria. Note: not wired yet (DN-M1-002); attribution pending
  brand guidance; no live call made in this ticket.
- Step 6 after merge:
  - verify MERGED in its own call;
  - DN-M1-001 `done`;
  - DN-M1-002 and DN-M1-003 `ready`;
  - NOW.md and BACKLOG.md.
