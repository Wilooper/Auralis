# Auralis community SDK v1 — Android 0.5.0

Auralis loads **data-only extension files**. Community business logic runs on a server behind open HTTPS APIs. The phone renders approved contributions and performs checked operations through its host bridge. No extension has filesystem access, shell access, native code, reflection, dynamic class loading, or an unrestricted JavaScript-to-Android bridge.

## Quick start

1. Copy `examples/midnight.auralis.json` to your phone.
2. Open Auralis → Settings → Extensions & internet → Import extension file.
3. Open Access, select individual capability grants, then Allow & enable. Disable or Remove revokes access. A replacement with the same ID is installed disabled and requires review again.
4. To connect your server, generate a manifest:

```sh
python3 python/auralis_sdk.py provider --id org.community.myserver --name "My music" --base-url https://music.example.com --output myserver.auralis.json
python3 python/auralis_sdk.py validate myserver.auralis.json
```

Run these SDK tools on your development computer. Their Python or TypeScript code is **never loaded into Auralis**. Edit a generated manifest's approved origins and contribution URLs before importing it. The server must present a certificate trusted by Android; certificate errors are never bypassed. HTTP-only LAN services need an HTTPS reverse proxy.

## Channels and modes

| Capability/channel | Mode | Operation | Host behavior |
|---|---|---|---|
| `catalog.read` | `foreground` | `search` | User-triggered, bounded HTTPS catalog fetch |
| `stream.play` | `playback` | `prepare` | Validates a direct audio URL and creates an opaque queue URI; playback starts only through Auralis UI |
| `party.session` | `party` | `request` | User-triggered create/join/state/enqueue/leave on your server |
| `web.embed` | `web` | `describe` | One remote iframe player in a restricted WebView, opened by the user |
| `skin.apply` | `appearance` | `describe` | Bounded, accessible color/typography/size/layout tokens applied by Compose |

The Kotlin host API is `ExtensionBridge.call(extensionId, BridgeRequest)`. It checks API version, request ID, mode, capability, revision, origins, operation, and payload budget. `host-bridge.schema.json` documents the envelope. It is an **in-process API**, not an exported Android service or publicly reachable device HTTP API. Do not call the private loopback stream proxy from third-party applications. Adding an unrestricted external bridge would weaken this security boundary.

Host observers can subscribe to the bounded `ExtensionBridge.events` flow (`host.lifecycle/revoked`, channel/completed, `stream.play/prepared`). This flow carries no tokens, local files, song URLs or listening history; it has a 16-event buffer, no replay or polling. SDK v1 extension files do not subscribe to events or run handlers on the phone.

## Provider API

`openapi.json` defines the server-facing REST API. `GET /v1/catalog?q=&limit=100` returns `{"tracks":[{"id":"song1","title":"Song","artist":"Artist","streamUrl":"https://music.example.com/v1/audio/song1"}]}`. Maximum 100 unique IDs per page. Optional `format` is `mp3` (default), `flac`, `wav` or `ogg` (including Opus). Auralis forces the corresponding native demuxer with automatic/playlist fallback disabled. MP4/M4A, WebM, HLS/DASH and remote playlists need a server-side adapter/transcode in SDK v1. Stream URLs must use a manifest-approved exact HTTPS origin and return direct audio bytes, not an HTML player page, playlist, DRM license flow or redirect.

Auralis sends an optional bearer token only to the catalog origin (or the party origin for a party-only extension). Set it in the extension manager; it stays encrypted with an Android Keystore key. Credentials are not stored in manifests or queued media URLs. Do not use credential-bearing URLs, cookies, browser sessions, or URL tokens as a replacement.

Audio is proxied through a random-token loopback route into LibVLC; Range requests support seekable servers. Each deck opening rechecks its extension grant, including after restoring a saved queue. Disable/update/remove invalidates tokens, disconnects bridge streams, and stops native playback for the revoked provider; an incoming crossfade deck is canceled too. Remote lyrics and downloads are not provider contributions in v1; imported LRC files remain usable. Remote songs appear in the play queue rather than the local database.

For a self-hosted YouTube Music service, write a server-side adapter returning this catalog shape and authorized direct audio responses. The example is a local-file server, **not an implemented YouTube Music connector**. Keep service credentials and server-specific code on your server.

## Party API

`POST /v1/party` accepts `{action, session, trackId}` and returns `{session, queue}`. The app can host/create, join, refresh, enqueue and leave a server-hosted shared queue. Refresh is manual. The included reference server uses one bearer token for a trusted group, opaque session IDs, a 100-item queue and a two-hour idle expiry. Leave returns a local leave acknowledgement; it does not delete the shared session or implement participant identities. For a public party service, implement per-user authentication, membership/host roles, persistence and abuse protection on your server.

SDK v1 does not automatically play the shared queue, synchronize clocks, stream local files to guests, or run a LAN listener. These require a future party synchronization protocol. The UI calls this a server-hosted shared queue and does not promise synchronized audio.

## Skins

Use **Customize appearance** in the extension manager to edit these tokens directly on the phone, without writing a file. Apply creates a local appearance-only extension that can be disabled or removed like any other skin.

Tokens: `primary`, `background`, `surface`, `text`, `muted` (#RRGGBB); `textScale` (.85–1.3); `rowHeight` (64–112 dp); `cornerRadius` (0–32 dp); `artworkSize` (40–80 dp); `layout` (`standard`, `compact`, `covers`); `font` (`sans`, `serif`, `mono`); `atmosphere` (boolean). Text and surface/background must reach 4.5:1 contrast; muted/primary have lower 3:1 checks. Native accessibility scaling still applies. Last enabled skin with a `skin.apply` grant wins. Disabling it restores the previous skin or built-in theme.

Skins style native text, surfaces, controls and library row/grid presentation. They cannot inject arbitrary HTML/CSS/Compose code, replace native navigation, remove security prompts, or shrink controls below supported touch sizes. Some artwork/featured decorations retain their built-in palette. This is the safe declarative skin API foundation, not an arbitrary UI runtime.

## Web/iframe players

`web.url` is an HTTPS embed URL. The parent iframe uses sandbox restrictions; WebView blocks file/content access, native interfaces, downloads, popups, mixed content, camera, microphone and geolocation. Only approved origins may load web resources. Add required CDN/script origins explicitly and review them; wildcards and origin lookalikes are rejected. Third-party cookies and DOM storage are disabled. No provider token is injected into the iframe.

Opening an embed pauses native playback. Close/background destroys or unloads the player; native playback resumes only when you press Play. Remote provider JavaScript can run in the WebView renderer to operate its player, but it cannot call the Auralis bridge or device APIs. It is not an extension script runtime. Provider framing restrictions, DRM, authentication and codec support can prevent a particular embed from playing. Native queue/repeat/crossfade controls do not control iframe audio.

## Resource and security budgets

24 installed manifests; 64 KiB per file; depth 16; 12 capabilities/origins; 8 KiB bridge request; 256 KiB JSON server response; 100 catalog tracks; 2 concurrent API requests; 10 requests/minute/extension; 8 s connect and 15 s read timeouts plus a checked 15 s response-read budget. Streams: 2 active transfers, 4 waiting socket tasks, 8 KiB request headers, 32 KiB transfer buffers, 1 MiB/s/deck, 512 MiB/response and 30 min transfer deadline. Loopback stream grants: 256 maximum. One WebView, created on demand, no background polling. Local library and playback behavior remain available without extensions.

Approved exact HTTPS origins are a user authorization boundary, including explicitly approved private servers. DNS destinations behind an approved hostname are not independently pinned. Redirects are denied by the native bridge. The web request filter restricts requested URLs, but WebView is a separate browser networking stack; iframe resources use an additional native redirect-denying fetch path (6 requests, 32 MiB/resource, 120 s read budget); response CSP restricts connection origins, forms, workers and nested frames. Malicious-page CPU/memory use is not a proven hard resource quota. No system can promise zero overload on every device or web page. Do not treat these guardrails as an independent security audit or renderer CPU/memory guarantee.

## Reference server

```sh
export AURALIS_SERVER_TOKEN='a-random-secret-at-least-24-characters'
python3 python/reference_server.py --music /your/music --base-url https://music.example.com --cert /your/fullchain.pem --key /your/privkey.pem --bind 127.0.0.1 --port 8443
```

Use a trusted TLS certificate/reverse proxy, configure your private hosting access, then import the generated extension. The server scans up to 500 supported files at startup and exposes catalog, Range-enabled audio and shared queues. Its stdlib implementation is a reference for a trusted small group, not a production community hosting platform. It never serves arbitrary paths.

`python3 -m unittest discover -s python -p 'test_*.py'` runs SDK and reference-server tests. Android bridge and registry tests live in `app/src/test/kotlin/app/auralis/extensions`.

## Future compatibility

`apiVersion: 1` is explicitly versioned. Unknown capabilities, contribution fields, modes, operations and versions fail closed. URL installation, archive packages, publisher signatures, extension store discovery, executable extensions, arbitrary API proxying and scheduled/background modes are not implemented. A store can later distribute these same manifest files, but publishing/signature and update policy will need their own design.

Security implementation references: [Android WebView file-access guidance](https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion), [Android cleartext guidance](https://developer.android.com/privacy-and-security/risks/cleartext-communications), and [LibVLC per-media options](https://videolan.videolan.me/vlc/master/group__libvlc__media.html).
