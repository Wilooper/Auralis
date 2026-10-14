package app.auralis

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.auralis.library.artworkUri
import app.auralis.model.*
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
internal fun TrackCover(uri: Uri?, title: String, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp), animate: Boolean = false) {
    val context = LocalContext.current
    val skin = LocalSkin.current
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val colors = remember(title, primary, surface) { listOf(primary.copy(alpha = .18f), surface, surface) }
    val motionAllowed = animate && skin.coverMotion != "none" && android.animation.ValueAnimator.areAnimatorsEnabled()
    val scale = if(motionAllowed && skin.coverMotion == "pulse") {
        rememberInfiniteTransition(label = "cover-pulse").animateFloat(1f, 1.035f,
            infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "cover-scale").value
    } else 1f
    val rotation = if(motionAllowed && skin.coverMotion == "turntable") {
        rememberInfiniteTransition(label = "cover-turntable").animateFloat(0f, 360f,
            infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "cover-rotation").value
    } else 0f
    BoxWithConstraints(modifier.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = rotation }.clip(shape).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        val target = if (constraints.hasBoundedWidth) with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.roundToPx().coerceIn(64, 2048) } else 128
        val request = remember(uri, target, skin.artworkStyle, skin.primary, skin.background) {
            ImageRequest.Builder(context).data(uri).size(target).apply {
                if (skin.artworkStyle != "image") {
                    allowHardware(false)
                    transformations(ArtworkTreatment(skin.artworkStyle, android.graphics.Color.parseColor(skin.primary), android.graphics.Color.parseColor(skin.background)))
                }
            }.build()
        }
        Icon(Icons.Rounded.Album, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (uri != null) AsyncImage(request, "Artwork for $title", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun HomeScreen(home: HomeState, library: LibraryViewModel, canPlay: Boolean,
    onPlay: (List<LibraryTrack>, String) -> Unit, openSettings: () -> Unit, favorite: (String) -> Unit, favoritesOnly: Boolean = false) {
    val context = LocalContext.current
    val openGroup = home.artist != null || home.album != null
    val skin = LocalSkin.current
    val discover = skin.layout != "compact" && !favoritesOnly && home.query.isBlank() && !openGroup && home.browse == LibraryBrowse.SONGS
    val featured = home.played.firstOrNull() ?: home.recent.firstOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "companion-library-actions") { Column(Modifier.padding(horizontal = 20.dp)) { app.auralis.extensions.CompanionActions("library") } }
        item(key = "welcome") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(if (favoritesOnly) "YOUR PERSONAL MIX" else when (java.time.LocalTime.now().hour) {
                    in 5..11 -> "GOOD MORNING"; in 12..16 -> "GOOD AFTERNOON"; in 17..21 -> "GOOD EVENING"; else -> "GOOD TO HAVE YOU BACK"
                }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(if (favoritesOnly) "Your favorites" else "Your music, in one place.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(if (home.loading) "Getting your library ready" else "${home.total} ${if (home.total == 1) "song" else "songs"} in your library",
                    Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(home.query, library::search, Modifier.fillMaxWidth().padding(top = 18.dp),
                    placeholder = { Text("What do you want to play?") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(22.dp)) },
                    trailingIcon = { if (home.query.isNotEmpty()) IconButton(onClick = { library.search("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
                    shape = CircleShape, colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .7f)))
                if (!favoritesOnly) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryBrowse.entries.filter { it != LibraryBrowse.FAVORITES }.forEach { browse ->
                        val selected = home.browse == browse
                        FilterChip(selected, { library.browse(browse) }, label = { Text(if (browse == LibraryBrowse.RECENT) "Recently added" else browse.name.lowercase().replaceFirstChar { it.titlecase() }) },
                            shape = CircleShape, border = null, colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                    }
                }
            }
        }
        if (home.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(22.dp)) }
        if (!home.loading && home.total == 0) item(key = "empty") {
            Surface(Modifier.padding(22.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("A home for every song.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
                    Text("Choose a folder once. Your collection will be here whenever you return.", Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = openSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CircleShape) { Text("Set up library in Settings") }
                }
            }
        }
        if (discover && featured != null) item(key = "featured") {
            Surface(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .18f), MaterialTheme.colorScheme.surfaceContainerHigh))).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 14.dp)) {
                        Text(if (home.played.isEmpty()) "PICK UP SOMETHING NEW" else "PICK UP WHERE YOU LEFT OFF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(featured.title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(featured.artist, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        FilledTonalButton(onClick = { onPlay(if (home.played.isEmpty()) home.recent else home.played, featured.id) },
                            enabled = canPlay, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp), shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("Play now")
                        }
                    }
                    TrackCover(featured.artworkUri(context), featured.title, Modifier.size(112.dp), RoundedCornerShape(16.dp))
                }
            }
        }
        if (discover && home.played.isNotEmpty()) item(key = "quick-access-title") {
            Text("Recently played", Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (discover && home.played.isNotEmpty()) items(home.played.take(4).chunked(2), key = { "quick:${it.first().id}" }) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { track ->
                    Surface(Modifier.weight(1f).heightIn(min = 64.dp).clickable(enabled = canPlay) { onPlay(home.played, track.id) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrackCover(track.artworkUri(context), track.title, Modifier.size(60.dp), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                            Text(track.title, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (discover && home.played.isNotEmpty()) item(key = "played") {
            CoverShelf("Made for another listen", "From your listening history", home.played, canPlay, onPlay)
        }
        if (discover && home.recent.isNotEmpty()) item(key = "recent") {
            CoverShelf("Freshly added", "New in your library", home.recent, canPlay, onPlay)
        }
        if (openGroup) item(key = "breadcrumb") {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = library::clearGroup) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "All songs") }
                Text(home.album ?: home.artist.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            }
        }
        if (home.total > 0) item(key = "results") {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (home.query.isNotBlank()) "Search results" else if (openGroup) "Tracks" else when (home.browse) {
                        LibraryBrowse.SONGS -> "All songs"; LibraryBrowse.ARTISTS -> "Artists"; LibraryBrowse.ALBUMS -> "Albums"
                        LibraryBrowse.HISTORY -> "Recently played"; LibraryBrowse.RECENT -> "Recently added"; LibraryBrowse.FAVORITES -> "Liked songs"
                    }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (discover) Text("Browse your whole collection", Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }

            }
        }
        if (home.browse !in listOf(LibraryBrowse.ARTISTS, LibraryBrowse.ALBUMS)) {
            if(skin.layout == "covers") {
                items(home.tracks.chunked(2), key = { "covers:${it.first().id}" }) { pair ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        pair.forEach { track -> Column(Modifier.weight(1f)) {
                            TrackCover(track.artworkUri(context), track.title, Modifier.fillMaxWidth().aspectRatio(1f).clickable(enabled = canPlay) { onPlay(home.tracks, track.id) }, MaterialTheme.shapes.large)
                            Text(track.title, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                            Text(track.artist, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton({ favorite(track.id) }) { Text(if(track.id in home.favoriteIds) "Favorited" else "Favorite") }
                        } }
                        if(pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else items(home.tracks, key = { "song:${it.id}" }) { track -> HomeTrackRow(track, canPlay, track.id in home.favoriteIds, { favorite(track.id) }) { onPlay(home.tracks, track.id) } }
        } else {
            items(home.groups.chunked(2), key = { "groups:${it.first().name}:${it.first().artist}" }) { pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { group ->
                        Column(Modifier.weight(1f).clickable { library.openGroup(group) }) {
                            TrackCover(group.track.artworkUri(context), group.name, Modifier.fillMaxWidth().aspectRatio(1f),
                                if (home.browse == LibraryBrowse.ARTISTS) CircleShape else RoundedCornerShape(12.dp))
                            Text(group.name, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(group.artist ?: "${group.count} songs", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (home.total > 0 && home.tracks.isEmpty() && home.groups.isEmpty() && !home.loading) item {
            Column(Modifier.padding(24.dp)) {
                Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
                Text("Try another song title, artist or album.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (maxOf(home.tracks.size, home.groups.size) >= home.limit) item {
            TextButton(onClick = library::more, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Load more") }
        }
    }
}

@Composable
private fun CoverShelf(title: String, subtitle: String, tracks: List<LibraryTrack>, enabled: Boolean, play: (List<LibraryTrack>, String) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.padding(top = 20.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, Modifier.padding(start = 20.dp, top = 4.dp, bottom = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(tracks, key = { it.id }) { track ->
                Column(Modifier.width(148.dp).clickable(enabled = enabled) { play(tracks, track.id) }) {
                    TrackCover(track.artworkUri(context), track.title, Modifier.size(148.dp), RoundedCornerShape(14.dp))
                    Text(track.title, Modifier.padding(top = 9.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(track.artist, Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HomeTrackRow(track: LibraryTrack, enabled: Boolean, loved: Boolean, favorite: () -> Unit, play: () -> Unit) {
    val context = LocalContext.current
    val skin = LocalSkin.current
    Row(Modifier.fillMaxWidth().heightIn(min = maxOf(68, skin.rowHeight).dp).clickable(enabled = enabled, onClick = play).padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        TrackCover(track.artworkUri(context), track.title, Modifier.size(skin.artworkSize.dp), RoundedCornerShape(maxOf(10, skin.cornerRadius - 4).dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            Text(track.artist, Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = favorite) { Icon(if (loved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if(loved) "Remove ${track.title} from favorites" else "Favorite ${track.title}", tint = if(loved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
