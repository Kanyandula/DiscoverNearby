# Cold-boot comparison, 2026-10-05/06 (DN-SP-003)

The raw record behind ADR-002's "Cold-boot comparison" evidence. Runs took place 2026-10-05 23:20 to
2026-10-06 00:05 (Irish time) on `AAOS_AOSP_33_userdebug`.

## Conditions

- **Image:** `AAOS_AOSP_33_userdebug`, cold-booted twice (`emulator -avd AAOS_AOSP_33_userdebug -port 5554
  -no-snapshot-load`; the old instance was stopped with `adb emu kill`). After each boot:
  - driver user 10;
  - RotaryService `focusedNode=null`, `inRotaryMode=false`;
  - location on for user 10, fixed at Greystones (docs/04 Location A);
  - Park.
- **Discover Nearby:** the debug build installed 2026-10-05 18:31, after `ec280a1`. Every later `app/` commit
  changes only tests and comments, so the build behaves as `main`.
- **Stub navigation app:** installed.
- **Probe:** `tools/cal-rotary-probe` (Car App Library 1.7.0), template host 1.007.
- **Input:** `cmd car_service inject-rotary -c true|false` (turn) and `inject-key 23|4` (select, Back). No touch.
  This is not a hand-driven Extended Controls run.
- **State readings:**
  - Focus comes from `rfocus.sh`: the bounds of RotaryService's `focusedNode` in `dumpsys`. It prints a Compose
    tile's name when the bounds match one (Coffee is `Rect(48, 156 - 344, 380)`). The same name can therefore
    appear while the probe is on screen; that means the service still holds a stale Compose node.
  - The top activity comes from `dumpsys activity activities`.
  - Screenshots come from `screencap`.
- **Scripts:** in [`harness-2026-10-06/`](harness-2026-10-06/), exactly as run. Two of them changed during the
  runs:
  - **`step.sh`:** runs c1-00 to c1-03 used an earlier version that also printed `texts.sh` output, which calls
    `uiautomator dump`, after every step. `texts.sh` was removed from c1-10 on.
  - **`deepflow-nowait.sh`:** `deepflow.sh` with its `uiautomator` wait replaced by `sleep 14`.

**Not retained:**
- **RotaryController logcat files.** Logcat was cleared between runs and never saved to files. Only the excerpts
  quoted below exist.
- **Screenshots** were taken for every step but kept only in a temporary directory. Shots 8–15 in this folder
  are the ones copied into the repo.
- **Per-step readings** below are transcribed from the command output as printed, with notes in brackets.

## Boot 1: the probe first, then Compose

**Probe, first launch after boot.**
- `cal-journey.sh` started first. Rotary entered the probe and the grid turns moved from Coffee towards Family.
  The script then lost sync: selects were sent before the previous screen had rendered. Its output was not kept.
- The run continued step by step:

```text
ccw 1: Rect(136, 394 - 324, 600)
ccw 2: Rect(700, 188 - 888, 394)          [Family]
after select: Rect(700, 188 - 888, 394)   [the click registered; the list rendered ~10 s later]
list turn 1: Rect(136, 394 - 324, 600)    [Scenic, still on the grid; its select pushed a second list]
after select row: Rect(112, 172 - 912, 288)
details turn 1: Rect(112, 288 - 912, 404)
details turn 2: Rect(112, 404 - 912, 520)
details turn 3: Rect(112, 520 - 912, 636)
after select row 4: Rect(112, 520 - 912, 636)   [ACTION_CLICK at 23:28:34; Details focused at 23:28:44]
focus: Rect(24, 580 - 1000, 656)                  [Navigate]
ProbeNav: 23:29:10.670 navigate selected
ProbeNav: 23:29:10.721 startCarApp failed: HostException
back 1: Rect(24, 580 - 1000, 656) | calprobe
back 2: Rect(112, 172 - 912, 288) | calprobe     [the Family list: the extra screen, shot 14]
back 3: Rect(112, 172 - 912, 288) | calprobe
+10 s:  Rect(136, 188 - 324, 394)                 [the grid, Coffee]
```

- **Select → render latency, measured once:** the list appeared 12–14 s after the select.
- **The emulator was slow:**
  - the template host logged "Skipped 47 frames" / "Skipped 50 frames";
  - `system_server` logged "Slow delivery took 952ms" and "… 1344ms".
- **Back on the root grid** left the probe on screen for 15 s; it did not leave the app.

**Compose run 1** (`deepflow.sh`, with its `uiautomator` wait; the probe was still in the back stack):

```text
discover: Coffee
recs open: Rect(0, 76 - 1024, 672)      [the ComposeView host]
recs 1: Rect(0, 76 - 1024, 672)
recs 2: Rect(0, 76 - 1024, 672)
details open: Rect(0, 76 - 1024, 672)
details 1: Coffee                       [stale Discover nodes]
details 2: Food
back to recs: Food
recs turn: Rect(136, 188 - 324, 394)    [Back left the app; the probe's grid]
back to discover: Rect(136, 188 - 324, 394)
discover 1: Rect(324, 188 - 512, 394)
discover 2: Rect(512, 188 - 700, 394)
events: 19
```

**Compose run 2** (`step.sh` with `texts.sh`, i.e. a `uiautomator dump` after every step; 3–8 s waits):

```text
c1-00: none | discovernearby
c1-01: Coffee | discovernearby
c1-02: Coffee | discovernearby     [select: Recommendations did not open]
c1-03: none | discovernearby
```

RotaryController logged "Clear focus then focus on the node again", then injected only `ACTION_UP` for
`KEYCODE_DPAD_CENTER`, with no `ACTION_DOWN`. The select was lost.

**Compose run 3** (`step.sh` without `uiautomator`; 3–8 s waits). This time the service injected both
`ACTION_DOWN` and `ACTION_UP`.

```text
c1-10: none
c1-11: Coffee
c1-12: Rect(48, 156 - 124, 232)    [Recommendations, header Back]
c1-13: Rect(48, 246 - 976, 382)    [row 1]
c1-14: Rect(48, 396 - 976, 532)    [row 2]
c1-15: Rect(48, 546 - 976, 624)    [row 3]
c1-16: Rect(48, 156 - 124, 232)    [Details, header Back]
c1-17: Rect(576, 536 - 976, 624)   [Navigate]
c1-18: Rect(576, 536 - 976, 624)
c1-19: Rect(576, 536 - 976, 624)
c1-20: Rect(576, 536 - 976, 624) | stubnavigation    StubNav received: 1
c1-21: Rect(576, 536 - 976, 624)   [Back from the stub]
c1-22: Rect(576, 536 - 976, 624)
c1-23: Rect(0, 76 - 1024, 672)     [Back → Recommendations: the host]
c1-24: Rect(48, 156 - 124, 224)
c1-25: Rect(48, 224 - 976, 360)
c1-26: Coffee                      [Back → Discover]
c1-27: Food
c1-28: Outdoors
```

## Boot 2: Compose first, then the probe

**Compose** (`step.sh` without `uiautomator`):

```text
c2-00: none
c2-01: Coffee
c2-02: Rect(48, 156 - 124, 232)
c2-03: Rect(48, 246 - 976, 382)
c2-04: Rect(48, 396 - 976, 532)            [shot 8]
c2-05: Rect(48, 156 - 124, 232)
c2-06: Rect(576, 536 - 976, 624)           [shot 9]
c2-07: Rect(576, 536 - 976, 624) | stubnavigation    StubNav received: 1
c2-08: Rect(576, 536 - 976, 624)
c2-09: Rect(0, 76 - 1024, 672)             [Back → Recommendations: the host; shot 10]
c2-10: Rect(48, 156 - 124, 232)
c2-11: Rect(48, 246 - 976, 382)
c2-12: Coffee                              [Back → Discover]
c2-13: Food
c2-14: Outdoors                            [shot 11]
```

**Probe, first launch on this boot** (`step.sh`, 15 s after each select and Back):

```text
p2-00: Outdoors                     [stale Compose node]
p2-01: Rect(136, 188 - 324, 394)    [entered: Coffee]
p2-02: Rect(324, 188 - 512, 394)
p2-03: Rect(112, 172 - 912, 288)
p2-04: Rect(112, 288 - 912, 404)
p2-05: Rect(112, 404 - 912, 520)
p2-06: Rect(24, 580 - 1000, 656)    [Navigate]
p2-07: Rect(24, 580 - 1000, 656)
p2-08: Rect(24, 580 - 1000, 656)    ProbeNav 23:44:50 navigate selected; startCarApp failed: HostException
p2-09: Rect(112, 172 - 912, 288)
p2-10: Rect(136, 188 - 324, 394)
p2-11: Rect(324, 188 - 512, 394)
p2-12: Rect(324, 188 - 512, 394)    [Back on the root grid: still the probe]
p2-13: Rect(512, 188 - 700, 394)
```

**Probe entries, in order.** Each entry is one launch, 25 s of waiting, then one turn. ✓ means focus entered
the probe; ✗ means it did not.

| Entry | How | First turn |
| --- | --- | --- |
| p2 | first launch (above) | ✓ `Rect(136, 188 - 324, 394)` |
| A | force-stop the probe, start it | ✓ `Rect(136, 188 - 324, 394)` |
| B | force-stop, one `uiautomator dump`, start | ✓ `Rect(136, 188 - 324, 394)` |
| C1 | `sp002-entry.sh` (DN-SP-002's entry test: Compose with a `uiautomator` wait, one turn there, then the probe) | ✓ `Rect(136, 188 - 324, 394)` |
| C2 | the same | ✗ `none` |
| C3 | the same (uptime 11 min) | ✗ `Rect(12, 9 - 94, 68)`, the status bar |
| H1–H3 | `sp002-entry-nowait.sh` (the same, with `sleep 14` instead of `uiautomator`) | ✗ `Rect(17, 682 - 93, 758)`, `Rect(12, 9 - 94, 68)`, `Rect(192, 682 - 268, 758)` |
| A2–A3 | force-stop the probe, start it | ✗ `Rect(192, 682 - 268, 758)` both |
| I1–I2 | force-stop the probe and the template host (`templates.host:render`), start | ✗ `Rect(192, 682 - 268, 758)` both; shot 15 |

The Compose controls D–G (below) ran between C3 and H1.

**C3's RotaryController log, trimmed:**
- At 23:51:11.423, the turn first set `mFocusedNode` to the probe's Coffee tile (`TYPE_VIEW_FOCUSED` from
  `templates.host`).
- Within 300 ms focus moved to `Rect(17, 682 - 93, 758)` (the bottom bar), then to `Rect(12, 9 - 94, 68)` (the
  status bar).
- Shot 15 shows the result: the host's ring on Coffee and the rotary ring on a bottom-bar button.

**Compose controls** (`deepflow.sh` pauses: 1 s after a turn, 3 s after a key). D and F wait with `uiautomator`
until "Coffee" is on screen, plus 2 s. E and G use `sleep 14` instead. The wait lengths were not matched: D and F
started turning sooner after launch than E and G.

```text
D (uiautomator wait)                     E (sleep 14)
discover: Coffee                         discover: Food
recs open: Rect(0, 76 - 1024, 672)       recs open: Rect(48, 156 - 124, 232)
recs 1: Rect(48, 156 - 124, 232)         recs 1: Rect(48, 246 - 976, 382)
recs 2: Rect(48, 156 - 124, 232)  [stuck] recs 2: Rect(48, 396 - 976, 532)
details open: Rect(0, 76 - 1024, 672)    details open: Rect(48, 156 - 124, 232)
  [select fired header Back → Discover, shot 12]
details 1: Rect(0, 76 - 1024, 672)       details 1: Rect(576, 536 - 976, 624)  [Navigate]
details 2: Rect(0, 76 - 1024, 672)       details 2: Rect(576, 536 - 976, 624)
back to recs: Rect(0, 76 - 1024, 672)    back to recs: Rect(0, 76 - 1024, 672)
recs turn: Rect(142, 537 - 246, 641)     recs turn: Rect(48, 156 - 124, 232)
  [Back left the app: the home screen]
back to discover: Rect(142, 537 - 246, 641)   back to discover: Coffee
discover 1: Rect(142, 537 - 246, 641)    discover 1: Food
discover 2: Rect(142, 537 - 246, 641)    discover 2: Outdoors
events: 20                               events: 13

F (uiautomator wait)                     G (sleep 14)
discover: Coffee                         discover: Food
recs open: Rect(0, 76 - 1024, 672)       recs open: Rect(48, 156 - 124, 232)
recs 1: Rect(48, 156 - 124, 232)         recs 1: Rect(48, 156 - 124, 232)  [one turn absorbed]
recs 2: Rect(48, 156 - 124, 232)  [stuck] recs 2: Rect(48, 246 - 976, 382)
details open: Rect(0, 76 - 1024, 672)    details open: Rect(48, 156 - 124, 232)
details 1: Rect(0, 76 - 1024, 672)       details 1: Rect(576, 536 - 976, 624)  [Navigate]
details 2: Rect(0, 76 - 1024, 672)       details 2: Rect(576, 536 - 976, 624)
back to recs: Rect(-831, 537 - -727, 641)     back to recs: Rect(0, 76 - 1024, 672)
recs turn: Rect(142, 537 - 246, 641)     recs turn: Rect(48, 156 - 124, 232)
back to discover: Rect(142, 537 - 246, 641)   back to discover: Coffee
discover 1: Rect(142, 537 - 246, 641)    discover 1: Food
discover 2: Rect(142, 537 - 246, 641)    discover 2: Outdoors
events: 15                               events: 12
```

`deepflow.sh` focuses Navigate but does not select it. In E and G, Navigate was reached but not selected.

## Tally

| | With a `uiautomator` wait or dump | Without |
| --- | --- | --- |
| Compose journey | 0 of 3 completed (Boot 1 run 1, D, F), plus Boot 1 run 2 lost a select | 4 of 4 completed (Boot 1 run 3, Boot 2, E, G). Navigate was selected in 2 runs and focused in the other 2 |
| Probe entry (Boot 2) | C1 ✓, C2 ✗, C3 ✗; B ✓ | p2 ✓, A ✓, then H1–H3, A2–A3, I1–I2 ✗ |

- **Probe full journey:** 2 of 2, both on the first launch after a cold boot.
- **Probe entry:** the first four entries of Boot 2 worked; all seven after them failed, with or without
  `uiautomator`.
