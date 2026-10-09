# Auralis M0 Build Results

Date: 2026-10-07. Version: `0.1.0-dev` (code 1). Application ID: `app.auralis`.

## Environment

Linux x86_64 build host. JDK 17.0.20.1, Gradle 8.11.1, AGP 8.10.1, Kotlin/Compose compiler 2.2.10, Android SDK 36, build tools 35.0.0. LibVLC Android 3.7.7; Media3 Common/Session 1.6.1. No custom NDK or C++ source is required for this prototype.

The environment initially had only a Java runtime. Official build tools and SDK packages were installed. Native dependency metadata required upgrading the initial SDK-35 configuration to SDK 36; Kotlin pins were aligned with LibVLC's published standard-library dependency. Tools are not bundled in the source archive.

## Commands and Outcomes

With `JAVA_HOME` pointing to JDK 17 and `ANDROID_HOME` pointing to the SDK:

```sh
./gradlew :core-model:test :core-playback:lintDebug :app:lintDebug :app:assembleDebug --write-locks --no-daemon --console=plain
./gradlew clean :core-model:test :core-playback:lintDebug :app:lintDebug :app:assembleDebug --offline --no-daemon --console=plain
./gradlew :app:assembleDebug --offline --no-daemon --console=plain --rerun-tasks
```

All three final commands succeeded. Dependency resolution used approved network access initially. Offline builds require a populated dependency cache; they are not evidence that a fresh checkout builds without Internet.

The combined clean/lint/assemble invocation emitted only the universal APK. The subsequent standalone assembly regenerated all configured ABI APKs. If split outputs are missing after checks, run the standalone assembly command above.

| Check | Result | Evidence |
|---|---|---|
| Pure Kotlin tests | PASS: 9 tests, 0 failures/errors/skips | `evidence/model-tests.xml` |
| Playback module lint | PASS: 0 errors, 2 warnings | `evidence/playback-lint.txt` |
| App lint | PASS: 0 errors, 6 warnings | `evidence/app-lint.txt` |
| Debug APK assembly | PASS: ARM64, ARMv7, x86_64, universal | `app/build/outputs/apk/debug/` |
| Cached clean offline build | PASS | Commands above |
| ARM64 APK signature | PASS: v2, one debug signer | `apksigner verify --verbose` |
| ARM64 APK ZIP/16 KB alignment | PASS | `zipalign -c -P 16 4` |
| LibVLC ARM64 ELF LOAD alignment | PASS: 0x4000 in inspected three native libraries | `readelf -lW` |
| Physical playback / UI / codec tests | NOT RUN | No device attached |

Remaining lint warnings only identify newer dependency/tool versions. Pins were kept for this verified configuration; no error baseline was used. The exported-service warning is locally suppressed because Media3 needs external system controllers, with untrusted controllers rejected in `onConnect`; trusted external controllers cannot replace the queue or set the custom crossfade preference.

Native packaging reports that libraries could not be stripped and packages them as-is. No release shrinking, native-size optimization or release signing has been done.

## Delivered ARM64 APK

- Filename: `Auralis-0.1.0-arm64-debug.apk`
- Size: 75,808,664 bytes (about 72.3 MiB)
- SHA-256: `dc9bd1d93a0190bd9ac577b4d00ea30467cf536078336003ea6042915c348f97`
- Android 8.0+ / ARM64. Debug-signed; not a store-ready release.

The source archive excludes build caches, generated APKs, SDK/JDK installations, Git internals and signing keys. It includes wrapper scripts/JAR, dependency locks, source, license texts, evidence and device test instructions. The APK is provided separately.

## What the Tests Establish

LRC multi-tag parsing, fraction widths, invalid seconds, metadata handling, offsets, seek-to-earlier-line lookup and empty input. Crossfade gain endpoints/headroom/monotonicity, waiting for readiness, paused clock behavior, cancellation/restart and invalid duration handling.

These tests do not instantiate a LibVLC native player or prove audible overlap. Native playback, audio-focus integration and service/session runtime behavior are not covered by this unit suite.

## Unrun Gates

`adb devices -l` returned no attached devices. No hardware-accelerated emulator endpoint (`/dev/kvm`) was available. No emulator, instrumentation, screenshot, output recording, battery benchmark, memory profiling or codec corpus test was run.

Static ELF/APK alignment checks do not prove playback on a 16 KB-page Android device. UI and screen-off/background behavior still need device verification. See [DEVICE-CHECKLIST.md](DEVICE-CHECKLIST.md) and [M0-DECISION.md](M0-DECISION.md).

The broad-format native engine also makes this development APK larger than a small platform-only player. The future small local recommendation model is not present; there is no AI-model size or speed result to report.
