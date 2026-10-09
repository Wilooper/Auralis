# Advanced Android Music Player — Research and Implementation Plan
Research date: 7 October 2026

## 1. Decision

**Recommended foundation: fork PixelPlayerOSS, retain its Kotlin/Jetpack Compose application and Media3 playback, then introduce stable extension boundaries.**

This is a provisional engineering recommendation based on repository documentation and selected build/license files, not a completed source audit or device benchmark. The first implementation milestone must reproduce its build and test actual playback, crossfade, library scanning, and extension feasibility.

PixelPlayerOSS documents local playback, lyrics, artwork, crossfade, FFmpeg support, and self-hosted libraries. Its Android 11+ requirement fits the proposed primary target. Its README lists Kotlin, Compose, Media3, Room, DataStore, Hilt, WorkManager, Retrofit/OkHttp, Coil and TagLib. [1]

**Fallback sequence:** PixelPlayerOSS → APlayer if the fork proves impractical → an original Kotlin/Compose shell with a replaceable playback engine. Use LibVLC in that shell if format coverage justifies it. Building a new application does not require building a new decoder.

Assumptions: Android first; Android 11+ initially; fully usable offline; community source contributions; small optional ML; no Python runtime, external background daemon, mandatory cloud account, or always-running language model.

## 2. Candidate comparison

“Documented” means upstream describes a feature. It does not establish quality or performance on a particular phone.

| Candidate | Verified public evidence | Fit and remaining work | Decision |
|---|---|---|---|
| [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS) | Kotlin/Compose, Media3, lyrics, crossfade, artwork, local library, Navidrome/Subsonic and Jellyfin; GPL-3.0-or-later; Android 11+ [1] | Closest combination; arbitrary UI composition, command playlists, local mood analysis and extensible sharing need new design | Primary candidate |
| [APlayer](https://github.com/rRemix/APlayer) | Compose, embedded/local/online and word lyrics, themes, artwork, playlists, WebDAV, background controls; GPL-3.0 [2] | Good UI and lyrics alternative; playback engine and crossfade require source audit | Reserve candidate |
| [Gramophone](https://github.com/FoedusProgramme/Gramophone) | Kotlin-oriented Media3 player, LRC/TTML/SRT, word/syllable lyrics, ReplayGain, MediaStore; GPL-3.0 [3] | Strong lyrics/audio reference; no verified composable skin or provider ecosystem | Secondary reference |
| [Auxio](https://github.com/OxygenCobalt/Auxio) | Local-first Android music player emphasizing a restrained interface; GPL-3.0-or-later [4] | Expansion would depart substantially from its stated direction; required lyrics capability not verified | Not preferred |
| [Symphony](https://github.com/zyrouge/symphony) | Kotlin/Compose offline player, Android 9+; AGPL-3.0 [5] | Worth reconsidering if older Android support becomes essential | Alternative |
| [Echo](https://github.com/brahmkshatriya/echo) | Extension-based Android music player; upstream says it is moving toward a Compose multiplatform remake [6] | Valuable extension reference, but a moving application foundation; independently audit reuse rights | Architecture reference |
| [Namida](https://github.com/namidaco/namida) | Flutter; extensive customization, lyrics and crossfade; current EULA restricts renamed redistribution and README notes build obstacles [7] | Technology and redistribution terms conflict with this project | Exclude as fork |
| [LibVLC](https://github.com/videolan/vlc-android) | Reusable Android media engine; broad decoding/network support; engine license differs from application license [8] | Provides playback, not the requested app, lyrics, recommendations or layout system | Engine fallback |

PixelPlayerOSS currently lists JDK 21 and compile/target SDK 37. Do not copy those numbers into a new project without checking the pinned source revision and available build tools. [1]

## 3. What we retain and what we build

Retain the initial library, artwork, playback/session integration and existing lyrics experience. Preserve existing self-hosted adapters while wrapping them behind a common source interface. Validate documented crossfade before redesigning it.

Build the following separately:

| Area | New capability |
|---|---|
| Appearance | Independent home, artwork, full-player, mini-player, queue and lyrics styles; exported presets |
| Discovery | Similar-track ranking, local preference learning, gradual mood journeys |
| Commands | Offline playlist query parser with temporary sessions and preview |
| Sources | Versioned provider SDK; multiple named server instances; expiring stream handling |
| Sharing | User-started phone server, QR invitation and browser player |
| Extensions | Capability contracts, compatibility tests, safe installation/update paths |
| Metadata | Confidence, field provenance, user overrides and incremental indexing |

A basic player should remain usable with all optional modules disabled.

## 4. Language and runtime plan

Use **Kotlin for application logic, UI, networking, local server, indexing coordination and ranking**. Use existing native libraries for decoding and tag access. Add custom C++ only after profiling identifies a demanding audio-analysis or mixing loop.

| Responsibility | Proposed implementation |
|---|---|
| Screens and reusable layouts | Jetpack Compose |
| App state and concurrency | Kotlin coroutines and Flow |
| Track index and playlist records | Room |
| Settings and appearance presets | DataStore plus versioned JSON |
| Playback | Media3/ExoPlayer behind an engine interface |
| Tags | Existing TagLib integration after audit |
| Source requests | Retain Retrofit/OkHttp initially |
| Optional phone server | Kotlin embedded server; evaluate Ktor CIO on Android |
| Optional tiny model | LiteRT, CPU first |
| Heavy DSP if required | C++ through JNI, batched buffers |

LiteRT provides Kotlin and C++ APIs for Android, making it a viable optional runtime rather than requiring Python. [9] Ktor supports embedded server engines, but JVM support alone does not guarantee Android suitability; test its chosen engine and dependencies on actual phones. [10]

A friends-in-browser player necessarily needs a small HTML/CSS/JavaScript surface. That is a static browser client bundled with the app, not another backend runtime. The Android application and server remain Kotlin/native.

Language choice alone cannot guarantee responsiveness. Artwork decoding, large Compose recompositions, database work and analysis on the main thread can make a Kotlin/C++ app slow.

## 5. Core architecture and contracts

Start with clear package boundaries; extract Gradle modules gradually. The inspected PixelPlayerOSS settings currently include application and baseline-profile modules, not the plugin architecture proposed here. [11]

Suggested boundaries:

- core-model: Track, SourceRef, Album, LyricsDocument, QueueEntry, PlaybackRequest.
- core-library: scanning, indexing, provenance and artwork.
- core-playback: engine, audio focus, transitions and one authoritative session.
- core-appearance: schema and component registry.
- core-extensions: capability registration and compatibility negotiation.
- feature-smartqueue, feature-commands and feature-sharing.
- provider-local, provider-subsonic, provider-jellyfin and later provider-custom-server.

Key contracts:

| Contract | Responsibility |
|---|---|
| MusicSource | Browse/search, metadata, artwork, playlist access and stream resolution |
| LyricsProvider | Return text, line or word timing with attribution and confidence |
| QueueStrategy | Suggest candidates and explanations without starting playback |
| AudioAnalyzer | Produce cached features under a resource budget |
| AppearancePack | Supply declarative styles or approved component IDs |
| SharingTransport | Create and revoke sessions and expose eligible tracks |

Keep a source-scoped ID such as local:123 or server-instance:456. Two servers can use the same track ID. Match equivalent recordings separately; never collapse songs solely by title.

Resolved streams carry URL or content URI, required headers, MIME type, expiry, seekability and cache/share capabilities. A plugin returns this descriptor; only the playback service consumes it. Do not store a temporary stream URL as permanent track identity.

## 6. Audio formats and crossfade

Replace “every file” with a tested support matrix. Encrypted subscription downloads, malformed files and some specialist formats cannot be promised.

Media3 separates container extraction from sample decoding. Its FFmpeg extension expands codec decoding; it does not automatically add extraction for every container. Device decoder availability also matters. [12]

Initial matrix: MP3, AAC/M4A, FLAC, WAV/PCM, Ogg Vorbis and Opus. Expansion candidates: ALAC, WMA, APE, WavPack, AIFF, DSF/DFF and tracker/MIDI formats. Record playback, seek, duration and tag results per engine, codec/container combination and device.

Add a Storage Access Framework folder/file path for formats MediaStore misses. Keep persisted URI permissions and use content access rather than assuming filesystem paths. Gramophone itself documents scanner limitations on some extensions. [3]

**True crossfade overlaps outgoing and incoming audio.** Fade-out followed by fade-in is different. Media3’s public crossfade feature request remains open in the retrieved issue; do not assume one ordinary ExoPlayer queue provides the required transition. [13]

First audit the fork’s implementation. If needed:

1. One queue controller selects the incoming track.
2. A second playback instance prebuffers it.
3. Transition control ramps both gains during a configurable overlap.
4. The public media session reports one logical active item and timeline.
5. Pause, seek, skip, calls, Bluetooth changes and provider failure explicitly cancel or settle the transition.

A starting equal-power envelope is outgoing gain cos(πt/2), incoming gain sin(πt/2), for t from 0 to 1. Add headroom and clipping protection; this envelope can increase peak level for correlated signals. It is a musical starting point, not a universal optimum.

Offer 0–12 seconds, album gapless mode, transition preview and exclusions for spoken audio/very short tracks. Disable audio offload when dual playback or processing requires it; expose the battery tradeoff. Implement a native single-output mixer only if dual-player timing or fidelity fails tests.

LibVLC also needs app-level queue, session and transition work. Changing the engine does not automatically solve crossfade.

## 7. Small local recommendation system

Use a hybrid of deterministic ranking and an optional trained classifier. The inexpensive baseline is useful before the classifier exists.

Per-track records: language, multi-label genre/mood, tempo estimate, energy, brightness, optional embedding, confidence, analysis version and user corrections. Keep language, emotional mood and musical energy separate: fast songs can be sad, and soft songs can be happy.

Baseline ranking combines metadata similarity, recent co-listening, user likes and repeat/artist-diversity penalties. Once available, cached acoustic features refine that score. Reweight only available features; unknown fields must not become false matches.

A proposed learned plugin analyzes a few short excerpts, produces a log-mel input and runs a quantized classifier. **A specific small model has not been selected or validated.** Selection requires model-weight/license review and a multilingual evaluation, particularly Hindi and Punjabi. English-centric genre labels are insufficient.

Targets for model selection, not measured claims: roughly 5–20 MB weights, low incremental memory, and sub-100 ms cached ranking. Feature extraction/inference can be slower during background indexing.

Analyze on import or idle/charging windows, never on every next-track event. Bound concurrency, allow cancellation, cache results and let users disable analysis.

Do not identify lyric language from script alone; Roman-script lyrics can be Hindi, Punjabi or English. Prefer explicit metadata, user labels and validated language detection.

### Similar mode and mood journey

Similar mode keeps the current track’s profile as a reference while enforcing diversity.

For gradual shifts, interpolate a target mood/energy vector across the next N songs and choose a route with bounded adjacent differences. A short lookahead or beam search can avoid dead ends better than repeatedly choosing the closest next track.

Example: sad → reflective → calm → warm → upbeat over eight songs.

When the library lacks bridge tracks, show the limitation and offer a longer journey or relaxed constraints. Never claim a smooth path exists when it does not. Freeze tracks the user manually adds; smart suggestions must not overwrite their queue.

Learning uses explicit feedback and listening history locally. An accidental skip or phone call should not count automatically as dislike.

## 8. Command-based temporary playlists

Begin with an offline parser, aliases and a structured preview. No LLM is needed for predictable commands.

Examples:

- “Hindi sad love songs for 40 minutes.”
- “Punjabi mellow songs, no rap, maximum two songs per artist.”
- “Songs like this, slowly getting happier.”
- “Romantic Hindi songs released before 2015.”

Translate into a validated PlaylistQuery: languages, mood/genre labels, exclusions, duration/count, seeds, diversity rules and journey settings. Clarify AND/OR meanings when a phrase is ambiguous.

For “Hindi + sad + love,” require Hindi and both mood/theme labels in strict mode. Label relaxed results separately and never silently weaken a language constraint.

Preview count, duration and confidence. Say which metadata is missing. Allow “strict tags only” or “include inferred tracks.” A temporary playlist is a session queue: persist it for crash recovery but do not add it to the saved playlist library unless requested.

Later, an optional compact intent classifier can support broader phrasing. It outputs the same constrained query object and cannot execute arbitrary commands.

## 9. Mix-and-match UI

Treat UI as independent slots rather than three whole-app clones:

| Slot | Example styles |
|---|---|
| Home | Large recommendation shelves, dense library, album grid |
| Artwork | Large square art, rounded card, full-bleed background |
| Full player | Spacious artwork, compact controls, lyrics focus |
| Mini player | Bottom bar, floating card, expanded strip |
| Queue | Drawer or dedicated page |
| Lyrics | Line scrolling, karaoke, plain text |
| Navigation | Bottom tabs or rail |

Thus a user can combine Apple Music-inspired artwork, a Spotify-inspired mini player and YouTube Music-inspired home shelves. Presets select component IDs and design tokens; all components consume the same playback state.

Editable tokens include color, type scale, corner radius, spacing, motion, blur and artwork behavior. Provide import/export, preview, reset and schema migrations. Preserve accessibility, readable contrast and large text.

Start with curated slot choices. Arbitrary drag-and-drop layouts are a much larger feature and should follow a stable component system. Use original assets and app branding.

## 10. Extensions and community contributions

Use three levels:

1. **Bundled Kotlin feature/provider modules:** first release; users enable capabilities individually. Contributors submit changes and receive compatibility tests.
2. **Declarative downloadable packs:** JSON presets, aliases and bounded layout choices; no executable code required.
3. **Optional external provider apps:** later, separately installed and signed Android packages communicating through a narrow Binder/AIDL API.

A build module is not independently installable merely because it is called a plugin. Level 1 still needs an app release for new code. Level 3 enables independent provider releases.

For external apps, define trust prompts, signature checks, SDK versions, cancellation, timeouts and permission-aware capabilities. Prefer IPC over loading downloaded DEX or native libraries into the main app. Android documents security and distribution concerns with dynamic code loading. [14]

IPC does not make a plugin automatically trustworthy: an independently installed app can use its own permissions. In-process extensions share the host’s privilege boundary.

Manifest fields: ID, version, minimum/maximum SDK compatibility, capabilities, configured hosts, declared data needs, author, source/license and signing/update identity. UI packs remain declarative; third parties do not inject arbitrary Compose code initially.

Publish an extension template, contract tests, provider fixtures, contribution guide, compatibility policy and sample source. Core owns playback. Plugins provide capabilities and data.

## 11. Online and self-hosted sources

Reuse the existing Navidrome/Subsonic and Jellyfin paths after validation. OpenSubsonic provides a documented stream endpoint with optional format/bitrate parameters; actual support depends on the server. [15]

Each source instance gets its own account, library namespace, cache and connection settings. Protect credentials, redact logs and refresh streams at playback time. Present combined or source-specific libraries.

For a custom self-hosted YouTube Music backend, define an API contract: health/capabilities, search, track, artwork, lyrics and stream resolution. A server URL alone is insufficient because different instances expose different APIs.

The adapter should handle signed URLs, headers, stream expiration and connection failures. Keep server-specific logic outside the queue and UI. The phone consumes the existing backend; it does not require embedding Python or yt-dlp.

Respect whether a provider permits caching, export and resharing. A logged-in stream is not automatically a shareable local asset.

## 12. Browser sharing through QR or URL

First release: **same Wi-Fi or phone hotspot**. Remote friends require a reachable hosted endpoint, tunnel or relay; a private LAN URL cannot cross the internet by itself.

User flow: select playlist → Start sharing → choose listening/control permissions → display QR/link → friends open the browser player → host can revoke guests or stop the session.

The Kotlin server serves a small web client plus:
- Session metadata and allowlisted playlist tracks.
- Artwork and lyrics.
- Byte-range audio requests for seeking.
- Optional host-controlled playback events.

Use random, expiring invitations exchanged for a scoped session; avoid permanent secrets in logs. Never expose arbitrary filesystem paths or forward provider credentials. Limit guests and resource usage.

There are three separate products:

| Mode | Behavior |
|---|---|
| Playlist listening | Guests choose tracks and play independently |
| Remote control | Authorized guests control the host phone |
| Listen together | Guests follow a shared timeline; needs clock synchronization and drift correction |

Build playlist listening first. Listen-together is not sample-accurate across ordinary browsers or Bluetooth devices; define and measure a tolerance.

**Phone decoding support and browser decoding support differ.** Serve compatible originals when possible. For unsupported codecs offer optional cached transcoding using a proven native encoder, or an explicit unsupported-file message. Treat transcoding as an optional module with battery, disk and dependency costs.

Keep the sharing session visible and stoppable. Playback uses the media service; sharing-only background operation needs a separately compliant foreground-service/lifecycle design. Do not misuse mediaPlayback solely to keep an idle server alive.

Test Wi-Fi isolation, hotspot reachability, IP changes, permission denial and OEM background restrictions. Current Android guidance makes LAN access dependent on target SDK, including explicit runtime permission for target SDK 37+. [16]

## 13. Build phases and acceptance gates

Effort estimates assume one experienced developer and substantial upstream reuse; they are planning ranges, not delivery promises.

| Phase | Scope | Exit gate | Rough effort |
|---|---|---|---|
| 0 | Pin revision; build stock fork; audit licenses, formats, crossfade, session and architecture | Debug APK and device findings; keep/reject decision | 3–7 days |
| 1 | New app identity; stable local library and playback; engine/source contracts | Background, headset, seek and queue recovery pass | 1–2 weeks |
| 2 | Slot-based appearance with two presets; configurable lyrics | Mix styles without playback restart or state loss | 2–3 weeks |
| 3 | Command parser, temporary playlists, cached similar mode | Correct filters and clear sparse-library behavior | 1–2 weeks |
| 4 | Optional ML/audio features and mood journeys | Multilingual quality/battery evaluation | 2–4 weeks |
| 5 | LAN browser sharing | QR join, seeking, revocation and screen-off testing | 1–3 weeks |
| 6 | Custom server adapter and contributor SDK | Add a sample provider without modifying queue/UI | 1–2 weeks |
| 7 | External extension apps, listen-together, advanced editor | Compatibility, isolation and drift tests | Separate later releases |

A broad usable beta is approximately 9–17 weeks if the fork passes Phase 0. A solo learning project may take considerably longer. Native mixing, transcoding and independent plugin distribution can each expand scope.

Suggested first public beta: local playback/lyrics, existing crossfade, composable presets, deterministic smart queue, command playlists and one self-hosted source. Add tiny acoustic ML and sharing as independent milestones.

## 14. Validation and resource budgets

Use a real low/mid-range Android phone and at least one older supported device. Define a reference library and codec corpus before measurement.

Proposed budgets:
- Interactive cached operations under 100 ms at P95.
- Smooth scrolling at the device’s selected refresh rate.
- Indexing progress visible and cancellable.
- Playback remains uninterrupted during scanning and recommendation work.
- Optional models and analysis disabled without loss of ordinary playback.
- APK and memory growth reported against the unmodified fork.

Required tests:
- Valid/truncated audio, long tracks, VBR, embedded art and missing tags.
- MediaStore and SAF imports; removable storage and revoked URI permissions.
- Crossfade with pause, skip, seek, buffering, audio focus and Bluetooth.
- Lyrics after seeks, speed changes and mid-fade song changes.
- Cold start, process death, screen off and queue restoration.
- Command filters, contradictory commands, duplicates and insufficient matches.
- Recommendations evaluated separately for similarity and emotional labels.
- Sharing range responses, token revocation, concurrent guests and unsupported browser codecs.
- Server authentication expiry and external plugin cancellation/failure.
- Appearance import migrations and accessible font/contrast behavior.

**No APK was built, repository cloned, model benchmarked or phone tested for this report.** Repository evidence establishes candidate suitability; Phase 0 establishes implementation suitability.

## 15. Immediate implementation brief

1. Fork PixelPlayerOSS under an original name; pin and record the upstream revision.
2. Reproduce its documented build without altering functionality.
3. Inspect playback, crossfade, lyrics, providers, native binaries and licenses.
4. Run the small codec/session test corpus on the target phone.
5. Extract source, queue and appearance contracts while retaining working behavior.
6. Deliver the first mixed-layout preset and deterministic command playlist.
7. Publish the contribution template before adding multiple new providers.
8. Advance only after each phase’s exit gate passes.

Retain upstream attribution and applicable GPL notices. Treat licensing of native binaries and plugins as an explicit dependency audit; a GitHub repository being public is not permission to redistribute everything inside it.

## References

All references are primary upstream repositories or official documentation, accessed 7 October 2026. Feature claims may describe upstream intent rather than verified runtime behavior.

[1] PixelPlayerOSS README: https://github.com/PixelPlayerHQ/PixelPlayerOSS
[2] APlayer: https://github.com/rRemix/APlayer
[3] Gramophone: https://github.com/FoedusProgramme/Gramophone
[4] Auxio: https://github.com/OxygenCobalt/Auxio
[5] Symphony: https://github.com/zyrouge/symphony
[6] Echo: https://github.com/brahmkshatriya/echo ; extension template: https://github.com/brahmkshatriya/echo-extension-template
[7] Namida current README/license notice: https://github.com/namidaco/namida
[8] VideoLAN Android README and engine distinction: https://github.com/videolan/vlc-android/blob/master/README.md
[9] LiteRT Android: https://developers.google.com/edge/litert/android
[10] Ktor server engines: https://ktor.io/docs/server-engines.html
[11] PixelPlayerOSS module configuration: https://github.com/PixelPlayerHQ/PixelPlayerOSS/blob/main/settings.gradle.kts
[12] Media3 formats and decoder extensions: https://developer.android.com/media/media3/exoplayer/supported-formats
[13] Media3 crossfade issue: https://github.com/androidx/media/issues/2
[14] Android dynamic code loading: https://developer.android.com/privacy-and-security/risks/dynamic-code-loading
[15] OpenSubsonic streaming: https://opensubsonic.netlify.app/docs/endpoints/stream/
[16] Android LAN permissions: https://developer.android.com/privacy-and-security/local-network-permission
[17] Android background media service: https://developer.android.com/media/media3/session/background-playback
[18] PixelPlayerOSS native dependency notices: https://github.com/PixelPlayerHQ/PixelPlayerOSS/blob/main/THIRD_PARTY_NOTICES.md
