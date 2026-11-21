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
- A small UI: start/stop, device list, logging and autostart toggles, share and clear logs.

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
- **Status:** Accepted
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

### D-011: Debug-signed releases until a release keystore is on the build server

- **Date:** 2026-11-21
- **Status:** Accepted
- **Context:** There is no release keystore yet; the user will create one and add it to the build server.
- **Decision:** tag builds keep publishing debug-signed APKs (the fallback in `build.yml`) until the `SIGNING_*` secrets exist in the build server's `secrets/<repo>.env`. From then on they publish a signed release APK with no change to `build.yml`. How to create and add the keystore: `docs/SIGNING.md`.
- **Alternatives considered:** none.
- **Consequences:** the first signed release has a different signature from the debug builds, so the head unit needs one uninstall and reinstall, which wipes the app's saved settings (autostart, logging toggle, saved device). The keystore and its passwords must be backed up: losing them means every later update needs that uninstall again.

## 4. Prototype review

Not applicable: the app was built and tried in the car before it used this template.

| Date | Feedback | Outcome |
| ---- | -------- | ------- |
| | | |

## 5. Discarded ideas

| Idea | Why it was discarded | Date |
| ---- | -------------------- | ---- |
| Choosing the USB device by hand in a dropdown | Replaced by probing every device with `ATZ` (D-006); unreliable names, and no one to pick it in the car | 2026-05-30 |
| Mutex around service start | Replaced by the run-job check in `onStartCommand` (D-004) | 2026-09-21 |

## 6. Future work

From the README TODO list:

- [ ] Configurable TCP address and port.
- [ ] Refresh the UI: the device selector is no longer useful since D-006.
- [ ] Refactor and clean up.
- [ ] Set up donations.

## 7. Open questions

- Should per-message TCP↔USB logging be off by default (or a separate toggle), to save storage and power on the head unit? Pruning was answered in D-010. (raised 2026-11-21)
