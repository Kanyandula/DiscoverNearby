# DN-M0-012 Detekt and CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every PR into `main` is checked automatically by detekt, unit tests and a debug build, and `main` cannot be merged into unless that check passes.

**Architecture:** detekt 1.23.8 is applied to `:app` with `buildUponDefaultConfig = true` and a small override file at `config/detekt/detekt.yml`. A single GitHub Actions job named `build` runs `./gradlew detekt testDebugUnitTest assembleDebug` on JDK 21 for every `pull_request` to `main`. Once that check has run once, the existing `main` protection is replaced in full by the same protection plus `build` as a required, up-to-date status check.

**Tech Stack:** detekt Gradle plugin `io.gitlab.arturbosch.detekt` 1.23.8; GitHub Actions `actions/checkout@v7`, `actions/setup-java@v6` (Temurin 21), `gradle/actions/setup-gradle@v6`; `gh api` for branch protection.

**Spec:** Ticket `~/.claude/projects/Discover Nearby/tickets/DN-M0-012-detekt-and-ci.md`, plus `docs/05-discover-nearby-delivery-plan.md` §2–3 and `docs/03-discover-nearby-engineering-implementation-plan.md` §6.1, §20, and the project `CLAUDE.md` (Commands, Ticket workflow).

## Global Constraints

- detekt version is recorded; it must run on this project's Gradle 9.4.1 / AGP 9.2.1 / built-in Kotlin 2.2.10 (ticket AC).
- `./gradlew detekt` passes with `maxIssues: 0`; a baseline is used only if needed (ticket AC). None is needed: the two existing issues are fixed instead.
- CI: `.github/workflows/ci.yml`, trigger `pull_request` to `main`, JDK 21, command exactly `./gradlew detekt testDebugUnitTest assembleDebug` (ticket AC).
- The required check is added only after it has run once, so its name exists (ticket AC).
- Existing `main` protection is kept exactly: PR required with 0 approvals, `enforce_admins: true`, `allow_force_pushes: false`, `allow_deletions: false` (ticket AC).
- compileSdk 37 needs platform `android-37.0` (and its `android.car.jar`) on the runner.
- No detekt formatting or Compose-rules plugins in this ticket (not in the AC; adding them is a separate decision).
- Commits: never on local `main`; no AI attribution in commits or the PR.

## Review Focus

1. **CI green while detekt is silently skipped.** If detekt were not wired to the sources, CI would pass without checking anything. Pinned by Task 1, Step 2 (detekt fails on the two known long lines before they are fixed).
2. **Runner lacks SDK platform 37 or `android.car.jar`.** The build would fail on the runner only. Pinned by the explicit `sdkmanager` step in Task 2 and the first CI run.
3. **Required-check name mismatch.** If the protection names a context the workflow never reports, every PR is blocked forever. Pinned by reading the actual check-run name in Task 3, Step 1 before writing it into the protection.
4. **Protection update drops an existing setting.** The PUT replaces the whole protection object. Pinned by Task 3, Step 3 comparing every field after the update.
5. **The required check does not actually block.** Pinned by Task 3, Step 4: a deliberate detekt violation on the PR makes the check fail and `mergeStateStatus` report `BLOCKED`, then it is reverted.

---

## Investigation findings (2026-10-02)

- detekt 1.23.8 is the latest 1.x; 2.x is `2.0.0-alpha.6` only. Spike in a throwaway worktree: 1.23.8 runs on Gradle 9.4.1 + AGP 9.2.1, works with the configuration cache, and reports real issues.
- The plugin emits one Gradle deprecation: `ReportingExtension.file(String)` (removed in Gradle 10). It comes from detekt itself; harmless until Gradle 10.
- Applied in `:app`, detekt scans `src/main/java` and `src/test/java` and reads the root `config/detekt/detekt.yml` by default; no `source` or `config` wiring is needed.
- Against current `main` it finds 2 issues, both `MaxLineLength` in `ManifestContractTest.kt` (lines 54 and 61).
- GitHub Actions is enabled for the repo. `main` protection today: PR required (0 approvals), admins enforced, no force-push, no deletion, no required checks.
- Latest action releases: `actions/checkout` v7.0.1, `actions/setup-java` v6.0.1, `gradle/actions` v6.4.0.
- `gradlew` is tracked as `100755`, so it runs on Linux.

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `gradle/libs.versions.toml` | Modify | `detekt` version and plugin alias |
| `build.gradle.kts` (root) | Modify | Declare the detekt plugin `apply false` |
| `app/build.gradle.kts` | Modify | Apply detekt; `buildUponDefaultConfig = true` |
| `config/detekt/detekt.yml` | Create | Overrides on top of detekt defaults |
| `app/src/test/java/com/kanyandula/discovernearby/ManifestContractTest.kt` | Modify | Wrap the two lines over 120 characters |
| `.github/workflows/ci.yml` | Create | The `build` check |
| `CLAUDE.md` | Modify | Commands and workflow now that detekt exists |
| `main` branch protection (GitHub) | Update | Add `build` as a required, strict status check |

---

### Task 0: Start the ticket

**Files:**
- Modify: `~/.claude/projects/Discover Nearby/tickets/DN-M0-012-detekt-and-ci.md` (frontmatter)
- Commit: `docs/superpowers/plans/2026-10-02-dn-m0-012-detekt-and-ci.md`

**Interfaces:**
- Consumes: nothing
- Produces: branch `dn-m0-012-detekt-ci`

- [ ] **Step 1: Mark the ticket in progress**

In the ticket frontmatter set:

```yaml
status: in_progress
branch: dn-m0-012-detekt-ci
```

- [ ] **Step 2: Update main and create the branch**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-m0-012-detekt-ci
git status --short
```

Expected: only `?? docs/superpowers/plans/2026-10-02-dn-m0-012-detekt-and-ci.md`.

- [ ] **Step 3: Commit the plan**

```bash
git add docs/superpowers/plans/2026-10-02-dn-m0-012-detekt-and-ci.md
git commit -m "Add DN-M0-012 implementation plan"
```

---

### Task 1: detekt runs and passes locally

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
- Create: `config/detekt/detekt.yml`
- Modify: `app/src/test/java/com/kanyandula/discovernearby/ManifestContractTest.kt:54,61`
- Modify: `CLAUDE.md:67,69,89`

**Interfaces:**
- Consumes: nothing
- Produces: Gradle task `:app:detekt` (reached by `./gradlew detekt`); `config/detekt/detekt.yml`

- [ ] **Step 1: Wire detekt**

`gradle/libs.versions.toml`, add under `[versions]` after `junit`:

```toml
detekt = "1.23.8"
```

and under `[plugins]` before `kotlin-compose`:

```toml
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

Root `build.gradle.kts` becomes:

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
}
```

In `app/build.gradle.kts`, the `plugins` block becomes:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}
```

and add after the `android { … }` block:

```kotlin
// Defaults plus config/detekt/detekt.yml (picked up from the root by convention).
detekt {
    buildUponDefaultConfig = true
}
```

`config/detekt/detekt.yml`:

```yaml
build:
  maxIssues: 0

naming:
  FunctionNaming:
    # Composable functions are PascalCase by convention.
    ignoreAnnotated:
      - "Composable"
```

- [ ] **Step 2: Run detekt and watch it fail on the known issues**

Run: `./gradlew detekt --console=plain 2>&1 | grep -E "\[MaxLineLength\]|weighted issues|BUILD"`
Expected: two `[MaxLineLength]` lines for `ManifestContractTest.kt:54:1` and `:61:1`, `Analysis failed with 2 weighted issues.`, `BUILD FAILED`. This proves detekt reads `app/src/test/java` with `maxIssues: 0`.

- [ ] **Step 3: Wrap the two long lines**

In `ManifestContractTest.kt`, replace line 54:

```kotlin
        val automotive = elements("uses-feature").single { it.androidAttr("name") == "android.hardware.type.automotive" }
```

with:

```kotlin
        val automotive = elements("uses-feature")
            .single { it.androidAttr("name") == "android.hardware.type.automotive" }
```

and replace line 61:

```kotlin
        assertTrue(permissions.containsAll(setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.INTERNET")))
```

with:

```kotlin
        val required = setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.INTERNET")
        assertTrue(permissions.containsAll(required))
```

- [ ] **Step 4: Run the full local check**

Run: `./gradlew detekt testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "\[[A-Za-z]+\]|BUILD|FAILED"`
Expected: no rule lines, `BUILD SUCCESSFUL`. Then confirm 4/4 tests:

Run: `grep -o -E 'tests="[0-9]+"|failures="[0-9]+"' app/build/test-results/testDebugUnitTest/*.xml`
Expected: `tests="4"`, `failures="0"`.

- [ ] **Step 5: Update CLAUDE.md for a configured detekt**

Replace line 67:

```markdown
- Detekt: `./gradlew detekt` — not configured yet (M0 task). Until then: build + test + lint.
```

with:

```markdown
- Detekt: `./gradlew detekt` (1.23.8, defaults + `config/detekt/detekt.yml`, `maxIssues: 0`).
- CI runs `./gradlew detekt testDebugUnitTest assembleDebug` on every PR; the `build` check is required on `main`.
```

Replace on line 69 `build, test and lint (and detekt once configured) must pass.` with `detekt, test, build and lint must pass.`

Replace on line 89 `Run \`./gradlew detekt testDebugUnitTest assembleDebug\` (detekt once it exists).` with `Run \`./gradlew detekt testDebugUnitTest assembleDebug\`.`

Replace on line 15 `Next: DN-M0-012 (detekt + CI), then DN-M0-001.` with `detekt + CI in place (DN-M0-012). Next: DN-M0-001.`

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts config/detekt/detekt.yml \
  app/src/test/java/com/kanyandula/discovernearby/ManifestContractTest.kt CLAUDE.md
git commit -m "Add detekt 1.23.8 with defaults and zero issues

Applied to :app with buildUponDefaultConfig; config/detekt/detekt.yml
sets maxIssues 0 and exempts @Composable from FunctionNaming. Wraps the
two ManifestContractTest lines it flagged instead of baselining them."
```

---

### Task 2: CI workflow, running on a draft PR

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `./gradlew detekt testDebugUnitTest assembleDebug` passing locally (Task 1)
- Produces: a check run named `build` on PRs into `main`; draft PR for this branch

- [ ] **Step 1: Write the workflow**

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: build
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "21"

      - uses: gradle/actions/setup-gradle@v6

      # compileSdk 37 and the android.car.jar compileOnly path need this platform.
      - name: Install Android SDK platform 37
        run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-37.0" > /dev/null

      - name: Detekt, unit tests, debug build
        run: ./gradlew detekt testDebugUnitTest assembleDebug --console=plain
```

- [ ] **Step 2: Commit and push**

```bash
git add .github/workflows/ci.yml
git commit -m "Run detekt, unit tests and a debug build on PRs to main"
git push -u origin dn-m0-012-detekt-ci
```

- [ ] **Step 3: Open a draft PR so the `pull_request` trigger fires**

```bash
gh pr create --draft --base main --title "DN-M0-012: Add detekt and CI, require CI on main" \
  --body "Ticket: DN-M0-012. Draft while CI is proven; description follows via pr-description."
```

Expected: a PR URL.

- [ ] **Step 4: Watch the first CI run**

Run: `gh pr checks --watch --interval 20`
Expected: `build  pass`.

If the SDK step fails because `cmdline-tools/latest` is missing on the runner, replace that step with `- uses: android-actions/setup-android@v3` followed by `- run: sdkmanager "platforms;android-37.0"`, commit, push, and watch again. Record the change in the ticket.

---

### Task 3: Make `build` a required check on `main`, and prove it blocks

**Files:**
- None in the repo (GitHub settings). Temporary commit in Step 4, reverted in Step 5.

**Interfaces:**
- Consumes: the check run from Task 2
- Produces: `main` protection with `required_status_checks: {strict: true, contexts: ["build"]}`

- [ ] **Step 1: Read the actual check-run name**

Run: `gh api repos/Kanyandula/DiscoverNearby/commits/$(git rev-parse HEAD)/check-runs --jq '.check_runs[].name'`
Expected: `build`. If it prints anything else, use that exact string in Step 2.

- [ ] **Step 2: Replace the protection with the same settings plus the required check**

```bash
gh api -X PUT repos/Kanyandula/DiscoverNearby/branches/main/protection --input - <<'EOF'
{
  "required_status_checks": { "strict": true, "contexts": ["build"] },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 0,
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF
```

- [ ] **Step 3: Verify every field**

Run:

```bash
gh api repos/Kanyandula/DiscoverNearby/branches/main/protection --jq '{checks: .required_status_checks.contexts, strict: .required_status_checks.strict, admins: .enforce_admins.enabled, force: .allow_force_pushes.enabled, del: .allow_deletions.enabled, reviews: .required_pull_request_reviews.required_approving_review_count}'
```

Expected: `{"checks":["build"],"strict":true,"admins":true,"force":false,"del":false,"reviews":0}`.

- [ ] **Step 4: Prove the check blocks a failing PR**

Add this line, over 120 characters, to the end of `MainActivity.kt`:

```kotlin
private const val DETEKT_GATE_PROBE = "this string only exists to push the line past the one hundred and twenty character limit"
```

```bash
git add app/src/main/java/com/kanyandula/discovernearby/ui/MainActivity.kt
git commit -m "Probe: deliberate detekt violation (reverted next commit)"
git push
gh pr checks --watch --interval 20
gh pr view --json mergeStateStatus --jq .mergeStateStatus
```

Expected: `build  fail`, then `BLOCKED`.

- [ ] **Step 5: Revert the probe**

```bash
git revert --no-edit HEAD
git push
gh pr checks --watch --interval 20
```

Expected: `build  pass`.

---

### Task 4: Close out the ticket and finish the PR

**Files:**
- Modify: `~/.claude/projects/Discover Nearby/tickets/DN-M0-012-detekt-and-ci.md` (completion notes)

**Interfaces:**
- Consumes: Tasks 0–3
- Produces: a ready-for-review PR

- [ ] **Step 1: Final local check**

Run: `./gradlew detekt testDebugUnitTest assembleDebug --console=plain 2>&1 | grep -E "BUILD|FAILED"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run `simplify` on the branch diff and apply its findings**

If it changes anything, rerun Step 1, then:

```bash
git add -A && git commit -m "Apply simplify review findings" && git push
```

- [ ] **Step 3: Write the ticket completion notes**

Append under "Completion notes":

```markdown
- 2026-10-02 — Branch `dn-m0-012-detekt-ci`. Plan: `docs/superpowers/plans/2026-10-02-dn-m0-012-detekt-and-ci.md`.
- detekt 1.23.8 (latest 1.x; 2.x is alpha only). Runs on Gradle 9.4.1 / AGP 9.2.1 / Kotlin 2.2.10 with the configuration cache. Known: one Gradle 10 deprecation (`ReportingExtension.file`) from the plugin itself.
- No baseline: the two MaxLineLength issues in ManifestContractTest were fixed.
- CI: `.github/workflows/ci.yml`, job `build`, JDK 21, installs platform `android-37.0`, runs `./gradlew detekt testDebugUnitTest assembleDebug`.
- Required check: `gh api -X PUT …/branches/main/protection` with `required_status_checks {strict: true, contexts: ["build"]}`; all other protection fields unchanged (verified).
- Checks run: local detekt RED (2 MaxLineLength) → GREEN; first CI run on the PR; probe commit → `build fail` + `mergeStateStatus BLOCKED`; revert → `build pass`.
```

Fill in each check with the actual output observed.

- [ ] **Step 4: Write the PR description with the `pr-description` skill and mark it ready**

The body must include `Ticket: DN-M0-012`, each acceptance criterion with how it was met, the probe evidence, and no AI attribution. Then:

```bash
gh pr ready
```

- [ ] **Step 5: After the user merges**

Set the ticket `status: done`, set DN-M0-001 `status: ready`, update `NOW.md` (next: DN-M0-001), and delete the branch:

```bash
git switch main && git pull --ff-only
git branch -d dn-m0-012-detekt-ci
git push origin --delete dn-m0-012-detekt-ci
```
