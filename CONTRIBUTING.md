# Contributing

Thanks for wanting to help!

## Reporting bugs

Open an [issue](https://github.com/Faaa97/OBDUSBtoTCPBridge/issues) with:

- the head unit or phone model and Android version,
- the USB OBD2 adapter (chip if you know it: ELM327, FTDI, CH340, CP210x, …),
- the OBD2 app you connect with,
- what happened and what you expected,
- logs if you can: turn on **Enable Logs**, reproduce the problem, then use **Share logs**.

## Pull requests

1. Fork the repository and create a branch from `master`.
2. Keep the change focused: one fix or feature per pull request.
3. Make sure it builds and the tests pass: `./gradlew assembleDebug testDebugUnitTest`.
4. Explain what changed and how you tested it (ideally on a real head unit and adapter).

The app runs unattended in a car, so the priorities are: never crash, recover from errors on its own, keep the service running, and use as few resources as possible (see [CLAUDE.md](CLAUDE.md)).

## License

By contributing, you agree that your contributions are licensed under the [GNU GPL v3.0 or later](LICENSE), the same license as the project.
