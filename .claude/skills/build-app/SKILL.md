---
name: build-app
description: Build, lint and unit-test the Autodict Android app. Use when asked to build the app, compile, run the debug APK, run lint, or run the JVM unit tests for this Kotlin/Compose project. Knows the environment caveats (Android SDK + Google Maven needed).
---

# Build the Autodict Android app

Autodict is a Kotlin + Jetpack Compose Android app (single `:app` module, Gradle Kotlin DSL,
version catalog in `gradle/libs.versions.toml`).

## Preconditions

Building REQUIRES:
1. **JDK 17+** (this environment has JDK 21 — fine).
2. **Android SDK** with `compileSdk 35`. If `ANDROID_HOME`/`ANDROID_SDK_ROOT` is unset, the
   SDK is missing.
3. **Network access to Google's Maven repo** (`maven.google.com` / `dl.google.com`) for the
   Android Gradle Plugin and AndroidX/Compose artifacts.

⚠️ In the default Claude Code web/remote environment the Android SDK is absent AND Google's
Maven repo is network-blocked (403). In that case the build CANNOT succeed here — say so
plainly rather than retrying. Real builds happen on a developer machine or CI with the SDK
and Google Maven access.

To check quickly:
```bash
echo "ANDROID_HOME=$ANDROID_HOME"
curl -so /dev/null -w "%{http_code}\n" https://maven.google.com/   # 200 = ok, 403 = blocked
```

## Commands

```bash
./gradlew :app:assembleDebug   # build the debug APK -> app/build/outputs/apk/debug/
./gradlew test                 # JVM unit tests
./gradlew lint                 # Android lint -> app/build/reports/lint-results-*.html
```

Use `--offline` if dependencies are already cached. First run downloads the Gradle
distribution (8.9) and dependencies.

## Notes

- Runtime behaviour (recording, audio playback, on-device transcription) needs a real device
  or emulator with a microphone — it cannot be verified from a headless build.
- If a build fails on plugin/dependency resolution with a Google Maven host error, that is the
  network-policy block described above, not a code bug.
