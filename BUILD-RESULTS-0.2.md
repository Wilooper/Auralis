# Auralis 0.2 Build Results

Date: 2026-10-07. Version `0.2.0-dev`, code `2`, application ID `app.auralis`.

## Changes

Automatic embedded plain, line-timed and word-timed lyrics; USLT/ULT, SYLT/SLT, lyric text frames, FLAC/Vorbis/Opus comments, MP4 lyrics atoms, APEv2, WAV/AIFF ID3 chunks and DSF metadata pointers. Common TTML timing is supported as a text payload. See [LYRICS-SUPPORT.md](LYRICS-SUPPORT.md) for format coverage and limits; this is not universal proprietary-tag support.

Lyrics load lazily for the active track, using a bounded cache and cancellation when the track changes. Plain text scrolls freely; timed lines auto-scroll and can seek; word tokens highlight against playback position. External UTF-8 lyrics files can override embedded lyrics and be reset.

Recursive SAF folder import includes subfolders, audio detection, progress, cancellation, skipped inaccessible entries and duplicate filtering. Import appends to the queue and preserves the active LibVLC deck. Queue and timeline snapshots are cached between changes to avoid rebuilding a large playlist on each position tick. Added tracks are sent in batches of 100.

The queue remains temporary and is not restored after process death. No automatic timing generation from plain text is implemented.

## Verification

JDK 17.0.20.1, Gradle 8.11.1, AGP 8.10.1, Kotlin/Compose plugin 2.2.10, Android SDK 36 / build tools 35.0.0. LibVLC 3.7.7 remains the sole audio engine; Media3 1.6.1 extractor is used only for ID3 framing/metadata.

```sh
./gradlew clean :core-model:test :app:testDebugUnitTest :app:lintDebug :core-playback:lintDebug :app:assembleDebug --write-locks --no-daemon --console=plain
```

The final clean invocation succeeded and produced ARM64, ARMv7, x86_64 and universal debug APKs. A clean rebuild resolved stale incremental packaging state from the previous build environment. Resolved dependency lockfiles were updated for the extractor and test dependencies.

| Check | Result |
|---|---|
| Core model suite | 29 tests, zero failures/errors/skips |
| Actual Media3 ID3 decoder / WAV fixture suite | 5 tests, zero failures/errors/skips |
| Total unit tests | 34 passed |
| App lint | Zero errors, one dependency-version warning |
| Playback lint | Zero errors, two dependency-version warnings |
| Debug assembly | All four configured APKs produced |
| ARM64 APK signature | Valid v2 signature, one debug signer |
| APK ZIP / 16 KiB alignment | Passed zipalign |
| Generated WAV fixture decoding | All four decoded without FFmpeg errors |
| Physical 0.1 playback | User confirmed installation and file playback |
| New lyrics/folder UI on a phone | Not run here; no attached ADB device |

The successful build emitted a nullable-Java fixture-path warning in a unit test and a native-library stripping notice; native binaries are packaged unchanged. There is no release shrinking or production signing. Evidence XML, lint reports and the final build log are in `evidence/`. Prior build evidence is preserved in [BUILD-RESULTS-0.1.md](BUILD-RESULTS-0.1.md).

Tests cover Unicode/plain text, LRC offset/repeated tags, word spans/end markers, common TTML and XML entity rejection, SYLT encoding/time units/truncation, USLT UTF-16, audio discovery, FLAC/Ogg/Opus/MP4/APEv2/container tag fixtures, malformed sizes and skipping a sparse 1 GB MP4 audio payload. App tests invoke the real ID3 decoder, including the generated playable WAV fixtures.

These are parsing/build/static packaging checks. They do not prove device rendering, provider access, cancel responsiveness, playback preservation during import, or crossfade synchronization. Follow the phone checks in `LYRICS-SUPPORT.md`. The fixtures are generated quiet tones with original Hindi/English test text, not copyrighted songs or a codec corpus.

## Install and Try

**Uninstall the previous 0.1 prototype before installing this 0.2 APK.** The development signing certificate changed because the previous temporary build key was unavailable. Android cannot install this APK as an update over a different signer. Uninstalling clears the prototype's settings and grants; select your files/folder again afterward. Your original music files are not removed by uninstalling the app.

The new development key is saved separately in `Auralis-Debug-Signing.zip`. Keep it for future development builds; restore its `debug.keystore` to the build host's `~/.android/debug.keystore` to retain the signer. It is excluded from the source archive and must never be used for production releases.

1. Install `Auralis-0.2.0-arm64-debug.apk` on the ARM64 phone that ran the original prototype.
2. Select **+ → Music folder (with subfolders)**. Add another folder during playback, then reimport it to check duplicate filtering.
3. Play a track and open **Lyrics**. Timing appears only when the file actually contains supported embedded timestamps.
4. Extract `Auralis-Lyrics-Test-Files.zip`, import its folder, and try all four examples. They exercise plain USLT, line LRC, native word SYLT and enhanced word LRC.

## Delivered APK

- Size: 76,251,032 bytes, approximately 72.7 MiB.
- SHA-256: `627e73e19233309c06ffea987b2748b70c5a51c057f3d9e763c9977abc0912ce`.
- Signer certificate SHA-256: `bf474b8376bc594d1e210b701835a334a8abae4747078a5cd75a739502026d21`.
- Android 8.0+ / ARM64; development prototype.

The source archive contains Kotlin source, Gradle wrapper/locks, tests, generated fixtures, their build-time generator, documentation and evidence. It excludes SDK/JDK installations, build caches/output APKs, Git internals and signing keys. Python is used only to generate fixture files on a development machine and is not in the Android app or required to build the included source/fixtures.
