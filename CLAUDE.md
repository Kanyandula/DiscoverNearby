# Discover Nearby — Claude Context

AAOS proof of concept: intent-based nearby-place discovery (Coffee, Food, Outdoors,
Family, Scenic, Explore) → up to 3–5 recommendations → place details → navigation handoff.
Emulator-only. Debug builds, sideloaded. Not shipping to Play.

Source of truth: `docs/` — **Revision 4.1** (brief, UX spec, engineering plan, test plan,
delivery plan, `docs/adr/`, `docs/design/` = visual spec). Read `docs/03-…` §2, §3, §6 before
structural work. If code and docs disagree, stop and ask.

## Current state of the code (read this first)
Single Compose `:app` module (DN-M0-009): a placeholder `ui/MainActivity`, declared
`distractionOptimized`, and `ManifestContractTest`. compileSdk 37 (one `compileApi` value in
`app/build.gradle.kts`, which also derives the `android.car.jar` path), targetSdk 36, minSdk 29.
No Car App Library; do not add its APIs. detekt + CI in place (DN-M0-012). Next: DN-M0-001.

## Stack (Revision 4)
- Kotlin, Coroutines. Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`).
- UI: **Jetpack Compose** (Compose BOM, Material 3), `activity-compose`, `navigation-compose`,
  `lifecycle-viewmodel-compose`. One activity, declared `distractionOptimized`. No Car App Library.
- Car API: `android.car` from the SDK's `optional/android.car.jar`, `compileOnly` (as in NyasaPlayer).
- minSdk 29. One compileSdk for the whole project.
- State: one Jetpack `ViewModel` per content screen exposing `StateFlow`; screens use
  `collectAsStateWithLifecycle()`. ViewModels created via `viewModelFactory` from `AppContainer`.
- Networking: OkHttp + kotlinx.serialization, REST only. No provider SDK.
- DI: manual constructor injection, single wiring point `AppContainer` (created in
  `DiscoverApplication`). No Hilt.
- Persistence: none. Provider responses are in-memory only.
- Testing: JUnit, coroutines-test, Robolectric, Compose UI test (`ui-test-junit4`, `createComposeRule`).

## Structure (single module `app/`, package per concern — docs/03 §3)
- `ui/` — MainActivity, DiscoverNavHost, theme/, components/, screens/ (+ ViewModels)
- `car/` — `DrivingRestrictions` (interface + `DrivingState`, no android.car) and
  `CarDrivingRestrictions` (CarUxRestrictionsManager → StateFlow)
- `discovery/` — DiscoverUseCase, RecommendationEngine, DiscoveryCategory, DiscoveryContext, CategoryConfig
- `places/` — PlacesRepository; `fake/FakePlacesRepository` (M0); `<provider>/` client + mapper (after ADR-001)
- `location/`, `navigation/` — interfaces + Android implementations (`IntentNavigationLauncher`)
- `model/` — GeoPoint, PlaceSummary, PlaceDetails, PlaceAttribute, Recommendation
- `tools/stub-navigation/` — separate test APK: ACTION_VIEW `geo:` handler, distractionOptimized

## Architecture rules (do not break)
- Only `ui/` imports Compose. Only `car/CarDrivingRestrictions` imports `android.car`.
  `discovery/`, `model/`, `places/`, `location/` have no Compose or Car API imports.
- Provider response models never leave the provider package; map to domain models.
- RecommendationEngine is pure Kotlin. Unknown data is neutral; known-closed places are excluded;
  never pad results. It never sees driving state.
- The ViewModel applies the driving limit: `visible = take(min(5, drivingState.listLimit ?: MAX))`,
  and re-trims when the state changes.
- Loading/content/error are states of ONE destination, never pushed destinations.
  Core path ≤ 3 destinations deep. Back pops one; Back on Discover leaves the app.
- Navigation: `Intent(ACTION_VIEW, "geo:%.6f,%.6f")` with Locale.US, application Context +
  FLAG_ACTIVITY_NEW_TASK. Never target a specific app. ActivityNotFound/Security/any failure →
  NavigationUnavailable.
- Location permission via `rememberLauncherForActivityResult(RequestPermission())`;
  Grant offered only when `DrivingState.distractionOptimizationRequired` is false. It reports UX
  restrictions, not the gear; never call it "parked" in code.
- Rotary is the app's job: every actionable element focusable with visible focus, focus order per
  UX spec §16, initial focus on first item, focus restored after Back (V7 — prove in M0).
- Every request carries a requestId; drop stale responses. Provider calls have a timeout.
- Attributes shown only if PROVIDED or DERIVED. All user text in strings.xml.
- `docs/design/` is the visual spec (layout, copy, states, icons, colours).

## Commands
- Build: `./gradlew assembleDebug`
- Unit + Robolectric/Compose tests: `./gradlew test`
- Lint: `./gradlew lintDebug`
- Detekt: `./gradlew detekt` (1.23.8, defaults + `config/detekt/detekt.yml`, `maxIssues: 0`).
- CI runs `./gradlew detekt testDebugUnitTest assembleDebug` on every PR; the `build` check is required on `main`.
- Repo git hooks do NOT run on this machine (global `core.hooksPath`); run checks yourself.
- Before saying a task is done: detekt, test, build and lint must pass.

## Emulator (AVD `AAOS_AOSP_33_userdebug`, x86_64 — this Mac is Intel; always `adb -s emulator-5554`)
- Drive: `adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8`
  then hold moving: `adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60`
- Park: `adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4`
- Check state: `adb -s emulator-5554 shell dumpsys car_service --services CarDrivingStateService`
- Distraction-optimised check: `adb -s emulator-5554 shell cmd car_service get-do-activities com.kanyandula.discovernearby`
- Never `adb reboot`; kill and relaunch the emulator instead.

## Secrets
Provider keys live in `local.properties` → BuildConfig. Never commit or log keys or raw coordinates.

## Ticket workflow

Tickets live in the vault: `~/.claude/projects/Discover Nearby/tickets/` (status in frontmatter).

1. Pick a `ready` ticket. Set `status: in_progress` and its `branch:` field.
2. Update `main` (`git switch main && git pull`), then branch from it:
   `dn-<ticket-id-lowercase>-<short-name>`, e.g. `dn-m0-001-compose-app`.
3. Do the work. Run `./gradlew detekt testDebugUnitTest assembleDebug`.
4. Run the `simplify` skill and fix its findings.
5. Push the branch and open the PR into `main` with the `pr-description` skill; include
   the ticket ID and its acceptance criteria. Never commit on local `main`, never push to
   remote `main`; merge only via the PR (`main` is branch-protected).
6. After the merge: set the ticket to `done`, update `NOW.md`, delete the branch.

No AI attribution (`Co-Authored-By: Claude`, "Generated with Claude Code") in commits,
PR titles or PR bodies.
