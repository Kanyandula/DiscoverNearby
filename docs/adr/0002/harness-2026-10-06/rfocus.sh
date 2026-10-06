#!/bin/bash
# Print the tile (or bounds) the rotary service believes is focused.
b=$(adb -s emulator-5554 shell dumpsys activity service com.android.car.rotary/.RotaryService 2>/dev/null \
  | grep -E "^\s+focusedNode=" | grep -oE "boundsInScreen: Rect\([^)]*\)" | head -1)
case "$b" in
  *"48, 156 - 344, 380"*) echo Coffee ;; *"364, 156 - 660, 380"*) echo Food ;;
  *"680, 156 - 976, 380"*) echo Outdoors ;; *"48, 400 - 344, 624"*) echo Family ;;
  *"364, 400 - 660, 624"*) echo Scenic ;; *"680, 400 - 976, 624"*) echo Explore ;;
  *) echo "${b:-none}" ;;
esac
