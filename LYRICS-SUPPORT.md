# Auralis 0.3 — Embedded Lyrics and Folder Indexing

The embedded lyrics reader introduced in 0.2 is retained. The 0.3 library and recovery changes require phone verification alongside lyrics behavior.

## Embedded tag readers

| Container / tag | Read path | Lyric modes |
|---|---|---|
| MP3 / ID3v2 | Media3 `USLT` / `ULT`, lyric `TXXX` / `TXX`, binary `SYLT` / `SLT` | Plain, line LRC, enhanced word LRC, common TTML, native synchronized ID3 |
| FLAC | Vorbis comment `LYRICS`, `UNSYNCEDLYRICS`, `SYNCEDLYRICS` and normalized synonyms | Plain or timed text payload |
| Ogg Vorbis / Opus | Vorbis comment / `OpusTags`, including continued packets | Plain or timed text payload |
| M4A / MP4 | `©lyr` and freeform `----` lyric-name `data` atoms; UTF-8/UTF-16 | Plain or timed text payload |
| APEv2 (APE, WavPack, etc.) | Unicode lyric items in tail tags, including before ID3v1 | Plain or timed text payload |
| WAV / AIFF | Embedded ID3 chunks | ID3-supported modes |
| DSF | ID3 metadata pointer | ID3-supported modes |

Playback codec support and lyric tag support are separate. ASF/WMA lyric objects, Lyrics3v1/v2, arbitrary proprietary tags and every possible TTML profile are not implemented. No claim of universal embedded-tag support is made. If several supported lyric entries exist, word timing is preferred over line timing, then plain text. There is no language selector yet.

## Timing and rendering

- Plain Unicode lyrics preserve paragraph breaks and never invent timestamps or offer click-to-seek.
- LRC handles repeated line timestamps, fractional seconds and signed offsets. Enhanced LRC handles inline `<mm:ss.xxx>` word timestamps, repeated-line shifts and trailing end markers.
- ID3 SYLT reads the declared text encoding and millisecond times. MPEG-frame timestamps require a recognized MPEG audio frame header to convert them; otherwise that synchronized entry is skipped.
- Common TTML paragraphs/spans support clock expressions and offsets (`ms`, `s`, `m`, `h`, `f`, `t`), basic inherited starts and explicit ends. Sequential timing containers, frame-rate multipliers, styling, ruby and every karaoke/vendor timing convention are not implemented.
- Current-line text follows the LibVLC playback position, updated approximately five times per second. Word and syllable timing use the stored tokens; this is discrete highlighting, not progressive karaoke fill. For simultaneous translations, there is not yet a separate parallel-line layout.
- External LRC/TXT/TTML attachments override embedded lyrics for that track until reset or process death. External text files are currently decoded as UTF-8.

## Import behavior

The system folder picker grants saved access to the chosen folder and its subfolders. Folder sources and tracks are indexed in SQLite on an IO dispatcher. MediaStore supplies indexed metadata; SAF discovers other supported audio files. Artwork loads lazily when displayed. Indexing updates the library independently of playback and does not start a queue. Tap a Home song to play its browse result. Repeated selection of the same folder keeps existing identities and history.

The library and queue checkpoint survive process termination. Playback restores paused. Manual lyrics attachments remain in-memory. Android's folder picker restricts some roots/private locations; use an allowed music subfolder. Child documents inherit the tree grant. Optional READ_MEDIA_AUDIO (or READ_EXTERNAL_STORAGE on older Android) enables fast MediaStore indexing, restricted by Auralis to your selected folders. Provider access can be revoked.

## Bounds and cancellation

Text payloads are limited to 1 MiB, ID3/APEv2 tag bodies to 8 MiB, total metadata reads to 16 MiB, container operations to 20,000, Ogg pages to 512 and logical streams to 16. MP4 traversal skips audio payloads instead of copying them. XML entities are rejected and tree recursion is limited. A 32-entry embedded-lyrics cache includes negative results. Lyrics load lazily for the active track; importing a folder does not decode every track's lyrics.

Pipe-backed providers expose only a bounded 8 MiB prefix to the lyrics reader, so tail metadata may be unavailable. Folder scans stop at 100,000 entries. Cancel stops indexing while retaining already saved batches. Queue changes use batches of 100 to avoid oversized Binder calls. Large-folder responsiveness and unusual providers still need phone testing.

## Phone checks

1. Index a music folder containing subfolders, mixed audio/non-audio files and duplicate selections. Verify Home counts, deduplication and progress cancellation. Kill/reopen and confirm the folder remains saved.
2. Import another folder while a song plays, and again while paused. Check that the song position and play/pause state stay unchanged.
3. Play files containing Unicode plain USLT, LRC USLT, ID3 SYLT, enhanced LRC FLAC/Opus and MP4 lyrics. Verify the displayed mode/text and absence of raw timestamps.
4. Seek forward/backward, pause, switch tracks rapidly and enable crossfade. Confirm lyrics follow the current track and never retain a previous track's text.
5. Tap a timed line to seek. Verify plain lyrics can scroll and cannot seek. Attach an external file and restore embedded lyrics.
6. Try a file without lyrics, malformed/oversized tags, a revoked grant and a pipe-backed provider. Expect useful empty/status states without crashing or interrupting audio.
7. Check screen-off playback, headset controls and notification controls again after this update.

## References used

- ID3v2.3 USLT/SYLT: https://id3.org/id3v2.3.0
- ID3v2.4 frames: https://id3.org/id3v2.4.0-frames
- Vorbis comments: https://xiph.org/vorbis/doc/v-comment.html
- FLAC format: https://xiph.org/flac/format.html
- Opus tags: https://www.rfc-editor.org/rfc/rfc7845
- TTML2 timing: https://www.w3.org/TR/ttml2/
- Android folder access: https://developer.android.com/training/data-storage/shared/documents-files
- Media3 extractor 1.6.1 published ID3 decoder source.

Media3 extractor is used only for metadata decoding. LibVLC remains the sole audio playback engine.
