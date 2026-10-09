package app.auralis.model

import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    @Test fun fadeEndpointsAndClamping() {
        assertEquals(1.0, FadeEnvelope.at(-1.0).outgoing, 0.00001)
        assertEquals(0.0, FadeEnvelope.at(0.0).incoming, 0.00001)
        assertEquals(1.0, FadeEnvelope.at(2.0).incoming, 0.00001)
    }
    @Test fun fadeHasHeadroomAndIsMonotonic() {
        var last = 0.0
        for (step in 0..100) {
            val gains = FadeEnvelope.at(step / 100.0)
            assertTrue(gains.incoming >= last)
            assertTrue(gains.outgoing >= 0)
            assertEquals(1.0, gains.incoming + gains.outgoing, 0.00001)
            last = gains.incoming
        }
        assertEquals(0.5, FadeEnvelope.at(0.5).incoming, 0.00001)
    }
    @Test fun lrcSupportsMultipleTagsAndFractionWidths() {
        val lyrics = LrcParser.parse("[01:02.3][02:03.45]Hello\n[00:01.234]First")
        assertEquals(listOf(1234L, 62300L, 123450L), lyrics.lines.map { it.timeMs })
        assertEquals("Hello", lyrics.lines.last().text)
    }
    @Test fun lrcRejectsInvalidSecondsAndIgnoresMetadata() {
        assertTrue(LrcParser.parse("[ar:Artist]\n[00:61.1]Bad\nplain").lines.isEmpty())
    }
    @Test fun lrcOffsetAndSeekUseTheSameClock() {
        val lyrics = LrcParser.parse("[offset:500]\n[00:01]One\n[00:02]Two")
        assertEquals(-1, lyrics.activeIndex(0))
        assertEquals(0, lyrics.activeIndex(500))
        assertEquals(1, lyrics.activeIndex(1500))
        assertEquals(0, lyrics.activeIndex(750))
    }
    @Test fun emptyLyricsHaveNoActiveLine() {
        assertEquals(-1, LrcParser.parse("").activeIndex(1000))
    }
    @Test fun crossfadeWaitsForIncomingAndFreezesWhilePaused() {
        val clock = CrossfadeClock()
        clock.start(1000)
        assertFalse(clock.advance(400, true, false))
        assertEquals(0.0, clock.progress, 0.00001)
        assertFalse(clock.advance(400, true, true))
        assertFalse(clock.advance(500, false, true))
        assertEquals(0.4, clock.progress, 0.00001)
        assertTrue(clock.advance(600, true, true))
        assertEquals(1.0, clock.progress, 0.00001)
    }
    @Test fun cancelAndRestartCannotCommitAnOldFade() {
        val clock = CrossfadeClock()
        clock.start(1000)
        clock.advance(500, true, true)
        clock.cancel()
        assertFalse(clock.advance(2000, true, true))
        assertEquals(0.0, clock.progress, 0.00001)
        clock.start(500)
        assertTrue(clock.advance(Long.MAX_VALUE, true, true))
    }
    @Test(expected = IllegalArgumentException::class)
    fun fadeRejectsZeroDuration() { CrossfadeClock().start(0) }
}
