# Discover Nearby — Claude Context

AAOS proof of concept: intent-based nearby-place discovery (Coffee, Food, Outdoors,
Family, Scenic, Explore) → up to 3–5 recommendations → place details → navigation handoff.
This iteration targets a sideloaded debug POC on the AAOS userdebug emulator. Production distribution is
undecided and outside this POC's scope; Product must choose a supported route before production planning.
An OEM-preinstall route requires OEM confirmation.

Source of truth: `docs/` — **Revision 4.1** (brief, UX spec, engineering plan, test plan,
delivery plan, `docs/adr/`, `docs/design/` = visual spec). Read `docs/03-…` §2, §3, §6 before
structural work. If code and docs disagree, stop and ask.

## Current state of the code (read this first)
Single Compose `:app` module (DN-M0-009): a placeholder `ui/MainActivity`, declared
`distractionOptimized`, and `ManifestContractTest`. compileSdk 37 (one `android-compileSdk` in
`gradle/libs.versions.toml`; the app derives its `android.car.jar` path from it), targetSdk 36, minSdk 29.
No Car App Library; do not add its APIs. detekt + lint + CI in place (DN-M0-012, DN-M0-001).
Baseline (DN-M0-001): `DiscoverApplication` → `AppContainer`, canvas theme (`ui/theme`), Robolectric 4.17 at
SDK 36, `ArchitectureRulesTest`. Discover grid and navigation (DN-M0-002): `DiscoverNavHost`, in-app Back
(AOSP car bar has none), Recommendations placeholder. Domain model, `CategoryConfigs` and fakes (DN-M0-003): `model/`,
`places/` (+ `fake/`), `location/` (+ `fake/`), wired in `AppContainer`. UX restrictions via `CarDrivingRestrictions` (DN-M0-010; unknown = restrictions
apply; emulator moving limit 21). Recommendations flow on fake data (DN-M0-004): `RecommendationEngine`,
`DiscoverUseCase`, `RecommendationsViewModel` (collects the driving state; Back switches without a fade); debug
launches take `--es scenario <FakeScenario>`. Place Details (DN-M0-005): rows open
`PlaceDetailsRoute(place, distanceMeters)` (JSON route via `JsonNavType`), `PlaceDetailsViewModel` falls back to the summary,
Navigate hands off through `IntentNavigationLauncher` (DN-M3-001:
`ACTION_VIEW geo:`, failure → `NavigationUnavailable`). Location (DN-M0-006): `AndroidLocationProvider` (GPS/network,
8 s fix timeout; approximate-only uses the platform's recent coarse fix), Grant only while restrictions allow it,
denied copy; emulator location via `adb emu geo fix`, location on for user 10, `pm clear --user 10`. Rotary
(DN-M0-011): 4 dp focus ring on the tiles, `RotaryContractTest`. ADR-002 (2026-10-06): **Compose for the
emulator POC**; production distribution stays open. V7 is **open**: E1 (DN-SP-003) traced the 2026-10-05 Not
workable result to the harness's `uiautomator` polling at launch. Gate: rotation reaches Navigate, select
activates the focused control, Back loses no turn, visible focus on every actionable control; controller
rotation on Android 13 only, no nudging. One bounded Compose fix (lost turn after Back + focus on rows, header
Back, Navigate), then a clean re-test; if it fails, V7 fails and ADR-002 reopens. Check docs/05 §9 V7 before
rotary work.
Stub navigation app (DN-M0-008): module `:stub-navigation` in `tools/stub-navigation/`, a `geo:` VIEW handler
(`singleTask`, distractionOptimized) that shows the URI and logs `StubNav: received geo:…`.
Smoke baseline (DN-M0-007): reference configuration in docs/04 §2, the M0 smoke in docs/04 §10 (re-run it when
the image or UI changes); the Robolectric smoke test is `DiscoverScreenTest`.
Car App Library rotary probe (DN-SP-002): `tools/cal-rotary-probe/`, a standalone build (not in the root build or CI),
evidence for ADR-002 (it did not pass; on cold boots it completed the journey, but rotary entry failed after
the first few launches); keep it until V7 resolves, delete it if V7 passes (ADR-002 reopens if V7 fails).
Next: the bounded V7 fix (DN-M0-011), then the clean re-test; DN-UX-001 is unblocked.

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
- Location permission via `rememberLauncherForActivityResult(RequestMultiplePermissions())`, fine and coarse
  together (either grant counts; approximate-only must work — DN-M0-006);
  Grant offered only when `DrivingState.distractionOptimizationRequired` is false. It reports UX
  restrictions, not the gear; never call it "parked" in code.
- Rotary is the app's job: every actionable element focusable with visible focus, focus order per
  UX spec §16, initial focus on first item. No in-app focus restore after Back for now: it "trapped rotation"
  only in a `uiautomator`-waited run (DN-M0-011); re-test it cleanly before relying on either result. Where
  focus lands after Back is recorded, not gated (ADR-002).
- Every request carries a requestId; drop stale responses. Provider calls have a timeout.
- Attributes shown only if PROVIDED or DERIVED. All user text in strings.xml.
- `docs/design/` is the visual spec (layout, copy, states, icons, colours).

## Commands
- Build: `./gradlew assembleDebug`
- Unit + Robolectric/Compose tests: `./gradlew testDebugUnitTest` (release host tests are disabled).
- Lint: `./gradlew lintDebug`
- Detekt: `./gradlew detekt` (1.23.8, defaults + `config/detekt/detekt.yml`, `maxIssues: 0`).
- Stub navigation app: `ANDROID_SERIAL=emulator-5554 ./gradlew :stub-navigation:installDebug`; watch hand-offs with
  `adb -s emulator-5554 logcat -s StubNav` (a cold start of the debug APK can take ~10 s on the emulator).
- CI runs `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` on every PR; the `build` check is required on `main`.
- Repo git hooks do NOT run on this machine (global `core.hooksPath`); run checks yourself.
- Before saying a task is done: detekt, test, build and lint must pass.

## Emulator (AVD `AAOS_AOSP_33_userdebug`, x86_64 — this Mac is Intel; always `adb -s emulator-5554`)
- Drive: `adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8`
  then hold moving: `adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60`
- Park: `adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4`
- Check state: `adb -s emulator-5554 shell dumpsys car_service --services CarDrivingStateService`
- Distraction-optimised check: `adb -s emulator-5554 shell cmd car_service get-do-activities com.kanyandula.discovernearby`
- Never `adb reboot`; kill and relaunch the emulator instead.
- Rotary runs: no `uiautomator` while the app is on screen (runs that waited with it reproduced V7's
  failure; E1, DN-SP-003, tests why). Read focus from
  `adb -s emulator-5554 shell dumpsys activity service com.android.car.rotary/.RotaryService` (`focusedNode`)
  and screenshots.

## Secrets
Provider keys live in `local.properties` → BuildConfig. Never commit or log keys or raw coordinates
(except the stub navigation app's `StubNav` log, which docs/03 §11 requires).

## Ticket workflow

Tickets live in the vault: `~/.claude/projects/Discover Nearby/tickets/` (status in frontmatter).

1. Pick a `ready` ticket. Set `status: in_progress` and its `branch:` field.
2. Update `main` (`git switch main && git pull`), then branch from it:
   `dn-<ticket-id-lowercase>-<short-name>`, e.g. `dn-m0-001-compose-app`.
3. Do the work. Run `./gradlew detekt lintDebug testDebugUnitTest assembleDebug`.
4. Run the `simplify` skill and fix its findings.
5. Push the branch and open the PR into `main` with the `pr-description` skill; include
   the ticket ID and its acceptance criteria. Never commit on local `main`, never push to
   remote `main`; merge only via the PR (`main` is branch-protected).
6. After the merge: set the ticket to `done`, update `NOW.md`, delete the branch.

No AI attribution (`Co-Authored-By: Claude`, "Generated with Claude Code") in commits,
PR titles or PR bodies.
