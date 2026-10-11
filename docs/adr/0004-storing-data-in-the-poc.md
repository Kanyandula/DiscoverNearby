# ADR-004: Storing Data in the POC

**Status:** Accepted for the emulator POC (Product Lead, 2026-10-11); providers' storage terms go to Legal before
production  
**Date:** 2026-10-11  
**Deciders:** Product Lead (rule change) · Android/Tech Lead · Legal (terms, before production)

---

## Context

Until now the POC stored nothing on the device. docs/05 §2a and docs/03 §14 kept provider responses in memory and
discarded them when the session ended, and [ADR-001](0001-poi-provider.md#v6b--caching-and-storage) and
[ADR-003](0003-place-photos-and-ratings.md) made "no persistence" the default while the providers' storage terms were
open.

Saving places (DN-UX-006, `docs/superpowers/specs/2026-10-11-place-details-and-saved-design.md`) needs data that
outlives the app. The Product Lead changed the rule on 2026-10-11: providers' storage terms are for "production when
publishing the app on Google Play and not now".

## Decision

**The POC may store data on the device, provider data included, where a feature needs it.**

- **First use:** saved places (DN-UX-006), in a Room database on the device. A saved place keeps its HERE summary and
  its Tripadvisor photo link, rating, price and description.
- **How (DN-UX-006):** package `saved/`, Room 2.8 through KSP. One table, `saved_places`: the place ID (key), the
  category, when it was saved, the HERE summary and the Tripadvisor enrichment as JSON. No exported schema: a schema
  change wipes saved places, which the POC accepts. A row that no longer decodes is skipped. Live data and the
  `--es scenario` fakes keep separate files (`saved-places.db`, `saved-places-fakes.db`), so made-up places never
  show in a live list; Robolectric keeps them in memory.
- **Search results stay in memory.** No feature needs them stored, so no cache is built, and the app keeps no local
  mirror of POI data.
- **Images** keep Coil's memory-only cache (ADR-003).
- **The repository stays clean:** no provider responses, place names or keys in the public repo; tests use made-up
  data.

## Terms → Legal before production

Engineering quotes and does not interpret (docs/05 §4). Tracked in the docs/05 register, V6b.

- HERE Platform Terms §6.4(j): Results are not to be cached outside the Platform "for more than 30 days" … "unless
  Results are used solely for your internal testing, evaluation, or record retention for audit and legal compliance
  purposes" (ADR-001 [H1]).
- HERE Exhibit 2 §1.3: no pre-fetching, caching or storing "except as explicitly allowed by the caching headers
  (HTTP/1.1 standard) returned by HERE Location Services" (ADR-001 [H1]).
- Tripadvisor caching policy: only the location ID may be cached (ADR-003).

## Consequences

- docs/05 §2a and V6b, docs/03 (stack, §14), ADR-001 V6b and ADR-003 point here.
- Before Google Play, each stored field is checked against these terms; a saved place may then have to keep IDs only
  and fetch the rest again.