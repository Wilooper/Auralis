## Artwork Motion / manager acceptance checks (0.7.0)

- Import the bundled artwork manifest, grant artwork.apply, and verify existing skin colours remain selected.
- Customize: compare Drift, Tilt and Still, test touch and mouse hover, scroll across the cover and verify it does not capture scrolling. Save 60%, 90% and 100%; verify no transport overlap in portrait, landscape or large fonts. Cancel must not persist edits.
- Restart ReVa: size/motion persist. Disable/re-enable: settings return. Remove/update: active effects disappear; re-import requires review.
- Try two artwork extensions: last enabled wins and disable/removal restores the previous one. Verify the manager identifies the active contribution.
- Pause, open notification shade / switch apps, enable Battery Saver and set animator duration scale to zero: motion stops. Resume with animation enabled: motion returns. Still and list thumbnails must have no animation loop.
- Filter and search both manager types, including no matches and narrow screens. Stop a native task without cancelling another extension's task. Expand/collapse diagnostics.
- Measure frame times, memory and battery on a real device; desktop tests do not establish animation smoothness, thermal behaviour or power consumption.

# ReVa 0.6.1 base-hardening checks

- Upgrade a populated 0.6.0 install using the same signing key: songs, selected folders, favorites, history and queue recovery should remain. The Smart Flow button should be gone. Only the old computed-profile table is removed; debug builds signed with a different temporary key cannot perform this upgrade test.
- Play tracks while changing queue order, toggling shuffle/repeat and stepping previous/next. The displayed queue should remain stable and the active song should not change just from toggling shuffle.
- Enable Track Inspector and invoke its action repeatedly on the same install. Results should not intermittently fail with a busy-worker error after the previous panel completes.
- Check foreground-only task cancellation, explicit background grants, returning after sleep, repeated refresh taps, permission revocation and package replacement. Audio playback should continue while unrelated compute tasks are cancelled.
- Record worker/process cleanup, frame timing, battery and total memory separately; desktop tests do not establish these device measurements.

## Earlier player checklist

# M0 Device Checklist

Status: NOT RUN. Fill this in on a physical device before passing M0.

## Device Record

- Device / chipset / ABI:
- Android version / API / OEM:
- Auralis version / APK hash:
- Output route (speaker, wired, USB, Bluetooth):
- Cold/warm build mode / battery / temperature:

## Local Playback

| Check | Result | Evidence |
|---|---|---|
| Import valid MP3 CBR/VBR, FLAC, WAV, AAC/M4A, Ogg Vorbis, Opus | NOT RUN | |
| ALAC, AIFF, WMA, APE, WavPack | NOT RUN | |
| DSF/DFF, MIDI, tracker files: explicit success/unsupported outcome | NOT RUN | |
| Files with Unicode names, long titles, unusual tags | NOT RUN | |
| Embedded artwork / missing artwork / large artwork | NOT RUN | |
| Seek forward/back on each seekable format | NOT RUN | |
| Truncated audio, revoked SAF grant, deleted file | NOT RUN | |
| Duration and seek on long VBR and high-resolution files | NOT RUN | |

Use your own authorized audio or redistributable fixtures. Record codec/container via a trusted media probe; extensions are not evidence of encoding. No fixtures are bundled.

## Lifecycle

| Check | Result | Evidence |
|---|---|---|
| Screen off for 30 minutes | NOT RUN | |
| Notification / lock screen / headset controls | NOT RUN | |
| Incoming call, transient focus loss and resume | NOT RUN | |
| Focus permanently stolen by another player | NOT RUN | |
| User pauses during transient loss: no automatic resume | NOT RUN | |
| Headphone unplug / Bluetooth disconnect | NOT RUN | |
| Activity rotation / dismissal and reopening | NOT RUN | |
| Service stopped while idle: native objects released | NOT RUN | |
| Force-stop / process kill: no spontaneous restart | NOT RUN | |

## Crossfade Experiment

Default is off. Enable a 4-second fade with two known local tracks. Record output (where permitted) and identify audible overlap, transition duration, clipping, silence and public media-session identity. Volume-envelope unit tests cannot establish native audio continuity.

| Check | Result | Evidence |
|---|---|---|
| Audible overlap and next-track clock at commit | NOT RUN | |
| Pause/resume before and during overlap | NOT RUN | |
| Seek during overlap cancels incoming and keeps current track | NOT RUN | |
| Next/previous during overlap cancels and loads selected item | NOT RUN | |
| Replace/reorder queue during overlap: no stale next item | NOT RUN | |
| Incoming unreadable/undecodable: outgoing keeps playing | NOT RUN | |
| Different sample rates / Bluetooth / wired output | NOT RUN | |
| Short tracks skip crossfade and advance normally | NOT RUN | |
| 100 repeated transitions: no sustained native-memory growth | NOT RUN | |

Pause freezes both decks and the transition clock. Seek/skip/queue edits cancel the incoming deck. One logical outgoing item remains public until commit. The prototype opens the incoming deck at the fade threshold, not ahead of time; measured setup latency may shorten the overlap. If this fails device tests, disable crossfade and investigate preloading or a mixer rather than claiming success.

## Lyrics and UI

- LRC timestamps, offsets, repeated tags and UTF-8 text.
- Seek and queue transition switch lyrics on the native clock.
- Small phone, large phone/tablet, landscape, font scaling and TalkBack.
- Import/cancel/error/disconnected states.
- Native artwork rendering and controls fit without clipping.
- Palette changes do not restart audio; preferences survive relaunch.

## Measurements

Record cached play/pause UI latency and audible latency separately. Capture memory after cold start and after repeated transitions. Compare screen-off battery usage against a baseline player on the same output route. No benchmark target is passed merely because compilation succeeds.
