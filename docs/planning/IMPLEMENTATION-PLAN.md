# Implementation plan

## Product goal
Make local music feel rich and personal: extensive format support, flexible screens, synced lyrics, configurable transitions, explainable smart queues, command-generated playlists and optional self-hosted sources/sharing.

## Core versus optional
Core: import/index, stable playback, queue, artwork, local lyrics, Android controls, settings and contracts.
Optional modules: smart queue, acoustic analysis, sharing, source adapters and extra appearance packs. Disabling an optional module must preserve ordinary playback.

## Milestones

| ID | Work | Concrete output | Exit gate |
|---|---|---|---|
| M0 | Validate native foundation | Small LibVLC prototype, pinned dependency record, codec/crossfade results | Device playback and Android background integration work; transition approach decided |
| M1 | Build local core | Import, index, queue, artwork, local lyrics, persistent settings | Play/seek/pause, screen-off, headset and restore scenarios pass |
| M2 | Add appearance slots | Independent home/artwork/full-player/mini-player/lyrics styles | Changing appearance preserves queue and playback |
| M3 | Add commands and similar mode | Offline parser, temporary playlists, deterministic ranker | Exact constraints hold; insufficient matches are explained |
| M4 | Add one self-hosted source | Adapter, multiple instances, credentials and expiring streams | Stream refresh, network loss and logout handled |
| M5 | Add browser sharing | Explicit LAN session, QR, browser player, scoped streams | Guests can seek; revocation works; lifecycle and codec limits visible |
| M6 | Add optional acoustic ML | Licensed model, cached features, mood journeys | Multilingual quality and device resource budgets met |
| M7 | Publish external extension support | IPC contract, sample extension and compatibility suite | Third-party provider can fail without stopping core playback |

M0 is a decision gate. If dual LibVLC instances cannot provide acceptable crossfade, compare a native mixer prototype against a simpler transition release. Do not silently call sequential fades crossfade. If basic LibVLC integration fails requirements, record evidence before revisiting the engine.

## M0: first seven working days
These are work blocks, not guaranteed calendar dates.

| Day | Task | Evidence |
|---|---|---|
| 1 | Create Kotlin/Compose project; pin Gradle/JDK/SDK/LibVLC and ABI dependencies | Clean reproducible debug build; dependency/license inventory |
| 2 | Play imported content URIs; seek, pause and release safely | Valid audio corpus results |
| 3 | Wrap player state; add one Android media session and foreground playback service | Notification/headset/call tests |
| 4 | Preload two player instances; implement transition experiment | Overlap recording and timing observations |
| 5 | Test pause/seek/skip/focus changes during overlap | Transition state-machine results |
| 6 | Measure lifecycle, memory, battery baseline and unsupported files | Device benchmark report |
| 7 | Choose transition design; freeze core contracts; prioritize M1 | Decision record and next task list |

## Implementation rules
- One authoritative playback service and queue.
- UI observes state and sends commands; it does not construct native players.
- Metadata and analysis work stay off the main thread.
- Content URIs remain first-class; permissions may be revoked.
- Optional network features default off.
- Queue suggestions never override tracks explicitly added by the user.
- Store provenance and confidence for inferred metadata.
- Every phase ends with a runnable APK and findings, not only documentation.

## Rough effort
M0: 1 week. M1: 2–3 weeks. M2: 2–3 weeks. M3: 1–2 weeks. M4: 1–2 weeks. M5: 2–3 weeks. M6: 2–4 weeks. M7: 2–4 weeks.
The feature-rich beta through M5 is roughly 9–14 weeks for one experienced developer. Learning, upstream integration failures, native mixing and transcoding can extend this.

## Scope controls
Initial formats are tested explicitly, not advertised as universal. Browser sessions play independently initially. Appearance customization begins with curated components, not arbitrary code or a complete drag-and-drop designer. Natural-language commands begin with predictable grammar. No selected ML model or dataset is assumed.
