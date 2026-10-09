> Historical design snapshot: planned APIs and examples may differ from the current implementation. Use ../../sdk/README.md and ../../V5-SDK.md for implemented v5 contracts.

# Advanced Music Player — implementation pack
Version 0.1 • 7 October 2026 • Planning artifacts, not a working application

## Working direction
Build an original Android application using Kotlin, Jetpack Compose and LibVLC as the sole decoding/playback engine. Kotlin owns app logic and a user-started sharing server. Custom C++ is optional and introduced only when an audio prototype demonstrates a need.

The earlier player-fork research remains a reference. This pack plans the fresh LibVLC application discussed next; it does not assume PixelPlayerOSS code has been adopted.

## Read in this order
1. [Mind map](MIND-MAP.md) and [visual](MIND-MAP.svg).
2. [Implementation plan](IMPLEMENTATION-PLAN.md): scope, phases and exit gates.
3. [Architecture](ARCHITECTURE.md): responsibilities and data flow.
4. [Extension SDK](EXTENSION-SDK.md): integration and trust boundaries.
5. [Smart queue](SMART-QUEUE.md): commands, similarity and mood journeys.
6. [Sharing](SHARING.md): browser sessions and network limits.
7. [Validation](VALIDATION.md): test corpus and proposed performance budgets.
8. [Decisions](DECISIONS.md): assumptions and unresolved choices.
9. [Backlog](BACKLOG.json): ordered tasks and dependencies.
10. [Agent brief](AGENT-BRIEF.md): first implementation task.

Examples: [appearance preset](examples/appearance-preset.json), [plugin manifest](examples/provider-manifest.json), [playlist query](examples/playlist-query.json). These are illustrative contracts; validation and production schemas are implementation tasks.

## First build
A local player that imports user-selected audio, plays through LibVLC with background controls, displays artwork and local lyrics, remembers its queue, and lets the user combine home/player/mini-player styles. Prototype crossfade before promising it. Add deterministic command playlists next.

## Deliverable boundaries
No APK, model weights, source repository or tested performance is included. Numeric budgets are targets. Android 11+ is the initial assumption. Build tools, library versions and ABI choices must be pinned during the first milestone.

## Evidence
See [sources and decisions](DECISIONS.md). Reuse of third-party code, binaries and models requires recorded licenses. Community modules should target the published contracts rather than private implementation classes.
