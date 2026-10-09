package app.auralis.model

/** ID3 USLT/ULT frame payload; framing and unsynchronization are handled by Media3. */
object UsltParser {
    fun parse(data: ByteArray): Lyrics? {
        if (data.size < 5 || data.size > LyricsParser.MAX_TEXT_BYTES) return null
        val encoding = data[0].toInt() and 255
        if (encoding !in 0..3) return null
        val width = if (encoding in 1..2) 2 else 1
        var position = 4
        while (position + width <= data.size) {
            if (data[position] == 0.toByte() && (width == 1 || data[position + 1] == 0.toByte())) break
            position += width
        }
        if (position + width > data.size) return null
        val text = data.copyOfRange(position + width, data.size)
        if (width == 2 && text.size % 2 != 0) return null
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> when {
                text.size >= 2 && ((text[0] == 0xff.toByte() && text[1] == 0xfe.toByte()) ||
                    (text[0] == 0xfe.toByte() && text[1] == 0xff.toByte())) -> Charsets.UTF_16
                data[4] == 0xff.toByte() && data.getOrNull(5) == 0xfe.toByte() -> Charsets.UTF_16LE
                else -> Charsets.UTF_16BE
            }
        }
        val language = data.copyOfRange(1, 4).toString(Charsets.ISO_8859_1)
        return LyricsParser.parse(text.toString(charset).removePrefix("\uFEFF").trimEnd('\u0000'), "Embedded ID3 $language")
    }
}
