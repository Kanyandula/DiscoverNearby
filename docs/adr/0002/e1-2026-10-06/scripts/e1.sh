#!/bin/bash
# E1 run (DN-SP-003). $1 = arm A|B|C|D|E, $2 = run id, $3 = C's wait in s (the preceding B's measured wait + 2).
# Journey at deepflow.sh's pauses: turn, select, turn ×2, select, turn ×2, select Navigate (wait for the stub),
# Back ×3 with a turn after each. Writes $run/steps.txt, one screenshot per step and $run/rotary.log.
E=$(dirname "$0"); O="$E/runs/$2"; mkdir -p "$O"
now() { perl -MTime::HiRes=time -e 'printf "%.1f", time'; }
since() { perl -e "printf '%.1f', $(now) - $t0"; }
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
turn() { d cmd car_service inject-rotary -c true >/dev/null; sleep 1; rec "$1"; }
key() { d cmd car_service inject-key "$1" >/dev/null; sleep 3; rec "$2"; }
uiwait() { "$E/waitfor.sh" Coffee 40 >/dev/null; echo "uiautomator wait: $(since) s" >> "$O/steps.txt"; }

echo "arm $1 run $2 $(date '+%F %T')" > "$O/steps.txt"
adb -s emulator-5554 logcat -c
t0=$(now); d am start -S -n com.kanyandula.discovernearby/.ui.MainActivity >/dev/null 2>&1
case "$1" in
  A|D) sleep 14 ;;
  B) uiwait; r=$(perl -e "printf '%.1f', 14 - ($(now) - $t0)"); perl -e "exit($r <= 0)" && sleep "$r" ;;
  C) sleep "$3" ;;
  E) uiwait; sleep 2 ;;
esac
echo "first turn at: $(since) s after launch" >> "$O/steps.txt"
rec start
turn discover
key 23 recs-open
if [ "$1" = D ]; then d uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; rec dumped; fi
turn recs-1
turn recs-2
key 23 details-open
turn details-1
turn details-2
d cmd car_service inject-key 23 >/dev/null
for _ in $(seq 1 15); do sleep 1; [ "$(top)" = com.kanyandula.stubnavigation ] && break; done
rec navigate
key 4 back-1; turn back-1-turn
key 4 back-2; turn back-2-turn
key 4 back-3; turn back-3-turn
echo "stub received: $(adb -s emulator-5554 logcat -d -s StubNav:I | grep -c 'received')" >> "$O/steps.txt"
adb -s emulator-5554 logcat -d -v time -s RotaryController:D > "$O/rotary.log"
