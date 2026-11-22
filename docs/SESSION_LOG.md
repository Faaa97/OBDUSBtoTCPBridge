# Session log

What each session did and left for the next. Newest entries first; keep each entry short. Nothing loads this file automatically: read it only when you need history beyond `AGENTS.md`'s **Current state**, `git log` and the PRs. Lasting facts (gotchas, commands) belong in `AGENTS.md` or a topic doc, not only here.

### 2026-11-22 (keep-alive review)

- **Done:** Reviewed how the service stays up against the FYT notes in hive-mind. Fixed (D-016): a `START_STICKY` restart now auto-connects instead of stopping on the first failed open; `LaunchServiceActivity` gets a `USB_DEVICE_ATTACHED` filter so Android relaunches the app (and grants USB permission) even after a force-stop; the UI's autostart passes auto-connect; a coroutine exception handler and crash logging. `StartPolicyTest` added; `testDebugUnitTest lintDebug testMockUnitTest` pass.
- **Next:** On the head unit, tick "Use by default" for the adapter in the first USB dialog, then check the log after a trip: `(USB attached)`, `Service restarted by the system` or nothing tells whether FYT kills the app on sleep.

### 2026-11-22 (in-app updates)

- **Done:** Released v0.2.0 (bump on `master`, tag pushed; the APK is signed). Added in-app updates (D-015): `Updates` checks GitHub's latest release, a banner above the log offers Update, which downloads the APK and opens the package installer. `UpdatesTest` covers version comparison and release parsing. `testDebugUnitTest lintDebug` and `testMockUnitTest` pass.
- **Next:** Try the banner on the head unit once a release newer than the installed one exists (install 0.2.0, then release 0.2.1 or later). No emulator was available to check it on screen.
- **Notes:** To see the banner without a new release, temporarily lower `versionName` in `app/build.gradle.kts` and install the debug or mock build.

### 2026-11-22 (emulator, mock build)

- **Done:** Ran PR #2 on the `Medium_Phone_API_36.1` emulator in landscape. Fixed the Share/Clear log buttons (baseline alignment shifted the two-line one down) and gave the controls column an always-visible scrollbar plus fading edges. Added `tools:` preview data to `activity_main.xml`. Added the `mock` build type (D-014, `docs/BUILDS.md`) behind a new `AdapterSource` seam; checked on the emulator: traffic through the real bridge, unplug/plug, auto-reconnect backoff, and that the real debug build is unchanged. Debug and release APKs checked to contain no mock classes.
- **Next:** Try PR #2 on the head unit.
- **Notes:** AGP 9 creates unit tests only for `debug`; `build.gradle.kts` turns them on for `mock` via `androidComponents.beforeVariants`. In landscape on a phone emulator, `wm user-rotation lock 1` rotates it (the `settings put system user_rotation` route didn't).

### 2026-11-22 (UI refresh)

- **Done:** Redesigned `MainActivity` as a landscape, always-dark dashboard for the 9" head unit (D-013): status card, big Start/Stop, switch tiles, log buttons, full-height log. Removed the device dropdown; the detected device is shown via `USBDeviceSelection.findCompatibleDevice()` (preference logic tested in `DevicePreferenceTest`). On-screen log bounded to 500 lines; receiver/observer leaks fixed. Merged `master` into PR #2 (template, then the go-public/GitHub Actions changes; renumbered the UI decision from D-012 to D-013 since master took D-012).
- **Next:** Try PR #2 on the head unit and adjust sizes if needed.
- **Notes:** Installed the Android SDK in the session; `testDebugUnitTest lintDebug` and `assembleDebug` pass. Pre-existing lint warning: `shareLogFiles` uses `resolveActivity` without a `<queries>` entry, which returns null on Android 11+ (fine on the Android 10 unit).

### 2026-11-22

- **Done:** With the repo going public (PR #1), moved CI and releases back to GitHub Actions (D-012): deleted `build.yml`, added `.github/workflows/ci.yml` (tests and lint on pushes to `master` and PRs; APKs are built only for `v*` tags), and reworked `release-apk.yml` to run on `v*` tags with git-cliff notes. Merged `master` into PR #1 to clear its conflicts.
- **Next:** check that CI passes; add the signing secrets on GitHub (`docs/SIGNING.md`).

### 2026-11-21

- **Done:** Adopted the project template. `CLAUDE.md` now just imports `AGENTS.md`, which keeps its requirements under **Hard requirements**. `DESIGN.md` backfilled from the code and `git log` as Accepted (D-001 to D-009). `build.yml` and `cliff.toml` for the build server replace `.github/workflows/release-apk.yml`, keeping its optional APK signing. Added `docs/PR.md`, `.dashboard.json`, and rewrote `README.md`.
- **Next:** Turn the repo on in the build server and check the first CI run; set the repo to squash merge only; add the release keystore to the build server (`docs/SIGNING.md`).
- **Decided:** logs are deleted only from the UI (D-010); releases stay debug-signed until the keystore is on the build server (D-011).
- **Notes:** No Android SDK in the session, so Gradle wasn't run; `build.yml` is untested until the server runs it.

### Before 2026-11-21

Built without the template, 2026-04-20 to 2026-09-28: TCP forwarding, USB device selection, the headless launcher activity, probing devices with `ATZ`, auto-reconnect, file logging, handling repeated service starts, tests, and a GitHub Actions APK release (v0.1.0). See `git log` and `DESIGN.md`.
