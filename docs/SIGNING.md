# Release signing

Tag builds on the build server (see `build.yml`) sign the release APK when these four secrets are in the server's `secrets/<repo>.env` on the build machine. Without them, they publish a debug-signed APK instead (D-011 in `DESIGN.md`). `build.yml` needs no change either way.

| Secret | Value |
| ------ | ----- |
| `SIGNING_KEYSTORE_BASE64` | The keystore file, base64 on one line |
| `SIGNING_KEYSTORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias inside the keystore |
| `SIGNING_KEY_PASSWORD` | Key password (with a PKCS12 keystore, the same as the keystore password) |

## Creating the keystore (once)

Run on your own machine (`keytool` comes with any JDK or Android Studio):

```sh
keytool -genkeypair -v -keystore obd-bridge-release.jks -alias obd-bridge \
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a password and a name (anything is fine). Then make the one-line base64 value:

```sh
base64 -w0 obd-bridge-release.jks > obd-bridge-release.jks.b64   # macOS: base64 -i obd-bridge-release.jks
```

Never commit the keystore or the base64 file (`.gitignore` already ignores `*.jks` and `*.keystore`).

## Adding it to the build server

Append to `secrets/<repo>.env` on the build machine (the file and repo name the build server uses for this repo):

```sh
SIGNING_KEYSTORE_BASE64=<contents of obd-bridge-release.jks.b64>
SIGNING_KEYSTORE_PASSWORD=<password>
SIGNING_KEY_ALIAS=obd-bridge
SIGNING_KEY_PASSWORD=<password>
```

The next `v*` tag build then produces `dist/OBDUSBtoTCPbridge-<tag>.apk`, signed and checked with `apksigner verify`. A build without the secrets names its file `…-debug.apk`, so the file name shows which one you got.

## Before you rely on it

- **Back up the keystore and passwords** somewhere safe besides the build machine. If they are lost, an update can't be installed over the existing app.
- **The first signed release needs one uninstall on the head unit**, because its signature differs from the debug builds. Uninstalling wipes the app's settings (autostart, logging toggle, saved device), so set them again afterwards, and re-check that the head unit still launches "Start ODBUSBToTCPBridge Service".
