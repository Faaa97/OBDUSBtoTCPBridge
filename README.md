# ODB USB to TCP bridge

Small Android app that lets a **USB OBD2 adapter (ELM327)** work with apps that only support WiFi or Bluetooth OBD2 adapters. It is meant to run in the background on an Android car head unit.

## How it works

- Finds the ELM327 among the connected USB serial devices (it sends `ATZ` to each and waits for an `ELM` reply) and remembers it.
- Serves it on TCP port **35000**. In your OBD2 app, choose a WiFi adapter at `127.0.0.1`, port `35000`.
- Runs as a foreground service that reconnects by itself when the adapter is unplugged or the app disconnects.
- The **"Start ODBUSBToTCPBridge Service"** launcher starts the service with no UI, so the head unit can launch it on boot.
- The main app is a landscape dashboard for the head unit: the detected USB device, service status, a large Start/Stop button, autostart and logging switches, and a live log. It can share or clear the log files.

## Build

```sh
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

Releases are built by the build server from `build.yml` when a `v*` tag is pushed; the APK is attached to the GitHub release.

## Docs

- [`DESIGN.md`](DESIGN.md): design decisions, future work and open questions.
- [`AGENTS.md`](AGENTS.md): how to work on the repo (for AI agents and humans).
