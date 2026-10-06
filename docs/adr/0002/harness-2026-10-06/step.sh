#!/bin/bash
# One rotary step: $1 = cw|ccw|select|back|none, $2 = label, $3 = wait seconds (default 6).
# Prints focus, top activity and the first on-screen texts; saves $S/$2.png.
S=$(dirname "$0"); d() { adb -s emulator-5554 shell "$@"; }
case "$1" in
  cw) d cmd car_service inject-rotary -c true >/dev/null ;;
  ccw) d cmd car_service inject-rotary -c false >/dev/null ;;
  select) d cmd car_service inject-key 23 >/dev/null ;;
  back) d cmd car_service inject-key 4 >/dev/null ;;
esac
sleep "${3:-6}"
top=$(d dumpsys activity activities | grep -m1 topResumedActivity | grep -oE 'u10 [^ /]+' | cut -c 5-)
echo "$2: $("$S/rfocus.sh" | sed 's/boundsInScreen: //') | $top"
adb -s emulator-5554 exec-out screencap -p > "$S/$2.png"
