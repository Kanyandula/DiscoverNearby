#!/bin/bash
# Print every text on screen, one per line.
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb -s emulator-5554 shell cat /sdcard/ui.xml | tr '>' '\n' | grep -oE ' text="[^"]+"' | sed 's/ text=//'
