# V7 re-test after the bounded fix (DN-M0-011, 2026-10-06)

**Result: V7 failed.** Every behavioural run passed three of the four gate conditions. The fourth, "Back without
losing a turn", failed in one of the four runs: park1, on Back to Discover. Under the rule fixed before the run,
V7 passes only if all four behavioural runs meet all four conditions. So V7 is recorded as failed, and ADR-002
reopens (Product, 2026-10-06).

## What the fix changed

The one bounded Compose fix (plan: `docs/superpowers/plans/2026-10-06-dn-m0-011-bounded-v7-fix.md`):

- **`focusRing`:**
  - a 4 dp ring on every actionable control: tiles, Recommendations rows, header Back, Navigate, and the
    message buttons;
  - `Accent` on dark surfaces, light (`OnSurface`) on the `Action`-blue Navigate and Retry/Grant.
- **`ReturnFocus`:**
  - remembers the item selected by rotary (one that had focus when selected);
  - after Back, requests focus on it again once 250 ms have passed.

**The wait changed during development, before the re-test.**
- The plan specified one frame.
- In developer check-1 (warm emulator, one-frame wait), Back to Recommendations worked. On Back to Discover,
  though, the restored Family was never reported to the rotary service, and the next turn jumped to Coffee.
- **The working hypothesis:**
  - Compose reports a focus change to accessibility only for a node already in its last semantics snapshot
    (`previousSemanticsNodes[id] ?: return@forEachKey` in `AndroidComposeViewAccessibilityDelegateCompat`,
    Compose UI 1.12.1);
  - that snapshot refreshes at most every 100 ms;
  - so a focus change one frame after the screen returns can come too early.
- **The change:** the wait became 250 ms. Check-2 then passed.
- **How it was ruled:** a parameter of the same fix, not a second mechanism (DN-M0-011 ledger). Whether Product
  agrees is Product's call.
- **park1 contradicts the hypothesis:** it failed with the 250 ms wait.

## Conditions

- **Image:** `AAOS_AOSP_33_userdebug`, cold-booted (`-no-snapshot-load`), set up with
  [`scripts/setup.sh`](scripts/setup.sh): user 10, location at Greystones, Park, load average below 2.5.
- **Build:** this branch, at `246b662`, installed at 16:12.
- **Input:** adb injection (`cmd car_service inject-rotary` / `inject-key`), no touch, no `uiautomator` in the
  behavioural runs.
- **Readings:**
  - RotaryService `focusedNode` from `dumpsys`, 3 s after each key;
  - the top activity;
  - screenshots;
  - the RotaryController log at V level.
- **The journey,** [`scripts/v7gate.sh`](scripts/v7gate.sh):
  - Discover: cw ×7, ccw ×2 → Family, select;
  - Recommendations: cw ×6, ccw ×2 → row 2 (Adventure Playground), select;
  - Details: cw, cw, ccw, cw → Navigate, select, then the stub;
  - Back → Details, ccw; Back → Recommendations, cw; Back → Discover, cw.

## Gate scores

Behavioural runs, from [`scripts/score.py`](scripts/score.py). Run it with the logs extracted from
[`rotary-logs.tar.gz`](rotary-logs.tar.gz):

| Run | Navigate reached | Selection activates | Back 1 / 2 / 3: in step | Back 1 / 2 / 3: lands right |
| --- | --- | --- | --- | --- |
| park1 | ✅ | ✅ | ✅ / ✅ / ❌ | ✅ / ✅ / ❌ |
| park2 | ✅ | ✅ | ✅ / ✅ / ✅ | ✅ / ✅ / ✅ |
| park3 | ✅ | ✅ | ✅ / ✅ / ✅ | ✅ / ✅ / ✅ |
| drive1 (driving state 2) | ✅ | ✅ | ✅ / ✅ / ✅ | ✅ / ✅ / ✅ |

- **"In step":** from the log, the last node the rotary service took after each Back, and before the next turn, is
  an element of the screen shown (not the host or a stale node).
- **"Lands right":** the turn after Back lands where the drawn ring implies (header Back; the row below the opened
  row; Scenic after Family).
- **Why "in step" uses the log:** an earlier version scored it from the `dumpsys` reading 3 s after Back. Focus
  events reached the service 2–4 s after Back on this emulator, so that reading raced them. It wrongly failed
  park2 and passed park3's Back 2 on a stale node. The final review found this.
- **park1, Back to Discover** ([shots](shots/)):
  - the ring is drawn on Family (restored);
  - the log shows only the ComposeView host's focus event: no event for Family, and none for Compose's own initial
    Coffee focus, which the passing runs report first;
  - the turn went from the host to the first tile, Coffee, so the driver sees the ring jump from Family back to
    Coffee.

**Visible focus.** Checked by exact pixel colour at each focused control's left edge, in the frames kept:

| Control | park1 | drive1 | error1 |
| --- | --- | --- | --- |
| Category tile | `#4C8DF6` (Accent) | `#4C8DF6` | — |
| Recommendations row | `#4C8DF6` | `#4C8DF6` | — |
| Header Back | `#4C8DF6` | `#4C8DF6` | — |
| Navigate | `#EEF1F3` (OnSurface) | `#EEF1F3` | — |
| Try Again (network error) | — | — | `#EEF1F3` |
| Back (network error) | — | — | `#4C8DF6` |

park2's and park3's frames were not kept, so their visible focus is not separately evidenced. The ring is drawn by
the same code on every run, and the Robolectric pixel tests cover every control.

**Where focus lands after Back** (recorded, not gated):
- **Details:** Navigate.
- **Recommendations:** the opened row (Adventure Playground), followed by the service in every run.
- **Discover:** Family, followed by the service in three of four runs; park1 is the exception.
- **A short jump:** in the passing runs, the service took Compose's own Coffee first, then Family 0.10–0.46 s
  later. A turn inside that window would be overridden by the restore.

## Reported separately

**`uiautomator` control** (control1: `uiautomator` wait for "Coffee", then 2 s): in step and landed right after
every Back.

**Developer checks before the cold boot:**
- check-1 (one-frame wait) failed Back to Discover as park1 did;
- check-2 (250 ms) passed everything.

**Manual Extended Controls run:** not done. It stays its own section in ADR-002.

## What this shows

- **Fixed in every behavioural run:**
  - E1's lost turn after Back to Recommendations (4 of 4);
  - visible focus on every control.
- **The remaining failure is on Back to Discover, in park1.** There, Compose reported no tile focus to the rotary
  service at all.
- **The evidence does not show that the Discover restore caused it.** Compose's own Coffee focus went unreported
  in park1 too.
- **It doesn't show that removing the restore would bring E1's behaviour back either.** (In E1, without the
  restore, Coffee was reported after Back to Discover in 4 of 4 V7-bar runs.)
- **Not tried, by rule:** any further change, such as no restore on Discover, another wait, or guarding the
  restore. That is the Product Lead's call.

## Record

- `runs/<id>/steps.txt`: every reading as printed.
- [`rotary-logs.tar.gz`](rotary-logs.tar.gz): the V-level RotaryController log of every run.
- [`shots/`](shots/): key frames for park1, drive1 and control1, plus the error-state frames. The full screenshot
  sets are not kept, as for E1.
- The check-1 and check-2 frames were deleted before their key frames were copied; their readings and logs are
  kept.
