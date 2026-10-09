package app.auralis.model

import org.junit.Assert.*
import org.junit.Test

class EmbeddedTagsTest {
    private val content = "[00:01]<00:01>Love <00:02>song"
    private fun source(data: ByteArray) = object : TagSource {
        override val size = data.size.toLong()
        override fun read(offset: Long, count: Int) = data.copyOfRange(offset.toInt(), offset.toInt() + count)
    }
    private fun read(data: ByteArray) = EmbeddedLyricsReader { _, _ -> emptyList() }.read(source(data))
    private fun ascii(value: String) = value.toByteArray(Charsets.ISO_8859_1)
    private fun number(value: Int, little: Boolean = false) = ByteArray(4) { (value ushr ((if (little) it else 3 - it) * 8)).toByte() }
    private fun comments(key: String = "LYRICS", text: String = content): ByteArray {
        val comment = "$key=$text".toByteArray()
        return number(0, true) + number(1, true) + number(comment.size, true) + comment
    }
    private fun atom(name: String, data: ByteArray) = number(data.size + 8) + ascii(name) + data
    private fun ogg(packet: ByteArray): ByteArray {
        val header = ByteArray(27)
        ascii("OggS").copyInto(header)
        header[26] = 1
        return header + byteArrayOf(packet.size.toByte()) + packet
    }
    @Test fun flacVorbisCommentsExposeWordLyrics() {
        val comments = comments()
        val block = byteArrayOf(0x84.toByte()) + number(comments.size).copyOfRange(1, 4)
        val lyrics = read(ascii("fLaC") + block + comments).lyrics!!
        assertEquals(LyricsTiming.WORD, lyrics.timing)
        assertEquals("Love song", lyrics.lines.single().text)
    }
    @Test fun oggVorbisAndOpusComments() {
        val vorbis = read(ogg(byteArrayOf(3) + ascii("vorbis") + comments())).lyrics!!
        val opus = read(ogg(ascii("OpusTags") + comments())).lyrics!!
        assertEquals(vorbis.lines, opus.lines)
    }
    @Test fun continuedOggPacketsAreJoined() {
        val packet = ascii("OpusTags") + comments(text = "a".repeat(260))
        val first = ByteArray(27).apply { ascii("OggS").copyInto(this); this[26] = 1 }
        val second = first.copyOf().apply { this[5] = 1 }
        val result = read(first + byteArrayOf(255.toByte()) + packet.copyOfRange(0, 255) +
            second + byteArrayOf((packet.size - 255).toByte()) + packet.copyOfRange(255, packet.size))
        assertEquals("a".repeat(260), result.lyrics!!.lines.single().text)
    }
    @Test fun mp4StandardAndFreeformLyricsAtoms() {
        val data = atom("data", number(1) + number(0) + content.toByteArray())
        fun file(lyrics: ByteArray) = atom("ftyp", ascii("M4A ") + number(0)) +
            atom("mdat", ByteArray(200)) + atom("moov", atom("udta", atom("meta", number(0) + atom("ilst", lyrics))))
        assertEquals("Love song", read(file(atom("©lyr", data))).lyrics!!.lines.single().text)
        val freeform = atom("----", atom("name", number(0) + ascii("LYRICS")) + data)
        assertEquals(LyricsTiming.WORD, read(file(freeform)).lyrics!!.timing)
    }
    @Test fun apeTailAfterAudioAndBeforeId3v1() {
        val text = "Plain embedded lyrics".toByteArray()
        val item = number(text.size, true) + number(0) + ascii("Lyrics") + byteArrayOf(0) + text
        val footer = ascii("APETAGEX") + number(2000, true) + number(item.size + 32, true) + number(1, true) + ByteArray(12)
        val data = ByteArray(100) + item + footer
        assertEquals(LyricsTiming.PLAIN, read(data).lyrics!!.timing)
        assertEquals("Plain embedded lyrics", read(data + ascii("TAG") + ByteArray(125)).lyrics!!.lines.single().text)
    }
    @Test fun id3InMp3WavAiffAndDsfReachesDecoder() {
        val tag = ascii("ID3") + byteArrayOf(3, 0, 0, 0, 0, 0, 0)
        var decoded = 0
        val reader = EmbeddedLyricsReader { bytes, _ ->
            assertArrayEquals(tag, bytes)
            decoded++
            listOf(LyricsParser.parse("Hello")!!)
        }
        fun check(data: ByteArray) { assertNotNull(reader.read(source(data)).lyrics) }
        check(tag)
        check(ascii("RIFF") + number(4 + 8 + tag.size, true) + ascii("WAVEid3 ") + number(tag.size, true) + tag)
        check(ascii("FORM") + number(4 + 8 + tag.size) + ascii("AIFFID3 ") + number(tag.size) + tag)
        val dsf = ByteArray(28).apply { ascii("DSD ").copyInto(this); this[20] = 28 }
        check(dsf + tag)
        assertEquals(4, decoded)
    }
    @Test fun malformedSizesReturnWarningsWithoutOutOfBoundsReads() {
        val corrupt = ascii("fLaC") + byteArrayOf(0x84.toByte(), 0x7f, 0xff.toByte(), 0xff.toByte())
        val result = read(corrupt)
        assertNull(result.lyrics)
        assertTrue(result.warnings.isNotEmpty())
        val mp4 = atom("ftyp", ascii("M4A ") + number(0)) + number(Int.MAX_VALUE) + ascii("moov")
        assertTrue(read(mp4).warnings.isNotEmpty())
    }
    @Test fun hugeAudioPayloadIsSkippedRatherThanCopied() {
        var readBytes = 0
        val lyricsAtom = atom("moov", atom("udta", atom("meta", number(0) + atom("ilst", atom("©lyr",
            atom("data", number(1) + number(0) + "Hello".toByteArray()))))))
        val prefix = atom("ftyp", ascii("M4A ") + number(0)) + number(1_000_000_008) + ascii("mdat")
        val tailOffset = prefix.size.toLong() + 1_000_000_000
        val sparse = object : TagSource {
            override val size = tailOffset + lyricsAtom.size
            override fun read(offset: Long, count: Int): ByteArray {
                readBytes += count
                return when {
                    offset + count <= prefix.size -> prefix.copyOfRange(offset.toInt(), offset.toInt() + count)
                    offset >= tailOffset -> lyricsAtom.copyOfRange((offset - tailOffset).toInt(), (offset - tailOffset).toInt() + count)
                    else -> ByteArray(count)
                }
            }
        }
        val result = EmbeddedLyricsReader { _, _ -> emptyList() }.read(sparse)
        assertEquals("Hello", result.lyrics!!.lines.single().text)
        assertTrue(readBytes < 500)
    }
}
