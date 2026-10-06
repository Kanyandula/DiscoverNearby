#!/bin/bash
# After a cold boot: wait, then location on for user 10, Greystones fix, Park. Prints the state.
d() { adb -s emulator-5554 shell "$@"; }
adb -s emulator-5554 wait-for-device
for _ in $(seq 1 90); do [ "$(d getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 5; done
sleep 40
d cmd location set-location-enabled true --user 10
adb -s emulator-5554 emu geo fix -6.0633 53.1440 >/dev/null; sleep 3; adb -s emulator-5554 emu geo fix -6.0633 53.1440 >/dev/null
d cmd car_service inject-vhal-event 0x11400400 4 >/dev/null
echo "user $(d am get-current-user | tr -d '\r'); uptime: $(d uptime | tr -d '\r')"
d dumpsys activity service com.android.car.rotary/.RotaryService | grep -E 'focusedNode=|inRotaryMode' | tr -s ' '
echo "driving: $(d dumpsys car_service --services CarDrivingStateService | grep 'changed from' | tail -1 | tr -s ' ')"
