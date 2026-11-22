# AURA OS

**AURA OS v0.1** — an Android launcher concept built around a blue Liquid Glass visual language and lightweight productivity tools.

## Current MVP
- Android Home launcher entry point (Android 12+).
- Live clock and date.
- Quick task list with completion state.
- 25-minute focus timer.
- App drawer with installed launchable apps and search.
- GitHub Actions debug APK build.

## Build
GitHub Actions runs on pushes to `main` and via **Actions → AURA OS Android APK → Run workflow**.

The generated artifact is named `AURA-OS-v0.1-debug`.

## Requirements
- JDK 17
- Gradle 8.9
- Android SDK platform 35 (downloaded by the Android Gradle Plugin)
- Android 12 (API 31) or newer

## Notes
This is an installable Android launcher, not a replacement Android firmware/ROM. Choose **AURA OS** in Android's Home app chooser to use it as the default launcher.
