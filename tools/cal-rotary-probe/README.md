# Car App Library rotary probe (DN-SP-002)

Throwaway. A standalone Gradle build, not part of the root build, `:app` or CI. It exists to answer one question for ADR-002: does a Car App Library template app get a working rotary journey (grid → list → details → Navigate → Back) on the reference emulator?

- Build: `./gradlew -p tools/cal-rotary-probe assembleDebug` (needs `tools/cal-rotary-probe/local.properties` with `sdk.dir`).
- Install: `adb -s emulator-5554 install -r tools/cal-rotary-probe/build/outputs/apk/debug/cal-rotary-probe-debug.apk`
- Logs: `adb -s emulator-5554 logcat -s ProbeNav`

Delete it if ADR-002 chooses Compose.
