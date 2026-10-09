# Auralis 0.4 Build Results

Date: 2026-10-08. Version `0.4.0-dev`, code `4`, application ID `app.auralis`.

## Default interface

Dark discovery screens combine editorial headings, a featured real track, horizontal cover shelves and two-column artist/album grids. The new rose accent is the default when no color was saved; earlier selected accents retain their indices. Search, saved sources and recent history continue to use the existing library.

The floating mini player has compact cover art, metadata, play/pause/next and a progress rail. It opens an immersive player with a cached soft album-art backdrop, large cover, white transport controls and Music/Lyrics/Queue shortcuts. Android Back and the collapse arrow return to Home. Covers reserve room for controls on short displays and account for font scale; very short layouts and long titles remain scrollable. System bars use light icons against the dark interface. Unknown duration is shown as unknown until the restored track prepares.

Lyrics use large bold text, bright current lines/words and subdued surrounding lines over the same artwork atmosphere. Reading ahead pauses follow; a button resumes it immediately, or it resumes after six seconds without a gesture. Plain lyrics remain freely scrollable. Attach/reset controls and transport remain available. The visual review exposed an FFmpeg text-tag convention, `TXXX:USLT`; recognizing USLT/ULT text labels now lets those embedded lyrics display as well.

No app framework or runtime dependency was added. The backdrop is a static remembered Coil request, sampled to 96 pixels and blurred at 64 pixels off the UI thread. No continuously animated shader, microphone analyzer or scan-time cover extraction is introduced. Original Auralis layouts are inspired by Apple Music's listening screens and Spotify/YouTube Music/Tidal discovery ideas. Third-party logos, screenshots, artwork and fonts are not bundled.

See [UI-DESIGN.md](UI-DESIGN.md) for screen details, rendering and device review. Persistence, MediaStore updates, LibVLC decoding and prior embedded lyric formats remain.

## Verification

Toolchain: JDK 17.0.20.1, Gradle 8.11.1, AGP 8.10.1, Kotlin/Compose 2.2.10, SDK 36 / build tools 35.0.0. Existing tool/dependency caches were restored after the environment reset. Final verification used cached dependencies:

```sh
./gradlew :core-model:test :app:testDebugUnitTest :app:lintDebug :core-playback:lintDebug :app:assembleDebug --offline --no-daemon --console=plain
```

| Check | Result |
|---|---|
| Core suite | 33 passed, zero failures/errors |
| App suite | 17 passed, zero failures/errors |
| Total | 50 passed |
| App lint | Zero errors, ten KTX/style suggestions |
| Playback lint | Zero errors, two dependency-version warnings |
| Debug assembly | ARM64, ARMv7, x86_64 and universal APKs |
| ARM64 signature/alignment | Valid v2 debug signature; zipalign/16 KiB check passed |
| Signing continuity | Same certificate as 0.2 and 0.3 |
| Physical-phone rendering/performance | Not tested here |

Tests retain library reopening/search/reconciliation, queue checkpoints, media notifications, lyrics/container formats and crossfade policy. A new real-Media3 decoder test reproduces `TXXX:USLT` and checks its line timestamps. Generated test audio is original quiet/silent material; no copyrighted music is used.

## Visual review

The app is inspected on an API 28 x86_64 Android emulator at 720 × 1440 pixels / 320 dpi (360 × 720 dp). Original MP3 fixtures with embedded covers and timed text populate a test-only library; this setup checks rendering, not folder-grant acquisition. Screenshots in `evidence/v04/screens/` show the actual app, not a standalone design mockup.

The emulator has no hardware acceleration. An API 35 test image first suffered system-service boot failures; the API 28 image also showed a temporary System UI ANR during boot before recovering. These environment failures are not app performance measurements. Raw framebuffer captures avoid occasional truncated PNG output from the emulator. The reviewed layout caught low system-bar contrast and short-screen transport clipping; both were corrected before the delivered APK.

A physical phone must still verify cold/warm launch time, smooth scrolling, battery cost, seek gestures, audio behavior, Hindi/long titles, enlarged fonts, landscape, missing art and word-sync appearance. Existing device gates in [DEVICE-CHECKLIST.md](DEVICE-CHECKLIST.md) remain. Compilation and screenshots do not prove universal codec, tag or device support.

## Install

**Install over 0.3 without uninstalling.** Version code is 4 and the signing key is unchanged. The same APK can update the 0.2 development build. Uninstalling would clear app settings, library and grants.

1. Install `Auralis-0.4.0-arm64-debug.apk` on an ARM64 Android 8.0+ phone.
2. Check Home/search and the artist/album grids using your saved collection. Existing selected colors are preserved; choose rose in settings if you previously chose another color.
3. Play a song, tap the floating mini player, then switch between Music and Lyrics. Confirm transport controls stay reachable.
4. Read ahead in timed lyrics, resume follow, attach/reset a lyric file and check plain text too.
5. Kill/reopen and check saved folders/queue again. Check screen-off/notification/headset playback and crossfade on your phone.

The default design is one coherent hybrid, not separate Apple/Spotify/Tidal theme presets. Full component customization, shuffle/repeat, favorites, queue editing, saved playlists, providers/plugins and recommendations remain future work.

## APK identity

- Size: 76,629,761 bytes, approximately 73.1 MiB.
- SHA-256: `80fcfbccb888568f3c97954c3cac68bcc54418add60981cd4a573957b26aae76`.
- Certificate SHA-256: `bf474b8376bc594d1e210b701835a334a8abae4747078a5cd75a739502026d21`.

This is a debug development build, not a production/store release. The source archive includes code, wrapper/locks, tests, generated lyric fixtures, documentation and evidence; SDK/JDK installations, caches, output APKs, Git internals and signing keys are excluded. Keep the previously delivered development signing archive for subsequent updates. Python is only a development fixture/verification tool, not an app runtime.

Previous reports are preserved as `BUILD-RESULTS-0.3.md`, `BUILD-RESULTS-0.2.md` and `BUILD-RESULTS-0.1.md`.
