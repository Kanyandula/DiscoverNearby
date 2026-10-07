# ADR-001: POI Provider

**Status:** Provisionally selected, pending licensing confirmation (2026-10-07), from the live matrix; Legal sign-off on provider terms pending before production  
*(Outcome at end of the 3-day spike, exactly one of: **Selected** · **Provisionally selected, pending licensing confirmation** · **No viable provider**)*  
**Date:** 2026-10-05 (documentation phase); 2026-10-07 (live matrix and outcome)  
**Deciders:** Android/Tech Lead (API practicality) · Product Lead (data usefulness) · Product/Legal/Business owner (in-vehicle licensing; product evaluation owner: the Product Lead, 2026-10-06; Legal sign-off on terms separate)

---

## Context

Discover Nearby is an embedded Android Automotive OS application. This ADR answers the project's single provider gate (V4):

> **Which provider/service combination is commercially and legally usable for an embedded AAOS application, and is its data good enough to prove Coffee, Family and Scenic?**

Raw OSM data and an OSM-backed hosted service are different things; the question concerns the combination we would actually use.

**Integration constraint:** the core POC (M1–M4) uses the provider's **REST API only**, via OkHttp + kotlinx.serialization and our own mapper. No provider SDK. If M5 needs an SDK, that is a separate ADR.

Candidates, in order:

1. TomTom
2. HERE
3. Geoapify Places API (OSM-based), evaluation only (named at kickoff, 2026-10-05)

## Decision

**HERE** (Geocoding & Search v7 `/browse`), outcome: **Provisionally selected, pending licensing confirmation**

Risk accepted by the Product Lead on 2026-10-07; licensing clarification pending from Legal (HERE Platform Terms
§6.3(a), §6.4, Acceptable Use Policy and Supplier Terms [H1]). Legal sign-off is required before production.

## One-sheet comparison

| Question | TomTom | HERE | Geoapify Places (OSM-based) |
| --- | --- | --- | --- |
| AAOS / embedded vehicle use permitted? | → Legal. Terms page not readable as text; search-index text says the licence excludes "Automotive Usage or Navigation Functionality" without a separate written agreement (unverified) [T1]. POI Details is "Automotive only" [T4][T6]. | → Legal. Platform Terms §6.3 (effective 18 Sep 2023) opens "Except as otherwise permitted in your Subscription Plan or separate agreement with HERE, you may not:"; §6.3(a) lists integrating HERE Materials into "a … vehicle system or any component thereof, including … windshield or display screens installed in the vehicle"; a phone projecting to the vehicle screen "is not an integration". Use with non-HERE data: §6.4(a) [H1] | → Legal. Terms (Version 5, 2 Feb 2024) contain no clause on vehicles, automotive, embedded or navigation use; absence is not a permission [G3] |
| Evidence / contractual source | Developer Portal Terms and conditions [T1] (no version or date readable); POI Details conditions [T4]; Pricing [T6] | HERE Platform Terms, effective 18 September 2023: §6.3, §6.4(a), §6.4(j), §6.4(m), §13.1, Exhibit 2 §1.3 [H1]; Acceptable Use Policy and Supplier Terms listed, not read [H1] | Terms and Conditions, Version 5, 2 February 2024 (KEPTAGO LTD, Cyprus law) [G3]; Places product page [G2]; Pricing [G4]; data licence: OpenStreetMap ODbL [G5] |
| REST POI search | Yes: Search API `nearbySearch` (GET) [T2] | Yes: Geocoding & Search v7 `/browse` (GET) [H2] | Yes: Places API `GET /v2/places` [G1] |
| Nearby / category search | `lat`, `lon`, `radius`, `categorySet`, `openingHours`, `limit` ≤ 100 [T2] | `at`, `in=circle:…` (≤ 250 km), `categories` (levels 1–3), `foodTypes`, `limit` [H2] | `categories` (required), `filter=circle:lon,lat,radiusMeters`, `bias`, `conditions`, `limit`, `offset`, `lang` [G1] |
| Along-route capability | Search API `searchAlongRoute` (POST, `maxDetourTime` ≤ 3600 s, `categorySet`) [T5] | `/browse` with the `route` parameter [H2] | No `route` parameter; [G1] links an "Along a Route" how-to and offers `filter=geometry:<id>` for a previously generated geometry [G1] |
| Coffee coverage | 3/3 Good, 0 Thin, 0 Empty (Greystones · Dublin · Galway: Good · Good · Good); pubs among the nearest results (CAFE_PUB) | 3/3 Good, 0 Thin, 0 Empty (Good · Good · Good) | 3/3 Good, 0 Thin, 0 Empty (Good · Good · Good) |
| Family coverage | 1/3 Good, 2 Thin, 0 Empty (Good · Thin · Thin); massage/spa and comedy venues among the results | 2/3 Good, 1 Thin, 0 Empty (Good · Thin · Good) | 0/3 Good, 3 Thin, 0 Empty (Thin · Thin · Thin); most playgrounds unnamed |
| Scenic coverage | 2/3 Good, 1 Thin, 0 Empty (Good · Good · Thin) | 2/3 Good, 1 Thin, 0 Empty (Good · Good · Thin) | 1/3 Good, 2 Thin, 0 Empty (Good · Thin · Thin); most viewpoints unnamed |
| Rating / count | Not in Search results [T2]. POI Details API: `value`, `totalRatings` — "Automotive only", Tripadvisor conditions [T4], "Contact Sales" [T6] | Not in the documented response [H2]; `show=tripadvisor` adds ratings — "BETA, RESTRICTED", Tripadvisor branding rules [H4] | Not documented [G1][G2] |
| Opening hours | `openingHours=nextSevenDays` → `poi.openingHours` time ranges [T2] | Not documented on [H2] | Not a Places API response field [G1]; the product page attributes opening hours to the separate Place Details API [G2] |
| Useful amenities | No parking or toilet fields in the Nearby response [T2]. Family categories: zoos, amusement parks, leisure centres; no playground code [T3] | No parking or toilet fields documented [H2][H4]. Family categories: zoo, amusement park, water park, children's museum; no playground [H3] | `conditions` filter for accessibility/amenities [G1]; facilities such as wheelchair, internet access, dogs [G2]; `leisure.playground` category exists [G1] |
| Attribution | Search API on Orbis Maps: OSM ODbL notice + "© 1992 - 2023 TomTom. All rights reserved…", placement not stated [T10]; Orbis Maps: on a website or the app's About screen [T7]. TomTom Maps (non-Orbis) Search: not found | Keep all HERE Marks and copyright notices in Results "in accordance with HERE brand guidance" (§13.1) [H1] | OpenStreetMap attribution always; Geoapify attribution on the Free plan [G3]; "Powered by Geoapify" link plus data-source attribution [G4]; ODbL notice [G5] |
| Caching restrictions | Not documented in fetchable pages; terms not readable [T1][T9] → Legal | → Legal. §6.4(j): Results not cached outside the Platform "for more than 30 days" … "unless Results are used solely for your internal testing, evaluation, or record retention for audit and legal compliance purposes"; Exhibit 2 §1.3: only "as explicitly allowed by the caching headers" [H1] | → Legal. Product page: "allowed to cache, store, and redistribute the results … without any additional limits or restrictions" [G2]; Terms say nothing on caching [G3]; ODbL share-alike for derived databases [G5] |
| Authentication model | API key; restrict by domain (CORS) and product; mobile apps: proxy server recommended, obfuscate, rotate by remote config; no app/package restriction documented [T8] | API key (`apiKey`) or OAuth 2.0 bearer tokens; trusted domains (≤ 20); rotate by adding a second key; no app/package restriction documented [H5][H6] | API key (`apiKey` query parameter); restrict by "allowed IP addresses, HTTP referrers, origins, and CORS" [G1] |
| POC cost | "Start for free": Search API "Free 2.5K monthly"; Places Search API Discover/Details "Free5K monthly", Suggest "Free10K monthly"; POI Details only via "Contact Sales" [T6]; rate limits by plan [T9] | Not established: pricing page shows no figures as text [H7] | Free: "3,000 credits / day", no credit card [G4]; Places: "Every 20 places costing 1 credit" [G1] |
| Production path plausible | Documented: "Pay as you grow", "Enterprise"; automotive-only products via Contact Sales [T6] | Not established from readable pages [H7] | Paid plans API 10 ($59/month) to API 250 ($609), Custom from $860 [G4]; Free plan in production "with some limitations" [G3] |

Cells cite the provider's own pages ([T…] TomTom, [H…] HERE, [G…] Geoapify) listed under Evidence. Licence rows quote and point to Legal; they never answer yes or no (docs/05 §4: engineering does not interpret licence language). Coverage rows come from the live matrix (2026-10-07; see Test matrix).

## Evidence (public documentation)

Read on 2026-10-05 from each provider's public pages, without an account. These are signals, not approvals.

### TomTom

- **[T1]** Terms and conditions (Developer Portal). <https://docs.tomtom.com/legal/terms-and-conditions> (developer.tomtom.com/terms-and-conditions redirects here). The page is rendered by script, and its text could not be fetched; no version or date was readable. Web-search index text for this page reports the licence grant as "…excluding any Automotive Usage or Navigation Functionality except as specifically permitted by TomTom under a separate written agreement…". The index text was seen through this session's web search tool on 2026-10-05; the same text uses "Navigation Functionality" as a defined term. **This wording is unverified. A step that needs no account: open the page in a browser and record its wording, version and date. → Legal.**
- **[T2]** Search API (TomTom Maps docs, not Orbis), Nearby Search. <https://docs.tomtom.com/search-api/documentation/search-service/nearby-search>.
  - `GET /search/{versionNumber}/nearbySearch/.{ext}` with `lat`, `lon`, `radius`, `categorySet`, `openingHours`, `relatedPois`, `limit` (max 100).
  - Response: `poi.name`, `poi.categorySet`, `poi.classifications`, `poi.openingHours`, `poi.phone`, `poi.brands`, `position`, `dist`, `entryPoints`, `dataSources`.
  - No rating field.
- **[T3]** Search API (TomTom Maps docs, not Orbis), Supported category codes. <https://docs.tomtom.com/search-api/documentation/product-information/supported-category-codes>. Code names only, no numeric IDs. Inference, not stated on this page: the numeric IDs come from the POI Categories endpoint, which needs a key.
  - CAFE_PUB ("café", "coffee shop"…), RESTAURANT, PARK_RECREATION_AREA, BEACH, ZOOS_ARBORETA_BOTANICAL_GARDEN, AMUSEMENT_PARK, LEISURE_CENTER, SCENIC_PANORAMIC_VIEW ("scenic/panoramic view", "observation point"), IMPORTANT_TOURIST_ATTRACTION, MUSEUM.
  - No playground code is listed.
- **[T4]** Points of Interest Details API. <https://docs.tomtom.com/poi-details-api/documentation/poi-details-api/poi-details>.
  - `GET /search/{versionNumber}/poiDetails.{ext}?id=…`, where the id comes "from the poiDetails section at the bottom of the Search response".
  - Response: rating `value`, `totalRatings`, `scale`; price range; reviews; photos.
  - Marked "Automotive only".
  - Conditions quoted on the page: "Rich Content shall not be provided to Enterprise Customers under any circumstances"; "Partner shall ensure that Tripadvisor's logos and ratings bars will be served directly from Tripadvisor URLs"; "Partner will make all displays of Rich Content on Partner Product non-indexable by search engines, unless otherwise agreed in a Commercial Agreement."
- **[T5]** Search API (TomTom Maps docs, not Orbis), Along Route Search. <https://docs.tomtom.com/search-api/documentation/search-service/along-route-search>. `POST /search/{versionNumber}/searchAlongRoute/{query}.{ext}`; `maxDetourTime` "Maximum value: 3600 seconds"; `categorySet` supported; at least 2 route points.
- **[T6]** Pricing. <https://docs.tomtom.com/pricing>.
  - Plans: "Start for free", "Pay as you grow", "Enterprise".
  - Search API: "Free 2.5K monthly". The newer Places Search API is priced separately: "Places Search API Discover" "Free5K monthly", "Places Search API Details" "Free5K monthly", "Places Search API Suggest" "Free10K monthly". Its docs were not read; docs/05 §4 notes it has no along-route search.
  - Points of Interest API, Parking API and Fuel Prices API: "Automotive only", "Contact Sales".
  - Per-1,000 prices were not shown in the fetched page.
- **[T7]** TomTom Orbis Maps, Copyright. <https://docs.tomtom.com/tomtom-orbis-maps/documentation/copyright>. The notice must be presented "either within a website or within the About screen of your application".
  - TomTom text: "© TomTom. All rights reserved. This material is proprietary and the subject of copyright protection, database right protection, and other intellectual property rights owned by TomTom or its suppliers."
  - OpenStreetMap text: "© OpenStreetMap contributors. This data is licensed under the terms of the Open Database License (ODbL)…"
  - This is the Orbis Maps page; for Search on Orbis see [T10].
- **[T8]** API key management best practices. <https://docs.tomtom.com/platform/documentation/api-best-practices/api-key-management-best-practices>.
  - Keys can be restricted by domain (a CORS whitelist) and by product.
  - For mobile apps: "Use a proxy server to interact with TomTom API"; "Encrypt or obfuscate the API key"; rotate the key "through a remote config mechanism".
  - No app/package or IP restriction is documented.
- **[T9]** Search API, FAQ. <https://docs.tomtom.com/search-api/documentation/product-information/faq>. Rate limits "depend on your subscription plan". The FAQ says nothing about caching.
- **[T10]** Search API on TomTom Orbis Maps, Copyrights. <https://docs.tomtom.com/search-api/documentation/tomtom-orbis-maps/product-information/copyrights>. Two notices: "© OpenStreetMap contributors. This data is licensed under the terms of the Open Database License (ODbL)" and "© 1992 - 2023 TomTom. All rights reserved. This material is proprietary and the subject of copyright protection…". This page does not say where the notices must appear.

### HERE

- **[H1]** HERE Platform Terms, effective "Monday, 18 September, 2023". <https://www.here.com/en-gb/terms/here-platform-terms-september-2023> (the canonical <https://legal.here.com/en-gb/terms/here-platform-terms> redirects here). Verbatim:
  - **§6.3, opening words:** "Except as otherwise permitted in your Subscription Plan or separate agreement with HERE, you may not:"
  - **§6.3(a), restricts:** "Integrate HERE Materials into a ground, aerial, manned, or unmanned vehicle system or any component thereof, including vehicle positioning sensors (for example, GPS, triangulation, odometer, compass, gyroscope, or accelerometer), navigation terminals or black boxes, and windshield or display screens installed in the vehicle."
  - **§6.3(a), exception:** "a mobile device that is connected to a vehicle for the purpose of projecting on the vehicle's windshield or display screen the visual information that is displayed on the mobile device screen is not an integration".
  - **§6.3(b):** no use "for or in connection with any systems or functions for automatic or autonomous control of … vehicle behavior…".
  - **§6.4(j), in full:** no "Cache or store outside of the Platform any Results that include anything from the use of HERE Content or Location Services for more than 30 days, except HERE Positioning services which cannot be cached or stored outside the Platform for more than 24 hours, unless Results are used solely for your internal testing, evaluation, or record retention for audit and legal compliance purposes;"
  - **§6.4(a) (use with other maps; final sentence, on Japan, omitted):** no "Use non-HERE datasets with HERE Content or Results in Applications that are made available to End-Users or third parties (as allowed by your license in these Terms). Notwithstanding the foregoing, and subject to compliance with Section 6.4 b. below and your license to HERE Materials for use in Applications, you may (i) layer Your Content and third-party content with HERE Content or Results for display purposes; and (ii) combine Your Content and third-party content with HERE Content or Results for the purpose of delivering derived location responses, such as a route or search result, provided, in both cases (i) and (ii), that the origin of the HERE Content and non-HERE content can be distinguished and correct attribution can be provided. …"
  - **Exhibit 2 §1.3:** "you may not use HERE Materials in a manner that pre-fetches, caches, or stores data or Results, except as explicitly allowed by the caching headers (HTTP/1.1 standard) returned by HERE Location Services".
  - **§13.1:** "You may not remove or obfuscate any HERE Marks or copyright notices affixed to or included in HERE Materials or Results. You will ensure that all HERE Marks and copyright notices are present in the HERE Materials and Result in accordance with HERE brand guidance".
  - **§6.4(m):** no "incorrect attribution of any information derived from the Platform".

  Not read: the HERE terms hub <https://legal.here.com/us-en/terms> also lists a "HERE Acceptable Use Policy" and "Supplier Terms" ("terms applicable to location and other content made available through HERE products and services"). No Platform Terms newer than 18 September 2023 were found.

  **→ Legal.**
- **[H2]** Geocoding & Search v7, Browse. <https://docs.here.com/geocoding-and-search/docs/endpoint-browse-brief>.
  - `GET https://browse.search.hereapi.com/v1/browse` with `at`, `in=circle:…` (up to 250 km), `categories` (levels 1–3), `foodTypes`, `limit`, `lang`, and `route` ("search along the route").
  - Response items: `title`, `categories`, `distance`, `foodTypes`, `chains`.
  - No opening-hours, contact or rating field on this page (the page text has no `openingHours` or `contacts`).
- **[H3]** Geocoding & Search v7, Places category system. <https://docs.here.com/geocoding-and-search/docs/places-category-system-full>.
  - 100-1100-0010 Coffee Shop (100-1100 Coffee-Tea); 100-1000 Restaurant.
  - 550-5510-0202 Park-Recreation Area; 550-5510-0205 Beach; 350-3522-0239 Forest, Heath or Other Vegetation.
  - 550-5520-0208 Zoo; 550-5520-0207 Amusement Park; 550-5520-0357 Water Park; 300-3100-0027 Children's Museum.
  - 550-5510-0242 Scenic Point; 350-3510-0238 Mountain Peaks.
  - 300-3000-0023 Tourist Attraction; 300-3000 Landmark-Attraction; 300-3100 Museum.
  - No playground category is listed.
- **[H4]** Geocoding & Search v7, Response enrichment. <https://docs.here.com/geocoding-and-search/docs/response-enrichment>. `show=tripadvisor` "Adds available Tripadvisor rich attributes (image, ratings, editorials)". It is marked "BETA, RESTRICTED". Apps must "comply with the Tripadvisor Branding Guidelines", and mobile apps must render a "Find out more" link.
- **[H5]** Identity & Access Management, API keys. <https://docs.here.com/identity-and-access-management/docs/plat-using-apikeys>.
  - The key is passed as `apiKey`.
  - "Trusted domains" limit a key to "designated sites" (up to 20).
  - Rotation: create a second key, then delete the original.
  - No app/package restriction is documented.
- **[H6]** Identity & Access Management, OAuth 2.0. <https://docs.here.com/identity-and-access-management/docs/how-to-authorize-with-oauth-20>. OAuth 2.0 bearer tokens are offered as an alternative (page title and URL from the index; page not fetched).
- **[H7]** Pricing. <https://www.here.com/get-started/pricing>. The page text shows no figures (rendered by script). Free allowance and prices are unknown without an account or a browser. A 2018 HERE press release (<https://www.here.com/about/press-releases/2018-01-08>) announced a freemium plan; treat it as dated.
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

### Geoapify

Named at kickoff (2026-10-05) as the OSM-backed candidate, **for evaluation only**.

- **[G1]** Places API reference. <https://apidocs.geoapify.com/docs/places/>.
  - `GET https://api.geoapify.com/v2/places` with `apiKey`, `categories` (required), `filter` (`circle:lon,lat,radiusMeters`, `rect:…`, `place:…`), `bias`, `conditions` ("Additional filters such as accessibility or amenities"), `limit`, `offset`, `lang`.
  - Response: `name`, `lat`, `lon`, `place_id`, `distance` ("to the biased location"), `categories`.
  - Categories: `catering.cafe`, `catering.cafe.coffee`, `catering.restaurant`, `leisure.park`, `leisure.playground`, `entertainment.zoo`, `entertainment.theme_park`, `tourism.attraction.viewpoint`, `entertainment.museum`, `natural.beach`, `natural.mountain.peak`.
  - No `route` parameter. The page links a how-to, "How to Find Restaurants, Fuel Stations, or Parking Along a Route", and `filter=geometry:geometryId` can "Re-use a previously generated geometry (e.g. isoline)".
  - No rating or opening-hours fields on the page.
  - Key protection: "You can also protect your API key by listing allowed IP addresses, HTTP referrers, origins, and CORS for added security."
  - Credits: "Every 20 places costing 1 credit"; "If you retrieve 115 places, the cost would be 6 credits."
- **[G2]** Places API product page. <https://www.geoapify.com/places-api/>.
  - "you are allowed to cache, store, and redistribute the results obtained from the API without any additional limits or restrictions".
  - Facilities are mentioned (for example "wheelchair accessibility", "internet access", "dogs"), and a `datasource` with "sourcename: openstreetmap" and "attribution: © OpenStreetMap contributors". Opening hours are attributed to the separate Place Details API ("enriched, structured data … such as its address, contact information, opening hours").
  - OpenStreetMap is the "primary data source".
  - "For each request made to the Places API, you will be charged 1 credit".
- **[G3]** Terms and Conditions, "2 February 2024 (Version 5)", KEPTAGO LTD (Cyprus law). <https://www.geoapify.com/terms-and-conditions/>.
  - **No clause** on vehicles, automotive, embedded devices or navigation use, and **no clause** on caching or storing results. That absence is not a permission. **→ Legal.**
  - Verbatim: "When using the Services, you must always provide OpenStreetMap attribution."; "Geoapify attribution is mandatory when using Free subscription plan."; "Different APIs may require additional attribution…".
  - On the Free plan: "The commercial use of the Free-package is allowed in the development and, with some limitations, in the production phase."
- **[G4]** Pricing. <https://www.geoapify.com/pricing/>.
  - Free: "3,000 credits / day", "No credit card required"; "You can use the Free plan for commercial websites, apps, and business projects, including in production."
  - Paid, monthly: API 10 $59, API 25 $109, API 50 $179, API 100 $299, API 250 $609; Custom "from $860".
  - Attribution: "Powered by Geoapify" link plus data-source attribution.
  - Note: the production wording differs from [G3] ("with some limitations").
- **[G5]** Data licence: OpenStreetMap copyright and licence. <https://www.openstreetmap.org/copyright>.
  - "Open Data Commons Open Database License (ODbL)"; "Provide credit to OpenStreetMap by displaying our attribution notice" and "Make clear that the data is available under the Open Database License".
  - Share-alike: "If you alter or build upon our data, you may distribute the result only under the same license."
  - This is the data licence, separate from Geoapify's service terms [G3].

## Test matrix (selected provider, and each candidate tested)

Run on 2026-10-07 from a development Mac on a home connection (latency is indicative, not in-vehicle), with
dev-only keys, through `tools/provider-eval/evaluate.py`. Limit 10 per query. Radii from `CategoryConfigs` (Coffee
5 km, Family 15 km, Scenic 30 km). Categories as in "Candidate category codes"; TomTom IDs confirmed through its
POI Categories endpoint (CAFE_PUB 9376; ZOOS_ARBORETA_BOTANICAL_GARDEN 9927, AMUSEMENT_PARK 9902, LEISURE_CENTER 9378;
SCENIC_PANORAMIC_VIEW 7337). All 27 queries returned HTTP 200.

**What is recorded here:** the repo is public, so this ADR holds derived metrics and verdicts only, with no names,
place IDs, coordinates or raw responses. The raw responses and the names were kept locally (git-ignored) for the
judgment and deleted on 2026-10-07.

**Metrics** (Hours, Phone, Web, Amenities: results with that field; distances in metres; Amenities is Geoapify's
`facilities` and is not measured for TomTom or HERE, shown as —):

| Provider | Category | Location | HTTP | ms | Results | Hours | Phone | Web | Amenities | Nearest m | Median m |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tomtom | Coffee | Greystones | 200 | 382 | 10 | 2 | 2 | 3 | — | 28 | 112 |
| tomtom | Coffee | Dublin | 200 | 302 | 10 | 3 | 4 | 5 | — | 48 | 135 |
| tomtom | Coffee | Galway | 200 | 193 | 10 | 1 | 2 | 4 | — | 41 | 108 |
| tomtom | Family | Greystones | 200 | 251 | 10 | 0 | 3 | 9 | — | 149 | 6930 |
| tomtom | Family | Dublin | 200 | 205 | 10 | 3 | 1 | 10 | — | 144 | 243 |
| tomtom | Family | Galway | 200 | 249 | 10 | 2 | 0 | 9 | — | 49 | 103 |
| tomtom | Scenic | Greystones | 200 | 235 | 10 | 0 | 0 | 1 | — | 2987 | 14054 |
| tomtom | Scenic | Dublin | 200 | 272 | 10 | 0 | 0 | 1 | — | 337 | 16295 |
| tomtom | Scenic | Galway | 200 | 180 | 2 | 0 | 0 | 0 | — | 19366 | 21722 |
| here | Coffee | Greystones | 200 | 304 | 10 | 7 | 10 | 6 | — | 37 | 95 |
| here | Coffee | Dublin | 200 | 141 | 10 | 4 | 9 | 5 | — | 23 | 32 |
| here | Coffee | Galway | 200 | 149 | 10 | 8 | 8 | 7 | — | 49 | 161 |
| here | Family | Greystones | 200 | 116 | 7 | 2 | 4 | 2 | — | 1869 | 6975 |
| here | Family | Dublin | 200 | 126 | 10 | 3 | 6 | 4 | — | 36 | 708 |
| here | Family | Galway | 200 | 131 | 10 | 5 | 8 | 6 | — | 197 | 2084 |
| here | Scenic | Greystones | 200 | 121 | 10 | 4 | 4 | 6 | — | 11896 | 17336 |
| here | Scenic | Dublin | 200 | 122 | 10 | 4 | 5 | 7 | — | 1542 | 12338 |
| here | Scenic | Galway | 200 | 120 | 3 | 3 | 1 | 1 | — | 121 | 933 |
| geoapify | Coffee | Greystones | 200 | 390 | 10 | 2 | 0 | 3 | 8 | 27 | 167 |
| geoapify | Coffee | Dublin | 200 | 251 | 10 | 4 | 0 | 5 | 9 | 29 | 95 |
| geoapify | Coffee | Galway | 200 | 392 | 10 | 5 | 6 | 6 | 8 | 50 | 173 |
| geoapify | Family | Greystones | 200 | 487 | 10 | 0 | 0 | 0 | 2 | 479 | 2151 |
| geoapify | Family | Dublin | 200 | 439 | 10 | 0 | 0 | 0 | 0 | 300 | 518 |
| geoapify | Family | Galway | 200 | 570 | 10 | 0 | 0 | 0 | 0 | 227 | 686 |
| geoapify | Scenic | Greystones | 200 | 255 | 10 | 0 | 0 | 0 | 1 | 2635 | 3940 |
| geoapify | Scenic | Dublin | 200 | 506 | 10 | 0 | 0 | 0 | 3 | 920 | 3322 |
| geoapify | Scenic | Galway | 200 | 426 | 10 | 0 | 0 | 0 | 3 | 89 | 978 |

**Relevance and verdicts** (relevant / named relevant / results; verdict on named relevant: Good ≥ 3, Thin 1–2,
Empty 0; a recommendation row shows the place name, so unnamed results do not count toward the verdict):

| Provider | Coffee G | Coffee D | Coffee Gal | Family G | Family D | Family Gal | Scenic G | Scenic D | Scenic Gal |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TomTom | 5/5/10 Good | 6/6/10 Good | 3/3/10 Good | 3/3/10 Good | 1/1/10 Thin | 1/1/10 Thin | 10/10/10 Good | 10/10/10 Good | 2/2/2 Thin |
| HERE | 10/10/10 Good | 10/10/10 Good | 10/10/10 Good | 5/5/7 Good | 1/1/10 Thin | 7/7/10 Good | 7/7/10 Good | 7/7/10 Good | 2/2/3 Thin |
| Geoapify | 10/10/10 Good | 10/10/10 Good | 10/10/10 Good | 10/2/10 Thin | 10/1/10 Thin | 10/2/10 Thin | 10/7/10 Good | 10/1/10 Thin | 10/2/10 Thin |

G = Greystones, D = Dublin, Gal = Galway. Relevant: Coffee is a café, coffee shop or tea room (a pub, bar or
restaurant without coffee in its name or categories does not count); Family is a playground, zoo or aquarium, farm
park, amusement, theme or water park, play centre, family amusement arcade, karting or children's museum, and a
leisure centre only when its categories say it serves families (not gaming or slot arcades, casinos, massage or
spa, comedy, cinema, escape rooms or general sports clubs); Scenic is a viewpoint, scenic point, peak, cliff walk
or coastal lookout. Duplicates count once. Totals: TomTom 6 Good, 3 Thin; HERE 7 Good, 2 Thin; Geoapify 4 Good,
5 Thin; no Empty cells. This is the rule as applied; it widens the plan's draft for Family (aquarium, play centre,
family arcade, karting). The raw output is deleted, so the per-cell counts cannot be re-checked.

## Field availability (selected provider)

| Field | Provided / Derivable / Unavailable | Coverage notes |
| --- | --- | --- |
| Rating | Unavailable | Not in `/browse` responses [H2]; ratings only through `show=tripadvisor`, "BETA, RESTRICTED", with Tripadvisor branding rules [H4] |
| Rating count | Unavailable | As Rating |
| Open now | Provided | `openingHours[].isOpen` on 40 of 80 results (Coffee 19/30, Family 10/27, Scenic 11/23) |
| Parking | Unavailable | No parking field in the 80 results; not documented [H2] |
| Toilets | Unavailable | No toilet field in the 80 results; not documented [H2] |
| Family information | Derivable | From `categories` (Amusement Park, Zoo, Water Park, Children's Museum); no age or family attributes |
| Travel time from origin | Unavailable | `distance` in metres from `at` only; M1 ranks by distance |
| Along-route search | Provided | `/browse` `route` parameter [H2]; M5 only, not live-tested |
| Contact (phone / web) | Provided | `contacts` on 62 of 80 results (phone 55, web 44) |

Hours, phone and web counts come from the Metrics table. The field names (`openingHours[].isOpen`, `contacts` on 62
of 80 results, and the absence of any parking or toilet field) come from a survey of the raw responses' field names
on 2026-10-07, before the raw output was deleted; they cannot be re-derived from the committed table.

## Category mapping

### Candidate category codes (documented)

| Category | TomTom | HERE | Geoapify |
| --- | --- | --- | --- |
| Coffee | CAFE_PUB ("café", "coffee shop") [T3] | 100-1100-0010 Coffee Shop (100-1100 Coffee-Tea) [H3] | `catering.cafe`, `catering.cafe.coffee` [G1] |
| Food | RESTAURANT [T3] | 100-1000 Restaurant [H3] | `catering.restaurant` [G1] |
| Outdoors | PARK_RECREATION_AREA, BEACH [T3] | 550-5510-0202 Park-Recreation Area, 550-5510-0205 Beach, 350-3522-0239 Forest… [H3] | `leisure.park`, `natural.beach` [G1] |
| Family | ZOOS_ARBORETA_BOTANICAL_GARDEN, AMUSEMENT_PARK, LEISURE_CENTER; no playground code [T3] | 550-5520-0208 Zoo, 550-5520-0207 Amusement Park, 550-5520-0357 Water Park, 300-3100-0027 Children's Museum; no playground [H3] | `leisure.playground`, `entertainment.zoo`, `entertainment.theme_park` [G1] |
| Scenic | SCENIC_PANORAMIC_VIEW ("scenic/panoramic view", "observation point") [T3] | 550-5510-0242 Scenic Point, 350-3510-0238 Mountain Peaks [H3] | `tourism.attraction.viewpoint`, `natural.mountain.peak` [G1] |
| Explore | IMPORTANT_TOURIST_ATTRACTION, MUSEUM [T3] | 300-3000-0023 Tourist Attraction, 300-3000 Landmark-Attraction, 300-3100 Museum [H3] | `entertainment.museum`; `tourism.attraction` family (parent of viewpoint) [G1] |

### Selected provider

| Category | Provider types / categories |
| --- | --- |
| Coffee | 100-1100 Coffee-Tea (live-tested) |
| Food | 100-1000 Restaurant (documented, not live-tested) |
| Outdoors | 550-5510-0202 Park-Recreation Area, 550-5510-0205 Beach, 350-3522-0239 Forest, Heath or Other Vegetation (documented, not live-tested) |
| Family | 550-5520-0208 Zoo, 550-5520-0207 Amusement Park, 550-5520-0357 Water Park, 300-3100-0027 Children's Museum (live-tested) |
| Scenic | 550-5510-0242 Scenic Point, 350-3510-0238 Mountain Peaks (live-tested) |
| Explore | 300-3000-0023 Tourist Attraction, 300-3000 Landmark-Attraction, 300-3100 Museum (documented, not live-tested) |

**First live category (DN-M1-002): Coffee.** It is HERE's only category rated Good at all three test locations
(Test matrix). It was verified live on the emulator at Greystones, Dublin and Galway on 2026-10-07. The other five
categories also run live from DN-M1-002, but are accepted in M2 (DN-M2-001, DN-M2-002).

## V6a — Attribution

| Field | Value |
| --- | --- |
| Required text | "© 20XX HERE" [H8]; §13.1 requires notices "in accordance with HERE brand guidance" [H1]. The responses carry no notice of their own [H10] |
| Required logo | Not required by [H8]; [H9] governs a logo if one is shown |
| Must be always visible? | Not stated for an app without a map [H8] → Legal |
| Can appear in About / Data Sources? | "In mobile handsets, including watches and embedded in-car systems, the HERE copyright may alternatively be displayed in the 'About HERE' section in the Settings" [H8]; tied to a map → Legal |
| Required on Recommendations? | Not established. §6.4(a): when combined with non-HERE content, "the origin of the HERE Content and non-HERE content can be distinguished and correct attribution can be provided" [H1] |
| Required on Place Details? | Not established |

**Implemented (DN-M1-003; Product Lead decision, 2026-10-07):** "© {current year} HERE", a muted line under the
Recommendations list and at the bottom left of Place Details. It shows only for HERE data
(`PlaceSummary.attribution`), so the fakes show none. Whether that satisfies [H8] for an app without a map is
pending Legal.

## V6b — Caching and storage

| Field | Value |
| --- | --- |
| May POI responses be persisted? For how long? | → Legal. §6.4(j): Results not cached outside the Platform "for more than 30 days" … "unless Results are used solely for your internal testing, evaluation, or record retention for audit and legal compliance purposes" [H1]. POC: no persistence (in memory) |
| May place IDs be stored? | → Legal. Not addressed in the sections read. The app writes no place data to storage. The opened place's summary (with its ID) travels in the navigation route, so it sits in the back stack's saved state while the task exists (DN-M0-005) |
| May responses be recorded as test fixtures? | No: none recorded (public repo); revisit with Legal |
| Caching-header rules | Exhibit 2 §1.3: only "as explicitly allowed by the caching headers (HTTP/1.1 standard) returned by HERE Location Services" [H1]; headers not recorded in this run |

Default until this is filled in: **no persistence** (in-memory, discarded at session end).

## V6c — Authentication

| Field | Value |
| --- | --- |
| Key / auth model | API key (`apiKey` query parameter) or OAuth 2.0 bearer tokens [H5][H6] |
| Can keys be restricted (package, signing, referrer)? | Trusted domains (up to 20) [H5]; no app/package or signing restriction documented |
| Key rotation / revocation | Create a second key, then delete the original [H5] |
| Requires backend proxy, device auth, signed tokens, or SDK credentials? (→ additional scope) | Not required by the API. A key in the APK can be extracted; the POC accepts that with a dev-only, low-quota key. Production: decide between a proxy and OAuth before release |

POC baseline: dev-only key in `local.properties` → `BuildConfig`, low quota, easy to revoke, never committed.

## Known limitations

- Family is thin in central Dublin (1 relevant of 10): the amusement-park code also returns gaming arcades, casinos
  and sports venues.
- Scenic is thin around Galway (2 relevant of 3), and one result's position looks wrong.
- Some results are miscategorised businesses (for example, roofing contractors and a restaurant under Scenic Point).
- The nearest Scenic result from Greystones is 11.9 km away.
- No rating, parking or toilet data in `/browse` responses.
- Latency (116–304 ms) was measured from a development Mac on a home connection, not in the vehicle.
- HERE's free allowance and pricing are not established [H7].
- Licence: the §6.3(a) quote goes to Legal before production.

## Rejected

- TomTom — not selected: the Product Lead judged only HERE's data good enough (2026-10-07). Matrix observations:
  Family mixed in massage/spa and comedy venues (Thin in Dublin and Galway); Coffee mixes in pubs (CAFE_PUB).
  Licence text [T1] unverified → Legal.
- Geoapify — not selected: the Product Lead judged only HERE's data good enough (2026-10-07). Matrix observations:
  most playgrounds and viewpoints are unnamed (Family Thin in all three locations). Terms silent on vehicles [G3]
  → Legal.

## Sign-off

| Question | Name | Result | Date |
| --- | --- | --- | --- |
| Data good enough (Product) | Product Lead | Yes for HERE (Coffee, Family and Scenic overall); No for TomTom and Geoapify | 2026-10-07 |
| Licence suitable for embedded automotive use (Product/Legal/Business) | Legal | Pending: risk accepted by the Product Lead until then | — |
| API technically workable (Tech Lead) | Tech Lead | Yes, with the limits listed (no ratings, parking or toilets; key in the APK) | 2026-10-07 |
