# Agent Guide

Instructions for AI coding agents (and humans) working in this repository. This file loads into every session and every request, so keep it short: everything here should matter for most tasks. Put details that matter only for one area in topic docs listed under **Read before working on…**, and history in `docs/SESSION_LOG.md`.

## Hard requirements

The app runs unattended on the Android head unit in the user's car. Every change must keep these true:

- **Never crash.** Catch, log and recover from anything unexpected; rethrow only `CancellationException`.
- **Recover gracefully** from errors: USB unplugged, client gone, port busy, permission denied.
- **The car's OS may start the service several times.** A running bridge must keep running untouched (see D-004 in `DESIGN.md`).
- **Always run in the background** as a foreground service.
- **Use as few resources as possible**: block or wait on signals, never spin or poll tightly.

## Key files

| File | Purpose |
| ---- | ------- |
| `README.md` | Public description of the project. |
| `AGENTS.md` | This file: workflow, conventions, current state. |
| `DESIGN.md` | Source of truth for what the project is and why: vision, MVP, design decisions, discarded ideas, future work, open questions. |
| `docs/SESSION_LOG.md` | What past sessions did. Not loaded automatically; read it only when you need that history. |
| `.github/workflows/ci.yml` | GitHub Actions CI: tests, lint and a debug build on every push to `master` and every PR. |
| `.github/workflows/release-apk.yml` | GitHub Actions release: builds the APK and creates the GitHub release with git-cliff notes when a `v*` tag is pushed. |
| `cliff.toml` | How release notes group the commits. |
| `.dashboard.json` | Status (`active`, `paused`, `done` or `idea`), next step, tags and links (`{"label", "url"}` with an `https://` url) shown on the project's card in project-dashboard. |

## Read before working on…

Topic docs for areas that need more than a line here. Add a row when you create one.

| Area | Read |
| ---- | ---- |
| Opening a PR, commit message format | `docs/PR.md` |
| Release signing, keystore, `SIGNING_*` secrets | `docs/SIGNING.md` |

## Starting a session

1. Read **Current state** at the bottom of this file.
2. Run `git log --oneline -20` and `git status` to see recent changes and any uncommitted work.
3. Skim `DESIGN.md`'s headings and read only the parts your task touches: the `D-NNN` entries for the area, and Discarded ideas before proposing something new. Don't read it in full.

## Builds, CI and releases

They run on GitHub Actions (free now that the repo is public; D-012):

- **CI:** `.github/workflows/ci.yml` runs `./gradlew testDebugUnitTest lintDebug assembleDebug` on every push to `master` and every PR. The test/lint reports and the debug APK are kept as run artifacts.
- **Releases:** pushing a version tag releases the project: `git tag v1.2.0 && git push origin v1.2.0`. `.github/workflows/release-apk.yml` tests, builds the APK (a signed release APK if the `SIGNING_*` repository secrets are set, else a debug-signed one), then creates the GitHub release with notes from [git-cliff](https://git-cliff.org/) and the APK attached. A manual run with an existing tag rebuilds and replaces the APK. There is no `CHANGELOG.md`. Release only when a milestone is reached, and ask the user before pushing a tag. Bump `versionName`/`versionCode` in `app/build.gradle.kts` in the same change.
- **Versions** are tags like `v1.2.3`; a suffix (`v1.2.3-rc.1`) makes a pre-release. Pick the number with semver from the commits since the last tag (`fix` → patch, `feat` → minor, breaking → major); `git cliff --bumped-version` suggests it.
- **Preview the notes** locally with `git cliff --unreleased --strip header`.

## Working conventions

- **`DESIGN.md` is the source of truth.** If the code and `DESIGN.md` disagree, raise it with the user instead of silently picking one. When a change makes or changes a design decision, record it as a `D-NNN` in the same change.
- **Don't re-propose discarded ideas** unless there is new information. If there is, say what changed.
- **Open a PR to `master` for every task** (this repo's default branch is `master`, not `main`). Exception: docs-only meta changes can go to `master` directly. **Read `docs/PR.md` first:** PRs are squash merged, so the PR title becomes the commit on `master` and must be a [Conventional Commit](https://www.conventionalcommits.org/). Don't wait for the user to merge it.
- **Add and run tests for new behavior** (JVM unit tests in `app/src/test`, using `FakeSerialLink` for USB). Never report work as done when tests are failing or were not run; if you couldn't run them (no Android SDK), say so.
- **Ask before irreversible or wide-reaching changes**, such as deleting files, rewriting history, changing core dependencies or reversing a locked decision.
- **Keep the docs current, and this file short.** Add what a future session needs here if it matters for most tasks, or to a topic doc otherwise. Move notes out when this file grows past roughly 10 KB.
- **Keep `.dashboard.json` current** in the same change as the work that prompts it: `next` to the new next step, `status` if it changed, and an `https://` link under `links` for anything reachable from the internet.

## Project specifics

- **Tech stack:** Kotlin, Android Views, coroutines, [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android). AGP 9, JDK 21 toolchain, `minSdk 26`, `targetSdk 36`. Versions in `gradle/libs.versions.toml`.
- **Setup:** Android Studio, or the Android SDK with `ANDROID_HOME` set.
- **Run:** `./gradlew installDebug`, then open the app, or the "Start ODBUSBToTCPBridge Service" launcher for headless auto-connect. OBD2 apps connect to `127.0.0.1:35000`.
- **Test:** `./gradlew testDebugUnitTest`
- **Lint:** `./gradlew lintDebug`
- **Release build:** a tag build attaches `OBDUSBtoTCPbridge-<tag>.apk` to the release (signed release, or `-debug.apk` without signing secrets).
- **Directory layout:** all code is in `app/src/main/java/com/faaa97/obdusbtotcpbridge/`. `ObdService` owns the run loop and reconnects; `TcpUsbBridge` moves bytes between one TCP client and the `SerialLink`; `USBDeviceSelection` probes devices with `ElmVerifier`; `LaunchServiceActivity` is the headless entry point; `ObdLogBus` logs to Logcat, the UI and a daily file.
- **Gotchas:**
  - `app/build.gradle.kts` runs `git rev-parse` to name the APK, so Gradle fails outside a git checkout or in a shallow clone without history (the workflows check out with `fetch-depth: 0`).
  - Blocking socket and USB reads don't respond to coroutine cancellation: close the socket/port on cancel (see `closeOnCancel` in `TcpUsbBridge`).

## Ending a session

Before stopping, leave the repo so the next session can pick up without extra context:

1. Commit or clearly describe any work in progress.
2. Update `DESIGN.md` with any decisions, discarded ideas, future work or open questions from this session.
3. Put gotchas a future session needs here or in the topic doc for their area.
4. Add a short entry at the top of `docs/SESSION_LOG.md`: date, what was done, and the next steps.
5. **Replace** the **Current state** section below (don't append to it): a few lines on where things stand and what's next.
6. Update `next` (and `status` if it changed) in `.dashboard.json`.

## Current state

Version 0.1.0: the MVP works and is in use in the car (see `DESIGN.md`). On 2026-11-21 the repo adopted the project template (`AGENTS.md`, backfilled `DESIGN.md`). On 2026-11-22, with the repo going public, CI and releases moved back from the build server to GitHub Actions (D-012). Releases stay debug-signed until the user adds a release keystore as repository secrets (D-011, `docs/SIGNING.md`). Log files are only ever deleted from the UI (D-010).

Next: check that CI passes on GitHub, add the signing secrets. Then Future work in `DESIGN.md`: configurable address/port, UI refresh.
