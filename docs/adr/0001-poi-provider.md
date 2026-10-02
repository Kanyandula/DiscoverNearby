# ADR-001: POI Provider

**Status:** Proposed — spike not yet run  
*(Outcome at end of the 3-day spike, exactly one of: **Selected** · **Provisionally selected, pending licensing confirmation** · **No viable provider**)*  
**Date:** [DATE]  
**Deciders:** Android/Tech Lead (technical) · Product Lead (data quality) · [Product/Legal/Business owner] (licensing)

---

## Context

Discover Nearby is an embedded Android Automotive OS application. This ADR answers the project's single provider gate (V4):

> **Which provider/service combination is commercially and legally usable for an embedded AAOS application, and is its data good enough to prove Coffee, Family and Scenic?**

Raw OSM data and an OSM-backed hosted service are different things; the question concerns the combination we would actually use.

**Integration constraint:** the core POC (M1–M4) uses the provider's **REST API only**, via OkHttp + kotlinx.serialization and our own mapper. No provider SDK. If M5 needs an SDK, that is a separate ADR.

Candidates, in order:

1. TomTom
2. HERE
3. [Named OSM-backed service or self-host approach]

## Decision

**[PROVIDER]**, outcome: **[Selected / Provisionally selected / No viable provider]**

If provisionally selected: risk accepted by [Product Lead name] on [date]; licensing clarification pending from [owner].

## One-sheet comparison

| Question | TomTom | HERE | OSM option |
| --- | --- | --- | --- |
| AAOS / embedded vehicle use permitted? | | | |
| Evidence / contractual source | | | |
| REST POI search | | | |
| Nearby / category search | | | |
| Along-route capability | | | |
| Coffee coverage | | | |
| Family coverage | | | |
| Scenic coverage | | | |
| Rating / count | | | |
| Opening hours | | | |
| Useful amenities | | | |
| Attribution | | | |
| Caching restrictions | | | |
| Authentication model | | | |
| POC cost | | | |
| Production path plausible | | | |

## Test matrix (selected provider, and each candidate tested)

| Category | Greystones | Dublin | Galway | Notes |
| --- | --- | --- | --- | --- |
| Coffee | | | | |
| Family | | | | |
| Scenic | | | | |

Per query, record: place ID, name, coordinates, place types, distance, rating, rating count, opening state, parking, toilets, family-related information, attribution requirement, latency.

## Field availability (selected provider)

| Field | Provided / Derivable / Unavailable | Coverage notes |
| --- | --- | --- |
| Rating | | |
| Rating count | | |
| Open now | | |
| Parking | | |
| Toilets | | |
| Family information | | |
| Travel time from origin | | Decides distance vs time in M1 |
| Along-route search | | Relevant to M5 only |

## Category mapping

| Category | Provider types / categories |
| --- | --- |
| Coffee | |
| Food | |
| Outdoors | |
| Family | |
| Scenic | |
| Explore | |

## V6a — Attribution

| Field | Value |
| --- | --- |
| Required text | |
| Required logo | |
| Must be always visible? | |
| Can appear in About / Data Sources? | |
| Required on Recommendations? | |
| Required on Place Details? | |

## V6b — Caching and storage

| Field | Value |
| --- | --- |
| May POI responses be persisted? For how long? | |
| May place IDs be stored? | |
| May responses be recorded as test fixtures? | |
| Caching-header rules | |

Default until this is filled in: **no persistence** (in-memory, discarded at session end).

## V6c — Authentication

| Field | Value |
| --- | --- |
| Key / auth model | |
| Can keys be restricted (package, signing, referrer)? | |
| Key rotation / revocation | |
| Requires backend proxy, device auth, signed tokens, or SDK credentials? (→ additional scope) | |

POC baseline: dev-only key in `local.properties` → `BuildConfig`, low quota, easy to revoke, never committed.

## Known limitations

- [e.g. Scenic coverage weak outside cities]
- [e.g. no restroom field]

## Rejected

- [Provider B] — [reason]
- [Provider C] — [reason]

## Sign-off

| Question | Name | Result | Date |
| --- | --- | --- | --- |
| Data good enough (Product) | | | |
| Licence suitable for embedded automotive use (Product/Legal/Business) | | | |
| API technically workable (Tech Lead) | | | |
