# Discover Nearby — AAOS UX & Interaction Specification

**Status:** Draft — Revision 4  
**Platform:** Android Automotive OS  
**Phase:** Proof of Concept  
**Owners:** Product + Design  
**Primary Test Environment:** AAOS Emulator  
**Last Updated:** 2026-10-02

> **Revision 2:** This revision names the template for each screen. It adds a Permission Required state and a Timeout state, and makes Loading/Error states of the same screen rather than new screens. The result count is now "up to 3–5", with no padding. Distance replaces travel time until the latter is calculated. Attributes are shown only when provided or derived. Outdoors and Scenic are now distinct categories, the second screen is consistently called "Recommendations", and maps are deferred.
>
> **Revision 3:** Adds §22 with links to the design canvas (screens, overview diagram, icon set). Interaction rules are unchanged.
>
> **Revision 4:** The UI is built in Kotlin + Jetpack Compose as a distraction-optimized AAOS activity, replacing Car App Library templates. Screens are described by layout, not template. The app now builds focus (§16) and applies driving restrictions itself (§17). The design canvas becomes the visual spec (§22). Flows, states and copy are unchanged.
>
> **Revision 4.1:** "Parked" in the engineering sense now means *the UX restrictions don't require distraction optimization* (`DrivingState.distractionOptimizationRequired == false`). The app reads UX restrictions, not the gear; AOSP advises against inferring driving state from them ([AOSP](https://source.android.com/docs/automotive/driver_distraction/consume)). V8 is confirmed from the AAOS developer guide. User-facing copy still says "park", because that is what the driver does.

---

## 1. Purpose

This document defines the expected user experience for the Discover Nearby AAOS proof of concept.

It describes:

- primary screens and the layout each one uses
- user flows
- interaction rules
- content hierarchy
- loading, empty, permission and error states
- driving-oriented UX principles

The app draws its own UI in Jetpack Compose, so this spec and the design canvas (§22) are the visual source of truth. Implementation details are left to the Engineering Implementation Plan.

---

## 2. Experience Goal

Discover Nearby should feel like a **decision assistant for spontaneous driving stops**, not a conventional map search tool.

The user should be able to move from:

> “I feel like doing something”

to:

> “Take me there”

with very few interactions.

---

## 3. UX Principles

### 3.1 Keep choices small

Show **up to 3–5 strong recommendations**. If only one or two credible places exist, show those. Never pad the list with weak results.

The number shown never exceeds the list limit the vehicle's driving restrictions allow (§17).

### 3.2 Prefer intent over search syntax

The user should not have to type:

> “family friendly park with playground and toilets near me”

They should be able to choose:

> Family

and receive useful recommendations.

### 3.3 Optimize for driving context

Where possible, show:

- distance (or travel time / time ahead / detour once calculated)
- parking
- toilets
- useful stop attributes

rather than dense descriptive information.

### 3.4 Show facts, not guesses

An attribute is shown only if the data source provides it, or if it is reliably derived from the place type. Unknown attributes are omitted, not guessed.

### 3.5 Reduce cognitive load

Avoid:

- large filter trees
- long reviews
- photo galleries
- complex comparison tables
- deeply nested navigation
- long-form descriptions

### 3.6 Preserve a clear exit path

Every screen supports predictable Back behaviour.

---

## 4. Primary Information Architecture

```text
Discover               (category grid)
   ↓
Recommendations        (list)
   ↓
Place Details          (details pane)
   ↓
Navigate               (handoff to navigation app)
```

Loading, empty, permission and error states are **states of these screens**, not extra screens pushed onto the stack. This keeps the back stack shallow and Back predictable.

Optional stretch flow:

```text
Discover               (category grid)
   ↓
Along Route            (list — fixed destination presets)
   ↓
Stops Ahead            (list)
   ↓
Place Details          (details pane)
   ↓
Navigate
```

### Screen summary

| Screen / state | Layout (Compose) |
| --- | --- |
| Discover | 2 × 3 category grid |
| Recommendations (loading, content) | List |
| Place Details (loading, content) | Details pane with Navigate |
| Permission Required, errors, empty | Message state (title, body, up to two actions) drawn inside the owning screen |
| Along Route presets (stretch) | List |
| Stops Ahead (stretch) | List |

No map is used in the POC. A map does not help prove the hypothesis and adds implementation and licensing complexity.

---

## 5. Screen 1 — Discover

**Layout:** 2 × 3 category grid

### Purpose

Allow the user to quickly express what they are looking for.

### Primary content

- Coffee
- Food
- Outdoors
- Family
- Scenic
- Explore

Each tile has a short label and an icon (vector drawables from the icon set, not emoji).

### Conceptual layout

```text
Discover Nearby

┌────────────┐  ┌────────────┐  ┌────────────┐
│  Coffee    │  │  Food      │  │  Outdoors  │
└────────────┘  └────────────┘  └────────────┘

┌────────────┐  ┌────────────┐  ┌────────────┐
│  Family    │  │  Scenic    │  │  Explore   │
└────────────┘  └────────────┘  └────────────┘
```

### Behaviour

Selecting a category:

1. opens the Recommendations screen in its loading state
2. starts discovery for that category
3. shows the category name as the screen title

If location permission has not been granted, the Permission Required state is shown first (§10).

### POC constraint

No advanced filter screen.

---

## 6. Screen 2 — Recommendations

**Layout:** list

### Purpose

Present a short list of strong choices.

### Example

```text
Family Nearby

Riverside Park
4.2 km · Playground · Parking

Forest Park
7.8 km · Trails · Playground

Visitor Centre
9.1 km · Café
```

### Recommendation row content

Each row contains only what is needed for an initial choice:

- Place name
- Distance (or travel time, if the provider supplies it reliably)
- Rating, only if the provider supplies it
- 1–3 provided or derived attributes
- Optional route relevance / detour (stretch only)

Rows must render cleanly when rating or attributes are missing.

### Result count

**Up to 3–5 recommendations**, capped by the driving-restriction list limit (§17). One or two results is a valid outcome.

### States on this screen

| State | Shown as |
| --- | --- |
| Loading | List in loading state with title “Family Nearby” |
| Content | Recommendation rows |
| Empty | Message: “No good matches nearby” |
| Location unavailable | Message (§11) |
| Network / provider failure | Message (§12) |
| Timeout | Message (§12) |

Changing state redraws the same screen. It does not push a new screen.

### Selection

Selecting a recommendation opens Place Details.

---

## 7. Screen 3 — Place Details

**Layout:** details pane

### Purpose

Give the user enough confidence to decide whether to navigate there.

### Example

```text
Forest Park

7.8 km away
★ 4.8                 (only if provided)

Park · Playground · Parking

[Navigate]
```

### Content, where available

- Place name
- Distance (or travel time, if calculated)
- Rating (if provided)
- Category
- Provided or derived amenities
- Open/closed status (if provided)
- Provider attribution, if the provider requires it here
- **Navigate** as the primary action

### Details fetch failure

If richer details cannot be loaded, show the information already known from the Recommendations row with the **Navigate** action still available. Do not block navigation on an optional details call.

### Not required for POC

- Full review feed
- User-generated comments
- Photo gallery
- Menu browsing
- Long descriptions
- Social actions
- Booking
- Account features

---

## 8. Screen 4 — Stops Ahead / Along Route

**Stretch Goal**

### Along Route (destination presets)

**Layout:** list

The user chooses from fixed destination presets (for example: Galway, Wicklow, Kilkenny). Free-text destination entry is out of scope.

### Stops Ahead

**Layout:** list

```text
Family Stops Ahead

Riverside Park
18 min ahead · +4 min detour

Visitor Centre
36 min ahead · +6 min detour
```

### Principle

Prefer journey impact over geographic distance. Show “ahead” and “detour” values only when the implementation actually calculates them.

---

## 9. Loading State

Example:

```text
Finding good places nearby…
```

- The screen title and category context remain visible.
- The UI stays stable while results are fetched; avoid rapidly replacing content.
- Loading never continues indefinitely. After the provider timeout, the Timeout state is shown.
- A late response for a previous request never replaces the current screen's content.

---

## 10. Permission Required

**Layout:** message state

Location permission prompts may only be shown while the vehicle is parked.

In this spec, "parked" means the car's UX restrictions do not require distraction optimization. The app reads those restrictions, not the gear (Engineering Plan §6). On the reference emulator this is the Park state.

### While parked

```text
Location permission required

Discover Nearby needs your location
to find places around you.

[Grant Permission]   [Back]
```

### While driving

```text
Location permission required

Park the vehicle to allow Discover Nearby
to access your location.

[Back]
```

The Grant action becomes available when the vehicle is parked.

### Permission denied

```text
Location permission required

Discover Nearby can't find places without
location access.

[Grant Permission]   [Back]
```

### Permission denied permanently

After the user has refused twice, Android no longer shows the permission dialog. While parked, Settings takes
Grant's place (DN-UX-001):

```text
Location permission required

Location access is off for Discover Nearby.
Turn it on in Settings.

[Open Settings]   [Back]
```

While driving, the park-first copy is shown, with Back only. Back from Settings searches again.

When permission is granted, discovery continues for the selected category.

---

## 11. Location Unavailable

Permission is granted, but no location fix is available.

```text
Location unavailable

Discover Nearby needs a location to find places around you.
```

Actions:

- Retry
- Back

---

## 12. Network / Provider Error / Timeout

```text
Unable to load places

Check your connection and try again.
```

Actions:

- Retry
- Back

The same message covers network failure, provider failure and timeout. Engineering distinguishes them internally for logging.

---

## 13. Empty State

```text
No good matches nearby

Try another category.
```

Actions:

- Back to Discover

The app does not substitute irrelevant results to avoid an empty screen. If only one or two credible places exist, they are shown (this is not an empty state).

---

## 14. Navigation Handoff Failure

```text
Navigation unavailable

No compatible navigation app could open this destination.
```

Action:

- Back

---

## 15. Touch Behaviour

All primary actions must:

- use large touch targets (at least 76 dp, as in NyasaPlayer) on full-width rows and buttons
- not require precise touch gestures
- not depend on drag or hidden swipe interactions

---

## 16. Rotary Behaviour

The primary flow must be fully usable through rotary input.

With a Compose UI, **the app builds rotary support**. Every actionable element is focusable, shows a clearly visible focus indicator, follows the order below, and gets focus back sensibly after Back. AAOS's rotary service turns controller input into focus movement; the app makes sure Compose responds to it (delivery plan §9, V7).

Expected order:

```text
Discover:         Coffee → Food → Outdoors → Family → Scenic → Explore
Recommendations:  Result 1 → Result 2 → Result 3 …
Place Details:    Content → Navigate
```

Verify that:

- Back always returns to the previous logical screen
- focus lands on a sensible element after Back
- no primary action is unreachable
- focus is never lost or trapped

Any problems found are app defects.

---

## 17. Park vs Drive Behaviour

The same core flow is available in Park and Drive.

The app enforces driving restrictions itself, from the platform's `CarUxRestrictions`: while driving, it caps list length to the platform limit and disables parked-only actions (those allowed only when distraction optimization is not required). The activity is declared distraction-optimized; without that, AAOS blocks it while driving. The app does not add separate Park-only content in the POC.

Differences the user may see:

- The location permission prompt is only available while parked (§10).
- The number of visible recommendations may be lower if the driving restrictions lower the list limit.
- If the limit allows no results at all, the list is replaced by a message (DN-UX-001):

  ```text
  Park to see places

  Results can't be shown while driving.
  Park the vehicle, then try again.

  [Try Again]   [Back]
  ```

  Parking shows the places on its own; Try Again searches again.

The POC validates that the flow remains stable when the vehicle state changes, including while results are loading.

---

## 18. Provider Attribution

The selected provider may require attribution (wording, logo, placement). The provider ADR records:

```text
Provider attribution
→ required placement
→ required wording / logo
→ applicable screens
```

The attribution UI is designed once the provider is selected. Likely locations are a Place Details row or an action on Recommendations. Attribution is not invented before then.

---

## 19. Content Guidelines

Use:

- short labels
- familiar category names
- concise attributes
- action-oriented primary buttons

Avoid:

- marketing language
- long explanatory text
- ambiguous labels
- technical terminology

Good:

> Family

> 4.2 km

> 18 min ahead · +4 min detour

> Navigate

Avoid:

> Discover family-oriented recreational opportunities

---

## 20. Category Behaviour

Attributes are shown only when **provided** or **derived** (see Product Brief §10).

### Coffee

Practical, nearby stops. Useful attributes: open now, parking, drive-through, rating.

### Food

Useful attributes: open now, parking, restaurant type, rating.

### Outdoors — “I want to do something outdoors”

Park, trail, forest, beach, hiking area, outdoor attraction. Useful attributes: parking.

### Family

Playground, zoo, aquarium, family attraction, park. Useful attributes: toilets, café, parking.

### Scenic — “I want to go somewhere because it is nice to look at”

Viewpoint, scenic spot, coastal overlook, landmark, waterfall, natural attraction.

### Explore

Attraction, museum, landmark, historic place, local point of interest.

---

## 21. POC UX Acceptance Criteria

- From launch, the user reaches recommendations with one category selection.
- Recommendations show up to 3–5 results, never padded, and render cleanly with missing data.
- Only provided or derived attributes are displayed.
- Touch supports the complete primary journey.
- Rotary supports the complete primary journey without touch.
- Back navigation is predictable; focus is visible, ordered and never trapped.
- Permission Required, Loading, Empty, Location unavailable, Error/Timeout and Navigation unavailable states are represented.
- Loading never continues indefinitely.
- Changing categories produces intent-appropriate recommendations.
- Place Details exposes a clear Navigate action, even if the details fetch fails.
- The core flow remains functional in emulator Park and Drive states.

---

## 22. Design References

The screens are mocked up on the design canvas **"Discover Nearby — AAOS POC Screens"** ([canvas](https://claude.ai/artifact/3v68QUCWeu87t7n2GFf8nf); private until shared from its Share menu).

**Exported PNGs are in `design/`** next to these docs, with an index in `design/README.md`. Use these when the canvas link isn't accessible, for example from Claude Code.

The canvas contains:

- **Overview** — the app-flow diagram: vehicle system UI vs Discover Nearby vs outside the app, the states attached to each screen, and the Navigate hand-off.
- **Core flow** — Discover, Recommendations, Place Details, and the stub navigation hand-off.
- **States** — Permission Required (parked and driving), Location Unavailable, Loading, Empty, Network/Provider Error, Timeout, Navigation Unavailable, Sparse Results, and Details summary-only fallback.
- **Stretch** — Along Route presets and Stops Ahead.
- **Icon set** — the app icon, six category icons, amenity icons and state/action icons, with suggested resource names (`ic_cat_*`, `ic_amenity_*`, `ic_*`).

**How to read the mockups:** the app draws its own UI in Compose, so the mockups are the **visual spec**: layout, hierarchy, copy, states, icons and colours. Colours and type become a Material 3 theme (day and night). System chrome (status bar, system navigation) still comes from the vehicle. Template names in artboard titles (`GridTemplate` and so on) date from Revision 3; read them as layout patterns. Frames are 1408 × 792 and the reference emulator is 1024p landscape, so layouts adapt to the screen instead of hard-coding frame sizes.
