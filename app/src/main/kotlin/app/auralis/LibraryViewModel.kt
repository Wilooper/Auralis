package app.auralis

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.auralis.library.AudioPermission
import app.auralis.library.LibraryIndexStatus
import app.auralis.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class HomeState(
    val loading: Boolean = true,
    val total: Int = 0,
    val query: String = "",
    val browse: LibraryBrowse = LibraryBrowse.SONGS,
    val artist: String? = null,
    val album: String? = null,
    val tracks: List<LibraryTrack> = emptyList(),
    val groups: List<LibraryGroup> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val recent: List<LibraryTrack> = emptyList(),
    val played: List<LibraryTrack> = emptyList(),
    val sources: List<LibrarySource> = emptyList(),
    val index: LibraryIndexStatus = LibraryIndexStatus(),
    val autoUpdates: Boolean = false,
    val error: String? = null,
    val limit: Int = 500,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AuralisApplication).library
    private val controls = MutableStateFlow(HomeState())
    private val mutableHome = MutableStateFlow(HomeState())
    val home = mutableHome.asStateFlow()
    init {
        viewModelScope.launch {
            combine(repository.revision, controls) { _, control -> control }.collectLatest { control ->
                val result = withContext(Dispatchers.IO) {
                    val db = repository.database
                    control.copy(loading = false, total = db.count(), sources = db.sources(), autoUpdates = AudioPermission.granted(getApplication()),
                        tracks = if (control.browse == LibraryBrowse.SONGS) db.search(control.query, control.artist, control.album, control.limit) else if (control.browse !in listOf(LibraryBrowse.ARTISTS, LibraryBrowse.ALBUMS)) db.browse(control.query, control.browse, control.limit) else emptyList(),
                        groups = if (control.browse in listOf(LibraryBrowse.ARTISTS, LibraryBrowse.ALBUMS)) db.groups(control.query, control.browse, control.limit) else emptyList(),
                        recent = db.recent(), played = db.played(), favoriteIds = db.favoriteIds())
                }
                mutableHome.value = result.copy(index = repository.status.value, query = mutableHome.value.query)
            }
        }
        viewModelScope.launch { repository.status.collect { mutableHome.value = mutableHome.value.copy(index = it) } }
    }
    private var searchJob: Job? = null
    fun search(query: String) {
        mutableHome.value = mutableHome.value.copy(query = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(120); controls.value = controls.value.copy(query = query, limit = 500) }
    }
    fun browse(browse: LibraryBrowse) { controls.value = controls.value.copy(browse = browse, artist = null, album = null, limit = 500) }
    fun openGroup(group: LibraryGroup) {
        controls.value = controls.value.copy(browse = LibraryBrowse.SONGS, artist = group.artist ?: group.name,
            album = if (group.artist != null) group.name else null, limit = 500)
    }
    fun clearGroup() { controls.value = controls.value.copy(artist = null, album = null, limit = 500) }
    fun more() { controls.value = controls.value.copy(limit = (controls.value.limit + 500).coerceAtMost(100_000)) }
    fun favorite(id: String) { repository.favorite(id) }
    fun clearHistory() { repository.clearHistory() }
    fun refresh() { repository.refresh(full = true) }
    fun onForeground() { repository.refresh() }
    fun permissionChanged() { controls.value = controls.value.copy(autoUpdates = AudioPermission.granted(getApplication())); repository.refresh() }
    fun cancelScan() { repository.cancelScan() }
    fun addFolder(uri: Uri) = operation { repository.addFolder(uri) }
    fun changeFolder(old: LibrarySource, uri: Uri) = operation { repository.addFolder(uri); if (old.uri != uri.toString()) repository.removeSource(old) }
    fun addFiles(uris: List<Uri>) = operation { repository.addFiles(uris) }
    fun remove(source: LibrarySource) = operation { repository.removeSource(source) }
    fun dismissError() { controls.value = controls.value.copy(error = null); mutableHome.value = mutableHome.value.copy(error = null) }
    private fun operation(action: suspend () -> Unit) { viewModelScope.launch {
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { controls.value = controls.value.copy(error = failure.message ?: "Library action failed") }
    } }
}
