package app.auralis.library

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackCheckpoint(val items: List<MediaItem>, val index: Int, val positionMs: Long)

class PlaybackCheckpointStore(context: Context) {
    private val preferences = context.getSharedPreferences("queue-checkpoint", Context.MODE_PRIVATE)
    fun read(): PlaybackCheckpoint? {
        return runCatching {
            val rows = JSONArray(preferences.getString("items", null) ?: return null)
            val items = (0 until rows.length().coerceAtMost(100_000)).map { index ->
                val row = rows.getJSONObject(index)
                val metadata = MediaMetadata.Builder().setTitle(row.optString("title")).setArtist(row.optString("artist"))
                    .setAlbumTitle(row.optString("album"))
                row.optString("art").takeIf { it.isNotBlank() }?.let { metadata.setArtworkUri(android.net.Uri.parse(it)) }
                MediaItem.Builder().setMediaId(row.getString("id")).setUri(row.getString("uri")).setMediaMetadata(metadata.build()).build()
            }
            if (items.isEmpty()) null else PlaybackCheckpoint(items, preferences.getInt("index", 0).coerceIn(items.indices), preferences.getLong("position", 0).coerceAtLeast(0))
        }.getOrNull()
    }
    fun save(checkpoint: PlaybackCheckpoint, queueChanged: Boolean) {
        val encoded = if (queueChanged) JSONArray().apply {
            checkpoint.items.forEach { item -> put(JSONObject().apply {
                put("id", item.mediaId); put("uri", item.localConfiguration?.uri.toString())
                put("title", item.mediaMetadata.title?.toString().orEmpty()); put("artist", item.mediaMetadata.artist?.toString().orEmpty())
                put("album", item.mediaMetadata.albumTitle?.toString().orEmpty()); put("art", item.mediaMetadata.artworkUri?.toString().orEmpty())
            }) }
        }.toString() else null
        preferences.edit(commit = true) {
            if (encoded != null) putString("items", encoded)
            putInt("index", checkpoint.index); putLong("position", checkpoint.positionMs)
        }
    }
}
