# DN-SP-001 Phase 2: Live Provider Matrix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the 27 live queries (TomTom, HERE, Geoapify × Coffee, Family, Scenic × Greystones, Dublin, Galway), judge
the data, and record exactly one ADR-001 outcome with its sign-offs.

**Architecture:** A small stdlib-only Python script, `tools/provider-eval/evaluate.py`, builds each provider's REST
request, times it, and reduces the response to derived metrics. Raw responses and a "local view" (names, categories,
distances) stay in the git-ignored `tools/provider-eval/out/`, because the repo is public. ADR-001 gets the
metrics, the Product Lead's verdicts and the outcome. No app code changes.

**Tech Stack:** Python 3.14 (stdlib: `urllib`, `json`, `unittest`), the providers' REST APIs as documented in ADR-001
([T2], [H2], [G1]).

**Spec:** the ticket `~/.claude/projects/Discover Nearby/tickets/DN-SP-001-provider-spike.md` (acceptance criteria and
the 2026-10-07 gate decision) and `docs/adr/0001-poi-provider.md` (template, evidence, documented category codes).

## Global Constraints

- **Gate (user, Product Lead role, 2026-10-07):** "evaluation confirmed, evaluate now, Legal before production".
- **Live calls are limited to:**
  - the 27 matrix cells;
  - one TomTom `poiCategories` lookup per `check` run;
  - after a fix, at most 2 re-runs of a failed check cell per provider.

  No other live provider calls.
- **Keys:** never print, log, store in a tracked file or commit one. Error bodies are redacted before they are saved
  or printed. URLs hold keys, so URLs are never printed or saved.
- **Public repo (`Kanyandula/DiscoverNearby` is public):** no provider content is committed: no names, place IDs,
  coordinates, raw responses or sample values. ADR-001 gets derived metrics and verdicts only.
- **Raw output:** only in `tools/provider-eval/out/` (git-ignored). It is deleted once the sign-offs are recorded.
- **Licence language:** engineering does not interpret it. Quote it, link it, route it to Legal (docs/05 §4).
- **Outcome:** ADR-001 records exactly one: **Selected** · **Provisionally selected, pending licensing confirmation** ·
  **No viable provider**. With Legal pending, "Selected" is not available. A provisional selection needs explicit
  Product risk acceptance (Product Lead, role only, with the date).
- **Integration:** REST only, no provider SDK (ADR-001 Context). No changes to `app/`.
- **Time-box:** 2 working days remain (the clock restarted on 2026-10-07).
- **Attribution:** none, in commits or the PR.

## Review Focus

1. **A key leaks.** A provider echoes the request (and its key) in an error body, and it is saved or printed. Expect
   `[key]` in its place. Pinned by `RunTest.test_a_key_echoed_in_an_error_is_redacted_before_saving` (Task 1), plus
   the key scan in Task 2, Step 4.
2. **Provider content reaches the public repo through the metrics.** Expect metric rows and the table to hold no
   names or IDs. Pinned by `RunTest.test_metric_rows_hold_no_place_content` (Task 1), plus the `git check-ignore`
   step in Task 1.
3. **A provider's request is malformed** (for example, HERE rejects `at` together with `in=circle`). Expect its other
   8 cells not to be spent. Pinned by `RunTest.test_a_provider_whose_check_failed_is_not_queried_further` (Task 1).
4. **The matrix is run twice** (a retry after a partial failure). Expect no cell that already returned 200 to be
   queried again. Pinned by `RunTest.test_a_cell_that_returned_200_is_never_queried_again` (Task 1).
5. **A network failure or timeout.** Expect it recorded as HTTP 0 with no results, without crashing the run. Pinned
   by `RunTest.test_a_network_failure_is_recorded_not_raised` (Task 1).

---

## File structure

| File | Change | Responsibility |
| --- | --- | --- |
| `tools/provider-eval/evaluate.py` | Create | requests, timing, metrics, local view, table |
| `tools/provider-eval/test_evaluate.py` | Create | offline tests: no network, no real keys |
| `.gitignore` | Modify | `tools/provider-eval/out/` |
| `docs/adr/0001-poi-provider.md` | Modify | matrix, coverage, fields, mapping, V6a–c, outcome, sign-offs |
| `docs/05-discover-nearby-delivery-plan.md` | Modify | V4 row, current gates |
| `CLAUDE.md` | Modify | "Next" and the tool line |

---

### Task 1: The evaluation script, tested offline

**Files:**
- Create: `tools/provider-eval/evaluate.py`
- Create: `tools/provider-eval/test_evaluate.py`
- Modify: `.gitignore` (append one line)

**Interfaces:**
- Consumes: nothing.
- Produces:
  - **Commands:**
    - `python3 tools/provider-eval/evaluate.py check|matrix|table`;
    - `out/metrics.json`, a dict keyed `"<provider>/<Category>/<Location>"` of rows with exactly `METRIC_KEYS`;
    - `out/raw/*.json`;
    - `out/view/*.md`.
  - **Functions:**
    - `request_url(provider, category, location, key) -> str`;
    - `places(provider, body) -> list[Place]`;
    - `metrics(found) -> dict`;
    - `run(cells, keys, done, get=fetch, out=OUT) -> dict`;
    - `table(results) -> str`;
    - `all_cells()`.

- [ ] **Step 1: Write the failing tests**

Create `tools/provider-eval/test_evaluate.py`:

```python
"""Offline checks for evaluate.py: no network, no real keys.

python3 -m unittest discover -s tools/provider-eval -v
"""
import json
import tempfile
import unittest
import urllib.parse
from pathlib import Path

import evaluate

KEYS = {"tomtom": "TT-KEY-123", "here": "HERE-KEY-456", "geoapify": "GEO-KEY-789"}
EMPTY = {"results": [], "items": [], "features": []}


def query(url):
    return dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(url).query))


class RequestUrlTest(unittest.TestCase):
    def test_tomtom_nearby_search_uses_the_category_radius_and_ids(self):
        q = query(evaluate.request_url("tomtom", "Family", "Galway", "k"))
        self.assertEqual(("53.2707", "-9.0568", "15000"), (q["lat"], q["lon"], q["radius"]))
        self.assertEqual("9927,9902,9378", q["categorySet"])
        self.assertEqual("nextSevenDays", q["openingHours"])
        self.assertEqual("k", q["key"])

    def test_here_browse_searches_a_circle_around_the_location(self):
        q = query(evaluate.request_url("here", "Scenic", "Dublin", "k"))
        self.assertEqual("53.3498,-6.2603", q["at"])
        self.assertEqual("circle:53.3498,-6.2603;r=30000", q["in"])
        self.assertEqual("550-5510-0242,350-3510-0238", q["categories"])
        self.assertEqual("k", q["apiKey"])

    def test_geoapify_puts_longitude_first(self):
        q = query(evaluate.request_url("geoapify", "Coffee", "Greystones", "k"))
        self.assertEqual("circle:-6.0633,53.144,5000", q["filter"])
        self.assertEqual("proximity:-6.0633,53.144", q["bias"])
        self.assertEqual("catering.cafe", q["categories"])


class PlacesTest(unittest.TestCase):
    def test_tomtom_results(self):
        body = {"results": [
            {"dist": 120.4, "poi": {"name": "Cafe A", "categories": ["café"], "phone": "1", "openingHours": {}}},
            {"dist": 300.0, "poi": {"name": "Pub B", "categories": ["pub"], "url": "u"}},
        ]}
        found = evaluate.places("tomtom", body)
        self.assertEqual(["Cafe A", "Pub B"], [p.name for p in found])
        self.assertEqual(
            {"results": 2, "hours": 1, "phone": 1, "web": 1, "amenities": 0, "nearest_m": 120, "median_m": 210},
            evaluate.metrics(found),
        )

    def test_here_items(self):
        body = {"items": [{
            "title": "Viewpoint", "distance": 900, "categories": [{"name": "Scenic Point"}],
            "contacts": [{"phone": [{"value": "1"}], "www": [{"value": "w"}]}], "openingHours": [{}],
        }]}
        found = evaluate.places("here", body)
        self.assertEqual(["Scenic Point"], found[0].categories)
        self.assertEqual(
            {"results": 1, "hours": 1, "phone": 1, "web": 1, "amenities": 0, "nearest_m": 900, "median_m": 900},
            evaluate.metrics(found),
        )

    def test_geoapify_features(self):
        body = {"features": [
            {"properties": {"name": "Playground", "categories": ["leisure.playground"], "distance": 50,
                            "facilities": {"wheelchair": True}}},
            {"properties": {"categories": ["entertainment.zoo"], "distance": 70, "website": "w",
                            "contact": {"phone": "1"}, "opening_hours": "Mo-Su 09:00-17:00"}},
        ]}
        found = evaluate.places("geoapify", body)
        self.assertEqual(["Playground", ""], [p.name for p in found])
        self.assertEqual(
            {"results": 2, "hours": 1, "phone": 1, "web": 1, "amenities": 1, "nearest_m": 50, "median_m": 60},
            evaluate.metrics(found),
        )

    def test_no_results_have_no_distances(self):
        self.assertEqual(
            {"results": 0, "hours": 0, "phone": 0, "web": 0, "amenities": 0, "nearest_m": None, "median_m": None},
            evaluate.metrics([]),
        )


class KeysTest(unittest.TestCase):
    def write(self, text):
        path = Path(tempfile.mkdtemp()) / "local.properties"
        path.write_text(text)
        return path

    def test_reads_the_three_keys(self):
        path = self.write("sdk.dir=/x\ntomtom.apiKey=a\nhere.apiKey=b\ngeoapify.apiKey=c\n")
        self.assertEqual({"tomtom": "a", "here": "b", "geoapify": "c"}, evaluate.read_keys(path))

    def test_a_missing_or_empty_key_stops_the_run(self):
        path = self.write("tomtom.apiKey=a\nhere.apiKey=\n")
        with self.assertRaises(SystemExit) as stop:
            evaluate.read_keys(path)
        self.assertIn("here, geoapify", str(stop.exception))


class RunTest(unittest.TestCase):
    def setUp(self):
        self.out = Path(tempfile.mkdtemp())
        self.calls = []

    def fake(self, status=200, body=None, failing_host=None):
        def get(url):
            self.calls.append(url)
            if failing_host and failing_host in url:
                return 400, {"error": "bad request"}
            return status, body if body is not None else EMPTY
        return get

    # Review Focus 4
    def test_a_cell_that_returned_200_is_never_queried_again(self):
        cells = [("geoapify", "Coffee", "Greystones")]
        done = evaluate.run(cells, KEYS, {}, get=self.fake(), out=self.out)
        evaluate.run(cells, KEYS, done, get=self.fake(), out=self.out)
        self.assertEqual(1, len(self.calls))

    # Review Focus 3: the check cell is retried once by the matrix run; the other 8 HERE cells are not spent.
    def test_a_provider_whose_check_failed_is_not_queried_further(self):
        done = evaluate.run([("here", "Coffee", "Greystones")], KEYS, {}, get=self.fake(400, {"error": "x"}),
                            out=self.out)
        evaluate.run(evaluate.all_cells(), KEYS, done, get=self.fake(failing_host="hereapi"), out=self.out)
        self.assertEqual(2, len([u for u in self.calls if "hereapi" in u]))
        self.assertEqual(18, len([u for u in self.calls if "hereapi" not in u]))

    # Review Focus 1
    def test_a_key_echoed_in_an_error_is_redacted_before_saving(self):
        echo = {"error": "Invalid request: apiKey=GEO-KEY-789 rejected"}
        done = evaluate.run([("geoapify", "Coffee", "Greystones")], KEYS, {}, get=self.fake(401, echo),
                            out=self.out)
        saved = "".join(p.read_text() for p in self.out.rglob("*") if p.is_file())
        self.assertNotIn("GEO-KEY-789", saved + json.dumps(done))
        self.assertIn("[key]", saved)

    # Review Focus 2
    def test_metric_rows_hold_no_place_content(self):
        body = {"features": [{"properties": {"name": "Secret Cafe", "categories": ["catering.cafe"],
                                             "distance": 10, "place_id": "id1", "lat": 1, "lon": 2}}]}
        done = evaluate.run([("geoapify", "Coffee", "Greystones")], KEYS, {}, get=self.fake(200, body),
                            out=self.out)
        self.assertEqual(set(evaluate.METRIC_KEYS), set(done["geoapify/Coffee/Greystones"]))
        self.assertNotIn("Secret Cafe", evaluate.table(done))
        self.assertIn("Secret Cafe", (self.out / "view" / "geoapify-Coffee-Greystones.md").read_text())

    # Review Focus 5
    def test_a_network_failure_is_recorded_not_raised(self):
        done = evaluate.run([("tomtom", "Coffee", "Greystones")], KEYS, {}, get=self.fake(0, {"error": "URLError"}),
                            out=self.out)
        self.assertEqual((0, 0), (done["tomtom/Coffee/Greystones"]["status"], done["tomtom/Coffee/Greystones"]["results"]))


class TableTest(unittest.TestCase):
    def test_cells_not_run_are_marked(self):
        text = evaluate.table({})
        self.assertEqual(2 + 27, len(text.splitlines()))
        self.assertIn("| tomtom | Coffee | Greystones | not run |", text)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run them and see them fail**

Run: `python3 -m unittest discover -s tools/provider-eval -v`
Expected: ERROR, `ModuleNotFoundError: No module named 'evaluate'`.

- [ ] **Step 3: Write the script**

Create `tools/provider-eval/evaluate.py`:

```python
#!/usr/bin/env python3
"""DN-SP-001 phase 2: the 3 x 3 provider matrix for ADR-001 (docs/adr/0001-poi-provider.md).

Reads the dev-only keys from local.properties and never prints them. Raw responses and the local view (names,
categories, distances) go to tools/provider-eval/out/, which is git-ignored: the repo is public, so only derived
metrics leave this machine.

  python3 tools/provider-eval/evaluate.py check    # one query per provider (Coffee, Greystones), TomTom IDs
  python3 tools/provider-eval/evaluate.py matrix   # every other cell; a cell that returned 200 is never re-run
  python3 tools/provider-eval/evaluate.py table    # the metrics table from out/metrics.json, no calls
"""
from __future__ import annotations

import json
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import namedtuple
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
REPO = TOOL_DIR.parents[1]
OUT = TOOL_DIR / "out"
LIMIT = 10
TIMEOUT_S = 10
PROVIDERS = ("tomtom", "here", "geoapify")
CATEGORY_NAMES = ("Coffee", "Family", "Scenic")
LOCATIONS = {  # TestLocation.kt (docs/03 §7)
    "Greystones": (53.1440, -6.0633),
    "Dublin": (53.3498, -6.2603),
    "Galway": (53.2707, -9.0568),
}
RADIUS_M = {"Coffee": 5_000, "Family": 15_000, "Scenic": 30_000}  # CategoryConfigs
# ADR-001 "Candidate category codes (documented)". `check` confirms the TomTom IDs against poiCategories.
CATEGORIES = {
    "tomtom": {"Coffee": "9376", "Family": "9927,9902,9378", "Scenic": "7337"},
    "here": {
        "Coffee": "100-1100",
        "Family": "550-5520-0208,550-5520-0207,550-5520-0357,300-3100-0027",
        "Scenic": "550-5510-0242,350-3510-0238",
    },
    "geoapify": {
        "Coffee": "catering.cafe",
        "Family": "leisure.playground,entertainment.zoo,entertainment.theme_park",
        "Scenic": "tourism.attraction.viewpoint,natural.mountain.peak",
    },
}
TOMTOM_CODES = {
    "9376": "CAFE_PUB",
    "9927": "ZOOS_ARBORETA_BOTANICAL_GARDEN",
    "9902": "AMUSEMENT_PARK",
    "9378": "LEISURE_CENTER",
    "7337": "SCENIC_PANORAMIC_VIEW",
}
CHECK_CELL = ("Coffee", "Greystones")
METRIC_KEYS = (
    "provider", "category", "location", "status", "ms",
    "results", "hours", "phone", "web", "amenities", "nearest_m", "median_m",
)

Place = namedtuple("Place", "name categories distance has_hours has_phone has_web has_amenities")


def read_keys(path: Path) -> dict[str, str]:
    keys = {}
    for line in path.read_text().splitlines():
        name, sep, value = (part.strip() for part in line.partition("="))
        provider = name.removesuffix(".apiKey")
        if sep and name.endswith(".apiKey") and provider in PROVIDERS and value:
            keys[provider] = value
    missing = [p for p in PROVIDERS if p not in keys]
    if missing:
        sys.exit("Missing keys in local.properties: " + ", ".join(missing))
    return keys


def redact(text: str, keys: dict[str, str]) -> str:
    for key in keys.values():
        text = text.replace(key, "[key]")
    return text


def request_url(provider: str, category: str, location: str, key: str) -> str:
    lat, lon = LOCATIONS[location]
    radius = RADIUS_M[category]
    categories = CATEGORIES[provider][category]
    if provider == "tomtom":  # [T2] Nearby Search
        base = "https://api.tomtom.com/search/2/nearbySearch/.json"
        query = {"key": key, "lat": lat, "lon": lon, "radius": radius, "categorySet": categories,
                 "limit": LIMIT, "openingHours": "nextSevenDays"}
    elif provider == "here":  # [H2] Browse
        base = "https://browse.search.hereapi.com/v1/browse"
        query = {"apiKey": key, "at": f"{lat},{lon}", "in": f"circle:{lat},{lon};r={radius}",
                 "categories": categories, "limit": LIMIT}
    else:  # [G1] Places
        base = "https://api.geoapify.com/v2/places"
        query = {"apiKey": key, "categories": categories, "filter": f"circle:{lon},{lat},{radius}",
                 "bias": f"proximity:{lon},{lat}", "limit": LIMIT}
    return base + "?" + urllib.parse.urlencode(query)


def places(provider: str, body: dict) -> list[Place]:
    if provider == "tomtom":
        return [
            Place(r["poi"].get("name", ""), r["poi"].get("categories", []), r.get("dist"),
                  "openingHours" in r["poi"], "phone" in r["poi"], "url" in r["poi"], False)
            for r in body.get("results", []) if "poi" in r
        ]
    if provider == "here":
        return [
            Place(i.get("title", ""), [c.get("name", "") for c in i.get("categories", [])], i.get("distance"),
                  "openingHours" in i, any("phone" in c or "mobile" in c for c in i.get("contacts", [])),
                  any("www" in c for c in i.get("contacts", [])), False)
            for i in body.get("items", [])
        ]
    return [
        Place(p.get("name", ""), p.get("categories", []), p.get("distance"), "opening_hours" in p,
              "phone" in p.get("contact", {}), "website" in p, "facilities" in p)
        for p in (f.get("properties", {}) for f in body.get("features", []))
    ]


def metrics(found: list[Place]) -> dict:
    distances = sorted(p.distance for p in found if p.distance is not None)
    return {
        "results": len(found),
        "hours": sum(p.has_hours for p in found),
        "phone": sum(p.has_phone for p in found),
        "web": sum(p.has_web for p in found),
        "amenities": sum(p.has_amenities for p in found),
        "nearest_m": round(distances[0]) if distances else None,
        "median_m": round(statistics.median(distances)) if distances else None,
    }


def fetch(url: str) -> tuple[int, dict]:
    """GET as JSON. The URL holds a key, so it is never printed or stored."""
    try:
        with urllib.request.urlopen(url, timeout=TIMEOUT_S) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, {"error": error.read().decode("utf-8", "replace")[:500]}
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        return 0, {"error": type(error).__name__}


def cell_id(provider: str, category: str, location: str) -> str:
    return f"{provider}/{category}/{location}"


def all_cells() -> list[tuple[str, str, str]]:
    """Provider by provider, each starting with its check cell (Coffee, Greystones)."""
    return [(p, c, loc) for p in PROVIDERS for c in CATEGORY_NAMES for loc in LOCATIONS]


def save(out: Path, folder: str, cell: str, suffix: str, text: str) -> None:
    path = out / folder / (cell.replace("/", "-") + suffix)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def run(cells, keys: dict[str, str], done: dict, get=fetch, out: Path = OUT) -> dict:
    """Queries each cell that hasn't returned 200; a provider whose check cell hasn't returned 200 is skipped."""
    results = dict(done)
    for provider, category, location in cells:
        cell = cell_id(provider, category, location)
        check = cell_id(provider, *CHECK_CELL)
        if results.get(cell, {}).get("status") == 200:
            continue
        if cell != check and results.get(check, {}).get("status") != 200:
            continue
        started = time.monotonic()
        status, body = get(request_url(provider, category, location, keys[provider]))
        ms = round((time.monotonic() - started) * 1000)
        if "error" in body:
            body = {"error": redact(body["error"], keys)}
        save(out, "raw", cell, ".json", json.dumps(body, indent=1))
        found = places(provider, body) if status == 200 else []
        view = [f"## {cell} (HTTP {status})", ""] + [
            f"{n}. {p.name or '(no name)'} | {', '.join(p.categories)} | {p.distance} m"
            for n, p in enumerate(found, 1)
        ]
        save(out, "view", cell, ".md", "\n".join(view) + "\n")
        results[cell] = {"provider": provider, "category": category, "location": location,
                         "status": status, "ms": ms, **metrics(found)}
    return results


def tomtom_category_names(key: str, get=fetch) -> dict[str, str]:
    status, body = get("https://api.tomtom.com/search/2/poiCategories.json?" + urllib.parse.urlencode({"key": key}))
    names = {str(c.get("id")): c.get("name", "") for c in body.get("poiCategories", [])} if status == 200 else {}
    return {i: f"{code} -> {names.get(i, f'not found (HTTP {status})')}" for i, code in TOMTOM_CODES.items()}


def table(results: dict) -> str:
    rows = [
        "| Provider | Category | Location | HTTP | ms | Results | Hours | Phone | Web | Amenities | Nearest m | Median m |",
        "| " + " | ".join(["---"] * len(METRIC_KEYS)) + " |",
    ]
    for provider, category, location in all_cells():
        row = results.get(cell_id(provider, category, location))
        if row is None:
            rows.append(f"| {provider} | {category} | {location} | not run |" + " |" * (len(METRIC_KEYS) - 4))
        else:
            rows.append("| " + " | ".join("—" if row[k] is None else str(row[k]) for k in METRIC_KEYS) + " |")
    return "\n".join(rows)


def main(argv: list[str]) -> None:
    command = argv[1] if len(argv) > 1 else ""
    metrics_file = OUT / "metrics.json"
    done = json.loads(metrics_file.read_text()) if metrics_file.exists() else {}
    if command == "table":
        print(table(done))
        return
    if command not in ("check", "matrix"):
        sys.exit(__doc__)
    keys = read_keys(REPO / "local.properties")
    if command == "check":
        for category_id, name in tomtom_category_names(keys["tomtom"]).items():
            print(category_id, name)
        cells = [(p, *CHECK_CELL) for p in PROVIDERS]
    else:
        cells = all_cells()
    results = run(cells, keys, done)
    OUT.mkdir(parents=True, exist_ok=True)
    metrics_file.write_text(json.dumps(results, indent=1))
    print(table(results))


if __name__ == "__main__":
    main(sys.argv)
```

- [ ] **Step 4: Run the tests and see them pass**

Run: `python3 -m unittest discover -s tools/provider-eval -v`
Expected: `Ran 15 tests`, `OK`.

- [ ] **Step 5: Keep the output out of git**

Append to `.gitignore`:

```gitignore
# DN-SP-001 provider evaluation output: provider content, never committed (public repo)
tools/provider-eval/out/
```

Run: `git check-ignore -v tools/provider-eval/out/raw/x.json`
Expected: the `.gitignore` line is printed.

- [ ] **Step 6: Commit**

```bash
git add tools/provider-eval/evaluate.py tools/provider-eval/test_evaluate.py .gitignore
git commit -m "Add the DN-SP-001 provider evaluation script"
```

---

### Task 2: The live run

**Files:** none committed. The output goes to `tools/provider-eval/out/` (git-ignored).

**Interfaces:**
- Consumes: Task 1's `check`, `matrix` and `table` commands.
- Produces: `out/metrics.json` (27 rows), `out/view/*.md` (the local view), and the metrics table, which Task 4
  pastes into ADR-001.

- [ ] **Step 1: The check run (3 matrix cells + 1 TomTom lookup)**

Run: `python3 tools/provider-eval/evaluate.py check`
Expected:
- **The five TomTom IDs map to matching names:** `9376 CAFE_PUB -> Café/Pub` (or close),
  `9927 … -> Zoos, Arboreta & Botanical Garden`, `9902 … -> Amusement Park`, `9378 … -> Leisure Centre`,
  `7337 … -> Scenic/Panoramic View`.
- **The table:** `tomtom`, `here` and `geoapify` Coffee/Greystones rows with HTTP 200, and 24 rows "not run".

If an ID maps to an unrelated name, stop. Look up the right ID in the same response, which is saved nowhere, so
re-run `check`. Fix `CATEGORIES`/`TOMTOM_CODES`, and ledger a ruling.

- [ ] **Step 2: If a check row is not 200**

Read the redacted error: `cat tools/provider-eval/out/raw/<provider>-Coffee-Greystones.json`.
- **HTTP 401 or 403 (key rejected):** stop and tell the user. The key is theirs to fix.
- **HTTP 400 (a malformed request):** correct the parameter from the provider's page ([T2], [H2] or [G1] in
  ADR-001). For HERE, try dropping `at` and keeping `in=circle`, or the reverse. Ledger a ruling, then re-run `check`.
  At most 2 re-runs per provider.
- **HTTP 0 (network):** re-run `check` once.

- [ ] **Step 3: The matrix**

Run: `python3 tools/provider-eval/evaluate.py matrix`
Expected: 27 rows, each HTTP 200, apart from a provider stopped at Step 2. `out/metrics.json` holds 27 entries.

- [ ] **Step 4: Prove nothing leaked**

```bash
for k in tomtom here geoapify; do
  v=$(awk -F= -v k="$k.apiKey" '$1==k {sub(/^[^=]*=/, ""); gsub(/[ \t\r]/, ""); print}' local.properties)
  echo "$k: $(grep -rlF -- "$v" tools/provider-eval/out | wc -l | tr -d ' ') files in out/, \
$(git grep -lF -- "$v" | wc -l | tr -d ' ') tracked files"
done
git status --porcelain --ignored tools/provider-eval | head
```

Expected: `0 files in out/, 0 tracked files` for each key, and `out/` listed only as ignored (`!!`).

---

### Task 3: Relevance and the Product Lead's verdicts

**Files:** none committed. The notes stay in `out/`.

**Interfaces:**
- Consumes: `out/view/*.md` and `out/metrics.json` (Task 2).
- Produces: per cell, `relevant` (count) and a verdict, and per provider, the Product sign-off. Task 4 records
  them in ADR-001.

- [ ] **Step 1: Count the relevant results per cell**

Read each `out/view/*.md`, and count the results that fit the category. The rule:
- **Coffee:** a café, coffee shop or tea room. A pub, bar or restaurant without coffee in its name or categories
  does not count.
- **Family:** a playground, zoo, farm park, amusement, theme or water park, or a children's museum. A leisure
  centre counts only if its categories say it serves families or children.
- **Scenic:** a viewpoint, a scenic or panoramic point, a peak, a cliff walk or a coastal lookout. A general tourist
  attraction does not count.

Verdict per cell:
- **Good:** 3 or more relevant results within the radius. The app shows 3–5 recommendations.
- **Thin:** 1–2.
- **Empty:** 0.

Write them to `tools/provider-eval/out/relevance.md` as a table: provider, category, location, relevant/results,
verdict.

- [ ] **Step 2: Show the Product Lead, and ask**

Print the relevance table in the terminal. Point the user to `tools/provider-eval/out/view/` for the names; they
are not published anywhere. Ask, with AskUserQuestion, in one call:
1. Per provider: is the data good enough for Coffee, Family and Scenic (the Product sign-off)? Options: Yes · No · Yes
   for some categories (they say which).
2. Which provider is selected? Give engineering's recommendation first. The recommendation rests on data usefulness
   (the most Good cells, then the fewest Empty) and then API practicality (auth model, open-now data, latency, free
   quota). The licence quotes are shown alongside it, unweighted. Options: each provider that passed, and "No
   viable provider".
3. Does the Product Lead accept the licensing risk until Legal confirms (needed for "Provisionally selected")?
   Options: "Yes, accept the risk" · "No".
4. Who confirms API practicality (the Tech Lead sign-off)? Options: "The user, as Tech Lead" · "Engineering
   assessment only, pending a Tech Lead".

Expected: the answers decide the outcome. "No viable provider" if no provider passed, or if the risk isn't accepted.
Otherwise "Provisionally selected, pending licensing confirmation".

---

### Task 4: ADR-001, docs/05, CLAUDE.md

**Files:**
- Modify: `docs/adr/0001-poi-provider.md`
- Modify: `docs/05-discover-nearby-delivery-plan.md` (V4 row; current-gates paragraph)
- Modify: `CLAUDE.md` (the "Next" paragraph; one line for the tool)

**Interfaces:**
- Consumes: the metrics table (Task 2), and the relevance table and the answers (Task 3).
- Produces: the recorded outcome. After merge, the M1 tickets become `ready` if a provider is provisionally
  selected.

- [ ] **Step 1: Record the matrix**

In ADR-001, replace the "Test matrix" section's body (from "Pending the gate:" to "…attribution requirement,
latency.") with:

```markdown
Run on 2026-10-07 from a development Mac on a home connection (latency is indicative, not in-vehicle), with
dev-only keys, through `tools/provider-eval/evaluate.py`. Limit 10 per query. Radii from `CategoryConfigs` (Coffee
5 km, Family 15 km, Scenic 30 km).

**What is recorded here:** the repo is public, so this ADR holds derived metrics and verdicts only, with no names,
place IDs, coordinates or raw responses. The raw responses and the names were kept locally (git-ignored) for the
judgment and deleted on <date of Step 6>.

**Metrics** (Hours, Phone, Web, Amenities: results with that field; distances in metres):

<the output of `python3 tools/provider-eval/evaluate.py table`>

**Relevance and verdicts** (relevant/results; Good ≥ 3, Thin 1–2, Empty 0):

<the relevance table from Task 3, Step 1, as a Markdown table: provider rows × the 9 cells>
```

- [ ] **Step 2: Coverage rows and the selected provider's sections**

- **The one-sheet's Coffee, Family and Scenic coverage rows:** per provider, replace "Pending the live matrix" with
  `<Good>/3 Good, <Thin> Thin, <Empty> Empty (Greystones · Dublin · Galway: <verdicts>)`.
- **Field availability (selected provider):** fill each row from the metrics and the evidence rows.
  - Rating, Rating count: Unavailable in the endpoint used. Cite the evidence row, e.g. [T2] or [H2] or [G1].
  - Open now: Provided or Derivable from the hours field. Coverage note: `hours` over `results` across the 9 cells.
  - Parking, Toilets, Family information: from the evidence row and the `amenities` count.
  - Travel time from origin: Unavailable in the endpoint (distance only). Note: M1 uses distance.
  - Along-route search: from the one-sheet row.
- **Category mapping, Selected provider:** Coffee, Family and Scenic from `CATEGORIES` in `evaluate.py` (as run).
  Food, Outdoors and Explore come from the documented codes table, marked "not live-tested".
- **V6a, V6b, V6c:** fill from the selected provider's evidence rows, quoting and citing them. In V6b, "May
  responses be recorded as test fixtures?" is "No: none recorded (public repo); revisit with Legal".
- **Known limitations:** one line per Thin or Empty category, plus "latency measured from a dev Mac", plus "licence
  pending Legal".
- **Rejected:** one line per other provider, with the reason (data, or the Product decision). Licence language is
  quoted, not judged.

- [ ] **Step 3: Outcome and sign-offs**

- **Status line:** replace with `**Status:** <outcome> (2026-10-07), from the live matrix; Legal sign-off on provider terms pending before production`.
- **Decision:** replace `**[PROVIDER]**, outcome: **[Selected / Provisionally selected / No viable provider]**` with the
  provider and the outcome.
- **The provisional line:** `If provisionally selected: risk accepted by the Product Lead on 2026-10-07; licensing
  clarification pending from Legal.` If there is no provisional selection, delete that line.
- **Sign-off table:**

| Question | Name | Result | Date |
| --- | --- | --- | --- |
| Data good enough (Product) | Product Lead | <answer 1, per provider> | 2026-10-07 |
| Licence suitable for embedded automotive use (Product/Legal/Business) | Legal | Pending: risk accepted by the Product Lead until then | — |
| API technically workable (Tech Lead) | <answer 4> | <Yes / Yes, with the limits listed> | 2026-10-07 |

- [ ] **Step 4: docs/05 and CLAUDE.md**

**docs/05, V4 row:**
- Status cell, provisional selection: `🟡 **Provisionally selected: <provider>** (ADR-001, 2026-10-07): live matrix done; Legal sign-off on provider terms pending before production`.
- Status cell, no viable provider: `🔴 **No viable provider** (ADR-001, 2026-10-07)`.
- Current-gates paragraph (line 10, and the one at line 357): replace "The provider decision V4 blocks M1" with the
  outcome. With a provisional selection: "V4: <provider> provisionally selected (Legal pending before production);
  M1 can start". Otherwise: "V4: no viable provider; M1 stays blocked".

**CLAUDE.md:**
- Replace the "Next:" paragraph with the outcome and the next step: "M1 (DN-M1-001 REST client) is ready" or
  "M1 stays blocked".
- Add one bullet to "Current state": `- **Provider evaluation (DN-SP-001):** `tools/provider-eval/evaluate.py` (stdlib Python, not in Gradle or CI); output in the git-ignored `out/`; outcome in ADR-001.`

- [ ] **Step 5: Check the docs**

Run:
```bash
awk 'length > 120 {print FILENAME":"NR}' CLAUDE.md
for k in tomtom here geoapify; do
  v=$(awk -F= -v k="$k.apiKey" '$1==k {sub(/^[^=]*=/, ""); gsub(/[ \t\r]/, ""); print}' local.properties)
  git diff main -- . | grep -cF -- "$v"
done
```

Expected: no CLAUDE.md lines printed, and `0` three times. Then read the ADR diff once. It should hold no place
names, IDs or coordinates; only the test-location names (Greystones, Dublin, Galway) may appear.

- [ ] **Step 6: Delete the raw output, then commit**

```bash
rm -rf tools/provider-eval/out
python3 -m unittest discover -s tools/provider-eval
git add docs/adr/0001-poi-provider.md docs/05-discover-nearby-delivery-plan.md CLAUDE.md
git commit -m "Record the live provider matrix and the ADR-001 outcome"
```

Expected: `OK` from the tests. The commit holds the three docs only.

---

## After the tasks

- The final whole-branch review on the most capable model (executing-plans).
- Simplify on `tools/provider-eval/`.
- `./gradlew detekt lintDebug testDebugUnitTest assembleDebug` (nothing in `app/` changed; this confirms it).
- PR with `pr-description`: DN-SP-001 phase 2, the acceptance criteria and the outcome.
- Step 6 after merge:
  - verify MERGED in its own call;
  - ticket: `done` if an outcome is recorded;
  - M1 tickets: `ready` if a provider is provisionally selected;
  - NOW.md and BACKLOG.md.
