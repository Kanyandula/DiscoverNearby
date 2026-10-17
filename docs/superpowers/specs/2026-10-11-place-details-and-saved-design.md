# Place Details as in the concept, the app rail and Saved places — design

**Date:** 2026-10-11 · **Tickets:** DN-UX-005 (PR A), DN-UX-006 (PR B) · **Decided by:** the Product Lead, in a design
session on 2026-10-11 · **Evidence:** DN-SP-005 (Place Details extras probe, private vault; counts below)

## Goal

Make Place Details look like the Product Lead's concept (`docs/design/03b-place-details-concept.png`) and let the driver
save places and come back to them. The concept shows a photo, rating, kind and price, open status with the closing
time, amenity tiles, a brief description, and **Save** beside **Navigate**, with a rail on the left.

## Decisions (Product Lead, 2026-10-11)

- Build the concept's elements that a source can fill (DN-SP-005); leave out the ones none can (below).
- **Price** as symbols, the way Tripadvisor's own site shows its levels: € (Cheap Eats), €€–€€€ (Mid Range), €€€€
  (Fine Dining).
- **Rule change: the POC stores data.** The POC may store data on the device, provider data included, where a feature
  needs it ([ADR-004](../../adr/0004-storing-data-in-the-poc.md)). Providers' storage terms (HERE's 30-day caching
  limit, Tripadvisor's location-ID-only caching policy) are a production item, reviewed before Google Play, not now.
- **Save** keeps places **on the device, in Room**.
- **The app draws its own rail** with two tabs, Discover and Saved. The car keeps its own system rail or bar (Music,
  Phone, Settings), as `docs/design/00-overview.png` shows.
- **Saved** lists places nearest first.
- **Opening line** wording as in PR A below, "Open now" included for an open place without a closing time.
- The rail adds rotary focus targets. Approving this design is the Product decision ADR-002 requires for a rotary
  change.

## What the sources can fill (DN-SP-005, 173 benchmark places)

| Concept element | Source | Coverage | Extra calls |
| --- | --- | --- | --- |
| Open · Closes 18:00 | HERE structured opening hours, in the details response | 83/173, the places that show open/closed | 0 |
| Café · €€ | Tripadvisor `price_level` (restaurants only), in the nearby search the app makes | 9/10 matched cafés and restaurants probed | 0 |
| Description | Tripadvisor `descriptions`, same search | 12/30 matched places probed (about 1 row in 5); 132–867 characters | 0 |
| Car park nearby | A HERE car park (category `800-8500`) within 150 m | 117/173 (68 %) | 1 HERE call when Details opens |
| Wi-Fi, outdoor seating, family friendly | Tripadvisor `attributes`: none for 30/30 places; OpenStreetMap: Wi-Fi 1/173, outdoor seating 4/173, children 0/173 | Not viable | — |

**Out of scope:** Wi-Fi, outdoor seating and family-friendly tiles (no source; docs show only provided or derived
facts); the concept's ★ rating (Tripadvisor's display rules require its own graphic, ADR-003); the concept's Music,
Phone and Settings rail items (the car's); removing a place from the Saved list itself; any sync or account.

## PR A — Place Details as in the concept (DN-UX-005)

**Layout** (the concept, on the 4:3 reference screen, with the rail taking width):
- Top: the photo panel (Tripadvisor's photo, else the category artwork; "Photo: Tripadvisor" under a photo) on the
  left; on its right the name, the Tripadvisor rating chip, "Kind · price" and the opening line.
- Then the amenity tile row, only when it has a tile (today, at most "Car park nearby").
- Then the description, at most two lines with "…".
- Bottom: **Save** (left) and **Navigate** (right, primary). In PR A, Save is not shown; PR B turns it on.
- "© HERE" stays bottom-left for HERE data.

**Content rules:**
- **Opening line:** "Open · Closes 18:00" when HERE says the place is open now and today's structured hours give a
  closing time; "Open 24 hours" when today's period covers the whole day; "Open now" when open without a closing time;
  "Closed now" when closed; HERE's weekly text only when the status is unknown (as today). The time follows the car's
  12/24-hour setting. A period that runs past midnight closes on the next day's time.
- **Price:** shown only when Tripadvisor gives a level; mapped in the `tripadvisor` package to a domain `PriceLevel`
  (`CHEAP`, `MID`, `FINE`), drawn by the UI as €, €€–€€€, €€€€.
- **Description:** Tripadvisor's English description. Hidden while UX restrictions apply
  (`DrivingState.distractionOptimizationRequired`), since cars limit text length when moving.
- **Car park nearby:** one HERE `/browse` for category `800-8500` within 150 m of the place, limit 1, run alongside the
  details lookup. A failure, timeout or no result means no tile. It is a fact about the area, not the place's own
  parking, so it is a display-only field and never feeds ranking.

**Code:**
- `PlaceEnrichment` gains `priceLevel: PriceLevel?` and `description: String?`; `TripadvisorEnricher` reads both from
  the nearby response it already gets (no new Tripadvisor call).
- `PlaceDetails` gains `closesAt` (today's closing time, when known) and `carParkNearby: Boolean?`; the HERE mapper
  parses `openingHours[].structured` (`start`, `duration`, `recurrence` with `BYDAY`).
- `PlacesRepository` gains the car-park check; the fakes answer it from their own data.
- `PlaceDetailsViewModel` collects the driving state (for the description).
- **Details fallback:** if the details call fails, Details shows the row's summary and enrichment (price, description)
  and Navigate, as today.
- **Fakes:** a fake enricher gives the `--es scenario` places made-up prices and descriptions (no photo), so the layout
  is built and checked without paid calls. CI and Robolectric keep no enrichment unless a test supplies one.

## PR B — the rail, Save and Saved (DN-UX-006)

**Rail**
- A column on the left of the app on every screen, under the header row: **Discover** (grid icon) and **Saved** (heart).
- The selected tab follows the back stack: Saved is selected on Saved and on a Place Details opened from it.
- Saved navigates to `SavedRoute` above Discover (single top); Discover pops to `DiscoverRoute`. Neither builds a stack.
- Back: Saved → Discover; Back on Discover leaves the app. Deepest path: Saved → Place Details (two destinations).
- Rotary: both tabs carry `focusRing`; the knob reaches them after the screen's content; initial focus stays on the
  screen's first item. `ReturnFocus` is unchanged. Recorded in the V7 register row.

**Storage (`saved/`, no Compose or Car API)**
- Room: `SavedPlaceEntity` (`placeId` primary key, `category`, `savedAt`, `summaryJson`, `enrichmentJson` nullable),
  `SavedPlaceDao` (observe all, observe whether one is saved, upsert, delete), `SavedPlacesDatabase`, and a
  `SavedPlacesRepository` interface with a Room implementation. Domain model `SavedPlace(summary, category, enrichment,
  savedAt)`.
- JSON through kotlinx.serialization (`PlaceSummary` is already serializable), so no per-field converters.
- No exported schema; a schema change wipes saved places (destructive migration). Acceptable for the POC.
- Separate database files for live data and the `--es scenario` fakes, so made-up places never appear in a live list.
- Built in `AppContainer`; `TestDiscoverApplication` uses an in-memory database.

**Save on Place Details**
- **Save** (outline heart) ↔ **Saved** (filled heart); a tap toggles; the state follows the database.
- Saves the place as Details knows it (the refreshed HERE summary once loaded), the category and the enrichment.
- Works while driving (one tap). Focus order Save → Navigate; initial focus on the screen's first item, Back (Product
  Lead, 2026-10-11).

**Saved screen**
- Rows reuse `RecommendationRow`: photo or artwork, name, distance from the car now, kind, Tripadvisor chip.
- Nearest first from the current location; the usual loading state while locating; without a location (permission
  refused, no fix), most recently saved first and no distances.
- Parked: every saved place, scrolling. Driving: the existing list limit trims it.
- Empty: "No saved places yet" / "Tap Save on a place to keep it here."
- A row opens Place Details (`PlaceDetailsRoute` with the stored category and enrichment), which refreshes from HERE.
  Removing the place there drops it from the list.
- "© HERE" under the list when rows are HERE data.

## Docs

- **The rule change (on this branch, with the spec):** ADR-004 (storing data in the POC); docs/05 stack, §2a and
  register row V6b (parked for production, Legal before Google Play); docs/03 stack and §14; ADR-001 V6b; ADR-003;
  the docs index; CLAUDE.md.
- **PR B:** ADR-004 gains the Room details (what is stored, the separate files); docs/03 gets the `saved/` package in
  the structure and architecture rules.
- **docs/02:** the rail, Save and Saved; "Long descriptions: not required" becomes a two-line description, hidden while
  driving.
- **docs/design/README.md:** the concept image as the Place Details reference; the car's rail vs the app's rail; the
  tiles that stay out.
- **CLAUDE.md** and the V7 register row (the rail as a Product-approved rotary change).

## Tests

- Unit: closing time from structured hours (overnight, all day, no hours, closed); price mapping; car-park check
  (result, none, failure); enricher reads price and description.
- Room DAO on an in-memory database: save, remove, live updates, saving twice.
- ViewModels: Save toggling; Saved nearest first, without location, driving limit; description hidden while driving.
- Compose: rail selection, Back, rotary order (`RotaryContractTest`); Saved empty state; Save button; Place Details
  still fits the 4:3 screen (`PlaceDetailsFitTest`).
- `ArchitectureRulesTest` covers `saved/`.
- Emulator: the M0 smoke (docs/04 §10) again, on the fakes, plus a few live checks (Tripadvisor calls are charged past
  1,000 a month).

## Risks

- **Room needs KSP; the project uses AGP 9 with built-in Kotlin.** PR B's first step is a throwaway build check. If KSP
  does not work with it, stop and ask; the fallback is DataStore with the same snapshot.
- **Rotary next to V7:** the rail adds focus targets. The smoke records how rotation behaves; ADR-002's waiver covers
  where focus lands after Back.

## Order

DN-UX-005 (PR A), then DN-UX-006 (PR B), then DN-M2-004 (its daytime re-run is independent).
