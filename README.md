# Auralis — 0.5.0-dev

File-based extensions now contribute HTTPS music providers, server-hosted party queues, isolated iframe players and declarative UI skins through granular capability grants. Open Settings → Extensions & internet to import/review/toggle extensions or customize appearance. Community code executes on servers, never as phone plugins.

See [SDK guide](sdk/README.md), [REST OpenAPI](sdk/openapi.json), [manifest schema](sdk/manifest.schema.json), [host bridge schema](sdk/host-bridge.schema.json), and [v5 notes](V5-SDK.md).

## Android player

A local-first Android music player. The current development prototype uses Kotlin/Jetpack Compose, LibVLC as the sole audio engine, and Media3 for Android media-session integration. There is no ExoPlayer dependency and no Python/Dart runtime in the app.

## Local player features (v4 foundation)

- Android 8.0+ (API 26); compile/target API 36.
- Saved folder/file sources and a private SQLite music index that survives process death.
- MediaStore metadata queries and change notifications for selected on-device folders; recursive SAF fallback.
- Home with recently added/played songs, songs/artists/albums browsing and Unicode song/artist/album search.
- Lazy embedded artwork with a bounded disk cache, song-row covers and a mini player.
- Saved queue and position checkpoint; recovery is paused until you press Play.
- New dark discovery layout with cover shelves, album/artist grids and a rose default accent.
- Immersive player and large synchronized lyrics over a cached soft album-art backdrop.
- Floating mini player; Music/Lyrics switching and lyrics follow that pauses while reading ahead.
- LibVLC decoding, play/pause/seek, previous/next, shuffle and repeat off/all/one.
- Persistent Favorites and service-recorded History; Home/Favorites/Queue/Lyrics navigation.
- Dedicated Settings for all music folder/file management.
- Queue Play next, move and remove actions.
- Cached, opt-in local audio analysis and 25-song gradual Smart Flow previews.
- Embedded artwork and platform-readable metadata, with filename fallback.
- Service-owned playback, media notification/session, audio focus and unplug pause.
- Automatic embedded lyrics: plain text, line LRC, enhanced word LRC, ID3 SYLT and common TTML timing.
- Word highlighting, timed line scrolling/click-to-seek, and manual lyrics attachment with embedded reset.
- Background folder indexing, progress/cancellation and duplicate filtering without restarting playback.
- Five accent colors, persisted accent and crossfade settings; earlier selected colors are retained.
- Two-deck crossfade and smooth DJ volume blend, disabled by default; no beat matching.

See [V4-FINISH.md](V4-FINISH.md) for usage, limitations and the phone checklist.

Compilation is not device verification. Read [BUILD-RESULTS.md](BUILD-RESULTS.md) and [M0-DECISION.md](M0-DECISION.md) before treating any audio behavior as validated.

## Build

Install JDK 17 and Android SDK platform 36 / build tools 35.0.0. Android Studio can install these. Set `ANDROID_HOME` to your SDK directory, or put `sdk.dir=/absolute/path/to/sdk` in an untracked `local.properties` file.

```sh
./gradlew :core-model:test :app:testDebugUnitTest :core-playback:lintDebug :app:lintDebug :app:assembleDebug
```

Windows: use `gradlew.bat`. Dependencies and wrapper distribution are pinned, with lockfiles for the verified configurations. The Gradle distribution checksum is verified; builds still require network access for dependencies on first use.

APK outputs are in `app/build/outputs/apk/debug/`. Prefer the `arm64-v8a` APK on a modern Android phone; the universal APK includes multiple native ABIs and is substantially larger. All prototype APKs are debug-signed, not release artifacts.

## Try It

1. Open **Settings** to add a music folder or individual files, then allow music access for the fast Android index. Saved folders and songs return after killing/reopening the app. Folder grants from 0.2 are recovered where Android still retains them. Actual decoding support depends on LibVLC.
2. Play a track and open **Lyrics**. Embedded lyrics load automatically for the current track. Word sync highlights timed words; line sync scrolls to the current line; plain lyrics remain freely scrollable. Attach an LRC, TXT or supported TTML file with the folder icon; the restore icon switches back to embedded lyrics.
3. Open **Settings** to change the accent or enable crossfade/DJ blend. Open **Extensions & internet** to import extensions, approve permissions, connect providers or edit appearance.
4. Tap the floating mini player to expand. Use Music/Lyrics below the player, the queue shortcut, or the collapse arrow/Android Back. Scroll timed lyrics to read ahead; use Follow lyrics to return, or wait six seconds.
5. Test media controls and screen-off behavior using [DEVICE-CHECKLIST.md](DEVICE-CHECKLIST.md).

Indexing adds songs to the library independently of the active queue. Tap a song to play the current browse result as a queue. Cancel keeps batches already saved. Queue snapshots and position are saved periodically and restore paused after process death. Manual lyrics associations remain in-memory; embedded lyrics are reread as needed.

New downloads in a saved on-device folder appear after Android indexes them and emits a media change. Reopening/foregrounding also refreshes the index. Non-indexed or unusual providers use a slower directory fallback; an app that is force-stopped cannot receive live changes. Read [LIBRARY-PERFORMANCE.md](LIBRARY-PERFORMANCE.md) for architecture, host measurements and phone checks.

Read [LYRICS-SUPPORT.md](LYRICS-SUPPORT.md) for supported embedded tags, limits and test scope. Lyrics synchronization requires timestamps already present in the file; this version does not generate timing from plain text.

Read [UI-DESIGN.md](UI-DESIGN.md) for the default design, rendering approach and device review checklist. The listening screens draw inspiration from Apple Music; discovery combines ideas from Spotify, YouTube Music and Tidal with original Auralis layouts.

## Modules

| Module | Responsibility |
|---|---|
| `app` | Home/search, SQLite/MediaStore/SAF index, lazy artwork, queue checkpoints, playback service/session |
| `core-model` | Library identity/search rules, lyrics/container parsing, audio discovery and crossfade policy |
| `core-playback` | Native deck lifetime, queue execution, Media3 custom Player, audio focus |

The project currently uses three Gradle modules. Extension registry, bridge, manager and network implementations live under `app/src/main/kotlin/app/auralis/extensions`; community contracts and authoring helpers live in `sdk/`. Extract additional modules as the contracts stabilize.

## Next Gates

First: validate cache startup, automatic downloads, covers and lifecycle behavior on an ARM64 phone. Next validate Smart Flow, DJ blend and the v5 extension bridge on a diverse real library. Future work includes saved playlists, command playlists, deeper recommendations, service-specific adapters, synchronized parties and extension-store distribution.

No trained ML model, phone-hosted browser sharing server, executable on-device plugin runtime, extension store or equalizer is implemented. Declarative provider/skin/embed manifests, HTTPS playback and a theme editor are implemented; party mode currently shares server queues. Smart Flow uses cached acoustic heuristics. No promise of universal codec support is made. File grants can be revoked; such failures must be surfaced and tested.

## Termux

Editing Kotlin source and installing/testing APKs on your phone is practical. The official Linux Android SDK build binaries used here target x86_64 hosts, not Termux's ARM64 environment. The included CI workflow or a Linux/Android Studio build host is the supported build path for now. Do not assume the desktop SDK can be installed unchanged in Termux.

## License

Original Auralis source uses Apache-2.0 as an initial open-source default. LibVLC and other dependencies retain their own licenses. Read [THIRD-PARTY.md](THIRD-PARTY.md). Native distribution compliance, package-name ownership and name/trademark clearance remain public-release gates.

## Community development

Start with [CONTRIBUTING.md](CONTRIBUTING.md) for builds, tests and extension contributions. See [SECURITY.md](SECURITY.md) for the bridge boundary and reporting guidance. The [planning pack](docs/planning/README.md) records the original design and backlog; [V5-SDK.md](V5-SDK.md) and the implemented SDK contracts describe the current behavior.

CI runs Android tests, lint and a debug APK build, plus the SDK/reference-server tests. CI APKs use an ephemeral development certificate and cannot update the previously delivered phone builds; keep your existing private development signing key outside the repository for compatible local updates.
