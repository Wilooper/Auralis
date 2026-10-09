package app.auralis.library

import app.auralis.model.LyricsTiming
import app.auralis.model.TagSource
import app.auralis.model.EmbeddedLyricsReader
import java.io.File
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class Id3LyricsTest {
    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8)
    private fun number(size: Int, syncSafe: Boolean = false) = ByteArray(4) {
        (size ushr ((3 - it) * if (syncSafe) 7 else 8) and if (syncSafe) 127 else 255).toByte()
    }
    private fun tag(id: String, payload: ByteArray, version: Int = 3): ByteArray {
        val frame = bytes(id) + number(payload.size, version == 4) + byteArrayOf(0, 0) + payload
        return bytes("ID3") + byteArrayOf(version.toByte(), 0, 0) + number(frame.size, true) + frame
    }
    @Test fun actualId3DecoderExtractsPlainAndEnhancedUslt() {
        val plain = decodeId3Lyrics(tag("USLT", byteArrayOf(3) + bytes("hin") + byteArrayOf(0) + bytes("प्यार का गीत")), null).single()
        assertEquals(LyricsTiming.PLAIN, plain.timing)
        assertEquals("प्यार का गीत", plain.lines.single().text)
        val word = decodeId3Lyrics(tag("USLT", byteArrayOf(3) + bytes("eng") + byteArrayOf(0) + bytes("[00:01]<00:01>Hello <00:02>world"), 4), null).single()
        assertEquals(LyricsTiming.WORD, word.timing)
    }
    @Test fun actualId3DecoderPassesSyltBinaryFrameToWordParser() {
        val payload = byteArrayOf(3) + bytes("eng") + byteArrayOf(2, 1, 0) +
            bytes("Hello ") + byteArrayOf(0) + number(1000) + bytes("world") + byteArrayOf(0) + number(2000)
        val lyrics = decodeId3Lyrics(tag("SYLT", payload), null).single()
        assertEquals("Hello world", lyrics.lines.single().text)
        assertEquals(LyricsTiming.WORD, lyrics.timing)
        assertEquals(2000L, lyrics.lines.single().words.last().timeMs)
    }
    @Test fun actualId3DecoderHandlesTxxxLyricsAndIgnoresOtherTags() {
        val lyrics = decodeId3Lyrics(tag("TXXX", byteArrayOf(3) + bytes("LYRICS") + byteArrayOf(0) + bytes("[00:01]One")), null).single()
        assertEquals(LyricsTiming.LINE, lyrics.timing)
        assertTrue(decodeId3Lyrics(tag("TXXX", byteArrayOf(3) + bytes("COMMENT") + byteArrayOf(0) + bytes("Something")), null).isEmpty())
        assertTrue(decodeId3Lyrics(tag("TIT2", byteArrayOf(3) + bytes("Song title")), null).isEmpty())
    }
    @Test fun malformedId3ReturnsNoLyrics() {
        assertTrue(decodeId3Lyrics(bytes("ID3"), null).isEmpty())
    }
    @Test fun ffmpegStyleTxxxUsltPreservesEmbeddedLineTiming() {
        val payload = byteArrayOf(0) + bytes("USLT") + byteArrayOf(0) + bytes("[00:00.000]First line\n[00:07.000]Second line")
        val lyrics = decodeId3Lyrics(tag("TXXX", payload), null).single()
        assertEquals(LyricsTiming.LINE, lyrics.timing)
        assertEquals("Second line", lyrics.lines[1].text)
        assertEquals(7000L, lyrics.lines[1].timeMs)
    }
    @Test fun playableWavFixturesDecodeThroughContainerAndActualId3Reader() {
        val folder = File(checkNotNull(System.getProperty("auralis.fixtures")))
        val expected = listOf(LyricsTiming.PLAIN, LyricsTiming.LINE, LyricsTiming.WORD, LyricsTiming.WORD)
        val files = folder.listFiles()!!.filter { it.extension == "wav" }.sortedBy { it.name }
        assertEquals(4, files.size)
        files.forEachIndexed { index, file ->
            val data = file.readBytes()
            val source = object : TagSource {
                override val size = data.size.toLong()
                override fun read(offset: Long, count: Int) = data.copyOfRange(offset.toInt(), offset.toInt() + count)
            }
            val result = EmbeddedLyricsReader(::decodeId3Lyrics).read(source)
            assertTrue(result.warnings.toString(), result.warnings.isEmpty())
            assertEquals(expected[index], result.lyrics!!.timing)
            assertEquals("प्यार का गीत", result.lyrics!!.lines.first().text)
        }
    }
}
