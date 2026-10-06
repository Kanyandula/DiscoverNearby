#!/bin/bash
# Force-stop, then start the app with any extra arguments (e.g. --es scenario SLOW).
adb -s emulator-5554 shell am start -S -n com.kanyandula.discovernearby/.ui.MainActivity "$@"
