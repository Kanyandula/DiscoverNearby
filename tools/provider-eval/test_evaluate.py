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
            {"results": 2, "hours": 1, "phone": 1, "web": 1, "amenities": None, "nearest_m": 120, "median_m": 210},
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
            {"results": 1, "hours": 1, "phone": 1, "web": 1, "amenities": None, "nearest_m": 900, "median_m": 900},
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

    # Final review: only Geoapify's facilities are inspected, so TomTom and HERE show "not measured", not 0.
    def test_amenities_are_not_measured_for_tomtom_and_here(self):
        body = {"results": [{"dist": 1, "poi": {"name": "A"}}]}
        row = {"provider": "tomtom", "category": "Coffee", "location": "Greystones", "status": 200, "ms": 1,
               **evaluate.metrics(evaluate.places("tomtom", body))}
        self.assertIsNone(row["amenities"])
        self.assertIn("| — |", evaluate.table({"tomtom/Coffee/Greystones": row}))

    def test_no_results_have_no_distances(self):
        self.assertEqual(
            {"results": 0, "hours": 0, "phone": 0, "web": 0, "amenities": None, "nearest_m": None, "median_m": None},
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
