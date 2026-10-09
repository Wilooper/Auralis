# Product and implementation mind map

View the [SVG diagram](MIND-MAP.svg) or [PNG preview](MIND-MAP.png). This Mermaid source is editable.

```mermaid
mindmap
  root((Android music player))
    Playback foundation
      LibVLC engine
      Background media session
      Formats and crossfade prototype
    Personal experience
      Independent UI slots
      Local synced lyrics
      Preset import and export
    Local intelligence
      Command playlists
      Similar track ranking
      Gradual mood journeys
    Sources and sharing
      Local files and folders
      Self hosted adapters
      QR browser listening
    Community and quality
      Versioned extension contracts
      Optional feature modules
      Performance and device gates
```

## Dependencies that determine build order
Playback and queue recovery precede UI expansion. Source identities precede mixed-source queues. A deterministic ranking baseline precedes acoustic ML. A trusted local share session precedes remote sharing or listen-together. External executable plugins follow a stable SDK.

## Release split
Core alpha: local playback, artwork, lyrics, queue recovery.
Personal beta: UI slots, verified crossfade, command playlists, similar mode.
Connected beta: one self-hosted source and LAN browser listening.
Later: optional acoustic model, mood journeys, external plugins and synchronized listening.
