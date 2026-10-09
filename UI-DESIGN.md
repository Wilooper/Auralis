# Auralis 0.4 — Default interface

The default design combines an immersive Apple Music-inspired listening experience with dark discovery surfaces inspired by Spotify, YouTube Music and Tidal. Auralis keeps its own name, original layouts and icons; third-party screenshots, logos, fonts and artwork are not bundled.

## Screens

| Surface | Design | Actions |
|---|---|---|
| Home | Near-black canvas, rose accent, editorial headings, featured real track and horizontal cover shelves | Play featured/recent songs, search titles/artists/albums, browse groups, manage folders |
| Artists/albums | Two-column artwork grid; circular artist images, square album covers | Open a group's songs and play them |
| Mini player | Floating rounded surface, compact artwork, two-line metadata and thin progress rail | Open player, play/pause, next track |
| Full player | Static soft album-art backdrop, large square cover, white controls, light seek rail | Seek, previous/play/pause/next, open lyrics/queue, collapse to Home |
| Lyrics | Same artwork atmosphere, large bold text, bright current line and subdued surrounding lines | Word highlighting, timed-line seeking, attach/reset lyrics, pause/next, resume follow |
| Queue | Clean numbered rows and highlighted current song | Select a queued track |

The featured track and shelves come from actual recent history/additions. There are no fabricated recommendation playlists or decorative inactive playback buttons. Shuffle/repeat, favorites, volume controls and playlist editing are not introduced in this visual update.

## Navigation and readability

Home and Queue retain bottom navigation and the mini player. Music and Lyrics use immersive layouts with a collapse arrow, a Music/Lyrics switch and a queue shortcut. Android Back collapses the immersive view. Settings remain accessible throughout.

Search and song/group lists remain backed by the existing saved library. Empty states explain how to add a folder or lyrics file. Titles use bounded wrapping or ellipsis. Song rows and player controls use touch targets of at least 48 dp. The main player scrolls on short displays and respects system insets. Accent choices are retained by their old indices; rose is the new default when no accent has been saved.

Timed lyrics pause follow when the user scrolls, then resume after six seconds without a gesture. The Follow lyrics control resumes immediately. Plain lyrics scroll freely and do not acquire invented timestamps. Timing and supported embedded containers remain unchanged.

## Rendering cost

The atmosphere uses the existing private artwork provider and Coil. A remembered request downsamples to 96 pixels, performs a fixed 64-pixel blur off the main thread and caches the result by image/request identity. It works on API 26 without adding a graphics library or depending on API 31 blur. Gradients and scrims are static; no continuously animated backdrop, audio-reactive shader or scan-time cover extraction is added. The full-size cover and background use separate image requests.

## Device review

Check Home with an empty and populated library, very long/Hindi song names, missing art, cold/warm cover caches, search and artist/album grids. Confirm a first cover decode does not block scrolling or audio. Check both portrait/landscape and enlarged font sizes; the main player reserves space for controls and remains scrollable for unusually long titles/font sizes. Verify seek gestures, word/line/plain lyrics, reading ahead, Follow lyrics, attached lyrics reset, mini-player expansion/collapse and Android Back. Phone battery/frame timing requires physical-device measurement.

Reference descriptions: Apple lyrics controls at https://support.apple.com/en-lamr/109355 and Android image customization at https://developer.android.com/develop/ui/compose/graphics/images/customize. These guide interaction and platform considerations, not a claim of pixel-identical reproduction.


## v4 finish / 0.4.1

Folder controls are confined to the dedicated Settings screen. Bottom navigation replaces Playing with Favorites and keeps Queue/Lyrics. Now Playing remains accessible from the mini player. Home tabs now include History and Recently added; they scroll horizontally. Favorite hearts appear in library rows/full player; shuffle/repeat icons live in the transport row. Queue overflow menus expose Play next, Move up/down and Remove. Home has a sparkle action for acoustic Smart Flow analysis/preview. See V4-FINISH.md for behavior and limits.
