"""Score V7 gate journeys (DN-M0-011 re-test) from runs/<id>/steps.txt. Usage: python3 score.py run-id...

Automated: Navigate reached; selection activates (the hand-off reached the stub); Back without losing a turn:
after each Back the service holds an element of the screen now shown (in step with the drawn ring, not the
ComposeView host or a stale node), and the turn moves it to another element of that screen. Visible focus is
judged from the screenshots, not here.

Two readings of "without losing a turn" are printed:
- in step: after Back the service holds an element of the screen shown (not the host or a stale node), and the turn
  moves it to another element of that screen;
- lands right: the turn lands where the drawn ring implies for this journey (Details: Navigate → ccw → header
  Back; Recommendations: the opened row → cw → the row below it; Discover: Family → cw → Scenic).
"""
import os
import re
import sys

HOST = "Rect(0, 76 - 1024, 672)"
NAVIGATE = "Rect(576, 536 - 976, 624)"
HEADER_BACK = re.compile(r"Rect\(48, 156 - 124, 2\d\d\)")
TILES = {
    "Rect(48, 156 - 344, 380)", "Rect(364, 156 - 660, 380)", "Rect(680, 156 - 976, 380)",
    "Rect(48, 400 - 344, 624)", "Rect(364, 400 - 660, 624)", "Rect(680, 400 - 976, 624)",
}


def on_details(b):
    return b == NAVIGATE or bool(HEADER_BACK.fullmatch(b))


def on_recommendations(b):
    return bool(HEADER_BACK.fullmatch(b)) or bool(re.fullmatch(r"Rect\(48, \d+ - 976, \d+\)", b))


def on_discover(b):
    return b in TILES


STEP = re.compile(r"^\d\d (\S+)\s+(Rect\([^)]*\)|none)\s*(\S*)")


def read(run_dir):
    steps, stub = {}, 0
    for line in open(os.path.join(run_dir, "steps.txt")):
        m = STEP.match(line)
        if m:
            steps[m.group(1)] = (m.group(2), m.group(3))
        k = re.search(r"stub received: (\d+)", line)
        if k:
            stub = int(k.group(1))
    return steps, stub


EXPECTED_LANDING = {1: HEADER_BACK.pattern, 2: re.escape("Rect(48, 396 - 976, 532)"), 3: re.escape("Rect(364, 400 - 660, 624)")}


def score(steps, stub):
    reached = any(b == NAVIGATE for n, (b, _) in steps.items() if n.startswith("details-"))
    activated = steps.get("navigate", ("", ""))[1] == "com.kanyandula.stubnavigation" and stub >= 1
    in_step, lands = [], []
    for n, shown in ((1, on_details), (2, on_recommendations), (3, on_discover)):
        after_back, after_turn = steps.get(f"back-{n}", ("none", ""))[0], steps.get(f"back-{n}-turn", ("none", ""))[0]
        in_step.append(shown(after_back) and shown(after_turn) and after_turn != after_back)
        lands.append(bool(re.fullmatch(EXPECTED_LANDING[n], after_turn)))
    return reached, activated, in_step, lands


def main(runs):
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "runs")
    print("| Run | Navigate reached | Selection activates | Back 1 / 2 / 3: in step | Back 1 / 2 / 3: lands right |")
    print("| --- | --- | --- | --- | --- |")
    for run in runs:
        reached, activated, in_step, lands = score(*read(os.path.join(base, run)))
        mark = lambda ok: "✅" if ok else "❌"  # noqa: E731
        print(f"| {run} | {mark(reached)} | {mark(activated)} | {' / '.join(mark(b) for b in in_step)} | "
              f"{' / '.join(mark(b) for b in lands)} |")


if __name__ == "__main__":
    main(sys.argv[1:])
