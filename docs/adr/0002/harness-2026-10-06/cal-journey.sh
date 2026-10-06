#!/bin/bash
# Full rotary journey in the CAL probe: launch, 4 turns → Family, select, 3 turns on the list, select,
# 4 turns on details, select (Navigate?), Back ×3. $1 = label for screenshots.
S=$(dirname "$0"); d() { adb -s emulator-5554 shell "$@"; }
f() { "$S/rfocus.sh" | sed -E 's/boundsInScreen: //' | cut -c 1-36; }
top() { d dumpsys activity activities | grep -m1 topResumedActivity | grep -oE 'u10 [^ ]+' | cut -c 5-; }
d am start --user 10 -n com.kanyandula.calprobe/androidx.car.app.activity.CarAppActivity >/dev/null 2>&1; "$S/pause.sh" 25
adb -s emulator-5554 exec-out screencap -p > "$S/cb-$1-0.png"
adb -s emulator-5554 logcat -c
for i in 1 2 3 4; do d cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; echo "grid $i: $(f)"; done
adb -s emulator-5554 exec-out screencap -p > "$S/cb-$1-grid.png"
d cmd car_service inject-key 23 >/dev/null; "$S/pause.sh" 4
for i in 1 2 3; do d cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; echo "list $i: $(f)"; done
adb -s emulator-5554 exec-out screencap -p > "$S/cb-$1-list.png"
d cmd car_service inject-key 23 >/dev/null; "$S/pause.sh" 4
for i in 1 2 3 4; do d cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; echo "details $i: $(f)"; done
adb -s emulator-5554 exec-out screencap -p > "$S/cb-$1-details.png"
d cmd car_service inject-key 23 >/dev/null; "$S/pause.sh" 3
echo "ProbeNav: $(adb -s emulator-5554 logcat -d -s ProbeNav | grep -c 'navigate selected') | top: $(top)"
for b in 1 2 3; do d cmd car_service inject-key 4 >/dev/null; "$S/pause.sh" 3; echo "back $b: $(f) | top: $(top)"; done
adb -s emulator-5554 exec-out screencap -p > "$S/cb-$1-end.png"
