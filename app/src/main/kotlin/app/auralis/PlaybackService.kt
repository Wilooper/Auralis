package app.auralis

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.session.*
import app.auralis.playback.VlcSessionPlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import app.auralis.library.PlaybackCheckpoint
import app.auralis.library.PlaybackCheckpointStore
import androidx.media3.common.MediaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect

const val CROSSFADE_COMMAND = "app.auralis.CROSSFADE"

@UnstableApi
class PlaybackService : MediaSessionService() {
    private lateinit var player: VlcSessionPlayer
    private var session: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val snapshots = Channel<PlaybackCheckpoint>(Channel.CONFLATED)
    private var checkpointItems = emptyList<MediaItem>()
    private var restoring = true
    private var lastHistoryId: String? = null
    private val checkpointListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) checkpointItems = (0 until player.mediaItemCount).map(player::getMediaItemAt)
            if (!restoring) checkpoint()
            val id = player.currentMediaItem?.mediaId
            if (player.isPlaying && id != null && id != lastHistoryId) {
                lastHistoryId = id; (application as AuralisApplication).library.markPlayed(id)
            }
            if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED) || events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED))
                getSharedPreferences("playback", MODE_PRIVATE).edit { putInt("repeat", player.repeatMode); putBoolean("shuffle", player.shuffleModeEnabled) }
        }
    }
    private fun checkpoint() {
        snapshots.trySend(PlaybackCheckpoint(checkpointItems, player.currentMediaItemIndex.coerceAtLeast(0), player.currentPosition.coerceAtLeast(0)))
    }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) player.pauseForNoisyRoute()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = VlcSessionPlayer(this, (application as AuralisApplication).extensionBridge::resolve)
        player.addListener(checkpointListener)
        val extensionRegistry = (application as AuralisApplication).extensions
        var revisions = extensionRegistry.entries.value.associate { it.manifest.id to it.revision }
        serviceScope.launch {
            extensionRegistry.entries.collect { entries ->
                val next = entries.associate { it.manifest.id to it.revision }
                val changed = revisions.keys.filter { revisions[it] != next[it] }.toSet()
                if(changed.isNotEmpty()) player.setCrossfade(getSharedPreferences("playback", MODE_PRIVATE).getLong("crossfade",0)) // Cancel a revoked incoming deck too.
                val uri = player.currentMediaItem?.localConfiguration?.uri
                if(uri?.scheme == "auralis") {
                    val entry = entries.firstOrNull { it.manifest.id == uri.authority }
                    if(entry == null || !entry.enabled || "stream.play" !in entry.grants || uri.authority in changed) player.stop()
                }
                revisions = next
            }
        }
        serviceScope.launch {
            val store = PlaybackCheckpointStore(this@PlaybackService)
            val saved = withContext(Dispatchers.IO) { store.read() }
            if (saved != null && player.mediaItemCount == 0) {
                player.setMediaItems(saved.items.take(100))
                saved.items.drop(100).chunked(100).forEach { player.addMediaItems(it) }
                player.seekTo(saved.index, saved.positionMs)
                // Opening Auralis restores context without unexpectedly starting audio.
            }
            restoring = false
            checkpoint()
        }
        serviceScope.launch(Dispatchers.IO) {
            val store = PlaybackCheckpointStore(this@PlaybackService)
            var previousItems: List<MediaItem>? = null
            for (snapshot in snapshots) {
                store.save(snapshot, previousItems != snapshot.items)
                previousItems = snapshot.items
                delay(300)
            }
        }
        serviceScope.launch { while (isActive) { delay(2000); if (!restoring) checkpoint() } }
        val playback = getSharedPreferences("playback", MODE_PRIVATE)
        val savedRepeat = playback.getInt("repeat", Player.REPEAT_MODE_OFF)
        val savedShuffle = playback.getBoolean("shuffle", false)
        player.setCrossfade(playback.getLong("crossfade", 0))
        player.setDjMode(playback.getBoolean("dj", false))
        player.repeatMode = savedRepeat
        player.shuffleModeEnabled = savedShuffle
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    if (controller.packageName != packageName && !controller.isTrusted)
                        return MediaSession.ConnectionResult.reject()
                    val defaults = super.onConnect(session, controller)
                    val commands = defaults.availableSessionCommands.buildUpon()
                    if (controller.packageName == packageName) commands.add(SessionCommand(CROSSFADE_COMMAND, Bundle.EMPTY))
                    val playerCommands = defaults.availablePlayerCommands.buildUpon()
                    if (controller.packageName != packageName) playerCommands.removeAll(
                        Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS)
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands.build())
                        .setAvailablePlayerCommands(playerCommands.build()).build()
                }
                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if (controller.packageName != packageName || customCommand.customAction != CROSSFADE_COMMAND)
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                    val ms = args.getLong("durationMs", 0).coerceIn(0, 12_000)
                    player.setCrossfade(ms)
                    val dj = args.getBoolean("dj", false)
                    player.setDjMode(dj)
                    getSharedPreferences("playback", MODE_PRIVATE).edit { putLong("crossfade", ms); putBoolean("dj", dj) }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).build()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        unregisterReceiver(noisy)
        session?.release()
        session = null
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }
}
