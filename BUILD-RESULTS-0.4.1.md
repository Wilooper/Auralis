# Auralis 0.4.1 — v4 finish

Built 2026-10-08. Version `0.4.1-dev`, Android version code `5`, application ID `app.auralis`. This is a v4 update; it does not start the v5 feature milestone.

## Implemented

- A full Settings screen owns all music folder and file management. Add/change/remove, refresh, music-access permission, appearance, DJ blend, crossfade and history clearing are available there. Change-folder cancellation preserves the previous source.
- Home / Favorites / Queue / Lyrics bottom navigation. The mini player opens Now Playing. Top tabs: Songs, Artists, Albums, History, Recently added.
- Persistent favorite buttons in library rows and full player. Latest-played history is recorded from the playback service when a song starts, including background transitions.
- Playback-service shuffle and repeat off/all/one. An explicit ordered Media3 timeline keeps next/previous and auto advance consistent with LibVLC shuffle traversal. Modes are saved. Repeat-one does not crossfade.
- Queue actions: Play next, Move up/down, Remove. Play next disables shuffle to honor the immediate choice. Removing the final song clears the saved queue.
- Smart Flow: opt-in local sample decoding, cached energy/brightness/tempo heuristics, Wind down / Stay similar / Build energy, preview and play up to 25 distinct analyzed songs. Completed/unsupported attempts are retained by file fingerprint; cancellation keeps progress. Playing a flow disables shuffle to preserve the sequence.
- DJ blend: two-deck overlap with a smooth volume envelope and 40-ms ramp updates. Gain sum remains capped at one. Settings supports Off–12 seconds, with an eight-second initial DJ preset.

No app runtime dependency was added; Kotlin/Compose/LibVLC remain the stack. LibVLC is the playback engine. Android's decoder is used only for explicit analysis, so an analysis-incompatible file can still play through LibVLC.

See [V4-FINISH.md](V4-FINISH.md) for behavior, detailed limitations and device checks.

## Verification

Toolchain: JDK 17.0.20.1, Gradle 8.11.1, Android Gradle Plugin 8.10.1, Kotlin/Compose 2.2.10, SDK 36, build tools 35.0.0.

Command:

```sh
./gradlew :core-model:test :app:testDebugUnitTest :app:lintDebug :core-playback:lintDebug :app:assembleDebug --no-daemon --console=plain
```

| Check | Result |
|---|---|
| Pure Kotlin model tests | 40 passed |
| Android/Robolectric tests | 20 passed |
| Total | 60 passed; zero failures/errors |
| App lint | Zero errors; 13 version/KTX suggestions |
| Playback lint | Zero errors; two dependency-version warnings |
| Build | ARM64, ARMv7, x86_64 and universal debug APKs produced |
| ARM64 signature | Valid v2 signature |
| Certificate continuity | Same development certificate as 0.2/0.3/0.4.0 |
| Alignment | zipalign and 16-KiB alignment verification passed |

New tests cover database v1→v2 migration without losing songs/history, favorite persistence and refresh, analysis fingerprint invalidation, history clearing, stable shuffle traversal/previous/wrap, gradual-flow uniqueness/direction, synthetic 120-BPM estimation, silent/malformed input, and bounded DJ gain shaping. Existing 10,000-song index, lyrics, MediaStore and queue-checkpoint tests remain.

An initial migration test failed because a test-only `use` cast relied on a newer Android AutoCloseable API. The test was changed to explicit try/finally close and the complete suite passed. No production database API was changed to depend on that newer method.

## Limits

Smart Flow estimates acoustic energy and tempo from an excerpt. It does not recognize emotion, song language or lyrical meaning, and its confidence flag is not a calibrated probability. Ordering quality depends on the songs available. Unsupported/silent profiles are skipped and cached. Initial analysis is user-started and must be resumed manually after process death.

DJ blend is a shaped volume crossfade, not beat matching, tempo warping, key matching or calibrated loudness normalization. No physical phone was connected here; audible overlap quality, Bluetooth/focus behavior, battery cost, frame timing and real Hindi/Punjabi playlist quality still require phone testing. Broad codec support does not guarantee matching Android analyzer codec coverage.

## Install and quick use

Install over 0.4.0 without uninstalling to retain the saved library, queue, grants and settings. Use Settings to manage sources, heart tracks to populate Favorites, and tap the mini player for shuffle/repeat/favorite controls. Use the sparkle button in Home to analyze songs and preview a flow. Start with 6–8 seconds for DJ blend.

This remains a development debug build. Production publishing, provider plugins, sharing and the distinct v5 roadmap remain outside this release.

APK SHA-256: `85ee24769f3d44cd815e6c00bd9c86346be59b064b1e672a972eae67c36d34c7`. Size: 76480588 bytes.


## Emulator review

Actual API 28 x86_64 app screens at 720×1440 / 320 dpi were inspected: Home, Settings, populated mini player, full-player controls, Favorites and Smart Flow. The controls fit the test display. A full-player heart produced a Favorites row with its embedded cover. Native decoding analyzed all three generated MP3 signals, saved 3/3 usable profiles, and produced a three-song preview. No AndroidRuntime crash was logged during those checks. Actual captures are in `evidence/v041/screens/`.

The populated library was supplied through a test-only database fixture with valid track IDs and generated audio files. This checks rendering and data/playback/analyzer integration; it does not validate the DocumentsUI folder grant flow. That legacy emulator picker returned null automation roots intermittently. An initial streaming APK install timed out, and a pushed APK installed successfully with `pm install`. These are emulator limitations, not phone latency measurements. Audible DJ blend and full physical-device lifecycle gates remain open.
