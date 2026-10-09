# Auralis v4 finish — 0.4.1

Install over 0.4.0. Application ID stays `app.auralis`, version code is 5, and the saved development signing key is unchanged. The product remains in v4; v5 is reserved for the next feature milestone.

## Listening and navigation

- Bottom navigation: Home, Favorites, Queue, Lyrics. Tap the mini player to expand the full player.
- Top browsing tabs: Songs, Artists, Albums, History, Recently added. Tabs scroll horizontally on small screens.
- Settings is the single place to add/change/remove music folders, add individual files, refresh, and enable Android music access for download detection. Removing a source never deletes device audio. Changing a folder only removes the previous source after a new folder is selected and its saved permission is accepted.
- Favorites: heart buttons on library rows and full player; stored in SQLite and preserved through file metadata refreshes and reopening.
- History: songs are recorded by the playback service when playback starts, including screen-off changes. This is a latest-played list of unique songs, not a complete session event log. Clear it in Settings. Merely restoring a paused queue no longer counts as listening.
- Shuffle: stable traversal for each queue with an explicit matching Android timeline. Previous retraces that order. Tapping a different queued song begins a fresh traversal from that song. The enabled setting survives reopening; the random order can change after queue edits/reopening.
- Repeat cycles Off → All → One → Off. End-of-track behavior lives in the service; repeat-all wraps the queue. Repeat-one skips crossfade.
- Queue menu: Play next, Move up/down, Remove. Play next disables shuffle to honor the chosen immediate next song. Moving other items preserves the active decoder and its position. Removing the final item now clears the saved queue.

## Smart Flow

Open the sparkle button in Home. Analyze unprofiled songs, choose Wind down / Stay similar / Build energy, preview the order, and play it. Playing a flow turns shuffle off to preserve the intended sequence.

Analysis runs sequentially on a background dispatcher, only after a user request. It decodes at most a 16-second interior excerpt using Android MediaExtractor/MediaCodec, with an eight-second processing budget per track. It streams RMS/10-ms energy envelopes and zero crossings rather than retaining whole PCM songs. It estimates relative energy, a rough brightness proxy and tempo through onset-envelope autocorrelation. This is a lightweight deterministic audio analyzer, not a trained emotion model. The confidence field is a usability flag and is not a calibrated probability.

Results and unavailable/silent attempts are cached by track ID and size/modification fingerprint. File changes invalidate cached attempts. Cancellation keeps completed profiles; unsupported analysis codecs and silent/very short tracks are excluded from flow ordering, while LibVLC playback remains available independently. The app does not record the microphone or upload music.

The planner favors small changes in energy/brightness/tempo while moving toward a target, uses at most 25 distinct analyzed songs, and starts from the current song if its profile is available. Results are limited by the collection: it cannot guarantee a smooth transition if suitable intermediate tracks are missing. Loud mastering, quiet intros, tempo octave ambiguity, and different sections of a song can skew estimates. It does not infer Hindi/Punjabi meaning, sadness, romance, language, genre or musical key.

Analysis persists across process death, but an interrupted scan is resumed by pressing Analyze again. No continuous background acoustic scan is started on launch. Keep the app open during the initial analysis.

## DJ blend

Settings → Playback & transitions: enable DJ blend and set crossfade from Off to 12 seconds. Enabling DJ blend when crossfade is off chooses eight seconds. Start with 6–8 seconds.

Two LibVLC decks overlap using a smoothstep-shaped entrance/exit over the existing normalized gain envelope. Summed gain is capped at one; volume ramps tick at 40 ms during a transition. Transition selection respects shuffle and repeat-all, and repeat-one does not overlap. Seeking, queue edits, toggling modes or changing fade settings cancel the overlap safely. Short tracks skip overlap when their duration is less than twice the requested fade duration.

This is a DJ-style volume blend, not beat-matched mixing. There is no time stretching, key matching, EQ/bass swap, automatic silent-intro trimming, calibrated loudness normalization or hardware timing guarantee. Auditory quality and gap behavior must be tested on a phone; emulator tests cannot establish them.

## Phone checklist

1. Install the update without uninstalling. Check your saved folders, songs, queue, position and accents.
2. Heart a song, open Favorites, kill/reopen, and verify it stays saved.
3. Open History and Recently added; search title/artist/album there too.
4. Change a folder in Settings; cancel once and verify the original remains. Remove a source and verify audio remains in the file manager.
5. Enable shuffle and advance through several songs; try Previous. Try repeat-one at the end of a short song, then repeat-all at the queue end with the screen off.
6. Move/remove queued items while playing and verify the current position is preserved. Remove all items, kill/reopen, and verify the old queue does not return.
7. Analyze a small library, preview all three flow directions and play one. Cancel/restart analysis; kill/reopen and verify completed profiles are reused.
8. Enable a 6–8-second DJ blend. Check mixed formats, screen-off, Bluetooth, incoming calls/audio focus, pauses and seeks during overlap, and the last→first repeat-all transition.
9. Check long Hindi titles, missing art, larger fonts, short screens, lyrics, navigation and folder download updates.

## Next milestone

v5 can build on these foundations with validated mood/language tags, playlist commands, better native mixing/beat features and community provider extensions. No v5 scope is silently enabled by this update.
