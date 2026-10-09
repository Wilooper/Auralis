package app.auralis.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SmartFlowTest {
    private fun tracks() = (0..20).map { LibraryTrack("$it", "file:///$it", "Song $it") }
    private fun features() = (0..20).associate { "$it" to SongFeatures(it/20.0, 80.0+it*3, .3, .8) }
    @Test fun flowsMoveGraduallyWithoutDuplicatesAndKeepSeed() {
        val rise = SmartFlow.order(tracks(), features(), "0", FlowGoal.RISE)
        val calm = SmartFlow.order(tracks(), features(), "20", FlowGoal.CALM)
        assertEquals("0", rise.first().id); assertEquals("20", calm.first().id)
        assertEquals(rise.size, rise.map { it.id }.toSet().size)
        assertTrue(rise.last().id.toInt() > rise.first().id.toInt())
        assertTrue(calm.last().id.toInt() < calm.first().id.toInt())
        assertTrue(rise.zipWithNext().all { (a,b) -> kotlin.math.abs(a.id.toInt()-b.id.toInt()) <= 3 })
    }
    @Test fun missingOrSilentProfilesAreExcludedAndLimitHonored() {
        assertTrue(SmartFlow.order(tracks(), emptyMap(), null, FlowGoal.STEADY).isEmpty())
        val profiles = features().mapValues { (id,f) -> if(id == "5") f.copy(confidence = 0.0) else f }
        val result = SmartFlow.order(tracks(), profiles, "5", FlowGoal.STEADY, 8)
        assertEquals(8, result.size); assertFalse(result.any { it.id == "5" })
    }
    @Test fun shuffleVisitsEachSongOnceAndRepeatWrapsAndPreviousRetraces() {
        val order = QueueOrder(Random(42)); order.reset(10, 4, true)
        val visited = mutableListOf(4)
        while(true) { val next = order.next(visited.last(), false) ?: break; visited += next }
        assertEquals(10, visited.size); assertEquals(10, visited.toSet().size)
        assertEquals(4, order.next(visited.last(), true))
        for(i in 1 until visited.size) assertEquals(visited[i-1], order.previous(visited[i], false))
    }
    @Test fun sequentialOrderHandlesEmptySingleAndLastTrack() {
        val order = QueueOrder(Random(2)); order.reset(0, 0, false); assertNull(order.next(0, true))
        order.reset(1, 0, false); assertNull(order.next(0, false)); assertEquals(0, order.next(0, true))
        order.reset(3, 1, false); assertEquals(2, order.next(1, false)); assertEquals(0, order.previous(1, false))
    }
    @Test fun analyzerDistinguishesAmplitudeAndDetectsSyntheticBeat() {
        val quiet = AudioFeatureAccumulator(8000); val loud = AudioFeatureAccumulator(8000); val beat = AudioFeatureAccumulator(8000)
        repeat(8000*16) { i ->
            val wave = sin(i * 2 * PI * 220 / 8000)
            quiet.sample(wave*.015); loud.sample(wave*.7)
            val pulse = if(i % 4000 < 600) .8 else .001
            beat.sample(wave*pulse)
        }
        assertTrue(loud.finish().energy > quiet.finish().energy)
        assertEquals(120.0, beat.finish().tempo, 3.0)
        assertEquals(0.0, AudioFeatureAccumulator(8000).finish().confidence, 0.0)
    }
    @Test fun djEnvelopeHasSofterEntranceAndBoundedOverlap() {
        assertTrue(FadeEnvelope.at(.1, true).incoming < FadeEnvelope.at(.1).incoming)
        assertEquals(1.0, FadeEnvelope.at(0.0, true).outgoing, 1e-9)
        assertEquals(1.0, FadeEnvelope.at(1.0, true).incoming, 1e-9)
        for(i in 0..100) { val gains = FadeEnvelope.at(i/100.0, true); assertTrue(gains.outgoing+gains.incoming <= 1.000001) }
    }
    @Test fun descriptorsAreBoundedForMalformedSamples() {
        val acc = AudioFeatureAccumulator(100)
        repeat(400) { acc.sample(if(it % 2 == 0) Double.NaN else 10.0) }
        val f = acc.finish()
        assertTrue(f.energy.isFinite()); assertTrue(f.energy in 0.0..1.0); assertTrue(f.brightness in 0.0..1.0)
    }
}
