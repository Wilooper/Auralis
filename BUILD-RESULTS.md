# Auralis 0.5.0-dev — v5 SDK foundation

Built 2026-10-08. Android versionCode 6, minSdk 26, compile/targetSdk 36. Delivered APK is the ARM64 debug build, signed with the same certificate as the earlier Auralis builds. Install over 0.4.1 without uninstalling to preserve the library and preferences.

## Implemented

- File-based JSON manifests, schema/API version checks, install disabled, per-capability review, enable/disable/remove, update revocation, and maximum 24 extensions. No archive, APK, script or native-plugin execution.
- Versioned in-process host API and bounded event flow. Foreground/catalog, playback/stream, appearance/skin, web/embed and party/session channels have explicit operations and modes. There is no unrestricted externally exported device bridge.
- HTTPS catalog/search, optional encrypted bearer token, origin-scoped credentials, URI/redirect checks, request budgets and capped responses. Remote songs can replace the native queue or be appended to it.
- Random-token loopback audio proxy with Range forwarding and revision checks into LibVLC. Declared remote MP3/FLAC/WAV/Ogg demux selection disables playlist fallback; unsupported formats require a server adapter. Disabled/replaced/removed providers disconnect bridge access, stop their native playback, and cancel an incoming crossfade deck.
- Restricted, on-demand iframe WebView. File/content access, native JS interfaces, downloads, popups, mixed content and device permissions are blocked. Native web-resource interception denies redirects and applies origin/CSP/transfer restrictions. Remote web player JS runs only in the browser renderer, outside the extension host API.
- Server-backed create/join/refresh/enqueue/leave party queue operations. Native skin editor and importable skins for colors, text/font scale, row/cover sizes, corners, artwork atmosphere and standard/compact/covers library layouts.
- SDK guide, manifest and host schemas, REST OpenAPI, Python builder/validator, TypeScript types/helper, four sample files, Apache license, and a HTTPS catalog/audio/shared-queue reference server.

## Verification

**80 Android tests passed** (40 core-model, 40 app); **7 Python SDK/reference-server tests passed**. No failures/errors. Android tests cover the four distributed example manifests, unknown/executable fields, parsing limits, origin escapes, granular grants, update/disable/uninstall behavior, rate/response limits, protocol version/mode/operation restrictions, private stream tokens, redirects, iframe request/CSP rules and stream invalidation. Existing local library, lyric parsing, Smart Flow, queue and repeat/shuffle tests continue to pass.

App and playback lint completed with zero errors. App has 21 advisory warnings, mainly KTX suggestions plus intentional synchronous preference persistence on IO workers; playback reports no issues. No new runtime dependency was added. TypeScript helper was inspected but not compiler-checked; a TypeScript compiler was unavailable. Schema/OpenAPI JSON syntax and sample interoperability were checked; no independent OpenAPI/JSON-Schema linter was available.

API 28 x86_64 emulator smoke checks:

1. Actual Android document-picker import of a provider file; installed disabled. Permissions displayed with individual channel checkboxes and exact approved HTTPS origin.
2. Token saved through the manager with Android Keystore encryption. Authenticated HTTPS catalog returned the three original generated audio fixtures.
3. Native remote playback through the bridge: media-session state 3 (playing), advancing position 28,054 ms, speed 1.0, error null, queue size 3. MP3 playback with explicit remote demux succeeded.
4. Update/relaunch retained the remote queue and server settings; restored paused rather than starting audio. Subsequent playback on the delivery playback implementation also returned state 3 with no error.
5. Disabling the provider returned native playback to idle (state 0, position 0, speed 0.0, no error). Queue remained visible for user control.
6. Native appearance editor applied a local skin; accent/control/cover styling changed to the saved green palette. Screenshots are actual app screens, not mockups.

The HTTPS fixture used an ephemeral test CA installed in the emulator trust store, not an APK trust override. Test CA private keys and server credentials are not included in deliverables. The fixture server served locally generated audio, not a third-party service. Final iframe networking fixes received unit tests and a rebuild; no real third-party iframe playback or paired-device party sync test was performed. No physical phone was available, and the software-rendered emulator timings are not device performance measurements.

## Resource boundaries

Manifest 64 KiB/depth 16; 24 extensions; 8 KiB bridge request; 256 KiB API response; 100 catalog tracks; 2 API requests; 10 requests/min/extension. Native streams: 2 transfers, 4 queued socket tasks, 32 KiB buffers, 1 MiB/s/deck, 512 MiB response and a checked 30-minute transfer deadline. Connect/read timeouts and grant checks limit slow or revoked traffic. One web player; 6 web resource slots, 32 MiB/resource and a checked 120-second read budget. No extension background polling or executable on-device plugin runtime.

These are application guardrails, not a formal browser CPU/memory quota or a promise that every device or page can never overload. Independent security review and physical-device stress testing remain necessary before a public extension store.

## Current limits

- File loading only. URL installation, stores, publisher signatures and automatic updates remain future work.
- Party mode shares server queues. It does not synchronize clocks/audio, broadcast local tracks, automatically play the party queue or implement participant/host roles. The reference service uses a shared bearer token for a small trusted group; public hosting needs production authentication/roles/persistence.
- Self-hosted YouTube Music needs an adapter implementing the documented catalog/direct-audio contract. This build does not include a service-specific YouTube Music connector, browser-cookie importer, OAuth flow, or remote download/lyric contribution.
- Remote MP3/FLAC/WAV/Ogg only in native SDK v1. M4A/MP4/WebM/HLS/DASH/DRM require conversion/adapter work or a compatible web embed. Remote songs remain provider/queue entries; local favorites/history/analysis are backed by the local database.
- Skins are bounded declarative tokens and layout presets. They cannot replace navigation/security prompts or execute arbitrary UI code; some decorative artwork keeps its built-in palette.
- Provider frame/auth/DRM policies may block a web embed. Native queue, lyrics, repeat and crossfade do not control iframe audio. No host credentials are injected into web pages.

## Artifact checks

APK size: 76,857,751 bytes. APK SHA-256: `8f024d07b1ac0218d3cadf9a2efc56805acfc90b37cb4696bd86841163b00d92`.

Signing certificate SHA-256: `bf474b8376bc594d1e210b701835a334a8abae4747078a5cd75a739502026d21`. APK signing verification and 16 KiB ZIP alignment checks passed. Build logs, test XML, lint results, output metadata, media-session evidence and UI screenshots are included under `evidence/v05` in the source archive.

The source archive excludes build caches, local SDK paths, signing keys and test-server TLS keys. The standalone community SDK contains authoring tools/docs/examples and the server adapter; those tools run on developer computers/servers, never as phone extensions.
