package app.auralis.library

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.auralis.ReVaApplication
import app.auralis.model.LibraryText
import app.auralis.model.LibraryTrack
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Semaphore

/** Coil and Media3 open this private URI on their image-loading threads, never during library scans. */
class ArtworkProvider : ContentProvider() {
    private val readers = Semaphore(2)
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/*"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only artwork")
        val context = requireNotNull(context)
        val id = uri.lastPathSegment?.takeIf { Regex("[a-f0-9]{64}").matches(it) } ?: throw FileNotFoundException()
        val track = (context.applicationContext as ReVaApplication).library.database.track(id) ?: throw FileNotFoundException()
        val directory = File(context.cacheDir, "covers-v4-original").apply { mkdirs() }
        val key = LibraryText.id("${track.id}:${track.fingerprint}")
        val output = File(directory, "$key.art")
        val negative = File(directory, "$key.none")
        if (!output.exists()) {
            if (negative.exists() && System.currentTimeMillis() - negative.lastModified() < 3_600_000) throw FileNotFoundException("No embedded artwork")
            readers.acquire()
            try {
                if (!output.exists()) {
                    val data = artwork(track)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    if (data != null) BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                    if (data == null || bounds.outWidth !in 1..32768 || bounds.outHeight !in 1..32768 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000) {
                        negative.writeText(""); throw FileNotFoundException("No supported embedded artwork")
                    }
                    val temporary = File.createTempFile("cover-", ".art", directory)
                    try {
                        temporary.outputStream().use { it.write(data) }
                        if (!temporary.renameTo(output)) throw FileNotFoundException("Cannot cache artwork")
                    } finally { temporary.delete() }
                    synchronized(this) {
                        run {
                            val files = directory.listFiles().orEmpty().filter { it.extension == "art" }.sortedBy { it.lastModified() }
                            var bytes = files.sumOf { it.length() }
                            files.forEach { if (bytes > 48 * 1024 * 1024 && it != output) { val size = it.length(); if (it.delete()) bytes -= size } }
                            directory.listFiles().orEmpty().filter { it.extension == "none" && System.currentTimeMillis() - it.lastModified() > 3_600_000 }.forEach { it.delete() }
                        }
                    }
                }
            } finally { readers.release() }
        }
        output.setLastModified(System.currentTimeMillis())
        return ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    private fun artwork(track: LibraryTrack): ByteArray? {
        val context = requireNotNull(context)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(track.uri))
            retriever.embeddedPicture?.takeIf { it.size <= 8 * 1024 * 1024 }?.let { return it }
        } catch (_: Exception) { /* Try the system album image next. */ }
        finally { retriever.release() }
        return track.albumArt?.let { art -> runCatching {
            context.contentResolver.openInputStream(Uri.parse(art))?.use { stream ->
                val data = stream.readBytesLimited(8 * 1024 * 1024)
                data.takeIf { it.isNotEmpty() }
            }
        }.getOrNull() }
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw java.io.IOException("Artwork exceeds read limit")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
