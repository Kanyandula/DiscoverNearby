#!/bin/bash
# Visible focus on an error state's buttons (DN-M0-011 re-test): NETWORK_FAILURE scenario, open Coffee, three turns.
E=$(dirname "$0"); O="$E/../runs/error1"; mkdir -p "$O"
d() { adb -s emulator-5554 shell "$@"; }
foc() {
  d dumpsys activity service com.android.car.rotary/.RotaryService | grep -E '^\s+focusedNode=' \
    | grep -oE 'boundsInScreen: Rect\([^)]*\)' | head -1 | sed 's/boundsInScreen: //'
}
echo "error1 $(date '+%F %T')" > "$O/steps.txt"
d am start -S -n com.kanyandula.discovernearby/.ui.MainActivity --es scenario NETWORK_FAILURE >/dev/null; sleep 14
d cmd car_service inject-rotary -c true >/dev/null; sleep 1
d cmd car_service inject-key 23 >/dev/null; sleep 6
adb -s emulator-5554 exec-out screencap -p > "$O/00-open.png"; echo "00 open $(foc)" >> "$O/steps.txt"
for i in 1 2 3; do
  d cmd car_service inject-rotary -c true >/dev/null; sleep 1
  adb -s emulator-5554 exec-out screencap -p > "$O/0$i-turn.png"; echo "0$i turn $(foc)" >> "$O/steps.txt"
done
