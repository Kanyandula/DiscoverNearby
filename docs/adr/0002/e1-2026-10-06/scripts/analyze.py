"""Score E1 runs (DN-SP-003) from runs/<id>/steps.txt. Usage: python3 analyze.py [prefix]

Per run: complete (stub received, hand-off on top, Back x3 lands on Discover and the turn after it moves),
ignored turns (bounds unchanged, not at a list end), longest run of ignored turns (trap if >= 3; keys between
turns don't reset it), host-focus steps, and "select fired Back" (Details expected, Discover shown).
"""
import os
import re
import sys

HOST = "Rect(0, 76 - 1024, 672)"
NAVIGATE = "Rect(576, 536 - 976, 624)"
TILES = {
    "Rect(48, 156 - 344, 380)": "Coffee", "Rect(364, 156 - 660, 380)": "Food",
    "Rect(680, 156 - 976, 380)": "Outdoors", "Rect(48, 400 - 344, 624)": "Family",
    "Rect(364, 400 - 660, 624)": "Scenic", "Rect(680, 400 - 976, 624)": "Explore",
}
APP, STUB = "com.kanyandula.discovernearby", "com.kanyandula.stubnavigation"
TURNS = ["discover", "recs-1", "recs-2", "details-1", "details-2", "back-1-turn", "back-2-turn", "back-3-turn"]
STEP = re.compile(r"^\d\d (\S+)\s+(Rect\([^)]*\)|none)\s*(\S*)")


def read(run_dir):
    steps, meta = {}, {}
    for line in open(os.path.join(run_dir, "steps.txt")):
        m = STEP.match(line)
        if m:
            steps[m.group(1)] = (m.group(2), m.group(3))
        for key, pat in (("wait", r"uiautomator wait: ([\d.]+)"), ("first", r"first turn at: ([\d.]+)"),
                         ("stub", r"stub received: (\d+)")):
            k = re.search(pat, line)
            if k:
                meta[key] = k.group(1)
    return steps, meta


def score(steps, meta):
    order = list(steps)
    ignored, streak, longest = [], 0, 0
    for name in TURNS:
        if name not in steps:
            continue
        prev = steps[order[order.index(name) - 1]][0]
        cur = steps[name][0]
        at_end = cur == NAVIGATE and prev == NAVIGATE and name in ("details-2", "back-1-turn")
        # Ruling: a Coffee reading at start is the previous run's stale node (no ring is drawn), so the
        # first turn landing on Coffee is a visible move, not an ignored turn.
        at_end = at_end or (name == "discover" and cur == prev == "Rect(48, 156 - 344, 380)")
        if cur == prev and not at_end:
            ignored.append(name)
            streak += 1
            longest = max(longest, streak)
        elif cur != prev:
            streak = 0
    end, after = steps.get("back-3", ("none", "")), steps.get("back-3-turn", ("none", ""))
    on_discover = after[1] == APP and after[0] in TILES and after[0] != end[0]
    handed_off = steps.get("navigate", ("", ""))[1] == STUB and int(meta.get("stub", 0)) >= 1
    fired_back = steps.get("details-open", ("", ""))[0] in TILES
    host = [n for n, (b, _) in steps.items() if b == HOST]
    # A select sent while the service holds the ComposeView host acts on nothing the driver can see.
    misfires = [s for s in ("recs-open", "details-open", "navigate")
                if s in steps and steps[order[order.index(s) - 1]][0] == HOST]
    complete = handed_off and on_discover
    ok = complete and longest < 3 and not fired_back and not misfires
    return dict(complete=complete, ignored=ignored, longest=longest, host=host, fired_back=fired_back, misfires=misfires, ok=ok)


def main(prefix=""):
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "runs")
    tally = {}
    for run in sorted(r for r in os.listdir(base) if r.startswith(prefix)):
        steps, meta = read(os.path.join(base, run))
        s = score(steps, meta)
        arm = run.split("-")[-1]
        boot = run.split("-")[0]
        tally.setdefault((boot, arm), []).append(s["ok"])
        print(f"{run:12} {'PASS' if s['ok'] else 'FAIL'}  complete={s['complete']!s:5} "
              f"first={meta.get('first', '?'):>5}s wait={meta.get('wait', '-'):>5}  ignored={','.join(s['ignored']) or '-'}"
              f"  longest={s['longest']}  host={','.join(s['host']) or '-'}{'  SELECT-WENT-BACK' if s['fired_back'] else ''}{'  misfire=' + ','.join(s['misfires']) if s['misfires'] else ''}")
    print()
    for (boot, arm), oks in sorted(tally.items()):
        print(f"{boot} {arm}: {sum(oks)} of {len(oks)} pass")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "")
