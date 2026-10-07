# DN-M1-003 Live Place Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- Verify live HERE place details (`/lookup`).
- Show HERE's copyright notice, "© {current year} HERE", on Recommendations and Place Details whenever the data is
  HERE's.
- Record the attribution evidence and the Product decision in ADR-001.

**Architecture:**
- **Who the notice credits:** the copyright holder becomes a field of `PlaceSummary` (`attribution: String?`, null
  for the fakes). Both screens that show provider data can then read it.
- **One field:** `PlaceDetails.attribution`, always null today, is removed.
- **The notice itself:** one small component, `ProviderAttribution`, draws it in the muted style. It sits under the
  Recommendations list and at the bottom left of Place Details, level with Navigate.
- **Unchanged:** the details flow (Loading, Content, summary-only fallback) from DN-M0-005.

**Tech Stack:** Kotlin, Jetpack Compose, `java.time.Year` (minSdk 29), Robolectric, the AAOS emulator.

**Spec:**
- the ticket `~/.claude/projects/Discover Nearby/tickets/DN-M1-003-live-place-details.md`, including its
  2026-10-07 notes: the research quotes and the Product decision;
- docs/02 §7 and §18; docs/03 §8, §14 and §19;
- ADR-001: V6a, V6b and Field availability.

## Global Constraints

- **Ticket acceptance criteria:**
  - Opening a place uses the provider mapper and fills `PlaceDetails`.
  - Summary fields stay available while details load, and if the request fails.
  - Rating, opening status and amenities appear only when supplied or reliably derived.
  - Attribution is shown on the screens, in the form ADR-001 requires.
  - Provider data isn't kept beyond what ADR-001 allows.
- **Product Lead decision (the user, 2026-10-07):**
  - the wording is "© {current year} HERE", with the year from the device clock;
  - a muted line on Recommendations and on Place Details;
  - shown only when the data is live HERE, so the fakes show nothing.
- **Legal:** the brand guidance doesn't address apps without a map, so that question goes to Legal. Engineering
  doesn't interpret licence language: quote it, cite it, route it to Legal.
- **User text:** all of it lives in `strings.xml`. The holder name ("HERE") is data from the mapper; the notice's
  format is a string resource.
- **Rotary:** no behaviour change (ADR-002). The notice is plain text and isn't focusable.
- **Public repo:** no provider content (place names, IDs or coordinates of results) goes into commits or tracked
  docs. Emulator results are recorded as counts and states.
- **Emulator:** always `adb -s emulator-5554`. Send `geo fix` twice (DN-M1-002). No `uiautomator`.
- **Checks:** `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`, plus the import-order check.
- **Attribution:** none, in commits or the PR.

## Review Focus

1. **Details fail on live data** (offline). Expect the summary, the "More details unavailable right now" note,
   Navigate, and still the HERE notice, because the summary is HERE's. Pinned by
   `PlaceDetailsScreenTest.hereDataShowsTheNoticeInEveryState` (Task 2) and the offline check in Task 3.
2. **A place with every fact plus the notice on the 468 dp panel.** Expect both the summary-only note and the notice
   to fit, with the notice below the note. Pinned by `PlaceDetailsFitTest.summaryOnlyNoteFitsTheReferencePanel`
   (extended in Task 2).
3. **Fake data** (CI, tests, `--es scenario`). Expect no notice: the fakes are not HERE's. Pinned by
   `RecommendationsScreenTest.resultsWithoutAnAttributionShowNoNotice` and
   `PlaceDetailsScreenTest.dataWithoutAnAttributionShowsNoNotice` (Task 2), and the scenario check in Task 3.
4. **A route saved before this change** (process death across an upgrade). Expect the summary to decode with no
   notice, not to crash. The new field has a default. Pinned by `JsonNavTypeTest`, unchanged and still green
   (Task 1).
5. **`/lookup` returns hours.** Expect them on Place Details, proving the live details call. Pinned by the
   emulator check in Task 3, which opens rows until one shows hours.

---

## File structure

| File | Change | Responsibility |
| --- | --- | --- |
| `app/src/main/.../model/Place.kt` | Modify | `PlaceSummary.attribution`; `PlaceDetails.attribution` removed |
| `app/src/main/.../places/here/HereMapper.kt` | Modify | attribution "HERE" on every HERE summary |
| `app/src/main/.../places/fake/FakePlacesRepository.kt` | Modify | drop the removed argument |
| `app/src/main/.../ui/components/ProviderAttribution.kt` | Create | "© {year} {holder}", muted, or nothing |
| `app/src/main/.../ui/screens/RecommendationsScreen.kt` | Modify | the notice under the list |
| `app/src/main/.../ui/screens/PlaceDetailsScreen.kt` | Modify | the notice at the bottom left |
| `app/src/main/res/values/strings.xml` | Modify | `provider_attribution` |
| `app/src/main/.../AppContainer.kt` | Modify | one log line when there is no key |
| tests: `HereMapperTest`, `RecommendationsScreenTest`, `PlaceDetailsScreenTest`, `PlaceDetailsFitTest`, `PlaceDetailsViewModelTest`, `ui/TestScreens.kt` | Modify | the cases above; a stale comment |
| `docs/adr/0001-poi-provider.md`, `docs/02-…` §18, `docs/03-…` §8, `CLAUDE.md` | Modify | evidence, decision, model sketch, state |

`…` is `java/com/kanyandula/discovernearby`.

---

### Task 1: The copyright holder on PlaceSummary

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/model/Place.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/places/here/HereMapper.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/places/fake/FakePlacesRepository.kt:57,60`
- Test: `app/src/test/java/com/kanyandula/discovernearby/places/here/HereMapperTest.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModelTest.kt:36`,
  `PlaceDetailsScreenTest.kt:69` (drop the removed argument)

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `PlaceSummary.attribution: String? = null`: the data's copyright holder, e.g. "HERE";
  - `PlaceDetails(summary, openingSummary)`, which no longer has `attribution`.

- [ ] **Step 1: Write the failing assertions**

In `HereMapperTest.aFullItemMapsEveryKnownField`, after `assertEquals(true, place.isOpenNow)`, add:

```kotlin
        assertEquals("HERE", place.attribution) // DN-M1-003: HERE's notice goes with its data (ADR-001 V6a)
```

In `HereMapperTest.detailsAddTheOpeningHoursText`, replace:

```kotlin
        assertNull(details.attribution)
```

with:

```kotlin
        assertEquals("HERE", details.summary.attribution)
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*HereMapperTest' --console=plain`
Expected: compilation FAILS: `Unresolved reference 'attribution'` on `PlaceSummary`.

- [ ] **Step 3: Move the field**

In `Place.kt`, replace:

```kotlin
    val travelTimeMinutes: Int?, // only if provider supplies it
)
```

with:

```kotlin
    val travelTimeMinutes: Int?, // only if provider supplies it
    val attribution: String? = null, // the data's copyright holder, e.g. "HERE"; null when no notice is needed
)
```

and replace:

```kotlin
data class PlaceDetails(
    val summary: PlaceSummary,
    val openingSummary: String?,
    val attribution: String?,
)
```

with:

```kotlin
data class PlaceDetails(
    val summary: PlaceSummary,
    val openingSummary: String?,
)
```

In `HereMapper.kt`, add above `internal fun HereItem.toSummary()`:

```kotlin
// The holder HERE's brand guidance names in its notice, "© 20XX HERE" (ADR-001 V6a).
private const val HERE_ATTRIBUTION = "HERE"
```

In `toSummary`, after `travelTimeMinutes = null,`, add:

```kotlin
        attribution = HERE_ATTRIBUTION,
```

In `toDetails`, delete the line `attribution = null,`, and replace its KDoc:

```kotlin
/** Details add the opening-hours text. Attribution stays null until HERE's brand guidance is read (ADR-001 V6a). */
```

with:

```kotlin
/** Details add the opening-hours text; the copyright notice comes with the summary. */
```

In `FakePlacesRepository.kt`, remove `, attribution = null` from both `PlaceDetails(…)` constructions (lines 57 and
60).

In `PlaceDetailsViewModelTest.kt:36` and `PlaceDetailsScreenTest.kt:69`, remove `, attribution = null` from the
`PlaceDetails(…)` call.

- [ ] **Step 4: Run them and see them pass, then the whole suite**

Run: `./gradlew :app:testDebugUnitTest --tests '*HereMapperTest' --console=plain`
Expected: PASS.

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: PASS (207). `JsonNavTypeTest` stays green: the route JSON handles the new defaulted field (Review
Focus 4).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/model/Place.kt \
  app/src/main/java/com/kanyandula/discovernearby/places/here/HereMapper.kt \
  app/src/main/java/com/kanyandula/discovernearby/places/fake/FakePlacesRepository.kt \
  app/src/test/java/com/kanyandula/discovernearby/places/here/HereMapperTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsViewModelTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreenTest.kt
git commit -m "Carry the data's copyright holder on PlaceSummary"
```

---

### Task 2: The notice on Recommendations and Place Details

**Files:**
- Create: `app/src/main/java/com/kanyandula/discovernearby/ui/components/ProviderAttribution.kt`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt` (the `Content`
  branch)
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreen.kt` (the left column;
  the KDoc)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `RecommendationsScreenTest`, `PlaceDetailsScreenTest`, `PlaceDetailsFitTest`

**Interfaces:**
- Consumes: `PlaceSummary.attribution` (Task 1).
- Produces: `@Composable fun ProviderAttribution(holder: String?, modifier: Modifier = Modifier)`, in package
  `com.kanyandula.discovernearby.ui.components`.

- [ ] **Step 1: Write the failing tests**

`RecommendationsScreenTest`, add the imports if missing: `androidx.compose.ui.test.assertCountEquals`,
`androidx.compose.ui.test.onAllNodesWithText`, `java.time.Year`. Then add:

```kotlin
    // DN-M1-003 (ADR-001 V6a; Product decision): live HERE results carry HERE's copyright notice.
    @Test
    fun liveResultsShowTheProvidersNotice() {
        val live = testPlace("Live Cafe", "cafe").copy(attribution = "HERE")
        state = Content(requestId = 1, recommendations = listOf(row(live, 300)))
        rule.onNodeWithText("© ${Year.now().value} HERE").assertIsDisplayed()
    }

    // Review Focus 3: the fakes are not HERE's, so they show no notice.
    @Test
    fun resultsWithoutAnAttributionShowNoNotice() {
        state = Content(requestId = 1, recommendations = listOf(row(testPlace("Fake Cafe", "cafe"), 300)))
        rule.onAllNodesWithText("©", substring = true).assertCountEquals(0)
    }
```

`PlaceDetailsScreenTest`, add the import `java.time.Year`. Then add:

```kotlin
    // Review Focus 1: the notice comes with the summary, so it shows while loading, with details, and on the
    // summary-only fallback alike.
    @Test
    fun hereDataShowsTheNoticeInEveryState() {
        val live = place.copy(attribution = "HERE")
        val notice = "© ${Year.now().value} HERE"
        listOf(Loading(live), Content(PlaceDetails(live, openingSummary = null)), SummaryOnly(live)).forEach {
            state = it
            rule.onNodeWithText(notice).assertIsDisplayed()
        }
    }

    // Review Focus 3
    @Test
    fun dataWithoutAnAttributionShowsNoNotice() {
        state = SummaryOnly(place)
        rule.onAllNodesWithText("©", substring = true).assertCountEquals(0)
    }
```

`PlaceDetailsFitTest.summaryOnlyNoteFitsTheReferencePanel` (Review Focus 2). Add the import `java.time.Year`.

In the test, add `attribution = "HERE",` to the `everything` copy, after the `attributes = …` line. After the last
`assertTrue(…)`, add:

```kotlin
        val notice = rule.onNodeWithText("© ${Year.now().value} HERE")
        notice.assertIsDisplayed()
        assertTrue(notice.getUnclippedBoundsInRoot().bottom <= ReferencePanelHeight)
        assertTrue(note.getUnclippedBoundsInRoot().bottom <= notice.getUnclippedBoundsInRoot().top)
```

Add `import androidx.compose.ui.test.assertIsDisplayed` to `PlaceDetailsFitTest` if it's missing.

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsScreenTest' --tests '*PlaceDetailsScreenTest' --tests '*PlaceDetailsFitTest' --console=plain`
Expected:
- these FAIL, because no node has the notice text: `liveResultsShowTheProvidersNotice`,
  `hereDataShowsTheNoticeInEveryState` and `summaryOnlyNoteFitsTheReferencePanel`;
- the two "no notice" tests PASS. They pin the fakes, which already show nothing.

- [ ] **Step 3: The component and the string**

`strings.xml`, add after `details_unavailable`:

```xml
    <!-- The data provider's copyright notice (ADR-001 V6a): "© 2026 HERE". -->
    <string name="provider_attribution">© %1$d %2$s</string>
```

Create `ui/components/ProviderAttribution.kt`:

```kotlin
package com.kanyandula.discovernearby.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.theme.OnSurfaceVariant
import java.time.Year

/**
 * The data provider's copyright notice, "© {year} {holder}", in the muted style; nothing when the data needs none
 * (the fakes). HERE's brand guidance asks for "© 20XX HERE" (ADR-001 V6a); the year is the device's.
 */
@Composable
fun ProviderAttribution(holder: String?, modifier: Modifier = Modifier) {
    if (holder == null) return
    val year = remember { Year.now().value }
    Text(
        text = stringResource(R.string.provider_attribution, year, holder),
        style = MaterialTheme.typography.labelMedium,
        color = OnSurfaceVariant,
        modifier = modifier,
    )
}
```

- [ ] **Step 4: Place it on both screens**

`RecommendationsScreen.kt`, in the `is RecommendationsUiState.Content ->` branch, replace:

```kotlin
            is RecommendationsUiState.Content ->
                LazyColumn(modifier = body, verticalArrangement = Arrangement.spacedBy(RowGap)) {
```

with:

```kotlin
            is RecommendationsUiState.Content -> Column(
                modifier = body,
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowGap)) {
```

The `items(…)` block keeps its indentation. After the `LazyColumn`'s closing brace (16 spaces), add:

```kotlin
                // HERE's notice with HERE's data (ADR-001 V6a); the fakes carry none.
                ProviderAttribution(
                    state.recommendations.firstNotNullOfOrNull { it.place.attribution },
                    Modifier.align(Alignment.End),
                )
            }
```

Add the `Column`, `Alignment` and `ProviderAttribution` imports if they're missing. If detekt reports LongMethod on
`RecommendationsScreen` (limit 60), move this branch into a private `RecommendationList(state, returnFocus,
onPlaceSelected, modifier)` composable and record a ruling.

`PlaceDetailsScreen.kt`, replace:

```kotlin
                Column(modifier = Modifier.weight(1f)) {
                    Facts(state.summary, distanceMeters, openingSummary)
                    if (state is PlaceDetailsUiState.SummaryOnly) DetailsUnavailable()
                }
```

with:

```kotlin
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Facts(state.summary, distanceMeters, openingSummary)
                    if (state is PlaceDetailsUiState.SummaryOnly) DetailsUnavailable()
                    Spacer(Modifier.weight(1f))
                    // Bottom left, level with Navigate: HERE's notice with HERE's data (ADR-001 V6a).
                    ProviderAttribution(state.summary.attribution)
                }
```

Add the `fillMaxHeight`, `Spacer` and `ProviderAttribution` imports if they're missing. In the screen's KDoc, replace
`ponytail: no photo, attribution or place-kind label until ADR-001` with
`ponytail: no photo or place-kind label until ADR-001`.

- [ ] **Step 5: Run the tests and see them pass, then the whole suite**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecommendationsScreenTest' --tests '*PlaceDetailsScreenTest' --tests '*PlaceDetailsFitTest' --tests '*RotaryContractTest' --tests '*FocusRingTest' --console=plain`
Expected: PASS.

Run: `./gradlew detekt testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL; 211 tests (207 + 4).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/components/ProviderAttribution.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreen.kt \
  app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreen.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/kanyandula/discovernearby/ui/screens/RecommendationsScreenTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreenTest.kt \
  app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsFitTest.kt
git commit -m "Show HERE's copyright notice with HERE data"
```

---

### Task 3: Live details on the emulator

**Files:** none committed. The results go into the DN-M1-003 ticket as counts and states, with no place names.

**Interfaces:**
- Consumes: Tasks 1–2 installed.
- Produces: the evidence Task 4 records.

Setup:

```bash
P=com.kanyandula.discovernearby
S=/private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/75e889b9-af5f-479d-b87c-146177cf6a78/scratchpad
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain -q
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
for i in 1 2; do adb -s emulator-5554 emu geo fix -6.0633 53.1440; sleep 4; done     # Greystones
```

Run the step commands under `bash -c` (zsh doesn't split unquoted variables).

- [ ] **Step 1: The notice on a live list**

Cold launch (`am start -S --user 10 --activity-clear-task -n $P/.ui.MainActivity`), wait 16 s, tap Coffee
(196, 268), wait 16 s, then take a screenshot.
Expected: the live Coffee list, with "© {year} HERE" in the muted style under the list.

- [ ] **Step 2: Live details with hours** (Review Focus 5)

Tap the first row, wait 14 s, and take a screenshot. If it shows no opening-hours line, go Back and try rows 2 and 3
(row centres about y = 296, 410, 524). Stop at the first row that shows hours.
Expected:
- Place Details with the name, distance and Navigate;
- an opening-hours line on at least one of the three rows, which proves `/lookup` works;
- "© {year} HERE" at the bottom left;
- no "More details unavailable right now".

Record the row count tried, and which showed hours.

- [ ] **Step 3: Back**

Press Back (`input keyevent KEYCODE_BACK`) and take a screenshot.
Expected: the same live list. Back again: Discover.

- [ ] **Step 4: Offline details** (Review Focus 1)

Open Coffee (live list). Turn on airplane mode (`cmd connectivity airplane-mode enable`), tap the first row, wait
14 s, and take a screenshot. Then turn airplane mode off.
Expected: the summary (name, distance), "More details unavailable right now", Navigate, and "© {year} HERE".

- [ ] **Step 5: The fakes show no notice** (Review Focus 3)

Cold launch with `--es scenario NORMAL`, tap Coffee, wait 16 s, and take a screenshot.
Expected: the fake list (names ending in ", Greystones") and no "©" line.

- [ ] **Step 6: Record**

Leave the emulator at Greystones, Park, airplane mode off. Add the results to the DN-M1-003 ticket's notes, as counts
and states, plus the number of live calls.

---

### Task 4: ADR-001, docs, two small leftovers, full check

**Files:**
- Modify: `docs/adr/0001-poi-provider.md` (V6a table and note; V6b place-ID row; Evidence: `[H8]`, `[H9]`, `[H10]`)
- Modify: `docs/02-discover-nearby-ux-interaction-spec.md` §18
- Modify: `docs/03-discover-nearby-engineering-implementation-plan.md` §8 model sketch
- Modify: `CLAUDE.md` (the HERE bullet; "Next")
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ui/TestScreens.kt:17` (stale comment)
- Modify: `app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt` (one log line)

**Interfaces:**
- Consumes: Task 3's results.
- Produces: nothing.

- [ ] **Step 1: ADR-001 evidence**

Under `### HERE` in "Evidence", after the `[H7]` bullet, add:

```markdown
- **[H8]** HERE brand guidance, Copyright. <https://brand.here.com/legal/copyright/> (the Platform Terms §13.1 link
  to "HERE brand guidance" resolves here; no page date shown). Verbatim:
  - "Any material (e.g. the Map canvas, Web pages, Presentations, Marketing Material, Advertising, etc.) owned by
    HERE and used by external parties should have the following copyright notice: © 20XX HERE"
  - "In tablet and desktop sized products showing the map canvas, the HERE copyright should be displayed on the
    map. In mobile handsets, including watches and embedded in-car systems, the HERE copyright may alternatively be
    displayed in the 'About HERE' section in the Settings."
  - Nothing addresses an app that shows results without a map. **→ Legal.**
- **[H9]** HERE brand guidance, Trademarks. <https://brand.here.com/legal/trademarks/>. Covers the use of the HERE
  logo and wordmark. It does not require a logo to be displayed.
- **[H10]** Geocoding & Search v7, Lookup. <https://docs.here.com/geocoding-and-search/docs/endpoint-lookup-brief>.
  `GET https://lookup.search.hereapi.com/v1/lookup?id=…`. The sample response has `title`, `id`, `address`,
  `position`, `access`, `categories`, `references`, `contacts`. `references` names data suppliers; there is no
  copyright or attribution field to display.
```

- [ ] **Step 2: ADR-001 V6a and V6b**

Replace the V6a rows:

```markdown
| Required text | → Legal. §13.1: "You will ensure that all HERE Marks and copyright notices are present in the HERE Materials and Result in accordance with HERE brand guidance" [H1]. The brand guidance was not read |
| Required logo | → Legal; HERE brand guidance not read |
| Must be always visible? | Not established (brand guidance not read) |
| Can appear in About / Data Sources? | Not established |
```

with:

```markdown
| Required text | "© 20XX HERE" [H8]; §13.1 requires notices "in accordance with HERE brand guidance" [H1]. The responses carry no notice of their own [H10] |
| Required logo | Not required by [H8]; [H9] governs a logo if one is shown |
| Must be always visible? | Not stated for an app without a map [H8] → Legal |
| Can appear in About / Data Sources? | "In mobile handsets, including watches and embedded in-car systems, the HERE copyright may alternatively be displayed in the 'About HERE' section in the Settings" [H8]; tied to a map → Legal |
```

Replace the note that starts `DN-M1-001: \`PlaceDetails.attribution\` stays null` (2 lines) with:

```markdown
**Implemented (DN-M1-003; Product Lead decision, 2026-10-07):** "© {current year} HERE", a muted line under the
Recommendations list and at the bottom left of Place Details. It shows only for HERE data
(`PlaceSummary.attribution`), so the fakes show none. Whether that satisfies [H8] for an app without a map is
pending Legal.
```

In V6b, replace the row that starts `| May place IDs be stored? |` with:

```markdown
| May place IDs be stored? | → Legal. Not addressed in the sections read. The app writes no place data to storage. The opened place's summary (with its ID) travels in the navigation route, so it sits in the back stack's saved state while the task exists (DN-M0-005) |
```

- [ ] **Step 3: docs/02 §18 and docs/03 §8**

In docs/02 §18, replace the paragraph that starts `The attribution UI is designed once the provider is selected.`
with:

```markdown
**HERE (ADR-001 V6a; DN-M1-003):** "© {current year} HERE", a muted line under the Recommendations list and at the
bottom left of Place Details. It appears only when the data is HERE's; the fakes show none. HERE's guidance ties
placement to a map or an "About HERE" settings section. Placement in an app without a map is pending Legal.
```

In docs/03's §8 model sketch, move `attribution` from `PlaceDetails` to `PlaceSummary`. Replace:

```kotlin
    val travelTimeMinutes: Int?,          // only if provider supplies it
)

data class PlaceDetails(
    val summary: PlaceSummary,
    val openingSummary: String?,
    val attribution: String?,
)
```

with:

```kotlin
    val travelTimeMinutes: Int?,          // only if provider supplies it
    val attribution: String? = null,      // copyright holder for the notice, e.g. "HERE"
)

data class PlaceDetails(
    val summary: PlaceSummary,
    val openingSummary: String?,
)
```

- [ ] **Step 4: CLAUDE.md**

After the line `  - **Live categories:** Coffee is the first live category (ADR-001); the others are live but are accepted in M2.`,
add:

```markdown
  - **Attribution (DN-M1-003):** `ProviderAttribution` shows "© {year} HERE" under the list and on Place Details,
    for HERE data only (`PlaceSummary.attribution`). Placement without a map is pending Legal.
```

Replace the paragraph that starts `Next: DN-M1-003 (live place details` (through `is P3.`) with:

```markdown
Next: M1's remaining items are Legal's: the terms before production, and attribution placement without a map.
M2 (DN-M2-001, categories and ranking) follows. ADR-001 provisionally selects HERE. DN-TD-002 (Gradle/CI tuning) is
P3.
```

- [ ] **Step 5: Two small leftovers from DN-M1-002's review**

In `ui/TestScreens.kt`, replace:

```kotlin
/** The app's own container, as MainActivity passes it (Robolectric creates DiscoverApplication). */
```

with:

```kotlin
/** The app's own container, as MainActivity passes it (Robolectric creates the keyless TestDiscoverApplication). */
```

In `AppContainer.kt`, add `import android.util.Log` (sorted with the other `android.` imports). Add this as the first
member of the class:

```kotlin
    init {
        // Without a key the app serves the fakes; say so once, so a demo can't mistake them for live data.
        if (hereApiKey.isBlank()) Log.i("AppContainer", "No HERE key: serving the fake places")
    }
```

- [ ] **Step 6: The full check**

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
- BUILD SUCCESSFUL, with 211 tests;
- no `UNSORTED` lines;
- no CLAUDE.md lines printed;
- `0`.

- [ ] **Step 7: Commit**

```bash
git add docs/adr/0001-poi-provider.md docs/02-discover-nearby-ux-interaction-spec.md \
  docs/03-discover-nearby-engineering-implementation-plan.md CLAUDE.md \
  app/src/test/java/com/kanyandula/discovernearby/ui/TestScreens.kt \
  app/src/main/java/com/kanyandula/discovernearby/AppContainer.kt
git commit -m "Record HERE's attribution evidence and placement"
```

---

## After the tasks

- The final whole-branch review on the most capable model (executing-plans), then `simplify` on the `app/` diff.
- PR with `pr-description`: DN-M1-003, its acceptance criteria, the Product decision and the Legal question.
- Step 6 after merge:
  - verify MERGED in its own call;
  - DN-M1-003 `done`;
  - NOW.md and BACKLOG.md;
  - M1's open items: Legal on the terms, and attribution placement without a map.
