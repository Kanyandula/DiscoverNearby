# Discover Nearby — AAOS Proof of Concept Product Brief

**Status:** Proposed — Revision 4  
**Platform:** Android Automotive OS (AAOS)  
**Phase:** Proof of Concept  
**Primary Test Environment:** Android Automotive OS Emulator  
**Owner:** Product  
**Last Updated:** 2026-10-02

> **Revision 2:** The POI/routing provider is now unselected and chosen through a licensing spike. Recommendation quality is validated with a small relevance benchmark. The result count is "up to 3–5" and never padded. Attributes carry provenance. A location-permission state is added. Generic voice discovery and maps are deferred. See `README.md` for the full change summary.
>
> **Revision 3:** This revision adds delivery planning. The provider spike is now bounded (2–3 providers, a 3 × 3 test matrix, a time-box), and ADR-001 may be provisionally accepted pending licensing confirmation. Roles and go/iterate/stop rules are defined (§16), and only M1 waits for the provider decision. Details are in `05-discover-nearby-delivery-plan.md`.
>
> **Revision 4:** The UI is built in Kotlin + Jetpack Compose as a distraction-optimized AAOS activity, replacing Car App Library templates. The app now owns layout, focus and driving-restriction handling, which the template host used to provide. Product scope, the provider spike and the relevance benchmark are unchanged.

---

## 1. Overview

Discover Nearby is an AAOS application concept that helps drivers discover interesting and useful places around them or along their journey without requiring them to know exactly what to search for.

Instead of searching for a specific business or destination, users express an intent such as:

- Coffee
- Food
- Outdoors
- Family
- Scenic
- Explore

The app then surfaces a small number of strong recommendations and allows the user to hand the selected destination to an installed navigation application.

The initial implementation is a **proof of concept** intended to validate the product experience and technical feasibility using the Android Automotive OS emulator.

---

## 2. Problem

Existing navigation applications are primarily optimized around:

> “I know where I want to go.”

Discover Nearby explores a different problem:

> “I want to go somewhere, but I do not know where.”

Typical situations include:

- “Find somewhere nice to stop.”
- “Find somewhere the kids can play.”
- “Find a scenic place nearby.”
- “Find coffee somewhere ahead on my journey.”
- “Find something interesting within 20 minutes.”

Drivers currently have to translate these needs into several searches, compare results manually, and decide whether each result is practical in the context of the current journey.

---

## 3. Product Hypothesis

We believe drivers would find value in an experience that converts broad intent into a small number of contextual recommendations.

The POC should prove that AAOS can support the experience:

```text
Intent
  ↓
Relevant recommendations
  ↓
Place selection
  ↓
Navigation handoff
```

Discover Nearby is not intended to replace the vehicle's navigation application.

The POC must show that the recommendations are **useful**, not only that an API call returns results. This is what the relevance benchmark (§11) is for.

---

## 4. POC Objectives

### 4.1 Location-aware discovery

A simulated vehicle location produces relevant nearby recommendations.

### 4.2 Intent-based discovery

Selecting categories such as Family, Outdoors, Coffee, or Scenic produces different, intent-appropriate recommendation sets.

### 4.3 Automotive-friendly UX

The UI is built in Jetpack Compose as a distraction-optimized AAOS activity, and remains usable under automotive interaction constraints (touch, rotary, Park/Drive).

### 4.4 Navigation handoff

A selected recommendation can be passed to an installed navigation application through a standard Android `geo:` intent. The system decides which navigation application handles it.

### 4.5 Route-aware discovery (stretch)

Discover Nearby can recommend useful places ahead on a journey rather than only places geographically near the current vehicle location.

---

## 5. Primary User Scenario

A driver is travelling with their family and decides they would like somewhere to stop.

Instead of opening a navigation application and performing multiple searches, they open Discover Nearby and select:

**Family**

The application returns a small recommendation set such as:

### Riverside Park

- 4.2 km away
- Playground
- Parking

### Forest Park

- 7.8 km away
- Trails
- Playground

### Visitor Centre

- 9.1 km away
- Café

The driver selects a place and chooses **Navigate**.

The destination is then handed to the installed navigation application.

> Distances and attributes are illustrative. Whether the first release shows distance or travel time depends on the selected provider (§9). Attributes are shown only when they are provided by the data source or reliably derived from it (§10).

---

## 6. Core User Journey

```text
Launch Discover Nearby
        ↓
Choose an intent
        ↓
Coffee / Food / Outdoors / Family / Scenic / Explore
        ↓
See up to 3–5 recommendations
        ↓
Open recommendation
        ↓
View essential details
        ↓
Navigate
```

---

## 7. POC Scope

### Core

- Native AAOS application: one Kotlin + Jetpack Compose activity, declared distraction-optimized
- Emulator-only validation
- Compose UI: category grid, recommendation list, place details, message states (no map)
- Six discovery intents
- A POI provider licensed for in-vehicle use, chosen through a provider spike
- Real provider data
- Deterministic recommendations
- Relevance benchmark
- Location permission flow
- Place details
- Navigation handoff
- Stub navigation app for handoff verification
- Touch
- Rotary
- Back
- Park/Drive
- Loading, empty and error states (including timeout)

### Stretch

- Along-route discovery
- Fixed destination presets
- Journey-aware ranking
- Detour calculation
- “Stops Ahead” recommendations

### Deferred

- Maps
- Mobile companion app
- Generic voice discovery
- Free-text destination entry
- AI recommendations
- User accounts
- Social functionality
- Production analytics
- Production backend unless required by the selected provider or licence
- Production offline architecture
- Production-scale caching
- Full turn-by-turn navigation
- Physical head-unit testing
- Real vehicle testing
- OEM-specific integration
- Full accessibility certification
- Production launch readiness

**Why maps are deferred:** a map does not materially help prove the hypothesis (intent → recommendation → decision → navigation). It adds implementation and licensing complexity, and the grid, list and details screens are enough.

**Why generic voice is deferred:** voice integration is not part of this POC. General conversational discovery ("find somewhere scenic for the kids") is a separate product problem and is outside this POC.

---

## 8. Product Principles

### Reduce choice

The application should not behave like a conventional search engine.

The default experience shows **up to 3–5 strong recommendations**. If only one or two credible places exist, show those. **Never pad the list with weak results.**

### Show journey relevance

Where route information exists, prefer:

- time ahead
- detour time
- practical stop value

over raw physical distance.

Example:

> 18 min ahead · +4 min detour

is more useful than:

> 7.2 km away

Journey-relevance values are shown only when the implementation actually calculates them (stretch scope).

### Make intent primary

The user should be able to express broad intent such as Family, Scenic or Outdoors without constructing a detailed search query.

### Do not present guesses as facts

An attribute such as “Toilets” or “Playground” is shown only if the data source provides it, or if it is reliably derived (for example, the place itself is a playground). Unknown is not shown.

### Keep navigation external

Discover Nearby helps the user decide **where to go**.

The installed navigation application handles **how to get there**.

---

## 9. POI and Routing Provider

The POC does **not** assume a specific provider.

Some widely used place APIs restrict use in applications embedded in vehicles. A provider must therefore be chosen on evidence before live POI integration starts.

A short **provider-selection spike** runs in parallel with the AAOS skeleton (Milestone 0). It evaluates candidates against:

- licence suitability for embedded / in-vehicle use
- AAOS / in-car usage rights
- POI coverage at the three test locations
- supported place types
- Family-category and Scenic-category data
- ratings and review-count availability
- opening-state availability
- amenity availability
- travel time or distance from origin
- along-route search capability
- routing capability
- cost / quota model
- attribution requirements
- caching / storage restrictions
- API-key requirements

The spike is deliberately small:

- **Objective:** select a POI provider that is legally usable in an embedded vehicle application and whose data is good enough to prove Coffee, Family and Scenic.
- **Three candidates, in order:** TomTom (strongest technical fit, including along-route search), HERE, and one *named* OSM-backed service or self-host approach. None is approved. OSM *data* is openly licensed with attribution; the open question for OSM is the service layer that serves it.
- **The same test matrix for every provider:** Coffee, Family and Scenic × Greystones, Dublin and Galway.
- **Time-boxed: 3 working days, no extensions.** The spike ends in one of three outcomes: **Selected** (go to M1), **Provisionally selected** (M1 only with explicit risk acceptance), or **No viable provider** (stop and rethink the data strategy).
- **REST only.** The core POC calls provider REST APIs directly; no provider SDK.

**Output:** ADR-001 (`adr/0001-poi-provider.md`), naming the provider, recording the evidence, and stating what the provider can and cannot supply.

**Sign-off:** Product approves data usefulness. A Product/Legal/Business owner confirms licence suitability; engineering does not interpret ambiguous licence language alone. If licensing cannot be confirmed within the time-box, ADR-001 may be recorded as **"Provisionally selected, pending licensing confirmation"**, and M1 may proceed on that basis.

The spike runs in parallel with M0. M0 builds against a fake POI repository, so **only M1 live-provider integration waits for ADR-001**.

### What “12 min away” means

The first Nearby milestone shows **straight-line distance** (“4.2 km away”) unless the selected provider supplies travel time reliably as part of the same request. Driving-time estimates are not promised until the implementation calculates them.

---

## 10. Discovery Categories

Each category lists the place kinds it targets and the signals that may help ranking **if the provider supplies them**. Exact provider mappings come from the provider ADR.

### Coffee

Café, coffee shop. Optional signals: open now, rating, parking.

### Food

Restaurant, fast casual, takeaway. Optional signals: open now, rating, parking.

### Outdoors — “I want to do something outdoors”

Park, trail, forest, beach, hiking area, outdoor attraction.

### Family

Playground, zoo, aquarium, family attraction, park. Optional signals: toilets, café, parking.

### Scenic — “I want to go somewhere because it is nice to look at”

Viewpoint, scenic spot, coastal overlook, landmark, waterfall, natural attraction.

### Explore

Tourist attraction, museum, landmark, heritage site, unusual local attraction.

### Attribute provenance

Every attribute shown on a recommendation has a source:

- **Provided** — the data source states it (for example, a parking field).
- **Derived** — inferred reliably from the place type (for example, a place whose type is “playground”).
- **Unavailable** — not known; not shown.

---

## 11. Recommendation Philosophy

The POC does not require machine learning or AI. A deterministic, transparent, tunable model is sufficient.

```text
Recommendation score =
    category relevance
  + location relevance
  + optional quality signals      (only if the provider has them)
  + optional amenity signals      (only if the provider has them)
  + optional open-state signal    (only if the provider has it)
  - distance / detour
```

Rules:

- **Known-closed places are excluded**, not penalised.
- **Unknown data is neutral** — no bonus, no penalty.
- **Light diversity** — avoid returning several near-identical places (for example, five city parks for Outdoors).
- The model must work when ratings, review counts, opening state or amenities are unavailable.

### Relevance benchmark

A small deterministic check of whether recommendations are useful: three reference locations × six categories.

```text
                Coffee  Food  Outdoors  Family  Scenic  Explore
Location A
Location B
Location C
```

For each cell, a reviewer inspects the top recommendations against these rules:

- **Top result** — reasonably relevant to the selected intent.
- **Top three** — where at least three credible places exist, at least 2 of the top 3 are judged useful.
- **No wrong category first** — an obviously wrong-category place never ranks #1.
- **Sparse is acceptable** — 1–2 strong recommendations are better than a padded list.

This benchmark evaluates the concept. It is not a statistically rigorous ranking experiment. Cells where the provider has sparse data are recorded as learning, not as failure.

---

## 12. POC Acceptance Criteria

### Location

At fixed Location A and Location B, the same category produces contextually different recommendations where the provider has different nearby places.

### Intent

At the same location, Coffee, Family and Outdoors do not consistently return the same recommendation set.

### Recommendation relevance

- The top result reasonably matches the selected intent.
- Where at least three credible places exist, at least two of the top three are judged useful.
- An obviously wrong-category place is not ranked first.
- Sparse result sets may contain fewer than three recommendations.
- All six categories exercise the live provider.

### Details

Opening a recommendation shows, where available:

- Place name
- Distance (or travel time, if calculated)
- Rating, if the provider supplies it
- Provided or reliably derived attributes only
- Navigate action

### Navigation

- The stub navigation app receives the coordinates of the selected recommendation.
- If no navigation handler is available, or the handoff fails, a clear failure state is shown.

### Automotive Interaction

- The primary journey can be completed using touch.
- The primary journey can be completed using rotary input without touch.
- Back returns to the previous logical screen.
- Focus behaviour (built by the app in Compose) is verified: visible focus, no lost focus, no traps, no unreachable primary actions.
- The full supported flow remains stable under emulator Park and Drive states.

### Resilience

The application handles the following gracefully, with no crash and no endless loading:

- permission required
- location unavailable
- network failure
- provider failure
- timeout
- empty results
- navigation unavailable

---

## 13. Success Criteria for the POC

The POC should answer these questions:

1. Can a driver-safe AAOS application support intent-based place discovery?
2. Can real, appropriately licensed POI data be turned into a useful small recommendation set? (Answered by the relevance benchmark.)
3. Can vehicle location meaningfully change recommendations?
4. Can the user move from discovery to navigation with minimal interaction?
5. Is route-aware discovery technically feasible enough to justify a follow-on phase?
6. Where is the data too sparse to support a category (likely Scenic and Family amenities), and does that change the product?

---

## 14. Privacy and Data Handling (POC level)

- Vehicle location is sent to the POI provider **only when the user requests discovery**.
- Provider API keys are not committed to source control and are never logged in full.
- Precise raw coordinates are not logged unnecessarily.
- Only data the selected provider's terms allow is stored.
- No backend proxy is added unless the selected provider or licence requires one.

---

## 15. POC Limitations

This implementation is validated using the Android Automotive OS emulator only.

The POC does **not** validate:

- Real GNSS behaviour
- Real vehicle movement
- Physical head-unit performance
- OEM-specific AAOS behaviour
- OEM navigation applications
- Real rotary-controller ergonomics
- Physical touch accuracy while moving
- Real-world connectivity transitions
- Vehicle antenna/GPS characteristics
- Sunlight/readability conditions
- Production API scale or economics
- Real driver-distraction behaviour
- Vehicle-specific VHAL behaviour
- Production launch readiness
- A Play Store distribution path for a Compose POI app (delivery plan §9, V8)

The correct product statement is:

> The POC validates the concept and AAOS application flow in the Android Automotive OS emulator. Real-vehicle validation remains future work.

---

## 16. Governance

| Decision | Owner |
| --- | --- |
| Product scope | Product Lead |
| Data usefulness (provider spike) | Product Lead |
| Licence suitability for embedded automotive use | Product / Legal / Business owner |
| Relevance benchmark sign-off (M2 exit) | Product Lead |
| Go / Iterate / Stop (end of M4) | Product Lead |

The same person may hold the Product roles. Licence confirmation is kept separate from engineering wherever possible.

### Go / Iterate / Stop

- **GO** (continue to M5 / real-hardware exploration): the AAOS flow works, the provider is legally viable, recommendations are reasonably useful, the interaction feels appropriate in the emulator, and no architectural blocker was found.
- **ITERATE** (tune categories, provider or ranking): the AAOS flow works, but recommendation quality is weak.
- **STOP:** there is no commercially usable data source, AAOS constraints prevent the intended interaction, or recommendations don't provide meaningful value.

GO also names the distribution path (V8): Play, OEM/preinstall, or a template UI layer for Play. An unresolved V8 does not block GO, but it must be settled before any production planning.

See `05-discover-nearby-delivery-plan.md` for owners, timeline and the verification register.

---

## 17. Follow-on Opportunities

If the POC is successful, future phases may explore:

- Map view
- Mobile companion app
- Saved places
- Trip planning
- Phone-to-car and car-to-phone handoff
- Personalised recommendations
- Voice-first discovery
- Weather-aware recommendations
- EV charging + nearby activities
- Parking integration
- Family-focused stopping recommendations
- Production offline strategy
- Real vehicle testing
- OEM integration
