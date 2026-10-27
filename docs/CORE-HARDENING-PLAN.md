# ReVa local base hardening — 15 October 2026

This plan is published before application changes. It continues the open base/companion PR (#11), starting from 6cb7f774d62b38966f5b985d71c193b157dcb995.

## Goal and boundary

Remove the built-in Smart Flow playlist generation and its audio-analysis workload. Playlist generation can return later as an optional extension. Strengthen existing queue, persistence and companion-runtime behaviour before adding more feature types. No AI/model, online provider, equalizer, Dolby processing, replacement audio backend or new YAML installer is introduced in this pass.

## Ordered implementation

1. Remove the Smart Flow button/dialog, view model, analyzer, feature accumulator, playlist sorter and generated-flow playback entry point. The stock player must not enumerate or decode the library to build descriptor profiles.
2. Move ordinary shuffle traversal into its own queue module. Preserve existing queue order, repeat, previous/next and crossfade behaviour. Add bounds handling/tests for empty queues and invalid indices; preserve the independent fade-envelope checks.
3. Upgrade the private library database from version 2 to version 3. Remove only the obsolete analysis table. Preserve selected sources, indexed tracks, metadata, favorites and listening history. New installations must not create the analysis table. Cover an upgrade containing legacy profiles with a regression test; do not delete/reset the library database or run a blocking VACUUM at startup.
4. Coalesce companion discovery refreshes and bound concurrent discovery work. Verify only the chosen companion on action activation rather than repeatedly scanning every installed companion. Keep fresh package/certificate checks and revision-bound grants.
5. Harden the cooperative SDK worker lifecycle: enforce one active task, bounded and strict input/output envelopes, exactly-once terminal results, cancellation, admission after completion and deadline cleanup. Worker callbacks must not expose exceptions or leave stuck busy state. Keep host-side admission and background permission enforcement.
6. Expire elapsed task leases on visibility changes/new work, and preserve the existing separation between compute workers and the playback media service. No hard CPU/RAM or malware-free guarantee is made for arbitrary companion APK components.
7. Update current documentation and version to identify a local base without built-in playlist generation. Historical evidence files are retained as historical records.

## Verification and delivery

- Focused queue tests: stable shuffle traversal, no duplicates, repeat wrap, previous traversal, malformed indices and empty/single queues.
- Android persistence test: a version-2 database with analysis rows upgrades without losing sources, songs, favorites or listening history.
- SDK worker tests: valid result, malformed/oversized input/output, unsupported action, repeated completion/cancellation and a second task after a terminal callback.
- Resource policy tests: visibility/background grants, expired leases, capacity and bounded Binder request admission.
- Existing library, lyrics, queue, extension-security and playback lint checks continue to run.
- Retry a local Android build. If environment downloads are blocked, push the code as previously authorized and use the single GitHub APK workflow to build/verify ReVa and the sample companion.
- Report the actual GitHub result and remaining physical-device verification. Native process lifetime, frame timing, heat/battery and total memory usage are not established by unit tests.
