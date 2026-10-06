#!/bin/bash
# Entry test: put rotary focus inside the Compose app (a driver arriving from another app), bring the probe forward,
# wait $1 s with the grid on screen, turn once; report where focus landed. $2 = label.
S=$(dirname "$0"); d() { adb -s emulator-5554 shell "$@"; }
f() { "$S/rfocus.sh" | sed -E 's/boundsInScreen: //' | cut -c 1-36; }
"$S/launch.sh" >/dev/null; sleep 14
d cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1; from=$(f)
d am start --user 10 -n com.kanyandula.calprobe/androidx.car.app.activity.CarAppActivity >/dev/null 2>&1; "$S/pause.sh" "$1"
adb -s emulator-5554 exec-out screencap -p > "$S/sp002-entry-$2.png"
d cmd car_service inject-rotary -c true >/dev/null; "$S/pause.sh" 1
echo "$2: from [$from] wait $1 s → turn 1 [$(f)]"
