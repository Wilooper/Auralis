# ReVa base and companion extension implementation plan

Status: planned before implementation. Baseline: main at f1b2da492a2c822ced4a3dda1e03dcfb1b13849d.

## Product boundary

ReVa remains a local-first Android music player. The default UI becomes light, with warm neutral surfaces, deep teal controls, readable text and restrained artwork atmosphere. Equalizer, Dolby, spatial processing, AI, online providers and a device shell are future features; no provider or local model will be built into this change. Internet access is available as an explicitly granted extension capability, not a stock online service.

Existing JSON/data extensions remain compatible. Native extensions use separately installed companion APKs. ReVa never evaluates YAML as instructions, downloads executable source into its process, or compiles dependencies on the user's phone. Kotlin and native Rust/C++ dependencies are built by extension publishers.

## Architecture decisions

1. A small Android SDK module defines a versioned Binder protocol and extension service base. Companion workers declare `isolatedProcess=true`, expose the discovery action, and have no independent Android permissions while executing in that worker. The host validates the service metadata and publisher certificate before binding an explicit component.
2. Native companion APKs retain their own Android identity outside the isolated service. ReVa cannot sandbox their other components. The installer must explain this boundary; publisher identity is not a malware-free guarantee. Never label an arbitrary installed APK as broker-only merely because it declares a capability.
3. Each activation grants capabilities separately. Revision-bound sessions are invalidated when disabled, permissions change, the publisher changes, or the package is updated/removed. A private Binder session is supplied only to the chosen worker, not through an exported generic host service.
4. Network access uses a host broker: exact HTTPS origins, GET initially, no arbitrary headers/credentials, redirects disabled, bounded concurrency/response/time, and checks before and after I/O. No cleartext, loopback or private-network grants in the initial SDK. Sharing is user initiated through the Android chooser; extensions do not receive broad filesystem access.
5. Compute actions have one active task per extension and a small global worker limit. Leaving the visible app disconnects foreground compute workers unless the user explicitly approved background processing. There is no automatic foreground-service escalation. Playback stays in its existing media service and is not cancelled with compute tasks.
6. Host deadlines and binding revocation bound accepted work and bridge access. They do not constitute hard per-APK CPU/memory quotas. Android owns isolated-process lifetime after unbinding. Device tests must verify the behaviour before resource guarantees are made. Do not claim that cooperative SDK limits contain hostile code in other APK components.
7. UI contributions are named actions with a fixed icon vocabulary and supported locations (player, library, settings). The host validates IDs and labels, provides accessible labels, limits visible actions, and renders native result panels. Custom skins can consume the same registry. Arbitrary cross-package Compose objects are not passed into the host process.
8. Engine/decoder contracts must separate session/queue ownership from backend processing. ReVa keeps the built-in LibVLC backend until an alternative can pass state, focus, queue, seek, format and recovery tests. Native ABI uses opaque handles and C-compatible messages; no raw Kotlin/Rust/C++ objects across process boundaries. Full engine replacement and PCM transport require an additional integration milestone; merely declaring an engine capability must not pretend to replace playback.

## Ordered milestones and acceptance criteria

### A. Publish this plan

- Create a fresh branch from current main, retaining the user's existing checkout.
- Push this document before writing application code.
- Open a reviewable PR. Keep the repository's single APK workflow.

### B. Light UI

- Use semantic light/dark colour roles selected from skin luminance, with light stock defaults.
- Change launch/system bars and artwork atmosphere to match the selected theme.
- Remove dark hardcoded fallback artwork and unreadable light-theme progress labels.
- Preserve existing navigation, library, player, lyrics, queue and JSON extension behaviours.
- Treat art/text layout dimensions as proposed targets, not REA measurements.
- REA/source measurement remains a separate investigation: REA is not installed here and no runtime reference measurements have been performed. Do not claim its results.

### C. Companion SDK and isolated worker proof

- Add a minimal Android library with Binder interfaces, protocol validation, and service lifecycle hooks.
- Discover installed companions only; do not auto-install unknown APKs.
- Show package, service, publisher SHA-256, requested access, Android permission boundary and version.
- Provide a local sample action companion APK without internet/AI dependencies.
- Include the sample in the existing workflow build/artifact step so the protocol is reviewable and executable.
- Verify version mismatch, invalid metadata, certificate changes, disabled sessions, oversized requests and binder death handling.

### D. Broker, contributions and budgets

- Add per-extension capability grants and user-controlled background processing (off by default).
- Enforce active-task and elapsed-time limits before starting workers. No work from discovery alone.
- Route network requests through host validation; blocked I/O must not freeze the UI thread.
- Surface valid action icons in stock player/library/settings locations and return clean host-native panels.
- Cancel/revoke compute tasks on visibility loss, disable, timeout and package changes.
- Add sharing of selected extension result text through an explicit user action.
- Log bounded events using controlled fields, with no URLs containing credentials or raw extension payloads.
- Diagnostics show state, deadlines and reasons. CPU/RAM attribution remains unmeasured until profiled on device.

### E. Packaging and replacement-engine follow-up

- Define a strict YAML v2 distribution manifest and developer documentation describing identity, compatibility, artifacts, hashes, capabilities, origins, contributions and build dependencies.
- Manifest links describe releases; they never execute build commands. Existing JSON manifests retain their v1 loader.
- Gate online APK installation, native custom dashboards and replacement engines behind their completed proof/tests. These are not silently implied by a generic action worker.
- Add backend negotiation, file-descriptor/PCM transport and focus/session adapters before enabling engine replacement. Avoid per-sample Binder calls.
- Highest-quality audio and full-resolution artwork selection must be verified against the actual decoder/output and embedded/remote source; do not invent quality by upsampling.

## Verification and delivery

- Core policy tests: grants, revocation, global/per-extension capacity, visibility/background permission, deadline expiry and retention bounds.
- Android tests: companion discovery metadata and certificate rules, Binder protocol limits, network origin/size/redirect limits, and stock light/legacy-dark skin roles.
- Build the app, SDK and sample companion with the existing GitHub workflow. Retry a local APK build, but push code if local toolchain downloads fail (user-authorized).
- Maintain only `.github/workflows/reva-apk.yml`.
- Report exactly what is functional, what is a contract/proof, and what still needs device testing. Desktop build success is not evidence of battery, heat, malware prevention or audio fidelity.

## Performance budgets for the first proof

- Maximum 2 active compute workers globally, 1 task per extension.
- Maximum 30 seconds per foreground action, 15 seconds for broker network response, 256 KiB response, and 10 requests per minute per session.
- Background permission is explicit and does not increase task/concurrency/network limits.
- Maximum 4 visible contributions per location and 100 retained diagnostic events globally.
- All numbers are conservative initial limits; benchmark before changing them.

## Deferred explicitly

Equalizer UI, Dolby codecs/licensed integrations, stereo spatializer, AI/local models, built-in internet lyrics/catalogs, local shell/server, arbitrary downloaded DEX/.so loading and automatic plugin installation.
