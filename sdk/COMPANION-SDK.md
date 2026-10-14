# ReVa companion SDK v2

This SDK is a working installed-companion action protocol. It does not enable replacement playback engines, arbitrary downloaded native libraries, online APK installation or custom cross-process Compose objects. Those require the integration milestones in `docs/REVA-BASE-IMPLEMENTATION-PLAN.md`.

## Try the sample

Install the ReVa app APK and `reva-track-inspector-debug.apk` from the same GitHub workflow artifact. In ReVa open Settings → Extensions → Native, refresh, review Track Inspector's publisher identity, select its `ui.actions` permission and save. The player gains **Track info**, and Settings gains **Extension info**. Each action binds an isolated companion worker on demand, returns a host-rendered native panel, and releases its binding. No network, AI, audio effects or storage permission is used by this sample. It deliberately has no launcher activity.

Development APKs use a temporary debug key. Installed sample updates may require uninstalling a sample signed by another build. Publisher fingerprints are shown; they are identity information, not a safety rating.

## Developer contract

Depend on the `extension-sdk` Android library (currently a source module in this repository). Subclass `app.reva.sdk.ExtensionWorkerService` and implement `executeAction(action, input, host)`. Return a JSON object with only `title` and `text`. Input contains the selected track's title and artist on the player; library/settings actions receive an empty object. Output is limited to 16 KiB, title to 80 characters and text to 8000 characters. No HTML or executable renderer is accepted.

Declare an exported Android service with `isolatedProcess=true`, `process=:worker`, discovery action `app.reva.extension.WORKER`, and a metadata resource named `app.reva.extension.MANIFEST`. See `sample-extension` for the complete buildable example. ReVa validates the metadata, service flags, current APK signer set, version and update time. Any package update requires fresh capability approval.

The manifest resource is a strict JSON object: `sdk` (2), `name`, `capabilities`, `origins` and `actions`. Each action contains `id`, `label`, `icon` and `location`. Supported locations are `player`, `library` and `settings`. Icons are `info`, `lyrics`, `playlist`, `share`, `extension`. A companion can declare at most four actions per location. Stock screens display at most four contributions per location globally, ordered by installed package name. Custom host layouts can call the same `CompanionActions` composable. Per-user contribution reordering and external native dashboard activities are follow-up UI work.

## Grants

- `ui.actions`: user can invoke declared actions; required for activation.
- `network.fetch`: exact approved HTTPS origins, GET only, no redirects or extension-supplied headers. Responses must be JSON/plain text and at most 256 KiB including the returned envelope. Call `host.fetch(url)` on the worker task thread. ReVa performs I/O off the UI thread with public-address-filtered DNS, standard TLS verification, a 15-second call deadline, one request at a time per task, and ten requests per minute per companion. Tokens/authenticated provider integration is not included yet.
- `share.text`: exposes a host-owned Share button for result text. ReVa checks the grant again when the user taps it and opens Android's chooser. No silent upload or broad file access.
- `compute.background`: lets the user explicitly opt into background continuation. Declaring it alone does not enable background work; the permission and separate switch must both be approved. Limits remain unchanged. There is no automatic foreground-service escalation.

## Work and isolation

Maximum two compute tasks globally, one per companion, thirty seconds per action. Leaving ReVa cancels foreground-only sessions; the host disconnects the worker and revokes its private bridge. Approved background tasks can continue only within their existing deadline. Playback remains in the existing media service.

Cooperative SDK workers are single-threaded and have a self-termination watchdog. Host limits control admitted tasks, bridge calls and bindings. Android controls isolated-process cleanup; this is **not** an OS-enforced CPU/RAM quota for arbitrary companion APK components. A separate APK can contain other components and request its own Android permissions. No malware-free or hard resource-quota claim is made. Profile unbinding, native cancellation and process death on device before expanding this runtime.

Native C++/Rust can be packaged as Android `.so` libraries inside the companion APK and called through JNI from the isolated worker. Compile dependencies on the publisher's machine/CI for the target Android ABIs. Keep audio work out of the action protocol: PCM transport, realtime budgets, audio focus and engine selection need dedicated adapters. Do not send samples one by one over Binder. The first SDK rejects undeveloped engine/decoder capabilities instead of pretending they work.

## Diagnostics

The Native page lists the last 100 host-controlled task/network lifecycle events. Raw extension exceptions, request URLs, credentials and response bodies are not logged. CPU/RAM attribution and native symbolication require further device tooling; the event list is not a full debugger. Cancel tasks, disable an extension or change its grants to invalidate outstanding sessions.

Three worker/protocol failures in a host session suspend the extension and clear its grants. Review access again to retry. Admission gates reject excess Binder requests before posting to the main looper; terminal callbacks are accepted at most once. Pending network jobs and deadline timers are cancelled when a session ends.

## YAML distribution envelope (planned, not an executable loader)

YAML describes a precompiled companion release. It must contain only schema-defined data: manifest version, extension/package identity, display name, host SDK range, immutable artifact URL, artifact SHA-256, publisher certificate SHA-256, supported Android/ABI versions, capability summary and a dependency inventory with versions/licences. APK-internal metadata remains authoritative after installation. A remotely supplied hash is not publisher authentication; signatures and a trusted/explicitly accepted publisher identity are separate requirements.

No YAML command, script, tag, anchor, dependency installation instruction or source URL will be executed on device. The current Native page discovers installed APKs; YAML link review/download/installer handoff will be added only with the dedicated distribution verification flow. This avoids representing a YAML preview as a working native installer.
