package app.auralis.extensions

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.ReVaApplication
import org.json.JSONObject

@Composable
fun CompanionScreen() {
    val manager = (LocalContext.current.applicationContext as ReVaApplication).companions
    val entries by manager.entries.collectAsStateWithLifecycle()
    val events by manager.events.collectAsStateWithLifecycle()
    val running by manager.running.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("All") }
    var diagnostics by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<CompanionEntry?>(null) }
    var allowed by remember { mutableStateOf(emptySet<String>()) }
    var background by remember { mutableStateOf(false) }
    LaunchedEffect(manager) { manager.refresh() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Native extensions", style = MaterialTheme.typography.headlineMedium)
            Text("Add features without loading every extension at startup.", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = manager::refresh) { Icon(Icons.Rounded.Refresh, null); Text("Refresh installed") }
                if (running.isNotEmpty()) TextButton(onClick = manager::cancelAll) { Text("Cancel tasks") }
            }
        }
        item {
            Text("${entries.size} installed · ${entries.count { it.enabled }} enabled · ${running.size} running", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(search, { search = it.take(120) }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
                label = { Text("Search native extensions") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Enabled", "Off", "Running").forEach { value -> FilterChip(status == value, { status = value }, label = { Text(value) }) }
            }
        }
        val visible = entries.filter { entry ->
            (when (status) { "Enabled" -> entry.enabled; "Off" -> !entry.enabled; "Running" -> entry.key in running; else -> true }) &&
                (search.isBlank() || entry.descriptor.name.contains(search, true) || entry.component.packageName.contains(search, true))
        }
        if (entries.isNotEmpty() && visible.isEmpty()) item { Text("No native extensions match your search and filter.") }
        if (entries.isEmpty()) item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Rounded.Extension, null, tint = MaterialTheme.colorScheme.primary)
                    Text("No companion extensions installed", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Text("Install the Track Inspector sample APK from the build artifacts, then refresh. Extensions stay off until you review their access.", Modifier.padding(top = 8.dp))
                }
            }
        }
        items(visible, key = { it.key }) { entry ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.descriptor.name, style = MaterialTheme.typography.titleLarge)
                    Text(entry.component.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (entry.key in running) "Running · 30-second task budget" else if (entry.enabled) "Enabled · starts only when used" else "Off · review access to enable", color = MaterialTheme.colorScheme.primary)
                    Text("${entry.descriptor.actions.size} actions · ${if (entry.background) "background approved" else "foreground processing"}", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { review = entry; allowed = entry.grants; background = entry.background }) { Text("Review access") }
                        if (entry.key in running) TextButton(onClick = { manager.cancel(entry.key) }) { Text("Stop task") }
                        if (entry.enabled) TextButton(onClick = { manager.grant(entry.key, emptySet()) }) { Text("Disable") }
                    }
                }
            }
        }
        item {
            Text("Processing limits", style = MaterialTheme.typography.titleMedium)
            Text("Two tasks at once, one per extension. Tasks stop when ReVa leaves the foreground unless background processing was explicitly approved. Playback continues independently.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
            Text("Android controls process cleanup. These are task and connection limits, not hard CPU or RAM quotas for an entire companion APK.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { TextButton(onClick = { diagnostics = !diagnostics }) { Text("${if (diagnostics) "Hide" else "Show"} diagnostics · ${events.size} events") } }
        if (diagnostics && events.isEmpty()) item { Text("No workers have run in this session.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (diagnostics) items(events.asReversed()) { event ->
            Text("${event.name} · ${event.event}", style = MaterialTheme.typography.bodySmall)
        }
    }
    review?.let { entry ->
        AlertDialog(onDismissRequest = { review = null }, title = { Text("Access for ${entry.descriptor.name}") }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The worker must be isolated. Other components of the installed APK remain governed by Android's own permissions.")
                Text("Publisher SHA-256\n${entry.publisher}", style = MaterialTheme.typography.bodySmall)
                entry.descriptor.capabilities.sorted().forEach { capability ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(capability in allowed, onCheckedChange = { checked ->
                            allowed = if (checked) allowed + capability else allowed - capability
                            if (capability == "compute.background" && !checked) background = false
                        })
                        Text(when (capability) {
                            "ui.actions" -> "Run feature actions (selected track title and artist only)"
                            "network.fetch" -> "Fetch from declared HTTPS domains"
                            "share.text" -> "Offer sharing through your Android chooser"
                            else -> "Allow requesting background processing"
                        })
                    }
                }
                if ("network.fetch" in entry.descriptor.capabilities) Text(entry.descriptor.origins.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                if ("compute.background" in allowed) Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(background, onCheckedChange = { background = it }); Text("Keep this extension's tasks running when I leave ReVa")
                }
                Text("Changing access cancels active tasks. App updates require a new review.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            TextButton(onClick = {
                if (manager.entries.value.any { it.key == entry.key && it.identity == entry.identity }) manager.grant(entry.key, allowed, background)
                review = null
            }, enabled = "ui.actions" in allowed) { Text("Save access") }
        }, dismissButton = { TextButton(onClick = { review = null }) { Text("Cancel") } })
    }
}

/** The same contribution registry is available to stock screens and custom host-rendered layouts. */
@Composable
fun CompanionActions(location: String, input: JSONObject = JSONObject()) {
    val manager = (LocalContext.current.applicationContext as ReVaApplication).companions
    val entries by manager.entries.collectAsStateWithLifecycle()
    val running by manager.running.collectAsStateWithLifecycle()
    val actions = entries.filter { it.enabled }.flatMap { entry ->
        entry.descriptor.actions.filter { it.location == location }.map { entry to it }
    }.take(4)
    if (actions.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { (entry, action) ->
            OutlinedButton(onClick = { manager.invoke(entry.key, action, input) }, enabled = entry.key !in running) {
                Icon(when (action.icon) {
                    "lyrics" -> Icons.Rounded.Lyrics
                    "playlist" -> Icons.AutoMirrored.Rounded.PlaylistPlay
                    "share" -> Icons.Rounded.Share
                    "extension" -> Icons.Rounded.Extension
                    else -> Icons.Rounded.Info
                }, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(action.label)
            }
        }
    }
}

@Composable
fun CompanionPanelDialog() {
    val context = LocalContext.current
    val manager = (context.applicationContext as ReVaApplication).companions
    val panel by manager.panel.collectAsStateWithLifecycle()
    val entries by manager.entries.collectAsStateWithLifecycle()
    panel?.let { value ->
        AlertDialog(onDismissRequest = manager::dismissPanel, title = { Text(value.title) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(value.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(value.text, Modifier.padding(top = 12.dp))
            }
        }, confirmButton = { TextButton(onClick = manager::dismissPanel) { Text("Done") } }, dismissButton = {
            if (value.share && entries.any { it.key == value.sourceKey && it.identity == value.sourceIdentity && it.enabled && "share.text" in it.grants }) {
                TextButton(onClick = {
                    manager.shareIntent()?.let { intent -> runCatching { context.startActivity(Intent.createChooser(intent, "Share extension result")) } }
                }) { Text("Share") }
            }
        })
    }
}
