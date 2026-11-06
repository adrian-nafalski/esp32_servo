# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Servo: a native Android app (Kotlin, Jetpack Compose, Material 3, minSdk 31) that controls one positional servo (SG90/MG90S) on an ESP32 over BLE, plus an Arduino sketch for the ESP32 in `firmware/`. The GATT protocol is a custom demo protocol shared by both halves. UI strings, code comments, READMEs and firmware messages are in **Polish**. Commits use Conventional Commits in English (`feat(app):`, `feat(firmware):`, `test(app):`, `docs:`).

## Commands

```bash
./gradlew :app:assembleDebug                 # build
./gradlew :app:testDebugUnitTest             # all JVM unit tests
./gradlew :app:lintDebug                     # lint (only "newer version available" notices are expected)
./gradlew :app:installDebug                  # install on a connected device/emulator

# single test class / single test (test names are backticked sentences, use a wildcard)
./gradlew :app:testDebugUnitTest --tests "com.example.esp32_servo.servo.AngleCommandSenderTest"
./gradlew :app:testDebugUnitTest --tests "com.example.esp32_servo.ui.ServoViewModelTest.controls stay locked*"
```

- AGP 9.x with **built-in Kotlin** (no `kotlin-android` plugin), `compileSdk { version = release(37) }`, Gradle daemon toolchain JDK 25 (`gradle/gradle-daemon-jvm.properties`). Versions live in `gradle/libs.versions.toml`.
- After changing the version catalog or `app/build.gradle.kts`, Android Studio needs a Gradle sync; until then the IDE shows false "Unresolved reference" errors even though `./gradlew` compiles.
- There are no instrumented tests (`androidTest` was removed on purpose). The emulator has no BLE, so real connect/write/read can only be checked on a physical phone with the ESP32.
- The firmware is not part of the Gradle build. It intentionally fails to compile (`#error`) until `SERVO_PIN` is defined. Never pick a GPIO, supply voltage or pulse widths for the user; they depend on the board and servo.

## Architecture

Unidirectional data flow, manual DI:

`ServoApplication` → `AppContainer` (app-scoped `CoroutineScope`, `BleManager`, `BleServoRepository`) → `ServoViewModel` (factory reads `application.container.servoRepository`) → Compose (`ui/ServoScreen.kt` → `ServoRoute`).

- **`data/ServoRepository`** is the seam. It exposes `availability`, `scanState` and `connectionState` as `StateFlow`, plus commands and `suspend sendTargetAngle(session, angle): SendResult`. `BleServoRepository` delegates to `BleManager`. `FakeServoRepository` lives in the **main** source set (not test) because Compose previews (`ui/ServoPreviews.kt`) and unit tests both use it. Its API calls go only to the first step (e.g. `connect()` → `Connecting`), and tests drive further transitions with `simulate*()`.
- **`ble/BleManager`** confines all BLE state to `Dispatchers.Default.limitedParallelism(1)` and serializes GATT operations with a `Mutex` plus `CompletableDeferred`, each with its own timeout (connect, discovery, status read, write). Every connection is a `GattSession` with an incrementing id and its own `BluetoothGattCallback`, so events from a closed session are ignored. `ConnectionState.Ready` carries the `session` id, and writes for a stale session are rejected. `Ready` is published only after the service and angle characteristic are validated (and the optional status characteristic read). Write calls branch on API 33+ (`writeCharacteristic(ch, value, type)`) vs the deprecated 31–32 path.
- **`servo/AngleCommandSender`** throttles writes to ≤1 per `ServoConfig.COMMAND_INTERVAL_MS`. It is "latest wins" (a single `MutableStateFlow` slot, not a queue) and tracks the in-flight command so `ServoViewModel.requestAngle` can skip re-sending a value already queued, in flight or delivered.
- **`ui/ServoViewModel`** combines repository flows with a private `ServoLocalState` (target angle, command status, active session, picker visibility) through the pure function `buildServoUiState(...)` in `ui/ServoUiState.kt`. UI events come in as `ServoAction`. On a new `Ready` session it clears pending commands and may adopt the firmware's last accepted angle **locally without sending**. On any non-Ready state it drops pending commands.
- **Background policy** is process-level, not per Activity: `ServoApplication` observes `ProcessLifecycleOwner` and calls `BleServoRepository.onAppForegroundChanged`. Scanning stops immediately and the device disconnects after `BACKGROUND_DISCONNECT_DELAY_S`. Rotation and theme changes must not count as backgrounding.

## Invariants to preserve

- **No automatic movement.** App start, connect, reconnect and return from background never send an angle. Only user input (slider, ±1°, presets, retry) does.
- **Target vs measured angle.** The servo reports no position. UI text and the drawing always describe the *target* (zadany) angle. `FirmwareTarget` is only "last command accepted by firmware".
- **Protocol sync.** UUIDs and the uint16 little-endian payload are defined in `ble/ServoProtocol.kt` and in `firmware/esp32_servo_ble/esp32_servo_ble.ino`. The angle range is in `servo/ServoConfig.kt` and `ANGLE_MIN`/`ANGLE_MAX` in the sketch. Change both sides together. `0xFFFF` is reserved for "no command since boot".
- **Splash and theme colors.** `@color/servo_background` (values and values-night) must equal `background` in `ui/theme/Color.kt`, or the splash → Compose transition flashes. The splash uses the platform SplashScreen API (theme attributes plus `splashScreen.setOnExitAnimationListener` in `MainActivity`), with no core-splashscreen library and no SplashActivity.
- **Shared servo geometry.** `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `splash_servo.xml` and `ui/ServoGlyph.kt` share the same coordinates (axis at 63,62 in a 108 viewport). Keep them consistent.
- **Permissions.** Only `BLUETOOTH_SCAN` (with `neverForLocation`) and `BLUETOOTH_CONNECT`. No location, ADVERTISE or INTERNET.
- **Accessibility.** Status is never shown by color alone (`StatusGlyph` shapes plus text). Degree phrases for TalkBack use Polish plurals (`plurals/degrees_spoken`).

## Testing notes

- `MainDispatcherRule` swaps `Dispatchers.Main` for a `StandardTestDispatcher`. `runTest` shares its scheduler, so the ViewModel and the test advance the same virtual clock.
- Run `AngleCommandSender` in a **foreground** test scope (`CoroutineScope(StandardTestDispatcher(testScheduler) + job)`, cancelled in `@After`), not `backgroundScope`. `advanceUntilIdle()` does not advance time for background-scope work, and later sends silently never happen.

## Docs assets

`docs/screenshots/` was captured on an emulator by temporarily wiring a `FakeServoRepository` into `AppContainer`. If you regenerate it the same way, restore the production `AppContainer`/`ServoApplication` afterwards. The README states that the screenshots use sample data.
