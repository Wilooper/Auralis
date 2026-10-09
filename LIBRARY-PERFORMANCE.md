# Auralis 0.3 — Library and Performance

## What changed

Home can display the saved library without waiting for folder enumeration, metadata extraction or a playback controller. SQLite stores sources, track metadata, folder membership and recent history; it stores no audio or image bytes. Read grants are persisted through Android's folder picker. On upgrade, retained 0.2 grants are recovered. Removing a source removes its index associations and grant, never its audio files.

MediaStore supplies metadata in bulk for selected on-device folders. A content observer coalesces media changes and refreshes indexed rows; unchanged records avoid database rewrites and metadata extraction. This is a bulk metadata query with changed-row writes, not a MediaStore generation-delta implementation. Reopening/foregrounding refreshes the index too. Folder paths match boundaries, so selecting Music/Love excludes Music/Lovely.

Recursive SAF enumeration discovers unsupported/unindexed audio files and works when music access is denied or MediaStore is unavailable. The first full reconciliation also checks SAF in the background. Batches become visible while enumeration continues. Complete successful scans reconcile removals; inaccessible providers or missing mounts keep cached records. Providers without change notifications are checked every 30 seconds while the process is running. Force-stopping stops observers; the next open loads cached data and refreshes.

Artwork is loaded on demand through a private provider, independently of library scanning. It tries embedded artwork, then Android album artwork, samples images to at most 768 pixels, and caches by track fingerprint. Up to two extractions run concurrently. The disk budget is about 48 MiB, pruned periodically. Missing covers have a consistent placeholder. Platform metadata/artwork support can be narrower than LibVLC playback support.

Home includes recent additions, recently played songs, songs/artists/albums browsing, song/artist/album search, pagination and a mini player. Search folds Unicode using NFKC and Locale.ROOT; Hindi remains searchable and SQL wildcards are treated literally. Playback position updates do not trigger database browsing queries. Queue snapshots/position restore paused, with the most recent checkpoint potentially a few seconds behind the last audible position.

## Why Kotlin remains

The previous bottleneck was repeated discovery and audio-file reads, not a computational algorithm. Kotlin plus Android's index, SQLite, IO dispatchers and lazy image loading removes those repeated operations without a second application runtime. LibVLC remains the C/C++ decoder. Rust is not introduced for this update; profile a phone before adding another native boundary.

Gramophone's documented MediaStore indexing informed the approach. Its GPL source and assets were not copied; this is an original implementation.

## Measurement and limits

The reproducible 10,000-track host test writes its measurements to `evidence/library-host-benchmark.json`. It measures initial SQLite indexing, a cached 500-track page, artist search, unchanged-record checks and one changed record. Cached browsing opens zero audio files. It is a Robolectric API 28 Linux-host test, not measured phone startup, frame rate or battery consumption. No physical device is attached in this build environment.

Live-update integration tests use an Android-like content provider and notifications. They do not prove a particular downloader or OEM emits changes immediately. New downloads must finish and enter Android's index before the fast path can discover them. Downloads outside selected folders are intentionally excluded. Cloud providers and API 26–28 removable storage can use the slower SAF path. Tracks without readable artist/album tags fall back to Unknown; search cannot infer missing metadata.

## Phone acceptance checks

1. Install over 0.2 without uninstalling. Confirm retained folders appear, grant music access once, then index a large local folder.
2. Kill the app from recents, reopen, and confirm Home shows cached songs without asking for the folder. Repeat after force-stop and reboot. Record time to first useful song rows.
3. While Home is open, download a tagged song into the selected folder. Confirm it appears after Android indexes it, with no folder picker. Rename/delete it and check reconciliation.
4. Search part of a song name, artist and Hindi text. Browse artist/album groups and play a song; check the selected queue and mini player.
5. Use files with embedded art and files without it. Scroll during a first cover load and reopen with a warm image cache; inspect stutter and missing covers.
6. Add/refresh a folder while playing. Confirm audio and position continue. Cancel a large scan and verify already indexed songs remain saved.
7. Kill/reopen during playback, confirm queue/position recovery is paused, and press Play. Check notification, headset, focus and screen-off behavior again.
8. Deny/revoke music access, revoke a folder grant, and unmount an SD card. Expect retained cache and useful errors, not wiped history. Regrant/reselect as needed.

## References

- Gramophone: https://github.com/FoedusProgramme/Gramophone
- Android shared media: https://developer.android.com/training/data-storage/shared/media
- ContentObserver: https://developer.android.com/reference/android/database/ContentObserver
- Saved folder access: https://developer.android.com/training/data-storage/shared/documents-files

Shuffle/repeat, queue editing, saved playlists, custom UI layouts, plugins and recommendations remain subsequent work. This update addresses library persistence, discovery, covers and browsing.
