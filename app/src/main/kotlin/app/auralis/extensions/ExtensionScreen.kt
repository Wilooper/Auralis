package app.auralis.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ExtensionScreen(entries: List<ExtensionRegistry.Entry>, state: ExtensionState, model: ExtensionViewModel, import: () -> Unit,
    play: (String, List<ExtensionBridge.RemoteTrack>, String, Boolean) -> Unit, embed: (String) -> Unit) {
    var styleEditor by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<ExtensionRegistry.Entry?>(null) }
    var allowed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var credentials by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Extensions") }
    var session by remember { mutableStateOf("") }
    var track by remember { mutableStateOf("") }
    val enabled = entries.filter { it.enabled }
    val entry = enabled.firstOrNull { it.manifest.id == selected }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Make Auralis yours", style = MaterialTheme.typography.headlineSmall)
            Text("File-based extensions · SDK v1", color = MaterialTheme.colorScheme.primary)
            Text("Import a .auralis.json file, review its access, then turn it on. Extensions use Auralis APIs; they cannot install code or access your files.", Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Extensions", "Internet", "Party").forEach { FilterChip(mode == it, { mode = it; selected = null }, label = { Text(it) }) }
            }
            state.message?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }
            if(state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = model::cancel) { Text("Cancel request") } }
        }
        if(mode == "Extensions") {
            item { Button(import, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Import extension file") }
                OutlinedButton({ styleEditor = true }, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Customize appearance") }
                Text("Updates replace the same ID and disable it for a fresh review. Maximum 24 installed extensions.", style = MaterialTheme.typography.bodySmall) }
            if(entries.isEmpty()) item { Text("Your extension collection starts here. Try a community provider or a custom skin.") }
            items(entries, key = { it.manifest.id }) { row ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row { Column(Modifier.weight(1f)) { Text(row.manifest.name, style = MaterialTheme.typography.titleMedium); Text("${row.manifest.id} · ${row.manifest.version}", style = MaterialTheme.typography.bodySmall) }
                            Switch(row.enabled, { if(it) { allowed = row.manifest.capabilities; review = row } else model.toggle(row.manifest.id, false) }, enabled = !state.busy) }
                        Text(row.manifest.capabilities.joinToString(" · "), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton({ allowed = if(row.enabled) row.grants else row.manifest.capabilities; review = row }) { Text("Access") }
                            if(row.manifest.provider != null || row.manifest.party != null) TextButton({ credentials = row.manifest.id; token = "" }) { Text("Server token") }
                            TextButton({ model.remove(row.manifest.id) }, enabled = !state.busy) { Text("Remove") }
                        }
                    }
                }
            }
            item { Text("Skins control colors, typography, cover size, rows, corners and library layout. The last enabled skin wins; disabling it restores the previous style.", style = MaterialTheme.typography.bodySmall)
                Text("No background polling, downloaded scripts, APKs, native plugins, or store downloads in SDK v1.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }
        } else {
            val choices = enabled.filter { if(mode == "Party") "party.session" in it.grants && it.manifest.party != null else ("catalog.read" in it.grants && it.manifest.provider != null) || ("web.embed" in it.grants && it.manifest.web != null) }
            if(choices.isEmpty()) item { Text("Enable a ${if(mode == "Party") "party" else "provider or web embed"} extension first.") }
            items(choices, key = { it.manifest.id }) { row -> FilterChip(selected == row.manifest.id, { selected = row.manifest.id; query = ""; if(row.manifest.provider != null && "catalog.read" in row.grants && mode == "Internet") model.browse(selected!!, "") }, label = { Text(row.manifest.name) }) }
            if(entry != null && mode == "Internet") {
                if(entry.manifest.web != null && "web.embed" in entry.grants) item { OutlinedButton({ embed(entry.manifest.id) }, Modifier.fillMaxWidth()) { Text("Open sandboxed web player") }
                    Text("Web playback has separate controls. The provider may block iframes. Close it before returning to native playback.", style = MaterialTheme.typography.bodySmall) }
                if(entry.manifest.provider != null && "catalog.read" in entry.grants) {
                    item { OutlinedTextField(query, { query = it.take(200) }, label = { Text("Search your server") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button({ model.browse(entry.manifest.id, query) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Fetch songs") } }
                    if(state.provider == entry.manifest.id) items(state.tracks, key = { it.id }) { song ->
                        ListItem(headlineContent = { Text(song.title) }, supportingContent = { Text(song.artist) }, trailingContent = {
                            Row { TextButton({ play(entry.manifest.id, state.tracks, song.id, false) }, enabled = "stream.play" in entry.grants) { Text("Play") }
                                TextButton({ play(entry.manifest.id, listOf(song), song.id, true) }, enabled = "stream.play" in entry.grants) { Text("+ Queue") } }
                        })
                    }
                }
            }
            if(entry != null && mode == "Party") {
                item {
                    Text("Server-hosted listening party", style = MaterialTheme.typography.titleMedium)
                    Text("Create or join a shared queue on your server. SDK v1 does not synchronize device clocks or broadcast local audio.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(session, { session = it.take(128) }, label = { Text("Session ID") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(track, { track = it.take(128) }, label = { Text("Server track ID to enqueue") }, modifier = Modifier.fillMaxWidth())
                    Row { TextButton({ model.party(entry.manifest.id,"create","","") }, enabled = !state.busy) { Text("Host") }
                        TextButton({ model.party(entry.manifest.id,"join",session,"") }, enabled = !state.busy && session.isNotBlank()) { Text("Join") }
                        TextButton({ model.party(entry.manifest.id,"state",session,"") }, enabled = !state.busy && session.isNotBlank()) { Text("Refresh") } }
                    Row { TextButton({ model.party(entry.manifest.id,"enqueue",session,track) }, enabled = !state.busy && session.isNotBlank() && track.isNotBlank()) { Text("Add song") }
                        TextButton({ model.party(entry.manifest.id,"leave",session,"") }, enabled = !state.busy && session.isNotBlank()) { Text("Leave") } }
                }
                if(state.provider == entry.manifest.id) state.party?.let { response -> item {
                    val id = response.optString("session")
                    Text("Session: $id", style = MaterialTheme.typography.titleMedium)
                    if(id.isNotBlank()) TextButton({ session = id }) { Text("Use this session") }
                    Text(response.optJSONArray("queue")?.toString()?.take(2000) ?: "No shared queue", style = MaterialTheme.typography.bodySmall)
                } }
            }
        }
    }
    if(styleEditor) SkinEditor(app.auralis.LocalSkin.current, { styleEditor = false }) { model.createSkin(it); styleEditor = false }
    review?.let { row -> AlertDialog(onDismissRequest = { review = null }, title = { Text("Allow ${row.manifest.name}?") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) { Text("Requested channels:"); row.manifest.capabilities.forEach { cap -> Row { Checkbox(cap in allowed, { checked -> allowed = if(checked) allowed + cap else allowed - cap }); Text(cap, Modifier.padding(top = 12.dp)) } }; Spacer(Modifier.height(12.dp)); Text("Approved servers:"); Text(row.manifest.origins.joinToString("\n").ifBlank { "None · offline appearance only" }); Spacer(Modifier.height(12.dp)); Text("Only user-triggered requests. No access to local files, microphone, camera, or device commands. Disabling revokes active bridge access.", style = MaterialTheme.typography.bodySmall) }
    }, confirmButton = { TextButton({ model.toggle(row.manifest.id,true, allowed); review = null }, enabled = !state.busy && allowed.isNotEmpty()) { Text("Allow & enable") } }, dismissButton = { TextButton({ review = null }) { Text("Cancel") } }) }
    credentials?.let { id -> AlertDialog(onDismissRequest = { credentials = null; token = "" }, title = { Text("Server bearer token") }, text = {
        Column { Text("Stored encrypted on this device. Sent only to the catalog or party server origin. Changing it disables this extension."); OutlinedTextField(token, { token = it.take(4096) }, label = { Text("Token (blank clears)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }
    }, confirmButton = { TextButton({ model.token(id,token); credentials = null; token = "" }) { Text("Save") } }, dismissButton = { TextButton({ credentials = null; token = "" }) { Text("Cancel") } }) }
}
