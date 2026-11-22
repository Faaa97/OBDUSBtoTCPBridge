# Builds

The app has three build types (D-014). Each makes its own APK in `app/build/outputs/apk/<type>/`, named `OBDUSBtoTCPbridge-<commit>-<type>.apk`.

| Build | Command | What it is |
| ----- | ------- | ---------- |
| Debug | `./gradlew assembleDebug` (or `installDebug`) | The real app, debug-signed. Talks to a USB OBD2 adapter. |
| Release | `./gradlew assembleRelease` | The real app as it is published. Built locally it is **unsigned** and won't install; tag builds sign it in GitHub Actions (`docs/SIGNING.md`). |
| Mock | `./gradlew assembleMock` (or `installMock`) | A debug build with a **simulated ELM327** instead of USB, to try the app on an emulator or a PC without a head unit. |

On Windows, use `gradlew.bat` instead of `./gradlew`. In Android Studio, pick the build type in **Build > Select Build Variant** (`debug`, `release` or `mock`), then press Run.

No workflow builds or tests the mock build. CI and releases only use `debug` and `release`, so mock code can never end up in a published APK.

## Trying the app without a head unit (mock build)

1. Start an emulator: in Android Studio, **Device Manager**, any phone or tablet image. Rotate it to landscape to see the head-unit layout.
2. Install and open the mock build:

   ```sh
   ./gradlew installMock
   adb shell am start -n com.faaa97.obdusbtotcpbridge.mock/com.faaa97.obdusbtotcpbridge.MainActivity
   ```

   It installs as **OBD bridge (mock)** (`com.faaa97.obdusbtotcpbridge.mock`), next to the real app if that is installed too.
3. The device card shows **Simulated ELM327**. Tap **Start Service**. The service, TCP server and UI run exactly as in the real app; only the adapter is fake.

What the mock build adds (all in `app/src/mock/`):

- **Simulated ELM327** (`MockElm327`): answers `AT` commands (`ATZ`, `ATI`, `ATE0`, `ATS0`, `ATRV`, `ATDP`…) and mode 01 PIDs `00`, `04`, `05`, `0B`, `0C` (RPM), `0D` (speed), `0F`, `10`, `11` with values that drift like a car driving around. Other PIDs answer `NO DATA`. It passes the same `ATZ` check as a real adapter.
- **Demo client** (`DemoClient`): plays the part of an OBD2 app. Every few seconds it connects to `127.0.0.1:35000`, polls RPM, speed, coolant and throttle for about 30 s, disconnects and waits 10 s. This keeps the log and the status moving.

### Controlling the mock from the PC

While the mock app is running, send these with adb:

```sh
P=com.faaa97.obdusbtotcpbridge.mock
adb shell am broadcast -p $P -a $P.UNPLUG           # Pull the simulated adapter out
adb shell am broadcast -p $P -a $P.PLUG             # Plug it back in
adb shell am broadcast -p $P -a $P.DEMO_CLIENT_OFF  # Stop the demo client (to use a real OBD2 app)
adb shell am broadcast -p $P -a $P.DEMO_CLIENT_ON   # Start it again
```

The demo client is on whenever the app starts.

Things worth testing this way:

- **Unplug while running** from the Start button: the service stops, as with a real adapter that goes away.
- **Auto-reconnect:** start the headless launcher instead (`adb shell am start -n com.faaa97.obdusbtotcpbridge.mock/com.faaa97.obdusbtotcpbridge.LaunchServiceActivity`), then unplug and plug. The log shows the backoff (`Retrying in 1s`, `2s`, `4s`…) and an immediate reconnect on plug.
- **A real OBD2 app:** install one on the emulator, send `DEMO_CLIENT_OFF`, and point the app at a WiFi adapter on `127.0.0.1`, port `35000`. The bridge serves one client at a time, so a real app has to wait while the demo client is connected.

### Tests

The simulator has its own unit tests in `app/src/testMock/`:

```sh
./gradlew testMockUnitTest
```

They aren't part of CI. Run them when you change mock code.

## Layout preview

The Android Studio layout editor shows mock data (a connected, running bridge and a full log) from `tools:` attributes in `activity_main.xml`. Those are stripped at build time and never reach any APK, mock included.
