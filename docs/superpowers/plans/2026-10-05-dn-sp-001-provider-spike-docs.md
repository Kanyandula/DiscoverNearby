# DN-SP-001 Provider Spike — Documentation Phase Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill every part of ADR-001 that public documentation can answer, for TomTom, HERE and Geoapify Places (evaluation only), with a source link on every claim. The live 3 × 3 matrix waits for a gate: dev-only keys in `local.properties` and an assigned licensing owner.

**Architecture:** No code and no accounts.
- **Sources:** each provider's own pages only (developer docs, API reference, category lists, attribution guides, pricing, terms), read with WebSearch/WebFetch. Third-party blogs and AI summaries are not evidence.
- **Where it goes:** findings go straight into `docs/adr/0001-poi-provider.md`:
  - the one-sheet comparison;
  - a documented category-code table;
  - an Evidence section with links and access dates.
- **Licence language is quoted verbatim, never interpreted.** Engineering must not interpret ambiguous licence language itself (docs/05 §4).
- **Still blank:** the sections that need live queries or people (test matrix, field coverage, sign-offs), marked as pending the gate.

**Tech Stack:** WebSearch / WebFetch (load them with ToolSearch first); Markdown.

**Spec:**
- Ticket `~/.claude/projects/Discover Nearby/tickets/DN-SP-001-provider-spike.md`.
- `docs/05-discover-nearby-delivery-plan.md` §4 (Provider Spike), §9 (V4, V6a–c).
- `docs/03-discover-nearby-engineering-implementation-plan.md` §5 ("Evaluate" table, "Output").
- `docs/adr/0001-poi-provider.md` (the template).

## Global Constraints

- **Candidates, in order:** TomTom, HERE, then the named OSM-backed option: **Geoapify Places API, for evaluation only** (user, 2026-10-05).
- **Core integration is REST only.** No provider SDK (docs/03 §5; docs/05 V5).
- **Assess each candidate** for:
  - contractual in-vehicle use;
  - API coverage and data fields;
  - attribution, caching/storage and authentication;
  - quota/cost and production path.
  (ticket AC)
- **No accounts, no terms accepted on the user's behalf, no live API calls** in this phase (user).
- **Live queries wait** until the user provides dev-only keys in `local.properties` *and* a licensing owner is assigned (user).
- **The matrix is 9 queries per provider** (Coffee, Family, Scenic × Greystones, Dublin, Galway), **27 in total** (user; docs/05 §4).
- **Evidence is a signal, not an approval** ("These are signals from public documentation reviewed during planning, not approvals" — docs/05 §4).
- **Sign-offs by role:** Product (data usefulness), Product/Legal/Business (in-vehicle licensing), Tech (API practicality) (user; docs/05 §4).
- **No fixtures** until the terms are confirmed to allow storing responses (ticket AC).
- **ADR-001's status stays "Proposed".** No outcome is recorded in this phase; that needs the live matrix and the sign-offs.
- Never commit on local `main`. No AI attribution.

## Open decisions (recorded here, in the ticket and the PR)

1. **Time-box accounting (proposal).** This documentation phase is day 1 (2026-10-05). Days 2–3 (the live matrix and the outcome) start when the gate opens. The ticket says "3 working days, no extensions", so you decide whether the clock pauses at the gate.
2. **Where evidence lives:** in ADR-001 itself, in a new "Evidence (public documentation)" section. Every row gets a numbered source with link and access date. One file, which the ticket's Verification ("review completed ADR-001 for evidence links") reads directly.
3. **Per-candidate tables.** The template's "Field availability", "Category mapping" and V6a–c tables are for the *selected* provider, so they stay blank until selection. Documented per-candidate values go into the one-sheet comparison (which is already per provider) and one new "Candidate category codes (documented)" table.
4. **No simplify pass on this branch.** The diff is documentation only; there is no code to simplify. The final whole-branch review still runs, focused on sourcing and on any licence interpretation that crept in.
5. **After this phase's PR merges,** the ticket goes to `blocked` (waiting on keys and the licensing owner), not `done`.

## Review Focus

1. **A claim with no source, or sourced from a third party:** every filled cell carries a `[n]` reference to the provider's own page with an access date. Anything not documented reads "Not documented (searched: …)", never a guess. Pinned by Task 4 Step 1's audit.
2. **Licence interpretation creeping in** (writing "permitted" or "allowed" for in-vehicle use): the licence rows hold verbatim quotes plus a link plus "→ Legal". They never say yes or no. Pinned by Task 4 Step 1's audit.
3. **Tier confusion:** the free/evaluation tier and production terms differ. Each cost, quota and terms row names the plan or document it comes from. Pinned by Tasks 1–3, Step 2.
4. **The wrong API product:** TomTom has a Search API and a newer Places API with different capabilities (along-route, per docs/05 §4). Each capability row names the specific API it was read from. Pinned by Task 1, Step 1.
5. **"OSM-based" data licence vs service terms:** for Geoapify, record both the data licence (OpenStreetMap ODbL attribution) and Geoapify's own service terms, separately. Pinned by Task 3, Step 2.

---

## How to research one provider (Tasks 1–3 follow this)

For each candidate, find the provider's own page for each item below and record what it says, with a short verbatim quote where wording matters:

| ADR one-sheet row | What to look for |
| --- | --- |
| AAOS / embedded vehicle use permitted? | Terms, service-specific terms, acceptable-use or restrictions clauses that mention vehicles, in-vehicle, automotive, embedded or navigation. Quote verbatim. Write **"→ Legal"**, never yes/no. |
| Evidence / contractual source | Names, links and versions or dates of the documents quoted. |
| REST POI search | Endpoint names for text/POI search. |
| Nearby / category search | The endpoint taking position + radius + category; the parameter names. |
| Along-route capability | Native along-route POI search: which API, which plan. |
| Coffee / Family / Scenic coverage | Leave blank. This needs live queries (gate). |
| Rating / count | Is a rating and count in the documented response schema? Which API or add-on? |
| Opening hours | Is there an opening-hours or open-now field in the schema? |
| Useful amenities | Parking, toilets, family facilities, as fields or categories. |
| Attribution | Required text or logo and placement rules. |
| Caching restrictions | Storage or caching rules: may responses or place IDs be stored, for how long, and may tests record them as fixtures? |
| Authentication model | Key type, key restriction options, whether a backend proxy is needed. |
| POC cost | Free/evaluation tier quota and price, from the pricing page, with the plan named. |
| Production path plausible | Is there a documented commercial or automotive plan? Write "documented / not documented", with a link. |

Record findings first in a working file in the scratchpad (`$S/sp001-<provider>.md`), each with its source URL and the date. Then transfer them to ADR-001 in the provider's task. Where a page can't be fetched (login wall, script-rendered), record "Not retrievable without an account" and the URL.

---

### Task 0: Start the ticket

- [ ] **Step 1:** In the ticket set `status: in_progress`, `branch: dn-sp-001-provider-spike`. Append to the ticket: "Kickoff 2026-10-05: third candidate = Geoapify Places API (evaluation only). Documentation phase first; live matrix gated on dev-only keys + licensing owner."

- [ ] **Step 2:**

```bash
cd ~/AndroidStudioProjects/DiscoverNearby
git switch main && git pull --ff-only
git switch -c dn-sp-001-provider-spike
git add docs/superpowers/plans/2026-10-05-dn-sp-001-provider-spike-docs.md
git commit -m "Add DN-SP-001 documentation-phase plan"
```

---

### Task 1: TomTom from public documentation

**Files:** `docs/adr/0001-poi-provider.md` (the TomTom column of the one-sheet, the Evidence section, candidate category codes).

- [ ] **Step 1: Capabilities, naming the API product (Review Focus 4).** Find and record, from developer.tomtom.com:
  - The nearby/category search endpoint and parameters (Search API "Nearby Search" / "Category Search", and the Places API equivalent if separate).
  - The POI category list, with codes or IDs for: café/coffee shop; restaurant; park; playground, zoo or amusement/family attractions; scenic/panoramic view or tourist attraction; museum/attraction.
  - Response fields: rating, review count, opening hours, parking, toilets. Name the API and the parameter or add-on if gated.
  - Along-route search: which API, and is it available on which plans.

  Expected: each item has a value or "Not documented", plus a URL.

- [ ] **Step 2: Terms, attribution, caching, auth, cost (Review Focus 2, 3).** Find and record:
  - **Terms:** the developer terms (and any service-specific terms) clauses that mention vehicle, in-vehicle, automotive, embedded or navigation use, quoted verbatim.
  - **Attribution:** required wording or logo.
  - **Storage:** rules on caching or storing responses and IDs.
  - **Keys:** model and restriction options.
  - **Pricing:** free tier quota and price, with the plan named; whether an automotive or commercial path is documented.

  Expected: each with a quote or value and a URL; licence rows end "→ Legal".

- [ ] **Step 3: Write the TomTom column** of the one-sheet comparison in ADR-001, plus the `[n]` sources in the Evidence section (created in this task: a `## Evidence (public documentation)` heading after "One-sheet comparison", with a numbered list `[n] Title — URL — accessed 2026-10-05`). Fill the TomTom column of "Candidate category codes (documented)". That table is created in this task, under `## Category mapping`, with rows Coffee, Food, Outdoors, Family, Scenic, Explore and columns TomTom, HERE, Geoapify.

- [ ] **Step 4: Commit**

```bash
git add docs/adr/0001-poi-provider.md
git commit -m "Record TomTom's documented capabilities and terms in ADR-001"
```

---

### Task 2: HERE from public documentation

**Files:** `docs/adr/0001-poi-provider.md` (the HERE column, Evidence, category codes).

- [ ] **Step 1: Capabilities.** From developer.here.com / here.com docs:
  - The Geocoding & Search API v7 "discover" / "browse" endpoints (or the current equivalent) and their parameters (`at`, `in=circle`, `categories`).
  - The HERE place-category system codes for the same six categories.
  - Response fields: rating, opening hours, contacts or amenities. Name which API, and whether extra (for example "Places premium") content is needed.
  - Along-route search (for example the `route` parameter on browse).

  Expected: values or "Not documented", plus URLs.

- [ ] **Step 2: Terms, attribution, caching, auth, cost.** Find and record:
  - **Terms:** the HERE Platform terms / service terms clauses mentioning vehicle, automotive, embedded or navigation use, quoted verbatim. docs/05 §4 notes the general terms permit app integration "subject to the specific subscription plan and product terms"; find those product terms.
  - **Attribution:** requirements.
  - **Storage:** caching and storage rules.
  - **Auth:** API key vs OAuth, and key restriction options.
  - **Pricing:** freemium/base plan quota and price, with the plan named; any automotive offering.

  Expected: as in Task 1, Step 2.

- [ ] **Step 3: Write the HERE column**, its sources and its category codes in ADR-001.

- [ ] **Step 4: Commit**

```bash
git add docs/adr/0001-poi-provider.md
git commit -m "Record HERE's documented capabilities and terms in ADR-001"
```

---

### Task 3: Geoapify Places from public documentation (evaluation only)

**Files:** `docs/adr/0001-poi-provider.md` (the "OSM option" column renamed "Geoapify Places (OSM-based)", Evidence, category codes).

- [ ] **Step 1: Capabilities.** From apidocs.geoapify.com / geoapify.com:
  - The Places API endpoint and parameters (`categories`, `filter=circle:…`, `bias`).
  - Category codes for the six categories (for example the `catering.*`, `leisure.*`, `tourism.*`, `natural.*` families as documented).
  - Response fields: opening hours, facilities or conditions (toilets, wheelchair, internet…), ratings, if any.
  - Along-route or "filter by route" capability, if documented.

  Expected: values or "Not documented", plus URLs.

- [ ] **Step 2: Terms, data licence vs service terms (Review Focus 5).** Record separately:
  - **Data licence:** the OpenStreetMap ODbL attribution requirement as Geoapify documents it, and any other data sources it names.
  - **Service terms:** Geoapify's own terms clauses on vehicle, automotive, embedded or navigation use, quoted verbatim, ending "→ Legal".
  - **Caching and storage:** rules.
  - **Auth:** key model and restrictions.
  - **Pricing:** the free plan quota and the paid plans, with the plan named.

  Also record that Geoapify is "for evaluation only" (kickoff decision).

- [ ] **Step 3: Write the column** (rename the header "OSM option" to "Geoapify Places (OSM-based)"), its sources and its category codes in ADR-001.

- [ ] **Step 4: Commit**

```bash
git add docs/adr/0001-poi-provider.md
git commit -m "Record Geoapify Places' documented capabilities and terms in ADR-001"
```

---

### Task 4: Audit, gate, close out the documentation phase

**Files:** `docs/adr/0001-poi-provider.md`, `docs/05-discover-nearby-delivery-plan.md` (§9 V4 status cell), the ticket.

- [ ] **Step 1: Sourcing audit (Review Focus 1, 2).** Re-read the ADR and check:
  - Every filled one-sheet cell carries a `[n]` that resolves to a provider-owned URL in Evidence.
  - No licence row says "permitted", "allowed", "yes" or "no". Each holds a quote, a link and "→ Legal".
  - "Not documented" cells name what was searched.

  Run:

```bash
grep -n -iE "permitted|allowed" docs/adr/0001-poi-provider.md
```

Expected: matches only in the template's own question wording ("AAOS / embedded vehicle use permitted?") and in verbatim quotes. Any other match is fixed.

- [ ] **Step 2: Status and gate.** In ADR-001:
  - Set `**Status:** Proposed — documentation phase complete (2026-10-05); live matrix pending dev-only keys and an assigned licensing owner`.
  - Set `**Date:** 2026-10-05 (documentation phase)`.
  - Set the Deciders line to the three roles.
  - Under "Test matrix", add: "Pending the gate: 9 queries per provider (27 in total) once dev-only keys are in `local.properties` and the licensing owner is assigned. No responses are stored until the terms allow."
  - Leave Decision, Field availability, V6a–c, Known limitations, Rejected and Sign-off blank.

- [ ] **Step 3: docs/05 §9 V4 status cell:** "🔴 **Open** — provider spike: documentation phase recorded in ADR-001 (2026-10-05); live matrix and licensing pending keys and a licensing owner".

- [ ] **Step 4:** Commit `Mark ADR-001's documentation phase complete`. Append the ticket's completion notes: what was researched (per provider), what is pending, the gate, and Open decisions 1–5.

- [ ] **Step 5: Close out.**
  1. Push and open a draft PR.
  2. Skip simplify: docs only (Open decision 4).
  3. Do the final whole-branch review (executing-plans), focused on Review Focus 1–5.
  4. Write the PR description with the `pr-description` skill: ticket ID, what is done vs gated, no AI attribution.
  5. Run `gh pr ready` once CI is green.

---

## Phase 2 (gated, planned separately)

Opens when the user has put dev-only keys in `local.properties` (`tomtom.apiKey`, `here.apiKey`, `geoapify.apiKey`) and a licensing owner is assigned.

Then a new plan covers:
- the 27 live queries, using the endpoints and category codes recorded in Tasks 1–3;
- capturing the docs/05 §4 fields and latency per query, without storing raw responses unless the terms allow;
- filling the test matrix and the coverage rows;
- the sign-offs and the single outcome.
