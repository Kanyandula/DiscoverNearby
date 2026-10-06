# E1: harness or timing? (DN-SP-003, 2026-10-06)

E1 asked what V7's Compose failure depends on: `uiautomator`, the time between launch and the first turn, or
neither. It is diagnostic: it does not close V7. The design and its reading were fixed in the DN-SP-003 ticket
before the first run. Arm E was added by the user before running.

## Conditions

- **Image:** `AAOS_AOSP_33_userdebug`, cold-booted twice (`-no-snapshot-load`). Each boot ran
  [`scripts/setup.sh`](scripts/setup.sh):
  - driver user 10;
  - location on, fixed at Greystones;
  - Park;
  - runs started only once the 1-minute load average was below 2.5.
- **Builds:** the app and the stub built from `main` (`app/` and `tools/stub-navigation/` identical to
  `origin/main` at `da8b17d`), installed with `adb install -r`.
- **Input:** `cmd car_service inject-rotary` / `inject-key` only; no touch. **This is not the hand-driven
  Extended Controls run**; that is recorded separately (see ADR-002).
- **Readings at every step:**
  - RotaryService `focusedNode` bounds from `dumpsys`;
  - the top activity;
  - a screenshot.
- **Per run:** RotaryController logcat at D level.

## Design

**The journey,** the same for every run ([`scripts/e1.sh`](scripts/e1.sh)), at `deepflow.sh`'s pauses (1 s after a
turn, 3 s after a key):
- turn, select (Recommendations);
- turn ×2, select (Details);
- turn ×2, select Navigate (then wait up to 15 s for the stub);
- Back ×3, with a turn after each.

**The arms** differ only in the wait between launch (`am start -S`) and the first turn:

| Arm | Wait | Role |
| --- | --- | --- |
| A | `sleep 14` | behavioural, no `uiautomator` |
| C | sleep for the preceding B's measured wait + 2 s | behavioural, no `uiautomator`, timing matched to B |
| B | `uiautomator` wait until "Coffee" is on screen, then sleep until 14 s after launch | `uiautomator` control |
| D | as A, plus one `uiautomator dump` once Recommendations is open | `uiautomator` control |
| E | `uiautomator` wait until "Coffee" is on screen, then 2 s (the 2026-10-05/06 failing condition) | positive control |

[`scripts/cycles.sh`](scripts/cycles.sh) interleaved the arms A B C D E, five cycles per boot.

**How each run was scored** ([`scripts/analyze.py`](scripts/analyze.py)):

| Measure | Definition |
| --- | --- |
| Complete | The stub received the URI, and Back ×3 reached Discover with the next turn moving |
| Ignored turn | The bounds are unchanged and focus is not at a list end |
| Trap | 3 or more ignored turns in a row; keys between them don't reset the count |
| Misfire | A select sent while the service holds the ComposeView host, which the driver can't see focused |
| Select went Back | The turns were stuck on the header Back, so the select activated it |

A run passes when it is complete, has no trap, has no misfire, and its select did not go Back.

## Results

[`results.txt`](results.txt) lists every run separately, and [`runs/`](runs/) has every reading as printed.

| Arm | Boot 1 | Boot 2 | Total |
| --- | --- | --- | --- |
| **A** (no `uiautomator`) | 5/5 | 5/5 | **10/10** |
| **C** (no `uiautomator`; first turn 6.2–20.9 s after launch) | 5/5 | 5/5 | **10/10** |
| B (`uiautomator` wait, 14 s start) | 1/5 | 1/5 | 2/10 |
| D (one dump mid-journey) | 4/4 | 5/5 | 9/9 |
| E (positive control) | 1/4 | 2/5 | 3/9 |

- **Every failure** was in B or E (14 of 19 runs). They show the V7 pattern:
  - the service takes the ComposeView host as its focus after navigation;
  - turns are ignored (the longest run was 6);
  - a select activates the stuck header Back, or misfires while the host holds focus (12 of the 14 failures).
- **In b1-c2-B,** the ring is drawn on Coffee while the service holds the host. This is ADR-002's screenshot 3.
- **Every passing run** shows the service holding the host after Back to Recommendations (`host=back-2`); the
  next turn then lands on the header Back.

**Reading, as fixed before the runs: inconclusive on both boots.**
- "Tool implicated" needed B **and** D to fail at least 4 of 5 per boot; D passed 9 of 9.
- "Timing implicated" needed C to fail like B; C passed 10 of 10.
- The positive control E failed on both boots (3 of 4, 3 of 5), so the boots reproduced the failure, and the
  reading isn't weak.

**Beyond that reading:**
- Timing is not the cause: C starts as early as E and never failed.
- The failure follows `uiautomator` polling while the app launches (B, E), not a single dump once the screen is
  up (D).
- The cause inside `uiautomator` was not investigated.

## V7-bar journeys

[`scripts/v7bar.sh`](scripts/v7bar.sh) ran three journeys parked and one in Drive (driving state 2), with A's
wait and no `uiautomator`. Each one:
- turns through all six tiles and holds at the end, then counter-clockwise to Family, and selects it;
- turns through every Recommendations row to the end, and opens the last row;
- turns to Navigate, back to the header Back, and to Navigate again, then selects it;
- presses Back ×3 with a turn after each.

All four gave the same readings ([`runs/v7-*.txt`](runs/), key frames in [`shots/`](shots/)). They are scored
on V7's gate as Product stated it on 2026-10-06:

| Gate condition | Result |
| --- | --- |
| Rotary reaches Navigate | ✅ 4/4: six tiles in UX order, ends hold, counter-clockwise works, every row reachable |
| Select activates the focused control | ✅ 4/4: Family opened; Navigate handed off to the stub, in Drive too |
| Back returns to a usable screen without losing a turn | ❌ 4/4: after Back to Recommendations, the service holds the host and the next turn is absorbed. It lands on the header Back, which already looked focused. |
| Every actionable control shows visible focus | ❌ Tiles show the 4 dp ring. Rows, header Back and Navigate show only a faint state-layer tint. |
| Where focus lands after Back (recorded, not gated) | Details: Navigate. Recommendations: the header Back after one turn, not the opened row. Discover: Coffee, not Family. |

## Exclusions and rulings

- **Excluded runs:**
  - `x-b1-c5-D-interrupted`: the emulator was shut down from outside the scripts at about 12:52, mid-run.
  - `x-b1-c5-E-emulator-restarted`: this run went to an instance Android Studio relaunched from a snapshot.
  - Boot 1 therefore has four D and four E runs.
- **Analysis rulings** ([`rulings.txt`](rulings.txt)):
  - A Coffee reading at launch is the previous run's stale node; no ring is drawn. So a first turn onto Coffee
    is a visible move, not an ignored turn.
  - A turn while Recommendations is still loading (only the header Back can take focus) is an end-of-list case.
- **Not kept in the repo:** most screenshots (54 MB). `shots/` keeps:
  - the first ignored turn and the misfire for every failure;
  - four frames from one passing run (`b1-c1-A`);
  - six frames each from `v7-park1` and `v7-drive1`.
- **The RotaryController logs** are in [`rotary-logs.tar.gz`](rotary-logs.tar.gz).
- **`analyze.py`** reads the scratch layout (`runs/<id>/steps.txt`); the repo keeps each run's file as
  `runs/<id>.txt`.
