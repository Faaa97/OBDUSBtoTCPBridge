# OBD USB to TCP bridge

A small Android app that lets apps which only support Wi-Fi (TCP) OBD2 adapters use a **USB** OBD2 adapter instead.

It's made for Android head units in cars: the bridge runs as a background service, opens the USB ELM327-style adapter and exposes it on TCP port 35000 (the usual ELM327 Wi-Fi port), so a Wi-Fi OBD2 app (Torque, Car Scanner, …) can connect to it as if it were a Wi-Fi dongle.

## Install

Download the latest APK from [Releases](https://github.com/Faaa97/OBDUSBtoTCPBridge/releases) and install it on the head unit. Android 8.0 (API 26) or newer is required.

1. Plug in the USB OBD2 adapter and open the app.
2. Pick the adapter and start the service (turn on **Auto start service** to start it with the car).
3. In your OBD2 app, choose a Wi-Fi / TCP adapter and point it at `127.0.0.1`, port `35000`.

## Roadmap

- Allow setting the address and port
- Refresh the UI (the device selector is no longer useful)
- Refactor and clean up

## Support the project

If the bridge is useful to you, you can buy me a coffee on [Ko-fi](https://ko-fi.com/faaa97). Thank you!

## Contributing

Bug reports, ideas and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Copyright (C) 2026 Faaa97

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).
