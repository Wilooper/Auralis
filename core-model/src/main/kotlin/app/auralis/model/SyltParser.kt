package app.auralis.model

object SyltParser {
    fun parse(data: ByteArray, mpegFrameMs: Double? = null): Lyrics? {
        if (data.size < 7 || data.size > LyricsParser.MAX_TEXT_BYTES) return null
        val encoding = data[0].toInt() and 255
        if (encoding !in 0..3 || data[5].toInt() !in 0..2) return null
        val multiplier = when (data[4].toInt()) { 2 -> 1.0; 1 -> mpegFrameMs ?: return null; else -> return null }
        val width = if (encoding in 1..2) 2 else 1
        var littleEndian = encoding == 1 && data.getOrNull(6)?.toInt()?.and(255) == 255
        var position = 6
        fun readString(): String? {
            val start = position
            while (position + width <= data.size) {
                if (data[position] == 0.toByte() && (width == 1 || data[position + 1] == 0.toByte())) {
                    val bytes = data.copyOfRange(start, position)
                    position += width
                    if (bytes.size >= 2 && encoding == 1) {
                        if (bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) littleEndian = true
                        if (bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()) littleEndian = false
                    }
                    val charset = when (encoding) {
                        0 -> Charsets.ISO_8859_1; 3 -> Charsets.UTF_8
                        2 -> Charsets.UTF_16BE; else -> if (littleEndian) Charsets.UTF_16LE else Charsets.UTF_16BE
                    }
                    return bytes.toString(charset).removePrefix("\uFEFF")
                }
                position += width
            }
            return null
        }
        readString() ?: return null
        val lines = mutableListOf<LyricLine>()
        val words = mutableListOf<LyricWord>()
        fun flush() {
            if (words.isNotEmpty()) {
                val finalized = words.mapIndexed { index, word -> word.copy(endTimeMs = words.getOrNull(index + 1)?.timeMs) }
                lines += LyricLine(words.first().timeMs, words.joinToString("") { it.text }, if (words.size > 1) finalized else emptyList())
                words.clear()
            }
        }
        var previousTime = -1L
        while (position < data.size) {
            val text = readString() ?: return null
            if (position + 4 > data.size) return null
            var value = 0L
            repeat(4) { value = (value shl 8) or (data[position++].toLong() and 255) }
            val time = (value * multiplier).toLong()
            if (time < previousTime) return null
            previousTime = time
            text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEachIndexed { index, part ->
                if (index > 0) flush()
                if (part.isNotEmpty()) words += LyricWord(part, time)
            }
        }
        flush()
        return if (lines.isEmpty()) null else Lyrics(lines, source = "Embedded ID3 SYLT")
    }
}
