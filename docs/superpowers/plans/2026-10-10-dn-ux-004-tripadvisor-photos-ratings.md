# DN-UX-004 Tripadvisor Photos and Ratings Implementation Plan

> **For agentic workers:** executed inline, task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill the photo slot (DN-UX-003) and the rating line with Tripadvisor's photo and rating for the places on
screen, and close three small gaps from the DN-UX-002 pass, so the app looks like the concept.

**Architecture:**
- **HERE stays the source** of places, ranking and positions. Tripadvisor *enriches* only the rows on screen, after
  ranking, so ranking and the M2 benchmark are unchanged (ratings are display-only).
- **Seam:** `places/PlaceEnricher` (`suspend fun enrich(place, category): PlaceEnrichment?`). `NoEnrichment` for
  the fakes, CI and Robolectric; `places/tripadvisor/TripadvisorEnricher` for live data. Response models stay in
  `places/tripadvisor/`.
- **Domain:** `model/PlaceEnrichment(photo: PlacePhoto?, rating: ProviderRating?)`, serializable, so Place Details
  gets it through the route with no second lookup.
- **ViewModel:** `RecommendationsViewModel` enriches the visible rows one by one after a result arrives and puts the
  results in `Content.enrichments`; a new request or Back cancels it (stale protection, docs/03 §15).
- **Tripadvisor (Terra):** `GET /api/locations/nearby` around HERE's position (200 m, RESTAURANT for Coffee and Food,
  ATTRACTION otherwise, `sort=distance,asc`); name match; then `GET /api/locations/{id}/photos?size=1` for the
  image URL (`original_size_url`, resized through the CDN). Key in `X-API-Key`.
- **Limits:** the nearby endpoint allows 1 request a second with a burst of 5; at most 5 rows a list, so one burst.
  HTTP 429 → that row keeps its artwork, no retry. In-memory `HERE id → Tripadvisor location_id` cache (the only
  thing Tripadvisor allows to be kept); photo URLs and ratings are never stored beyond the screen.
- **UI:** Coil 3 (`coil-compose`, `coil-network-okhttp`) with a memory cache only. `PlaceImage` takes the photo URL
  and falls back to the artwork. The rating line becomes Tripadvisor's bubbles (`icon_url` from the response) with
  the Tripadvisor logo and the count. Details also credits the photo.

**Tech Stack:** Kotlin, Compose, OkHttp, kotlinx.serialization, Coil 3, JUnit, Robolectric, the AAOS emulator.

**Spec:** the ticket `tickets/DN-UX-004-tripadvisor-photos-ratings.md`; DN-SP-004 (evidence); DN-UX-002 (gap list);
docs/02 §6–§7; docs/03 §2–§3, §10, §15; Tripadvisor Terra docs (nearby, photos, display requirements, caching, rate
limits).

## Global Constraints

- No key, photo URL, place name or coordinate in logs, tests or the repo. Tests use made-up names.
- Architecture rules (CLAUDE.md): only `ui/` imports Compose; provider models never leave their package; every
  request has a timeout; user text in strings.xml.
- Live Tripadvisor calls cost money past 1,000 a month: development and tests use the fakes; live checks are few.

## Tasks

### Task 1: The folded-in gaps (no network)

- [ ] Kind labels: `ui/PlaceText.kt` maps every kind `CategoryConfigs` targets to a string (`kind_*`); unknown kinds
      show nothing. Test: every targeted kind has a label.
- [ ] Rows: the kind leads the details line ("Café · 4.6 ★ (212) · Parking"); Details: the kind under the distance.
- [ ] Details: the opening line shows the status from `isOpenNow` ("Open now" in green / "Closed now"); HERE's
      schedule shows only when the status is unknown, muted, one line.
- [ ] The list's "© HERE" notice bottom-left.
- [ ] Tests updated; checks; commit.

### Task 2: The enrichment seam (no network)

- [ ] `model/PlaceEnrichment`, `PlacePhoto(url, credit)`, `ProviderRating(value, count, iconUrl)`.
- [ ] `places/PlaceEnricher` + `NoEnrichment`; `DiscoverUseCase`/`AppContainer` wiring (fakes → none).
- [ ] `RecommendationsViewModel`: enrich the shown rows in order; `Content.enrichments: Map<String, PlaceEnrichment>`;
      re-trim keeps enrichments; cancellation on retry/Back.
- [ ] `PlaceDetailsRoute` carries the row's enrichment.
- [ ] Tests with a fake enricher; checks; commit.

### Task 3: The Tripadvisor client and enricher

- [ ] `tripadvisor.apiKey` → `BuildConfig.TRIPADVISOR_API_KEY`; `AppContainer` uses `TripadvisorEnricher` only with
      live HERE data and a key.
- [ ] `TripadvisorClient`: nearby and photos calls (OkHttp, the HERE client's call pattern), timeouts, 429 → null.
- [ ] Matching: normalised names (case, accents, punctuation, generic words such as "cafe"), containment or a
      similarity of at least 0.75, within the 200 m search. Unit-tested on made-up names.
- [ ] The `location_id` cache; photo URL resized for the slot.
- [ ] Interceptor tests for the requests (header, parameters) and parsing; checks; commit.

### Task 4: The UI

- [ ] Coil 3 in the version catalog; an `ImageLoader` with a memory cache only.
- [ ] `PlaceImage(photoUrl)`: the photo cropped to the slot, the artwork while loading and on failure.
- [ ] `TripadvisorRating`: the official logo, the bubble image from `icon_url`, the count; rows and Details.
- [ ] Details: "Photo: {user} · Tripadvisor" credit under the panel.
- [ ] The official logo asset (Tripadvisor's media room); if it can't be fetched, ask the user.
- [ ] Tests (rating line, fallback); checks; commit.

### Task 5: Docs

- [ ] ADR-003 (Tripadvisor for photos and ratings): evidence, decision, terms → Legal, call budget, limits.
- [ ] docs/02 §6–§7, docs/03 (enrichment), docs/05 §9 (a register row for Tripadvisor's terms), the design README,
      CLAUDE.md.

### Task 6: Verify and ship

- [ ] Live check on the emulator at the three test locations (about 10 calls a list); screenshots in the scratchpad.
- [ ] detekt, lint, tests, build; `simplify`; PR with the ticket's criteria.
