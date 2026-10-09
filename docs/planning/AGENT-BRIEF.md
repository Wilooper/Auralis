# First implementation brief

Build milestone M0 only: validate Kotlin/Compose + LibVLC on Android before expanding the app.

Read README.md, IMPLEMENTATION-PLAN.md, ARCHITECTURE.md and VALIDATION.md. Treat BACKLOG.json as the task inventory; update statuses with evidence.

## Required work
1. Inspect existing repository instructions if present. Use an isolated branch for changes.
2. Create or use the Android repository; pin reproducible versions and record native dependency/license sources.
3. Add a simple Compose screen for SAF file selection and basic playback.
4. Implement a LibVLC adapter with explicit lifecycle, typed errors and authoritative state.
5. Connect a custom Player wrapper to Android session/background controls.
6. Add a two-player crossfade experiment, with pause/seek/skip cancellation behavior.
7. Run the format and device checks that the available environment permits.
8. Produce a debug APK when tooling permits, BUILD-RESULTS.md and M0-DECISION.md.

## Boundaries
No full theme editor, ML model, online extractor, remote relay or independent plugin installer yet. No Python runtime in the application. No additional playback engine unless documented M0 failures justify a revised decision.

## Evidence
Record exact commands, build versions, fixture provenance, device/API details, test outcomes, failures and next actions. Mark device tests unrun if no device is available. Do not claim crossfade from volume fades alone.

## Completion
The result is concrete and reviewable: source changes, build output or precise tooling blocker, test findings, and an engine/transition recommendation. Preserve supported local playback while experimenting. Never publish credentials or private music files.
