package app.auralis.library

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import app.auralis.model.LibrarySource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MediaIndexTest {
    private class MusicProvider : ContentProvider() {
        var queries = 0
        var newDownload = false
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            queries++
            val columns = projection!!
            val cursor = MatrixCursor(columns)
            val files = listOf(1 to "Music/Love/new.mp3", 2 to "Music/Lovely/other.mp3") +
                if (newDownload) listOf(3 to "Music/Love/downloaded.mp3") else emptyList()
            for ((id, path) in files) {
                val row = mapOf<String, Any>(
                    MediaStore.Audio.Media._ID to id, MediaStore.Audio.Media.TITLE to "New download",
                    MediaStore.Audio.Media.ARTIST to "Artist", MediaStore.Audio.Media.ALBUM to "Album",
                    MediaStore.Audio.Media.DURATION to 123000, MediaStore.Audio.Media.ALBUM_ID to 7,
                    MediaStore.MediaColumns.DISPLAY_NAME to path.substringAfterLast('/'),
                    MediaStore.MediaColumns.DATA to "${Environment.getExternalStorageDirectory().absolutePath}/$path",
                    MediaStore.MediaColumns.DATE_MODIFIED to 1234, MediaStore.MediaColumns.SIZE to 500)
                cursor.addRow(columns.map { row[it] }.toTypedArray())
            }
            return cursor
        }
        override fun getType(uri: Uri) = "audio/mpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }
    @Test fun permissionDeniedDoesNotQueryAndroidsMusicLibrary() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val provider = MusicProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        assertTrue(MediaIndex(context).read(listOf(LibrarySource("tree", "Love", volume = "primary", path = "Music/Love"))).isEmpty())
        assertEquals(0, provider.queries)
    }
    @Test fun indexedMetadataIsReadWithoutOpeningAudioAndRestrictedToChosenFolder() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val provider = MusicProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        val tracks = MediaIndex(context).read(listOf(LibrarySource("tree", "Love", volume = "primary", path = "Music/Love"))).getValue("tree")
        assertEquals(1, tracks.size)
        assertEquals("Artist", tracks.single().artist)
        assertEquals(123000L, tracks.single().durationMs)
        assertEquals(1, provider.queries)
        assertTrue(tracks.single().enriched)
    }
    @Test fun mediaStoreNotificationAddsNewDownloadToSavedFolderWithoutReimport() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        context.deleteDatabase("auralis-library.db")
        val database = LibraryDatabase(context)
        try {
            database.addSource(LibrarySource("content://test/tree/root", "Love", volume = "primary", path = "Music/Love"))
        } finally { database.close() }
        val provider = MusicProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        val repository = LibraryRepository(context)
        try {
            withTimeout(10_000) { while (repository.database.count() < 1) { ShadowLooper.idleMainLooper(); delay(50) } }
            provider.newDownload = true
            context.contentResolver.notifyChange(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null)
            ShadowLooper.idleMainLooper()
            withTimeout(10_000) { while (repository.database.count() < 2) { ShadowLooper.idleMainLooper(); delay(50) } }
            assertEquals(2, repository.database.count())
            assertEquals("Love", repository.database.sources().single().label)
        } finally { repository.close() }
    }
}
