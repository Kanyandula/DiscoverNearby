# ADR-002: UI Stack After V7 — Compose or Car App Library

**Status:** Proposed — awaiting the Product Lead  
**Date:** 2026-10-05  
**Deciders:** Product Lead · Android/Tech Lead

> **Update 2026-10-06 (DN-SP-003): V7 is confounded; clean E1 pending.** It is neither passed nor failed.
> - **Compose:** on two cold boots, the Compose failure described below reproduced only when the test harness
>   waited with `uiautomator`. Without it, the journey completed 4 of 4.
> - **Probe:** its entry failure is real, and it still reproduced without `uiautomator`.
>
> See [Cold-boot comparison](#cold-boot-comparison-dn-sp-003-2026-10-06).

---

## Context

Revision 4 replaced the Car App Library templates with a Jetpack Compose activity. That made rotary support
the app's job (UX spec §16), and the delivery plan made it a check inside M0: V7.

DN-M0-011 ran V7 on the reference image (`AAOS_AOSP_33_userdebug`). Rotary was injected with `cmd car_service
inject-rotary` / `inject-key`, not driven by hand from Extended Controls. The result is
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
- Grid, list and pane templates with static data, and a Navigate action. The manifest follows the AAOS guide, including `automotive_app_desc` (`<uses name="template"/>`).
- Driven with the same `cmd car_service inject-rotary` / `inject-key` as V7.

**Result: did not pass.** The journey (grid → list → details → Navigate → Back) could not be completed by rotary in any run.

| Launch | Rotary entered the probe? | What happened |
| --- | --- | --- |
| The first launch of the session (just after the first install) | Yes, once | Grid order Coffee → Food → Outdoors → Family → Scenic → Explore, host-drawn ring visible, stops at the end; the header icon never takes focus ([5](0002/5-cal-grid-focus.png)). **Not reproduced**, not even after uninstall and a fresh install. |
| Relaunch after force-stop; Home then reopen; reinstall then start; touch reset then start; uninstall then fresh install; return from another app | No (all 6) | The first turn focused a system-bar button (the top bar's "Driver", or the bottom bar) and stayed there ([6](0002/6-cal-focus-outside.png)). Nudges up/down moved between the system bars and skipped the probe. Select then drove system UI: the Dialer, the profile switcher, Settings ([7](0002/7-cal-select-drives-system-ui.png)) |
| After the review: `automotive_app_desc` added; entering from the Compose app with the grid rendered and a 30 s wait | No (3 of 3) | The ring was already on "Driver" before any turn. Turn 1 went to "Driver" twice, and once to the probe's whole window rather than a tile |
| The same entry test with Car App Library **1.4.0** | No (3 of 3) | Same as above |
| Exception, outside the launch tests | Yes | Once, after Back from a Settings screen, focus landed on the probe's Coffee tile |
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

A first scripted run was discarded: it turned before the probe had rendered (a cold start takes 15–20 s). It showed the same symptom: focus went to the bottom bar's Phone button.

**Reading.** On entry, RotaryController looks for a focus target inside the template app's window synchronously, finds none, and initialises focus in a system bar. The template host then focuses the app's first tile asynchronously, but the service has already moved on and ignores that event. This matches the AOSP `android13-release` RotaryService source: `initFocus` → `restoreDefaultFocusInRoot`, then `handleViewFocusedEvent` drops focus events it isn't waiting for.

This is a race. Once it was lost in these runs, rotation and nudges did not bring focus back into the app, with the one exception above. Adding `automotive_app_desc` and switching library versions did not change the result. The race's client half (`CarAppActivity`) ships inside the app as Car App Library code.

The list, details, Navigate and Back steps were never reached by rotary, so templates' behaviour there is unknown.

**Not tested** (any of these could change the picture):
- a freshly started rotary service or a cold-booted emulator, the condition of the one success (tested
  2026-10-06, see below);
- a newer AAOS image or template host;
- Car App Library 1.9.0, still alpha.

### Cold-boot comparison (DN-SP-003, 2026-10-06)

Every earlier result came from one long-running emulator session. Two cold boots then ran both apps, in both
orders.

**Where the record is**
- The full record is [2026-10-06-cold-boot-runs.md](0002/2026-10-06-cold-boot-runs.md): conditions, every
  focus reading, and the scripts as run ([`harness-2026-10-06/`](0002/harness-2026-10-06/)).
- Rotary was injected by adb, not driven by hand from Extended Controls.
- RotaryController logs were not saved to files; only the quoted excerpts exist.

| | Compose | Car App Library probe |
| --- | --- | --- |
| Full journey, no `uiautomator` | 4 of 4 completed. Navigate was selected (the stub received the URI) in 2 runs and focused in 2. | 2 of 2: the first launch after each boot ([13](0002/13-cal-navigate-focus.png)) |
| Full journey, with a `uiautomator` wait or dump | 0 of 3. The host takes focus after navigation; on Recommendations the second turn is ignored; select then fires the header Back ([12](0002/12-compose-uiautomator-run-back-fired.png)). In one more run a select was lost: only `ACTION_UP` was injected. | Not run |
| Entry by rotary | Every run | Boot 2: the first 4 entries; then 0 of 7, with or without `uiautomator`, and after restarting the template host process ([15](0002/15-cal-entry-two-rings.png)) |

**Reading**
- **Compose: the tool and the timing are not separated yet.**
  - V7's failure reproduced only when the harness waited with `uiautomator`. Every run block in the
    DN-M0-011 plan starts with that wait (`waitfor.sh Coffee 40`).
  - Those runs also started turning sooner after launch than the clean ones.
  - E1 (DN-SP-003) separates the two. Until then V7 is **confounded; clean E1 pending**.
- **The clean Compose runs still miss V7's existing bar:**
  - after Back to Recommendations, the service focuses the ComposeView host and the first turn is absorbed
    ([10](0002/10-compose-back-host-focus.png));
  - rows, header Back and Navigate show focus only as a faint tint ([8](0002/8-compose-row-focus-tint.png),
    [9](0002/9-compose-navigate-focus.png)); the 4 dp ring is on the tiles only
    ([11](0002/11-compose-discover-after-back.png));
  - Back lands on Coffee, not the originating tile. In-app restore is off by rule, and that rule came from the
    same `uiautomator`-waited harness.
  - These need app work or an explicit Product decision. They are not counted as passing.
- **Probe: the entry failure is not a harness artefact.**
  - It reproduced 11 minutes after a cold boot.
  - In the log, the turn first focuses the probe's tile; within 300 ms focus moves to a system bar.
  - Once rotary is inside, the journey works.
- **Probe, other findings on this emulator:**
  - a select takes 10–14 s to render the next template; a select sent before then still counts, and stacks an
    extra screen ([14](0002/14-cal-extra-screen-on-stack.png));
  - Back on the root grid does not leave the app;
  - after Back, focus goes to the first item.
- **Platform guidance:**
  - AAOS documents the limit on this release: "Android 12, Android 12L, and Android 13: AAOS provides limited
    rotary support for Compose UIs. Controller rotation works with an app-side workaround. We don't support
    nudging."
  - And: "Android 14 and higher: AAOS provides basic built-in rotary support for Jetpack Compose user
    interfaces, including controller rotation and nudging"
    ([AAOS 25Q4 release notes](https://source.android.com/docs/automotive/start/releases/aaos-25q4), read
    2026-10-06).
  - Nudging was not tested.

## Options

### A. Car App Library templates

The premise: the host renders the templates and owns focus and rotary, so V7 would become the platform's problem.
The probe below tested this premise. Templates also give a Play route (V8).

- **Cost:** the `ui/` layer is rebuilt as templates (grid, list and pane for Discover, Recommendations and Place
  Details), and the docs go back to their pre-Revision 4 UI decisions. That reopens V1 (Car App API level) and V2
  (how template UIs are tested).
- **Available:** the reference image ships the template host (`com.google.android.apps.automotive.templates.host`).
- **Unaffected:** `discovery/`, `model/`, `places/`, `location/` and `car/` already keep Compose out, by rule.
- **Trade-off:** the visual spec (`docs/design/`) is constrained to what the templates allow.
- **Rotary probe: did not pass** (DN-SP-002, Evidence above). On the reference image, rotary entered the template app on one launch, the first of the session, and in none of 12 later launches. Focus stayed in the system bars instead. As tested, the template path (Car App Library 1.7.0 / 1.4.0, host 1.007) showed its own entry failure on this image, so the premise that templates make V7 "the platform's problem" was not confirmed. No engineering recommendation for Option A follows from the probe.
  - **Cold boots (2026-10-06):** the probe completed the journey on the first launch after each boot. Entry still failed from the fifth entry on, with or without `uiautomator`. Templates don't fix rotary by themselves on this image.

### B. Keep Compose

The visual spec stays as designed, and the work done so far is kept.

- **Gap:** V7 is confounded (2026-10-06), and UX spec §16 and V7 remain open. Even the clean runs miss V7's
  existing bar in three places: focus visibility on rows, header Back and Navigate; the turn absorbed after Back;
  and focus after Back.
- **What it needs:** a fix that does not depend on run-to-run timing. Candidates were not evaluated in the time-box:
  - a different focus host per destination
  - changes upstream in Compose or the rotary service
- **Diagnostic only:** a newer AAOS image or another Compose version can show where the fault lies. Neither one changes the result on the reference image.
- **Distribution:** this iteration targets a sideloaded debug POC on the AAOS userdebug emulator. Production
  distribution is undecided and outside this POC's scope; Product must choose a supported route before
  production planning. An OEM-preinstall route requires OEM confirmation (V8).

## Decision

**[A / B]**, decided by [Product Lead] on [date].

## Consequences

- **A:** a plan revision for the UI layer, a new M0 baseline for templates, and V7 re-run against the templates.
- **B:** V7 stays open and blocks M0 exit until a reliable fix is found. Choosing B also means the Product Lead
  accepts an explicit scope cut: until then, the screens built don't meet docs/02 §16 for rotary.
