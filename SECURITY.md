# Security

## Current development boundary

The 0.5.0-dev extension foundation accepts bounded declarative JSON manifests, installs them disabled and requires per-capability approval. Updates revoke grants. Providers use approved exact HTTPS origins, bounded bridge calls and a private loopback audio proxy. Server credentials are stored using Android Keystore encryption.

Extensions do not load executable code on the phone. Remote web embeds run in a restricted WebView renderer without an Android JavaScript bridge. Web content can still consume browser resources; application limits are not a formal CPU/memory sandbox. See sdk/README.md and BUILD-RESULTS.md for limits and unverified behavior.

## Reporting

Report security-sensitive findings privately to the repository maintainer through GitHub private vulnerability reporting when enabled, or through a maintainer-provided private contact. No private reporting channel is configured in this source snapshot. Do not post working credentials or exploitable private details in a public issue. General non-sensitive bugs can use issues.

Include the affected version, capability, origin/mode, a minimal reproduction and the observed impact. Do not test other users' servers or accounts without authorization. Independent security review and physical-device stress testing remain gates before a public extension store.
