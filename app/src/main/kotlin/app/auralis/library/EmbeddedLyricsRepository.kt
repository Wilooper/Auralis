package app.auralis.library

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.Id3Decoder
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import app.auralis.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

@UnstableApi
class EmbeddedLyricsRepository(private val context: Context) {
    suspend fun read(uri: Uri): EmbeddedLyricsResult = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    val channel = input.channel
                    val seekable = runCatching { channel.size().takeIf { it > 0 } }.getOrNull()
                    val source = if (seekable != null) ChannelTagSource(channel, seekable)
                    else {
                        // Pipe-backed providers cannot seek; only their bounded metadata prefix is inspected.
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() < 8 * 1024 * 1024) {
                            coroutine.ensureActive()
                            val count = input.read(buffer, 0, minOf(buffer.size, 8 * 1024 * 1024 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        val data = output.toByteArray()
                        object : TagSource {
                            override val size = data.size.toLong()
                            override fun read(offset: Long, count: Int) = data.copyOfRange(offset.toInt(), offset.toInt() + count)
                        }
                    }
                    EmbeddedLyricsReader(::decodeId3Lyrics).read(source) { coroutine.ensureActive() }
                }
            } ?: EmbeddedLyricsResult(null, listOf("Cannot open this file's embedded tags"))
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { EmbeddedLyricsResult(null, listOf(failure.message ?: "Cannot read embedded lyrics")) }
    }

    private class ChannelTagSource(private val channel: FileChannel, override val size: Long) : TagSource {
        override fun read(offset: Long, count: Int): ByteArray {
            val buffer = ByteBuffer.allocate(count)
            var position = offset
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, position)
                if (read <= 0) throw java.io.IOException("Incomplete tag data")
                position += read
            }
            return buffer.array()
        }
    }
}

@UnstableApi
internal fun decodeId3Lyrics(data: ByteArray, frameMs: Double?): List<Lyrics> {
    if (data.size < 10) return emptyList()
    val metadata = Id3Decoder { _, a, b, c, d ->
        val id = charArrayOf(a.toChar(), b.toChar(), c.toChar(), d.toChar()).concatToString().trimEnd('\u0000')
        id in setOf("USLT", "SYLT", "TXXX", "ULT", "SLT", "TXX")
    }.decode(data, data.size) ?: return emptyList()
    val results = mutableListOf<Lyrics>()
    for (index in 0 until metadata.length()) {
        when (val entry = metadata[index]) {
            is TextInformationFrame -> if (EmbeddedLyricsReader.isLyricsKey(entry.description.orEmpty()))
                entry.values.forEach { LyricsParser.parse(it, "Embedded ID3 ${entry.description}")?.let(results::add) }
            is BinaryFrame -> when (entry.id) {
                "SYLT", "SLT" -> SyltParser.parse(entry.data, frameMs)?.let(results::add)
                "USLT", "ULT" -> UsltParser.parse(entry.data)?.let(results::add)
                else -> Unit
            }
        }
    }
    return results
}
