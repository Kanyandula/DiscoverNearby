# DN-M2-003 Ranking Tuning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the causes behind the M2 benchmark's failed cells and tuning findings, then re-run the affected cells
for the Product Lead. M2 exits after that re-run is judged.

**Architecture:**
- **Ranking data, not new code paths:**
  - Family gets finer kinds from HERE's codes, a weaker amusement-park weight and HERE's Aquarium code
    (`HereCategories.kt`, `CategoryConfig.kt`).
  - Outdoors' cap goes to 3 and the open-now bonus to 2 (`CategoryConfig.kt`).
- **HERE requests:**
  - Both `/browse` and `/lookup` ask for English (`lang=en`).
  - `/browse` takes 50 results instead of 20 (`HerePlacesRepository.kt`).
- **Provider data limits:** places HERE positions wrongly get no app change. They are recorded in ADR-001's Known
  limitations.
- **Re-run:** the real app on the emulator, in daytime. The full record (names) goes in the vault; the repo gets a
  name-free record.

**Tech Stack:** Kotlin, JUnit, OkHttp interceptor tests, adb and the AAOS emulator, Markdown.

**Spec:**
- the ticket `~/.claude/projects/Discover Nearby/tickets/DN-M2-003-ranking-tuning.md`: its acceptance criteria, the
  2026-10-07 investigation and the Product Lead decisions;
- `docs/benchmarks/2026-10-07-m2.md`, the record being followed up;
- docs/03 §10 (scoring), docs/04 §2 and §4 (locations, benchmark rules), ADR-001 (Family relevance rule, category
  mapping, Known limitations).

## Global Constraints

- **Product Lead decisions (the user, 2026-10-07):**
  - **Family:** finer kinds and a weak amusement park.
    - HERE's Amusement Park code maps to its own kind, weighted 20.
    - Zoo, aquarium, water park and children's museum stay at 30.
    - Add HERE's Aquarium code to the Family search.
  - **Positions:** record wrong HERE positions as a provider data limitation. No app change; the re-run records
    whether they still appear.
  - **Outdoors:** a cap of 3 per kind.
  - **Open now:** `OPEN_NOW_BONUS` = 2.
  - **M2:** exits after this ticket's re-run, not on DN-M2-002's sign-off.
- **Engineering rulings (this plan):**
  - `lang=en`: English is the app's only UI language (strings.xml).
  - `SEARCH_LIMIT` = 50: HERE's maximum is 100, and 50 already fills Dublin's Explore list in the investigation.
  - The re-run is in daytime, between 11:00 and 17:00 local time, because the 21:00 run dropped closed aquariums
    and museums.
- **Public repo:**
  - no place names, place IDs, result coordinates, raw provider responses or keys in code, tests, docs, commits or
    the PR;
  - kinds ("an aquarium") are fine;
  - test fixtures use invented names.
- **Architecture rules (CLAUDE.md):**
  - `discovery/` stays pure Kotlin;
  - provider models stay in `places/here/`;
  - unknown data is neutral;
  - never pad results.
- **Emulator:**
  - always `adb -s emulator-5554`, Park, user 10;
  - send `geo fix` twice;
  - no `uiautomator`;
  - screenshots go to the session scratchpad.
- **Checks:** `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` must pass before the PR. detekt
  MaxLineLength is 120.
- **Attribution:** none, in commits or the PR.

## Review Focus

1. **The fakes keep working:** the fake Family place is `family_attraction`, so that kind keeps its strong weight.
   Expect the fake Family list to be non-empty; `DiscoverUseCaseTest.ranksTheCategoryAroundTheOrigin` pins it.
2. **An amusement park as a secondary category:** for example an adventure park filed under Landmark with
   Amusement Park secondary. Expect it still to pass the floor (20 × 0.5 = 10). Pinned in Task 1.
3. **An amusement park next door against a zoo a few km away.** Expect the zoo first. Pinned in Task 1.
4. **The cap at 3, and repeated records:** a provider listing a place twice must not use up a cap. Expect the cap
   tests to cover Outdoors at 3 and Family at 2. Pinned in Task 2.
5. **A name leaking through the re-run record, a commit or the PR.** Expect none. Pinned by the name-leak check in
   Task 6.

---

### Task 1: Family: finer kinds, a weak amusement park, aquariums

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/places/here/HereCategories.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/discovery/CategoryConfig.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/places/here/HereCategoriesTest.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/discovery/BasicRecommendationEngineTest.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/places/here/HerePlacesRepositoryTest.kt` (categories string)

**Interfaces:**
- Produces:
  - kinds `amusement_park`, `aquarium`, `water_park` and `childrens_museum` from `kindFor()`;
  - Family `kindWeights` including them;
  - `HERE_CATEGORIES[FAMILY]` =
    `"550-5520-0208,550-5520-0211,550-5520-0207,550-5520-0357,300-3100-0027"`.

- [ ] **Step 1: Write the failing tests**

In `HereCategoriesTest.theMostSpecificCategoryWins`, replace the `family_attraction` line with:

```kotlin
        assertEquals("amusement_park", kindFor("550-5520-0207"))
        assertEquals("aquarium", kindFor("550-5520-0211"))
        assertEquals("water_park", kindFor("550-5520-0357"))
        assertEquals("childrens_museum", kindFor("300-3100-0027"))
```

Add to `HereCategoriesTest`:

```kotlin
    // DN-M2-003: HERE files aquariums under their own code, not Zoo; without it they only match at half weight.
    @Test
    fun familySearchesForAquariums() {
        assertTrue("550-5520-0211" in HERE_CATEGORIES.getValue(DiscoveryCategory.FAMILY).split(","))
    }
```

Add to `BasicRecommendationEngineTest`, after `primaryKindMatchesRankAboveSecondaryOnes`:

```kotlin
    // Product Lead, 2026-10-07 (DN-M2-003): HERE files salons, cafés and escape rooms under Amusement Park, so it
    // weighs less than a zoo, and a zoo a few km away outranks an amusement park next door.
    @Test
    fun aZooOutranksAnAmusementParkNextDoor() {
        val amusement = testPlace("amusement", "amusement_park", metersNorth = 100)
        val zoo = testPlace("zoo", "zoo", metersNorth = 3_000)
        assertEquals(listOf("zoo", "amusement"), ranked(FAMILY, amusement, zoo))
    }

    // DN-M2-003: an adventure park filed under another category, with Amusement Park as a secondary one, still passes
    // the floor.
    @Test
    fun aSecondaryAmusementParkStillPassesTheFloor() {
        assertEquals(listOf("adventure"), ranked(FAMILY, testPlace("adventure", "landmark", "amusement_park")))
    }
```

In `HerePlacesRepositoryTest.browseAsksForTheCategoryAroundTheOrigin`, set the expected categories to
`"550-5520-0208,550-5520-0211,550-5520-0207,550-5520-0357,300-3100-0027"`.

- [ ] **Step 2: Run them and watch them fail**

Run:
`./gradlew :app:testDebugUnitTest --tests '*HereCategoriesTest' --tests '*BasicRecommendationEngineTest' --tests '*HerePlacesRepositoryTest' --console=plain`

Expected: FAIL. `theMostSpecificCategoryWins` gets `family_attraction`, `familySearchesForAquariums` fails its
assertion, both engine tests get `[zoo]` or `[]` (amusement_park has no weight yet), and the browse test fails on
the categories string.

- [ ] **Step 3: Implement**

`HereCategories.kt`: in `HERE_CATEGORIES`, set
`FAMILY to "550-5520-0208,550-5520-0211,550-5520-0207,550-5520-0357,300-3100-0027",`.
In `KINDS`, replace the three `family_attraction` lines with:

```kotlin
    "550-5520-0207" to "amusement_park",
    "550-5520-0211" to "aquarium",
    "550-5520-0357" to "water_park",
    "300-3100-0027" to "childrens_museum",
```

`CategoryConfig.kt`: after `private const val WEAK_MATCH = 15`, add:

```kotlin
// HERE files salons, cafés and escape rooms under Amusement Park, often as their primary category; at 20 a zoo or
// play centre a few km away outranks one next door, and as a secondary category (10) it still passes the floor
// (Product Lead, 2026-10-07, DN-M2-003).
private const val AMUSEMENT_MATCH = 20
```

and set Family's weights to:

```kotlin
        kindWeights = strong("playground", "zoo", "aquarium", "water_park", "childrens_museum", "family_attraction") +
            mapOf("park" to WEAK_MATCH, "amusement_park" to AMUSEMENT_MATCH),
```

- [ ] **Step 4: Run them and watch them pass**

Run the Step 2 command. Expected: PASS. Then run the module:
`./gradlew :app:testDebugUnitTest --console=plain`. Expected: BUILD SUCCESSFUL. `everyRequestedCategoryMapsToAKindItsCategoryAccepts` passes, so every requested code maps to a weighted kind.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/places/here/HereCategories.kt \
  app/src/main/java/com/kanyandula/discovernearby/discovery/CategoryConfig.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/here/HereCategoriesTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/discovery/BasicRecommendationEngineTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/here/HerePlacesRepositoryTest.kt
git commit -m "Split HERE's Family codes and weaken amusement parks"
```

---

### Task 2: Outdoors cap 3, open now 2

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/discovery/CategoryConfig.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/discovery/CategoryConfigTest.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/discovery/BasicRecommendationEngineTest.kt`

**Interfaces:**
- Consumes: Task 1's `CategoryConfig.kt`.
- Produces: `OPEN_NOW_BONUS = 2.0`; Outdoors `maxPerKind = 3`.

- [ ] **Step 1: Write the failing tests**

`CategoryConfigTest`: rename `diversityCapsAllButCoffeeFoodAndScenic` to `diversityCapsFollowTheProductDecisions`,
set its comment to `// Product Lead, 2026-10-07: three of a kind in Outdoors, two in Family and Explore; Coffee, Food
and Scenic uncapped.` and expect `OUTDOORS to 3`.

`BasicRecommendationEngineTest`:
- Replace `keepsAtMostTwoOfAKind` with:

```kotlin
    // Light diversity (Product Lead, 2026-10-07): Outdoors keeps the three best parks (a city's parks fill HERE's
    // results), then the beach; the fourth park is dropped, not moved down.
    @Test
    fun outdoorsKeepsAtMostThreeOfAKind() {
        val parks = List(4) { testPlace("park-$it", "park", metersNorth = 100 * (it + 1)) }
        val beach = testPlace("beach", "beach", metersNorth = 900)
        assertEquals(
            listOf("park-0", "park-1", "park-2", "beach"),
            ranked(OUTDOORS, *(parks + beach).toTypedArray()),
        )
    }

    // Family and Explore keep two of a kind.
    @Test
    fun familyAndExploreKeepAtMostTwoOfAKind() {
        mapOf(FAMILY to "zoo", EXPLORE to "museum").forEach { (category, kind) ->
            val places = List(3) { testPlace("p$it", kind, metersNorth = 100 * (it + 1)) }
            assertEquals(category.name, listOf("p0", "p1"), ranked(category, *places.toTypedArray()))
        }
    }
```

- In `anUnknownPrimaryKindCountsAsTheKindItMatched`, add `val p3 = testPlace("p3", "park", metersNorth = 4_000)`,
  pass it after `p2`, and expect `listOf("p1", "p2", "p3")`.
- In `aRepeatedPlaceDoesNotUseUpTheCap`, use `"zoo"` for `a`, `b` and `c`, and rank in `FAMILY`. The expected
  result is unchanged: `listOf("a", "b")`.
- Add, after `openNowAHighRatingAndAWeightedAmenityRaiseAPlace`:

```kotlin
    // Product Lead, 2026-10-07 (DN-M2-003): open now breaks near-ties but doesn't carry a place 3.5 km further away
    // past a nearer one with the same match.
    @Test
    fun openNowDoesNotOutweighAFewKilometres() {
        val open = testPlace("open", "landmark", metersNorth = 3_800).copy(isOpenNow = true)
        val near = testPlace("near", "landmark", metersNorth = 300)
        assertEquals(listOf("near", "open"), ranked(EXPLORE, open, near))
    }
```

- In `primaryKindMatchesRankAboveSecondaryOnes`, change the comment's "(half, if the secondary is open now)" to
  "(about two thirds, if the secondary is open now)", and "DN-M2-002's benchmark tunes NEARNESS_WEIGHT" to
  "DN-M2-002's benchmark kept NEARNESS_WEIGHT".

- [ ] **Step 2: Run them and watch them fail**

Run:
`./gradlew :app:testDebugUnitTest --tests '*CategoryConfigTest' --tests '*BasicRecommendationEngineTest' --console=plain`

Expected: FAIL:
- `diversityCapsFollowTheProductDecisions` (Outdoors is 2);
- `outdoorsKeepsAtMostThreeOfAKind` (park-2 dropped);
- `anUnknownPrimaryKindCountsAsTheKindItMatched` (p3 dropped);
- `openNowDoesNotOutweighAFewKilometres` (open first).

- [ ] **Step 3: Implement**

`CategoryConfig.kt`:
- `const val OPEN_NOW_BONUS = 2.0`, with its KDoc extended:
  `/** Added when the provider says the place is open now; unknown or closed adds nothing. It breaks near-ties, worth
  about 1.5 km in Explore (Product Lead, 2026-10-07, DN-M2-003). */`
- after `DIVERSITY_CAP`, add:

```kotlin
// A city's parks fill HERE's Outdoors results (Product Lead, 2026-10-07, DN-M2-003).
private const val OUTDOORS_CAP = 3
```

- Outdoors: `maxPerKind = OUTDOORS_CAP,`

- [ ] **Step 4: Run them and watch them pass**

Run the Step 2 command, then `./gradlew :app:testDebugUnitTest --console=plain`. Expected: both PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/discovery/CategoryConfig.kt \
  app/src/test/java/com/kanyandula/discovernearby/discovery/CategoryConfigTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/discovery/BasicRecommendationEngineTest.kt
git commit -m "Allow three parks in Outdoors and lower the open-now bonus"
```

---

### Task 3: HERE answers in English, 50 results

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/places/here/HerePlacesRepository.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/places/here/HerePlacesRepositoryTest.kt`

**Interfaces:**
- Produces: every HERE request carries `lang=en`; `/browse` carries `limit=50`.

- [ ] **Step 1: Write the failing tests**

In `browseAsksForTheCategoryAroundTheOrigin`, expect `"50"` for `limit` and add
`assertEquals("en", url.queryParameter("lang"))`. In `detailsLookUpThePlaceById`, add
`assertEquals("en", url.queryParameter("lang"))`.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*HerePlacesRepositoryTest' --console=plain`
Expected: FAIL: `limit` is `20`, and `lang` is null.

- [ ] **Step 3: Implement**

In `HerePlacesRepository.kt`:

```kotlin
// HERE's nearest 20 covered only about 200 m of a dense city centre (DN-M2-003); 100 is its maximum.
private const val SEARCH_LIMIT = 50

// The app's only UI language (strings.xml). Without it HERE answers in the place's own language, such as Irish.
private const val RESPONSE_LANGUAGE = "en"
```

In `get()`, add the language next to the key, so both endpoints carry it:

```kotlin
        val body = if (apiKey.isBlank()) {
            null
        } else {
            fetch(url.newBuilder().addQueryParameter("lang", RESPONSE_LANGUAGE).addQueryParameter("apiKey", apiKey).build())
        }
```

If that line passes 120 characters, build the URL in a local `val withKey = …` first.

- [ ] **Step 4: Run them and watch them pass**

Run the Step 2 command, then `./gradlew :app:testDebugUnitTest --console=plain`. Expected: both PASS.
`failuresCarryNoKeyOrOrigin` still passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/places/here/HerePlacesRepository.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/here/HerePlacesRepositoryTest.kt
git commit -m "Ask HERE for English and 50 results"
```

---

### Task 4: Docs, and the full checks

**Files:**
- Modify: `docs/03-discover-nearby-engineering-implementation-plan.md` (§10 "Implemented" block)
- Modify: `docs/adr/0001-poi-provider.md` ([H3] list, "Selected provider" table, "Known limitations")
- Modify: `CLAUDE.md` (the Ranking bullet)

- [ ] **Step 1: docs/03 §10**

Under `**Implemented (DN-M2-001):**`, change the cap line to "at most three results per primary kind in Outdoors and
two in Family and Explore". After the block's last bullet, add:

```markdown
- **DN-M2-003 (Product Lead, 2026-10-07):** after the benchmark, HERE's Family codes map to finer kinds
  (amusement park, aquarium, water park, children's museum). An amusement park weighs 20, since HERE files
  non-family businesses under it; the others weigh 30. The open-now bonus is 2, so it breaks near-ties without
  outweighing a few kilometres.
```

- [ ] **Step 2: ADR-001**

- **[H3]:** add `550-5520-0211 Aquarium` to the Family codes line.
- **Selected provider table:** add the same code to the Family row, marked `(added DN-M2-003)`.
- **Known limitations:** add:

```markdown
- **HERE data quality (DN-M2-003 investigation, 2026-10-07).** These are provider data limitations; the app has no
  field to detect them:
  - Some records carry a wrong position that agrees with their own address and access point: a Scenic Point about
    60 km from where it shows, and several Dublin places at one street-level point.
  - Non-family businesses carry Amusement Park, sometimes as their primary category; it weighs less in Family.
  - Some places have duplicate records.

  They are reported through HERE's map feedback. The re-run records whether they still appear.
```

- [ ] **Step 3: CLAUDE.md**

In the Ranking bullet, change "at most two per primary kind in Outdoors, Family and Explore" to "at most three per
primary kind in Outdoors and two in Family and Explore", and add a sentence:
"DN-M2-003: HERE's Family codes map to finer kinds (an amusement park weighs 20), open now adds 2, and HERE answers
in English (`lang=en`) with up to 50 results."

- [ ] **Step 4: Full checks**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL, with every test passing (the suite was 231 and gains the new tests).

- [ ] **Step 5: Commit**

```bash
git add docs/03-discover-nearby-engineering-implementation-plan.md docs/adr/0001-poi-provider.md CLAUDE.md
git commit -m "Record the DN-M2-003 ranking changes and HERE's data limits"
```

---

### Task 5: The daytime re-run (10 cells)

**Files:**
- Create (vault, not the repo): `~/.claude/projects/Discover Nearby/benchmarks/<run date, YYYY-MM-DD>-m2-rerun.md`
- Screenshots: `$S/rerun/` in the session scratchpad.

**Interfaces:**
- Consumes: the branch's app (Tasks 1–3).
- Produces: a vault note in DN-M2-002's format: per cell, `### <Location> × <Category>`, rows
  `<n>. <name> — <d> km — suggested: ACCEPT|REJECT|UNSURE (<reason>)`, the four docs/04 lines ending
  `(suggested)`, a `Notes:` line and an empty `Product:` line. Frontmatter `status: awaiting-product-review`.

The cells:
- **Failed:** Dublin × Family, Galway × Family, Galway × Scenic.
- **Tuning:** Dublin × Outdoors, Dublin × Explore, Greystones × Explore.
- **Position checks:** Dublin × Coffee, Dublin × Food, Galway × Food, Galway × Explore.

- [ ] **Step 1: Prepare between 11:00 and 17:00 local time**

```bash
date +%H:%M
adb -s emulator-5554 get-state
adb -s emulator-5554 shell am get-current-user
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
grep -c '^here.apiKey=.' local.properties
git log --oneline -1
```

Expected: a time between 11:00 and 17:00; `device`; `10`; the install succeeds; `1`; the branch head.

- [ ] **Step 2: Capture the cells per location**

Use DN-M2-002's method (`docs/superpowers/plans/2026-10-07-dn-m2-002-relevance-benchmark.md`, Task 1, Steps 2–4):
- send `geo fix` twice, then relaunch;
- tap the tile;
- take the top screenshot, then swipe and take the end screenshot. Swipe only when a 4th row shows, since a swipe
  on 3 rows or fewer lands as a tap;
- press Back.

Tiles: Coffee (196,268), Food (512,268), Outdoors (828,268), Family (196,512), Scenic (512,512), Explore (828,512).

Locations, as `lon lat`:
- A Greystones `-6.0633 53.1440`: Explore;
- B Dublin `-6.2603 53.3498`: Coffee, Food, Outdoors, Family, Explore;
- C Galway `-9.0568 53.2707`: Food, Family, Scenic, Explore.

Expected: 10 cells captured, each with a State line (Content, Empty, Error, Permission).

- [ ] **Step 3: Suggest judgments and write the note**

Use DN-M2-002's rubric (that plan, Task 2):
- ACCEPT when the name shows a matching kind;
- REJECT when it shows a non-matching kind;
- UNSURE when it doesn't show its kind. Never guess.

Per cell, also record:
- **Notes:** whether it changed from 2026-10-07, and any position defect (a place shown at a distance its name
  contradicts).
- **Setup:** the time of day and the branch SHA.

Set `status: awaiting-product-review`.

- [ ] **Step 4: Stop for the Product Lead**

Report the note's path and a summary: per cell, the rows and the changes since 2026-10-07. Wait for
`status: signed-off`. This is the only pause in the plan.

---

### Task 6: The name-free record and the PR

**Files:**
- Create: `docs/benchmarks/<run date>-m2-rerun.md`
- Modify: `docs/benchmarks/2026-10-07-m2.md` (one dated line under Findings, pointing at the re-run)
- Modify: `docs/04-discover-nearby-test-demo-plan.md` §4 (a pointer after the M2 run line)
- Modify: `CLAUDE.md` (the Relevance benchmark bullet and Next)

**Interfaces:**
- Consumes: the signed-off vault note from Task 5.

- [ ] **Step 1: Write the repo record**

Use the 2026-10-07 record's format:
- **Setup:** time of day and branch.
- **Grid:** 10 cells, `a/n, u?` plus marks.
- **Per-cell table:** the Product verdict, the four rule verdicts and a name-free note.
- **Changes since 2026-10-07:** per cell, by kind.
- **Investigation:** DN-M2-003's causes, by kind:
  - HERE's amusement code on non-family businesses;
  - the one-kind cap;
  - the missing Aquarium code;
  - most 0.0 km rows are real places on the test point's street, while a few Dublin records share one street-level
    point;
  - the mispositioned Scenic record agrees with its own address.
- **Sign-off:** as recorded, including M2's exit.

UNSURE stays uncertain: never counted for or against a cell.

- [ ] **Step 2: Pointers**

- **`docs/benchmarks/2026-10-07-m2.md`:** under the Positions finding, add
  `  DN-M2-003 investigation (2026-10-07): most 0.0 km rows are real places on the test point's street; see
  docs/benchmarks/<run date>-m2-rerun.md.`
- **docs/04 §4:** after the M2 run line, add `**M2 re-run (DN-M2-003, <run date>):** \`docs/benchmarks/<run date>-m2-rerun.md\`.`
- **CLAUDE.md:** record the re-run's outcome and whether M2 has exited, as the Product Lead signed it off. Set Next
  to M3/M4 per docs/05 if M2 exits; otherwise to the follow-up.

- [ ] **Step 3: The name-leak check**

```bash
N="$HOME/.claude/projects/Discover Nearby/benchmarks"
grep -hE '^[0-9]\. ' "$N/2026-10-07-m2.md" "$N/<run date>-m2-rerun.md" \
  | sed -E 's/^[0-9]\. (.*) — [0-9.]+ km.*/\1/' | sort -u > "$S/rerun/names.txt"
git diff main -- . | grep -c -F -f "$S/rerun/names.txt"
git log main..HEAD --format=%B | grep -c -F -f "$S/rerun/names.txt"
```

Expected: `0` and `0`. Also run the word-by-word pass from DN-M2-002, and read any line that matches; only generic
words and the three test locations may match.

- [ ] **Step 4: Commit**

```bash
git add docs/benchmarks docs/04-discover-nearby-test-demo-plan.md CLAUDE.md
git commit -m "Record the M2 re-run after ranking tuning"
```

---

## After the tasks

- **Final whole-branch review** (executing-plans): code and docs, against this plan's Review Focus. It also checks
  the re-run record against the vault note and for leaks.
- **PR** with `pr-description`: DN-M2-003, its acceptance criteria, the Product decisions, the re-run outcome and
  the name-leak check.
- **Step 6 after the merge:**
  - verify MERGED in its own call;
  - set DN-M2-003 to `done`;
  - update NOW.md and BACKLOG.md, and record M2's exit if signed off;
  - delete the branch.
