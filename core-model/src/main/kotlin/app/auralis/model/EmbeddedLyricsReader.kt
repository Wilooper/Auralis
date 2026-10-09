package app.auralis.model

import java.io.ByteArrayOutputStream
import java.io.IOException

interface TagSource {
    val size: Long
    fun read(offset: Long, count: Int): ByteArray
}

data class EmbeddedLyricsResult(val lyrics: Lyrics?, val warnings: List<String> = emptyList())

class EmbeddedLyricsReader(private val decodeId3: (ByteArray, Double?) -> List<Lyrics>) {
    private data class Atom(val type: String, val body: Long, val end: Long)
    fun read(source: TagSource, checkCancelled: () -> Unit = {}): EmbeddedLyricsResult = Scanner(source, checkCancelled).read()

    private inner class Scanner(private val source: TagSource, private val checkCancelled: () -> Unit) {
        private var budget = 16 * 1024 * 1024
        private var objects = 0
        private val candidates = mutableListOf<Lyrics>()
        private val warnings = mutableListOf<String>()

        fun read(): EmbeddedLyricsResult {
            try {
                val head = bytes(0, minOf(source.size, 12).toInt())
                when {
                    ascii(head, 0, 3) == "ID3" -> id3(0)
                    ascii(head, 0, 4) == "fLaC" -> flac(4)
                    ascii(head, 0, 4) == "OggS" -> ogg()
                    ascii(head, 4, 4) == "ftyp" -> mp4(0, source.size, 0)
                    ascii(head, 0, 4) == "RIFF" -> riff(little = true)
                    ascii(head, 0, 4) == "FORM" -> riff(little = false)
                    ascii(head, 0, 4) == "DSD " -> {
                        val header = bytes(0, 28)
                        val offset = number(header, 20, 8, true)
                        if (offset in 28 until source.size) id3(offset)
                    }
                }
                ape()
            } catch (failure: IOException) {
                warnings += failure.message ?: "Cannot read embedded tags"
            } catch (failure: IllegalArgumentException) {
                warnings += failure.message ?: "Malformed embedded tags"
            }
            return EmbeddedLyricsResult(candidates.maxWithOrNull(compareBy<Lyrics> { it.timing.ordinal }.thenBy { it.lines.size }), warnings.distinct())
        }

        private fun bytes(offset: Long, count: Int): ByteArray {
            checkCancelled()
            if (++objects > 20_000 || count < 0 || offset < 0 || offset > source.size - count || count > budget)
                throw IOException("Embedded metadata is truncated or exceeds the read limit")
            budget -= count
            return source.read(offset, count).also { if (it.size != count) throw IOException("Truncated embedded metadata") }
        }

        private fun text(value: String, origin: String) {
            LyricsParser.parse(value.trimEnd('\u0000'), origin)?.let { candidates += it }
        }

        private fun id3(offset: Long) {
            val header = bytes(offset, 10)
            if (ascii(header, 0, 3) != "ID3") return
            if ((6..9).any { header[it].toInt() and 128 != 0 }) return
            var length = 0
            for (index in 6..9) length = (length shl 7) or (header[index].toInt() and 127)
            if (length > 8 * 1024 * 1024) { warnings += "ID3 tag exceeds 8 MB"; return }
            val data = header + bytes(offset + 10, length)
            val audioStart = offset + data.size
            val audio = bytes(audioStart, minOf(4096, (source.size - audioStart).coerceAtLeast(0)).toInt())
            candidates += decodeId3(data, mpegFrameDuration(audio))
        }

        private fun flac(start: Long) {
            var position = start
            while (position + 4 <= source.size) {
                val header = bytes(position, 4)
                val type = header[0].toInt() and 127
                val length = number(header, 1, 3, false).toInt()
                position += 4
                if (type == 4) comments(bytes(position, length), "Embedded FLAC")
                if (position > source.size - length) throw IOException("Truncated FLAC metadata")
                position += length
                if (header[0].toInt() and 128 != 0) break
            }
        }

        private fun comments(data: ByteArray, origin: String) {
            var position = 0
            fun length(): Int {
                require(position + 4 <= data.size) { "Truncated Vorbis comment" }
                val result = number(data, position, 4, true)
                position += 4
                require(result <= data.size - position) { "Invalid Vorbis comment length" }
                return result.toInt()
            }
            val vendorSize = length()
            position += vendorSize
            require(position + 4 <= data.size)
            val count = number(data, position, 4, true)
            position += 4
            require(count <= 10_000)
            repeat(count.toInt()) {
                val size = length()
                if (size <= LyricsParser.MAX_TEXT_BYTES) {
                    val comment = data.copyOfRange(position, position + size).toString(Charsets.UTF_8)
                    val separator = comment.indexOf('=')
                    if (separator >= 0 && isLyricsKey(comment.substring(0, separator)))
                        text(comment.substring(separator + 1), "$origin ${comment.substring(0, separator)}")
                }
                position += size
            }
        }

        private fun ogg() {
            data class Packet(val buffer: ByteArrayOutputStream = ByteArrayOutputStream(), var oversized: Boolean = false)
            val streams = mutableMapOf<Long, Packet>()
            var position = 0L
            repeat(512) {
                if (position + 27 > source.size) return
                val header = bytes(position, 27)
                if (ascii(header, 0, 4) != "OggS" || header[4] != 0.toByte()) return
                val segments = bytes(position + 27, header[26].toInt() and 255)
                val serial = number(header, 14, 4, true)
                if (serial !in streams && streams.size >= 16) return
                val packet = streams.getOrPut(serial) { Packet() }
                if (header[5].toInt() and 1 == 0) { packet.buffer.reset(); packet.oversized = false }
                var cursor = position + 27 + segments.size
                segments.forEach { raw ->
                    val length = raw.toInt() and 255
                    if (packet.buffer.size() + length > LyricsParser.MAX_TEXT_BYTES) packet.oversized = true
                    if (!packet.oversized) packet.buffer.write(bytes(cursor, length))
                    cursor += length
                    if (length < 255) {
                        if (!packet.oversized) {
                            val data = packet.buffer.toByteArray()
                            if (ascii(data, 0, 8) == "OpusTags") {
                                comments(data.copyOfRange(8, data.size), "Embedded Opus")
                                return
                            }
                            if (data.firstOrNull() == 3.toByte() && ascii(data, 1, 6) == "vorbis") {
                                comments(data.copyOfRange(7, data.size), "Embedded Vorbis")
                                return
                            }
                        }
                        packet.buffer.reset(); packet.oversized = false
                    }
                }
                position = cursor
            }
        }

        private fun atoms(start: Long, end: Long, action: (Atom) -> Unit) {
            var position = start
            while (position + 8 <= end) {
                val header = bytes(position, 8)
                var size = number(header, 0, 4, false)
                var headerSize = 8
                if (size == 1L) { size = number(bytes(position + 8, 8), 0, 8, false); headerSize = 16 }
                if (size == 0L) size = end - position
                require(size >= headerSize && size <= end - position) { "Invalid MP4 atom size" }
                action(Atom(ascii(header, 4, 4), position + headerSize, position + size))
                position += size
            }
        }

        private fun mp4(start: Long, end: Long, depth: Int) {
            require(depth < 12) { "MP4 metadata nesting limit exceeded" }
            atoms(start, end) { atom ->
                when (atom.type) {
                    "moov", "udta", "ilst" -> mp4(atom.body, atom.end, depth + 1)
                    "meta" -> mp4(atom.body + 4, atom.end, depth + 1)
                    "\u00a9lyr", "----" -> {
                        var name = ""
                        val values = mutableListOf<String>()
                        atoms(atom.body, atom.end) { entry ->
                            val length = entry.end - entry.body
                            if (entry.type == "name" && length in 4..1024) name = bytes(entry.body + 4, (length - 4).toInt()).toString(Charsets.UTF_8)
                            if (entry.type == "data" && length in 8..(LyricsParser.MAX_TEXT_BYTES + 8).toLong()) {
                                val data = bytes(entry.body, length.toInt())
                                when (number(data, 0, 4, false) and 0xffffff) {
                                    1L -> values += data.copyOfRange(8, data.size).toString(Charsets.UTF_8)
                                    2L -> values += data.copyOfRange(8, data.size).toString(Charsets.UTF_16BE)
                                }
                            }
                        }
                        if (atom.type == "\u00a9lyr" || isLyricsKey(name)) values.forEach { text(it, "Embedded MP4 lyrics") }
                    }
                }
            }
        }

        private fun riff(little: Boolean) {
            var position = 12L
            while (position + 8 <= source.size) {
                val header = bytes(position, 8)
                val length = number(header, 4, 4, little)
                val id = ascii(header, 0, 4)
                if (id.equals("id3 ", ignoreCase = true)) id3(position + 8)
                require(length <= source.size - position - 8)
                position += 8 + length + length % 2
            }
        }

        private fun ape() {
            for (id3v1Size in listOf(0, 128)) {
                val footerOffset = source.size - id3v1Size - 32
                if (footerOffset < 0) continue
                val footer = bytes(footerOffset, 32)
                if (ascii(footer, 0, 8) != "APETAGEX") continue
                val size = number(footer, 12, 4, true)
                val count = number(footer, 16, 4, true)
                require(size in 32..(8 * 1024 * 1024).toLong() && count <= 10_000)
                val data = bytes(footerOffset + 32 - size, (size - 32).toInt())
                var position = 0
                repeat(count.toInt()) {
                    require(position + 8 <= data.size)
                    val length = number(data, position, 4, true)
                    val flags = number(data, position + 4, 4, true)
                    position += 8
                    val start = position
                    while (position < data.size && data[position] != 0.toByte()) position++
                    require(position < data.size)
                    val key = ascii(data, start, position - start)
                    position++
                    require(length <= data.size - position)
                    if (flags shr 1 and 3 == 0L && isLyricsKey(key) && length <= LyricsParser.MAX_TEXT_BYTES)
                        text(data.copyOfRange(position, position + length.toInt()).toString(Charsets.UTF_8), "Embedded APEv2 $key")
                    position += length.toInt()
                }
                return
            }
        }
    }

    companion object {
        fun isLyricsKey(key: String): Boolean = key.uppercase(java.util.Locale.ROOT).replace(Regex("[ _-]"), "") in
            setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "SYNCHRONIZEDLYRICS", "UNSYNCHRONIZEDLYRICS", "USLT", "ULT")
        private fun ascii(data: ByteArray, offset: Int, length: Int): String =
            if (offset < 0 || offset + length > data.size) "" else String(data, offset, length, Charsets.ISO_8859_1)
        private fun number(data: ByteArray, offset: Int, length: Int, little: Boolean): Long {
            require(offset >= 0 && offset + length <= data.size)
            var value = 0L
            for (i in 0 until length) value = (value shl 8) or (data[offset + if (little) length - i - 1 else i].toLong() and 255)
            return value
        }
        private fun mpegFrameDuration(data: ByteArray): Double? {
            for (i in 0 until (data.size - 3).coerceAtLeast(0)) {
                val header = number(data, i, 4, false)
                if (header and 0xffe00000 != 0xffe00000) continue
                val version = (header shr 19 and 3).toInt()
                val layer = (header shr 17 and 3).toInt()
                val rate = (header shr 10 and 3).toInt()
                val bitrate = (header shr 12 and 15).toInt()
                if (version == 1 || layer == 0 || rate == 3 || bitrate !in 1..14) continue
                val samples = if (layer == 3) 384 else if (layer == 1 && version != 3) 576 else 1152
                val sampleRate = intArrayOf(44100, 48000, 32000)[rate] / when (version) { 3 -> 1; 2 -> 2; else -> 4 }
                return samples * 1000.0 / sampleRate
            }
            return null
        }
    }
}
