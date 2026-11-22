# Session log

What each session did and left for the next. Newest entries first; keep each entry short. Nothing loads this file automatically: read it only when you need history beyond `AGENTS.md`'s **Current state**, `git log` and the PRs. Lasting facts (gotchas, commands) belong in `AGENTS.md` or a topic doc, not only here.

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
