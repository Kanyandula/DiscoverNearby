# ADR-002: UI Stack After V7 — Compose or Car App Library

**Status:** Proposed — awaiting the Product Lead  
**Date:** 2026-10-05  
**Deciders:** Product Lead · Android/Tech Lead

---

## Context

Revision 4 replaced the Car App Library templates with a Jetpack Compose activity. That made rotary support
the app's job (UX spec §16), and the delivery plan made it a check inside M0: V7.

DN-M0-011 ran V7 on the reference image (`AAOS_AOSP_33_userdebug`, Extended Controls → rotary). The result is
**Not workable as built**:

- **What works.** On a freshly opened Discover grid, rotation follows the UX order (Coffee → Food → Outdoors →
  Family → Scenic → Explore), an app-drawn 4 dp ring shows focus, and select opens the category.
- **What fails.** After in-app navigation, the rotary service can be left unable to move focus. On Place
  Details, Navigate could not be reached. After Back, rotation stopped on Recommendations and on Discover.
- **Why**, from the RotaryController log:
  - On each navigation, the ComposeView host takes View focus again, and the service makes the host its focused node.
  - From the host, the service rotates through the previous screen's virtual nodes. Compose rejects those stale ids, so every move fails.
  - Compose reports its own focused node to the service only after something has fetched that node.
- **Restoring focus to the originating tile after Back made rotation stop**, so it was dropped.
- **Three app-side workarounds were tried and time-boxed:**
  - a subtree-changed event after each destination
  - the same event on every redraw
  - a host-focus reset

  Each worked on some runs and trapped rotation on others. None meets the bar: the full journey and the Back
  path work by rotary, with focus visible and Navigate reachable.

This fails UX spec §16 ("The primary flow must be fully usable through rotary input"). It also puts at risk the
GO condition "AAOS flow works" (delivery plan §7).

V8 is already confirmed and bears on the same choice. Play accepts `distractionOptimized` only on the Car App
Library's `CarAppActivity`. A Compose app therefore ships through an OEM/preinstall route.

Evidence: ticket DN-M0-011 (completion notes), and the outcome section of
`docs/superpowers/plans/2026-10-05-dn-m0-011-rotary-focus.md`.

## Options

### A. Car App Library templates

The host renders the templates and owns focus and rotary, so V7 becomes the platform's problem. Templates also
give a Play route (V8).

- **Cost:** the `ui/` layer is rebuilt as templates (grid, list and pane for Discover, Recommendations and Place
  Details), and the docs go back to their pre-Revision 4 UI decisions.
- **Unaffected:** `discovery/`, `model/`, `places/`, `location/` and `car/` already keep Compose out, by rule.
- **Trade-off:** the visual spec (`docs/design/`) is constrained to what the templates allow.

### B. Keep Compose

The visual spec stays as designed, and the work done so far is kept.

- **Gap:** rotary on the core path stays unreliable on the reference image, so UX spec §16 and V7 remain open.
- **What it needs:** a fix that does not depend on run-to-run timing. Candidates were not evaluated in the time-box:
  - a different focus host per destination
  - changes upstream in Compose or the rotary service
- **Diagnostic only:** a newer AAOS image or another Compose version can show where the fault lies. Neither one changes the result on the reference image.
- **Distribution:** stays OEM/preinstall (V8).

## Decision

**[A / B]**, decided by [Product Lead] on [date].

## Consequences

- **A:** a plan revision for the UI layer, a new M0 baseline for templates, and V7 re-run against the templates.
- **B:** V7 stays open and blocks M0 exit until a reliable fix is found. Rotary is out of scope for the screens built
  until then.
