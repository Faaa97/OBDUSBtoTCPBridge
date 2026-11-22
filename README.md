Small Android app that lets a **USB OBD2 adapter (ELM327)** work with apps that only support WiFi or Bluetooth OBD2 adapters. It is meant to run in the background on an Android car head unit.

## Install

Download the latest APK from [Releases](https://github.com/Faaa97/OBDUSBtoTCPBridge/releases) and install it on the head unit. Android 8.0 (API 26) or newer is required.

1. Plug in the USB OBD2 adapter and open the app.
2. Pick the adapter and start the service (turn on **Auto start service** to start it with the car).
3. In your OBD2 app, choose a WiFi / TCP adapter and point it at `127.0.0.1`, port `35000`.

## How it works

- Finds the ELM327 among the connected USB serial devices (it sends `ATZ` to each and waits for an `ELM` reply) and remembers it.
- Serves it on TCP port **35000**. In your OBD2 app, choose a WiFi adapter at `127.0.0.1`, port `35000`.
- Runs as a foreground service that reconnects by itself when the adapter is unplugged or the app disconnects.
- The **"Start ODBUSBToTCPBridge Service"** launcher starts the service with no UI, so the head unit can launch it on boot.
- The main app shows logs, and can share or clear the log files.

## Build

```sh
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

GitHub Actions runs the tests and lint on every push and pull request. Pushing a `v*` tag builds the APK and attaches it to a new GitHub release.

## Support the project

If the bridge is useful to you, you can buy me a coffee on [Ko-fi](https://ko-fi.com/faaa97). Thank you!

## Contributing

Bug reports, ideas and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Docs

- [`DESIGN.md`](DESIGN.md): design decisions, future work and open questions.
- [`AGENTS.md`](AGENTS.md): how to work on the repo (for AI agents and humans).

## License

Copyright (C) 2026 Faaa97

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).
