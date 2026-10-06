# ADR-002: UI Stack After V7 — Compose or Car App Library

**Status:** Accepted for the emulator POC: **B, Compose** (Product Lead, 2026-10-06). V7 is open, and the
[Decision](#decision) sets its gate. If V7 still fails after one bounded Compose fix, this ADR reopens.

**Date:** 2026-10-05

**Last Updated:** 2026-10-06

**Deciders:** Product Lead · Android/Tech Lead

> **Update 2026-10-06**
> - **Decision:** Compose for the emulator POC this iteration ([Decision](#decision)). Production distribution
>   and its UI requirements stay open.
> - **E1 (DN-SP-003):** it traced the 2026-10-05 "Not workable" failure to the test harness. The failure
>   followed `uiautomator` polling while the app launched, not timing. The behavioural runs, without
>   `uiautomator`, completed 20 of 20 ([E1](#e1-harness-or-timing-dn-sp-003-2026-10-06)).
> - **V7 is still not passed.** On current `main`, two gate conditions fail:
>   - a turn is lost after Back to Recommendations;
>   - focus on rows, header Back and Navigate is only a faint tint.
>
>   One bounded Compose fix covers both (DN-M0-011), followed by a clean re-test.
> - **The Car App Library probe's entry failure is real.** It reproduced without `uiautomator`
>   ([Cold-boot comparison](#cold-boot-comparison-dn-sp-003-2026-10-06)).

---

## Context

Revision 4 replaced the Car App Library templates with a Jetpack Compose activity. That made rotary support
the app's job (UX spec §16), and the delivery plan made it a check inside M0: V7.

This ADR chooses the UI stack for the current emulator POC. It does not choose the production distribution
route. The current POC target is a sideloaded debug build on `AAOS_AOSP_33_userdebug`; production distribution
is undecided and must be resolved before production planning.

DN-M0-011's 2026-10-05 run on the reference image (`AAOS_AOSP_33_userdebug`) was recorded as
**Not workable as built** at the time. Rotary was injected with `cmd car_service inject-rotary` / `inject-key`,
not driven by hand from Extended Controls. The failures below were later traced to the test harness
([E1](#e1-harness-or-timing-dn-sp-003-2026-10-06)); they are kept as the record of that run:

- **What works.** On a freshly opened Discover grid, rotation follows the UX order (Coffee → Food → Outdoors →
  Family → Scenic → Explore), an app-drawn 4 dp ring shows focus, and select opens the category.
- **What fails.** After in-app navigation, the rotary service can be left unable to move focus. On Place
  Details, rotation was stuck and Navigate could not be reached. After Back twice, rotation stopped on Discover.
  On the workaround builds, it also stopped on Recommendations after Back.
- **Observed mechanism in those failing runs**, from the RotaryController log. E1 found the trigger:
  `uiautomator` polling while the app launches.
  - On each navigation, the ComposeView host takes View focus again, and the service makes the host its focused node.
  - From the host, the service rotates through the previous screen's virtual nodes. Compose rejects those stale ids, so every move fails.
  - Compose reports its own focused node to the service only after something has fetched that node.
  - On the shipped build, the service received focus events only for the host View, none for Compose's own
    nodes; a View-based app (Settings) sent them. So nothing told the service that its cached nodes were stale.
- **Restoring focus to the originating tile after Back made rotation stop**, so it was dropped. That run used
  the same `uiautomator`-waited harness. E1 re-tested the current behaviour (no in-app restore).
- **Four app-side workarounds were tried and time-boxed:**
  - a subtree-changed event after each destination
  - the same event on every redraw
  - a host-focus reset
  - a `<queries>` entry for accessibility services: Compose then sent its events, but rotation stayed trapped

  Each worked on some runs and trapped rotation on others. None meets the bar: the full journey and the Back
  path work by rotary, with focus visible and Navigate reachable.

The reported behaviors conflict with UX spec §16 ("The primary flow must be fully usable through rotary
input") and the GO condition "AAOS flow works" (delivery plan §7). V7 remains open; the [Decision](#decision)
sets its gate.

V8 is already confirmed and bears on the same choice, but it does not decide the POC target. Play accepts
`distractionOptimized` only on the Car App Library's `CarAppActivity`. If Product later chooses Play, the
driving UI needs a compatible template layer; a Compose driving UI would need a supported alternate route,
such as OEM preinstallation. Product has not chosen the production route.

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
- a cold-booted emulator: tested 2026-10-06 (below). The first launch after boot works; later entries still
  fail;
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
| Rotary reached Navigate, no `uiautomator` | 4 of 4 reached Navigate. Handoff was confirmed in 2 runs; the other 2 only recorded focus at Navigate. | 2 of 2 template journeys reached and activated Navigate on the first launch after each boot ([13](0002/13-cal-navigate-focus.png)); the handoff itself failed with `HostException`. |
| Full journey, with a `uiautomator` wait or dump | 0 of 3. The host takes focus after navigation; on Recommendations the second turn is ignored; select then fires the header Back ([12](0002/12-compose-uiautomator-run-back-fired.png)). In one more run a select was lost: only `ACTION_UP` was injected. | Not run |
| Entry by rotary | Every run | Boot 2: the first 4 entries; then 0 of 7, with or without `uiautomator`, and after restarting the template host process ([15](0002/15-cal-entry-two-rings.png)) |

**Reading**
- **Compose: the tool and the timing were not separated here.**
  - V7's failure reproduced only when the harness waited with `uiautomator`. Every run block in the
    DN-M0-011 plan starts with that wait (`waitfor.sh Coffee 40`).
  - Those runs also started turning sooner after launch than the clean ones.
  - E1 separated the two; see below.
- **The clean Compose runs still miss V7's existing bar:**
  - they are not four completed journeys: Navigate handoff was confirmed in only two of the four runs;
  - after Back to Recommendations, the service focuses the ComposeView host and the first turn is absorbed
    ([10](0002/10-compose-back-host-focus.png));
  - rows, header Back and Navigate show focus only as a faint tint ([8](0002/8-compose-row-focus-tint.png),
    [9](0002/9-compose-navigate-focus.png)); the 4 dp ring is on the tiles only
    ([11](0002/11-compose-discover-after-back.png));
  - Back lands on Coffee, not the originating tile. In-app restore is off by rule, and that rule came from the
    same `uiautomator`-waited harness.
  - These were not counted as passing; E1 and the [Decision](#decision) settle how they are handled.
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

### E1: harness or timing (DN-SP-003, 2026-10-06)

**Where the record is**
- The design and reading were fixed in DN-SP-003 before the first run. The record is
  [`0002/e1-2026-10-06/`](0002/e1-2026-10-06/README.md):
  - scripts;
  - every run's readings, reported separately;
  - RotaryController logs;
  - key frames.
- **The setup:** two cold boots, the app and the stub built from `main`, rotary by adb injection.
- **The arms:** they differ only in the wait between launch and the first turn. The behavioural arms use no
  `uiautomator`; the `uiautomator` arms are controls, reported separately.

| Arm | Wait before the first turn | Boot 1 | Boot 2 | Total |
| --- | --- | --- | --- | --- |
| A, behavioural | `sleep 14` | 5/5 | 5/5 | **10/10** |
| C, behavioural | short sleep matched to B's wait + 2 s (first turn 6.2–20.9 s after launch) | 5/5 | 5/5 | **10/10** |
| B, control | `uiautomator` wait until "Coffee" shows, then up to 14 s after launch | 1/5 | 1/5 | 2/10 |
| D, control | as A, plus one `uiautomator dump` once Recommendations is open | 4/4 | 5/5 | 9/9 |
| E, positive control | `uiautomator` wait + 2 s (the 2026-10-05/06 condition) | 1/4 | 2/5 | 3/9 |

**Reading**
- **As fixed before the runs: inconclusive on both boots.**
  - "Tool implicated" also needed D to fail; D passed 9 of 9.
  - "Timing implicated" needed C to fail; C passed 10 of 10.
  - The positive control E reproduced the failure on both boots.
- **Beyond that rule:**
  - timing is ruled out, because C turns as early as E and never failed;
  - the failure follows `uiautomator` polling while the app launches (B, E: 14 of 19 failed), not one dump once
    the screen is up (D).
- **Every failure shows V7's pattern:**
  - the service holds the ComposeView host;
  - turns are ignored, up to 6 in a row;
  - then either a select misfires on the host (12 runs) or the stuck header Back is activated (2 runs).
- **The cause inside `uiautomator` was not investigated.**

**V7-bar journeys** (3 parked, 1 in Drive; no `uiautomator`; all four identical), scored on the gate in the
[Decision](#decision):

| Gate condition | Result |
| --- | --- |
| Rotary reaches Navigate | ✅ 4/4: six tiles in UX order, ends hold, counter-clockwise works, every row reachable |
| Select activates the focused control | ✅ 4/4: the category opens; Navigate hands off to the stub, in Drive too |
| Back returns to a usable screen without losing a turn | ❌ 4/4: after Back to Recommendations, the service holds the host and the next turn is absorbed (it lands on the header Back, which already looked focused) |
| Every actionable control shows visible focus | ❌ The tiles show the 4 dp ring; rows, header Back and Navigate show only a faint state-layer tint |
| Where focus lands after Back (recorded, not gated) | Details: Navigate. Recommendations: the header Back after one turn, not the opened row. Discover: Coffee, not the opened Family. |

**Exclusions:** two runs were cut off when the emulator was shut down and relaunched from outside the scripts
(Boot 1, cycle 5, arms D and E).

**Limitation:** all E1 input was adb injection.

### Manual Extended Controls run

The hand-driven journey (Extended Controls → Car rotary, no touch) is recorded here, separately from the
adb-driven runs above.

- **Status:** pending; the user runs it.

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

- **Gap:** V7 is open. E1's clean journeys fail two gate conditions: a turn is lost after Back to
  Recommendations, and focus on rows, header Back and Navigate is only a faint tint.
- **What it needs:** one bounded Compose fix for those two conditions, then a clean re-test (see Decision).
- **Diagnostic only:** a newer AAOS image or another Compose version can show where the fault lies. Neither one changes the result on the reference image.
- **Distribution:** this iteration targets a sideloaded debug POC on the AAOS userdebug emulator. Production
  distribution is undecided and outside this POC's scope; Product must choose a supported route before
  production planning. An OEM-preinstall route requires OEM confirmation (V8).

## Decision

**B, keep Compose, for the emulator POC this iteration.** Decided by the Product Lead on 2026-10-06.

- **Scope.**
  - The decision covers the emulator POC only. Production distribution and its UI requirements remain open.
  - If Product later chooses Google Play for this POI app, the stack decision is revisited then (V8).
- **Rotary scope.** Controller rotation on the current Android 13 image (`AAOS_AOSP_33_userdebug`). Nudging is
  not a POC requirement.
- **The V7 gate.** The existing bar is kept. As Product stated it, V7 passes only when:
  - rotary navigation reaches Navigate;
  - selection activates the focused control;
  - Back returns to a usable screen without losing a turn;
  - every actionable control shows visible focus.

  Where focus lands after Back (DN-M0-011's "originating tile") is re-tested and recorded, not gated (Product,
  2026-10-06).
- **Evidence rules.**
  - E1's behavioural runs are free of `uiautomator`; the `uiautomator` control is reported separately.
  - The manual Extended Controls run is recorded separately from the adb-driven E1 results.
- **If E1 exposes a reproducible input trap:**
  - one bounded Compose fix is allowed, then a re-test;
  - if V7 still fails, record it as failed and reopen this ADR;
  - V7 is never marked complete, and the stack never switches to the Car App Library, on assumption.

**After E1.** The clean journeys fail two gate conditions (E1 above). Product scoped the one bounded fix to
cover both:
- the turn lost after Back to Recommendations;
- visible focus on rows, header Back and Navigate.

The fix is done under DN-M0-011, followed by the clean V7-bar re-test.

## Consequences

- **M0 exit waits on V7:**
  - DN-M0-011 delivers the one bounded fix, then the clean re-test, with the `uiautomator` control reported
    separately;
  - the manual Extended Controls run is recorded on its own.
- **If V7 passes:**
  - M0 can exit;
  - `tools/cal-rotary-probe/` can be deleted (DN-SP-002).
- **If V7 still fails:** V7 is recorded as failed, and this ADR reopens with Option A's evidence as it stands.
  The probe stays until then.
- **DN-UX-001 is unblocked.** It was waiting on this stack decision.
