# V5 SDK foundation — 0.5.0-dev (versionCode 6)

File-based JSON extension loading; install disabled; per-channel review; enable/disable/remove; revision-based revocation on updates. Channels/modes cover catalog, streams, party sessions, web embeds and skins. Internal versioned bridge envelopes and bounded host events are documented alongside a REST OpenAPI contract.

HTTPS providers browse/search and queue direct MP3, FLAC, WAV or Ogg audio through a token-protected loopback proxy into LibVLC. Explicit native demux selection disables playlist fallback. Host credentials stay encrypted in Android Keystore storage and are restricted to the configured server origin. Redirects and undeclared origins fail closed. Queue restoration rechecks the extension grant.

Internet and Party pages live in the extension manager. Parties are server-hosted shared queues with create/join/refresh/enqueue/leave operations. They do not synchronize device clocks, stream local audio to guests, or auto-play a party queue. A self-hosted YouTube Music service needs an adapter returning the documented catalog/direct-audio contract; no service-specific connector or browser-cookie importer is supplied.

Skins change theme colors/text, font/scale, list row/cover sizes, corner radii, artwork atmosphere and standard/compact/covers library presentation. The manager also includes an accessible native appearance editor. Arbitrary executable UI skins and replacement of security/navigation screens are excluded. Some decorative artwork retains the original palette.

Web embeds use a remote iframe, no native JS bridge, file/content access or device permission grants. One on-demand WebView closes when backgrounded. Native request interception denies redirects and applies origin/CSP/buffer/response limits; provider-specific framing, authentication or DRM restrictions may still block playback. Browser CPU/memory cannot be guaranteed by these application-level limits.

SDK: JSON schemas, Python manifest builder/validator, TypeScript types/helpers, four example manifests, a HTTPS catalog/audio/shared-queue reference server, and a community-author guide. File installation only; URL installation and extension store remain future work.

The core library/favorites/history/lyrics/repeat/shuffle/Smart Flow/DJ features continue. This version introduces no new runtime dependency and no exported extension execution endpoint. It is a debug development build and has not received an independent security audit or physical-device stress certification.
