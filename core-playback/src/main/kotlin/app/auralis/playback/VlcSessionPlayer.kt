package app.auralis.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class VlcSessionPlayer(context: Context, resolveRemote: (android.net.Uri) -> android.net.Uri = { error("Remote bridge unavailable") }) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var disposed = false
    private val engine = VlcEngine(context.applicationContext, ::onEngineChanged, resolveRemote)
    private val focus = AudioFocus(context.applicationContext, engine)
    private var cachedPlaylist = emptyList<MediaItemData>()
    private var cachedTimeline: Timeline = Timeline.EMPTY
    private var cachedRevision = -1L
    private var cachedIndex = -1
    private var cachedSeekable = false

    private fun onEngineChanged() {
        if (disposed) return
        focus.onPlaybackStateChanged()
        invalidateState()
    }

    fun setCrossfade(durationMs: Long) { engine.setCrossfade(durationMs) }
    fun setDjMode(enabled: Boolean) { engine.setDjMode(enabled) }
    fun pauseForNoisyRoute() { focus.pause() }

    override fun getState(): State {
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
            Player.COMMAND_RELEASE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_GET_VOLUME, Player.COMMAND_SET_VOLUME, Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE,
        )
        if (engine.slots.isNotEmpty()) {
            commands.add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            if (engine.seekable) commands.addAll(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD)
            if (engine.previousIndex != null) commands.addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            if (engine.nextIndex != null) commands.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (cachedRevision != engine.playlistRevision || cachedIndex != engine.index || cachedSeekable != engine.seekable) {
            cachedPlaylist = engine.slots.mapIndexed { index, slot ->
                MediaItemData.Builder(slot.uid).setMediaItem(slot.item)
                    .setIsSeekable(index != engine.index || engine.seekable)
                    .setDurationUs(if (slot.durationMs > 0) slot.durationMs * 1000 else C.TIME_UNSET)
                    .build()
            }
            val base = State.Builder().setPlaylist(cachedPlaylist).build().timeline
            cachedTimeline = OrderedTimeline(base, engine.traversal)
            cachedRevision = engine.playlistRevision
            cachedIndex = engine.index
            cachedSeekable = engine.seekable
        }
        val playlist = cachedPlaylist
        return State.Builder()
            .setAvailableCommands(commands.build())
            .setPlaylist(cachedTimeline, Tracks.EMPTY, engine.slots.getOrNull(engine.index)?.item?.mediaMetadata)
            .setCurrentMediaItemIndex(if (playlist.isEmpty()) C.INDEX_UNSET else engine.index)
            .setPlayWhenReady(engine.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(engine.state)
            .setPlayerError(engine.error)
            .setContentPositionMs(engine.positionMs)
            .setContentBufferedPositionMs(PositionSupplier.getConstant(engine.positionMs))
            .setVolume(engine.volume)
            .setRepeatMode(engine.repeatMode)
            .setShuffleModeEnabled(engine.shuffle)
            .build()
    }

    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        engine.replace(mediaItems.map { QueueSlot(it) }, if (startIndex == C.INDEX_UNSET) 0 else startIndex,
            if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs)
        return done()
    }
    override fun handlePrepare(): ListenableFuture<*> { engine.prepare(); return done() }
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady && engine.slots.isNotEmpty()) {
            if (focus.play()) engine.setPlaying(true) else engine.focusDenied()
        }
        else focus.pause()
        return done()
    }
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val target = when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> engine.nextIndex ?: engine.index
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> engine.previousIndex ?: engine.index
            else -> if (mediaItemIndex == C.INDEX_UNSET) engine.index else mediaItemIndex
        }
        engine.seek(target,
            if (positionMs == C.TIME_UNSET) 0 else positionMs)
        if (seekCommand == Player.COMMAND_SEEK_TO_MEDIA_ITEM && engine.shuffle) engine.resetTraversal()
        return done()
    }
    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> { engine.setRepeatMode(repeatMode); return done() }
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> { engine.setShuffle(shuffleModeEnabled); return done() }
    override fun handleSetVolume(volume: Float): ListenableFuture<*> { engine.setVolume(volume); return done() }
    override fun handleStop(): ListenableFuture<*> { focus.abandon(); engine.stop(); return done() }
    override fun handleRelease(): ListenableFuture<*> {
        disposed = true
        focus.abandon()
        engine.release()
        return done()
    }
    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> = editQueue {
        addAll(index, mediaItems.map { QueueSlot(it) })
    }
    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> = editQueue {
        subList(fromIndex, toIndex).clear()
    }
    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> = editQueue {
        val moved = subList(fromIndex, toIndex).toList()
        subList(fromIndex, toIndex).clear()
        addAll(newIndex, moved)
    }
    override fun handleReplaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>): ListenableFuture<*> = editQueue {
        subList(fromIndex, toIndex).clear()
        addAll(fromIndex, mediaItems.map { QueueSlot(it) })
    }
    private fun editQueue(edit: MutableList<QueueSlot>.() -> Unit): ListenableFuture<*> {
        val slots = engine.slots.toMutableList().apply(edit)
        engine.editQueue(slots)
        return done()
    }
    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()
}

/** Media3's list-backed timeline does not implement shuffle order in 1.6.1. */
@UnstableApi
private class OrderedTimeline(private val base: Timeline, private val order: List<Int>) : Timeline() {
    private val positions = order.withIndex().associate { it.value to it.index }
    override fun getWindowCount() = base.windowCount
    override fun getPeriodCount() = base.periodCount
    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long) = base.getWindow(windowIndex, window, defaultPositionProjectionUs)
    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean) = base.getPeriod(periodIndex, period, setIds)
    override fun getIndexOfPeriod(uid: Any) = base.getIndexOfPeriod(uid)
    override fun getUidOfPeriod(periodIndex: Int) = base.getUidOfPeriod(periodIndex)
    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean) = if(shuffleModeEnabled) order.firstOrNull() ?: C.INDEX_UNSET else base.getFirstWindowIndex(false)
    override fun getLastWindowIndex(shuffleModeEnabled: Boolean) = if(shuffleModeEnabled) order.lastOrNull() ?: C.INDEX_UNSET else base.getLastWindowIndex(false)
    override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
        if(!shuffleModeEnabled) return base.getNextWindowIndex(windowIndex, repeatMode, false)
        if(repeatMode == Player.REPEAT_MODE_ONE) return windowIndex
        return order.getOrNull((positions[windowIndex] ?: -1)+1) ?: if(repeatMode == Player.REPEAT_MODE_ALL) getFirstWindowIndex(true) else C.INDEX_UNSET
    }
    override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
        if(!shuffleModeEnabled) return base.getPreviousWindowIndex(windowIndex, repeatMode, false)
        if(repeatMode == Player.REPEAT_MODE_ONE) return windowIndex
        return order.getOrNull((positions[windowIndex] ?: 0)-1) ?: if(repeatMode == Player.REPEAT_MODE_ALL) getLastWindowIndex(true) else C.INDEX_UNSET
    }
}
