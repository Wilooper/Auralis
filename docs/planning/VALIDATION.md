# Validation and release gates

## Status
All checks below are planned. No build or benchmark is claimed by this pack.

## Reference devices
Primary mid-range ARM64 Android phone; one Android 11 baseline device/emulator; another OEM physical phone before public release.
Record Android version, output route, ABI, library versions, build mode and thermal state.

## Codec corpus
| Group | Samples | Required observations |
|---|---|---|
| Common | MP3 CBR/VBR, AAC/M4A, FLAC, WAV, Vorbis, Opus | Play, seek, duration, tags and art |
| Expanded | ALAC, AIFF, WMA, APE, WavPack | Support according to packaged build |
| Specialist | DSF/DFF, tracker/MIDI | Explicit supported/unsupported outcomes |
| Broken | Truncated data, bad tags, unreadable URI | Useful error; no service crash |
| Long/large | Long VBR file and high-resolution audio | Memory bounds, seek and duration |

Use redistributable fixtures. A filename extension is not proof of a valid format. Verify codec and container.

## Behavioral gates
- Notification/headset play-pause-next; audio focus and calls.
- Screen-off playback, Bluetooth route changes and process recreation.
- Permission revocation, missing SD card, SAF imports and library rescan.
- Queue reorder while incoming track is preloaded.
- Pause/seek/skip during each crossfade state; one public timeline.
- Lyrics after seek, speed changes and transition commit.
- UI slot change without player restart.
- Offline commands, missing tags, contradictory constraints and zero matches.
- Expiring source URLs; provider outage and logout.
- Guest authorization, range seeking and sharing shutdown.

## Proposed performance targets
| Metric | Target and measurement |
|---|---|
| Cached play/pause UI feedback | P95 below 100 ms; measure separately from audible output |
| Cached smart ranking | P95 below 100 ms for a 10,000-track reference library |
| UI frames | Frame budget for selected refresh rate; report jank |
| Analysis | Never block playback; cancellation and concurrency limits |
| Native memory | No sustained growth after repeated play/release/transition cycles |
| Startup | Measure cold/warm separately, before defining release threshold |
| Battery | Compare playback-only against analysis/sharing-enabled scenarios |
| App size | Record per-ABI download and installed size, not only universal APK |

Targets are not measured results. Bluetooth latency is not solely application latency.

## Recommendation quality
Blind pairwise judgments on similarity. Human-rated mood journey continuity. Accuracy/confidence by language and genre. Compare deterministic baseline before enabling a model by default.

## Test ownership
Pure parser/ranking/preset validation: local unit tests.
Room/checkpoint integration: Android tests.
Engine callbacks/session/lifecycle: instrumentation and physical device tests.
Crossfade quality: recorded output plus listening review; UI tests cannot establish audio continuity.
Sharing: browser/device integration and adversarial request checks.

## Release checklist
Pinned dependencies and licenses; no secrets; reproducible build; migration checks; no critical playback regressions; documented format matrix; known limitations; test results and device details; original branding.
