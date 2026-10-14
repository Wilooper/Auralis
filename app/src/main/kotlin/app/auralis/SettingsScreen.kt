package app.auralis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.auralis.model.*

@UnstableApi
@Composable
internal fun SettingsScreen(state: PlayerScreenState, model: PlayerViewModel, home: HomeState, library: LibraryViewModel,
    addFolder: () -> Unit, addFiles: () -> Unit, enableUpdates: () -> Unit, changeFolder: (LibrarySource) -> Unit, openExtensions: () -> Unit) {
    var remove by remember { mutableStateOf<LibrarySource?>(null) }
    var clear by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.clickable(onClick = openExtensions).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .15f)) {
                        Icon(Icons.Rounded.Extension, null, Modifier.padding(13.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Extensions & services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Providers, skins and developer dashboards", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, "Open extension manager", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Column {
            Text("Your music", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Manage the folders and files ReVa scans on this device.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        items(home.sources, key = { it.uri }) { source ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if(source.kind == "folder") Icons.Rounded.FolderOpen else Icons.Rounded.AudioFile, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(source.label, style = MaterialTheme.typography.titleMedium)
                            Text(source.path ?: if(source.kind == "folder") "Saved music folder" else "Selected audio file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if(source.kind == "folder") TextButton(onClick = { changeFolder(source) }, enabled = !home.index.busy) { Text("Change folder") }
                        TextButton(onClick = { remove = source }) { Text("Remove") }
                    }
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(onClick = addFolder, enabled = !home.index.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CircleShape) {
                        Icon(Icons.Rounded.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("Add music folder")
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = addFiles) { Text("Add audio files") }
                        TextButton(onClick = library::refresh, enabled = !home.index.busy) { Text("Refresh") }
                    }
                    if (!home.autoUpdates) OutlinedButton(onClick = enableUpdates, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CircleShape) { Text("Enable new-download detection") }
                    else Text("New-download detection enabled", Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { Text("Playback", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("DJ blend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("A smoother volume transition between songs.", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(state.djMode, model::dj, enabled = state.connected)
                    }
                    Text("Crossfade · ${if(state.crossfadeSeconds == 0) "Off" else "${state.crossfadeSeconds} seconds"}", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleSmall)
                    var fade by remember(state.crossfadeSeconds) { mutableFloatStateOf(state.crossfadeSeconds.toFloat()) }
                    Slider(fade, { fade = it }, onValueChangeFinished = { model.crossfade(fade.toInt()) }, valueRange = 0f..12f, steps = 11, enabled = state.connected)
                    Text("Repeat-one skips crossfade. DJ blend adjusts volume; it does not match beats or tempo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Choose ReVa’s accent color", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        accents.forEachIndexed { index, color -> IconButton(onClick = { model.accent(index) }, modifier = Modifier.size(48.dp).background(color, CircleShape)) {
                            Icon(if(state.accent == index) Icons.Rounded.Check else Icons.Rounded.Palette, "Accent ${index+1}", tint = Color.Black)
                        } }
                    }
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Listening history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("History records songs when playback starts. Favorites stay saved until you remove them.", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { clear = true }, modifier = Modifier.padding(top = 4.dp)) { Text("Clear listening history") }
                }
            }
            Text("ReVa · Extension SDK v1", Modifier.padding(top = 12.dp, bottom = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    remove?.let { source -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove ${source.label}?") }, text = { Text("Remove this source from ReVa. Audio files stay on your device; songs covered by another saved folder stay in the library.") },
        confirmButton = { TextButton(onClick = { library.remove(source); remove = null }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } }) }
    if(clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear listening history?") }, text = { Text("This clears the History tab. Your music and favorites stay saved.") }, confirmButton = { TextButton(onClick = { library.clearHistory(); clear = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}

@Composable
internal fun FlowDialog(state: FlowState, model: FlowViewModel, seed: String?, dismiss: () -> Unit, play: (List<LibraryTrack>) -> Unit) {
    val goal = state.goal
    AlertDialog(onDismissRequest = dismiss, title = { Text("Smart flow") }, text = {
        LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Move gradually through your local music. Energy and tempo estimates guide the order; they do not identify lyrical mood.", style = MaterialTheme.typography.bodyMedium)
                Text("${state.analyzed} / ${state.total} songs analyzed", Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.primary) }
            item {
                if(state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${state.done} checked · ${state.message.orEmpty()}", Modifier.padding(top = 8.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = model::cancel) { Text("Pause analysis") }
                } else OutlinedButton(onClick = model::analyze, modifier = Modifier.fillMaxWidth(), enabled = state.total > 0) { Text("Analyze unprofiled songs") }
            }
            item { FlowGoal.entries.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(goal == option, enabled = !state.busy, onClick = { model.preview(option, seed) })
                    Text(option.label)
                }
            }
                Button(onClick = { model.preview(goal, seed) }, enabled = !state.busy && state.analyzed > 0, modifier = Modifier.fillMaxWidth()) { Text("Preview 25-song flow") }
                if (!state.busy) state.message?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.preview, key = { it.id }) { track ->
                Column { Text(track.title, style = MaterialTheme.typography.titleSmall); Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }, confirmButton = { TextButton(onClick = { play(state.preview) }, enabled = state.preview.isNotEmpty() && !state.busy) { Text("Play flow") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
}
