#!/bin/bash
# Wait up to ${2:-30} s for a UI node whose text or content-desc contains $1; print it. Exits 1 on timeout,
# so `waitfor.sh never-appears N` doubles as an N-second pause.
for _ in $(seq 1 "${2:-30}"); do
  adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb -s emulator-5554 shell cat /sdcard/ui.xml | tr '>' '\n' \
    | grep -m1 -oE "(text|content-desc)=\"[^\"]*$1[^\"]*\"" && exit 0
  sleep 1
done
echo "timed out: $1" >&2; exit 1
