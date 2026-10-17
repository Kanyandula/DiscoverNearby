# DN-UX-011 Nav Rail Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> This project runs plans natively (inline), task by task.

**Goal:** The app rail as the Product Lead's approved artboards R1 and R2: a rounded rail panel with labelled tabs
(Discover, Saved, Map; a divider; Profile at the bottom), Map and Profile opening "Coming soon", and "Discover
Nearby" aligned with the rail.

**Architecture:** `RailTab` gains Map and Profile, each tab's route, whether its icon is tinted, and whether it sits at
the bottom; `selectedTab` and `openTab` work from the routes, so every tab keeps DN-UX-006's rules (single top on
Discover, the tab follows the back stack). `AppRail` draws the panel and the labelled tabs; a tab keeps one
accessibility name (its label) and the visible label adds nothing to it. A `ComingSoonScreen` is the screen header
and `MessageState`. The header loses its icon.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Navigation Compose (type-safe routes), Robolectric, Compose UI
test.

**Spec:** the vault ticket DN-UX-011 (`tickets/DN-UX-011-nav-rail-redesign.md`) and the canvas artboards "R1 · Nav
rail — Discover selected" and "R2 · Nav rail — Map: Coming soon"
(https://claude.ai/artifact/3v68QUCWeu87t7n2GFf8nf).

## Global Constraints

- Worktree `.claude/worktrees/dn-ux-011-nav-rail-redesign`, branch `dn-ux-011-nav-rail-redesign`; never commit on
  `main`; no AI attribution. Commit subject ≤ 72 characters (50 preferred), body wrapped at 72.
- The rail stays the 76 dp column (`MinTouchTarget`) left of the panel: the content panel's width is unchanged.
- Tab: 76 × 96 dp, the whole tab the touch target with the focus ring; a 64 × 60 dp tile (radius `RowRadius`), icon
  `RailIconSize`, label `titleSmall` (14 sp) under it. Selected: Action tile, white icon, Accent label (semibold);
  unselected: no tile, OnSurfaceVariant icon and label. The Discover icon is `ic_compass_pin`, untinted.
- Rail panel: Surface colour, `PanelRadius`, 8 dp vertical padding, 4 dp between tabs; Profile at the bottom under a
  1 dp Raised divider inset `ContentGap`.
- Copy exactly: "Map", "Profile", "Coming soon", "The map isn't part of this version yet.", "Profiles aren't part of
  this version yet."
- Rotary (Product-approved): the rail after the content; order content → Discover → Saved → Map → Profile; the rail's
  two rules and `ReturnFocus` unchanged.
- Architecture rules (CLAUDE.md): only `ui/` imports Compose; all user text in `strings.xml`.

## Review Focus

- Back from Map or Profile returns to Discover; tapping the same tab twice stacks nothing; Map then Profile leaves one
  tab screen on the stack.
- The tab's accessibility name is read once (no "Saved Saved").
- The rail fits the 516 dp reference height with Profile whole at the bottom.
- The header text's left edge is the rail's left edge.
- Driving: the "Coming soon" screens are static; nothing to restrict.

---

### Task 1: Tabs, routes and the "Coming soon" screens

**Files:** `ui/DiscoverNavHost.kt` (`RailTab`, `MapRoute`, `ProfileRoute`, `selectedTab`, `openTab`, two destinations),
new `ui/screens/ComingSoonScreen.kt`, `res/values/strings.xml`, new `res/drawable/ic_map.xml`, `ic_person.xml`.
Tests: `ui/DiscoverNavigationTest.kt`, `ui/OpenTabTest.kt`.

- [ ] Failing tests: Map opens "Coming soon" with the map line, Map selected, Back to Discover; Profile's Back button
  returns to Discover; Map twice stacks one screen; Map then Profile then Back lands on Discover; rotary reaches Map
  and Profile after Saved.
- [ ] Run them: they fail (no Map or Profile tab).
- [ ] Implement: `RailTab(route, icon, label, tinted = true, atBottom = false)` with DISCOVER (`ic_compass_pin`,
  untinted), SAVED (`ic_bookmark`), MAP (`ic_map`), PROFILE (`ic_person`, at the bottom); `selectedTab` returns the
  tab whose route is on the stack, else Discover; `openTab` pops to Discover, back to the tab's own screen when it is
  selected, else navigates with `popUpTo<DiscoverRoute>()`; `ComingSoonScreen(title, icon, body, onBack)` (the
  simplify pass made it `ComingSoonScreen(tab, body, onBack)`, the title and icon read from the tab).
- [ ] Run them: they pass. Commit — `Add Map and Profile tabs that open Coming soon`.

### Task 2: The rail's look and the header

**Files:** `ui/components/AppRail.kt`, `ui/DiscoverNearbyApp.kt`, `ui/theme/Dimens.kt`. Test: `ui/components/AppRailTest.kt`
(new, AUTOMOTIVE_1024P).

- [ ] Failing test: on the reference panel, all four tabs are at least 76 dp wide and tall, Profile's bottom is inside
  the rail and below Map, and each tab has one accessibility name.
- [ ] Implement the rail panel, tabs, tile, label and divider; the rail fills the column's height; the header without
  its icon (and `HeaderIconSize` removed if unused).
- [ ] Run the rail, navigation, focus-ring and rotary tests. Commit — `Give the rail a panel and labelled tabs`.

### Task 3: Docs

**Files:** docs/02 (the rail, §16 order), docs/03 §17, `docs/design/README.md`, `CLAUDE.md`.

- [ ] Update each to the four tabs, the labels, "Coming soon", the header; commit — `Record the DN-UX-011 rail in the
  docs`.

### Task 4: Checks, emulator, review, PR

- [ ] `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`.
- [ ] Emulator (`AAOS_AOSP_33_userdebug`): the rail on Discover, Saved, Map and Profile; Back; Drive; rotary order with
  `focusedNode` (no `uiautomator`).
- [ ] `simplify`; `correctness-reviewer` and `quality-reviewer`; push; PR with `pr-description`; ticket and NOW.md.
