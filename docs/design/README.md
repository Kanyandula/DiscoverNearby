# Discover Nearby — Design Exports

PNG exports of the design canvas "Discover Nearby — AAOS POC Screens", exported 2026-10-02. Screens are 1408 × 792.

**How to read these (Revision 4):** the app draws its own UI in Jetpack Compose, so these mockups are the visual spec: layout, hierarchy, copy, states, icons and colours. System chrome still comes from the vehicle. The template names below and in artboard titles date from Revision 3; read them as layout patterns.

**With HERE data (ADR-001):** the attribution is "© {year} HERE" under the list and on Place Details. HERE supplies no photos, ratings, review counts or amenities such as parking. Photos and ratings come from Tripadvisor for the places it knows (ADR-003, about half of them); the others show the category's artwork and no rating. The only amenity shown is "Car park nearby", from HERE's car parks within 150 m (DN-UX-005); rows and details render cleanly without it (UX spec §6, §7). The kind of place (for example, "Café") and open now come from HERE.

**Place Details follows artboard 3c (DN-UX-007):** `03c-place-details-header.png` (the Product Lead, 2026-10-11, drawn at the emulator's 1024 × 768) rearranges the concept, `03b-place-details-concept.png` (DN-UX-005), which replaced `03-place-details.png`. Back and the place name head the page, as on the other screens; the photo sits under Back, with the facts and the description beside it; the tiles sit below the photo. The app keeps an in-app Back (the AOSP car bar has none), the distance (on the kind line), the photo credit and the HERE notice. Its tiles show only what a source has: the place's own amenities (none from HERE) and "Car park nearby", whose parking icon is a white P on an Action-blue badge (5.2:1; Google asks 4.5:1 of icons); Wi-Fi, outdoor seating and family friendly have no source (DN-SP-005). The ★ rating is Tripadvisor's own graphic (ADR-003). The rail on the left of the 1408 × 792 mockups is the car's system UI (`00-overview.png`). The app's own rail (DN-UX-006) sits left of the panel under the header: Discover (grid) and Saved (bookmark), icons only, the selected tab on Raised grey. Save (outline bookmark) ↔ Saved (filled bookmark) sits left of Navigate on Raised grey.

| File | Screen | Layout pattern (Rev 3 template name) |
| --- | --- | --- |
| `00-overview.png` | App flow overview: system UI vs app vs outside, states per screen, Navigate hand-off | — |
| `00-icons.png` | Icon set with suggested resource names | — |
| `01-discover.png` | Discover | GridTemplate |
| `02-recommendations.png` | Recommendations | ListTemplate |
| `03-place-details.png` | Place Details | PaneTemplate |
| `03b-place-details-concept.png` | Place Details (the Product Lead's concept; the layout reference until DN-UX-007) | — |
| `03c-place-details-header.png` | Place Details (artboard 3c, 1024 × 768; the layout reference since DN-UX-007) | — |
| `04-navigation-handoff-stub.png` | Stub navigation app (emulator test tool, not Discover Nearby) | MessageTemplate |
| `05a-permission-driving.png` | Permission Required — driving (Back only) | MessageTemplate |
| `05b-permission-parked.png` | Permission Required — parked (Grant + Back) | MessageTemplate |
| `06-location-unavailable.png` | Location Unavailable | MessageTemplate |
| `07-loading.png` | Recommendations, loading state | ListTemplate (loading) |
| `08-empty.png` | Empty | MessageTemplate |
| `09-network-error.png` | Network / provider error | MessageTemplate |
| `10-timeout.png` | Timeout | MessageTemplate |
| `11-navigation-unavailable.png` | Navigation unavailable | MessageTemplate |
| `12-sparse-results.png` | Sparse results (2 strong, no padding) | ListTemplate |
| `13-details-fallback.png` | Place Details, summary only (details fetch failed) | PaneTemplate |
| `14-along-route.png` | Along Route destination presets (stretch) | ListTemplate |
| `15-stops-ahead.png` | Stops Ahead (stretch) | ListTemplate |

Spec references: UX & Interaction Spec (`02-discover-nearby-ux-interaction-spec.md`) §4–§17 and §22.
