package app.auralis.library

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.os.Build
import app.auralis.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LibraryIndexStatus(val busy: Boolean = false, val message: String? = null)

class LibraryRepository(private val context: Context) {
    val database = LibraryDatabase(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = Mutex()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private val mutableStatus = MutableStateFlow(LibraryIndexStatus())
    val status = mutableStatus.asStateFlow()
    private var scanJob: Job? = null
    private var enrichJob: Job? = null
    private val fullRequested = java.util.concurrent.atomic.AtomicBoolean(false)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) { changes.trySend(Unit) }
    }
    private val observed = mutableSetOf<String>()

    init {
        scope.launch {
            try {
                writer.withLock {
                    // Upgrade recovery: Android already retained the 0.2 tree grants.
                    context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.forEach { grant ->
                        if (DocumentsContract.isTreeUri(grant.uri)) database.addSource(source(grant.uri))
                        else if (AudioFiles.isAudio(grant.uri.lastPathSegment.orEmpty(), runCatching { context.contentResolver.getType(grant.uri) }.getOrNull())) {
                            runCatching { quickFile(grant.uri) }.getOrNull()?.let { track ->
                                val saved = LibrarySource(grant.uri.toString(), track.title, "file")
                                database.addSource(saved); database.upsert(saved.uri, listOf(track))
                            }
                        }
                    }
                }
                changed()
                observe()
                refresh(full = true)
            } catch (failure: Exception) { mutableStatus.value = LibraryIndexStatus(message = failure.message ?: "Cannot restore saved folders") }
        }
        scope.launch {
            for (ignored in changes) {
                delay(350)
                while (changes.tryReceive().isSuccess) Unit
                refresh()
            }
        }
        scope.launch {
            while (isActive) {
                delay(30_000)
                // SAF providers need a cheap directory check when they do not send changes.
                refresh()
            }
        }
    }

    private fun changed() { mutableRevision.update { it + 1 } }
    private fun source(tree: Uri): LibrarySource {
        val doc = DocumentsContract.getTreeDocumentId(tree)
        val external = tree.authority == "com.android.externalstorage.documents" && ':' in doc
        return LibrarySource(tree.toString(), doc.substringAfter(':').trimEnd('/').substringAfterLast('/').ifBlank { "Music folder" },
            volume = if (external) doc.substringBefore(':') else null, path = if (external) doc.substringAfter(':') else null)
    }
    private fun persist(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    suspend fun addFolder(uri: Uri) = withContext(Dispatchers.IO) {
        try { persist(uri) } catch (_: SecurityException) {
            throw IllegalArgumentException("This provider did not allow a saved folder grant. Choose an on-device music folder.")
        }
        writer.withLock { database.addSource(source(uri)) }
        changed(); observe(); refresh(full = true)
    }
    suspend fun addFiles(uris: List<Uri>) = withContext(Dispatchers.IO) {
        writer.withLock {
            for (uri in uris.distinct()) {
                currentCoroutineContext().ensureActive()
                runCatching { persist(uri) }
                val track = quickFile(uri)
                val source = LibrarySource(uri.toString(), track.title, "file")
                database.addSource(source)
                database.upsert(source.uri, listOf(track))
            }
        }
        changed(); scheduleEnrichment()
    }
    private fun quickFile(uri: Uri): LibraryTrack {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        val (name, size) = cursor?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() to c.getLong(1) else "Local audio" to 0L } ?: ("Local audio" to 0L)
        val key = if (uri.authority == "com.android.externalstorage.documents") {
            val doc = DocumentsContract.getDocumentId(uri)
            LibraryText.storageKey(doc.substringBefore(':'), doc.substringAfter(':'))
        } else "uri:$uri"
        return LibraryTrack(LibraryText.id(key), uri.toString(), name.substringBeforeLast('.', name), size = size)
    }
    suspend fun removeSource(source: LibrarySource) = withContext(Dispatchers.IO) {
        writer.withLock { database.removeSource(source.uri) }
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(source.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        changed(); observe()
    }
    fun cancelScan() { scanJob?.cancel() }
    @Synchronized fun refresh(full: Boolean = false) {
        if (full) fullRequested.set(true)
        if (scanJob?.isActive == true) { changes.trySend(Unit); return }
        scanJob = scope.launch {
            val fullScan = fullRequested.getAndSet(false)
            mutableStatus.value = LibraryIndexStatus(true, "Updating saved music")
            try {
                observe()
                writer.withLock {
                    val sources = database.sources().filter { it.kind == "folder" }
                    val fast = try { MediaIndex(context).read(sources) }
                    catch (_: SecurityException) { emptyMap() }
                    for ((source, tracks) in fast) {
                        val previous = database.sourceSnapshot(source)
                        for (batch in tracks.chunked(250)) {
                            currentCoroutineContext().ensureActive()
                            if (database.upsert(source, batch, previous) > 0) changed()
                        }
                        database.reconcile(source, tracks.mapTo(mutableSetOf()) { it.id }, mediaOnly = true)
                    }
                    var inaccessible = 0
                    for (source in sources.filter { fullScan || it.uri !in fast }) {
                        val indexed = fast[source.uri]?.mapTo(mutableSetOf()) { it.id }.orEmpty()
                        currentCoroutineContext().ensureActive()
                        val scan = FolderScanner(context).scan(Uri.parse(source.uri)) { batch, count ->
                            val unindexed = batch.filter { it.id !in indexed }
                            if (database.upsert(source.uri, unindexed) > 0) changed()
                            mutableStatus.value = LibraryIndexStatus(true, "${source.label} · $count songs found")
                        }
                        if (scan.inaccessibleFolders == 0) database.reconcile(source.uri, scan.tracks.mapTo(mutableSetOf()) { it.id } + indexed)
                        inaccessible += scan.inaccessibleFolders
                    }
                    changed()
                    mutableStatus.value = LibraryIndexStatus(message = if (inaccessible > 0) "$inaccessible folders unavailable. Cached songs were kept; reselect a folder if its permission was revoked." else null)
                }
                scheduleEnrichment()
            } catch (cancelled: CancellationException) {
                mutableStatus.value = LibraryIndexStatus(message = "Update paused. Already indexed songs are saved.")
                throw cancelled
            } catch (failure: Exception) { mutableStatus.value = LibraryIndexStatus(message = "Library update: ${failure.message}") }
        }
    }
    private fun observe() {
        synchronized(observed) {
            val uris = database.sources().filter { it.kind == "folder" }.mapNotNull { Uri.parse(it.uri).authority }.toSet().map { "content://$it" }.toMutableSet()
            if (AudioPermission.granted(context)) {
                uris += MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()
                if (Build.VERSION.SDK_INT >= 29) runCatching { MediaStore.getExternalVolumeNames(context) }.getOrNull()?.forEach {
                    uris += MediaStore.Audio.Media.getContentUri(it).toString()
                }
            }
            uris.filter { it !in observed }.forEach { uri ->
                runCatching { context.contentResolver.registerContentObserver(Uri.parse(uri), true, observer) }.onSuccess { observed.add(uri) }
            }
        }
    }
    @Synchronized private fun scheduleEnrichment() {
        if (enrichJob?.isActive == true) return
        enrichJob = scope.launch {
            var updates = 0
            while (isActive) {
                val batch = database.pending(40)
                if (batch.isEmpty()) break
                for (track in batch) {
                    ensureActive()
                    val retriever = MediaMetadataRetriever()
                    val enriched = try {
                        retriever.setDataSource(context, Uri.parse(track.uri))
                        track.copy(title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: track.title,
                            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: track.artist,
                            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: track.album,
                            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: track.durationMs, enriched = true)
                    } catch (_: Exception) { track.copy(enriched = true) }
                    finally { retriever.release() }
                    writer.withLock { database.enrich(enriched) }
                    if (++updates % 20 == 0) changed()
                }
            }
            if (updates > 0) changed()
        }
    }
    fun favorite(id: String) { scope.launch { writer.withLock { database.toggleFavorite(id) }; changed() } }
    fun clearHistory() { scope.launch { writer.withLock { database.clearHistory() }; changed() } }
    fun markPlayed(id: String) { scope.launch { writer.withLock { database.markPlayed(id) }; changed() } }
    suspend fun close() {
        scope.cancel()
        scope.coroutineContext[Job]?.join()
        context.contentResolver.unregisterContentObserver(observer)
        database.close()
    }
}
