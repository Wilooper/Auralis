# Contributing to Auralis

Auralis is an Android-first Kotlin/Compose music player using LibVLC. Extension files are declarative JSON; community business logic runs on servers behind the documented HTTPS API.

## Build and check

Use JDK 17, Android SDK platform 36 and build tools 35.0.0. Set `ANDROID_HOME` or an untracked `local.properties`.

```sh
./gradlew :core-model:test :app:testDebugUnitTest :core-playback:lintDebug :app:lintDebug :app:assembleDebug
python3 -m unittest discover -s sdk/python -p 'test_*.py'
```

Generated APKs are in `app/build/outputs/apk/debug/`. Device audio/lifecycle checks are recorded separately in DEVICE-CHECKLIST.md; passing unit tests does not establish audible crossfade quality. CI uses a fresh debug certificate, so its APKs cannot update an existing installation signed with a different key.

## Extensions

Read sdk/README.md, manifest.schema.json, host-bridge.schema.json and openapi.json. Copy an example, validate it with the SDK helper, then import it through Settings → Extensions & internet. Provider adapters must return the documented catalog and direct audio contract. Native binaries, DEX, scripts and unrestricted device commands are not extension contributions.

## Changes

Use a focused branch and describe the user-visible behavior, validation and remaining limits in the pull request. Update schemas/examples/docs together when contracts change; retain explicit API version checks and permission revocation. Include regression coverage for playback or security changes, and report device model/Android version for device-specific behavior.

Do not commit signing keys, server credentials, private audio, SDK installations or build caches. Use original/generated audio fixtures with clear provenance. The original planning pack under docs/planning is historical; current code and v5 contracts govern implemented behavior.

## Termux

You can edit source and run the Python SDK tooling in Termux. Use the GitHub Actions workflow or an Android Studio/Linux build host for Android APKs; the supported desktop SDK toolchain is not an ARM64 Termux build environment.
