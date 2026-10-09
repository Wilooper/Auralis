package app.auralis.model

import kotlin.math.*

/** Audio descriptors, not semantic emotion labels. Tempo may be unknown (0). */
data class SongFeatures(val energy: Double, val tempo: Double, val brightness: Double, val confidence: Double)
enum class FlowGoal(val label: String, val target: Double?) { CALM("Wind down", .15), STEADY("Stay similar", null), RISE("Build energy", .9) }

class AudioFeatureAccumulator(private val sampleRate: Int) {
    private val envelope = ArrayList<Double>()
    private var squares = 0.0
    private var totalSquares = 0.0
    private var count = 0
    private var total = 0
    private var crossings = 0
    private var previous = 0.0
    private val frameSize = (sampleRate / 100).coerceAtLeast(1)
    fun sample(value: Double) {
        val v = if (value.isFinite()) value.coerceIn(-1.0, 1.0) else 0.0
        squares += v*v; totalSquares += v*v; count++; total++
        if ((v >= 0) != (previous >= 0)) crossings++
        previous = v
        if (count >= frameSize) { envelope += sqrt(squares / count); squares = 0.0; count = 0 }
    }
    fun finish(): SongFeatures {
        if (total < sampleRate * 2 || totalSquares < 1e-8) return SongFeatures(0.0, 0.0, 0.0, 0.0)
        val rms = sqrt(totalSquares / total)
        val db = 20 * log10(rms.coerceAtLeast(1e-9))
        val brightness = (crossings.toDouble() / total * 6).coerceIn(0.0, 1.0)
        val onset = DoubleArray(envelope.size) { i -> if (i == 0) 0.0 else (envelope[i] - envelope[i-1]).coerceAtLeast(0.0) }
        var best = 0.0; var bestLag = 0
        val norm = onset.sumOf { it*it }
        if (norm > 1e-8) for (lag in 33..100) { // 60–182 BPM; octave ambiguity remains.
            var correlation = 0.0
            for (i in lag until onset.size) correlation += onset[i] * onset[i-lag]
            val score = correlation / norm
            if (score > best) { best = score; bestLag = lag }
        }
        val tempo = if (best > .12 && bestLag > 0) 6000.0 / bestLag else 0.0
        val loudness = ((db + 42) / 36).coerceIn(0.0, 1.0)
        return SongFeatures((loudness*.7 + brightness*.15 + (if (tempo > 0) (tempo-60)/125 else .4)*.15).coerceIn(0.0, 1.0),
            tempo, brightness, .65)
    }
}

object SmartFlow {
    /** Up to 30 tracks. Unknown descriptors are excluded instead of inventing a mood. */
    fun order(tracks: List<LibraryTrack>, features: Map<String, SongFeatures>, seedId: String?, goal: FlowGoal, limit: Int = 25): List<LibraryTrack> {
        val remaining = tracks.distinctBy { it.id }.filter { (features[it.id]?.confidence ?: 0.0) > 0 }.toMutableList()
        if (remaining.isEmpty() || limit <= 0) return emptyList()
        val first = remaining.firstOrNull { it.id == seedId } ?: remaining.minBy { abs(features.getValue(it.id).energy - .5) }
        remaining.remove(first)
        val result = mutableListOf(first)
        val start = features.getValue(first.id).energy
        val target = goal.target ?: start
        val length = minOf(limit, remaining.size + 1)
        while (result.size < length) {
            val prior = result.last(); val a = features.getValue(prior.id)
            val desired = start + (target-start)*result.size / (length-1).coerceAtLeast(1)
            val next = remaining.minWith(compareBy<LibraryTrack> { track ->
                val b = features.getValue(track.id)
                val tempoCost = if (a.tempo > 0 && b.tempo > 0) abs(ln(a.tempo/b.tempo)) else .12
                abs(b.energy-desired)*2 + abs(b.energy-a.energy)*1.3 + abs(b.brightness-a.brightness)*.25 + tempoCost*.3 +
                    if (prior.artist == track.artist && prior.artist != "Unknown artist") -.03 else 0.0
            }.thenBy { it.id })
            remaining.remove(next); result += next
        }
        return result
    }
}

/** Stable shuffle traversal keeps queue UI order and the currently playing track. */
class QueueOrder(private val random: kotlin.random.Random = kotlin.random.Random.Default) {
    private var order = emptyList<Int>()
    fun reset(size: Int, current: Int, shuffle: Boolean) {
        order = if (size <= 0) emptyList() else if (shuffle) listOf(current) + (0 until size).filter { it != current }.shuffled(random) else (0 until size).toList()
    }
    fun indices(): List<Int> = order
    fun next(current: Int, repeatAll: Boolean): Int? {
        val at = order.indexOf(current)
        return order.getOrNull(at+1) ?: order.firstOrNull().takeIf { repeatAll }
    }
    fun previous(current: Int, repeatAll: Boolean): Int? {
        val at = order.indexOf(current)
        return order.getOrNull(at-1) ?: order.lastOrNull().takeIf { repeatAll }
    }
}
