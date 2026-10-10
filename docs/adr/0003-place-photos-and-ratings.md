# ADR-003: Place Photos and Ratings

**Status:** Accepted for the emulator POC (Product Lead, 2026-10-10); Tripadvisor's terms go to Legal before
production  
**Date:** 2026-10-10 (spike DN-SP-004; build DN-UX-004)  
**Deciders:** Product Lead (source, risk acceptance) · Android/Tech Lead (feasibility) · Legal (terms, before
production)

---

## Context

The mockups give every Recommendations row a photo and a rating, and Place Details a photo panel and a rating
(`docs/design/02`, `03`). HERE, the place provider ([ADR-001](0001-poi-provider.md)), returns no photos, ratings or
review counts, so live rows showed only a name and a distance. The Product Lead judged the POC "will not look nice
without the photos" (2026-10-10).

## Options (DN-SP-004 spike, 2026-10-10)

Coverage was measured on the benchmark matrix: 3 test locations × 6 categories, HERE's nearest 10 places per cell,
173 places in all. A place counts when the source has a photo of that place (matched by name near HERE's position).

| Source | Photos of the place | Access | Outcome |
| --- | --- | --- | --- |
| HERE `show=tripadvisor` enrichment | — | "BETA, RESTRICTED" (ADR-001 [H4]); our account gets HTTP 403 | Not available |
| TomTom POI Photos | — | "Automotive only", not in the free evaluation or pay-as-you-grow offers; sales only | Not available |
| OpenStreetMap tags (via Geoapify) and Wikidata | 14 of 173 (8 %): Coffee 0/30, Food 1/30 | Free | Landmarks only |
| Wikimedia Commons photos taken nearby | Most places have one, but a sample showed mostly other things nearby | Free | Misleading |
| **Tripadvisor (Terra)** | **74 of 173 (43 %)**: Coffee 15/30, Food 10/30, Outdoors 10/30, Family 11/30, Scenic 14/23, Explore 14/30 | Self-serve key; 1,000 free calls a month | **Chosen** |
| Foursquare Places | Not measured | Photos are a paid endpoint (developer credits) | Not needed |
| Yelp Fusion | Not measured | Paid; weak coverage in Ireland | Not pursued |
| Google Places | — | Restricts in-vehicle use (removed in Revision 2) | Excluded |

When Tripadvisor matched a place, it had a photo 93 % of the time (74 of 80).

## Decision

**Tripadvisor (Terra) supplies the photo and rating of the places on screen.** HERE stays the source of places,
ranking input and navigation positions; Tripadvisor cannot replace it (an 8 km search limit, three categories, no
re-sorting of its results, and the call budget).

- **Display only.** Ratings never feed the ranking: only the shown rows are enriched, after ranking, so the M2
  benchmark stays comparable.
- **How:** for each of the at most five rows, a nearby search 200 m around HERE's position (RESTAURANT for Coffee and
  Food, ATTRACTION otherwise), a strict name match, then the matched location's first photo. Two calls a place.
- **Strict matching:** a wrong match would show another place's photo and rating, which is worse than none. Names
  match on the same words, on one added word (a town), or on nearly the same spelling; two added words is no match.
  The best match wins, the nearer on a tie.
- **Failures** (no match, HTTP 429, network, timeout) leave the row as it was, with the category artwork; nothing is
  retried. The nearby endpoint allows one call a second in bursts of five.
- **Nothing is stored.** Tripadvisor's caching policy allows only its location ID. The app keeps none, and images load
  through a memory-only cache (no disk cache).
- **No calls in development or tests.** The fakes, CI and Robolectric get no enrichment.
- **Display:** Tripadvisor's own rating graphic (its owl mark and bubbles, from the API) on a white chip with the review
  count; "Photo: Tripadvisor" under the Place Details photo. The API masks who took a photo.

Risk accepted by the Product Lead on 2026-10-10 for the emulator POC, as for HERE.

## Terms → Legal before production

Engineering quotes and does not interpret (docs/05 §4). From Tripadvisor's API Master Terms (Terra docs) and policies:

- §3.4.2(a): include the required Tripadvisor mark and "link back to the relevant Tripadvisor Site, per the Linking
  Policy". The app shows the mark but no link: a link that opens a browser does not suit an in-car app.
- §3.4.2(f): "not combine Licensed Content with other content", with an aggregated rating system as the example;
  §3.4.2(h): clearly separate Licensed Content and marks from other content. Here Tripadvisor's photo and rating sit
  on a HERE place.
- §3.4.5: during the term, no licensing, collecting or displaying of content similar to the Licensed Content.
- §9.13: the licence covers the "Customer Application", the website or mobile app named at signup; an in-car app
  needs Tripadvisor's agreement.
- Caching policy: only the location ID may be cached.
- The production route for HERE ([ADR-001](0001-poi-provider.md#production-licensing)) applies here too: once Product
  picks the distribution route, Tripadvisor confirms in-vehicle use in writing, then Legal reviews.

## Costs

The Discover plan includes 1,000 calls a month; a card is on the account, so calls beyond that are charged. Each list
costs up to 10 calls (5 places × 2). Development and tests use the fakes; live checks are kept few.

## Known limitations

- About half the places on screen get a photo; the rest keep the category artwork.
- Strict matching misses some places whose names differ a lot between the two providers.
- HERE's position errors carry over: a place HERE puts in the wrong spot can still match Tripadvisor by name and show
  its photo at HERE's distance (ADR-001 Known limitations).
- Tripadvisor's nearby search is not strict about category; the name match is what keeps the result right.

## Evidence

- DN-SP-004 spike notes (private vault): per-source coverage, desk research, and the Terra request shapes.
- Tripadvisor Terra documentation: nearby search, location photos, display requirements, caching policy, rate limits,
  API Master Terms (<https://docs.terra.tripadvisor.com/>).
- TomTom POI Photos (<https://docs.tomtom.com/poi-photos-api/documentation/product-information/introduction>).
