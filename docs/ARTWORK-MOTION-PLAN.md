# Artwork Motion and extension manager plan

1. Add an independent, data-only `artwork.apply` contribution. It must compose with existing skins and use bounded host renderers; no downloaded code, service or network permission is required.
2. Ship an importable Artwork Motion example with gentle drift, interactive tilt and still modes, plus a 60–100% now-playing cover-size setting. Persist settings per extension; validate choices, restore them on restart, and remove active effects when access is revoked.
3. Add a native configuration dashboard with a preview, motion chips, size slider and reset. Keep resize inside the available player layout so transport and lyrics remain accessible.
4. Improve manager navigation with scrollable categories, search and All/Enabled/Off filters, clear active artwork ownership and direct customization. Improve native companion search, status filters and task cancellation.
5. Stop artwork animation outside the foreground, on pause, with system animations disabled or battery saver enabled. Use layer transforms, without per-frame image decoding or extension IPC.
6. Add security/persistence regression tests, update the public schema and SDK docs, retry the local APK build, then run Android tests/lint and verify APK artifacts in the single existing GitHub workflow.

This is procedural motion for existing artwork, not TIDAL media retrieval or label-authored motion videos. Companion action APIs remain unchanged. No AI, equalizer, Dolby, engine replacement or YAML installation is added.
