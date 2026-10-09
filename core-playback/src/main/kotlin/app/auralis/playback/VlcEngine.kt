package app.auralis.playback

import android.content.Context
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.auralis.model.FadeEnvelope
import app.auralis.model.CrossfadeClock
import app.auralis.model.QueueOrder
import kotlinx.coroutines.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.UUID
import kotlin.math.roundToInt

internal data class QueueSlot(
    val item: MediaItem,
    val uid: String = UUID.randomUUID().toString(),
    var durationMs: Long = C.TIME_UNSET,
)

@UnstableApi
internal class VlcEngine(private val context: Context, private val changed: () -> Unit, private val resolveRemote: (android.net.Uri) -> android.net.Uri) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val vlc = LibVLC(context, arrayListOf("--no-video", "--no-audio-time-stretch"))
    private val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Auralis:playback")
        .apply { setReferenceCounted(false) }
    private var wakeRefreshedNs = 0L
    var slots = emptyList<QueueSlot>(); private set
    var playlistRevision = 0L; private set
    var index = 0; private set
    var playWhenReady = false; private set
    var state = Player.STATE_IDLE; private set
    var error: PlaybackException? = null; private set
    var volume = 1f; private set
    var crossfadeMs = 0L; private set
    var repeatMode = Player.REPEAT_MODE_OFF; private set
    var shuffle = false; private set
    var djMode = false; private set
    private val order = QueueOrder()
    private var incomingIndex = -1
    val traversal: List<Int> get() = order.indices()
    fun resetTraversal() { order.reset(slots.size, index, shuffle); playlistRevision++; publish() }
    val nextIndex: Int? get() = order.next(index, repeatMode == Player.REPEAT_MODE_ALL)
    val previousIndex: Int? get() = order.previous(index, repeatMode == Player.REPEAT_MODE_ALL)
    var duck = 1f; private set
    private var active: Deck? = null
    private var incoming: Deck? = null
    private val fadeClock = CrossfadeClock()
    private var lastTickNs = 0L
    private var transitionAttempted = false
    private var released = false
    val positionMs: Long get() = active?.position ?: 0
    val seekable: Boolean get() = active?.player?.isSeekable == true

    init {
        scope.launch {
            while (isActive) {
                tick()
                delay(if (incoming != null) 40 else 100)
            }
        }
    }

    private inner class Deck(val slot: QueueSlot, startMs: Long, gain: Float) {
        val player = MediaPlayer(vlc)
        private var descriptor: ParcelFileDescriptor? = null
        private var closed = false
        var ready = false
        var initialSeek = startMs
        val position: Long get() = if (ready) player.time.coerceAtLeast(0) else initialSeek

        init {
            try {
                val source = requireNotNull(slot.item.localConfiguration?.uri) { "No audio URI" }
                val uri = if(source.scheme == "auralis") resolveRemote(source) else source
                require(uri.scheme == "content" || uri.scheme == "file" || (source.scheme == "auralis" && uri.scheme == "http" && uri.host == "127.0.0.1")) { "Use an approved provider through the extension bridge" }
                val media = if (uri.scheme == "content") {
                    descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: error("The selected audio file cannot be opened")
                    Media(vlc, descriptor!!.fileDescriptor)
                } else Media(vlc, uri)
                try {
                    media.addOption(":no-video")
                    media.addOption(":no-metadata-network-access")
                    if(source.scheme == "auralis") {
                        val demux = when(source.getQueryParameter("format") ?: "mp3") {
                            "mp3" -> "mpga"; "flac" -> "flac"; "wav" -> "wav"; "ogg" -> "ogg"; else -> error("Unsupported remote audio format")
                        }
                        // An explicit demux list ending in none disables playlist/automatic fallback.
                        media.addOption(":demux=$demux,none")
                        media.addOption(":no-sout-all")
                    }
                    player.media = media
                } finally { media.release() }
                player.setVolume((gain * 100).roundToInt())
                player.setEventListener { event ->
                    if (!closed && !released) onEvent(this, event)
                }
            } catch (failure: Exception) {
                close()
                throw failure
            }
        }

        fun close() {
            if (closed) return
            closed = true
            player.setEventListener(null)
            player.stop()
            player.release()
            descriptor?.close()
            descriptor = null
        }
    }

    fun replace(newSlots: List<QueueSlot>, startIndex: Int, startMs: Long = 0) {
        assertMain()
        cancelTransition()
        active?.close()
        active = null
        slots = newSlots
        playlistRevision++
        index = if (slots.isEmpty()) 0 else startIndex.coerceIn(slots.indices)
        order.reset(slots.size, index, shuffle)
        error = null
        state = Player.STATE_IDLE
        transitionAttempted = false
        if (slots.isNotEmpty()) load(startMs)
        else playWhenReady = false
        publish()
    }

    fun editQueue(newSlots: List<QueueSlot>) {
        assertMain()
        val uid = slots.getOrNull(index)?.uid
        val preserved = newSlots.indexOfFirst { it.uid == uid }
        if (preserved < 0) {
            replace(newSlots, index.coerceAtMost(newSlots.lastIndex).coerceAtLeast(0))
            return
        }
        // Keep the native active deck, playback position, and paused/playing state.
        cancelTransition()
        slots = newSlots
        index = preserved
        order.reset(slots.size, index, shuffle)
        playlistRevision++
        transitionAttempted = false
        publish()
    }

    fun prepare() {
        assertMain()
        if (slots.isEmpty()) return
        if (active == null || error != null) load(0)
        if (error == null) state = Player.STATE_READY
        publish()
    }

    fun setPlaying(playing: Boolean) {
        assertMain()
        if (slots.isEmpty()) return
        if (playing && (state == Player.STATE_ENDED || error != null)) load(0)
        playWhenReady = playing && error == null
        if (playWhenReady) {
            active?.player?.play()
            incoming?.player?.play()
            state = if (active?.ready == true) Player.STATE_READY else Player.STATE_BUFFERING
        } else {
            active?.player?.pause()
            incoming?.player?.pause()
        }
        lastTickNs = System.nanoTime()
        publish()
    }

    fun seek(target: Int, position: Long) {
        assertMain()
        if (slots.isEmpty()) return
        cancelTransition()
        transitionAttempted = false
        val selected = target.coerceIn(slots.indices)
        error = null
        if (selected != index || state == Player.STATE_ENDED || active == null) {
            index = selected
            load(position.coerceAtLeast(0))
        } else {
            val deck = active!!
            if (deck.ready) deck.player.setTime(position.coerceAtLeast(0))
            else deck.initialSeek = position.coerceAtLeast(0)
        }
        publish()
    }

    fun setRepeatMode(mode: Int) { cancelTransition(); repeatMode = mode.coerceIn(0, 2); transitionAttempted = false; publish() }
    fun setShuffle(enabled: Boolean) { cancelTransition(); shuffle = enabled; order.reset(slots.size, index, shuffle); playlistRevision++; transitionAttempted = false; publish() }
    fun setDjMode(enabled: Boolean) { cancelTransition(); djMode = enabled; transitionAttempted = false; publish() }
    fun setVolume(value: Float) { volume = value.coerceIn(0f, 1f); applyGains(); publish() }
    fun focusDenied() { fail(IllegalStateException("Audio focus unavailable. Pause other audio and try again.")); publish() }
    fun setDucking(value: Float) { duck = value; applyGains() }
    fun setCrossfade(ms: Long) {
        cancelTransition()
        crossfadeMs = ms.coerceIn(0, 12_000)
        transitionAttempted = false
        publish()
    }
    fun stop() {
        cancelTransition()
        active?.close()
        active = null
        playWhenReady = false
        state = Player.STATE_IDLE
        error = null
        publish()
    }

    private fun load(position: Long) {
        cancelTransition()
        active?.close()
        active = null
        transitionAttempted = false
        error = null
        try {
            active = Deck(slots[index], position, volume * duck)
            state = Player.STATE_READY
            if (playWhenReady) { state = Player.STATE_BUFFERING; active!!.player.play() }
        } catch (failure: Exception) { fail(failure) }
    }

    private fun onEvent(deck: Deck, event: MediaPlayer.Event) {
        assertMain()
        if (deck !== active && deck !== incoming) return
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                deck.ready = true
                if (deck.initialSeek > 0) {
                    deck.player.setTime(deck.initialSeek)
                    deck.initialSeek = 0
                }
                if (!playWhenReady) deck.player.pause()
                if (deck === active) state = Player.STATE_READY
                lastTickNs = System.nanoTime()
            }
            MediaPlayer.Event.Buffering -> if (deck === active && playWhenReady) {
                state = if (event.buffering < 100) Player.STATE_BUFFERING else Player.STATE_READY
            }
            MediaPlayer.Event.EncounteredError -> {
                if (deck === incoming) cancelTransition()
                else fail(IllegalStateException("LibVLC could not decode or read this audio file"))
            }
            MediaPlayer.Event.EndReached -> {
                if (deck === incoming) cancelTransition()
                else if (incoming?.ready == true) commitTransition()
                else if (repeatMode == Player.REPEAT_MODE_ONE) { load(0) }
                else if (nextIndex != null) { index = nextIndex!!; load(0) }
                else { cancelTransition(); state = Player.STATE_ENDED; playWhenReady = false }
            }
        }
        if (deck === active || deck === incoming) {
            val duration = deck.player.length
            updateDuration(deck, duration)
        }
        publish()
    }

    private fun tick() {
        if (released) return
        val now = System.nanoTime()
        val deltaMs = if (lastTickNs == 0L) 0 else ((now - lastTickNs) / 1_000_000).coerceIn(0, 250)
        lastTickNs = now
        val deck = active ?: return
        updateDuration(deck, deck.player.length)
        if (playWhenReady && state == Player.STATE_READY) {
            val remaining = deck.slot.durationMs - deck.position
            if (crossfadeMs > 0 && !transitionAttempted && incoming == null &&
                repeatMode != Player.REPEAT_MODE_ONE && nextIndex != null && nextIndex != index && deck.slot.durationMs > crossfadeMs * 2 &&
                remaining in 1..crossfadeMs) {
                transitionAttempted = true
                fadeClock.start(remaining)
                try {
                    incomingIndex = nextIndex!!
                    incoming = Deck(slots[incomingIndex], 0, 0f)
                    incoming!!.player.play()
                } catch (_: Exception) { cancelTransition() }
            }
            if (incoming?.ready == true) {
                val finished = fadeClock.advance(deltaMs, running = true, incomingReady = true)
                applyGains()
                if (finished) commitTransition()
            }
            publish()
        }
    }

    private fun applyGains() {
        val gains = FadeEnvelope.at(fadeClock.progress, djMode)
        active?.player?.setVolume((volume * duck * (if (incoming == null) 1.0 else gains.outgoing) * 100).roundToInt())
        incoming?.player?.setVolume((volume * duck * gains.incoming * 100).roundToInt())
    }

    private fun updateDuration(deck: Deck, duration: Long) {
        if (duration > 0 && duration != deck.slot.durationMs) {
            deck.slot.durationMs = duration
            playlistRevision++
        }
    }

    private fun cancelTransition() {
        incoming?.close()
        incoming = null
        fadeClock.cancel()
        applyGains()
    }

    private fun commitTransition() {
        val next = incoming ?: return
        active?.close()
        active = next
        incoming = null
        index = incomingIndex
        incomingIndex = -1
        fadeClock.cancel()
        transitionAttempted = false
        state = Player.STATE_READY
        applyGains()
    }

    private fun fail(cause: Exception) {
        cancelTransition()
        active?.close()
        active = null
        error = PlaybackException(cause.message ?: "Playback failed", cause,
            if (cause is SecurityException) PlaybackException.ERROR_CODE_IO_NO_PERMISSION
            else PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        playWhenReady = false
        state = Player.STATE_IDLE
    }

    private fun publish() {
        val keepAwake = playWhenReady && state != Player.STATE_ENDED && error == null
        val now = System.nanoTime()
        if (keepAwake && (!wakeLock.isHeld || now - wakeRefreshedNs >= 300_000_000_000L)) {
            wakeLock.acquire(600_000L)
            wakeRefreshedNs = now
        }
        if (!keepAwake && wakeLock.isHeld) wakeLock.release()
        changed()
    }

    fun release() {
        assertMain()
        if (released) return
        released = true
        scope.cancel()
        incoming?.close()
        active?.close()
        incoming = null
        active = null
        vlc.release()
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun assertMain() = check(Looper.myLooper() == Looper.getMainLooper())
}
