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
Single Compose `:app` module (DN-M0-009): the M0 fake-data flow is implemented across Discover, Recommendations,
Place Details and the `geo:` navigation handoff; location permission and UX-restriction handling are also in place.
The stub navigation app is separate. Every actionable control shows the rotary focus ring (`focusRing`); V7 failed
its clean re-test (2026-10-06); ADR-002 keeps Compose with a product waiver, and M0 exited under it (docs/05 §9).
The activity is declared `distractionOptimized`, with `ManifestContractTest`. compileSdk 37, minSdk 29 and
targetSdk 36 are in `gradle/libs.versions.toml` (`android-compileSdk`, `android-minSdk`, `android-targetSdk`); the
app derives its `android.car.jar` path from compileSdk. No Car App Library; do not add its APIs. detekt + lint + CI
in place (DN-M0-012, DN-M0-001).

- **Baseline (DN-M0-001):** `DiscoverApplication` → `AppContainer`, canvas theme (`ui/theme`), Robolectric 4.17 at
  SDK 36, `ArchitectureRulesTest`.
- **Discover grid and navigation (DN-M0-002):** `DiscoverNavHost` and in-app Back (AOSP car bar has none).
- **Domain model, `CategoryConfigs` and fakes (DN-M0-003):** `model/`, `places/` (+ `fake/`), `location/`
  (+ `fake/`), wired in `AppContainer`.
- **UX restrictions (DN-M0-010):** `CarDrivingRestrictions`; unknown = restrictions apply; emulator moving limit 21.
- **Recommendations on fake data (DN-M0-004):** `RecommendationEngine`, `DiscoverUseCase`,
  `RecommendationsViewModel` (collects the driving state; Back switches without a fade); debug launches take
  `--es scenario <FakeScenario>`.
- **Place Details (DN-M0-005):** rows open `PlaceDetailsRoute(place, distanceMeters)` (JSON route via
  `JsonNavType`); `PlaceDetailsViewModel` falls back to the summary; Navigate hands off through
  `IntentNavigationLauncher` (DN-M3-001: `ACTION_VIEW geo:`, failure → `NavigationUnavailable`).
- **Location (DN-M0-006):** `AndroidLocationProvider` (GPS/network, 8 s fix timeout; approximate-only uses the
  platform's recent coarse fix), Grant only while restrictions allow it, denied copy; emulator location via
  `adb emu geo fix`, location on for user 10, `pm clear --user 10`.
- **UI follow-ups (DN-UX-001):** `ParkToSee` when the driving limit allows no results; after a permanent refusal
  (`Denial.PERMANENT`), Open Settings replaces Grant while restrictions allow it, and the return from Settings
  searches again (`rememberOpenAppSettings`).
- **Rotary (DN-M0-011):** `focusRing` on every actionable control; `ReturnFocus` (after Back, rotary focus returns
  to the item selected by rotary once 250 ms have passed; the wait rests on a working hypothesis about Compose's
  semantics snapshot); `RotaryContractTest`. ADR-002 (Product Lead, 2026-10-06, after the re-test): **Compose for
  the emulator POC, with a product waiver**.
- **Stub navigation app (DN-M0-008):** module `:stub-navigation` in `tools/stub-navigation/`, a `geo:` VIEW handler
  (`singleTask`, distractionOptimized) that shows the URI and logs `StubNav: received geo:…`.
- **Smoke baseline (DN-M0-007):** reference configuration in docs/04 §2, the M0 smoke in docs/04 §10 (re-run it
  when the image or UI changes); the Robolectric smoke test is `DiscoverScreenTest`.
- **Provider evaluation (DN-SP-001):** `tools/provider-eval/evaluate.py` (stdlib Python, not in Gradle or CI); output
  in the git-ignored `out/`; outcome in ADR-001.
- **HERE client (DN-M1-001, wired in DN-M1-002):** `places/here/HerePlacesRepository` (OkHttp +
  kotlinx.serialization; `/browse`, `/lookup`), key `here.apiKey` → `BuildConfig.HERE_API_KEY`.
  - **Source:** `AppContainer` serves HERE when the key is set. It serves the fakes without a key (CI) and for
    `--es scenario`, which applies only to a fresh process: launch it with `am start -S`, because a warm
    relaunch keeps live data.
  - **Tests:** Robolectric uses `TestDiscoverApplication` (keyless) by its `Test<ApplicationName>` convention, so
    tests always get the fakes.
  - **Live categories:** Coffee is the first live category (ADR-001); the others are live but are accepted in M2.
- **Car App Library rotary probe (DN-SP-002):** removed in DN-M0-015 once ADR-002 kept Compose; its evidence is in
  `docs/adr/0002/`, its source in the DN-SP-002 plan and git history.

V7 **failed** its clean re-test after the one bounded fix (2026-10-06):
- Navigate reached and activated 4/4; visible focus everywhere; no lost turn after Back to Details or Back to
  Recommendations.
- In 1 of 4 runs, after Back to Discover, the service stayed on the host and the turn jumped to Coffee.

E1's pre-registered classification was inconclusive; its run pattern strongly implicated launch-time
`uiautomator` polling but did not prove the underlying cause. The gate stays: rotation reaches Navigate, select
activates the focused control, Back loses no turn, visible focus on every actionable control; controller rotation
on Android 13 only. V7 stays recorded as failed. The waiver accepts that Back to Discover jump as a known
limitation for this iteration, and M0 exits under it. Further rotary changes need a new decision.
Record: `docs/adr/0002/v7-retest-2026-10-06/`.

Next: DN-M1-003 (live place details: verify `/lookup`; attribution once HERE's brand guidance is read). ADR-001
provisionally selects HERE (Legal sign-off on provider terms pending before production). DN-TD-002 (Gradle/CI
tuning) is P3.

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
- `places/` — PlacesRepository; `fake/FakePlacesRepository` (M0); `here/` HERE client + mapper (DN-M1-001, ADR-001)
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
  UX spec §16, initial focus on first item. Rotary focus after Back goes through `ReturnFocus` only. Its
  Discover restore was not always reported to the rotary service in the re-test (waived for this iteration, ADR-002);
  don't change it without a new Product decision. Where focus lands after Back is recorded, not gated.
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
- CI runs `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` on every PR; the `build` check is required
  on `main`.
- Repo git hooks do NOT run on this machine (global `core.hooksPath`); run checks yourself.
- Before saying a task is done: detekt, test, build and lint must pass.

## Emulator (AVD `AAOS_AOSP_33_userdebug`, x86_64 — this Mac is Intel; always `adb -s emulator-5554`)
- Drive: `adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8`
  then hold moving: `adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60`
- Park: `adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4`
- Check state: `adb -s emulator-5554 shell dumpsys car_service --services CarDrivingStateService`
- Distraction-optimised check:
  `adb -s emulator-5554 shell cmd car_service get-do-activities com.kanyandula.discovernearby`
- Never `adb reboot`; kill and relaunch the emulator instead.
- Rotary behavioural runs: no `uiautomator` polling while the app launches. E1's preregistered classification
  was inconclusive, but launch-time polling strongly correlated with failures; report intentional controls
  separately. Read focus from
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
