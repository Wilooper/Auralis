# Auralis 0.3 Build Results

Date: 2026-10-07. Version `0.3.0-dev`, code `3`, application ID `app.auralis`.

## Delivered behavior

- Folder grants, sources and track metadata are saved in a private SQLite index. Killing/reopening the app no longer requires selecting the same folder again. Retained 0.2 file/tree grants are recovered on upgrade where Android still provides them.
- Home loads the saved library independently of indexing and playback connection. It includes recent additions/plays, songs/artists/albums browsing, song/artist/album search, pagination, folder management and a mini player.
- Optional music permission enables bulk MediaStore metadata queries for selected on-device folders. Content observers refresh after indexed media changes; foreground refresh and a 30-second process-local fallback handle other providers. Android must finish indexing a new download before the fast path can discover it.
- Recursive SAF scans discover non-indexed audio, stream saved batches and retain cached records when a folder becomes unavailable. Library updates do not replace the active playback queue.
- Covers load lazily from embedded pictures or Android album art, with sampled images, two concurrent extraction slots and a bounded disk cache. Scanning does not extract artwork. Missing art uses a placeholder.
- Queue and position checkpoints restore paused after process death. Embedded plain/line/word lyrics, LibVLC decoding and existing media controls remain.

Kotlin remains the application language; LibVLC remains the native C/C++ decoder. The change removes repeated file/tag/image reads rather than adding Rust to the same slow import pipeline. See [LIBRARY-PERFORMANCE.md](LIBRARY-PERFORMANCE.md) for implementation, limitations and phone checks.

## Verification

JDK 17.0.20.1, Gradle 8.11.1, AGP 8.10.1, Kotlin/Compose 2.2.10, Android SDK 36 / build tools 35.0.0. No new application runtime dependency was added. Robolectric 4.16.1 is test-only.

```sh
./gradlew :core-model:test :app:testDebugUnitTest :app:lintDebug :core-playback:lintDebug :app:assembleDebug --write-locks --no-daemon --console=plain
```

The verification run passed after a test-only Robolectric AutoCloseable mismatch was corrected to explicit database closure. A subsequent packaging pass removes the unused former importer and recompiles the app. The last lint/assembly invocation uses cached dependencies in offline mode because the resumed environment blocked a remote dependency-version check.

| Check | Result |
|---|---|
| Core suite | 33 tests, zero failures/errors/skips |
| App suite | 16 tests, zero failures/errors/skips |
| Total | 49 passed |
| App lint | Zero errors; KTX/style suggestions |
| Playback lint | Zero errors; two dependency-version warnings |
| Assembly | ARM64, ARMv7, x86_64 and universal debug APKs |
| ARM64 signature | Valid v2 signature; same certificate as 0.2 |
| ARM64 ZIP / 16 KiB alignment | Passed zipalign |
| Physical-device testing | No attached ADB device; not run here |

Tests retain the lyric/container fixtures and add saved-library reopening, literal wildcard/Hindi searches, folder boundaries, overlapping source membership, changed metadata invalidation, history preservation, queue checkpoint recovery, permission-denied indexing and provider-notification discovery without folder reimport. Robolectric uses native SQLite and an API 28 test environment; it is not an Android 16/OEM device certification.

Native binaries are packaged unchanged when stripping tools cannot process them. APKs remain development/debug builds; no release shrinking or production signing. Evidence contains test XML, lint output, build logs and the benchmark JSON. Previous reports are preserved in [BUILD-RESULTS-0.2.md](BUILD-RESULTS-0.2.md) and [BUILD-RESULTS-0.1.md](BUILD-RESULTS-0.1.md).

## 10,000-track host measurement

| Operation | Milliseconds |
|---|---:|
| Initial SQLite index | 2814.00 |
| Cached 500-track page | 34.95 |
| Artist search | 26.07 |
| Unchanged-record check | 230.35 |
| One changed record | 3.52 |

Cached browsing opens zero audio files. These are Linux-host Robolectric measurements, not phone launch latency, frame timing, provider scan speed or battery measurements. The JSON records the exact values and environment. Phone acceptance remains necessary, especially for OEM download notifications, large folders, thumbnails, SD storage and process-death playback recovery.

## Install

**Install this APK over 0.2 without uninstalling.** Version code increased to 3 and the signing key is unchanged. Uninstalling would clear the app index, settings and grants. Original music files are never deleted by source removal.

1. Install `Auralis-0.3.0-arm64-debug.apk` on an ARM64 Android 8.0+ phone.
2. Open Home. Allow music access for automatic MediaStore updates. Existing grants are recovered where possible; otherwise choose your folder once.
3. Let the initial update finish, kill/reopen, and confirm the folder and song list remain.
4. Download a song into that folder while Home is open. Check discovery after Android indexes it; try title and artist searches.
5. Test files containing art, then playback, queue recovery and embedded lyrics. Follow the full checks in `LIBRARY-PERFORMANCE.md`.

Keep the separately delivered `Auralis-Debug-Signing.zip` from 0.2 for future development builds. It is excluded from source and must not be used for a production release.

## APK identity

- Size: 76,365,900 bytes, approximately 72.8 MiB.
- SHA-256: `38d9cb5548a6e85fd2b5a75092dd5582869580c14a9174eaf266c1d184bf6d72`.
- Signer certificate SHA-256: `bf474b8376bc594d1e210b701835a334a8abae4747078a5cd75a739502026d21`.

The source archive includes source, wrapper/locks, tests, original generated fixtures, documentation and evidence. It excludes SDK/JDK installations, build caches/APKs, Git internals and signing keys. Python only generates development test fixtures; it is not shipped in the app.

Still pending: shuffle/repeat, queue editing, saved playlists, full UI customization, extension APIs, online providers and recommendations. This update prioritizes the reported persistence, browsing and performance problems.
