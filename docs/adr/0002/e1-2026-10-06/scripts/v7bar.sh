#!/bin/bash
# V7-bar journey (DN-SP-003), scored on DN-M0-011's criteria. $1 = run id, $2 = park|drive.
# A's wait (sleep 14). Discover: cw ×7 (six tiles + end), ccw ×2 → Family, select. Recommendations: cw ×6
# (every element + end), select. Details: cw ×2, ccw, cw, select Navigate (wait for the stub). Back ×3, a turn after each.
E=$(dirname "$0"); O="$E/runs/$1"; mkdir -p "$O"
d() { adb -s emulator-5554 shell "$@"; }
top() { d dumpsys activity activities | grep -m1 topResumedActivity | grep -oE 'u10 [^ /]+' | cut -c 5-; }
foc() {
  d dumpsys activity service com.android.car.rotary/.RotaryService | grep -E '^\s+focusedNode=' \
    | grep -oE 'boundsInScreen: Rect\([^)]*\)' | head -1 | sed 's/boundsInScreen: //'
}
n=0
rec() {
  n=$((n + 1)); local f; f=$(foc)
  printf '%02d %-14s %-28s %s\n' "$n" "$1" "${f:-none}" "$(top)" >> "$O/steps.txt"
  adb -s emulator-5554 exec-out screencap -p > "$O/$(printf %02d "$n")-$1.png"
}
cw() { d cmd car_service inject-rotary -c true >/dev/null; sleep 1; rec "$1"; }
ccw() { d cmd car_service inject-rotary -c false >/dev/null; sleep 1; rec "$1"; }
key() { d cmd car_service inject-key "$1" >/dev/null; sleep 3; rec "$2"; }

echo "v7bar $1 ($2) $(date '+%F %T')" > "$O/steps.txt"
if [ "$2" = drive ]; then
  d cmd car_service inject-vhal-event 0x11400400 8 >/dev/null
  d cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 240 >/dev/null 2>&1 &
  sleep 3
  echo "driving: $(d dumpsys car_service --services CarDrivingStateService | grep 'changed from' | tail -1 | tr -s ' ')" >> "$O/steps.txt"
fi
adb -s emulator-5554 logcat -c
d am start -S -n com.kanyandula.discovernearby/.ui.MainActivity >/dev/null 2>&1; sleep 14
rec start
for i in 1 2 3 4 5 6 7; do cw "discover-cw$i"; done
ccw discover-ccw1; ccw discover-ccw2
key 23 recs-open
for i in 1 2 3 4 5 6; do cw "recs-cw$i"; done
key 23 details-open
cw details-cw1; cw details-cw2; ccw details-ccw1; cw details-cw3
d cmd car_service inject-key 23 >/dev/null
for _ in $(seq 1 15); do sleep 1; [ "$(top)" = com.kanyandula.stubnavigation ] && break; done
rec navigate
key 4 back-1; cw back-1-turn
key 4 back-2; cw back-2-turn
key 4 back-3; cw back-3-turn
echo "stub received: $(adb -s emulator-5554 logcat -d -s StubNav:I | grep -c 'received')" >> "$O/steps.txt"
adb -s emulator-5554 logcat -d -v time -s RotaryController:D > "$O/rotary.log"
if [ "$2" = drive ]; then
  echo "driving at end: $(d dumpsys car_service --services CarDrivingStateService | grep 'changed from' | tail -1 | tr -s ' ')" >> "$O/steps.txt"
  d cmd car_service inject-vhal-event 0x11400400 4 >/dev/null
fi
