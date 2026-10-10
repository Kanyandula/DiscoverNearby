# Discover Nearby — Design Exports

PNG exports of the design canvas "Discover Nearby — AAOS POC Screens", exported 2026-10-02. Screens are 1408 × 792.

**How to read these (Revision 4):** the app draws its own UI in Jetpack Compose, so these mockups are the visual spec: layout, hierarchy, copy, states, icons and colours. System chrome still comes from the vehicle. The template names below and in artboard titles date from Revision 3; read them as layout patterns.

**With HERE data (ADR-001):** the attribution is "© {year} HERE" under the list and on Place Details. HERE supplies no photos, ratings, review counts or amenities such as parking, so those parts of `02` and `03` stay empty until Product decides what replaces them or drops them; rows and details render cleanly without them (UX spec §6, §7). The kind of place (for example, "Café") and open now are available.

| File | Screen | Layout pattern (Rev 3 template name) |
| --- | --- | --- |
| `00-overview.png` | App flow overview: system UI vs app vs outside, states per screen, Navigate hand-off | — |
| `00-icons.png` | Icon set with suggested resource names | — |
| `01-discover.png` | Discover | GridTemplate |
| `02-recommendations.png` | Recommendations | ListTemplate |
| `03-place-details.png` | Place Details | PaneTemplate |
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
