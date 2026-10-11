# DN-UX-005 Place Details as in the Concept — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> This project runs plans natively (inline), task by task.

**Goal:** Place Details laid out as the Product Lead's concept, with today's closing time, the price, a two-line
description and a "Car park nearby" tile, and a slot kept for Save (DN-UX-006).

**Architecture:** HERE's structured opening hours become domain `OpeningPeriod`s; the ViewModel turns them into
today's `Closing`. A new `PlacesRepository.hasCarParkNear` runs one HERE `/browse` beside the details lookup.
Tripadvisor's price level and description ride in the existing `PlaceEnrichment` from the nearby search the enricher
already makes. The screen is re-laid out to the concept; a fake enricher feeds the `--es scenario` fakes.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), OkHttp + kotlinx.serialization, java.time, JUnit, Robolectric,
Compose UI test.

**Spec:** `docs/superpowers/specs/2026-10-11-place-details-and-saved-design.md` (PR A). Visual reference:
`docs/design/03b-place-details-concept.png`. Evidence: DN-SP-005 (private vault).

## Global Constraints

- Branch `dn-ux-005-place-details-concept`; never commit on `main`; no AI attribution in commits or the PR.
- Commit subject ≤ 72 characters (50 preferred), body wrapped at 72, explaining why.
- Public repo: no real place names, place IDs, coordinates, provider responses or keys in code, tests, docs or commits.
  Tests use made-up names.
- Architecture rules (CLAUDE.md): only `ui/` imports Compose; `model/`, `places/`, `discovery/` have no Compose or Car
  API; provider response models never leave their package; all user text in `strings.xml`.
- Price symbols exactly: `€` (Cheap Eats), `€€–€€€` (Mid Range), `€€€€` (Fine Dining).
- Opening line exactly: "Open · Closes {time}", "Open 24 hours", "Open now", "Closed now"; HERE's weekly text only
  when the status is unknown. Time "HH:mm" on a 24-hour car, "h:mm a" (US) on a 12-hour one.
- Car park: HERE category `800-8500`, radius 150 m, `limit=1`; display only, never ranked.
- Description: Tripadvisor's English description, at most two lines with "…", hidden while
  `DrivingState.distractionOptimizationRequired`.
- No new Tripadvisor calls; Tripadvisor calls cost money past 1,000 a month: develop and check on the fakes.
- Navigate never waits for details (docs/02 §7); Navigate and Back stay at least `MinTouchTarget` (76 dp).
- Before done: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`.

## Review Focus

1. **Hours past midnight:** a place open Saturday 20:00–02:00 is open at 01:00 on Sunday and closes at 02:00
   (Task 1 test `hoursPastMidnightCloseTheNextDay`).
2. **HERE says open, but no period is open now** (clock, time zone or a missing schedule): the line reads "Open now",
   not a wrong closing time (Task 5 test `openWithoutAClosingTimeSaysOpenNow`).
3. **A slow car-park check:** details show as soon as they arrive; the tile comes later or never (Task 3 test
   `aSlowCarParkCheckDoesNotHoldBackTheDetails`).
4. **Driving starts while Details is open:** the description disappears and comes back when parked (Task 5 test
   `theDescriptionHidesWhileDriving`).
5. **Tripadvisor sends a price text we don't know or a description in another language:** no price, no description,
   rather than a wrong one (Task 4 test `anUnknownPriceOrAForeignDescriptionIsLeftOut`).

---

## File map

| File | Change |
| --- | --- |
| `app/src/main/java/.../model/OpeningHours.kt` | **New.** `OpeningPeriod`, `Closing`, `List<OpeningPeriod>.closingAt(now)` |
| `app/src/main/java/.../model/Place.kt` | `PlaceDetails` gains `openingPeriods`, `carParkNearby` |
| `app/src/main/java/.../model/PlaceEnrichment.kt` | `PlaceEnrichment` gains `priceLevel`, `description`; new `PriceLevel` |
| `app/src/main/java/.../places/here/HereModels.kt` | `HereOpeningHours.structured`, new `HereStructuredHours` |
| `app/src/main/java/.../places/here/HereOpeningHours.kt` | **New.** `HereStructuredHours.toPeriod()` |
| `app/src/main/java/.../places/here/HereMapper.kt` | `toDetails()` maps the periods |
| `app/src/main/java/.../places/PlacesRepository.kt` | `hasCarParkNear`, `CAR_PARK_RADIUS_METERS` |
| `app/src/main/java/.../places/here/HerePlacesRepository.kt` | `hasCarParkNear` via `/browse` |
| `app/src/main/java/.../places/fake/FakePlacesRepository.kt` | `hasCarParkNear` for the fakes |
| `app/src/main/java/.../places/fake/FakeEnricher.kt` | **New.** Made-up price and description |
| `app/src/main/java/.../places/tripadvisor/TripadvisorModels.kt`, `TripadvisorEnricher.kt` | Read `price_level`, `descriptions` |
| `app/src/main/java/.../discovery/DiscoverUseCase.kt` | `carParkNear(location)` |
| `app/src/main/java/.../AppContainer.kt` | Forward `hasCarParkNear`; fake enricher for scenarios |
| `app/src/main/java/.../ui/screens/PlaceDetailsViewModel.kt` | `Content.closing`; the car-park call; clock |
| `app/src/main/java/.../ui/screens/PlaceDetailsScreen.kt` | The concept layout |
| `app/src/main/java/.../ui/components/ScreenHeader.kt` | Extract `BackButton` |
| `app/src/main/java/.../ui/PlaceText.kt` | `PriceLevel.label`, `clockTime` |
| `app/src/main/java/.../ui/DiscoverNavHost.kt` | Pass `drivingRestricted` |
| `app/src/main/java/.../ui/theme/Dimens.kt` | Details dimens for the new layout; drop the unused ones |
| `app/src/main/res/values/strings.xml`, `res/drawable/ic_parking.xml` | Copy and the P icon |
| Tests | `OpeningHoursTest`, `HereOpeningHoursTest` (new); `HereMapperTest`, `HerePlacesRepositoryTest`, `FakePlacesRepositoryTest`, `DiscoverUseCaseTest`, `PlaceDetailsViewModelTest`, `TripadvisorEnricherTest`, `FakeEnricherTest` (new), `AppContainerTest`, `ClockTimeTest` (new), `PlaceDetailsScreenTest`, `PlaceDetailsFitTest`; `ScriptedPlaces` |
| Docs | docs/02 §7, docs/03 §8 and §10, ADR-003, `docs/design/README.md`, CLAUDE.md |

`...` = `com/kanyandula/discovernearby`. Test files mirror the main package under `app/src/test/java/...`.

---

### Task 1: Opening periods from HERE's structured hours

**Files:**
- Create: `app/src/main/java/.../model/OpeningHours.kt`
- Create: `app/src/main/java/.../places/here/HereOpeningHours.kt`
- Modify: `app/src/main/java/.../model/Place.kt` (`PlaceDetails`)
- Modify: `app/src/main/java/.../places/here/HereModels.kt` (`HereOpeningHours`)
- Modify: `app/src/main/java/.../places/here/HereMapper.kt` (`toDetails`)
- Test: `app/src/test/java/.../model/OpeningHoursTest.kt` (new), `.../places/here/HereOpeningHoursTest.kt` (new),
  `.../places/here/HereMapperTest.kt`

**Interfaces:**
- Produces: `data class OpeningPeriod(days: Set<DayOfWeek>, start: LocalTime, duration: Duration)`;
  `sealed interface Closing { data class At(time: LocalTime); data object AllDay }`;
  `fun List<OpeningPeriod>.closingAt(now: LocalDateTime): Closing?`;
  `PlaceDetails(summary, openingSummary, openingPeriods: List<OpeningPeriod> = emptyList(), carParkNearby: Boolean? = null)`.

HERE's shape, confirmed live on 2026-10-11:
`"structured": [{"start": "T070000", "duration": "PT09H00M", "recurrence": "FREQ:DAILY;BYDAY:MO,TU,WE,TH,FR"}]`.

- [ ] **Step 1: Write the failing model test** — `app/src/test/java/.../model/OpeningHoursTest.kt`

```kotlin
package com.kanyandula.discovernearby.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class OpeningHoursTest {

    private val weekdays = OpeningPeriod(
        setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), LocalTime.of(7, 0), Duration.ofHours(9),
    )

    // October 2026: the 10th is a Saturday, the 11th a Sunday, the 12th a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test
    fun anOpenPeriodGivesItsClosingTime() {
        assertEquals(Closing.At(LocalTime.of(16, 0)), listOf(weekdays).closingAt(at(12, 10)))
    }

    @Test
    fun beforeOpeningOrFromClosingThereIsNone() {
        assertNull(listOf(weekdays).closingAt(at(12, 6, 59)))
        assertNull(listOf(weekdays).closingAt(at(12, 16)))
    }

    @Test
    fun aDayNotListedHasNone() {
        assertNull(listOf(weekdays).closingAt(at(11, 10)))
    }

    // Review Focus 1
    @Test
    fun hoursPastMidnightCloseTheNextDay() {
        val late = OpeningPeriod(setOf(SATURDAY), LocalTime.of(20, 0), Duration.ofHours(6))
        assertEquals(Closing.At(LocalTime.of(2, 0)), listOf(late).closingAt(at(11, 1)))
        assertNull(listOf(late).closingAt(at(11, 2)))
    }

    @Test
    fun aWholeDayIsAllDay() {
        val always = OpeningPeriod(DayOfWeek.entries.toSet(), LocalTime.MIDNIGHT, Duration.ofHours(24))
        assertEquals(Closing.AllDay, listOf(always).closingAt(at(12, 3)))
    }

    // Split hours, or one entry per HERE category: the period open now decides; overlapping ones close at the latest.
    @Test
    fun thePeriodOpenNowDecides() {
        val lunch = OpeningPeriod(setOf(MONDAY), LocalTime.of(12, 0), Duration.ofHours(3))
        val dinner = OpeningPeriod(setOf(MONDAY), LocalTime.of(18, 0), Duration.ofHours(4))
        assertEquals(Closing.At(LocalTime.of(22, 0)), listOf(lunch, dinner).closingAt(at(12, 19)))
        assertEquals(Closing.At(LocalTime.of(16, 0)), listOf(lunch, weekdays).closingAt(at(12, 13)))
    }

    @Test
    fun noPeriodsHaveNone() {
        assertNull(emptyList<OpeningPeriod>().closingAt(at(12, 10)))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew testDebugUnitTest --tests '*OpeningHoursTest'`
Expected: compilation fails (`OpeningPeriod` unresolved).

- [ ] **Step 3: Write the model** — `app/src/main/java/.../model/OpeningHours.kt`

```kotlin
package com.kanyandula.discovernearby.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** A weekly opening period: it starts at [start] on each of [days] and lasts [duration], possibly past midnight. */
data class OpeningPeriod(val days: Set<DayOfWeek>, val start: LocalTime, val duration: Duration)

/** When an open place closes today, as the opening line says it (DN-UX-005). */
sealed interface Closing {
    data class At(val time: LocalTime) : Closing
    data object AllDay : Closing
}

private val WHOLE_DAY: Duration = Duration.ofHours(24)

/**
 * How the periods open at [now] end, or null when none is open: [Closing.AllDay] when one lasts a whole day,
 * otherwise the latest closing among them. Yesterday's periods count, for hours that run past midnight.
 */
fun List<OpeningPeriod>.closingAt(now: LocalDateTime): Closing? {
    val today = now.toLocalDate()
    val open = flatMap { period ->
        listOf(today.minusDays(1), today)
            .filter { it.dayOfWeek in period.days }
            .map { day -> period to day.atTime(period.start) }
    }.filter { (period, opens) -> !now.isBefore(opens) && now.isBefore(opens + period.duration) }
    return when {
        open.isEmpty() -> null
        open.any { (period, _) -> period.duration >= WHOLE_DAY } -> Closing.AllDay
        else -> Closing.At(open.maxOf { (period, opens) -> opens + period.duration }.toLocalTime())
    }
}
```

- [ ] **Step 4: Run the model test**

Run: `./gradlew testDebugUnitTest --tests '*OpeningHoursTest'`
Expected: PASS (7 tests).

- [ ] **Step 5: Write the failing HERE parsing test** — `app/src/test/java/.../places/here/HereOpeningHoursTest.kt`

```kotlin
package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.model.OpeningPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.LocalTime

// HERE's structured hours, in the shape a live /browse returned on 2026-10-11 (values invented).
class HereOpeningHoursTest {

    @Test
    fun heresShapeBecomesAPeriod() {
        val hours = HereStructuredHours("T070000", "PT09H00M", "FREQ:DAILY;BYDAY:MO,TU,WE,TH,FR")
        assertEquals(
            OpeningPeriod(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), LocalTime.of(7, 0), Duration.ofHours(9)),
            hours.toPeriod(),
        )
    }

    @Test
    fun minutesCount() {
        val hours = HereStructuredHours("T103000", "PT05H30M", "FREQ:DAILY;BYDAY:SU")
        assertEquals(
            OpeningPeriod(setOf(SUNDAY), LocalTime.of(10, 30), Duration.ofMinutes(330)),
            hours.toPeriod(),
        )
    }

    // An unreadable part drops that period only; the rest of the schedule still counts.
    @Test
    fun anUnreadablePartDropsThePeriod() {
        assertNull(HereStructuredHours("T070000", "PT09H00M", "FREQ:DAILY").toPeriod())
        assertNull(HereStructuredHours("T7", "PT09H00M", "FREQ:DAILY;BYDAY:MO").toPeriod())
        assertNull(HereStructuredHours("T070000", "9 hours", "FREQ:DAILY;BYDAY:MO").toPeriod())
        assertNull(HereStructuredHours("T070000", "PT09H00M", "FREQ:DAILY;BYDAY:XX").toPeriod())
        assertNull(HereStructuredHours().toPeriod())
    }
}
```

- [ ] **Step 6: Run it to see it fail**

Run: `./gradlew testDebugUnitTest --tests '*HereOpeningHoursTest'`
Expected: compilation fails (`HereStructuredHours` unresolved).

- [ ] **Step 7: Add the HERE model and parser**

In `HereModels.kt`, replace `HereOpeningHours` and add `HereStructuredHours`:

```kotlin
@Serializable
internal data class HereOpeningHours(
    val text: List<String> = emptyList(),
    val isOpen: Boolean? = null,
    val structured: List<HereStructuredHours> = emptyList(),
)

/** One weekly period: `start` "T070000", `duration` "PT09H00M", `recurrence` "FREQ:DAILY;BYDAY:MO,TU". */
@Serializable
internal data class HereStructuredHours(
    val start: String? = null,
    val duration: String? = null,
    val recurrence: String? = null,
)
```

Create `app/src/main/java/.../places/here/HereOpeningHours.kt`:

```kotlin
package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.model.OpeningPeriod
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val BY_DAY = Regex("""BYDAY:([A-Z,]+)""")
private val START = DateTimeFormatter.ofPattern("'T'HHmmss")
private val DAYS = mapOf(
    "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
    "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
)

/** HERE's structured period as a domain one, or null when any part is unreadable: that period is left out. */
internal fun HereStructuredHours.toPeriod(): OpeningPeriod? {
    val codes = BY_DAY.find(recurrence.orEmpty())?.groupValues?.get(1)?.split(',').orEmpty()
    val days = codes.mapNotNull { DAYS[it] }.toSet()
    if (days.isEmpty() || days.size != codes.size) return null
    return try {
        OpeningPeriod(days, LocalTime.parse(start.orEmpty(), START), Duration.parse(duration.orEmpty()))
    } catch (ignored: DateTimeParseException) {
        null
    }
}
```

- [ ] **Step 8: Run the parsing test**

Run: `./gradlew testDebugUnitTest --tests '*HereOpeningHoursTest'`
Expected: PASS (3 tests).

- [ ] **Step 9: Carry the periods in `PlaceDetails`** — failing mapper test first. Append to `HereMapperTest`:

```kotlin
    // DN-UX-005: the weekly schedule, for today's closing time; every HERE hours entry counts.
    @Test
    fun detailsCarryTheStructuredSchedule() {
        val details = checkNotNull(item("""{"id": "x", "title": "A", "position": {"lat": 1.0, "lng": 2.0},
            "openingHours": [
              {"text": ["Mon: 07:00 - 16:00"], "isOpen": true,
               "structured": [{"start": "T070000", "duration": "PT09H00M", "recurrence": "FREQ:DAILY;BYDAY:MO"}]},
              {"text": ["Sun: 10:00 - 12:00"],
               "structured": [{"start": "T100000", "duration": "PT02H00M", "recurrence": "FREQ:DAILY;BYDAY:SU"},
                              {"start": "bad"}]}]}""").toDetails())
        assertEquals(
            listOf(
                OpeningPeriod(setOf(DayOfWeek.MONDAY), LocalTime.of(7, 0), Duration.ofHours(9)),
                OpeningPeriod(setOf(DayOfWeek.SUNDAY), LocalTime.of(10, 0), Duration.ofHours(2)),
            ),
            details.openingPeriods,
        )
        assertNull(details.carParkNearby) // a separate call (Task 2)
    }
```

Add imports: `com.kanyandula.discovernearby.model.OpeningPeriod`, `java.time.DayOfWeek`, `java.time.Duration`,
`java.time.LocalTime`.

Run: `./gradlew testDebugUnitTest --tests '*HereMapperTest'` — expected: compilation fails (`openingPeriods`).

- [ ] **Step 10: Extend `PlaceDetails` and the mapper**

In `model/Place.kt`, replace `PlaceDetails`:

```kotlin
/** Richer fields, fetched only when a place is opened. */
data class PlaceDetails(
    val summary: PlaceSummary,
    val openingSummary: String?,
    val openingPeriods: List<OpeningPeriod> = emptyList(), // the weekly schedule, for today's closing time
    val carParkNearby: Boolean? = null, // a car park within CAR_PARK_RADIUS_METERS (DN-UX-005); null = unknown
)
```

In `HereMapper.kt`, replace `toDetails`:

```kotlin
/** Details add the opening hours, as text and as periods; the copyright notice comes with the summary. */
internal fun HereItem.toDetails(): PlaceDetails? = toSummary()?.let { summary ->
    PlaceDetails(
        summary = summary,
        openingSummary = openingHours.flatMap { it.text }.joinToString("; ").ifEmpty { null },
        openingPeriods = openingHours.flatMap { it.structured }.mapNotNull { it.toPeriod() },
    )
}
```

The `CAR_PARK_RADIUS_METERS` in the comment is defined in Task 2.

- [ ] **Step 11: Run the HERE tests**

Run: `./gradlew testDebugUnitTest --tests '*places.here*' --tests '*OpeningHoursTest'`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/model/OpeningHours.kt \
  app/src/main/java/com/kanyandula/discovernearby/model/Place.kt \
  app/src/main/java/com/kanyandula/discovernearby/places/here/ \
  app/src/test/java/com/kanyandula/discovernearby/model/OpeningHoursTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/here/
git commit -m "Read HERE's structured opening hours for the closing time"
```

Body: Place Details will say when an open place closes (DN-UX-005). HERE's structured hours give the weekly periods;
`closingAt` finds the one open now, including hours that run past midnight.

---

### Task 2: The car-park check

**Files:**
- Modify: `app/src/main/java/.../places/PlacesRepository.kt`, `.../places/here/HerePlacesRepository.kt`,
  `.../places/fake/FakePlacesRepository.kt`, `.../discovery/DiscoverUseCase.kt`, `.../AppContainer.kt`
- Modify: `app/src/test/java/.../places/ScriptedPlaces.kt`
- Test: `.../places/here/HerePlacesRepositoryTest.kt`, `.../places/fake/FakePlacesRepositoryTest.kt`,
  `.../discovery/DiscoverUseCaseTest.kt`

**Interfaces:**
- Produces: `const val CAR_PARK_RADIUS_METERS = 150`; `PlacesRepository.hasCarParkNear(location: GeoPoint): Boolean`
  (throws `PlacesException` like the others); `DiscoverUseCase.carParkNear(location: GeoPoint): Boolean?` (null on
  failure or timeout); `ScriptedPlaces.carPark: suspend (GeoPoint) -> Boolean` (default throws `ProviderFailure`).

- [ ] **Step 1: Write the failing HERE test.** Append to `HerePlacesRepositoryTest`:

```kotlin
    // DN-UX-005: one /browse for HERE's parking category around the place; any item means a car park nearby.
    @Test
    fun aCarParkCheckAsksForParkingWithin150m() = runBlocking {
        assertTrue(repository.hasCarParkNear(GREYSTONES))
        val url = requests.single().url
        assertEquals("browse.search.hereapi.com", url.host)
        assertEquals("53.144000,-6.063300", url.queryParameter("at"))
        assertEquals("circle:53.144000,-6.063300;r=150", url.queryParameter("in"))
        assertEquals("800-8500", url.queryParameter("categories"))
        assertEquals("1", url.queryParameter("limit"))
    }

    @Test
    fun noItemsMeansNoCarPark() = runBlocking {
        respond = { reply(it, 200, """{"items": []}""") }
        assertFalse(repository.hasCarParkNear(GREYSTONES))
    }

    @Test
    fun aFailedCarParkCheckIsAProviderFailure() {
        respond = { reply(it, 500, "{}") }
        assertTrue(failure { repository.hasCarParkNear(GREYSTONES) } is ProviderFailure)
    }
```

Add `org.junit.Assert.assertFalse` to the imports if missing.

Run: `./gradlew testDebugUnitTest --tests '*HerePlacesRepositoryTest'` — expected: compilation fails.

- [ ] **Step 2: Add the interface method and the HERE implementation**

In `PlacesRepository.kt`, add above the interface and inside it:

```kotlin
/** How far from a place a car park counts as nearby (DN-UX-005; DN-SP-005 measured 117 of 173 places). */
const val CAR_PARK_RADIUS_METERS = 150
```

```kotlin
    /** Whether a car park lies within [CAR_PARK_RADIUS_METERS] of [location]: a fact about the area, display only. */
    suspend fun hasCarParkNear(location: GeoPoint): Boolean
```

In `HerePlacesRepository.kt`, add the constant under `LOOKUP_URL` and the override after `getPlaceDetails`:

```kotlin
// HERE's parking facilities: garages, lots, park-and-ride (Places category system, ADR-001 [H3]).
private const val CAR_PARK_CATEGORY = "800-8500"
```

```kotlin
    override suspend fun hasCarParkNear(location: GeoPoint): Boolean {
        val url = BROWSE_URL.newBuilder()
            .addQueryParameter("at", location.latLng())
            .addQueryParameter("in", "circle:${location.latLng()};r=$CAR_PARK_RADIUS_METERS")
            .addQueryParameter("categories", CAR_PARK_CATEGORY)
            .addQueryParameter("limit", "1")
            .build()
        return get<HereBrowseResponse>(url).items.isNotEmpty()
    }
```

Also change the class KDoc's "Nothing is cached or stored (ADR-001 V6b)." to "Search results are not stored (ADR-004)."

- [ ] **Step 3: Implement it everywhere else `PlacesRepository` is implemented**

`FakePlacesRepository` (after `getPlaceDetails`):

```kotlin
    // A car park near every fake place, so the tile shows in the scenarios; none where the fakes drop optional data.
    override suspend fun hasCarParkNear(location: GeoPoint): Boolean {
        if (scenario == FakeScenario.DETAILS_FAILURE) throw ProviderFailure("Details unavailable")
        return scenario != FakeScenario.NULL_HEAVY
    }
```

`AppContainer.selectedPlaces` (add the override):

```kotlin
        override suspend fun hasCarParkNear(location: GeoPoint) = placesRepository.hasCarParkNear(location)
```

`ScriptedPlaces` (test helper; add after `details`):

```kotlin
    // Fails by default, so a test that doesn't care sees no tile and no extra state update.
    var carPark: suspend (GeoPoint) -> Boolean = { throw ProviderFailure() }

    override suspend fun hasCarParkNear(location: GeoPoint): Boolean = carPark(location)
```

(import `com.kanyandula.discovernearby.places.ProviderFailure` is same-package; no import needed.)

- [ ] **Step 4: Write the failing use-case and fake tests.** Append to `DiscoverUseCaseTest`:

```kotlin
    @Test
    fun aCarParkCheckComesFromTheProvider() = runTest {
        assertEquals(true, useCase().carParkNear(ORIGIN))
    }

    // Like details: a failed or slow check is unknown, and the screen shows no tile.
    @Test
    fun aFailedOrSlowCarParkCheckIsNull() = runTest {
        assertNull(withScenario(FakeScenario.DETAILS_FAILURE).carParkNear(ORIGIN))
        val slow = ScriptedPlaces().apply {
            carPark = {
                delay(PROVIDER_TIMEOUT_MILLIS + 1)
                true
            }
        }
        assertNull(useCase(slow).carParkNear(ORIGIN))
    }
```

Append to `FakePlacesRepositoryTest`:

```kotlin
    @Test
    fun fakesHaveACarParkNearbyExceptWhenNullHeavy() = runTest {
        assertTrue(FakePlacesRepository().hasCarParkNear(ORIGIN))
        assertFalse(FakePlacesRepository(FakeScenario.NULL_HEAVY).hasCarParkNear(ORIGIN))
    }
```

Use the imports the files already have for `runTest`, `ORIGIN`, `ScriptedPlaces`, `delay`; add any missing
(`com.kanyandula.discovernearby.discovery.ORIGIN`, `kotlinx.coroutines.delay`, `org.junit.Assert.assertFalse`).

Run: `./gradlew testDebugUnitTest --tests '*DiscoverUseCaseTest' --tests '*FakePlacesRepositoryTest'` — expected:
compilation fails (`carParkNear`).

- [ ] **Step 5: Add `carParkNear` to the use case** (after `details`):

```kotlin
    /** Whether a car park lies near [location], or null when the check fails or times out: then no tile shows. */
    suspend fun carParkNear(location: GeoPoint): Boolean? = try {
        withTimeoutOrNull(timeoutMillis) { places.hasCarParkNear(location) }
    } catch (ignored: PlacesException) {
        null
    }
```

Import `com.kanyandula.discovernearby.model.GeoPoint`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew testDebugUnitTest --tests '*HerePlacesRepositoryTest' --tests '*DiscoverUseCaseTest' --tests '*FakePlacesRepositoryTest' --tests '*AppContainerTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/places/ \
  app/src/main/java/com/kanyandula/discovernearby/discovery/DiscoverUseCase.kt \
  app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/ \
  app/src/test/java/com/kanyandula/discovernearby/discovery/DiscoverUseCaseTest.kt
git commit -m "Check HERE for a car park within 150 m of a place"
```

Body: no source has the place's own parking (DN-SP-005), but HERE has a car park within 150 m of 117 of 173 benchmark
places. Details shows it as "Car park nearby"; it never feeds ranking.

---

### Task 3: Place Details ViewModel — closing time and the car park

**Files:**
- Modify: `app/src/main/java/.../ui/screens/PlaceDetailsViewModel.kt`
- Test: `app/src/test/java/.../ui/screens/PlaceDetailsViewModelTest.kt`

**Interfaces:**
- Consumes: `closingAt` (Task 1), `DiscoverUseCase.carParkNear` (Task 2), `ScriptedPlaces.carPark`.
- Produces: `PlaceDetailsUiState.Content(details: PlaceDetails, closing: Closing? = null)`;
  `PlaceDetailsViewModel(place, discover, navigation, now: () -> LocalDateTime = LocalDateTime::now)`.

- [ ] **Step 1: Write the failing tests.** In `PlaceDetailsViewModelTest`, change `viewModel()` to take the clock and
  append the tests:

```kotlin
    private val monday10am = LocalDateTime.of(2026, 10, 12, 10, 0)

    private fun viewModel() = PlaceDetailsViewModel(
        place = place,
        discover = DiscoverUseCase(places, FakeLocationProvider(), BasicRecommendationEngine()),
        navigation = launcher,
        now = { monday10am },
    )
```

```kotlin
    // DN-UX-005: today's closing time comes from the schedule, at the time the details arrive.
    @Test
    fun theClosingTimeComesWithTheDetails() = runTest {
        val schedule = OpeningPeriod(setOf(DayOfWeek.MONDAY), LocalTime.of(7, 0), Duration.ofHours(11))
        places.details = { loaded.copy(openingPeriods = listOf(schedule)) }
        val vm = viewModel()
        runCurrent()
        assertEquals(Closing.At(LocalTime.of(18, 0)), (vm.uiState.value as Content).closing)
    }

    @Test
    fun aCarParkNearbyIsAddedToTheDetails() = runTest {
        places.details = { loaded }
        places.carPark = { true }
        val vm = viewModel()
        runCurrent()
        assertEquals(Content(loaded.copy(carParkNearby = true)), vm.uiState.value)
    }

    // Review Focus 3: the details show as they arrive; the tile follows.
    @Test
    fun aSlowCarParkCheckDoesNotHoldBackTheDetails() = runTest {
        places.details = { loaded }
        places.carPark = {
            delay(5_000)
            true
        }
        val vm = viewModel()
        runCurrent()
        assertEquals(Content(loaded), vm.uiState.value)
        advanceTimeBy(5_001)
        assertEquals(Content(loaded.copy(carParkNearby = true)), vm.uiState.value)
    }

    // The fallback keeps the summary; a car park found anyway is not shown there.
    @Test
    fun theCarParkNeedsTheDetails() = runTest {
        places.details = { throw ProviderFailure() }
        places.carPark = { true }
        val vm = viewModel()
        runCurrent()
        assertEquals(SummaryOnly(place), vm.uiState.value)
    }
```

Imports to add: `com.kanyandula.discovernearby.model.Closing`, `com.kanyandula.discovernearby.model.OpeningPeriod`,
`java.time.DayOfWeek`, `java.time.Duration`, `java.time.LocalDateTime`, `java.time.LocalTime`.

Run: `./gradlew testDebugUnitTest --tests '*PlaceDetailsViewModelTest'` — expected: compilation fails (`now`,
`closing`).

- [ ] **Step 2: Implement.** In `PlaceDetailsViewModel.kt`, change `Content` and the constructor and `init`:

```kotlin
    /** [closing]: when the place closes today, from the details' schedule at the time they arrived (DN-UX-005). */
    data class Content(val details: PlaceDetails, val closing: Closing? = null) : PlaceDetailsUiState {
        override val summary: PlaceSummary get() = details.summary
    }
```

```kotlin
class PlaceDetailsViewModel(
    private val place: PlaceSummary,
    discover: DiscoverUseCase,
    private val navigation: NavigationLauncher,
    now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    private val state = MutableStateFlow<PlaceDetailsUiState>(PlaceDetailsUiState.Loading(place))
    val uiState: StateFlow<PlaceDetailsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            // Its own call beside the details, so neither waits for the other (DN-UX-005).
            val carPark = async { discover.carParkNear(place.location) }
            val loaded = discover.details(place.id)
                ?.let { PlaceDetailsUiState.Content(it, it.openingPeriods.closingAt(now())) }
                ?: PlaceDetailsUiState.SummaryOnly(place)
            // A failed hand-off stays on screen: the driver asked to navigate, not to read the details.
            state.update { if (it is PlaceDetailsUiState.NavigationUnavailable) it else loaded }
            val nearby = carPark.await() ?: return@launch
            state.update { current ->
                if (current !is PlaceDetailsUiState.Content) return@update current
                current.copy(details = current.details.copy(carParkNearby = nearby))
            }
        }
    }
```

Imports: `com.kanyandula.discovernearby.model.Closing`, `com.kanyandula.discovernearby.model.closingAt`,
`kotlinx.coroutines.async`, `java.time.LocalDateTime`. `navigate()` is unchanged.

- [ ] **Step 3: Run the tests**

Run: `./gradlew testDebugUnitTest --tests '*PlaceDetailsViewModelTest'`
Expected: PASS (the six existing tests unchanged: `ScriptedPlaces.carPark` fails by default, so no update follows).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModel.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModelTest.kt
git commit -m "Give Place Details today's closing time and the car park"
```

---

### Task 4: Tripadvisor's price and description, and made-up ones for the fakes

**Files:**
- Modify: `app/src/main/java/.../model/PlaceEnrichment.kt`
- Modify: `app/src/main/java/.../places/tripadvisor/TripadvisorModels.kt`, `.../TripadvisorEnricher.kt`
- Create: `app/src/main/java/.../places/fake/FakeEnricher.kt`
- Modify: `app/src/main/java/.../AppContainer.kt` (`placeEnricher`)
- Test: `.../places/tripadvisor/TripadvisorEnricherTest.kt`, `.../places/fake/FakeEnricherTest.kt` (new),
  `.../AppContainerTest.kt`

**Interfaces:**
- Produces: `@Serializable enum class PriceLevel { CHEAP, MID, FINE }`;
  `PlaceEnrichment(photoUrl: String? = null, rating: ProviderRating? = null, priceLevel: PriceLevel? = null, description: String? = null)`;
  `object FakeEnricher : PlaceEnricher`.

- [ ] **Step 1: Write the failing enricher tests.** In `TripadvisorEnricherTest`, extend the matched location in
  `NEARBY` (location 22) with a price and descriptions, and append the tests:

```kotlin
private const val NEARBY = """{"data": [
  {"location": {"id": 11, "names": [{"value": "Harbour Lane Deli", "primary": true}]}},
  {"location": {"id": 22, "names": [{"value": "Harbour Coffee Seaview", "primary": true}],
    "price_level": "Mid Range",
    "descriptions": [{"language": "fr", "value": "Un café."}, {"language": "en", "value": "A café by the harbour."}],
    "traveler_ratings": {"overall": {"rating": 4.5, "count": 312, "icon_url": "https://example.test/bubbles-4.5.png"}}}}
]}"""
```

Update `theMatchedLocationGivesItsRatingAndFirstPhoto`'s expected value to include
`priceLevel = PriceLevel.MID, description = "A café by the harbour."`.

```kotlin
    // DN-UX-005: price and description come from the nearby search already made; no new call.
    @Test
    fun priceAndDescriptionComeWithoutAnotherCall() = runBlocking {
        val enrichment = enricher.enrich(cafe, COFFEE)
        assertEquals(PriceLevel.MID, enrichment?.priceLevel)
        assertEquals("A café by the harbour.", enrichment?.description)
        assertEquals(2, requests.size) // the nearby search and the photo, as before
    }

    @Test
    fun eachPriceLevelMaps() {
        assertEquals(PriceLevel.CHEAP, priceLevel("Cheap Eats"))
        assertEquals(PriceLevel.MID, priceLevel("Mid Range"))
        assertEquals(PriceLevel.FINE, priceLevel("Fine Dining"))
    }

    // Review Focus 5: unknown or foreign text is left out, not guessed.
    @Test
    fun anUnknownPriceOrAForeignDescriptionIsLeftOut() = runBlocking {
        assertNull(priceLevel("$$ - $$$"))
        assertNull(priceLevel(null))
        respond = { request ->
            val nearby = NEARBY.replace("\"Mid Range\"", "\"Pricey\"").replace(
                """{"language": "en", "value": "A café by the harbour."}""",
                """{"language": "de", "value": "Ein Café."}""",
            )
            reply(request, 200, if (request.url.encodedPath.endsWith("/photos")) PHOTOS else nearby)
        }
        val enrichment = enricher.enrich(cafe, COFFEE)
        assertNull(enrichment?.priceLevel)
        assertNull(enrichment?.description)
    }
```

Imports: `com.kanyandula.discovernearby.model.PriceLevel`.

Run: `./gradlew testDebugUnitTest --tests '*TripadvisorEnricherTest'` — expected: compilation fails.

- [ ] **Step 2: Extend the model.** Replace `model/PlaceEnrichment.kt`'s `PlaceEnrichment` and KDoc, and add
  `PriceLevel`:

```kotlin
/**
 * Tripadvisor's photo, rating, price level and description for a place on screen (DN-UX-004, DN-UX-005, ADR-003), for
 * display only: ranking never sees it. Saved places keep it on the device (ADR-004).
 */
@Serializable
data class PlaceEnrichment(
    val photoUrl: String? = null,
    val rating: ProviderRating? = null,
    val priceLevel: PriceLevel? = null,
    val description: String? = null,
)

/** Tripadvisor's restaurant price levels: Cheap Eats, Mid Range, Fine Dining. */
@Serializable
enum class PriceLevel { CHEAP, MID, FINE }
```

- [ ] **Step 3: Read the fields.** In `TripadvisorModels.kt`, extend `TripadvisorLocation` and add the description
  type:

```kotlin
@Serializable
internal data class TripadvisorLocation(
    val id: Long? = null,
    val names: List<LocationName> = emptyList(),
    @SerialName("traveler_ratings") val travelerRatings: TravelerRatings? = null,
    @SerialName("price_level") val priceLevel: String? = null,
    val descriptions: List<LocationDescription> = emptyList(),
)

@Serializable
internal data class LocationDescription(val language: String = "", val value: String = "")
```

In `TripadvisorEnricher.kt`, replace the last line of `enrich` and add the mappers below the class:

```kotlin
        val priceLevel = priceLevel(location.priceLevel)
        val description = location.descriptions.firstOrNull { it.language.startsWith("en") }?.value?.trim()
            ?.takeIf { it.isNotEmpty() }
        return if (photoUrl == null && rating == null && priceLevel == null && description == null) {
            null
        } else {
            PlaceEnrichment(photoUrl, rating, priceLevel, description)
        }
```

```kotlin
// Tripadvisor's words for its levels (Location Details, `price_level`, restaurants only); anything else is unknown.
internal fun priceLevel(text: String?): PriceLevel? = when (text) {
    "Cheap Eats" -> PriceLevel.CHEAP
    "Mid Range" -> PriceLevel.MID
    "Fine Dining" -> PriceLevel.FINE
    else -> null
}
```

Update the class KDoc first sentence to: "Tripadvisor (Terra) photo, rating, price level and description for a HERE
place (DN-UX-004, DN-UX-005, ADR-003): …" and "nothing is retried" stays; drop "or stored".

- [ ] **Step 4: Run the enricher tests**

Run: `./gradlew testDebugUnitTest --tests '*TripadvisorEnricherTest'`
Expected: PASS.

- [ ] **Step 5: Write the failing fake-enricher and container tests.** Create
  `app/src/test/java/.../places/fake/FakeEnricherTest.kt`:

```kotlin
package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.PriceLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FakeEnricherTest {

    // Made-up data for the scenario fakes, by the template name before the town: no photo, no rating, no call.
    @Test
    fun aKnownFakePlaceGetsAPriceAndDescription() = runTest {
        val enrichment = FakeEnricher.enrich(testPlace("The Daily Grind, Greystones", "coffee_shop"), COFFEE)
        assertEquals(PriceLevel.CHEAP, enrichment?.priceLevel)
        assertNotNull(enrichment?.description)
        assertNull(enrichment?.photoUrl)
        assertNull(enrichment?.rating)
    }

    @Test
    fun otherPlacesGetNothing() = runTest {
        assertNull(FakeEnricher.enrich(testPlace("Brew & Bloom, Greystones", "cafe"), COFFEE))
    }
}
```

Append to `AppContainerTest`:

```kotlin
    // DN-UX-005: a named scenario gets made-up prices and descriptions; keyless fakes (CI, Robolectric) get nothing.
    @Test
    fun onlyANamedScenarioGetsTheFakeEnrichment() = runTest {
        val place = testPlace("The Daily Grind, Greystones", "coffee_shop")
        assertNull(AppContainer(app, hereApiKey = "").placeEnricher.enrich(place, DiscoveryCategory.COFFEE))
        val scenario = AppContainer(app, hereApiKey = "").apply { useFakeScenario("NORMAL") }
        assertEquals(PriceLevel.CHEAP, scenario.placeEnricher.enrich(place, DiscoveryCategory.COFFEE)?.priceLevel)
    }
```

Imports: `com.kanyandula.discovernearby.discovery.DiscoveryCategory`, `com.kanyandula.discovernearby.discovery.testPlace`,
`com.kanyandula.discovernearby.model.PriceLevel`, `kotlinx.coroutines.test.runTest`, `org.junit.Assert.assertEquals`,
`org.junit.Assert.assertNull`.

Run: `./gradlew testDebugUnitTest --tests '*FakeEnricherTest' --tests '*AppContainerTest'` — expected: compilation
fails (`FakeEnricher`).

- [ ] **Step 6: Write the fake enricher** — `app/src/main/java/.../places/fake/FakeEnricher.kt`

```kotlin
package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.PriceLevel
import com.kanyandula.discovernearby.places.PlaceEnricher

/**
 * Made-up Tripadvisor-style prices and descriptions for some fake places (DN-UX-005), so Place Details is built and
 * checked on the `--es scenario` fakes without paid calls. No photo or rating: those need the network.
 */
object FakeEnricher : PlaceEnricher {

    private val byTemplate = mapOf(
        "The Daily Grind" to PlaceEnrichment(
            priceLevel = PriceLevel.CHEAP,
            description = "A specialty coffee shop with locally roasted beans, light bites and a relaxed atmosphere.",
        ),
        "Harbour Roasters" to PlaceEnrichment(
            priceLevel = PriceLevel.MID,
            description = "Small-batch roastery by the harbour.",
        ),
        // Long on purpose: it must stop at two lines.
        "Seaview Kitchen" to PlaceEnrichment(
            priceLevel = PriceLevel.FINE,
            description = "Seasonal plates from local growers and the morning's catch, served in a bright room " +
                "over the bay, with a short wine list, children's portions and a garden terrace open from May " +
                "to September when the weather allows.",
        ),
        "Little Acres Farm" to PlaceEnrichment(
            description = "A working farm where children can feed the animals and play in the hay barn.",
        ),
    )

    // Fake names are "{template}, {town}" (fakePlaces).
    override suspend fun enrich(place: PlaceSummary, category: DiscoveryCategory): PlaceEnrichment? =
        byTemplate[place.name.substringBefore(", ")]
}
```

In `AppContainer`, replace `placeEnricher`:

```kotlin
    /**
     * Tripadvisor's data for live HERE places when its key is set (DN-UX-004, ADR-003). A named scenario gets made-up
     * prices and descriptions (DN-UX-005); keyless fakes (CI, Robolectric) get nothing, so no test calls Tripadvisor.
     * Decided per call, like the places source.
     */
    val placeEnricher = PlaceEnricher { place, category ->
        when {
            fakesRequested -> FakeEnricher.enrich(place, category)
            servesFakes || tripadvisorApiKey.isBlank() -> null
            else -> tripadvisor.enrich(place, category)
        }
    }
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew testDebugUnitTest --tests '*FakeEnricherTest' --tests '*AppContainerTest' --tests '*TripadvisorEnricherTest' --tests '*ScenarioExtraTest' --tests '*DiscoverNavigationTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/model/PlaceEnrichment.kt \
  app/src/main/java/com/kanyandula/discovernearby/places/tripadvisor/ \
  app/src/main/java/com/kanyandula/discovernearby/places/fake/FakeEnricher.kt \
  app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/ \
  app/src/test/java/com/kanyandula/discovernearby/AppContainerTest.kt
git commit -m "Read Tripadvisor's price level and description"
```

Body: both come in the nearby search the enricher already makes, so they cost no call. The scenario fakes get made-up
ones, so Place Details can be built without paid calls.

---

### Task 5: The concept layout

**Files:**
- Modify: `app/src/main/java/.../ui/screens/PlaceDetailsScreen.kt` (rewrite of the content layout)
- Modify: `app/src/main/java/.../ui/components/ScreenHeader.kt` (extract `BackButton`)
- Modify: `app/src/main/java/.../ui/PlaceText.kt` (`PriceLevel.label`, `clockTime`)
- Modify: `app/src/main/java/.../ui/DiscoverNavHost.kt` (`drivingRestricted`)
- Modify: `app/src/main/java/.../ui/theme/Dimens.kt`
- Modify: `app/src/main/res/values/strings.xml`; Create: `app/src/main/res/drawable/ic_parking.xml`
- Test: `.../ui/ClockTimeTest.kt` (new), `.../ui/screens/PlaceDetailsScreenTest.kt`,
  `.../ui/screens/PlaceDetailsFitTest.kt`

**Interfaces:**
- Consumes: `Content.closing` (Task 3), `PlaceDetails.carParkNearby` (Task 2), `PlaceEnrichment.priceLevel` and
  `.description` (Task 4).
- Produces: `PlaceDetailsScreen(distanceMeters, category, state, onNavigate, onBack, modifier, enrichment, drivingRestricted: Boolean = false)`;
  `@Composable fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier)` (DN-UX-006's Saved header reuses it);
  `internal fun clockTime(time: LocalTime, is24Hour: Boolean): String`.

**Layout** (worst case on the 4:3 panel, 468 dp tall; tested 840 dp wide, the panel less a 72 dp rail and a 16 dp gap
for DN-UX-006):

```
┌────────────────────────────────────────────────────────────────────┐
│ [←]  ┌──────────────┐  Name                          headlineSmall │  top row ≈ 186
│      │ photo 240×150│  [Tripadvisor chip] or 4.6 ★ (342 reviews)   │
│      └──────────────┘  Café · €€ · 2.1 km away       titleMedium   │
│      Photo: Tripadvisor Open · Closes 18:00 (green)  titleMedium   │
│                        ⓘ More details unavailable right now        │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐                │  tiles 72
│ │  (P)     │ │          │ │          │ │          │                │
│ │ Parking  │ │ Toilets  │ │  Café    │ │Playground│                │
│ └──────────┘ └──────────┘ └──────────┘ └──────────┘                │
│ Two lines of description at most, bodyLarge, "…"                   │  56
│                                                                    │  (weight)
│                      [ Save slot, DN-UX-006 ] [ ▲ Navigate ]       │  76
│ © 2026 HERE                                                        │  ≈ 20
└────────────────────────────────────────────────────────────────────┘
```

Top part (row, tiles, description) sits in a `weight(1f)` column with 12 dp gaps, so it is squeezed first; the bottom
part (buttons 76, 8, notice) stays whole: 186 + 72 + 56 + 2×12 + 12 + 104 ≈ 454 dp.

- [ ] **Step 1: Write the failing clock-time test** — `app/src/test/java/.../ui/ClockTimeTest.kt`

```kotlin
package com.kanyandula.discovernearby.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class ClockTimeTest {

    // The car's 12/24-hour setting decides, as the system clock shows it.
    @Test
    fun followsTheCarsClockSetting() {
        assertEquals("18:00", clockTime(LocalTime.of(18, 0), is24Hour = true))
        assertEquals("6:00 PM", clockTime(LocalTime.of(18, 0), is24Hour = false))
        assertEquals("00:30", clockTime(LocalTime.of(0, 30), is24Hour = true))
        assertEquals("12:30 AM", clockTime(LocalTime.of(0, 30), is24Hour = false))
    }
}
```

Run: `./gradlew testDebugUnitTest --tests '*ClockTimeTest'` — expected: compilation fails.

- [ ] **Step 2: Add the text helpers, strings and icon.** In `PlaceText.kt`, add:

```kotlin
/** Tripadvisor's level as its own site shows it (DN-UX-005). */
@get:StringRes
internal val PriceLevel.label: Int
    get() = when (this) {
        PriceLevel.CHEAP -> R.string.price_cheap
        PriceLevel.MID -> R.string.price_mid
        PriceLevel.FINE -> R.string.price_fine
    }

/** "18:00", or "6:00 PM" when the car shows a 12-hour clock. */
internal fun clockTime(time: LocalTime, is24Hour: Boolean): String =
    time.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.US))
```

Imports: `com.kanyandula.discovernearby.model.PriceLevel`, `java.time.LocalTime`, `java.time.format.DateTimeFormatter`,
`java.util.Locale`.

In `strings.xml`, next to `open_now`/`closed_now`, add and remove:

```xml
    <string name="open_closes">Open · Closes %1$s</string>
    <string name="open_all_day">Open 24 hours</string>
    <string name="car_park_nearby">Car park nearby</string>
    <string name="price_cheap">€</string>
    <string name="price_mid">€€–€€€</string>
    <string name="price_fine">€€€€</string>
```

Remove `<string name="amenities">Amenities</string>` (the tiles replace the "Amenities" section; lint flags it unused).

Create `app/src/main/res/drawable/ic_parking.xml` in the house stroke style (Lucide "square-parking", ISC):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z M9 17v-10h4a3 3 0 0 1 0 6h-4"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

Run: `./gradlew testDebugUnitTest --tests '*ClockTimeTest'` — expected: PASS.

- [ ] **Step 3: Update the screen tests first.** In `PlaceDetailsScreenTest`:
  - Pin the clock to 24 hours in `setUp()` before `setContent`:

```kotlin
        Settings.System.putString(
            ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.System.TIME_12_24, "24",
        )
```

  - Add `private var restricted by mutableStateOf(false)` and pass `drivingRestricted = restricted` to the screen.
  - Change the assertions that the new layout changes:
    - `contentShowsWhatIsKnown`: replace the `"2.1 km away"` and `"Café"` lines with
      `rule.onNodeWithText("Café · 2.1 km away").assertIsDisplayed()`; replace the `"Parking · Family-friendly"` and
      `"Amenities"` lines with `rule.onNodeWithText("Parking").assertIsDisplayed()` and
      `rule.onNodeWithText("Family-friendly").assertIsDisplayed()`.
    - `navigateIsAvailableWhileDetailsLoad` and `unknownFieldsAreLeftOut`: `"2.1 km away"` → `"Café · 2.1 km away"`;
      in `unknownFieldsAreLeftOut`, `"Amenities"` → `rule.onNodeWithText("Parking").assertDoesNotExist()`.
  - Append:

```kotlin
    // DN-UX-005: the opening line says when an open place closes.
    @Test
    fun anOpenPlaceSaysWhenItCloses() {
        state = Content(PlaceDetails(place, openingSummary = null), Closing.At(LocalTime.of(18, 0)))
        rule.onNodeWithText("Open · Closes 18:00").assertIsDisplayed()
        state = Content(PlaceDetails(place, openingSummary = null), Closing.AllDay)
        rule.onNodeWithText("Open 24 hours").assertIsDisplayed()
    }

    // Review Focus 2: HERE says open, but no period gives a closing time.
    @Test
    fun openWithoutAClosingTimeSaysOpenNow() {
        state = Content(PlaceDetails(place, openingSummary = "Mon-Sun: 08:00 - 16:00"), closing = null)
        rule.onNodeWithText("Open now").assertIsDisplayed()
        rule.onAllNodesWithText("Closes", substring = true).assertCountEquals(0)
    }

    @Test
    fun theKindLineAddsTheTripadvisorPrice() {
        enrichment = PlaceEnrichment(priceLevel = PriceLevel.MID)
        state = Content(PlaceDetails(place, openingSummary = null))
        rule.onNodeWithText("Café · €€–€€€ · 2.1 km away").assertIsDisplayed()
    }

    @Test
    fun aCarParkNearbyIsATile() {
        state = Content(PlaceDetails(place, openingSummary = null, carParkNearby = true))
        rule.onNodeWithText("Car park nearby").assertIsDisplayed()
        state = Content(PlaceDetails(place, openingSummary = null, carParkNearby = false))
        rule.onNodeWithText("Car park nearby").assertDoesNotExist()
    }

    // Review Focus 4: cars limit text while moving, so the description goes and comes back.
    @Test
    fun theDescriptionHidesWhileDriving() {
        enrichment = PlaceEnrichment(description = "A café by the harbour.")
        state = Content(PlaceDetails(place, openingSummary = null))
        rule.onNodeWithText("A café by the harbour.").assertIsDisplayed()
        restricted = true
        rule.onNodeWithText("A café by the harbour.").assertDoesNotExist()
        restricted = false
        rule.onNodeWithText("A café by the harbour.").assertIsDisplayed()
    }

    // The fallback keeps the row's Tripadvisor data too (docs/02 §7).
    @Test
    fun summaryOnlyKeepsThePriceAndDescription() {
        enrichment = PlaceEnrichment(priceLevel = PriceLevel.CHEAP, description = "A café by the harbour.")
        state = SummaryOnly(place)
        rule.onNodeWithText("Café · € · 2.1 km away").assertIsDisplayed()
        rule.onNodeWithText("A café by the harbour.").assertIsDisplayed()
    }
```

Imports: `android.content.Context`, `android.provider.Settings`, `androidx.test.core.app.ApplicationProvider`,
`com.kanyandula.discovernearby.model.Closing`, `com.kanyandula.discovernearby.model.PriceLevel`,
`java.time.LocalTime`.

  In `PlaceDetailsFitTest`, make the worst case and the rail's width:

```kotlin
/** The panel's width on the reference AVD, 928 dp, less DN-UX-006's rail (72) and its gap (16). */
private val ReferencePanelWidth = 840.dp
```

Replace the test body's `setContent`, keep the existing assertions, and append these after them (they use its
`navigate`):

```kotlin
        rule.setContent {
            DiscoverNearbyTheme {
                Box(modifier = Modifier.height(ReferencePanelHeight).width(ReferencePanelWidth)) {
                    PlaceDetailsScreen(
                        distanceMeters = 1_400,
                        category = DiscoveryCategory.FAMILY,
                        state = SummaryOnly(everything),
                        onNavigate = {},
                        onBack = {},
                        enrichment = PlaceEnrichment(
                            photoUrl = "https://example.test/p.jpg",
                            rating = ProviderRating(4.5, 312, "https://example.test/b.png"),
                            priceLevel = PriceLevel.FINE,
                            description = "Seasonal plates from local growers and the morning's catch, served in a " +
                                "bright room over the bay, with a short wine list and a garden terrace in summer.",
                        ),
                    )
                }
            }
        }
```

```kotlin
        // Every part of the concept is on the panel: the tiles and the description sit above the actions.
        rule.onNodeWithText("Parking").assertIsDisplayed()
        val description = rule.onNodeWithText("Seasonal plates", substring = true)
        description.assertIsDisplayed()
        assertTrue(description.getUnclippedBoundsInRoot().bottom <= navigate.getUnclippedBoundsInRoot().top)
        navigate.assertHeightIsAtLeast(MinTouchTarget)
```

Imports: `androidx.compose.foundation.layout.width`, `com.kanyandula.discovernearby.model.PlaceEnrichment`,
`com.kanyandula.discovernearby.model.PriceLevel`, `com.kanyandula.discovernearby.model.ProviderRating`,
`com.kanyandula.discovernearby.ui.theme.MinTouchTarget`. Rename the test to `theWholeConceptFitsTheReferencePanel`.

Run: `./gradlew testDebugUnitTest --tests '*PlaceDetailsScreenTest' --tests '*PlaceDetailsFitTest'` — expected:
compilation fails (`drivingRestricted`).

- [ ] **Step 4: Extract `BackButton`.** In `ScreenHeader.kt`:

```kotlin
/** Back, as a 76 dp target with the focus ring. The AOSP car system bar has no Back button (docs/02 §3.6). */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier.focusRing(CircleShape).size(MinTouchTarget)) {
        Icon(painter = painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
    }
}
```

and use `BackButton(onBack)` in `ScreenHeader` in place of its `IconButton`.

- [ ] **Step 5: Update the dimens.** In `Dimens.kt`, replace the "Place Details artboards" block with:

```kotlin
// Place Details as in the Product Lead's concept (03b-place-details-concept, DN-UX-005), sized so the whole concept
// fits the 4:3 reference panel beside DN-UX-006's rail (PlaceDetailsFitTest).
val DetailsGap = 12.dp
val DetailsImageWidth = 240.dp
val DetailsImageHeight = 150.dp
val DetailsImageRadius = 16.dp
val DetailsImageIconSize = 72.dp
val AmenityTileHeight = 72.dp
val AmenityTileRadius = 14.dp
val AmenityIconSize = 24.dp
val NavigateRadius = 20.dp
val NavigateIconSize = 28.dp
val NavigateIconGap = 14.dp
val InfoIconGap = 12.dp
```

This removes `DetailsImageGap`, `DetailsInset`, `DetailsColumnGap`, `SectionPadding`, `ActionColumnWidth` and
`NavigateHeight` (only `PlaceDetailsScreen` used them; the buttons use `MinTouchTarget`).

- [ ] **Step 6: Rewrite the screen.** Replace `PlaceDetailsScreen`, `Facts` and `Section` in `PlaceDetailsScreen.kt`
  with the code below; keep `NavigationUnavailableMessage`, `ratingLine`, `PhotoCredit` and `NavigateButton` (change
  its height to `MinTouchTarget`); restyle `DetailsUnavailable` without its divider and padding.

```kotlin
/**
 * Place Details as in the Product Lead's concept (03b-place-details-concept, DN-UX-005): Back, the photo and what is
 * known about the place across the top; the amenity tiles; Tripadvisor's description; Navigate bottom right, with
 * Save's slot beside it (DN-UX-006). Navigate never waits on the optional details call (docs/02 §7), and one layout
 * serves every state but the hand-off failure, so nothing moves when details arrive. [enrichment] is the row's
 * Tripadvisor data (DN-UX-004, DN-UX-005); the description is hidden while [drivingRestricted]: cars limit text then.
 */
@Composable
fun PlaceDetailsScreen(
    distanceMeters: Int,
    category: DiscoveryCategory,
    state: PlaceDetailsUiState,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enrichment: PlaceEnrichment? = null,
    drivingRestricted: Boolean = false,
) {
    if (state is PlaceDetailsUiState.NavigationUnavailable) {
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
            ScreenHeader(title = state.summary.name, onBack = onBack)
            // docs/02 §14, design 11: Back is the only action, and it leaves the destination like the header's.
            MessageState(NavigationUnavailableMessage, R.string.back, onBack = onBack, modifier = Modifier.weight(1f))
        }
    } else {
        val content = state as? PlaceDetailsUiState.Content
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DetailsGap)) {
            // Squeezed first on a short panel; the actions and the notice below always stay whole.
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DetailsGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ContentGap)) {
                    BackButton(onBack)
                    Photo(category, enrichment?.photoUrl)
                    Summary(
                        place = state.summary,
                        distanceMeters = distanceMeters,
                        opening = openingLine(state.summary, content?.closing, content?.details?.openingSummary),
                        enrichment = enrichment,
                        summaryOnly = state is PlaceDetailsUiState.SummaryOnly,
                        modifier = Modifier.weight(1f),
                    )
                }
                val tiles = amenityTiles(state.summary, content?.details?.carParkNearby)
                if (tiles.isNotEmpty()) AmenityTiles(tiles)
                enrichment?.description?.takeUnless { drivingRestricted }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(PhotoCreditGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
                    Spacer(Modifier.weight(1f)) // Save's slot (DN-UX-006)
                    NavigateButton(onClick = onNavigate, modifier = Modifier.weight(1f))
                }
                // Bottom left: HERE's notice with HERE's data (ADR-001 V6a).
                ProviderAttribution(state.summary.attribution)
            }
        }
    }
}

@Composable
private fun Photo(category: DiscoveryCategory, photoUrl: String?) {
    Column(modifier = Modifier.width(DetailsImageWidth), verticalArrangement = Arrangement.spacedBy(PhotoCreditGap)) {
        PlaceImage(
            category = category,
            photoUrl = photoUrl,
            radius = DetailsImageRadius,
            iconSize = DetailsImageIconSize,
            modifier = Modifier.fillMaxWidth().height(DetailsImageHeight),
        )
        if (photoUrl != null) PhotoCredit()
    }
}

/**
 * The name; Tripadvisor's rating as Tripadvisor draws it, else the place's own; "Kind · price · distance"; the opening
 * line; and on the fallback the summary-only note. Each only when known.
 */
@Composable
private fun Summary(
    place: PlaceSummary,
    distanceMeters: Int,
    opening: String?,
    enrichment: PlaceEnrichment?,
    summaryOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RowLineGap)) {
        Text(
            text = place.name,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val tripadvisor = enrichment?.rating
        if (tripadvisor != null) {
            TripadvisorRating(
                rating = tripadvisor,
                count = tripadvisor.count?.let { pluralStringResource(R.plurals.reviews, it, it) },
            )
        } else {
            ratingLine(place)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val facts = listOfNotNull(
            kindLabel(place.primaryKind)?.let { stringResource(it) },
            enrichment?.priceLevel?.let { stringResource(it.label) },
            stringResource(R.string.distance_away, kilometres(distanceMeters)),
        )
        Text(
            text = facts.joinToString(SEPARATOR),
            style = MaterialTheme.typography.titleMedium,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        opening?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = if (place.isOpenNow == true) OpenNow else muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (summaryOnly) DetailsUnavailable()
    }
}

/**
 * "Open · Closes 18:00", "Open 24 hours" or "Open now" while open; "Closed now"; HERE's weekly text only when the
 * status is unknown. The time follows the car's 12/24-hour setting.
 */
@Composable
private fun openingLine(place: PlaceSummary, closing: Closing?, openingSummary: String?): String? =
    when (place.isOpenNow) {
        true -> when (closing) {
            Closing.AllDay -> stringResource(R.string.open_all_day)
            is Closing.At -> stringResource(
                R.string.open_closes,
                clockTime(closing.time, DateFormat.is24HourFormat(LocalContext.current)),
            )
            null -> stringResource(R.string.open_now)
        }
        false -> stringResource(R.string.closed_now)
        null -> openingSummary
    }

private class AmenityTile(@DrawableRes val icon: Int?, @StringRes val label: Int)

private const val TILE_SLOTS = 4 // the concept's row

/** The place's own amenities, then a car park nearby; at most a row of four. */
private fun amenityTiles(place: PlaceSummary, carParkNearby: Boolean?): List<AmenityTile> {
    val own = place.attributeTypes().map {
        AmenityTile(if (it == AttributeType.PARKING) R.drawable.ic_parking else null, it.label)
    }
    val carPark = AmenityTile(R.drawable.ic_parking, R.string.car_park_nearby).takeIf { carParkNearby == true }
    return (own + listOfNotNull(carPark)).take(TILE_SLOTS)
}

/** Four equal slots, so a lone tile keeps a quarter of the row as on the concept. Display only: not focusable. */
@Composable
private fun AmenityTiles(tiles: List<AmenityTile>) {
    Row(horizontalArrangement = Arrangement.spacedBy(ContentGap)) {
        repeat(TILE_SLOTS) { slot ->
            val tile = tiles.getOrNull(slot)
            if (tile == null) {
                Spacer(Modifier.weight(1f))
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(AmenityTileHeight)
                        .background(Raised, RoundedCornerShape(AmenityTileRadius))
                        .padding(horizontal = ContentGap),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    tile.icon?.let {
                        Icon(
                            painter = painterResource(it),
                            contentDescription = null, // the label says it
                            tint = Action,
                            modifier = Modifier.size(AmenityIconSize),
                        )
                    }
                    Text(
                        text = stringResource(tile.label),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
```

`DetailsUnavailable` becomes:

```kotlin
@Composable
private fun DetailsUnavailable() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InfoIconGap)) {
        Icon(painter = painterResource(R.drawable.ic_info), contentDescription = null, tint = muted)
        Text(
            text = stringResource(R.string.details_unavailable),
            style = MaterialTheme.typography.titleMedium,
            color = muted,
        )
    }
}
```

Fix the imports: add `android.text.format.DateFormat`, `androidx.annotation.DrawableRes`,
`androidx.annotation.StringRes`, `androidx.compose.foundation.background`, `androidx.compose.ui.platform.LocalContext`,
`com.kanyandula.discovernearby.model.AttributeType`, `com.kanyandula.discovernearby.model.Closing`,
`com.kanyandula.discovernearby.ui.clockTime`, `com.kanyandula.discovernearby.ui.components.BackButton`, the new dimens,
`ButtonGap`, `ContentGap`, `MinTouchTarget`; remove the unused ones (`HorizontalDivider`, `fillMaxHeight`,
`AnnotatedString` if unused, `takeOrElse`, `ProviderRating`, the removed dimens). Let the compiler and detekt list them.

- [ ] **Step 7: Pass the driving state.** In `DiscoverNavHost`'s `composable<PlaceDetailsRoute>`:

```kotlin
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            // Only for the description (DN-UX-005); Navigate and Back work the same either way.
            val driving by container.drivingRestrictions.state.collectAsStateWithLifecycle()
            PlaceDetailsScreen(
                distanceMeters = route.distanceMeters,
                category = route.category,
                enrichment = route.enrichment,
                state = state,
                drivingRestricted = driving.distractionOptimizationRequired,
                onNavigate = viewModel::navigate,
                onBack = { if (navController.isTop(entry)) navController.popBackStack() },
            )
```

This collects in the destination rather than the ViewModel (the spec's wording): the screen needs one flag, and the
Recommendations ViewModel stays the only one that applies a driving limit.

- [ ] **Step 8: Run the UI tests**

Run: `./gradlew testDebugUnitTest --tests '*PlaceDetails*' --tests '*ClockTimeTest' --tests '*DiscoverNavigationTest' --tests '*RotaryContractTest'`
Expected: PASS. If `PlaceDetailsFitTest` fails on height, keep the description at two lines and shrink instead:
`DetailsImageHeight` 150 → 140 first, then `AmenityTileHeight` 72 → 64; re-run, and record the values in the commit
body.

- [ ] **Step 9: Full checks**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Fix detekt findings (for example `LongMethod`, `LongParameterList`) by splitting
composables, not by suppressing.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/ app/src/main/res/ \
  app/src/test/java/com/kanyandula/discovernearby/ui/
git commit -m "Lay out Place Details as in the Product Lead's concept"
```

Body: the concept (03b-place-details-concept) puts the photo and the facts across the top, amenity tiles and a short
description below, and the actions at the bottom. Navigate sits on the right, leaving Save's slot for DN-UX-006. The
fit test now checks the whole concept on the 4:3 panel beside the rail to come.

---

### Task 6: Docs

**Files:**
- Modify: `docs/02-discover-nearby-ux-interaction-spec.md` (§7 Place Details)
- Modify: `docs/03-discover-nearby-engineering-implementation-plan.md` (§8 interface and `PlaceDetails`, §10
  enrichment)
- Modify: `docs/adr/0003-place-photos-and-ratings.md` (Decision: price and description)
- Modify: `docs/design/README.md`, `CLAUDE.md`

- [ ] **Step 1: docs/02 §7.** Under "Content, where available", replace the image-panel bullet and add the new items so
  the list reads (keep the rest):

```markdown
- Layout as in the Product Lead's concept (`docs/design/03b-place-details-concept.png`): Back, the image and the facts
  across the top; amenity tiles; a short description; **Navigate** bottom right, with **Save** beside it (DN-UX-006)
- The image: Tripadvisor's photo, credited "Photo: Tripadvisor", otherwise the category artwork, as on the rows
  (ADR-003)
- Place name
- Rating (if provided): Tripadvisor's graphic with its review count, else the provider's
- Category, price (Tripadvisor's level as €, €€–€€€ or €€€€, for cafés and restaurants) and distance, on one line
- Open/closed status (if provided), with today's closing time while open ("Open · Closes 18:00", "Open 24 hours")
- Amenity tiles: provided or derived amenities, and "Car park nearby" when HERE has a car park within 150 m
- A description (Tripadvisor's), two lines at most, hidden while driving
- Provider attribution, if the provider requires it here
- **Navigate** as the primary action
```

Under "Not required for POC", change "Long descriptions" to "Long descriptions (a two-line one is shown, hidden while
driving)".

- [ ] **Step 2: docs/03.** In the §8 `interface PlacesRepository` listing, add after `getPlaceDetails`:

```kotlin
    suspend fun hasCarParkNear(location: GeoPoint): Boolean // within CAR_PARK_RADIUS_METERS (150); display only
```

In the `data class PlaceDetails` listing, add `openingPeriods: List<OpeningPeriod>` and `carParkNearby: Boolean?`. In
§10's "Enrichment after ranking" paragraph, change "(a photo and Tripadvisor's rating)" to "(a photo, Tripadvisor's
rating, its price level and description)" and "nothing it returns is stored" to "search results are not stored
(ADR-004)".

- [ ] **Step 3: ADR-003.** In Decision, after the "Display" bullet add:

```markdown
- **Price and description (DN-UX-005):** the matched location's `price_level` (Cheap Eats, Mid Range, Fine Dining;
  restaurants only), shown as €, €€–€€€ or €€€€, and its English description, at most two lines and hidden while
  driving. Both come in the nearby search, so they cost no call. DN-SP-005 found them for 9 of 10 matched cafés and
  restaurants and 12 of 30 matched places respectively; Tripadvisor's `attributes` (amenities) were empty for all 30.
```

- [ ] **Step 4: design README.** Replace the "Deviation (DN-UX-004)" paragraph with:

```markdown
**Place Details follows the concept (DN-UX-005):** `03b-place-details-concept.png` (the Product Lead, 2026-10-11)
replaces `03-place-details.png` as the layout reference. The app keeps an in-app Back (the AOSP car bar has none), the
distance (on the kind line), the photo credit and the HERE notice. Its tiles show only what a source has: the place's
own amenities (none from HERE) and "Car park nearby"; Wi-Fi, outdoor seating and family friendly have no source
(DN-SP-005). The ★ rating is Tripadvisor's own graphic (ADR-003). The rail on the left of the mockups is the car's
system UI (`00-overview.png`); the app's own rail (Discover, Saved) comes with DN-UX-006.
```

Add a row to the file table for `03b-place-details-concept.png` | Place Details (concept) | the layout reference since
DN-UX-005.

- [ ] **Step 5: CLAUDE.md.** In "Current state of the code", replace the "Kind and opening (DN-UX-004)" bullet with:

```markdown
  - **Place Details (DN-UX-005):** laid out as the concept (`docs/design/03b-place-details-concept.png`). The kind,
    Tripadvisor's price and the distance share a line; "Open · Closes 18:00" from HERE's structured hours
    (`closingAt`); "Car park nearby" from one HERE `/browse` (`hasCarParkNear`, 150 m); Tripadvisor's description,
    two lines, hidden while driving. Scenario fakes get made-up prices and descriptions (`FakeEnricher`).
```

- [ ] **Step 6: Check the docs.** Run:

```bash
grep -rn "Amenities\b\|SectionPadding\|ActionColumnWidth" docs/design/README.md CLAUDE.md docs/02-discover-nearby-ux-interaction-spec.md
python3 - <<'PY'
import re, pathlib
for f in ["docs/02-discover-nearby-ux-interaction-spec.md", "docs/design/README.md", "docs/adr/0003-place-photos-and-ratings.md"]:
    for m in re.finditer(r"\]\(([^)#]+)", pathlib.Path(f).read_text()):
        target = (pathlib.Path(f).parent / m.group(1)).resolve()
        if not m.group(1).startswith("http") and not target.exists():
            print(f, "broken link:", m.group(1))
PY
```

Expected: no stale references, no broken links.

- [ ] **Step 7: Commit**

```bash
git add docs/02-discover-nearby-ux-interaction-spec.md docs/03-discover-nearby-engineering-implementation-plan.md \
  docs/adr/0003-place-photos-and-ratings.md docs/design/README.md CLAUDE.md
git commit -m "Record Place Details as in the concept in the docs"
```

---

### Task 7: Verify on the emulator and ship

- [ ] **Step 1: Full checks.** `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL; note
  the test count.
- [ ] **Step 2: Emulator on the fakes (no paid calls).** Check `adb -s emulator-5554 emu avd name` prints
  `AAOS_AOSP_33_userdebug`; install with `ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug`; Park; launch
  `adb -s emulator-5554 shell am start --user 10 -n com.kanyandula.discovernearby/.ui.MainActivity --es scenario NORMAL`
  with the location at Greystones (`geo fix` twice). Open Coffee → The Daily Grind and Food → Seaview Kitchen.
  Screenshots to the scratchpad. Check: the layout matches the concept; "Café · € · … km away"; the description stops
  at two lines; "Car park nearby" tile; Navigate hands off to the stub (`adb -s emulator-5554 logcat -s StubNav`).
- [ ] **Step 3: Drive.** `inject-vhal-event 0x11400400 8` and hold moving: the description disappears; Navigate and
  Back still work; Park brings it back.
- [ ] **Step 4: Fallback.** Relaunch with `--es scenario DETAILS_FAILURE`: the summary-only note shows, with price and
  description, no car-park tile, and Navigate works.
- [ ] **Step 5: A few live checks.** Relaunch without a scenario (live HERE, Tripadvisor key set), Galway, daytime if
  possible: open two Coffee places and one Scenic place. Check the closing time against HERE's text, the car-park tile
  and a Tripadvisor price where there is one. Count the Tripadvisor calls (about 7 a list) in the ticket.
- [ ] **Step 6: Rotary.** On Place Details, turn the controller: Back → Navigate, each with the focus ring; no other
  stop (the tiles are not focusable). Record what happens; ADR-002's waiver covers focus after Back.
- [ ] **Step 7: Simplify and review.** Run the `simplify` skill on `git diff main...HEAD`; fix what it finds. Then the
  `correctness-reviewer` and `quality-reviewer` agents on the branch; fix Critical and Important findings.
- [ ] **Step 8: PR.** Push the branch and open the PR with the `pr-description` skill: ticket DN-UX-005, its acceptance
  criteria and how each was met, the emulator and live checks, the Tripadvisor calls used, the review results. No AI
  attribution.
- [ ] **Step 9: After the merge (user merges).** Verify MERGED in its own call; then delete the branch locally and
  remotely; set DN-UX-005 to `done`; update NOW.md and BACKLOG; DN-UX-006 is next.
