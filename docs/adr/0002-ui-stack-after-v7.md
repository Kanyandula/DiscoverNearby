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
  Details, rotation was stuck and Navigate could not be reached. After Back twice, rotation stopped on Discover.
  On the workaround builds, it also stopped on Recommendations after Back.
- **Why**, from the RotaryController log:
  - On each navigation, the ComposeView host takes View focus again, and the service makes the host its focused node.
  - From the host, the service rotates through the previous screen's virtual nodes. Compose rejects those stale ids, so every move fails.
  - Compose reports its own focused node to the service only after something has fetched that node.
  - On the shipped build, the service received focus events only for the host View, none for Compose's own
    nodes; a View-based app (Settings) sent them. So nothing told the service that its cached nodes were stale.
- **Restoring focus to the originating tile after Back made rotation stop**, so it was dropped.
- **Four app-side workarounds were tried and time-boxed:**
  - a subtree-changed event after each destination
  - the same event on every redraw
  - a host-focus reset
  - a `<queries>` entry for accessibility services: Compose then sent its events, but rotation stayed trapped

  Each worked on some runs and trapped rotation on others. None meets the bar: the full journey and the Back
  path work by rotary, with focus visible and Navigate reachable.

This fails UX spec §16 ("The primary flow must be fully usable through rotary input"). It also puts at risk the
GO condition "AAOS flow works" (delivery plan §7).

V8 is already confirmed and bears on the same choice. Play accepts `distractionOptimized` only on the Car App
Library's `CarAppActivity`. A Compose app therefore ships through an OEM/preinstall route.

Evidence: see below, plus the outcome section of
`docs/superpowers/plans/2026-10-05-dn-m0-011-rotary-focus.md`.

## Evidence

Screenshots from the reference image (`docs/adr/0002/`):

| Shot | What it shows |
| --- | --- |
| [1-ring-on-family.png](0002/1-ring-on-family.png) | Fresh grid, four turns: the ring on Family. |
| [2-details-stuck.png](0002/2-details-stuck.png) | Place Details after two turns: no focus anywhere, Navigate not reachable. |
| [3-discover-after-two-backs-stuck.png](0002/3-discover-after-two-backs-stuck.png) | Discover after Back twice: the ring on Coffee, but turns do nothing. |
| [4-restore-trap.png](0002/4-restore-trap.png) | The dropped restore: the ring back on Family after Back, then three turns that do nothing. |

RotaryController log on the shipped build, Recommendations → Place Details, then one turn (`adb logcat -s RotaryController`, trimmed):

```text
onAccessibilityEvent: EventType: TYPE_VIEW_FOCUSED   source: ComposeView host, Rect(0, 76 - 1024, 672)
Focus event wasn't caused by performing an action
mFocusedNode set to: host
onRotaryEvents ROTATE cw=true
mFocusedNode is in a WebView or ComposeView: host
Found focused node host
Failed to perform ACTION_FOCUS on node Rect(48, 246 - 976, 382)   ← a Recommendations row, no longer on screen
```

Source references:

- AOSP `packages/apps/Car/RotaryController`, `android13-release`:
  - `RotaryService.initFocus` assumes focus is already set up when its focused node is inside a ComposeView.
  - `handleViewFocusedEvent` adopts any focus event not caused by its own action.
- Compose UI 1.12.1, `AndroidComposeViewAccessibilityDelegateCompat`:
  - `performActionHelper` returns false for a node id that is no longer in the tree.
  - `findFocus(FOCUS_INPUT)` returns a node only once that node's info has been fetched.

### Car App Library rotary probe (DN-SP-002, 2026-10-05)

Option A's premise, that templates give rotary a working journey, was tested on the same reference image.

**The probe**
- [`tools/cal-rotary-probe/`](../../tools/cal-rotary-probe/README.md): a throwaway, standalone build, not in the root build or CI.
- Car App Library 1.7.0. Template host 1.007 on the image; it negotiated Car API level 7.
- Grid, list and pane templates with static data, and a Navigate action.
- Driven with the same `cmd car_service inject-rotary` / `inject-key` as V7.

**Result: did not pass.** The journey (grid → list → details → Navigate → Back) could not be completed by rotary in any run.

| Launch | Rotary entered the probe? | What happened |
| --- | --- | --- |
| First launch after install | Yes | Grid order Coffee → Food → Outdoors → Family → Scenic → Explore, host-drawn ring visible, stops at the end; the header icon never takes focus ([5](0002/5-cal-grid-focus.png)) |
| Relaunch after force-stop; Home then reopen; reinstall then start; touch reset then start; uninstall then fresh install; return from another app | No (all 6) | The first turn focused a system-bar button (the top bar's "Driver", or the bottom bar) and stayed there ([6](0002/6-cal-focus-outside.png)). Nudges up/down moved between the system bars and skipped the probe. Select then drove system UI: the Dialer, the profile switcher, Settings ([7](0002/7-cal-select-drives-system-ui.png)) |
| Same stuck state, then the **Compose** app launched | (Compose) Yes | The first turn focused Coffee, the next Food |

RotaryController log for the probe's window, trimmed:

```text
Failed to find focused node in … Rect(0, 0 - 1024, 768) android.widget.FrameLayout
Restored focus in root failed
Initialize focus inside the window: … type=TYPE_SYSTEM …
onAccessibilityEvent: EventType: TYPE_VIEW_FOCUSED; PackageName: com.google.android.apps.automotive.templates.host
event source: … Rect(136, 188 - 324, 394)          ← the probe's first tile
Ignoring focus event because focus has since moved
```

The service knows the host and the probe (`hostApp=com.google.android.apps.automotive.templates.host`, `clientApps=[com.kanyandula.calprobe]`).

**Reading.** On entry, RotaryController looks for a focus target inside the template app's window synchronously, finds none, and initialises focus in a system bar. The template host then focuses the app's first tile asynchronously, but the service has already moved on and ignores that event. This is a race, and once it is lost, rotation and nudges can't reach the app. The cause is not fully established, and a probe-configuration cause is not ruled out within the time-box. The list, details, Navigate and Back steps were never reached by rotary, so templates' behaviour there is unknown.

## Options

### A. Car App Library templates

The host renders the templates and owns focus and rotary, so V7 becomes the platform's problem. Templates also
give a Play route (V8).

- **Cost:** the `ui/` layer is rebuilt as templates (grid, list and pane for Discover, Recommendations and Place
  Details), and the docs go back to their pre-Revision 4 UI decisions. That reopens V1 (Car App API level) and V2
  (how template UIs are tested).
- **Available:** the reference image ships the template host (`com.google.android.apps.automotive.templates.host`).
- **Unaffected:** `discovery/`, `model/`, `places/`, `location/` and `car/` already keep Compose out, by rule.
- **Trade-off:** the visual spec (`docs/design/`) is constrained to what the templates allow.
- **Rotary probe: did not pass** (DN-SP-002, Evidence above). On the reference image, rotary entered a template app only on its first launch after install; on every other launch, focus stayed in the system bars and the app was unreachable. Templates do not, as tested, make V7 "the platform's problem": the platform shows its own entry failure. No engineering recommendation for Option A follows from the probe.

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
- **B:** V7 stays open and blocks M0 exit until a reliable fix is found. Choosing B also means the Product Lead
  accepts an explicit scope cut: until then, the screens built don't meet docs/02 §16 for rotary.
