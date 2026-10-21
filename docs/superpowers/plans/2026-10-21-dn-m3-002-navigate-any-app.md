# DN-M3-002 Navigate Any App — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> This project runs plans natively (inline), task by task.

**Goal:** Navigate starts a route in whichever navigation app the car uses: the AAOS navigation intent first, the
`geo:` view intent as the fallback, Navigation unavailable when neither is handled.

**Architecture:** `IntentNavigationLauncher` starts `Intent("androidx.car.app.action.NAVIGATE", geo:lat,lng)`; on any
failure it starts `ACTION_VIEW` with the same URI; a failure of both is the `Result.failure` that Place Details already
maps to `NavigationUnavailable`. The action is a string constant (no Car App Library). The stub answers both: its
activity keeps the `VIEW` filter, and an `activity-alias` carries the `NAVIGATE` filter, so `pm disable` on the alias
exercises the fallback on the emulator. The stub shows and logs which request it received.

**Tech Stack:** Kotlin, Android intents, Jetpack Compose (stub), Robolectric.

**Spec:** the vault ticket DN-M3-002 (`tickets/DN-M3-002-navigate-any-app.md`) and its spike, DN-SP-006
(`tickets/DN-SP-006-navigation-handoff-any-app.md`): `NAVIGATE` + `geo:lat,lng` reached the right place in Google Maps
(started by itself) and Waze ("Received location", one tap); every `q=` form is out (Waze: text search or failure).

## Global Constraints

- Worktree `.claude/worktrees/dn-m3-002-navigate-any-app`, branch `dn-m3-002-navigate-any-app`; never commit on
  `main`; no AI attribution. Commit subject ≤ 72 characters (50 preferred), body wrapped at 72.
- URI unchanged: `geo:%.6f,%.6f`, `Locale.US` (`geoUri`); no `q=`, no label.
- Application `Context` + `FLAG_ACTIVITY_NEW_TASK` for both intents; never a package or component.
- No Car App Library dependency: the action is a string.
- The stub's log keeps its `StubNav: received geo:…` prefix (docs/03 §11, CLAUDE.md); the request is appended.

## Review Focus

- The fallback covers every `NAVIGATE` failure (no handler, `SecurityException`, anything else), and a `VIEW` failure
  still reaches `NavigationUnavailable`.
- Exactly one activity is started on success (no `VIEW` after a successful `NAVIGATE`).
- The alias is exported, distraction-optimised (Drive), and resolves to the same `singleTask` activity.

## Tasks

### Task 1: Launcher — `NAVIGATE` first, `VIEW` as the fallback

**Files:** `app/src/main/java/com/kanyandula/discovernearby/navigation/IntentNavigationLauncher.kt`,
`app/src/test/java/com/kanyandula/discovernearby/navigation/IntentNavigationLauncherTest.kt`

- [ ] Tests first: `NAVIGATE` started first (action, URI, `NEW_TASK`, no package or component, nothing else started);
  no `NAVIGATE` handler → `VIEW` with the same URI; `SecurityException` or any other failure on `NAVIGATE` → `VIEW`;
  no handler at all (`checkActivities`) → failure; both refused → failure.
- [ ] Implement: `runCatching { start(NAVIGATE) }.recoverCatching { start(VIEW) }`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*IntentNavigationLauncherTest'`; commit.

### Task 2: End-to-end test

**Files:** `app/src/test/java/com/kanyandula/discovernearby/ui/DiscoverNavigationTest.kt`

- [ ] `navigateHandsTheSelectedPlaceToTheSystem` expects the `NAVIGATE` action; the no-handler test still ends in
  Navigation unavailable. Run the class; commit with Task 1 if it only follows it.

### Task 3: Stub answers both requests

**Files:** `tools/stub-navigation/src/main/AndroidManifest.xml`, `StubNavigationActivity.kt`, `strings.xml`,
`StubNavigationActivityTest.kt`

- [ ] Tests first: an implicit `NAVIGATE` + `geo` intent resolves to the stub (the alias, targeting the activity,
  exported); the received request is shown and logged (`received geo:… via <action>`), for `VIEW` and `NAVIGATE`.
- [ ] Manifest: an `activity-alias` with the `NAVIGATE` filter and `distractionOptimized`; the screen shows the request
  under the URI; the log appends it.
- [ ] Run `./gradlew :stub-navigation:testDebugUnitTest`; commit.

### Task 4: Docs

- [ ] docs/03 §11: the mechanism and fallback; drop "only a template app can send"; the chooser with several navigation
  apps; the stub's two filters and the alias; the check on a real navigation app (DN-SP-006).
- [ ] docs/01 §4.4: the standard AAOS navigation intent, then a plain `geo:` intent.
- [ ] docs/04: §2 query for both intents; stub setup sanity checks; Scenario E (the stub shows `NAVIGATE`; the fallback
  with the alias disabled); Scenario S; §10 smoke step 3; §11 automated coverage; an optional real-app check on a Play
  image.
- [ ] CLAUDE.md: the Place Details bullet, the stub bullet, the structure line, the navigation architecture rule.
- [ ] Commit.

### Task 5: Checks and emulator

- [ ] `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`.
- [ ] Reference emulator (`AAOS_AOSP_33_userdebug`, fakes): Navigate → the stub shows `NAVIGATE`; alias disabled →
  `VIEW`; stub disabled → Navigation unavailable; in Drive the stub still shows. Re-enable everything.
- [ ] Play image (supplementary): the app's Navigate → Waze (the default) lists "Received location"; record it.
- [ ] Ticket completion notes.

### Task 6: Review and PR

- [ ] `simplify` skill; correctness and quality reviewers; fix findings.
- [ ] Push; PR with the ticket ID and acceptance criteria.
