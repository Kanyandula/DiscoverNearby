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
