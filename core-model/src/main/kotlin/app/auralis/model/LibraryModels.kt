package app.auralis.model

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

data class LibraryTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String = "Unknown artist",
    val album: String = "Unknown album",
    val durationMs: Long = 0,
    val modified: Long = 0,
    val size: Long = 0,
    val albumArt: String? = null,
    val addedAt: Long = 0,
    val lastPlayed: Long = 0,
    val enriched: Boolean = false,
) {
    val search: String get() = LibraryText.normalize("$title $artist $album")
    val fingerprint: String get() = "$modified:$size"
}

data class LibrarySource(val uri: String, val label: String, val kind: String = "folder", val volume: String? = null, val path: String? = null)
data class LibraryGroup(val name: String, val artist: String?, val count: Int, val track: LibraryTrack)
enum class LibraryBrowse { SONGS, ARTISTS, ALBUMS, HISTORY, RECENT, FAVORITES }

object LibraryText {
    fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
    fun terms(value: String) = normalize(value).split(Regex("\\s+")).filter { it.isNotBlank() }.take(16)
    fun likeTerm(value: String) = "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    fun id(key: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val hex = "0123456789abcdef"
        return CharArray(bytes.size * 2).apply { bytes.forEachIndexed { index, value ->
            this[index * 2] = hex[(value.toInt() and 255) ushr 4]
            this[index * 2 + 1] = hex[value.toInt() and 15]
        } }.concatToString()
    }
    fun storageKey(volume: String, path: String): String {
        val canonical = if (volume.equals("primary", true) || volume.equals("external_primary", true)) "primary" else volume.lowercase(Locale.ROOT)
        return "storage:$canonical:${path.trimStart('/')}"
    }
    fun within(path: String, folder: String): Boolean {
        val root = folder.trim('/')
        return root.isEmpty() || path.trimStart('/').startsWith("$root/")
    }
    fun unchanged(a: LibraryTrack, b: LibraryTrack) = a.fingerprint == b.fingerprint && a.uri == b.uri
}
