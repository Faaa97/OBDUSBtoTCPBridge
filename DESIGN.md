# Design

> Status: **Accepted**. Backfilled on 2026-11-21 from the existing code and history when the repo adopted the project template; the project predates it. Dates on decisions are when the behavior first appeared in `git log`.

## 1. Vision

### What the project should accomplish

A small Android app that lets a **USB OBD2 adapter (ELM327)** be used by OBD2 apps that only accept WiFi (or Bluetooth) adapters. It finds the ELM327 on USB and serves it over TCP on the device itself, so an app pointed at a "WiFi OBD2 dongle" on `127.0.0.1:35000` talks to the USB adapter instead.

It runs on the **Android head unit in the user's car** (an FYT unit, see the hive-mind car-head-unit notes). Success means it is started by the head unit's OS when the car turns on, connects to the adapter by itself, and keeps bridging for the whole drive without anyone touching it.

Hard requirements (from the original `CLAUDE.md`):

- **Never crash.** Anything unexpected is caught, logged, and recovered from.
- **The OS may start the service several times.** Extra starts must not restart or disturb a running bridge.
- **Always running in the background**, as a foreground service.
- **As few resources as possible**: idle while waiting, no polling loops that spin.

### What makes it unique

Most OBD2 apps on head units support WiFi or Bluetooth ELM327 dongles but not USB ones. This bridges that gap with no root, no app changes, and no UI needed once set up.

### Competitors and alternatives

Not researched: the project was built before it used this template.

| Name | What it does well | Where it falls short | What we take or avoid |
| ---- | ----------------- | -------------------- | --------------------- |
| _not researched_ | | | |

## 2. MVP

The MVP is built and in daily use (version 0.1.0).

### In scope

- Find the ELM327 among connected USB serial devices, last-used device first, and remember it.
- Bridge it to a TCP server on port 35000, one client at a time.
- A foreground service that survives repeated starts and reconnects when the adapter or the client goes away.
- A headless launcher activity the head unit can start on boot.
- A small UI: start/stop, the detected device and service status, logging and autostart toggles, share and clear logs, live log (D-013).

### Out of scope (for the MVP)

- Configurable address and port (see Future work).
- Several TCP clients at once: an ELM327 is a single command/response stream.
- Parsing or translating OBD2 data: bytes are passed through unchanged.

### External services

None. The app talks only to the USB device and to local TCP clients.

### Done when

- An OBD2 app set to a WiFi adapter at the head unit's `127.0.0.1:35000` reads live data from the USB ELM327. **Met.**
- Unplugging and replugging the adapter, or restarting the client app, recovers without user action. **Met.**

## 3. Design decisions

Locked-in decisions. Add new entries at the bottom and never silently rewrite old ones: if a decision changes, mark it **Superseded by D-NNN** and add a new entry.

### D-001: Native Kotlin app with Android Views

- **Date:** 2026-04-20
- **Status:** Accepted
- **Context:** Needs to run on an Android head unit (real Android 10 on the target unit) and use the USB host API.
- **Decision:** Kotlin, Android Views (no Compose), coroutines. `minSdk 26`, `targetSdk 36`, package `com.faaa97.obdusbtotcpbridge`.
- **Alternatives considered:** none recorded.
- **Consequences:** small APK and footprint; the UI is a single `MainActivity` layout.

### D-002: usb-serial-for-android for USB serial drivers

- **Date:** 2026-04-20
- **Status:** Accepted
- **Context:** ELM327 USB adapters use various USB-serial chips (CH340, FTDI, CP210x, PL2303…).
- **Decision:** use `com.github.mik3y:usb-serial-for-android` and its default prober. Code touches the port only through the small `SerialLink` interface so the bridge can be unit tested with `FakeSerialLink`.
- **Alternatives considered:** none recorded.
- **Consequences:** only chips the library supports are found.

### D-003: Plain TCP on port 35000, line-buffered pass-through

- **Date:** 2026-04-22
- **Status:** Accepted
- **Context:** WiFi ELM327 dongles conventionally listen on TCP 35000, so OBD2 apps default to it.
- **Decision:** `TcpUsbBridge` binds a `ServerSocket` on port 35000 and serves **one client at a time**. Client bytes are buffered until `\r` and then written to USB; USB bytes are buffered until the ELM `>` prompt and then written to the client. Leftover USB bytes are drained when a new client connects. A USB failure (`UsbFailure`) ends the session; a client failure only ends that client.
- **Alternatives considered:** raw byte pass-through without buffering (the first version; replaced to keep whole requests/responses together).
- **Consequences:** address and port are hard-coded (see Future work).

### D-004: Foreground service that ignores repeat starts

- **Date:** 2026-06-06 (mutex 2026-06-09, current form 2026-09-21)
- **Status:** Accepted
- **Context:** The head unit's OS may start the service several times; a restart would drop the active bridge.
- **Decision:** `ObdService` is a `dataSync` foreground service returning `START_STICKY`. `onStartCommand` starts the run loop only if it isn't running; otherwise it only wakes a pending retry. All work runs in one `SupervisorJob` scope on `Dispatchers.IO`, and every loop catches and logs errors other than cancellation.
- **Alternatives considered:** a mutex around start (2026-06-09), replaced by the single run-job check.
- **Consequences:** a running bridge is never disturbed by another start.

### D-005: Headless `LaunchServiceActivity` as the entry point for the head unit

- **Date:** 2026-05-18
- **Status:** Accepted
- **Context:** The head unit can launch apps (e.g. on boot) but not start a service directly.
- **Decision:** an exported, transparent, `noHistory` launcher activity that starts `ObdService` with `EXTRA_AUTO_CONNECT=true` and finishes at once.
- **Alternatives considered:** none recorded.
- **Consequences:** the app has two launcher icons: the UI and "Start ODBUSBToTCPBridge Service".

### D-006: Find the adapter by probing every device with `ATZ`

- **Date:** 2026-05-30
- **Status:** Accepted
- **Context:** Picking the device by hand in the UI didn't suit a car, and USB device names can change between boots.
- **Decision:** `USBDeviceSelection.openAndVerifyDevice()` opens each serial device in turn (the last verified one first), requesting USB permission with a 30 s timeout, sends `ATZ` and accepts the first that answers with `ELM` within 2 s (`ElmVerifier`). The verified device is persisted.
- **Alternatives considered:** a device dropdown with a saved stable ID (2026-05-06 to 2026-05-25); kept in the UI but no longer needed (README TODO).
- **Consequences:** a non-ELM serial device gets an `ATZ` written to it during probing.

### D-007: Auto-reconnect with exponential backoff

- **Date:** 2026-06-03 (backoff 2026-09-21)
- **Status:** Accepted
- **Context:** Adapters get unplugged, sessions die, and the car may boot before the adapter is ready; retrying must not waste power.
- **Decision:** with auto-connect on, the run loop retries after 1 s doubling up to 30 s (`Backoff`). The delay resets after a session that lasted over 60 s, or when woken by a USB attach broadcast or another service start. A detach of the active device cancels the session. Without auto-connect (started from the UI) the service stops when no adapter is found.
- **Alternatives considered:** none recorded.
- **Consequences:** idle cost is one timed wait; reconnect is immediate on replug.

### D-008: Log to a daily file in app storage, shareable from the UI

- **Date:** 2026-06-04
- **Status:** Accepted
- **Context:** Problems happen in the car, away from a debugger.
- **Decision:** `ObdLogBus` writes each message to Logcat, a `SharedFlow` for the UI, and `filesDir/obd_logs_YYYYMMDD.txt`. Logging can be switched off; the UI shares the files through a `FileProvider` or clears them.
- **Alternatives considered:** none recorded.
- **Consequences:** files are kept until the user deletes them (D-010), and every TCP↔USB message is logged while logging is on (see Open questions).

### D-009: Builds and releases on the build server

- **Date:** 2026-11-21
- **Status:** Superseded by D-012
- **Context:** GitHub Actions minutes for private repos ran out; the user's repos build on Faaa97/build-server from a `build.yml`.
- **Decision:** `build.yml` replaces `.github/workflows/release-apk.yml`: image `build-server/android:latest`, Gradle cache, CI runs `testDebugUnitTest lintDebug`, builds copy the APK to `dist/`. Tag builds sign a release APK when the `SIGNING_*` secrets exist on the build machine, and fall back to a debug-signed APK otherwise, as the old workflow did. Release notes come from git-cliff (`cliff.toml`).
- **Alternatives considered:** keeping the GitHub Actions workflow (no minutes left).
- **Consequences:** the repo must be turned on in the build server's page. `build.yml` hasn't run yet; the first CI run will show whether `lintDebug` passes on the existing code.

### D-010: Log files are deleted only from the UI

- **Date:** 2026-11-21
- **Status:** Accepted
- **Context:** Open question on whether old log files should be pruned automatically to save storage.
- **Decision:** never delete log files automatically. They are removed only when the user taps the clear-logs button in the UI.
- **Alternatives considered:** pruning files older than N days, or capping total size (rejected by the user).
- **Consequences:** no pruning code is to be added; storage use grows while logging is on until the user clears it.

### D-011: Debug-signed releases until a release keystore is configured

- **Date:** 2026-11-21
- **Status:** Accepted
- **Context:** There is no release keystore yet; the user will create one and add it as GitHub repository secrets (originally the build server; moved by D-012).
- **Decision:** tag builds keep publishing debug-signed APKs (the fallback in `.github/workflows/release-apk.yml`) until the `SIGNING_*` repository secrets exist on GitHub. From then on they publish a signed release APK with no change to the workflow. How to create and add the keystore: `docs/SIGNING.md`.
- **Alternatives considered:** none.
- **Consequences:** the first signed release has a different signature from the debug builds, so the head unit needs one uninstall and reinstall, which wipes the app's saved settings (autostart, logging toggle, saved device). The keystore and its passwords must be backed up: losing them means every later update needs that uninstall again.

### D-012: CI and releases back on GitHub Actions

- **Date:** 2026-11-22
- **Status:** Accepted
- **Context:** The repo goes public (PR #1), and GitHub Actions is free for public repos, so the reason for D-009 is gone.
- **Decision:** delete `build.yml`. `.github/workflows/ci.yml` runs `testDebugUnitTest lintDebug` on every push to `master` and every PR and keeps the reports; it builds no APK (the user wants APKs only from version tags). `.github/workflows/release-apk.yml` runs on `v*` tags: it tests, builds the APK (signed release with the `SIGNING_*` repository secrets, debug-signed otherwise), and creates the GitHub release with git-cliff notes (`cliff.toml`) and the APK attached; a tag with a suffix makes a pre-release.
- **Alternatives considered:** keeping the build server (an extra service to keep running, and outside contributors can't see the CI logs).
- **Consequences:** nothing to turn on in the build server. Signing secrets go in the repo's GitHub settings (`docs/SIGNING.md`).

### D-013: Landscape dashboard UI that shows the detected device instead of a selector

- **Date:** 2026-11-21
- **Status:** Accepted
- **Context:** The UI was a narrow centered column with small buttons, a tiny log and a device dropdown that D-006 made pointless. It runs on a ~9" landscape head unit (1920x768 or similar) and is tapped while in the car.
- **Decision:** `MainActivity` is a two-column, always-dark dashboard with no action bar. The left column (~40%) has a status card (USB device and service state with colored dots), a full-width 88dp Start/Stop button (green/red) and 72dp tiles for the Auto start and Logging switches and the Share/Clear log files buttons; it scrolls if the screen is too short. The right column (~60%) is the full-height live log. The device is display-only: `USBDeviceSelection.findCompatibleDevice()` shows the device in use, else the saved one, else the first serial device found, and never changes what the service uses; it refreshes on USB attach/detach and on resume. Start needs a detected device; Stop is always enabled. The on-screen log keeps only the last 500 lines in one `TextView` and auto-scrolls unless the user has scrolled up. The activity unregisters its receiver and observers in `onDestroy`.
- **Alternatives considered:** keeping the dropdown (no one to pick a device in the car, and the service probes all devices anyway); following the system day/night theme (head units often don't switch, and a light screen glares at night).
- **Consequences:** sized for ~480dp of usable height (1920x768 at 240dpi). The on-screen log is bounded, unlike the log files (D-010). Not yet checked on the head unit.

### D-014: A `mock` build type with a simulated adapter, kept out of CI and releases

- **Date:** 2026-11-22
- **Status:** Accepted
- **Context:** Trying UI or reconnect changes needed the head unit and the car's USB adapter. An emulator has no USB serial device, so the real app only ever shows "No compatible device" there.
- **Decision:** a third build type, `mock` (`initWith(debug)`, application id suffix `.mock`, label "OBD bridge (mock)"). Its code lives only in `app/src/mock/`: a manifest overlay swaps the Application for `MockApp`, which sets `Adapters.source` to a simulated ELM327 (`MockAdapterSource`, `MockElm327`), starts a `DemoClient` that polls the bridge over TCP like an OBD2 app, and takes adb broadcasts to unplug/plug the adapter and turn the demo client off. The main code reaches the adapter through a small `AdapterSource` interface (`UsbAdapterSource` wraps the existing `USBDeviceSelection`), so the service, TCP bridge and UI run unchanged in the mock build. Tests for the simulator are in `app/src/testMock` (`testMockUnitTest`). No workflow builds, tests or lints the mock build. How to use it: `docs/BUILDS.md`.
- **Alternatives considered:** product flavors (`real`/`mock`) would rename every task (`testRealDebugUnitTest`…) and need workflow changes; a demo switch inside the debug build would put mock code in the APK we test and ship from; layout-preview data only (`tools:` attributes, kept as well) doesn't run on a device.
- **Consequences:** Debug and release APKs contain no mock classes (checked in their dex files). `App` is `open` so `MockApp` can extend it. Adapters that send no USB broadcast notify through `AdapterSource.changes`; for USB it never fires, the system broadcasts still drive the service and the UI.

## 4. Prototype review

Not applicable: the app was built and tried in the car before it used this template.

| Date | Feedback | Outcome |
| ---- | -------- | ------- |
| | | |

## 5. Discarded ideas

| Idea | Why it was discarded | Date |
| ---- | -------------------- | ---- |
| Choosing the USB device by hand in a dropdown | Replaced by probing every device with `ATZ` (D-006); unreliable names, and no one to pick it in the car. Removed from the UI in D-013 | 2026-05-30 |
| Mutex around service start | Replaced by the run-job check in `onStartCommand` (D-004) | 2026-09-21 |

## 6. Future work

From the README TODO list:

- [ ] Configurable TCP address and port.
- [x] Refresh the UI: the device selector is no longer useful since D-006. Done in D-013.
- [ ] Refactor and clean up.
- [ ] Set up donations.

## 7. Open questions

- Should per-message TCP↔USB logging be off by default (or a separate toggle), to save storage and power on the head unit? Pruning was answered in D-010. (raised 2026-11-21)
