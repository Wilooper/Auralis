package app.auralis.model

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class LyricsTest {
    @Test fun plainUnicodeHasNoInventedTimestamps() {
        val lyrics = LyricsParser.parse("पहला गीत\r\n\r\nSecond line")!!
        assertEquals(LyricsTiming.PLAIN, lyrics.timing)
        assertEquals(listOf("पहला गीत", "", "Second line"), lyrics.lines.map { it.text })
        assertEquals(-1, lyrics.activeIndex(50_000))
    }
    @Test fun enhancedLrcKeepsWordsAndEndMarkers() {
        val lyrics = LyricsParser.parse("[offset:100]\n[00:01]<00:01>Hello <00:01.500>world<00:02>")!!
        assertEquals(LyricsTiming.WORD, lyrics.timing)
        assertEquals("Hello world", lyrics.lines.single().text)
        assertEquals(listOf(1000L, 1500L), lyrics.lines.single().words.map { it.timeMs })
        assertEquals(2000L, lyrics.lines.single().words.last().endTimeMs)
        assertEquals(0, lyrics.activeIndex(900))
    }
    @Test fun repeatedLineTagsShiftTheirWordClocks() {
        val lyrics = LyricsParser.parse("[00:01][00:05]<00:01>One <00:02>two")!!
        assertEquals(listOf(5000L, 6000L), lyrics.lines[1].words.map { it.timeMs })
    }
    @Test fun outOfOrderWordsFallBackToLineSync() {
        val lyrics = LyricsParser.parse("[00:01]<00:03>A<00:02>B")!!
        assertEquals(LyricsTiming.LINE, lyrics.timing)
        assertEquals("AB", lyrics.lines.single().text)
    }
    @Test fun ttmlWordSpansRespectEndTimes() {
        val lyrics = LyricsParser.parse("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:00:01.000" end="00:00:03.000"><span begin="00:00:01.000" end="00:00:01.500">Hello </span><span begin="00:00:01.500" end="00:00:02.500">world</span></p></div></body></tt>""")!!
        assertEquals(LyricsTiming.WORD, lyrics.timing)
        assertEquals("Hello world", lyrics.lines.single().text)
        assertEquals(1500L, lyrics.lines.single().words[1].timeMs)
        assertEquals(-1, lyrics.activeIndex(3000))
    }
    @Test fun ttmlRelativeOffsetsAndPlainParagraphs() {
        val timed = LyricsParser.parse("""<tt><body begin="2s"><div><p begin="1s" dur="2s">Test</p></div></body></tt>""")!!
        assertEquals(3000L, timed.lines.single().timeMs)
        assertEquals(5000L, timed.lines.single().endTimeMs)
        val plain = LyricsParser.parse("<tt><body><p>Hello</p><p>Again</p></body></tt>")!!
        assertEquals(LyricsTiming.PLAIN, plain.timing)
    }
    @Test fun xmlEntitiesAndOversizedTextAreRejected() {
        assertNull(LyricsParser.parse("<!DOCTYPE tt [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><tt><p>&x;</p></tt>"))
        assertNull(LyricsParser.parse("x".repeat(LyricsParser.MAX_TEXT_BYTES + 1)))
        assertNull(LyricsParser.parse("<unrecognized>lyrics</unrecognized>"))
    }
    @Test fun syltUtf8WordsAndNewlines() {
        val lyrics = SyltParser.parse(sylt(3, 2, "" to 0, "Hello " to 1000, "world" to 1500, "\nAgain" to 3000))!!
        assertEquals(LyricsTiming.WORD, lyrics.timing)
        assertEquals(listOf("Hello world", "Again"), lyrics.lines.map { it.text })
        assertEquals(1500L, lyrics.lines.first().words.first().endTimeMs)
        assertEquals(1, lyrics.activeIndex(3000))
    }
    @Test fun syltUtf16AndFrameTimeUnits() {
        val unicode = SyltParser.parse(sylt(1, 2, "" to 0, "प्यार" to 1000))!!
        assertEquals("प्यार", unicode.lines.single().text)
        val frames = sylt(3, 1, "" to 0, "One" to 50)
        assertNull(SyltParser.parse(frames))
        assertEquals(1300L, SyltParser.parse(frames, 26.0)!!.lines.single().timeMs)
    }
    @Test fun syltRejectsTruncationAndBackwardsClock() {
        assertNull(SyltParser.parse(sylt(3, 2, "" to 0, "One" to 1000).dropLast(1).toByteArray()))
        assertNull(SyltParser.parse(sylt(3, 2, "" to 0, "One" to 1000, "Two" to 500)))
    }
    @Test fun usltSupportsUtf16DescriptorAndInheritedByteOrder() {
        val descriptor = "description".toByteArray(Charsets.UTF_16LE)
        val text = "मेरा गीत".toByteArray(Charsets.UTF_16LE)
        val data = byteArrayOf(1, 'h'.code.toByte(), 'i'.code.toByte(), 'n'.code.toByte(), 0xff.toByte(), 0xfe.toByte()) +
            descriptor + byteArrayOf(0, 0) + text
        assertEquals("मेरा गीत", UsltParser.parse(data)!!.lines.single().text)
        assertNull(UsltParser.parse(data.dropLast(1).toByteArray()))
    }
    @Test fun audioFolderFilteringAcceptsCodecExtensionsAndProviderMime() {
        assertTrue(AudioFiles.isAudio("Song.FLAC", "application/octet-stream"))
        assertTrue(AudioFiles.isAudio("Nameless", "audio/opus"))
        assertTrue(AudioFiles.isAudio("Song.dsf", null))
        assertFalse(AudioFiles.isAudio("cover.jpg", "image/jpeg"))
        assertFalse(AudioFiles.isAudio("lyrics.lrc", "text/plain"))
    }

    private fun sylt(encoding: Int, format: Int, vararg entries: Pair<String, Int>): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(encoding.toByte(), 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), format.toByte(), 1))
        val charset = if (encoding == 1) Charsets.UTF_16 else Charsets.UTF_8
        output.write(entries.first().first.toByteArray(charset))
        output.write(if (encoding == 1) byteArrayOf(0, 0) else byteArrayOf(0))
        entries.drop(1).forEach { (text, time) ->
            output.write(text.toByteArray(charset))
            output.write(if (encoding == 1) byteArrayOf(0, 0) else byteArrayOf(0))
            output.write(be(time))
        }
        return output.toByteArray()
    }
    private fun be(value: Int) = ByteArray(4) { (value ushr ((3 - it) * 8)).toByte() }
}
