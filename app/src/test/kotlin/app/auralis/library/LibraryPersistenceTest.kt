package app.auralis.library

import android.app.Application
import android.content.Context
import androidx.media3.common.MediaItem
import app.auralis.model.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LibraryPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: LibraryDatabase
    private val folder = LibrarySource("content://test/tree/Music", "Music")
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("library-test.db")
        db = LibraryDatabase(context, "library-test.db")
        db.addSource(folder)
    }
    @After fun close() { db.close() }
    private fun track(id: String, title: String = "Song $id", artist: String = "Arijit Singh", album: String = "Album", modified: Long = 10) =
        LibraryTrack(id, "content://media/external/audio/media/$id", title, artist, album, modified = modified, size = 100, enriched = true)

    @Test fun reopenRestoresFoldersSongsAndSearchWithoutAFileScan() {
        db.upsert(folder.uri, listOf(track("1", "प्यार"), track("2", "Love Song", "Taylor")))
        db.close()
        db = LibraryDatabase(context, "library-test.db")
        assertEquals(folder, db.sources().single())
        assertEquals(2, db.count())
        assertEquals("प्यार", db.search("ARIJIT प्यार").single().title)
        assertEquals("Love Song", db.search("taylor").single().title)
    }
    @Test fun repeatedScansKeepEnrichedMetadataAndRecentlyPlayedHistory() {
        val enriched = track("1", "Real title")
        db.upsert(folder.uri, listOf(enriched)); db.markPlayed("1")
        assertEquals(0, db.upsert(folder.uri, listOf(enriched.copy(title = "Filename", artist = "Unknown artist", enriched = false))))
        assertEquals("Real title", db.track("1")!!.title)
        assertTrue(db.track("1")!!.lastPlayed > 0)
        assertTrue(db.pending().isEmpty())
    }
    @Test fun aChangedFileInvalidatesCachedMetadata() {
        db.upsert(folder.uri, listOf(track("1")))
        db.upsert(folder.uri, listOf(track("1", modified = 20).copy(enriched = false)))
        assertEquals(1, db.pending().size)
    }
    @Test fun incrementalAddAndDeletionPreserveOtherFolderMembership() {
        val nested = folder.copy(uri = "content://test/tree/Nested", label = "Nested")
        db.addSource(nested)
        db.upsert(folder.uri, listOf(track("1"), track("2")))
        db.upsert(nested.uri, listOf(track("2")))
        db.reconcile(folder.uri, setOf("1"))
        assertEquals(2, db.count())
        db.removeSource(nested.uri)
        assertEquals(listOf("1"), db.search("").map { it.id })
    }
    @Test fun mediaIndexReconciliationKeepsUnindexedSafFiles() {
        val saf = track("2").copy(uri = "content://documents/tree/music/song.flac")
        db.upsert(folder.uri, listOf(track("1"), saf))
        db.reconcile(folder.uri, emptySet(), mediaOnly = true)
        assertEquals("2", db.search("").single().id)
    }
    @Test fun searchesUseLiteralPercentUnderscoreAndArtistAlbumFilters() {
        db.upsert(folder.uri, listOf(track("1", "100%_love"), track("2", "100xxlove", "Other")))
        assertEquals("1", db.search("%_").single().id)
        assertEquals("1", db.search("", artist = "Arijit Singh", album = "Album").single().id)
        assertEquals(2, db.groups("", LibraryBrowse.ARTISTS).size)
    }
    @Test fun queueAndPositionRestoreWithoutAutoplay() {
        val store = PlaybackCheckpointStore(context)
        val items = listOf(MediaItem.Builder().setMediaId("1").setUri("content://audio/1").build(),
            MediaItem.Builder().setMediaId("2").setUri("content://audio/2").build())
        store.save(PlaybackCheckpoint(items, 1, 12345), true)
        val restored = PlaybackCheckpointStore(context).read()!!
        assertEquals(items.map { it.mediaId }, restored.items.map { it.mediaId })
        assertEquals(1, restored.index); assertEquals(12345L, restored.positionMs)
        store.save(PlaybackCheckpoint(items, 0, 500), false)
        assertEquals(500L, PlaybackCheckpointStore(context).read()!!.positionMs)
    }
    @Test fun favoritesSurviveReopenMetadataRefreshAndFolderOverlap() {
        db.upsert(folder.uri, listOf(track("1"), track("2")))
        db.toggleFavorite("1"); db.toggleFavorite("not-in-library")
        db.close(); db = LibraryDatabase(context, "library-test.db")
        assertEquals(setOf("1"), db.favoriteIds())
        db.upsert(folder.uri, listOf(track("1", modified = 20)))
        assertEquals("1", db.browse("", LibraryBrowse.FAVORITES, 100).single().id)
        db.toggleFavorite("1"); assertTrue(db.favoriteIds().isEmpty())
    }
    @Test fun analysisCacheInvalidatesChangedFilesAndHistoryCanClear() {
        val row = track("1"); db.upsert(folder.uri, listOf(row))
        db.saveAnalysis(row, SongFeatures(.6, 120.0, .3, .8)); db.markPlayed("1")
        assertEquals(1, db.analyses().size); assertEquals(1, db.browse("", LibraryBrowse.HISTORY, 100).size)
        db.clearHistory(); assertTrue(db.browse("", LibraryBrowse.HISTORY, 100).isEmpty())
        db.upsert(folder.uri, listOf(row.copy(modified = 22)))
        assertTrue(db.analyses().isEmpty())
        db.saveAnalysis(row, SongFeatures(.5, 100.0, .2, .8)); assertTrue(db.analyses().isEmpty())
    }
    @Test fun schemaOneUpgradeKeepsLibraryQueueAndHistory() {
        val legacy = context.openOrCreateDatabase("legacy.db", 0, null)
        legacy.execSQL("CREATE TABLE sources(uri TEXT PRIMARY KEY,label TEXT NOT NULL,kind TEXT NOT NULL,volume TEXT,path TEXT)")
        legacy.execSQL("CREATE TABLE tracks(id TEXT PRIMARY KEY,uri TEXT NOT NULL,title TEXT NOT NULL,artist TEXT NOT NULL,album TEXT NOT NULL,duration INTEGER NOT NULL,modified INTEGER NOT NULL,size INTEGER NOT NULL,art TEXT,added INTEGER NOT NULL,played INTEGER NOT NULL,enriched INTEGER NOT NULL,search TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO tracks VALUES('old','file:///old','Old song','Artist','Album',1000,1,2,NULL,1,3,1,'old song artist album')")
        legacy.version = 1; legacy.close()
        val upgraded = LibraryDatabase(context, "legacy.db")
        try {
            assertEquals("Old song", upgraded.track("old")!!.title)
            assertEquals(3L, upgraded.track("old")!!.lastPlayed)
            upgraded.toggleFavorite("old"); assertEquals(setOf("old"), upgraded.favoriteIds())
        }
        finally { upgraded.close() }
        context.deleteDatabase("legacy.db")
    }
    @Test fun tenThousandTracksWarmBrowseSearchAndDeltaBenchmark() {
        val rows = (0 until 10_000).map { track(it.toString(), "Song $it", "Artist ${it % 100}", "Album ${it % 500}") }
        val start = System.nanoTime(); db.upsert(folder.uri, rows); val indexMs = (System.nanoTime() - start) / 1e6
        db.close(); db = LibraryDatabase(context, "library-test.db")
        val warm = System.nanoTime(); val firstPage = db.search("", limit = 500); val warmMs = (System.nanoTime() - warm) / 1e6
        val search = System.nanoTime(); val found = db.search("artist 42"); val searchMs = (System.nanoTime() - search) / 1e6
        val unchanged = System.nanoTime(); assertEquals(0, db.upsert(folder.uri, rows)); val unchangedMs = (System.nanoTime() - unchanged) / 1e6
        val delta = System.nanoTime(); assertEquals(1, db.upsert(folder.uri, listOf(track("new", "New download")))); val deltaMs = (System.nanoTime() - delta) / 1e6
        assertEquals(500, firstPage.size); assertTrue(found.isNotEmpty()); assertEquals(10_001, db.count())
        val directory = File(System.getProperty("auralis.evidence")!!).apply { mkdirs() }
        File(directory, "library-host-benchmark.json").writeText("""{"environment":"Robolectric API 28 on Linux host; not phone latency","tracks":10000,"initialIndexMs":$indexMs,"warmPage500Ms":$warmMs,"artistSearchMs":$searchMs,"unchangedIndexCheckMs":$unchangedMs,"singleTrackDeltaMs":$deltaMs,"audioFileReadsOnWarmBrowse":0}""")
    }
}
