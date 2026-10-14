package app.auralis.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val extensionModes = listOf("Extensions", "Internet", "Party")

@Composable
fun ExtensionScreen(
    entries: List<ExtensionRegistry.Entry>,
    state: ExtensionState,
    model: ExtensionViewModel,
    import: () -> Unit,
    play: (String, List<ExtensionBridge.RemoteTrack>, String, Boolean) -> Unit,
    embed: (String) -> Unit,
) {
    var styleEditor by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<ExtensionRegistry.Entry?>(null) }
    var remove by remember { mutableStateOf<ExtensionRegistry.Entry?>(null) }
    var allowed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var credentials by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Extensions") }
    var session by remember { mutableStateOf("") }
    var track by remember { mutableStateOf("") }
    val enabled = entries.filter { it.enabled }
    val entry = enabled.firstOrNull { it.manifest.id == selected }
    val modeTitle = when (mode) { "Internet" -> "Connected services"; "Party" -> "Listening party"; else -> "Extensions" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "extension-header") {
            Column {
                Text("PERSONALIZE REVA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(modeTitle, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    when (mode) {
                        "Internet" -> "Browse music from the services you enabled. Connections run only when you ask."
                        "Party" -> "Join a server-hosted listening room and build a shared queue."
                        else -> "Add community features, switch skins and control what each extension can access."
                    },
                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    extensionModes.forEach { value ->
                        FilterChip(
                            selected = mode == value,
                            onClick = { mode = value; selected = null; query = "" },
                            label = { Text(value) },
                            leadingIcon = if (mode == value) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            border = null,
                        )
                    }
                }
            }
        }

        item(key = "extension-summary") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile("${entries.size}", "Installed", Icons.Rounded.Extension, Modifier.weight(1f))
                SummaryTile("${enabled.size}", "Enabled", Icons.Rounded.CheckCircle, Modifier.weight(1f))
                SummaryTile("SDK v1", "Sandboxed", Icons.Rounded.Security, Modifier.weight(1f))
            }
        }
        state.message?.let { message -> item(key = "extension-message") { MessageCard(message) } }
        if (state.busy) item(key = "extension-progress") {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = model::cancel) { Text("Cancel request") } }
        }

        if (mode == "Extensions") {
            item(key = "extension-import") {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .15f)) {
                                Icon(Icons.Rounded.Add, null, Modifier.padding(11.dp).size(23.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("Add an extension", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Import a manifest from your device or GitHub", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(import, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.busy, shape = CircleShape) {
                            Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose manifest file")
                        }
                        OutlinedTextField(
                            value = sourceUrl,
                            onValueChange = { sourceUrl = it.take(2048) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("GitHub manifest URL") },
                            placeholder = { Text("github.com/owner/repo/blob/main/manifest.json") },
                            leadingIcon = { Icon(Icons.Rounded.Link, null) },
                            singleLine = true,
                            enabled = !state.busy,
                            shape = RoundedCornerShape(16.dp),
                        )
                        FilledTonalButton(
                            onClick = { model.importUrl(sourceUrl); sourceUrl = "" },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            enabled = !state.busy && sourceUrl.isNotBlank(),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(8.dp)); Text("Import from GitHub")
                        }
                        Text("Imported extensions stay off until you review and grant their requested access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item(key = "extension-appearance") {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.clickable { styleEditor = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Appearance & skins", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Colors, artwork, animation and layout", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, "Customize appearance", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item(key = "installed-title") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your extensions", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${entries.size} / 24", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (entries.isEmpty()) item(key = "extension-empty") { ExtensionEmptyState(import) }
            items(entries, key = { "extension:${it.manifest.id}" }) { row ->
                ExtensionCard(
                    row = row,
                    busy = state.busy,
                    onEnableRequest = { enabledNow ->
                        if (enabledNow) { allowed = row.manifest.capabilities; review = row }
                        else model.toggle(row.manifest.id, false)
                    },
                    onAccess = { allowed = if (row.enabled) row.grants else row.manifest.capabilities; review = row },
                    onCredentials = { credentials = row.manifest.id; token = "" },
                    onRemove = { remove = row },
                    onSeekStyle = { model.chooseSeekBarStyle(row.manifest.id, it) },
                )
            }
            item(key = "extension-safety-note") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, Modifier.padding(top = 2.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Extensions use declared ReVa APIs. They do not install APKs or native code, and do not get local files or device controls. Network requests require an approved server and are user-triggered.",
                        Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val choices = enabled.filter { row ->
                if (mode == "Party") "party.session" in row.grants && row.manifest.party != null
                else ("catalog.read" in row.grants && row.manifest.provider != null) || ("web.embed" in row.grants && row.manifest.web != null)
            }
            if (choices.isEmpty()) item(key = "service-empty") {
                ExtensionEmptyState(onAction = { mode = "Extensions" }, title = if (mode == "Party") "No party extension enabled" else "No services connected", body = if (mode == "Party") "Enable a party extension to host or join a listening room." else "Enable a music provider or sandboxed web player to browse here.", button = "Manage extensions")
            }
            if (choices.isNotEmpty()) item(key = "service-picker") {
                Column {
                    Text(if (mode == "Party") "Choose a party service" else "Choose a music service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { row ->
                            FilterChip(
                                selected = selected == row.manifest.id,
                                onClick = {
                                    selected = row.manifest.id; query = ""
                                    if (row.manifest.provider != null && "catalog.read" in row.grants && mode == "Internet") model.browse(row.manifest.id, "")
                                },
                                label = { Text(row.manifest.name) },
                                leadingIcon = if (selected == row.manifest.id) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }
            if (entry != null && mode == "Internet") {
                if (entry.manifest.web != null && "web.embed" in entry.grants) item(key = "web-dashboard") {
                    val dashboard = entry.manifest.web.optString("role", "player") == "dashboard"
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(if (dashboard) "Developer dashboard" else "Web player", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (dashboard) "Open this extension’s custom controls in its approved sandbox." else "Open the approved web player. Some providers may block embedded playback.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { embed(entry.manifest.id) }, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp), shape = CircleShape) {
                                Icon(Icons.Rounded.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text(if (dashboard) "Open dashboard" else "Open web player")
                            }
                        }
                    }
                }
                if (entry.manifest.provider != null && "catalog.read" in entry.grants) {
                    item(key = "provider-search") {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(16.dp)) {
                                Text(entry.manifest.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Search this provider’s catalog", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(query, { query = it.take(200) }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Song, artist or album") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                                Button({ model.browse(entry.manifest.id, query) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp), shape = CircleShape) {
                                    Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(8.dp)); Text("Search music")
                                }
                            }
                        }
                    }
                    if (state.provider == entry.manifest.id) items(state.tracks, key = { "remote:${it.id}" }) { song ->
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            ListItem(
                                headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { play(entry.manifest.id, state.tracks, song.id, false) }, enabled = "stream.play" in entry.grants) { Icon(Icons.Rounded.PlayArrow, "Play ${song.title}") }
                                        IconButton(onClick = { play(entry.manifest.id, listOf(song), song.id, true) }, enabled = "stream.play" in entry.grants) { Icon(Icons.Rounded.Add, "Queue ${song.title}") }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            )
                        }
                    }
                }
            }
            if (entry != null && mode == "Party") item(key = "party-controls") {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Listening room", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Create a room or join one with its session ID. The server manages the shared queue.", Modifier.padding(top = 4.dp, bottom = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(session, { session = it.take(128) }, Modifier.fillMaxWidth(), label = { Text("Session ID") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                        OutlinedTextField(track, { track = it.take(128) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Track ID to add") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ model.party(entry.manifest.id, "create", "", "") }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !state.busy, shape = CircleShape) { Text("Host") }
                            OutlinedButton({ model.party(entry.manifest.id, "join", session, "") }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !state.busy && session.isNotBlank(), shape = CircleShape) { Text("Join") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton({ model.party(entry.manifest.id, "state", session, "") }, enabled = !state.busy && session.isNotBlank()) { Text("Refresh queue") }
                            TextButton({ model.party(entry.manifest.id, "enqueue", session, track) }, enabled = !state.busy && session.isNotBlank() && track.isNotBlank()) { Text("Add track") }
                            TextButton({ model.party(entry.manifest.id, "leave", session, "") }, enabled = !state.busy && session.isNotBlank()) { Text("Leave") }
                        }
                        if (state.provider == entry.manifest.id) state.party?.let { response ->
                            val id = response.optString("session")
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("Session  ${id.ifBlank { "Not created" }}", style = MaterialTheme.typography.titleSmall)
                            if (id.isNotBlank()) TextButton(onClick = { session = id }) { Text("Use this session") }
                            Text(response.optJSONArray("queue")?.toString()?.take(2000) ?: "The shared queue is empty.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (styleEditor) SkinEditor(app.auralis.LocalSkin.current, { styleEditor = false }) { model.createSkin(it); styleEditor = false }
    review?.let { row ->
        AlertDialog(
            onDismissRequest = { review = null },
            title = { Text("Review ${row.manifest.name}") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose the capabilities this extension may use.", style = MaterialTheme.typography.bodyMedium)
                    row.manifest.capabilities.forEach { cap ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(cap in allowed, { checked -> allowed = if (checked) allowed + cap else allowed - cap })
                            Text(cap, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider()
                    Text("Approved servers", style = MaterialTheme.typography.titleSmall)
                    Text(row.manifest.origins.joinToString("\n").ifBlank { "None · appearance-only extension" }, style = MaterialTheme.typography.bodySmall)
                    Text("Requests are user-triggered. Extensions cannot access local files, microphone, camera or device commands. Turning one off revokes its active access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button({ model.toggle(row.manifest.id, true, allowed); review = null }, enabled = !state.busy && allowed.isNotEmpty(), shape = CircleShape) { Text("Allow & enable") } },
            dismissButton = { TextButton({ review = null }) { Text("Cancel") } },
        )
    }
    remove?.let { row ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("Remove ${row.manifest.name}?") },
            text = { Text("This removes the extension and revokes its access. You can import it again later.") },
            confirmButton = { TextButton({ model.remove(row.manifest.id); remove = null }, enabled = !state.busy) { Text("Remove") } },
            dismissButton = { TextButton({ remove = null }) { Text("Keep it") } },
        )
    }
    credentials?.let { id ->
        AlertDialog(
            onDismissRequest = { credentials = null; token = "" },
            title = { Text("Server token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stored encrypted on this device. Sent only to this extension’s approved server. Saving a token asks you to review its access again.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(token, { token = it.take(4096) }, label = { Text("Bearer token (blank clears)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(16.dp))
                }
            },
            confirmButton = { Button({ model.token(id, token); credentials = null; token = "" }, shape = CircleShape) { Text("Save token") } },
            dismissButton = { TextButton({ credentials = null; token = "" }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SummaryTile(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Text(value, Modifier.padding(start = 7.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ExtensionCard(
    row: ExtensionRegistry.Entry,
    busy: Boolean,
    onEnableRequest: (Boolean) -> Unit,
    onAccess: () -> Unit,
    onCredentials: () -> Unit,
    onRemove: () -> Unit,
    onSeekStyle: (String) -> Unit,
) {
    val skin = row.manifest.skin
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .13f)) {
                    Icon(
                        when {
                            skin != null -> Icons.Rounded.Palette
                            row.manifest.web?.optString("role") == "dashboard" -> Icons.Rounded.DashboardCustomize
                            row.manifest.party != null -> Icons.Rounded.Groups
                            else -> Icons.Rounded.MusicNote
                        }, null, Modifier.padding(11.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(row.manifest.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${row.manifest.id}  ·  v${row.manifest.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = row.enabled, onCheckedChange = onEnableRequest, enabled = !busy)
            }
            Row(Modifier.padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(if (row.enabled) "Enabled" else "Disabled", if (row.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if (skin != null) StatusPill("Skin", MaterialTheme.colorScheme.tertiary)
                if (row.manifest.web?.optString("role") == "dashboard") StatusPill("Dashboard", MaterialTheme.colorScheme.secondary)
                if (row.manifest.provider != null) StatusPill("Music provider", MaterialTheme.colorScheme.secondary)
                if (row.manifest.party != null) StatusPill("Party", MaterialTheme.colorScheme.secondary)
            }
            if (row.enabled && skin != null && "skin.apply" in row.grants && skin.seekBarStyles.size > 1) {
                Text("Seek bar style", Modifier.padding(top = 13.dp), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    skin.seekBarStyles.forEach { style ->
                        FilterChip((row.seekBarStyle ?: skin.seekBarStyles.first()) == style, { onSeekStyle(style) }, label = { Text(style.replaceFirstChar(Char::uppercase)) }, enabled = !busy, shape = CircleShape)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onAccess, enabled = !busy, contentPadding = PaddingValues(horizontal = 10.dp)) { Icon(Icons.Rounded.Security, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Access") }
                if (row.manifest.provider != null || row.manifest.party != null) {
                    TextButton(onCredentials, enabled = !busy, contentPadding = PaddingValues(horizontal = 10.dp)) { Icon(Icons.Rounded.Key, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Token") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onRemove, enabled = !busy, contentPadding = PaddingValues(horizontal = 10.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = .13f)) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun MessageCard(message: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(message, Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun ExtensionEmptyState(
    onAction: () -> Unit,
    title: String = "Start with an extension",
    body: String = "Bring in a community skin, music provider or custom dashboard.",
    button: String = "Choose a manifest",
) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Extension, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onAction, Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp), shape = CircleShape) { Text(button) }
        }
    }
}
