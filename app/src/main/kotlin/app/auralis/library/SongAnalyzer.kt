package app.auralis.library

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import app.auralis.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder

/** Explicit background analysis: one 16-second interior excerpt; never sends audio anywhere. */
class SongAnalyzer(private val context: Context) {
    suspend fun analyze(track: LibraryTrack): SongFeatures? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null)
            val index = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else track.durationMs * 1000
            val start = (duration*.35).toLong().coerceAtMost((duration-16_000_000).coerceAtLeast(0))
            val end = start + 16_000_000
            extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0); decoder.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var accumulator = AudioFeatureAccumulator(rate)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val deadline = System.nanoTime() + 8_000_000_000L
            while (System.nanoTime() < deadline) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val input = decoder.dequeueInputBuffer(5000)
                    if (input >= 0) {
                        val buffer = requireNotNull(decoder.getInputBuffer(input))
                        val time = extractor.sampleTime
                        val size = if (time < 0 || time > end + 1_000_000) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) { decoder.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { decoder.queueInputBuffer(input, 0, size, time, 0); extractor.advance() }
                    }
                }
                when (val output = decoder.dequeueOutputBuffer(info, 5000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val actual = decoder.outputFormat
                        rate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (actual.containsKey(MediaFormat.KEY_PCM_ENCODING)) actual.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        if (encoding !in listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT)) return null
                        accumulator = AudioFeatureAccumulator(rate)
                    }
                    else -> if (output >= 0) {
                        try {
                            val buffer = decoder.getOutputBuffer(output)
                            if (buffer != null && info.size > 0) {
                                buffer.position(info.offset); buffer.limit(info.offset + info.size); buffer.order(ByteOrder.nativeOrder())
                                val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                                var frame = 0L
                                while (buffer.remaining() >= channels * bytes) {
                                    val time = info.presentationTimeUs + frame * 1_000_000 / rate
                                    var mono = 0.0
                                    repeat(channels) { mono += if (bytes == 4) buffer.float.toDouble() else buffer.short / 32768.0 }
                                    if (time in start until end) accumulator.sample(mono / channels)
                                    frame++
                                }
                            }
                        } finally { decoder.releaseOutputBuffer(output, false) }
                        if (info.presentationTimeUs >= end || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return accumulator.finish().takeIf { it.confidence > 0 }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: Exception) { return null
        } finally { codec?.let { runCatching { it.stop() }; it.release() }; extractor.release() }
    }
}
