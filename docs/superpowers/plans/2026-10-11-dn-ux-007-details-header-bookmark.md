# DN-UX-007 Place Details Header Layout, Parking Badge and Bookmark — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> This project runs plans natively (inline), task by task.

**Goal:** Place Details rearranged as the Product Lead's artboard 3c: Back and the name head the page, the photo sits
under Back with the facts and description beside it, the tiles below. The parking icon becomes a contrast-safe
badge, and Save, Saved and the rail's Saved tab use a bookmark instead of a heart.

**Architecture:** One screen file changes layout (`PlaceDetailsScreen`): the shared `ScreenHeader` replaces the
`BackButton` beside the photo and the name in the facts column; the description joins the facts column, one line on
the summary-only fallback. Two vector drawables replace the hearts; the parking drawable carries its own colours and
draws untinted.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), vector drawables, JUnit, Robolectric, Compose UI test.

**Spec:** the vault ticket DN-UX-007 (`tickets/DN-UX-007-details-header-bookmark.md`) and the design canvas artboard
"3c · Place Details — proposed" (https://claude.ai/artifact/3v68QUCWeu87t7n2GFf8nf, 1024 × 768).

## Global Constraints

- Worktree `.claude/worktrees/dn-ux-007-details-header-bookmark`, branch `dn-ux-007-details-header-bookmark`; never
  commit on `main`; no AI attribution in commits or the PR.
- Commit subject ≤ 72 characters (50 preferred), body wrapped at 72, explaining why.
- Architecture rules (CLAUDE.md): only `ui/` imports Compose; all user text in `strings.xml` (this ticket adds none).
- Rotary unchanged (ADR-002): Back stays the first focusable and keeps initial focus; order Content → Save → Navigate →
  rail; no change to `ReturnFocus`, `focusRing` or the rail.
- Fit: the 4:3 reference panel is 836 × 468 dp (`ReferencePanelWidth`, `ReferencePanelHeight`).
- Parking badge exactly: Action blue `#2563EB` rounded square, white P (5.2:1).
- Emulator: `adb -s emulator-5554` only, after `emu avd name` prints `AAOS_AOSP_33_userdebug`; the DN-M2-004 session
  shares the emulator, so ask the user before installing.

## Review Focus

- Long place name: the header keeps one line with an ellipsis and Back stays whole (`ScreenHeader` already
  ellipsizes; the fit test uses a long name).
- No Tripadvisor photo: the facts column (about 164 dp) outgrows the 150 dp artwork; the row grows, the layout still
  fits (normal-state fit test, Task 1).
- Driving: the description goes and the facts column shrinks; nothing else moves (`theDescriptionHidesWhileDriving`).
- Summary-only fallback with every field: the note and a one-line description fit beside the photo (fit test, Task 1).
- Rotary: Back first, initial focus on Back, then Save, Navigate, rail (emulator check, Task 4).

---

### Task 1: Header layout and the one-line fallback description

**Files:**
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreen.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreenTest.kt`
- Test: `app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsFitTest.kt`

**Interfaces:**
- Consumes: `ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier)` (components).
- Produces: no signature change to `PlaceDetailsScreen`.

- [ ] **Step 1: Write the failing layout test** (in `PlaceDetailsScreenTest`, after `tripadvisorsRatingAndPhotoCreditShow`;
  add imports `androidx.compose.ui.test.getUnclippedBoundsInRoot`, `org.junit.Assert.assertTrue`,
  `com.kanyandula.discovernearby.ui.theme.DetailsImageWidth`)

```kotlin
    // DN-UX-007 (artboard 3c): Back and the name head the page; the photo sits under Back, the facts beside it.
    @Test
    fun backHeadsThePageAndThePhotoSitsUnderIt() {
        enrichment = PlaceEnrichment(
            photoUrl = "https://example.test/p.jpg",
            rating = ProviderRating(4.5, 312, "https://example.test/b.png"),
        )
        state = Content(PlaceDetails(place, openingSummary = null))
        val back = rule.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot()
        val name = rule.onNodeWithText("The Daily Grind").getUnclippedBoundsInRoot()
        val credit = rule.onNodeWithText("Photo: Tripadvisor").getUnclippedBoundsInRoot()
        val reviews = rule.onNodeWithText("(312 reviews)").getUnclippedBoundsInRoot()
        assertTrue(name.left >= back.right && name.bottom <= back.bottom)
        assertTrue(credit.top >= back.bottom)
        assertEquals(back.left.value, credit.left.value, 0.5f)
        assertTrue(reviews.left >= back.left + DetailsImageWidth && reviews.top < credit.top)
    }
```

- [ ] **Step 2: Write the failing fit assertions** (in `PlaceDetailsFitTest`)

In `theWholeConceptFitsTheReferencePanel`, after the `description` lines, add:

```kotlin
        // DN-UX-007: on the fallback the note joins the facts beside the photo, so the description keeps one line.
        assertTrue(description.getUnclippedBoundsInRoot().height <= 30.dp)
```

Add a second test for the normal state, which keeps two lines (imports `PlaceDetails`,
`PlaceDetailsUiState.Content`, `com.kanyandula.discovernearby.model.PlaceDetails`):

```kotlin
    @Test
    fun theNormalStateFitsWithATwoLineDescription() {
        rule.setContent {
            DiscoverNearbyTheme {
                Box(modifier = Modifier.height(ReferencePanelHeight).width(ReferencePanelWidth)) {
                    PlaceDetailsScreen(
                        distanceMeters = 1_400,
                        category = DiscoveryCategory.FAMILY,
                        state = Content(PlaceDetails(everything, openingSummary = null), carParkNearby = true),
                        onNavigate = {},
                        onBack = {},
                        saved = false,
                        onToggleSave = {},
                        drivingRestricted = false,
                        enrichment = enrichment,
                    )
                }
            }
        }
        val description = rule.onNodeWithText("Seasonal plates", substring = true)
        description.assertHeightIsAtLeast(56.dp) // two bodyLarge lines
        val navigate = rule.onNodeWithText("Navigate")
        assertTrue(description.getUnclippedBoundsInRoot().bottom <= navigate.getUnclippedBoundsInRoot().top)
        assertTrue(navigate.getUnclippedBoundsInRoot().bottom <= ReferencePanelHeight)
        rule.onNodeWithText("Car park nearby").assertIsDisplayed()
        rule.onNodeWithText(hereNotice()).assertIsDisplayed()
    }
```

Move the existing test's `everything` place and the `PlaceEnrichment(...)` into private class properties
(`everything`, `enrichment`) so both tests share them unchanged.

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*PlaceDetailsScreenTest.backHeadsThePageAndThePhotoSitsUnderIt' --tests '*PlaceDetailsFitTest*'`
Expected: FAIL — the name sits beside the photo, not beside Back; the photo is right of Back.

- [ ] **Step 4: Re-lay out the screen** (`PlaceDetailsScreen.kt`)

Replace the `else` branch's body with:

```kotlin
        val content = state as? PlaceDetailsUiState.Content
        val summaryOnly = state is PlaceDetailsUiState.SummaryOnly
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DetailsGap)) {
            // Back and the name head the page as on the other screens (DN-UX-007), so the photo lines up with the tiles.
            ScreenHeader(title = state.summary.name, onBack = onBack)
            // Squeezed first on a short panel; the header, the actions and the notice always stay whole.
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DetailsGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ContentGap)) {
                    Photo(category, enrichment?.photoUrl)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowLineGap)) {
                        Summary(
                            place = state.summary,
                            distanceMeters = distanceMeters,
                            opening = openingLine(state.summary, content?.closing, content?.details?.openingSummary),
                            enrichment = enrichment,
                            summaryOnly = summaryOnly,
                        )
                        // One line on the fallback, where the note takes the other (PlaceDetailsFitTest).
                        enrichment?.description?.takeUnless { drivingRestricted }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = if (summaryOnly) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                AmenityTiles(state.summary, content?.carParkNearby)
            }
            // …the existing Save/Navigate Column and ProviderAttribution, unchanged…
        }
```

In `Summary`, delete the name `Text` (the header shows it) and update its KDoc to: "Tripadvisor's rating as
Tripadvisor draws it, else the place's own; "Kind · price · distance"; the opening line; and on the fallback the
summary-only note. Each only when known." Remove the now-unused `BackButton` import. Update the screen's KDoc: "Place
Details as the Product Lead's artboard 3c (DN-UX-007), from the concept (DN-UX-005): Back and the name as the header;
the photo under Back with the facts and Tripadvisor's description beside it; the amenity tiles; Navigate bottom
right, with Save ↔ Saved beside it (DN-UX-006). …" (keep the rest of the existing sentences).

- [ ] **Step 5: Run the Details tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*PlaceDetails*'`
Expected: PASS (all `PlaceDetailsScreenTest`, `PlaceDetailsFitTest`, `PlaceDetailsViewModelTest`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreen.kt app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsScreenTest.kt app/src/test/java/com/kanyandula/discovernearby/ui/screens/PlaceDetailsFitTest.kt
git commit -m "Put Back and the name above the Place Details photo"
```

Body: the Product Lead's artboard 3c lines the photo up with the tiles; the description moves beside the photo so
the page fits the 468 dp panel, one line on the summary-only fallback.

### Task 2: Parking badge

**Files:**
- Modify: `app/src/main/res/drawable/ic_parking.xml`
- Modify: `app/src/main/java/com/kanyandula/discovernearby/ui/components/AmenityTiles.kt`

- [ ] **Step 1: Make the drawable a two-colour badge** (`ic_parking.xml`)

```xml
<!-- A white P on an Action-blue badge (ui/theme Action): 5.2:1, where a blue P on the grey tile was 2.55:1
     (car app quality VD-1, DN-UX-007). Drawn untinted. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M7 2h10a5 5 0 0 1 5 5v10a5 5 0 0 1 -5 5h-10a5 5 0 0 1 -5 -5v-10a5 5 0 0 1 5 -5z"
        android:fillColor="#FF2563EB" />
    <path android:pathData="M9.5 17v-10h3.5a3 3 0 0 1 0 6h-3.5"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="2.2"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

- [ ] **Step 2: Draw tile icons untinted** (`AmenityTiles.kt`): in `AmenityTile`, change
  `tint = Action` to `tint = Color.Unspecified` (import `androidx.compose.ui.graphics.Color`; drop the `Action`
  import). The comment above becomes `// Decorative: the label says it. The badge carries its own colours.`

- [ ] **Step 3: Build and run the Details tests**

Run: `./gradlew testDebugUnitTest --tests '*PlaceDetails*' assembleDebug`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `git commit -m "Draw the parking icon as a contrast-safe badge"`; body: today's blue P
  on the grey tile is 2.55:1, under the 4.5:1 Google asks of icons in POI apps (VD-1); white on Action blue is 5.2:1,
  as the Product Lead's concept draws it.

### Task 3: Bookmark instead of heart

**Files:**
- Create: `app/src/main/res/drawable/ic_bookmark.xml`, `app/src/main/res/drawable/ic_bookmark_filled.xml`
- Delete: `app/src/main/res/drawable/ic_heart.xml`, `app/src/main/res/drawable/ic_heart_filled.xml`
- Modify: `ui/screens/PlaceDetailsScreen.kt:136`, `ui/DiscoverNavHost.kt:51`, `ui/screens/SavedScreen.kt:22`

- [ ] **Step 1: Add the drawables**

`ic_bookmark.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M19 21l-7 -4l-7 4v-16a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2z"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

`ic_bookmark_filled.xml`: the same path with `android:fillColor="#FFFFFFFF"` and no stroke attributes.

- [ ] **Step 2: Point the three uses at them** — Save ↔ Saved:
  `icon = if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark`; the rail:
  `SAVED(R.drawable.ic_bookmark_filled, R.string.tab_saved)`; the Saved empty state:
  `Message(R.drawable.ic_bookmark, Highlight, R.string.saved_empty_title, R.string.saved_empty_body)`.

- [ ] **Step 3: Delete the hearts** — `git rm app/src/main/res/drawable/ic_heart.xml app/src/main/res/drawable/ic_heart_filled.xml`

- [ ] **Step 4: Verify nothing still names them** — `grep -rn "ic_heart" app/src` prints nothing; then
  `./gradlew testDebugUnitTest assembleDebug` passes.

- [ ] **Step 5: Commit** — `git commit -m "Use a bookmark for Save, Saved and the Saved tab"`; body: the Product
  Lead's call in the DN-UX-007 review; a bookmark says "keep for later" where a heart says "favourite".

### Task 4: Docs, checks and the emulator

**Files:**
- Modify: `docs/02-discover-nearby-ux-interaction-spec.md` (§7, "Content, where available")
- Modify: `docs/design/README.md`
- Create: `docs/design/03c-place-details-header.png` (the artboard's export)
- Modify: `CLAUDE.md` (the Place Details bullet)

- [ ] **Step 1: docs/02 §7** — first bullet becomes: "Layout as the Product Lead's artboard 3c
  (`docs/design/03c-place-details-header.png`, DN-UX-007), from the concept (`03b-place-details-concept.png`): Back and
  the place name as the header; the image under Back, with the facts and a short description beside it; amenity tiles
  below the image; **Navigate** bottom right, with **Save** beside it (DN-UX-006)". The description bullet becomes:
  "A description (Tripadvisor's), two lines at most (one on the summary-only fallback), hidden while driving".

- [ ] **Step 2: design README** — the "Place Details follows the concept" paragraph: artboard 3c
  (`03c-place-details-header.png`, DN-UX-007) is the layout reference, rearranging the concept: Back and the name head
  the page; the photo sits under Back with the facts and description beside it; tiles below. The parking tile shows a
  white P on an Action-blue badge. The rail's Saved tab, Save (outline) and Saved (filled) use a bookmark. Add a table
  row for `03c-place-details-header.png`; the `03b` row's note becomes "the Product Lead's concept; the layout
  reference until DN-UX-007".

- [ ] **Step 3: the PNG** — the user exports artboard 3c from the canvas (select it › Export › PNG) to
  `docs/design/03c-place-details-header.png`; if that is not available, ask before substituting an emulator
  screenshot.

- [ ] **Step 4: CLAUDE.md** — the Place Details (DN-UX-005) bullet: "laid out as artboard 3c (DN-UX-007,
  `docs/design/03c-place-details-header.png`): Back and the name as the header, the photo under Back with the facts and
  the description beside it, the tiles below; the parking tile is a white P on a blue badge." In the Saved places
  bullet, "Save ↔ Saved" gains "(bookmark)".

- [ ] **Step 5: Checks** — `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`; all pass.

- [ ] **Step 6: Emulator check** (ask the user first: the DN-M2-004 session shares the emulator)
  - `adb -s emulator-5554 emu avd name` → `AAOS_AOSP_33_userdebug`; `ANDROID_SERIAL=emulator-5554 ./gradlew
    :app:installDebug`.
  - Fakes: `adb -s emulator-5554 shell am start --user 10 -n com.kanyandula.discovernearby/.ui.MainActivity --es scenario NORMAL`;
    Coffee → first row: header, photo under Back, facts and description beside it, tiles below, badge; screenshot.
  - `--es scenario DETAILS_FAILURE`: the note and a one-line description; screenshot.
  - Drive (CLAUDE.md commands): the description goes; Park: it returns.
  - Save → Saved shows the filled bookmark; the rail's Saved tab and the empty Saved screen show bookmarks.
  - Rotary on Details: Back first, then Save, Navigate, the rail; read `focusedNode` from the RotaryService dump; no
    `uiautomator` while the app launches.
  - Restore: Park, the user's previous app state.

- [ ] **Step 7: Commit** — `git commit -m "Record the DN-UX-007 Place Details layout in the docs"`.

### Task 5: Review and PR (ticket workflow steps 4–5)

- [ ] Run the `simplify` skill on the branch diff; fix its findings; re-run the checks.
- [ ] Dispatch `correctness-reviewer` and `quality-reviewer` on the branch; fix Critical/Important findings.
- [ ] Push the branch; open the PR into `main` with the `pr-description` skill (ticket ID and acceptance criteria, no AI
  attribution); update the ticket's Notes and NOW.md.
