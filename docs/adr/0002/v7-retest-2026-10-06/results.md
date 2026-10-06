# V7 re-test after the bounded fix (DN-M0-011, 2026-10-06)

**Result: V7 failed.** The re-test passed three of the four gate conditions in every behavioural run. The fourth,
"Back without losing a turn", failed in one of the four runs (park1). Under the rule fixed before the run, V7
passes only if all four behavioural runs meet all four conditions. So V7 is recorded as failed, and ADR-002
reopens (Product, 2026-10-06).

## What the fix changed

The one bounded Compose fix (plan: `docs/superpowers/plans/2026-10-06-dn-m0-011-bounded-v7-fix.md`):

- **`focusRing`:**
  - a 4 dp ring on every actionable control: tiles, Recommendations rows, header Back, Navigate, and the
    message buttons;
  - `Accent` on dark surfaces, light (`OnSurface`) on the `Action`-blue Navigate and Retry/Grant.
- **`ReturnFocus`:**
  - remembers the item selected by rotary (one that had focus when selected);
  - after Back, requests focus on it again once 250 ms have passed;
  - the wait is because Compose reports a focus change to accessibility only for a node already in its last
    semantics snapshot (`AndroidComposeViewAccessibilityDelegateCompat`, Compose UI 1.12.1);
  - the developer check with a one-frame wait (`check-1`) showed the Discover case was otherwise never
    reported.

## Conditions

- **Image:** `AAOS_AOSP_33_userdebug`, cold-booted (`-no-snapshot-load`), set up with
  [`scripts/setup.sh`](scripts/setup.sh): user 10, location at Greystones, Park, load average below 2.5.
- **Build:** this branch, at `246b662`, installed at 16:12.
- **Input:** adb injection (`cmd car_service inject-rotary` / `inject-key`), no touch, no `uiautomator` in the
  behavioural runs.
- **Readings:** RotaryService `focusedNode` from `dumpsys`, the top activity, and screenshots.
- **The journey,** [`scripts/v7gate.sh`](scripts/v7gate.sh):
  - Discover: cw ×7, ccw ×2 → Family, select;
  - Recommendations: cw ×6, ccw ×2 → row 2 (Adventure Playground), select;
  - Details: cw, cw, ccw, cw → Navigate, select, then the stub;
  - Back → Details, ccw; Back → Recommendations, cw; Back → Discover, cw.

## Gate scores

Behavioural runs, from [`scripts/score.py`](scripts/score.py) and `runs/<id>/steps.txt`:

| Run | Navigate reached | Selection activates | Back 1 / 2 / 3: in step | Back 1 / 2 / 3: lands right |
| --- | --- | --- | --- | --- |
| park1 | ✅ | ✅ | ✅ / ✅ / ❌ | ✅ / ✅ / ❌ |
| park2 | ✅ | ✅ | ✅ / ✅ / ❌ | ✅ / ✅ / ✅ |
| park3 | ✅ | ✅ | ✅ / ✅ / ✅ | ✅ / ✅ / ✅ |
| drive1 (driving state 2) | ✅ | ✅ | ✅ / ✅ / ✅ | ✅ / ✅ / ✅ |

- **"In step":** after Back, the service holds an element of the screen shown.
- **"Lands right":** the turn after Back lands where the drawn ring implies (header Back; the row below the opened
  row; Scenic after Family).
- **park2, Back 3:** the service held a stale Recommendations node, but its turn still landed on Scenic.
- **park1, Back 3:** the failure ([shots](shots/)):
  - after Back, the ring is drawn on Family (restored), but Compose reported only the ComposeView host's focus;
  - the clockwise turn then went from the host to the first tile, Coffee;
  - so the driver sees the ring jump from Family back to Coffee.

**Visible focus.** Each focused control's ring was checked by exact pixel colour at its left edge.

| Control | Frame | Ring |
| --- | --- | --- |
| Category tile | park1 `discover-cw4` | `#4C8DF6` (Accent) |
| Recommendations row | park1 `recs-cw2` | `#4C8DF6` |
| Header Back | park1 `details-ccw1` | `#4C8DF6` |
| Navigate | park1 `details-cw1` | `#EEF1F3` (OnSurface) |
| Try Again (network error) | `error1/01-turn` | `#EEF1F3` |
| Back (network error) | `error1/02-turn` | `#4C8DF6` |

**Where focus lands after Back** (recorded, not gated):
- **Details:** Navigate.
- **Recommendations:** the opened row (Adventure Playground), in 4 of 4 runs.
- **Discover:** Family was focused in Compose. That is seen in park1's and drive1's kept frames, and implied for park2
  and park3 by where their turns landed. The rotary service followed it in only 2 runs (park3, drive1).

## Reported separately

**`uiautomator` control** (control1: `uiautomator` wait for "Coffee", then 2 s):
- the turn after each Back landed right, including Scenic after Family;
- after Back to Discover the service held a stale node, as in park2.

**Developer checks before the cold boot** (check-1 with a one-frame wait, check-2 with 250 ms):
- check-1 had the same Back-to-Discover failure as park1, which led to the 250 ms wait;
- check-2 passed everything.

**Manual Extended Controls run:** not done. It stays its own section in ADR-002.

## What this shows

- **Fixed** in every behavioural run:
  - the original lost turn after Back to Recommendations (4 of 4);
  - visible focus on every control.
- **The remaining failure is on Back to Discover,** where the restore is not always reported. Restoring there was
  part of the fix (`ReturnFocus` on both screens); the gate itself doesn't require it, since where focus lands
  after Back is recorded only. In E1, without the restore, Back to Discover kept Compose's own focus on Coffee and
  reported it.
- **Not tried, by rule:** removing the Discover restore, or a longer wait, would be a second attempt. That is the
  Product Lead's call.

## Record

- `runs/<id>/steps.txt`: every reading as printed.
- [`rotary-logs.tar.gz`](rotary-logs.tar.gz): the V-level RotaryController log of every run.
- [`shots/`](shots/): key frames for park1, drive1 and control1, plus the error-state frames. The full screenshot
  sets are not kept, as for E1.
- The check-1 and check-2 frames were deleted before their key frames were copied; their readings and logs are
  kept.
