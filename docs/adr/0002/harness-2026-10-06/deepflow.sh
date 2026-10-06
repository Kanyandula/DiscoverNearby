#!/bin/bash
# Discover → Recommendations → Details and back, rotating on every screen.
S=$(dirname "$0"); r() { adb -s emulator-5554 shell cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; }
k() { adb -s emulator-5554 shell cmd car_service inject-key $1 >/dev/null; "$S/pause.sh" 3; }
f() { "$S/rfocus.sh" | cut -c 1-60; }
"$S/launch.sh" >/dev/null; "$S/waitfor.sh" Coffee 40 >/dev/null; "$S/pause.sh" 2
adb -s emulator-5554 logcat -c
r; echo "discover: $(f)"; k 23; echo "recs open: $(f)"
r; echo "recs 1: $(f)"; r; echo "recs 2: $(f)"; k 23; echo "details open: $(f)"
r; echo "details 1: $(f)"; r; echo "details 2: $(f)"; adb -s emulator-5554 exec-out screencap -p > "$S/$1-details.png"
k 4; echo "back to recs: $(f)"; r; echo "recs turn: $(f)"
k 4; echo "back to discover: $(f)"; r; echo "discover 1: $(f)"; r; echo "discover 2: $(f)"
adb -s emulator-5554 exec-out screencap -p > "$S/$1-discover.png"
echo "events: $(adb -s emulator-5554 logcat -d -s RotaryController:V | grep -c onAccessibilityEvent)"
