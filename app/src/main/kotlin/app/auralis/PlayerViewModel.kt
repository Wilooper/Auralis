package app.auralis

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import app.auralis.library.mediaItem
import app.auralis.library.EmbeddedLyricsRepository
import app.auralis.model.EmbeddedLyricsResult
import app.auralis.model.LyricsParser
import app.auralis.model.Lyrics
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

data class PlayerScreenState(
    val connected: Boolean = false,
    val importing: Boolean = false,
    val queue: List<MediaItem> = emptyList(),
    val index: Int = 0,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val seekable: Boolean = false,
    val error: String? = null,
    val crossfadeSeconds: Int = 0,
    val djMode: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val canNext: Boolean = false,
    val canPrevious: Boolean = false,
    val accent: Int = 4,
    val lyrics: Lyrics? = null,
    val lyricsLoading: Boolean = false,
    val lyricsMessage: String? = null,
    val importProgress: String? = null,
    val importMessage: String? = null,
    val importCommitting: Boolean = false,
)

@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("appearance", Application.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(PlayerScreenState(djMode = application.getSharedPreferences("playback", Application.MODE_PRIVATE).getBoolean("dj", false), accent = preferences.getInt("accent", 4),
        crossfadeSeconds = (application.getSharedPreferences("playback", Application.MODE_PRIVATE).getLong("crossfade", 0) / 1000).toInt()))
    val state = mutableState.asStateFlow()
    private var controller: MediaController? = null
    private val lyricsByTrack = mutableMapOf<String, Lyrics>()
    private val embeddedCache = object : LinkedHashMap<String, EmbeddedLyricsResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EmbeddedLyricsResult>?) = size > 32
    }
    private var activeTrack: String? = null
    private var lyricsJob: Job? = null
    private var transientError: String? = null
    private val future = MediaController.Builder(application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)))
        .setListener(object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                this@PlayerViewModel.controller = null
                mutableState.value = mutableState.value.copy(connected = false, error = "Playback service disconnected. Reopen Auralis to reconnect.")
            }
        }).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    init {
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                refresh(true)
            } catch (failure: Exception) {
                transientError = "Could not connect to playback: ${failure.cause?.message ?: failure.message}"
                mutableState.value = mutableState.value.copy(error = transientError)
            }
        }, Executor { Handler(Looper.getMainLooper()).post(it) })
        viewModelScope.launch {
            while (isActive) { refresh(); delay(200) }
        }
    }

    private fun refresh(updateQueue: Boolean = false) {
        val player = controller ?: return
        val queue = if (updateQueue || mutableState.value.queue.size != player.mediaItemCount)
            (0 until player.mediaItemCount).map(player::getMediaItemAt) else mutableState.value.queue
        val trackId = player.currentMediaItem?.mediaId
        mutableState.value = mutableState.value.copy(
            connected = true, queue = queue, index = player.currentMediaItemIndex.coerceAtLeast(0),
            playing = player.playWhenReady, buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            shuffle = player.shuffleModeEnabled, repeat = player.repeatMode,
            canNext = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
            canPrevious = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
            seekable = player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            error = transientError ?: player.playerError?.message,
        )
        if (trackId != activeTrack) {
            activeTrack = trackId
            loadEmbeddedLyrics()
        }
    }

    fun playFlow(tracks: List<app.auralis.model.LibraryTrack>) {
        if(tracks.isEmpty()) return
        controller?.shuffleModeEnabled = false
        playLibrary(tracks, tracks.first().id)
    }
    fun playLibrary(tracks: List<app.auralis.model.LibraryTrack>, id: String) {
        val player = controller ?: return
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val items = withContext(Dispatchers.Default) { tracks.map { it.mediaItem(getApplication()) } }
            val selected = items.indexOfFirst { it.mediaId == id }.coerceAtLeast(0)
            transientError = null
            player.pause()
            player.setMediaItems(items.take(100))
            items.drop(100).chunked(100).forEach { player.addMediaItems(it) }
            player.seekTo(selected, 0)
            player.prepare()
            player.play()
            refresh(true)
        }
    }

    fun playRemote(extension: String, tracks: List<app.auralis.extensions.ExtensionBridge.RemoteTrack>, selected: String, enqueue: Boolean = false) {
        val player = controller ?: return
        try {
            val bridge = (getApplication<Application>() as AuralisApplication).extensionBridge
            val items = tracks.take(100).map { track ->
                MediaItem.Builder().setMediaId("remote:$extension:${track.id}").setUri(bridge.mediaUri(extension, track.streamUrl, track.format))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle("Internet · $extension").build()).build()
            }
            if(enqueue) { require(player.mediaItemCount + items.size <= 1000) { "Remote queue limit reached" }; player.addMediaItems(items) }
            else if(items.isNotEmpty()) {
                player.pause(); player.setMediaItems(items); player.seekTo(tracks.indexOfFirst { it.id == selected }.coerceAtLeast(0), 0); player.prepare(); player.play()
            }
            transientError = null; refresh(true)
        } catch(failure: Exception) { transientError = failure.message; refresh() }
    }

    private fun loadEmbeddedLyrics() {
        lyricsJob?.cancel()
        val item = controller?.currentMediaItem
        val id = item?.mediaId
        val uri = item?.localConfiguration?.uri
        val manual = lyricsByTrack[id]
        val cached = embeddedCache[id]
        mutableState.value = mutableState.value.copy(lyrics = manual ?: cached?.lyrics,
            lyricsLoading = id != null && manual == null && cached == null,
            lyricsMessage = cached?.warnings?.joinToString(" · ")?.takeIf { it.isNotBlank() })
        if (id == null || uri == null || manual != null || cached != null) return
        if(uri.scheme == "auralis") { mutableState.value = mutableState.value.copy(lyricsLoading = false, lyricsMessage = "Remote provider lyrics are not available in SDK v1. Import an LRC file to follow along."); return }
        lyricsJob = viewModelScope.launch {
            val result = EmbeddedLyricsRepository(getApplication()).read(uri)
            embeddedCache[id] = result
            if (activeTrack == id && lyricsByTrack[id] == null) mutableState.value = mutableState.value.copy(
                lyrics = result.lyrics, lyricsLoading = false,
                lyricsMessage = result.warnings.joinToString(" · ").takeIf { it.isNotBlank() })
        }
    }

    fun useEmbeddedLyrics() {
        activeTrack?.let { lyricsByTrack.remove(it) }
        loadEmbeddedLyrics()
    }

    fun importLyrics(uri: Uri) {
        val trackId = controller?.currentMediaItem?.mediaId ?: return
        viewModelScope.launch {
            try {
                val lyrics = withContext(Dispatchers.IO) {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 1024 * 1024) { "Lyrics file is larger than 1 MB" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray().toString(Charsets.UTF_8).removePrefix("\uFEFF")
                    } ?: error("Cannot open the lyrics file")
                    LyricsParser.parse(text, "Attached lyrics file")
                }
                require(lyrics != null) { "No supported lyrics found" }
                lyricsByTrack[trackId] = lyrics
                if (activeTrack == trackId) {
                    lyricsJob?.cancel()
                    mutableState.value = mutableState.value.copy(lyrics = lyrics, lyricsLoading = false, lyricsMessage = null)
                }
                transientError = null
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { transientError = "Lyrics failed: ${failure.message}" }
            refresh()
        }
    }
    fun togglePlayback() {
        transientError = null
        controller?.run { if (playWhenReady) pause() else { prepare(); play() } }
        refresh()
    }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun next() { controller?.seekToNextMediaItem() }
    fun select(index: Int) { transientError = null; controller?.run { seekTo(index, 0); prepare(); play() } }
    fun remove(index: Int) { controller?.removeMediaItem(index) }
    fun move(index: Int, destination: Int) { controller?.moveMediaItem(index, destination) }
    fun playNext(index: Int) {
        val player = controller ?: return
        val target = if(index < player.currentMediaItemIndex) player.currentMediaItemIndex else player.currentMediaItemIndex + 1
        if(index != player.currentMediaItemIndex) player.shuffleModeEnabled = false
        if(index != player.currentMediaItemIndex) player.moveMediaItem(index, target.coerceAtMost(player.mediaItemCount-1))
    }
    fun seek(ms: Long) { controller?.seekTo(ms) }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { controller?.let { it.repeatMode = when(it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
    fun dj(enabled: Boolean) { mutableState.value = mutableState.value.copy(djMode = enabled); crossfade(if(enabled && state.value.crossfadeSeconds == 0) 8 else state.value.crossfadeSeconds) }
    fun crossfade(seconds: Int) {
        controller?.sendCustomCommand(SessionCommand(CROSSFADE_COMMAND, Bundle.EMPTY), Bundle().apply {
            putLong("durationMs", seconds.toLong() * 1000); putBoolean("dj", state.value.djMode)
        })?.addListener({ refresh() }, MoreExecutors.directExecutor())
        mutableState.value = mutableState.value.copy(crossfadeSeconds = seconds)
    }
    fun accent(index: Int) {
        preferences.edit { putInt("accent", index) }
        mutableState.value = mutableState.value.copy(accent = index)
    }
    fun dismissError() { transientError = null; mutableState.value = mutableState.value.copy(error = null) }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(future)
        super.onCleared()
    }
}
