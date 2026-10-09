# Extension SDK and appearance contracts

## Extension categories
MusicSource, LyricsProvider, QueueStrategy, AudioAnalyzer and AppearancePack. Playback engine and audio-focus ownership stay in core for the initial releases.

## Distribution levels
| Level | Delivery | Added without app release? | Trust boundary |
|---|---|---|---|
| 1 | Bundled Kotlin module | No | Same process and privileges |
| 2 | Declarative preset/alias pack | Yes | Validated data only |
| 3 | Separately installed provider app | Yes | Android process/UID and narrow IPC |

“Enable/disable modules” is achievable immediately; independent executable installation is later work. A Flutter package or Gradle module is not automatically a runtime plugin.

## MusicSource operations
- capabilities(instance): supported browse/search/lyrics/cache/share operations.
- browse(instance, parent, pageToken): paged item references.
- search(instance, query, pageToken): paged results.
- metadata(instance, trackId): attributed metadata.
- resolveStream(instance, trackId, quality): temporary playback descriptor.
- artwork(instance, trackId): scoped image descriptor.
- logout(instance): remove tokens and invalidate cached descriptors.

All requests need cancellation, timeout, typed failure and bounded response sizes. Do not send unbounded libraries or artwork bytes in Binder transactions. Use pagination and controlled descriptors for large data.

Error classes: unavailable, authentication-required, expired, unsupported, rate-limited and malformed-response. Core converts them into actionable UI and playback recovery.

## Versioning
SDK major versions represent breaking contracts; minor versions add optional capabilities. Negotiate features before calling them. Stable package IDs and signing identity protect updates. Manifest min/max compatibility should be checked before activation.

External plugins need signature/trust review, explicit install/enable, permitted-host configuration and log redaction. Network host declarations are policy checked by core where it makes requests; declarations alone cannot restrict an external app’s own network permissions.

Prefer native IPC to remote DEX/JAR loading. Do not load downloaded native binaries into core as an ordinary provider plugin.

## Appearance schema
Independent slots: home, artwork, fullPlayer, miniPlayer, queue, lyrics and navigation.
Tokens: color palette, text scale, radii, spacing, animation budget and blur.
Component IDs resolve to shipped components. Unknown IDs fall back safely.
Import validates schema version, sizes, colors and asset origins; it never executes code.
Store presets independently of playback state, with migrations and reset.

The example preset combines artwork-focus styling, a compact mini player and recommendation shelves. These are original component styles inspired by familiar patterns, not copied brand assets.

## Contributor deliverables
A source-only sample provider, fixture responses, contract tests, documentation, license, declared data access and compatibility manifest.
Checklist: no secrets in repository; no blocking main-thread work; cancellation works; errors typed; no direct engine calls; source IDs scoped; stream expiry respected.

## Compatibility suite
Test browse pagination, malformed metadata, authentication expiry, timeouts, cancellation, URL/header redaction, unsupported capabilities and SDK mismatches.
An external provider process crash should affect that request/source, not terminate ongoing local playback.
