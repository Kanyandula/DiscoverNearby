# DN-UX-008 Launcher Icon — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> This project runs plans natively (inline), task by task.

**Goal:** The Product Lead's "1 · Compass pin" as the app's adaptive launcher icon, replacing Android Studio's
placeholder robot, with an SVG master in `docs/design/`.

**Architecture:** Resources only. Three vector-drawable layers on the 108 × 108 dp canvas (background, foreground,
monochrome) and an adaptive icon for `ic_launcher` and `ic_launcher_round`. The mark keeps the canvas coordinates and
grows 8% through a group scale about the centre. No legacy bitmaps: minSdk 29 always uses the adaptive icon.

**Tech Stack:** Android vector drawables, adaptive icons (API 26+; themed icons API 33+), SVG.

**Spec:** the vault ticket DN-UX-008 (`tickets/DN-UX-008-launcher-icon.md`) and the canvas artboard "App icon —
ChatGPT's six, redrawn", card 1 (https://claude.ai/artifact/3v68QUCWeu87t7n2GFf8nf).

## Global Constraints

- Worktree `.claude/worktrees/dn-ux-008-launcher-icon`, branch `dn-ux-008-launcher-icon`; never commit on `main`; no
  AI attribution in commits or the PR. Commit subject ≤ 72 characters (50 preferred), body wrapped at 72.
- Colours exactly: background `#2563EB` (ui/theme `Action`); pin and needle south `#FFFFFF`; disc and pivot
  `#1A47B8`; needle north `#FF7A59`.
- Geometry (108 canvas, before the 1.08 scale about 54,54): pin head centre (54,45) radius 20.5 (the 19 dp head with
  its 1.5 dp rounding), tip rounded on radius 1.5 at (54,80); disc radius 13.5 at (54,45); needle N (62.13,36.87),
  S (45.87,53.13), sides (57.25,48.25) and (50.75,41.75); pivot radius 2.2.
- Google's rules: layers 108 × 108 dp; the mark inside the central 66 dp circle (scaled, its farthest point is 31.9
  dp from the centre); the logo 48–66 dp tall (61.6); only the background in the outer 18 dp; no drawn shadow or mask.
- The monochrome layer is shape only (the system tints it): the disc and the pivot are holes (`evenOdd`), not blue.
- Emulator: `adb -s emulator-5554` only; DN-M4-001 validates on it, so ask the user before installing.

## Review Focus

- Masks: circle (the AOSP default), squircle, rounded square, teardrop — nothing of the mark clipped.
- 48 px in the app grid: the needle still reads (both halves), the ring stays white.
- Themed icon (API 33+): the monochrome layer shows the pin, the needle and the holes, not a filled blob.
- Round icon: `android:roundIcon` resolves to the same adaptive icon.
- No other module or code references the removed `.webp` mipmaps or the robot drawables.

---

### Task 1: The icon

**Files:**
- Create: `docs/design/app-icon.svg`, `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`,
  `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
- Delete: `app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.webp`

- [ ] **Step 1:** Write the SVG master: background rect, then the mark in `<g transform="translate(54 54) scale(1.08)
  translate(-54 -54)">`: the pin outline (no stroke), the disc, the needle halves, the pivot.
- [ ] **Step 2:** Background layer: one 108 × 108 path in `#2563EB`.
- [ ] **Step 3:** Foreground layer: `<group pivotX="54" pivotY="54" scaleX="1.08" scaleY="1.08">` with the same four
  shapes as the master.
- [ ] **Step 4:** Monochrome layer: the same group; the pin with the disc as an `evenOdd` hole; the needle (both
  halves as one rhombus) with the pivot as an `evenOdd` hole; white fill.
- [ ] **Step 5:** `ic_launcher` and `ic_launcher_round`: `<adaptive-icon>` with background, foreground, monochrome.
- [ ] **Step 6:** Delete the ten `.webp` files; `grep -rn "ic_launcher" app tools` shows only the manifest and the
  mipmap XML.
- [ ] **Step 7:** `./gradlew assembleDebug lintDebug` passes (aapt validates the vectors; lint checks the icon).
- [ ] **Step 8:** Commit — `Add the Compass pin launcher icon`.

No unit test: resources only, and aapt plus lint validate them. The emulator check is the visual test.

### Task 2: Docs

**Files:** `docs/design/README.md`, `docs/02-discover-nearby-ux-interaction-spec.md` (§22), `CLAUDE.md`

- [ ] **Step 1:** Design README: a "Launcher icon (DN-UX-008)" paragraph (the Compass pin, `app-icon.svg` the master,
  built as adaptive layers; the app icon in `00-icons.png` is the earlier design) and a table row for `app-icon.svg`.
- [ ] **Step 2:** docs/02 §22: the icon set line notes the launcher icon is now the Compass pin (`app-icon.svg`).
- [ ] **Step 3:** CLAUDE.md: a "Launcher icon (DN-UX-008)" bullet.
- [ ] **Step 4:** Commit — `Record the Compass pin launcher icon in the docs`.

### Task 3: Checks and the emulator

- [ ] **Step 1:** `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` passes.
- [ ] **Step 2 (after the user's OK):** install on `emulator-5554`; screenshot the app grid; check the circle mask,
  48 px, and that the app still launches. Restore what DN-M4-001 had installed if the user asks.

### Task 4: Review and PR (ticket workflow steps 4–5)

- [ ] `simplify` on the branch diff; one review of the resources and docs; push; the PR with `pr-description`
  (ticket ID and acceptance criteria); update the ticket and NOW.md.
