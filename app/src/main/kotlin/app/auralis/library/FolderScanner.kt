package app.auralis.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.auralis.model.AudioFiles
import app.auralis.model.LibraryText
import app.auralis.model.LibraryTrack
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.ArrayDeque

data class FolderScan(val tracks: List<LibraryTrack>, val inaccessibleFolders: Int)

class FolderScanner(private val context: Context) {
    suspend fun scan(tree: Uri, progress: suspend (List<LibraryTrack>, Int) -> Unit): FolderScan {
        val folders = ArrayDeque<String>()
        folders.add(DocumentsContract.getTreeDocumentId(tree))
        val visited = mutableSetOf<String>()
        val files = linkedMapOf<String, LibraryTrack>()
        val batch = mutableListOf<LibraryTrack>()
        var inaccessible = 0
        var scanned = 0
        while (folders.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val id = folders.removeFirst()
            if (!visited.add(id)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            try {
                context.contentResolver.query(children, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ), null, null, null)?.use { cursor ->
                    val documentColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        require(++scanned <= 100_000) { "This folder exceeds the 100,000-entry scan limit. Choose a smaller folder." }
                        val childId = cursor.getString(documentColumn)
                        val name = cursor.getString(nameColumn).orEmpty()
                        val mime = cursor.getString(mimeColumn)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) folders.add(childId)
                        else if (AudioFiles.isAudio(name, mime)) {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId)
                            val key = if (tree.authority == "com.android.externalstorage.documents" && ':' in childId)
                                LibraryText.storageKey(childId.substringBefore(':'), childId.substringAfter(':')) else "uri:$uri"
                            val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                            val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                            val track = LibraryTrack(LibraryText.id(key), uri.toString(), name.substringBeforeLast('.', name),
                                modified = if (modifiedColumn >= 0) cursor.getLong(modifiedColumn) else 0,
                                size = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0)
                            if (files.put(childId, track) == null) batch += track
                            if (batch.size >= 100) { progress(batch.toList(), files.size); batch.clear() }
                        }
                    }
                } ?: run { inaccessible++ }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (limit: IllegalArgumentException) { throw limit }
            catch (_: Exception) { inaccessible++ }
            if (batch.isNotEmpty()) { progress(batch.toList(), files.size); batch.clear() }
        }
        return FolderScan(files.values.toList(), inaccessible)
    }
}
