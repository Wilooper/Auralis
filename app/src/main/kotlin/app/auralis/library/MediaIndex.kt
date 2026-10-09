package app.auralis.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import app.auralis.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object AudioPermission {
    val name: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun granted(context: Context) = ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
}

class MediaIndex(private val context: Context) {
    suspend fun read(sources: List<LibrarySource>): Map<String, List<LibraryTrack>> {
        if (!AudioPermission.granted(context)) return emptyMap()
        val folders = sources.filter { it.volume != null && it.path != null }
        val output = folders.associate { it.uri to mutableListOf<LibraryTrack>() }
        val volumes = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context) else setOf("external")
        for (volume in volumes) {
            currentCoroutineContext().ensureActive()
            val relevant = folders.filter { if (Build.VERSION.SDK_INT < 29) it.volume == "primary" else
                (if (it.volume == "primary") "external_primary" else it.volume!!.lowercase(java.util.Locale.ROOT)) == volume }
            if (relevant.isEmpty()) continue
            val uri = MediaStore.Audio.Media.getContentUri(volume)
            val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
            val selection = relevant.joinToString(" OR ", "(", ")") { "$pathColumn LIKE ? ESCAPE '\\'" } +
                if (Build.VERSION.SDK_INT >= 29) " AND ${MediaStore.MediaColumns.IS_PENDING}=0" else ""
            val legacyBase = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"
            val args = relevant.map {
                val prefix = (if (Build.VERSION.SDK_INT < 29) legacyBase else "") + it.path!!.trim('/').let { p -> if (p.isBlank()) "" else "$p/" }
                prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            }.toTypedArray()
            val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.MediaColumns.DISPLAY_NAME, pathColumn, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE)
            val cursor = context.contentResolver.query(uri, columns, selection, args, null)
                ?: throw java.io.IOException("Android's music index is unavailable")
            cursor.use { c ->
                fun value(column: String) = c.getString(c.getColumnIndexOrThrow(column)).orEmpty()
                fun long(column: String) = c.getLong(c.getColumnIndexOrThrow(column))
                while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val location = value(pathColumn)
                    val fileName = value(MediaStore.MediaColumns.DISPLAY_NAME)
                    val relative = if (Build.VERSION.SDK_INT >= 29) location.trimStart('/') + fileName else location.removePrefix(legacyBase)
                    val canonicalVolume = if (volume == "external" || volume == "external_primary") "primary" else volume
                    val track = LibraryTrack(
                        LibraryText.id(LibraryText.storageKey(canonicalVolume, relative)), ContentUris.withAppendedId(uri, long(MediaStore.Audio.Media._ID)).toString(),
                        value(MediaStore.Audio.Media.TITLE).ifBlank { fileName.substringBeforeLast('.', fileName) },
                        value(MediaStore.Audio.Media.ARTIST).takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown artist",
                        value(MediaStore.Audio.Media.ALBUM).takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown album",
                        long(MediaStore.Audio.Media.DURATION), long(MediaStore.MediaColumns.DATE_MODIFIED) * 1000,
                        long(MediaStore.MediaColumns.SIZE),
                        "content://media/$volume/audio/albumart/${long(MediaStore.Audio.Media.ALBUM_ID)}", enriched = true,
                    )
                    relevant.filter { LibraryText.within(relative, it.path!!) }.forEach { output.getValue(it.uri) += track }
                }
            }
        }
        // Missing/removable volumes are not reconciled as empty: keep their cached library.
        return output.filterKeys { key -> folders.first { it.uri == key }.let {
            if (Build.VERSION.SDK_INT < 29) it.volume == "primary" else (if (it.volume == "primary") "external_primary" else it.volume!!.lowercase(java.util.Locale.ROOT)) in volumes
        } }
    }
}
