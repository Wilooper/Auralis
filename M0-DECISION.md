# M0 Decision: Device Gate Still Open

Date: 2026-10-07

## Provisional Direction

Continue Kotlin/Compose + LibVLC as the only audio engine, with Media3 Common/Session wrapping a custom `SimpleBasePlayer`. There is no evidence yet requiring a second engine or handwritten C++ DSP.

Compilation has established API/build compatibility for this prototype. It has NOT established codec support, playback reliability, acceptable CPU/battery usage, audible overlap or native resource stability on Android hardware.

## Crossfade

Keep the two-deck experiment available but disabled by default. The incoming deck starts muted near the end of the outgoing track. Gain movement starts after its native Playing callback. Both decks pause together; the fade clock freezes. Seek/skip/queue edits cancel the incoming deck. The incoming track becomes the public item only at commit.

The gain curve is normalized from sine/cosine gains so their sum is one. This trades constant-power behavior for conservative correlated-signal headroom; it is not a clipping guarantee or evidence of a gapless transition. Native integer-volume steps and initialization latency require listening and recording tests.

If overlap fails on the target device, retain basic playback with crossfade off. Test muted preloading next, then consider a native mixer only with measured evidence. Do not expand the engine interface to promise seamless transitions now.

## Open Gates

- Physical ARM64 phone tests in DEVICE-CHECKLIST.md.
- Format/container outcomes from known fixtures.
- Notification/headset/screen-off/focus and output-route checks.
- Native object lifetime and memory across repeated queue/fade operations.
- UI screenshots at small/large screen sizes, font scaling and TalkBack.
- Public-release licensing/source inventory and package/name clearance.

M0 is implemented in source but is not accepted as device-validated. M1 persistent-library development should follow the basic playback/lifecycle gate, rather than building all later features on an unverified engine.

## Scope Adjustments

Use three real modules in M0 instead of six partially empty modules. Library/design/extension modules will be extracted when indexing, appearance slots and providers are implemented. The initial minimum SDK is 26 for simpler audio-focus APIs; this remains a product choice to revisit if Android 7 support matters.

The metadata importer uses Android's retriever, which may understand fewer formats than LibVLC. Unsupported tag extraction does not block audio playback. LibVLC-backed metadata indexing is an M1 improvement.

The temporary queue and lyrics associations do not survive process death. Theme accents and crossfade preferences do. No performance or smart-recommendation claim is made.
