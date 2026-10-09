package app.auralis

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import app.auralis.extensions.*
import app.auralis.library.AudioPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            val model: PlayerViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val extensions: ExtensionViewModel = viewModel()
            val entries by extensions.entries.collectAsStateWithLifecycle()
            val skin = entries.lastOrNull { it.enabled && "skin.apply" in it.grants && it.manifest.skin != null }?.manifest?.skin
            AuralisTheme(state.accent, skin) { AuralisScreen(state, model) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun AuralisScreen(state: PlayerScreenState, model: PlayerViewModel) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var extensionPage by rememberSaveable { mutableStateOf(false) }
    var webId by rememberSaveable { mutableStateOf<String?>(null) }
    val extensions: ExtensionViewModel = viewModel()
    val extensionRows by extensions.entries.collectAsStateWithLifecycle()
    val extensionState by extensions.state.collectAsStateWithLifecycle()
    val extensionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(extensions::importFile) }
    val webEntry = extensionRows.firstOrNull { it.enabled && "web.embed" in it.grants && it.manifest.id == webId && it.manifest.web != null }
    LaunchedEffect(webEntry, webId) { if(webId != null && webEntry == null) webId = null }
    var flowDialog by rememberSaveable { mutableStateOf(false) }
    val flow: FlowViewModel = viewModel()
    val flowState by flow.state.collectAsStateWithLifecycle()
    val library: LibraryViewModel = viewModel()
    val home by library.home.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, library) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) library.onForeground() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val musicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { library.permissionChanged() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) library.addFiles(uris) }
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importLyrics) }
    var changingFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val old = home.sources.firstOrNull { it.uri == changingFolder }
            if(old != null) library.changeFolder(old, uri) else library.addFolder(uri)
            if (uri.authority == "com.android.externalstorage.documents" && !AudioPermission.granted(context)) musicPermission.launch(AudioPermission.name)
        }
        changingFolder = null
    }
    val immersive = !settings && !extensionPage && (tab == 4 || tab == 3)
    val item = state.queue.getOrNull(state.index)
    BackHandler(enabled = tab != 0 || settings || extensionPage || webId != null) { if(webId != null) webId = null else if(extensionPage) extensionPage = false else if (settings) settings = false else { tab = 0; library.browse(app.auralis.model.LibraryBrowse.SONGS) } }
    ArtworkAtmosphere(if (immersive) item?.mediaMetadata?.artworkUri else null, Modifier.fillMaxSize()) {
        Scaffold(containerColor = if (immersive) Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                if (extensionPage) TopAppBar(title = { Text("Extensions & internet") }, navigationIcon = { IconButton(onClick = { extensionPage = false; webId = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to settings") } })
                else if (settings) TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = { settings = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to music") } })
                else if (immersive) CenterAlignedTopAppBar(
                    title = { Text(if (tab == 3) "LYRICS" else "NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f)) },
                    navigationIcon = { IconButton(onClick = { tab = 0; library.browse(app.auralis.model.LibraryBrowse.SONGS) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Collapse player", Modifier.size(30.dp)) } },
                    actions = { IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.Settings, "Player settings") } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent))
                else TopAppBar(title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Auralis", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }, actions = {
                    IconButton(onClick = { flow.reload(); flowDialog = true }) { Icon(Icons.Rounded.AutoAwesome, "Create a gradual playlist") }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.Settings, "Appearance and playback settings") }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            }, bottomBar = {
                if (settings || extensionPage) Unit
                else if (immersive) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.weight(1f), shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                TextButton(onClick = { tab = 4 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = tab == 4 }, colors = ButtonDefaults.textButtonColors(contentColor = if (tab == 4) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))) {
                                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Music")
                                }
                                TextButton(onClick = { tab = 3 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = tab == 3 }, colors = ButtonDefaults.textButtonColors(contentColor = if (tab == 3) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))) {
                                    Icon(Icons.Rounded.Lyrics, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Lyrics")
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        IconButton(onClick = { tab = 2 }) { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, "Open play queue", tint = MaterialTheme.colorScheme.onSurface) }
                    }
                } else Column {
                    if (item != null) MiniPlayer(state, model, open = { tab = 4 })
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        listOf("Home", "Favorites", "Queue", "Lyrics").forEachIndexed { index, label ->
                            val icon = when (index) { 0 -> Icons.Rounded.Home; 1 -> Icons.Rounded.Favorite; 2 -> Icons.AutoMirrored.Rounded.PlaylistPlay; else -> Icons.Rounded.Lyrics }
                            NavigationBarItem(selected = tab == index, onClick = { tab = index; if(index == 0) library.browse(app.auralis.model.LibraryBrowse.SONGS) else if(index == 1) library.browse(app.auralis.model.LibraryBrowse.FAVORITES) }, icon = { Icon(icon, null, Modifier.size(23.dp)) }, label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                state.error?.let { message -> StatusMessage(message, model::dismissError, error = true) }
                home.error?.let { message -> StatusMessage(message, library::dismissError, error = true) }
                if (home.index.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(home.index.message ?: "Updating your collection", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        TextButton(onClick = library::cancelScan) { Text("Pause") }
                    }
                } else home.index.message?.let { StatusMessage(it) }
                when {
                    extensionPage && webEntry != null -> WebEmbedScreen(webEntry) { webId = null }
                    extensionPage -> ExtensionScreen(extensionRows, extensionState, extensions, { extensionPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, model::playRemote, { id -> if(state.playing) model.togglePlayback(); webId = id })
                    settings -> SettingsScreen(state, model, home, library, { changingFolder = null; folderPicker.launch(null) }, { audioPicker.launch(arrayOf("*/*")) }, { musicPermission.launch(AudioPermission.name) }, { source -> changingFolder = source.uri; folderPicker.launch(android.net.Uri.parse(source.uri)) }, { extensionPage = true })
                    tab == 0 || tab == 1 -> HomeScreen(home, library, state.connected, { tracks, id ->
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        model.playLibrary(tracks, id)
                    }, { settings = true }, library::favorite, tab == 1)
                    !state.connected -> EmptyView("Connecting to playback", loading = state.error == null)
                    item == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                            Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Find your soundtrack", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.headlineSmall)
                            Text("Choose a song from your collection to begin.", Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { tab = 0 }, shape = CircleShape) { Text("Browse music") }
                        }
                    }
                    tab == 4 -> NowPlaying(state, model, item.mediaId in home.favoriteIds) { library.favorite(item.mediaId) }
                    tab == 2 -> QueueView(state, model)
                    else -> LyricsView(state, model) { lyricsPicker.launch(arrayOf("*/*")) }
                }
            }
        }
    }
    if (flowDialog) FlowDialog(flowState, flow, item?.mediaId, { flowDialog = false }) { tracks ->
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        model.playFlow(tracks)
        flowDialog = false
    }

}

@Composable
private fun StatusMessage(message: String, dismiss: (() -> Unit)? = null, error: Boolean = false) {
    Row(Modifier.fillMaxWidth().background(if (error) MaterialTheme.colorScheme.errorContainer else Color.Transparent).padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        if (dismiss != null) IconButton(onClick = dismiss) { Icon(Icons.Rounded.Close, "Dismiss message") }
    }
}

@Composable
private fun EmptyView(label: String, loading: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)) }
            Text(label)
        }
    }
}
