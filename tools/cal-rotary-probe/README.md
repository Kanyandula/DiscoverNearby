# Car App Library rotary probe (DN-SP-002)

Throwaway. A standalone Gradle build, not part of the root build, `:app` or CI. It exists to answer one question for ADR-002: does a Car App Library template app get a working rotary journey (grid → list → details → Navigate → Back) on the reference emulator?

- Build: `./gradlew -p tools/cal-rotary-probe assembleDebug` (needs `tools/cal-rotary-probe/local.properties` with `sdk.dir`).
- Install: `adb -s emulator-5554 install -r tools/cal-rotary-probe/build/outputs/apk/debug/cal-rotary-probe-debug.apk`
- Logs: `adb -s emulator-5554 logcat -s ProbeNav`
- Launch: `adb -s emulator-5554 shell am start --user 10 -n com.kanyandula.calprobe/androidx.car.app.activity.CarAppActivity`. A cold start renders in 15–20 s; confirm the grid with `adb -s emulator-5554 exec-out screencap -p > shot.png` before turning.
- Rotary: `adb -s emulator-5554 shell cmd car_service inject-rotary -c true` (turn), `inject-key 23` (select), `inject-key 4` (Back), `inject-key 280/281` (nudge up/down).
- Where focus is: `adb -s emulator-5554 shell dumpsys activity service com.android.car.rotary/.RotaryService | grep focusedNode`.
- Why it fails: `adb -s emulator-5554 logcat -s RotaryController:V`; look for "Restored focus in root failed" and "focus has since moved".

Delete it if ADR-002 chooses Compose.
