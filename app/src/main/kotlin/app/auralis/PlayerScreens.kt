package app.auralis

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import app.auralis.model.LyricsTiming
import kotlinx.coroutines.delay

@UnstableApi
@Composable
internal fun MiniPlayer(state: PlayerScreenState, model: PlayerViewModel, open: () -> Unit) {
    val item = state.queue.getOrNull(state.index) ?: return
    val metadata = item.mediaMetadata
    Surface(Modifier.padding(horizontal = 12.dp, vertical = 5.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 12.dp, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackCover(metadata.artworkUri, metadata.title.toString(), Modifier.size(46.dp), RoundedCornerShape(9.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(metadata.title?.toString() ?: "Untitled audio", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(metadata.artist?.toString() ?: "Unknown artist", Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = model::togglePlayback, enabled = state.connected) {
                    if (state.buffering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                    else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = model::next, enabled = state.connected && state.canNext) { Icon(Icons.Rounded.SkipNext, "Next track", Modifier.size(26.dp)) }
            }
            LinearProgressIndicator(progress = { if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f), trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
        }
    }
}

@UnstableApi
@Composable
internal fun NowPlaying(state: PlayerScreenState, model: PlayerViewModel, loved: Boolean, favorite: () -> Unit) {
    val item = state.queue.getOrNull(state.index) ?: return
    val metadata = item.mediaMetadata
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val reserve = 362.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val cover = minOf(maxWidth - 56.dp, (maxHeight - reserve).coerceAtLeast(100.dp), 360.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(metadata.albumTitle?.toString()?.takeUnless { it == "Unknown album" } ?: "Your collection",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            TrackCover(metadata.artworkUri, metadata.title?.toString().orEmpty(), Modifier.size(cover).shadow(28.dp, RoundedCornerShape(16.dp)), RoundedCornerShape(16.dp))
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(top = 20.dp)) {
                Text(metadata.title?.toString() ?: "Untitled audio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(metadata.artist?.toString() ?: "Unknown artist", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                PlayerSeekBar(state, model)
                PlayerTransport(state, model)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = favorite) { Icon(if(loved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if(loved) "Remove from favorites" else "Add to favorites", tint = if(loved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)) }
                    Text(if(state.crossfadeSeconds > 0) "${if(state.djMode) "DJ blend" else "Crossfade"} · ${state.crossfadeSeconds}s" else if(item.localConfiguration?.uri?.scheme == "auralis") "Internet playback" else "Local playback", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun PlayerSeekBar(state: PlayerScreenState, model: PlayerViewModel) {
    val id = state.queue.getOrNull(state.index)?.mediaId
    var scrub by remember(id) { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1).toFloat()
    val interaction = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onSurface, activeTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .85f), inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .18f))
    Slider(value = if (state.durationMs > 0) scrub ?: state.positionMs.toFloat().coerceIn(0f, duration) else 0f, onValueChange = { scrub = it },
        onValueChangeFinished = { scrub?.let { model.seek(it.toLong()) }; scrub = null },
        valueRange = 0f..duration, enabled = state.seekable && state.durationMs > 0,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp), interactionSource = interaction, colors = colors,
        thumb = { Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape)) },
        track = { slider -> SliderDefaults.Track(slider, Modifier.height(4.dp), enabled = state.seekable && state.durationMs > 0, colors = colors) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(timeLabel(scrub?.toLong() ?: state.positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        Text(if (state.durationMs > 0) "−${timeLabel((state.durationMs - (scrub?.toLong() ?: state.positionMs)).coerceAtLeast(0))}" else "—:—",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
    }
}

@UnstableApi
@Composable
private fun PlayerTransport(state: PlayerScreenState, model: PlayerViewModel, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = model::shuffle, enabled = state.connected) {
            Icon(Icons.Rounded.Shuffle, if(state.shuffle) "Turn shuffle off" else "Turn shuffle on",
                tint = if(state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        }
        IconButton(onClick = model::previous, enabled = state.connected && state.canPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Previous track", Modifier.size(if (compact) 28.dp else 34.dp), tint = if (state.canPrevious) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .25f))
        }
        IconButton(onClick = model::togglePlayback, enabled = state.connected,
            modifier = Modifier.size(if (compact) 60.dp else 72.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), CircleShape)) {
            if (state.buffering) CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
            else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(if (compact) 36.dp else 48.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = model::next, enabled = state.connected && state.canNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next track", Modifier.size(if (compact) 28.dp else 34.dp), tint = if (state.canNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .25f))
        }
        IconButton(onClick = model::repeat, enabled = state.connected) {
            Icon(if(state.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                when(state.repeat) { 1 -> "Repeat one: switch to off"; 2 -> "Repeat all: switch to repeat one"; else -> "Repeat off: switch to repeat all" },
                tint = if(state.repeat != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        }
    }
}

@UnstableApi
@Composable
internal fun QueueView(state: PlayerScreenState, model: PlayerViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text("THE SOUNDTRACK CONTINUES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Up next", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.headlineLarge)
                Text("${state.queue.size} songs in your queue", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(state.queue) { index, item ->
            val selected = index == state.index
            var menu by remember(item.mediaId, index) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clickable { model.select(index) }.background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.Transparent)
                .padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackCover(item.mediaMetadata.artworkUri, item.mediaMetadata.title.toString(), Modifier.size(54.dp), RoundedCornerShape(8.dp))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(item.mediaMetadata.title?.toString() ?: "Untitled audio", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(item.mediaMetadata.artist?.toString() ?: "Unknown artist", Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) Icon(Icons.Rounded.GraphicEq, "Current track", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                else Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Queue actions for ${item.mediaMetadata.title}") }
                    DropdownMenu(menu, { menu = false }) {
                        if(!selected) DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; model.playNext(index) })
                        if(index > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; model.move(index, index-1) })
                        if(index < state.queue.lastIndex) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; model.move(index, index+1) })
                        DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { menu = false; model.remove(index) })
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
internal fun LyricsView(state: PlayerScreenState, model: PlayerViewModel, import: () -> Unit) {
    val lyrics = state.lyrics
    val item = state.queue.getOrNull(state.index)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackCover(item?.mediaMetadata?.artworkUri, item?.mediaMetadata?.title.toString(), Modifier.size(44.dp), RoundedCornerShape(8.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item?.mediaMetadata?.title?.toString() ?: "Untitled audio", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(item?.mediaMetadata?.artist?.toString() ?: "Unknown artist", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (lyrics?.source == "Attached lyrics file") IconButton(onClick = model::useEmbeddedLyrics) { Icon(Icons.Rounded.Restore, "Use embedded lyrics", tint = MaterialTheme.colorScheme.onSurface) }
            IconButton(onClick = import) { Icon(Icons.Rounded.FolderOpen, "Attach lyrics file", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)) }
        }
        if (lyrics == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.lyricsLoading) CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Lyrics, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                    Text(if (state.lyricsLoading) "Finding the words…" else "Let the music speak.", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (state.lyricsLoading) "Reading embedded lyrics" else "No embedded lyrics found for this song.", Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                    state.lyricsMessage?.let { Text(it, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)) }
                    OutlinedButton(onClick = import, modifier = Modifier.padding(top = 22.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Open lyrics file") }
                }
            }
        } else key(item?.mediaId, System.identityHashCode(lyrics)) {
            val active = lyrics.activeIndex(state.positionMs)
            val list = rememberLazyListState()
            var following by remember { mutableStateOf(true) }
            var lastGesture by remember { mutableLongStateOf(0L) }
            val gesture = remember { object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput) { following = false; lastGesture = android.os.SystemClock.uptimeMillis() }
                    return Offset.Zero
                }
            } }
            LaunchedEffect(lastGesture) {
                if (lastGesture != 0L) { delay(6000); following = true }
            }
            LaunchedEffect(active, following) { if (following && active >= 0) list.animateScrollToItem(active) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (lyrics.timing) { LyricsTiming.WORD -> "WORD SYNC"; LyricsTiming.LINE -> "LINE SYNC"; LyricsTiming.PLAIN -> "PLAIN LYRICS" },
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                if (lyrics.isTimed && !following) TextButton(onClick = { following = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Follow lyrics") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().nestedScroll(gesture), state = list, contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 56.dp, bottom = 180.dp)) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val current = index == active
                    val base by animateColorAsState(if (current || !lyrics.isTimed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = if (index < active) .55f else .46f), tween(260), label = "Lyric emphasis")
                    val text = buildAnnotatedString {
                        if (current && line.words.isNotEmpty()) {
                            val clock = state.positionMs + lyrics.offsetMs
                            line.words.forEachIndexed { wordIndex, word ->
                                val end = word.endTimeMs ?: line.words.getOrNull(wordIndex + 1)?.timeMs ?: line.endTimeMs ?: lyrics.lines.getOrNull(index + 1)?.timeMs ?: Long.MAX_VALUE
                                val color = when { clock >= word.timeMs && clock < end -> MaterialTheme.colorScheme.onSurface; clock >= word.timeMs -> MaterialTheme.colorScheme.onSurface.copy(alpha = .78f); else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .48f) }
                                withStyle(SpanStyle(color = color)) { append(word.text) }
                            }
                        } else append(line.text.ifBlank { "…" })
                    }
                    Text(text, Modifier.fillMaxWidth().clickable(enabled = lyrics.isTimed && state.seekable) { model.seek((line.timeMs - lyrics.offsetMs).coerceAtLeast(0)); following = true }
                        .padding(vertical = 14.dp), fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp, color = base)
                }
            }
        }
        PlayerTransport(state, model, compact = true)
    }
}

internal fun timeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
