package app.auralis.library

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.auralis.model.LibraryTrack

fun LibraryTrack.artworkUri(context: Context): Uri = Uri.Builder().scheme("content").authority("${context.packageName}.artwork")
    .appendPath("cover").appendPath(id).appendQueryParameter("v", fingerprint).build()

fun LibraryTrack.mediaItem(context: Context): MediaItem = MediaItem.Builder().setMediaId(id).setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
        .setArtworkUri(artworkUri(context)).build()).build()
