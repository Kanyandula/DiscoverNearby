# DN-M2-002 Relevance Benchmark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the 18-cell relevance benchmark on the real app and record it, so the Product Lead can judge and sign
it off as M2's exit. The full record with names goes in the private vault; the public repo gets a name-free one.

**Architecture:**
- **Collection:** the app runs on the emulator in Park with the live HERE key, at the three docs/04 test locations.
  Each category is opened and its rows (up to 5) are read from screenshots. No code changes.
- **Records:**
  - The full record (names, distances, suggested judgments) is a vault note that the Product Lead edits in Obsidian.
  - After sign-off, a name-free summary (verdicts, counts, notes by kind) is committed under `docs/benchmarks/`.

**Tech Stack:** adb and the AAOS emulator, Markdown (Obsidian vault and repo docs), git.

**Spec:**
- the ticket `~/.claude/projects/Discover Nearby/tickets/DN-M2-002-relevance-benchmark.md`, including its 2026-10-07
  Product Lead decisions;
- docs/04 §2 (test locations) and §4 (benchmark rules and recording format);
- docs/05 §6 (ownership);
- docs/01 §10 (target kinds) and §11 (benchmark rules);
- ADR-001 "Test matrix" (its relevance rules for Coffee, Family and Scenic);
- the DN-M2-001 ticket notes: the questions this benchmark should answer.

## Global Constraints

- **Ticket acceptance criteria:**
  - Run all 18 cells: three locations × six categories.
  - Record up to five result names per cell and Product ACCEPT/REJECT judgments.
  - Mark top-result relevance, top-three usefulness where at least three credible places exist, wrong-category #1,
    and sparse-data notes.
  - Sparse cells are recorded as findings, not padded or treated as automatic failures.
  - Product Lead signs off the benchmark as the M2 exit condition.
- **Product Lead decisions (the user, 2026-10-07):**
  - **Names:** names live only in the vault note (`~/.claude/projects/Discover Nearby/benchmarks/`). The repo doc
    has verdicts, counts and name-free notes.
  - **Judging:** engineering pre-fills *suggested* judgments, marked as suggestions; the Product Lead reviews and
    signs off.
  - **Failures:** recorded, not tuned here. Any tuning is a follow-up ticket and a re-run.
- **Rules (docs/04 §4):**
  - Top result reasonably relevant?
  - Where at least three credible places exist, are at least 2 of the top 3 useful?
  - Obviously wrong-category #1? (Must not be.)
  - Sparse: are 1–2 strong results shown without weak padding?
- **Public repo:** no place names, place IDs or result coordinates in commits or tracked docs. Kinds ("a
  restaurant") are fine.
- **Emulator:**
  - always `adb -s emulator-5554`, Park, user 10;
  - send `geo fix` twice;
  - no `uiautomator`;
  - screenshots go to the session scratchpad, never the repo.
- **Checks:** docs only, so no Gradle run is needed. The name-leak check (Task 3) must print `0`.
- **Attribution:** none, in commits or the PR.

## Review Focus

1. **A stale location:** the app reuses the previous town's fix, so a whole location's cells show the wrong town.
   Expect each location's results to belong to that town. Pinned by the location check in Task 1 Step 2.
2. **A transient failure in a cell** (Error or Timeout, or the list still loading at the screenshot). Expect one
   retry, and the state recorded if it persists, never a guessed list. Pinned by Task 1 Step 4.
3. **Rows hidden below the fold.** The panel shows 3 rows and part of a 4th. Expect all rows (up to 5) recorded in
   order, with the overlap between the two screenshots removed. Pinned by Task 1 Step 4's rule: order by position,
   matching names and distances.
4. **A name leaking into the repo,** through the summary doc, a commit message or the PR. Expect none. Pinned by the
   name-leak check in Task 3 Step 3.
5. **A name that doesn't show its kind** (a one-word name under Food). Expect UNSURE rather than a guess, so the Product
   Lead decides. Pinned by Task 2's rubric.

---

### Task 1: Collect the 18 cells

**Files:**
- Create (vault, not the repo): `~/.claude/projects/Discover Nearby/benchmarks/2026-10-07-m2.md`
- Screenshots: `$S/bench/` where `S` is the session scratchpad
  (`/private/tmp/claude-501/-Users-admin-AndroidStudioProjects-DiscoverNearby/<session>/scratchpad`)

**Interfaces:**
- Produces: the vault note with one section per cell, `### <Location> × <Category>`, listing rows as
  `<n>. <name> — <distance> km` plus a `State:` line (Content, Empty, Error, Permission), and the setup block.

- [ ] **Step 1: Prepare the emulator and the app**

```bash
adb -s emulator-5554 get-state
adb -s emulator-5554 shell am get-current-user
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug --console=plain -q
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4
grep -c '^here.apiKey=.' local.properties
git log --oneline -1
```

Expected:
- `device`, then `10`;
- the install succeeds, from `main` with DN-M2-001 merged;
- `1`, so the key is set and the app serves live HERE data.

If the emulator isn't running, start `AAOS_AOSP_33_userdebug` with
`$HOME/Library/Android/sdk/emulator/emulator -avd AAOS_AOSP_33_userdebug` in the background and wait for
`sys.boot_completed` to read `1`.

- [ ] **Step 2: For each location, set it and check the app reads it**

Locations, as `lon lat` (docs/04 §2):
- A Greystones: `-6.0633 53.1440`
- B Dublin: `-6.2603 53.3498`
- C Galway: `-9.0568 53.2707`

For each location:

```bash
adb -s emulator-5554 emu geo fix <lon> <lat>; sleep 3; adb -s emulator-5554 emu geo fix <lon> <lat>
adb -s emulator-5554 shell am start -S -n com.kanyandula.discovernearby/.ui.MainActivity
sleep 12
```

Location check, done once per location after its Coffee cell:
- the Coffee rows are a few hundred metres to a few km away;
- the names belong to that town (no Greystones names in Dublin).

If they don't, send `geo fix` twice again, relaunch, and redo the location.

- [ ] **Step 3: Capture every category at that location**

Run this through `bash -c` (zsh doesn't split `$c`). Tiles are at Coffee (196,268), Food (512,268), Outdoors
(828,268), Family (196,512), Scenic (512,512) and Explore (828,512):

```bash
bash -c '
D="$1"; L="$2"; mkdir -p "$D"
for c in "coffee 196 268" "food 512 268" "outdoors 828 268" "family 196 512" "scenic 512 512" "explore 828 512"; do
  set -- $c
  adb -s emulator-5554 shell input tap "$2" "$3"; sleep 7
  adb -s emulator-5554 exec-out screencap -p > "$D/$L-$1-top.png"
  adb -s emulator-5554 shell input swipe 512 560 512 250 400; sleep 1
  adb -s emulator-5554 shell input swipe 512 560 512 250 400; sleep 2
  adb -s emulator-5554 exec-out screencap -p > "$D/$L-$1-end.png"
  adb -s emulator-5554 shell input keyevent 4; sleep 2
done' _ "$S/bench" <a|b|c>
```

Expected: 12 screenshots per location.

- [ ] **Step 4: Read each cell into the vault note**

For each cell, read `-top.png` and `-end.png`:
- **Rows, in order:** take the top screenshot's rows first, then the end screenshot's rows that come after them. A
  row in both shots has the same name and distance, so count it once. Record the name and distance.
- **State:** Content, Empty, Error or Permission.
- **Cell still loading, or Error/Timeout:** reopen that category once (tap, wait 10 s, both screenshots). If it
  still fails, record the state and "retried once".

Expected: 18 sections, each with 0 to 5 rows and a State line.

- [ ] **Step 5: Write the setup block and the grid**

At the top of the vault note:
- frontmatter: `type: benchmark`, `ticket: DN-M2-002`, `date: 2026-10-07`, `status: awaiting-product-review`;
- the setup: `AAOS_AOSP_33_userdebug`, user 10, Park, live HERE key, app at `main` `<sha>`, and the ranking rules
  in force (DN-M2-001);
- the docs/04 grid, Coffee to Explore × A–C, with each cell's row count.

---

### Task 2: Suggest the judgments

**Files:**
- Modify (vault): `~/.claude/projects/Discover Nearby/benchmarks/2026-10-07-m2.md`

**Interfaces:**
- Consumes: Task 1's cell sections.
- Produces:
  - every row as `<n>. <name> — <distance> km — suggested: ACCEPT|REJECT|UNSURE (<reason, ≤ 6 words>)`;
  - per cell, the four docs/04 lines, each ending `(suggested)`;
  - a `Product:` line left empty for the Product Lead.

- [ ] **Step 1: Apply the rubric to every row**

Relevance, by category:
- **Coffee:** a café, coffee shop or tea room. A pub, bar or restaurant without coffee in its name doesn't count
  (ADR-001).
- **Food:** a restaurant, fast casual or takeaway (docs/01 §10). A pub serving food counts.
- **Outdoors:** a park, trail, forest, beach, hiking area or outdoor attraction (docs/01 §10). An indoor venue
  doesn't count.
- **Family:**
  - counts: a playground, zoo or aquarium, farm park, amusement, theme or water park, play centre, family arcade,
    karting or children's museum;
  - doesn't count: a gaming arcade, casino, spa or general sports club (ADR-001).
- **Scenic:** a viewpoint, scenic point, peak, cliff walk, coastal lookout, waterfall or landmark worth looking at
  (ADR-001, docs/01 §10). A restaurant or other business doesn't count.
- **Explore:** a tourist attraction, museum, landmark, heritage site or unusual local attraction (docs/01 §10).

Suggestions:
- **ACCEPT:** the name shows a matching kind.
- **REJECT:** the name shows a non-matching kind.
- **UNSURE:** the name doesn't show its kind. Never guess.

- [ ] **Step 2: Suggest the per-cell rules**

- **Top result relevant?** YES if row 1 is ACCEPT, NO if REJECT, UNSURE otherwise.
- **2 of top 3 useful?** YES or NO, or "n/a (sparse)" when fewer than three rows are ACCEPT or UNSURE.
- **Obviously wrong #1?** YES only if row 1 is REJECT.
- **Notes:** sparse data, a pattern (for example "two beaches"), or a DN-M2-001 question this cell answers:
  nearness against category, Family's ceiling, miscategorised places.

- [ ] **Step 3: Hand the note to the Product Lead**

Tell the user where the note is, and summarise:
- the suggested failures (wrong #1, under 2 of top 3) and the UNSURE count;
- that every suggestion is theirs to change.

**Stop here** until the Product Lead has reviewed the note and set its frontmatter `status: signed-off` (or asked
for changes).

---

### Task 3: The name-free record and the follow-up

**Files:**
- Create: `docs/benchmarks/2026-10-07-m2.md`
- Modify: `docs/04-discover-nearby-test-demo-plan.md` (§4, after "The benchmark evaluates the concept…")
- Modify: `CLAUDE.md` ("Current state" and "Next")
- Create (vault, only if a cell failed): `~/.claude/projects/Discover Nearby/tickets/DN-M2-003-ranking-tuning.md`

**Interfaces:**
- Consumes: the signed-off vault note, which is the Product Lead's judgments, not the suggestions.

- [ ] **Step 1: Write the repo summary**

`docs/benchmarks/2026-10-07-m2.md`:
- **Header:** title, date, ticket, and a note that the names are kept privately (public repo; HERE content), so this
  record has verdicts only.
- **Setup:** as in the vault note, without coordinates.
- **Grid:** per cell `<accepted>/<shown>`, plus marks: ✓ for the top result relevant, ✗ for a wrong #1, S for
  sparse.
- **Per cell:** one line with the four verdicts, plus a name-free note, for example "#4 is a restaurant filed under
  Scenic Point".
- **Findings:** what the benchmark says about the DN-M2-001 questions:
  - nearness against category;
  - Family's ceiling;
  - miscategorised places.
- **Sign-off:** "Product Lead | ACCEPT/REJECT | 2026-10-07", from the vault note's sign-off.

- [ ] **Step 2: Point docs/04 and CLAUDE.md at it**

In docs/04 §4, after `The benchmark evaluates the concept. It is not a statistically rigorous ranking experiment.`,
add a blank line and:

```markdown
**M2 run (DN-M2-002, 2026-10-07):** `docs/benchmarks/2026-10-07-m2.md`. Place names are kept privately (public repo,
HERE content); the record holds verdicts, counts and notes.
```

In CLAUDE.md:
- **"Current state":** after the `- **Ranking (DN-M2-001):**` bullet, add one bullet:
  `- **Relevance benchmark (DN-M2-002):** \`docs/benchmarks/2026-10-07-m2.md\`; <signed off | not signed off>, <N>/18
  cells pass.`
- **"Next":** set it to the follow-up ticket if one is created, otherwise M3/M4 per docs/05.

- [ ] **Step 3: The name-leak check**

Extract every recorded name from the vault note and search the branch's diff for each one:

```bash
N="$HOME/.claude/projects/Discover Nearby/benchmarks/2026-10-07-m2.md"
grep -E '^[0-9]\. ' "$N" | sed -E 's/^[0-9]\. (.*) — [0-9.]+ km.*/\1/' | sort -u > "$S/bench/names.txt"
wc -l < "$S/bench/names.txt"
git diff main -- . | grep -c -F -f "$S/bench/names.txt"
git log main..HEAD --format=%B | grep -c -F -f "$S/bench/names.txt"
```

Expected:
- the name count equals the number of distinct rows recorded;
- `0` matches in the diff;
- `0` matches in the commit messages.

Generic words that are also names (a row named after a common noun) can match ordinary text. If any line matches, read it.
Reword a real leak; a generic word is fine.

- [ ] **Step 4: Follow-up ticket, only if a cell failed**

If the Product Lead failed any cell (wrong #1, or under 2 of top 3 where three credible places exist), create
`tickets/DN-M2-003-ranking-tuning.md` in the vault:
- frontmatter: `status: backlog`, `priority: P1`, `milestone: M2`, `depends_on: [DN-M2-002]`;
- one acceptance criterion per failing pattern, written by kind, not by name;
- a re-run of the failed cells.

Add it to BACKLOG.md.

- [ ] **Step 5: Commit**

```bash
git add docs/benchmarks/2026-10-07-m2.md docs/04-discover-nearby-test-demo-plan.md CLAUDE.md
git commit -m "Record the M2 relevance benchmark"
```

---

## After the tasks

- The final whole-branch review (executing-plans): docs only, so it checks accuracy against the vault note and for
  leaks.
- PR with `pr-description`: DN-M2-002, its acceptance criteria, the pass count, and the sign-off.
- Step 6 after merge:
  - verify MERGED in its own call;
  - DN-M2-002 `done`;
  - NOW.md and BACKLOG.md;
  - M2 exits if signed off.
