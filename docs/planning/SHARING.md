# Phone-hosted browser sharing

## Initial scope
Independent listening to selected local tracks on the same Wi-Fi or hotspot. Remote sharing and synchronized listen-together are later capabilities.

## Session flow
Host selects tracks → starts visible session → receives expiring QR/link → guest joins → host can revoke guest or stop session.
The app displays connection scope, selected tracks and current guest count.

## Proposed API
| Route | Purpose |
|---|---|
| POST /v1/join | Exchange invitation for short-lived session |
| GET /v1/session | Session name, expiry and permissions |
| GET /v1/playlist | Allowlisted metadata with opaque IDs |
| GET /v1/tracks/{id}/audio | Authorized ranged media stream |
| GET /v1/tracks/{id}/artwork | Bounded artwork response |
| GET /v1/tracks/{id}/lyrics | Selected lyrics document |
| POST /v1/leave | Revoke guest session |

A browser client can use a fragment invitation plus join exchange to reduce URL-secret leakage; guest cookies/credentials still need an explicit LAN HTTP versus HTTPS design. LAN HTTP is a trusted-network prototype assumption, not encrypted transport. Never transmit upstream credentials to guests.

Resolve opaque IDs through a selected-track allowlist. Never accept a raw filesystem path. Check authorization on each resource request, including artwork and range requests. Stop revokes all sessions.

## Audio and web client
Browser needs HTML/CSS/JavaScript; Android server remains Kotlin.
Start with native HTML audio playback, track list, seek, cover and lyrics. User interaction starts playback to satisfy autoplay policies.
Support correct MIME, lengths where available, byte ranges and stream cleanup. Nonseekable source responses are explicitly nonseekable.

LibVLC decoding on the phone does not expand browser codec support. Probe/document client support. Optional cached transcoding is later; its encoder, native package size, disk usage and battery cost need a separate gate.

## Lifecycle and network
Evaluate embedded Ktor CIO or a small Kotlin HTTP server on Android. Keep a visible user-started session. Media playback service cannot be used solely as an idle-server keepalive; choose a compliant foreground-service/lifecycle path for the target Android version.
Handle LAN permissions, denials, interface changes, client isolation, hotspot behavior and OEM background restrictions.

Bind only intended interfaces where practical; regenerate invitations after address changes. Do not add port forwarding automatically.

## Three separate modes
Independent listening: guest controls guest playback.
Remote control: authenticated guest sends host playback commands.
Listen-together: shared timeline, clock estimation, buffering and drift correction.
Only independent listening belongs to first sharing release.

## Limits and tests
Start with a configurable two-guest cap and no transcoding. Measure bandwidth, memory and screen-off behavior.
Test token expiry, revocation, unknown track IDs, traversal attempts, concurrent ranges, disconnect cleanup, long files and unsupported codecs.
Remote access requires a reachable endpoint/tunnel/relay and an encrypted authenticated transport design. A private IP URL is not a global invitation.
