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
| AAOS / embedded vehicle use permitted? | → Legal. Terms page not readable as text; search-index text says the licence excludes "Automotive Usage or Navigation Functionality" without a separate written agreement (unverified) [T1]. POI Details is "Automotive only" [T4][T6]. |  |  |
| Evidence / contractual source | Developer Portal Terms and conditions [T1] (no version or date readable); POI Details conditions [T4]; Pricing [T6] |  |  |
| REST POI search | Yes: Search API `nearbySearch` (GET) [T2] |  |  |
| Nearby / category search | `lat`, `lon`, `radius`, `categorySet`, `openingHours`, `limit` ≤ 100 [T2] |  |  |
| Along-route capability | Search API `searchAlongRoute` (POST, `maxDetourTime` ≤ 3600 s, `categorySet`) [T5] |  |  |
| Coffee coverage | Pending the live matrix |  |  |
| Family coverage | Pending the live matrix |  |  |
| Scenic coverage | Pending the live matrix |  |  |
| Rating / count | Not in Search results [T2]. POI Details API: `value`, `totalRatings` — "Automotive only", Tripadvisor conditions [T4], "Contact Sales" [T6] |  |  |
| Opening hours | `openingHours=nextSevenDays` → `poi.openingHours` time ranges [T2] |  |  |
| Useful amenities | No parking or toilet fields in the Nearby response [T2]. Family categories: zoos, amusement parks, leisure centres; no playground code [T3] |  |  |
| Attribution | "© TomTom. All rights reserved…" (+ OSM ODbL notice for Orbis Maps), on a website or the app's About screen; not stated for Search results [T7] |  |  |
| Caching restrictions | Not documented in fetchable pages; terms not readable [T1][T9] → Legal |  |  |
| Authentication model | API key; restrict by domain (CORS) and product; mobile apps: proxy server recommended, obfuscate, rotate by remote config; no app/package restriction documented [T8] |  |  |
| POC cost | "Start for free": Search API "Free 2.5K monthly"; POI Details only via "Contact Sales" [T6]; rate limits by plan [T9] |  |  |
| Production path plausible | Documented: "Pay as you grow", "Enterprise"; automotive-only products via Contact Sales [T6] |  |  |

Cells cite the provider's own pages ([T…] TomTom, [H…] HERE, [G…] Geoapify) listed under Evidence. Licence rows quote and point to Legal; they never answer yes or no (docs/05 §4: engineering does not interpret licence language). Coverage rows wait for the live matrix.

## Evidence (public documentation)

Read on 2026-10-05 from each provider's public pages, without an account. These are signals, not approvals.

### TomTom

- **[T1]** Terms and conditions (Developer Portal). <https://docs.tomtom.com/legal/terms-and-conditions> (developer.tomtom.com/terms-and-conditions redirects here). The page is rendered by script, and its text could not be fetched; no version or date was readable. Web-search index text for this page reports the licence grant as "…excluding any Automotive Usage or Navigation Functionality except as specifically permitted by TomTom under a separate written agreement…". **This wording is unverified: read the page itself. → Legal.**
- **[T2]** Search API, Nearby Search. <https://docs.tomtom.com/search-api/documentation/search-service/nearby-search>.
  - `GET /search/{versionNumber}/nearbySearch/.{ext}` with `lat`, `lon`, `radius`, `categorySet`, `openingHours`, `relatedPois`, `limit` (max 100).
  - Response: `poi.name`, `poi.categorySet`, `poi.classifications`, `poi.openingHours`, `poi.phone`, `poi.brands`, `position`, `dist`, `entryPoints`, `dataSources`.
  - No rating field.
- **[T3]** Search API, Supported category codes. <https://docs.tomtom.com/search-api/documentation/product-information/supported-category-codes>. Code names only: the numeric IDs come from the POI Categories endpoint, which needs a key.
  - CAFE_PUB ("café", "coffee shop"…), RESTAURANT, PARK_RECREATION_AREA, BEACH, ZOOS_ARBORETA_BOTANICAL_GARDEN, AMUSEMENT_PARK, LEISURE_CENTER, SCENIC_PANORAMIC_VIEW ("scenic/panoramic view", "observation point"), IMPORTANT_TOURIST_ATTRACTION, MUSEUM.
  - No playground code is listed.
- **[T4]** Points of Interest Details API. <https://docs.tomtom.com/poi-details-api/documentation/poi-details-api/poi-details>.
  - `GET /search/{versionNumber}/poiDetails.{ext}?id=…`, where the id comes "from the poiDetails section at the bottom of the Search response".
  - Response: rating `value`, `totalRatings`, `scale`; price range; reviews; photos.
  - Marked "Automotive only".
  - Conditions quoted on the page: "Rich Content shall not be provided to Enterprise Customers under any circumstances"; "Partner shall ensure that Tripadvisor's logos and ratings bars will be served directly from Tripadvisor URLs"; displays must remain "non-indexable by search engines, unless otherwise agreed".
- **[T5]** Search API, Along Route Search. <https://docs.tomtom.com/search-api/documentation/search-service/along-route-search>. `POST /search/{versionNumber}/searchAlongRoute/{query}.{ext}`; `maxDetourTime` "Maximum value: 3600 seconds"; `categorySet` supported; at least 2 route points.
- **[T6]** Pricing. <https://docs.tomtom.com/pricing>.
  - Plans: "Start for free", "Pay as you grow", "Enterprise".
  - Search API: "Free 2.5K monthly".
  - Points of Interest API, Parking API and Fuel Prices API: "Automotive only", "Contact Sales".
  - Per-1,000 prices were not shown in the fetched page.
- **[T7]** TomTom Orbis Maps, Copyright. <https://docs.tomtom.com/tomtom-orbis-maps/documentation/copyright>. The notice must be presented "either within a website or within the About screen of your application".
  - TomTom text: "© TomTom. All rights reserved. This material is proprietary and the subject of copyright protection, database right protection, and other intellectual property rights owned by TomTom or its suppliers."
  - OpenStreetMap text: "© OpenStreetMap contributors. This data is licensed under the terms of the Open Database License (ODbL)…"
  - The page does not say whether this applies to Search results.
- **[T8]** API key management best practices. <https://docs.tomtom.com/platform/documentation/api-best-practices/api-key-management-best-practices>.
  - Keys can be restricted by domain (a CORS whitelist) and by product.
  - For mobile apps: "Use a proxy server to interact with TomTom API"; "Encrypt or obfuscate the API key"; rotate the key "through a remote config mechanism".
  - No app/package or IP restriction is documented.
- **[T9]** Search API, FAQ. <https://docs.tomtom.com/search-api/documentation/product-information/faq>. Rate limits "depend on your subscription plan". The FAQ says nothing about caching.

### HERE

### Geoapify

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

### Candidate category codes (documented)

| Category | TomTom | HERE | Geoapify |
| --- | --- | --- | --- |
| Coffee | CAFE_PUB ("café", "coffee shop") [T3] |  |  |
| Food | RESTAURANT [T3] |  |  |
| Outdoors | PARK_RECREATION_AREA, BEACH [T3] |  |  |
| Family | ZOOS_ARBORETA_BOTANICAL_GARDEN, AMUSEMENT_PARK, LEISURE_CENTER; no playground code [T3] |  |  |
| Scenic | SCENIC_PANORAMIC_VIEW ("scenic/panoramic view", "observation point") [T3] |  |  |
| Explore | IMPORTANT_TOURIST_ATTRACTION, MUSEUM [T3] |  |  |

### Selected provider


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
