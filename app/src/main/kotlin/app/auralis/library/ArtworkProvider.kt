package app.auralis.library

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.auralis.AuralisApplication
import app.auralis.model.LibraryText
import app.auralis.model.LibraryTrack
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Semaphore

/** Coil and Media3 open this private URI on their image-loading threads, never during library scans. */
class ArtworkProvider : ContentProvider() {
    private val readers = Semaphore(2)
    private var writes = 0
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only artwork")
        val context = requireNotNull(context)
        val id = uri.lastPathSegment?.takeIf { Regex("[a-f0-9]{64}").matches(it) } ?: throw FileNotFoundException()
        val track = (context.applicationContext as AuralisApplication).library.database.track(id) ?: throw FileNotFoundException()
        val directory = File(context.cacheDir, "covers-v3").apply { mkdirs() }
        val key = LibraryText.id("${track.id}:${track.fingerprint}")
        val output = File(directory, "$key.jpg")
        val negative = File(directory, "$key.none")
        if (!output.exists()) {
            if (negative.exists() && System.currentTimeMillis() - negative.lastModified() < 3_600_000) throw FileNotFoundException("No embedded artwork")
            readers.acquire()
            try {
                if (!output.exists()) {
                    val data = artwork(track)
                    val bitmap = data?.let(::thumbnail)
                    if (bitmap == null) { negative.writeText(""); throw FileNotFoundException("No embedded artwork") }
                    val temporary = File.createTempFile("cover-", ".jpg", directory)
                    try {
                        temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                        if (!temporary.renameTo(output)) throw FileNotFoundException("Cannot cache artwork")
                    } finally { bitmap.recycle(); temporary.delete() }
                    synchronized(this) {
                        if (++writes % 16 == 0) {
                            val files = directory.listFiles().orEmpty().filter { it.extension == "jpg" }.sortedBy { it.lastModified() }
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
    private fun thumbnail(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1536) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = minOf(1f, 768f / maxOf(bitmap.width, bitmap.height))
        if (scale == 1f) return bitmap
        val resized = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        if (resized !== bitmap) bitmap.recycle()
        return resized
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
