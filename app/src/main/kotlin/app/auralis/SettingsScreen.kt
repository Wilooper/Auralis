package app.auralis

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.auralis.model.*

@UnstableApi
@Composable
internal fun SettingsScreen(state: PlayerScreenState, model: PlayerViewModel, home: HomeState, library: LibraryViewModel,
    addFolder: () -> Unit, addFiles: () -> Unit, enableUpdates: () -> Unit, changeFolder: (LibrarySource) -> Unit, openExtensions: () -> Unit) {
    var remove by remember { mutableStateOf<LibrarySource?>(null) }
    var clear by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Extensions & internet", style = MaterialTheme.typography.titleLarge)
            Text("Manage providers, custom skins, web players and server-hosted parties.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
            Button(openExtensions, Modifier.fillMaxWidth().padding(top = 12.dp)) { Icon(Icons.Rounded.Extension, null); Spacer(Modifier.width(8.dp)); Text("Open extension manager") }
        }
        item { Text("Your music library", style = MaterialTheme.typography.titleLarge)
            Text("Saved folders update in the background. Changes here do not delete your audio files.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            Button(onClick = addFolder, enabled = !home.index.busy, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                Icon(Icons.Rounded.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("Add music folder")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = addFiles) { Text("Add audio files") }
                TextButton(onClick = library::refresh, enabled = !home.index.busy) { Text("Refresh library") }
            }
            if (!home.autoUpdates) OutlinedButton(onClick = enableUpdates, modifier = Modifier.fillMaxWidth()) { Text("Enable new-download detection") }
            else Text("New-download detection enabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        item { HorizontalDivider(); Text("Playback & transitions", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleLarge) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("DJ blend", style = MaterialTheme.typography.titleMedium)
                    Text("A softer entrance and exit between songs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(state.djMode, model::dj, enabled = state.connected)
            }
            Text("Crossfade · ${if(state.crossfadeSeconds == 0) "Off" else "${state.crossfadeSeconds} seconds"}", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
            var fade by remember(state.crossfadeSeconds) { mutableFloatStateOf(state.crossfadeSeconds.toFloat()) }
            Slider(fade, { fade = it }, onValueChangeFinished = { model.crossfade(fade.toInt()) }, valueRange = 0f..12f, steps = 11, enabled = state.connected)
            Text("Try 6–8 seconds. Repeat-one skips crossfade. DJ blend is a volume transition; it does not match beats or change tempo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { HorizontalDivider(); Text("Appearance", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                accents.forEachIndexed { index, color -> IconButton(onClick = { model.accent(index) }, modifier = Modifier.background(color, CircleShape)) {
                    Icon(if(state.accent == index) Icons.Rounded.Check else Icons.Rounded.Palette, "Accent ${index+1}", tint = Color.Black)
                } }
            }
        }
        item { HorizontalDivider(); Text("Listening history", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleLarge)
            Text("History records songs when playback starts. Favorites stay saved until you remove them.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { clear = true }) { Text("Clear listening history") }
            Text("Auralis · v5 SDK foundation / 0.5.0", Modifier.padding(top = 18.dp, bottom = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    remove?.let { source -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove ${source.label}?") }, text = { Text("Remove this source from Auralis. Audio files stay on your device; songs covered by another saved folder stay in the library.") },
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
