# Decisions, assumptions and evidence

## Accepted working decisions
| ID | Decision | Reason |
|---|---|---|
| ADR-001 | Original Android application | Stable customization/plugin architecture can be designed directly |
| ADR-002 | Kotlin + Jetpack Compose | Android-first scope and direct platform integration |
| ADR-003 | LibVLC is the sole playback engine initially | Broad decoding with an existing native engine |
| ADR-004 | Media3 session adapter is allowed | System controls can remain separate from decoding |
| ADR-005 | Bundled extensions first | Establish contracts before independent executable distribution |
| ADR-006 | Deterministic recommendation baseline first | Useful offline behavior without unknown model quality |
| ADR-007 | LAN independent browser listening first | Bounded scope without a relay or synchronization system |

## Open decisions
Crossfade with two LibVLC players versus native mixer; exact dependency versions; minimum Android/ABI support; embedded server implementation and background-service category; external plugin trust model; model/weights/dataset; web transcoding; remote transport.

LibVLC’s broad support does not guarantee every exotic file, gapless behavior or crossfade. These remain device/build-specific acceptance gates.

## Risks
| Risk | Early mitigation |
|---|---|
| Transition timing and clipping | M0 overlap prototype and output recording |
| UI customization grows too large | Fixed slots and curated components first |
| ML misses Indian music labels | Language-specific evaluation and user corrections |
| Android kills sharing session | Real device lifecycle spike before feature buildout |
| Native dependency increases app size | ABI-specific packaging and measured size |
| Community plugin destabilizes app | Capability contracts, IPC later, bounded calls |
| Browser cannot decode local format | Explicit support message; optional transcoding later |

## Primary references
The project research and preceding engine comparison used these sources. They establish integration options, not app benchmarks.

- VideoLAN Android/LibVLC integration and licensing: https://github.com/videolan/vlc-android/blob/master/README.md
- Media3 custom Player/session integration: https://developer.android.com/media/media3/session/player
- Background media service: https://developer.android.com/media/media3/session/background-playback
- Android NDK: https://developer.android.com/ndk/guides
- Dynamic code loading guidance: https://developer.android.com/privacy-and-security/risks/dynamic-code-loading
- Local network permissions: https://developer.android.com/privacy-and-security/local-network-permission
- LiteRT Android: https://developers.google.com/edge/litert/android
- Embedded Ktor server engines: https://ktor.io/docs/server-engines.html
- OpenSubsonic stream contract: https://opensubsonic.netlify.app/docs/endpoints/stream/

Do not copy old build commands from upstream READMEs without validating current artifacts/tooling. Record licenses for the exact packaged binaries and model weights. Public source availability alone is not redistribution permission.
