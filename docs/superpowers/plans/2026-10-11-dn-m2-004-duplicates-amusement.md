# DN-M2-004 — Duplicate HERE records and the amusement rule Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A place HERE lists twice shows once and uses one slot of its kind's cap, and non-family businesses filed
under HERE's Amusement Park code stop taking Family's amusement slots; then a daytime re-run for the Product Lead.

**Architecture:** The HERE mapper reads each item's phone numbers into `PlaceSummary.phones` and maps two more HERE
codes to kinds that score nothing (`finance`, `community`). The engine drops a record that is the same place as a
better-ranked one (pure Kotlin, `discovery/SamePlace.kt`) before the per-kind cap, and drops places a category's
`KindExclusion` rules out before scoring. Both rules are data in `CategoryConfigs` or plain functions in `discovery/`.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit 4 (plain JVM tests for the engine, the mapper and the rule).

**Spec:** the ticket DN-M2-004 (vault) and the Product Lead's decisions of 2026-10-11, recorded in the private vault
note `benchmarks/2026-10-11-dn-m2-004-records.md`:
- **Duplicates (every category):** two records are one place when they are within 150 m of each other, share a phone
  number, and either stand within 10 m of each other or one name contains the other. The best-ranked record is kept.
- **Amusement (Family):** a place whose primary kind is amusement park is dropped when it also carries a food code
  (restaurant, café, coffee shop), a finance code or a community code, or when its name contains the whole word
  escape, salon, band or community.
- **Re-run:** Dublin × Family and Galway × Family, plus any cell where a merged pair appeared among the shown rows, in
  daytime (11:00–17:00), same setup; names in the vault, a name-free repo record, the name-leak check; the Product
  Lead judges it.

## Global Constraints

- The engine stays pure Kotlin: no Compose, Car or Android imports in `discovery/` or `model/` (CLAUDE.md).
- Provider response models never leave `places/here/`; the mapper maps to domain fields (docs/03 §8).
- Every weight and rule is data in `CategoryConfigs` (docs/03 §2, §10).
- Public repo: tests use made-up names only; no real place names, coordinates, phone numbers or provider responses in
  code, tests, docs, commits or the PR.
- Phone numbers are used only to tell one place listed twice; they are never shown.
- Commits: no AI attribution; subject ≤ 72 chars; body wrapped at 72.
- Filtered test runs: `./gradlew :app:testDebugUnitTest --tests '<pattern>'`.

## Review Focus

1. Records with no phone never merge, however close (the beach records): `SamePlaceTest.recordsWithoutAPhoneStaySeparate`.
2. Different businesses that share a phone, 27 m apart with different names, stay separate:
   `SamePlaceTest.aSharedPhoneAloneIsNotEnough`.
3. A zoo whose secondary codes include a café stays in Family; only a primary amusement park is ruled out:
   `BasicRecommendationEngineTest.onlyAPrimaryAmusementParkIsRuledOut`.
4. The name words match whole words only ("Bandstand Fun Park", "Escapade Play" stay):
   `BasicRecommendationEngineTest.nameWordsMatchWholeWordsOnly`.
5. A place stored before `phones` existed (saved places, a route after process death) still decodes:
   `PlaceSummaryJsonTest.aSummaryWithoutPhonesStillDecodes`.

---

### Task 1: Phones and two more kinds from HERE

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/model/Place.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/places/here/HereModels.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/places/here/HereMapper.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/places/here/HereCategories.kt`
- Test: `places/here/HereMapperTest.kt`, `places/here/HereCategoriesTest.kt`, new `model/PlaceSummaryJsonTest.kt`

**Interfaces:**
- Produces: `PlaceSummary.phones: Set<String> = emptySet()` (digits and a leading `+` only); `kindFor("700-7200-0271")
  == "finance"`, `kindFor("900-9200-0299") == "community"`.

- [ ] **Step 1: Write the failing tests**

`HereMapperTest` (FULL_ITEM already carries `"+353 1 000 0000"`):

```kotlin
    // DN-M2-004: phone numbers tell one place listed twice; spaces and dashes go, a leading + stays.
    @Test
    fun phonesAreReadWithoutSpacing() {
        val place = checkNotNull(item(FULL_ITEM).toSummary())
        assertEquals(setOf("+35310000000"), place.phones)
        val base = """{"title": "T", "id": "i", "position": {"lat": 1.0, "lng": 2.0}, "contacts": """
        assertEquals(
            setOf("+3531111", "0912222"),
            item(base + """[{"phone": [{"value": "+353 1-111"}]}, {"phone": [{"value": "091 2222"}]}]}""")
                .toSummary()?.phones,
        )
        assertTrue(checkNotNull(item(base + "[]}").toSummary()).phones.isEmpty())
    }
```

`HereCategoriesTest.theMostSpecificCategoryWins`, at its end:

```kotlin
        // DN-M2-004: codes that rule an amusement park out of Family; they score in no category.
        assertEquals("finance", kindFor("700-7200-0271"))
        assertEquals("community", kindFor("900-9200-0299"))
```

`app/src/test/java/com/kanyandula/discovernearby/model/PlaceSummaryJsonTest.kt`:

```kotlin
package com.kanyandula.discovernearby.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSummaryJsonTest {

    // Review Focus 5: saved places and routes written before DN-M2-004 carry no phones; they still decode.
    @Test
    fun aSummaryWithoutPhonesStillDecodes() {
        val stored = """{"id": "p1", "name": "Made-up Café", "location": {"lat": 1.0, "lng": 2.0},
            "placeKinds": ["cafe"], "primaryKind": "cafe", "attributes": [], "rating": null, "ratingCount": null,
            "isOpenNow": null, "travelTimeMinutes": null}"""
        assertEquals(emptySet<String>(), Json.decodeFromString(PlaceSummary.serializer(), stored).phones)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*HereMapperTest*' --tests '*HereCategoriesTest*' --tests '*PlaceSummaryJsonTest*'`
Expected: compilation FAILS (no `phones`); after Step 3's model change alone, the kind assertions fail.

- [ ] **Step 3: Implement**

`Place.kt`, `PlaceSummary`, after `attribution`:

```kotlin
    // Only to tell one place a provider lists twice (DN-M2-004); never shown. Digits and a leading +.
    val phones: Set<String> = emptySet(),
```

`HereModels.kt`: `HereItem` gains `val contacts: List<HereContacts> = emptyList(),` and:

```kotlin
@Serializable
internal data class HereContacts(val phone: List<HereContactValue> = emptyList())

@Serializable
internal data class HereContactValue(val value: String = "")
```

`HereMapper.kt`, in `toSummary()`, after `attribution = HERE_ATTRIBUTION,`:

```kotlin
        phones = contacts.flatMap { it.phone }.map { it.value.filter { c -> c.isDigit() || c == '+' } }
            .filter { it.isNotEmpty() }.toSet(),
```

`HereCategories.kt`: the KINDS comment gains "; a few more mark what rules a place out (DN-M2-004)", and the map:

```kotlin
    // DN-M2-004: they score in no category; Family's amusement rule reads them.
    "700-7200-0271" to "finance",
    "900-9200" to "community",
```

- [ ] **Step 4: Run them to verify they pass**

Run: the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Read HERE phone numbers and the finance and community codes"
```

---

### Task 2: One place listed twice shows once

**Files:**
- Create: `app/src/main/java/com/kanyandula/discovernearby/discovery/SamePlace.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/discovery/RecommendationEngine.kt`
- Test: new `discovery/SamePlaceTest.kt`, `discovery/BasicRecommendationEngineTest.kt`

**Interfaces:**
- Consumes: `PlaceSummary.phones` (Task 1).
- Produces: `internal fun PlaceSummary.isSamePlaceAs(other: PlaceSummary): Boolean`; `internal fun nameWords(name:
  String): List<String>` (lower case, accents dropped, letters and digits).

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/kanyandula/discovernearby/discovery/SamePlaceTest.kt`:

```kotlin
package com.kanyandula.discovernearby.discovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// DN-M2-004 (Product Lead, 2026-10-11): one place when within 150 m, a shared phone, and either within 10 m or one
// name containing the other. Made-up names in the shapes the benchmark found.
class SamePlaceTest {

    // Each record its own ID, as HERE gives them: the same ID is one place without any other test.
    private fun place(name: String, metersNorth: Int, vararg phones: String) =
        testPlace("$name@$metersNorth", "aquarium", metersNorth = metersNorth).copy(name = name, phones = phones.toSet())

    @Test
    fun twoNamesOnOneSpotWithOnePhoneAreOnePlace() {
        assertTrue(place("Bayside Aquaria", 100, "+1").isSamePlaceAs(place("National Sea Centre", 105, "+1")))
    }

    @Test
    fun theSameNameAPairOfStreetsApartIsOnePlace() {
        assertTrue(place("BLUEWAVE HALL", 100, "+1").isSamePlaceAs(place("BLUEWAVE HALL", 238, "+1")))
    }

    @Test
    fun oneNameInsideTheOtherIsOnePlace() {
        assertTrue(place("Lakeside Zoological Soc", 100, "+1").isSamePlaceAs(place("Lakeside Zoo", 146, "+1")))
        assertTrue(place("Aquatic Centre", 100, "+1").isSamePlaceAs(place("Splash at Aquatic Centre", 126, "+1")))
        assertTrue(place("Café Lúb", 100, "+1").isSamePlaceAs(place("CAFE LUB!", 160, "+1"))) // accents, case, marks
    }

    // Review Focus 2: neighbours with one owner's number, different names, 27 m apart.
    @Test
    fun aSharedPhoneAloneIsNotEnough() {
        assertFalse(place("Harbour Tavern", 100, "+1").isSamePlaceAs(place("Quayside & Co", 127, "+1")))
    }

    // Review Focus 1: three records of one beach, no contacts at all.
    @Test
    fun recordsWithoutAPhoneStaySeparate() {
        assertFalse(place("Silverspray Strand", 100).isSamePlaceAs(place("Silverspray Strand", 101)))
    }

    @Test
    fun farApartIsNeverOnePlace() {
        assertFalse(place("BLUEWAVE HALL", 100, "+1").isSamePlaceAs(place("BLUEWAVE HALL", 260, "+1")))
    }

    // Containment needs four letters, so "Bo" isn't inside every name with a "bo".
    @Test
    fun aVeryShortNameDoesNotMatchInsideAnother() {
        assertFalse(place("Bo", 100, "+1").isSamePlaceAs(place("Bo Bar", 130, "+1")))
    }

    @Test
    fun theSameIdIsAlwaysOnePlace() {
        assertTrue(testPlace("a", "zoo").isSamePlaceAs(testPlace("a", "zoo", metersNorth = 5_000)))
    }
}
```

`BasicRecommendationEngineTest`, after `aRepeatedPlaceDoesNotUseUpTheCap`:

```kotlin
    // DN-M2-004: one aquarium HERE lists twice shows once, so a second aquarium keeps the other slot.
    @Test
    fun aPlaceListedTwiceShowsOnceAndUsesOneSlot() {
        val record = testPlace("record-1", "aquarium", metersNorth = 1_700).copy(phones = setOf("+1"))
        val again = testPlace("record-2", "aquarium", metersNorth = 1_700).copy(phones = setOf("+1"))
        val other = testPlace("other", "aquarium", metersNorth = 2_600)
        assertEquals(listOf("record-1", "other"), ranked(FAMILY, record, again, other))
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*SamePlaceTest*' --tests '*BasicRecommendationEngineTest*'`
Expected: compilation FAILS (no `isSamePlaceAs`).

- [ ] **Step 3: Implement**

`SamePlace.kt`:

```kotlin
package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.model.PlaceSummary
import java.text.Normalizer

/** Two records further apart than this are never one place. */
private const val SAME_PLACE_METERS = 150

/** Records this close with one phone number are one place, whatever their names. */
private const val SAME_SPOT_METERS = 10

/** A shorter name is never matched inside another. */
private const val MIN_NAME_LENGTH = 4

/**
 * Whether two provider records are one place (DN-M2-004, Product Lead 2026-10-11): the same ID; or within 150 m, a
 * shared phone number, and either within 10 m of each other or one name containing the other. HERE lists some places
 * twice under different IDs and names; a shared phone alone also joins neighbours with one owner.
 */
internal fun PlaceSummary.isSamePlaceAs(other: PlaceSummary): Boolean {
    if (id == other.id) return true
    if (phones.none { it in other.phones }) return false
    val apart = location.distanceMetersTo(other.location)
    return apart <= SAME_SPOT_METERS || (apart <= SAME_PLACE_METERS && namesOverlap(name, other.name))
}

private fun namesOverlap(a: String, b: String): Boolean {
    val (short, long) = listOf(nameWords(a).joinToString(" "), nameWords(b).joinToString(" ")).sortedBy { it.length }
    return short.length >= MIN_NAME_LENGTH && short in long
}

/** A name's words: lower case, accents dropped, letters and digits only. */
internal fun nameWords(name: String): List<String> =
    Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
        .split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
```

`RecommendationEngine.kt`: replace

```kotlin
            .distinctBy { it.recommendation.place.id } // the list keys rows by id; a repeated place would crash it
```

with

```kotlin
            // In ranked order, so the best record of a place stays: a repeated ID would crash the list, and a place
            // HERE lists twice would show twice and use two of its kind's slots (DN-M2-004).
            .fold(mutableListOf<Scored>()) { kept, scored ->
                if (kept.none { it.recommendation.place.isSamePlaceAs(scored.recommendation.place) }) kept += scored
                kept
            }
```

and the class KDoc gains "One place listed twice counts once (`isSamePlaceAs`)."

- [ ] **Step 4: Run them to verify they pass**

Run: the Step 2 command. Expected: PASS, including the existing `keepsTheNearestCopyOfARepeatedPlace` and
`aRepeatedPlaceDoesNotUseUpTheCap`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Show a place HERE lists twice once, in one cap slot"
```

---

### Task 3: Family's amusement rule

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/discovery/CategoryConfig.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/discovery/RecommendationEngine.kt`
- Test: `discovery/BasicRecommendationEngineTest.kt`

**Interfaces:**
- Consumes: `nameWords` (Task 2); the `finance` and `community` kinds (Task 1).
- Produces: `data class KindExclusion(kind, withKinds, words)` with `fun excludes(place: PlaceSummary): Boolean`;
  `CategoryConfig.exclusions: List<KindExclusion> = emptyList()`.

- [ ] **Step 1: Write the failing tests** (in `BasicRecommendationEngineTest`)

```kotlin
    // DN-M2-004 (Product Lead, 2026-10-11): HERE files non-family businesses as Amusement Park; their other codes and
    // names rule them out of Family.
    @Test
    fun anAmusementParkThatIsAlsoACafeFinanceOrCommunityIsRuledOut() {
        val funPark = testPlace("fun-park", "amusement_park")
        val takeaway = testPlace("takeaway", "amusement_park", "restaurant")
        val bookmaker = testPlace("bookmaker", "amusement_park", "finance")
        val hall = testPlace("hall", "amusement_park", "community")
        assertEquals(listOf("fun-park"), ranked(FAMILY, funPark, takeaway, bookmaker, hall))
    }

    @Test
    fun anAmusementParkNamedForEscapeRoomsASalonABandOrACommunityIsRuledOut() {
        val kept = testPlace("Jumpers Fun Park", "amusement_park")
        val ruledOut = listOf("Lockbox Escape Rooms", "Glow Salon", "Riverside Brass Band", "Hillview Community Centre")
            .map { testPlace(it, "amusement_park").copy(name = it) }
        assertEquals(listOf(kept.id), ranked(FAMILY, kept, *ruledOut.toTypedArray()))
    }

    // Review Focus 3: a zoo with a café code stays, and so does a place with Amusement Park only as a secondary code.
    @Test
    fun onlyAPrimaryAmusementParkIsRuledOut() {
        val zoo = testPlace("zoo", "zoo", "amusement_park", "cafe")
        val karting = testPlace("Escape Karting", "tourist_attraction", "amusement_park").copy(name = "Escape Karting")
        assertEquals(setOf("zoo", "Escape Karting"), ranked(FAMILY, zoo, karting).toSet())
    }

    // Review Focus 4: whole words only.
    @Test
    fun nameWordsMatchWholeWordsOnly() {
        val names = listOf("Bandstand Fun Park", "Escapade Play")
        val places = names.map { testPlace(it, "amusement_park").copy(name = it) }
        assertEquals(names.toSet(), ranked(FAMILY, *places.toTypedArray()).toSet())
    }

    // The rule is Family's only.
    @Test
    fun otherCategoriesHaveNoExclusions() {
        assertEquals(
            listOf(FAMILY),
            CategoryConfigs.filterValues { it.exclusions.isNotEmpty() }.keys.toList(),
        )
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*BasicRecommendationEngineTest*'`
Expected: compilation FAILS (no `exclusions`); with the field alone, the three ruling-out tests fail.

- [ ] **Step 3: Implement**

`CategoryConfig.kt`, after `CategoryConfig`:

```kotlin
/**
 * A place whose primary kind is [kind] is dropped when it also has one of [withKinds], or when its name has one of
 * [words] as a whole word (DN-M2-004).
 */
data class KindExclusion(val kind: String, val withKinds: Set<String>, val words: Set<String>) {
    fun excludes(place: PlaceSummary): Boolean =
        place.primaryKind == kind &&
            (place.placeKinds.any { it in withKinds } || nameWords(place.name).any { it in words })
}
```

`CategoryConfig` gains, after `maxPerKind`:

```kotlin
    /** Places ruled out before scoring (Product Lead, 2026-10-11). */
    val exclusions: List<KindExclusion> = emptyList(),
```

Family's config gains:

```kotlin
        // HERE files non-family businesses under Amusement Park too: a takeaway, a bookmaker, community rooms,
        // escape rooms, a salon, a brass band. No code tells them from a fun park, so their other codes and their
        // names do (Product Lead, 2026-10-11, DN-M2-004).
        exclusions = listOf(
            KindExclusion(
                kind = "amusement_park",
                withKinds = setOf("restaurant", "cafe", "coffee_shop", "finance", "community"),
                words = setOf("escape", "salon", "band", "community"),
            ),
        ),
```

(Import `com.kanyandula.discovernearby.model.PlaceSummary`. The property is `words`, not `nameWords`, so it doesn't
hide the `nameWords()` function inside the class.)

Note: `finance` and `community` can be a primary kind now; such a place's cap kind becomes that kind instead of the
kind it matched. Both score nothing, so this only changes which cap a rare finance- or community-first place shares.

`RecommendationEngine.kt`, after the closed-places filter:

```kotlin
            .filterNot { place -> config.exclusions.any { it.excludes(place) } }
```

- [ ] **Step 4: Run them to verify they pass**

Run: the Step 2 command. Expected: PASS.

- [ ] **Step 5: Run the whole gate**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Rule non-family businesses filed as amusement parks out of Family"
```

---

### Task 4: Docs

**Files:** `docs/03-discover-nearby-engineering-implementation-plan.md` (§10), `CLAUDE.md` (Ranking bullet),
`docs/adr/0001-poi-provider.md` (HERE data notes, if it lists HERE's data defects), `docs/superpowers/specs/2026-10-11-place-details-and-saved-design.md`.

- [ ] **Step 1:** docs/03 §10 (ranking): after the per-kind cap, "**One place listed twice (DN-M2-004):** two records
  within 150 m that share a phone number and stand within 10 m of each other, or where one name contains the other,
  are one place; the best-ranked stays and uses one slot of its kind's cap." and "**Family's amusement rule
  (DN-M2-004):** a place filed first as an amusement park is dropped when it also carries a food, finance or
  community code, or its name has the word escape, salon, band or community (`KindExclusion`)."
- [ ] **Step 2:** CLAUDE.md, the Ranking bullet: "DN-M2-004: one place HERE lists twice counts once
  (`isSamePlaceAs`: a shared phone and the same spot or name); Family rules out non-family amusement parks
  (`KindExclusion`)."
- [ ] **Step 3:** the spec, line 100: "initial focus on Navigate" → "initial focus on the screen's first item, Back
  (Product Lead, 2026-10-11)"; line 84 unchanged.
- [ ] **Step 4:** `grep -rn "distinctBy { it.recommendation" docs CLAUDE.md` — no stale description of the old
  ID-only rule.
- [ ] **Step 5: Commit** `git commit -m "Record the duplicate and amusement rules in the docs"`

---

### Task 5: The daytime re-run (11:00–17:00 local)

- [ ] **Step 1:** Check the time is within 11:00–17:00; otherwise stop and resume in the window.
- [ ] **Step 2:** Emulator `AAOS_AOSP_33_userdebug` (`emu avd name`), Park, live key, install this branch. Capture
  Dublin × Family and Galway × Family (`geo fix` twice per location; screenshots in the scratchpad). For each other
  cell, replay its `/browse` (scratch script) and check whether a merged pair appeared among the five rows the
  2026-10-10 re-run showed; capture any such cell.
- [ ] **Step 3:** Vault note `benchmarks/2026-10-12-m2-rerun.md` (names, suggested judgments, score inputs),
  `status: awaiting-product-review`. Stop for the Product Lead's judgments.
- [ ] **Step 4:** After sign-off: name-free `docs/benchmarks/2026-10-12-m2-rerun.md`; the name-leak check (the vault
  note's names against the diff, the commits and the PR body: 0 hits); docs/05 §9 M2 line per the Product Lead's
  exit decision.
