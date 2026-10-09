package app.auralis.library

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.auralis.model.*

/** Small private persistent index. No audio or artwork is stored in SQLite. */
class LibraryDatabase(context: Context, name: String = "auralis-library.db") : SQLiteOpenHelper(context, name, null, 2) {
    init { setWriteAheadLoggingEnabled(true) }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sources(uri TEXT PRIMARY KEY,label TEXT NOT NULL,kind TEXT NOT NULL,volume TEXT,path TEXT)")
        db.execSQL("CREATE TABLE tracks(id TEXT PRIMARY KEY,uri TEXT NOT NULL,title TEXT NOT NULL,artist TEXT NOT NULL,album TEXT NOT NULL,duration INTEGER NOT NULL,modified INTEGER NOT NULL,size INTEGER NOT NULL,art TEXT,added INTEGER NOT NULL,played INTEGER NOT NULL,enriched INTEGER NOT NULL,search TEXT NOT NULL)")
        db.execSQL("CREATE TABLE membership(source TEXT NOT NULL REFERENCES sources(uri) ON DELETE CASCADE,track TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,PRIMARY KEY(source,track))")
        db.execSQL("CREATE INDEX tracks_title ON tracks(title COLLATE NOCASE)")
        db.execSQL("CREATE INDEX tracks_artist ON tracks(artist)")
        db.execSQL("CREATE INDEX tracks_album ON tracks(album,artist)")
        db.execSQL("CREATE INDEX tracks_added ON tracks(added DESC)")
        db.execSQL("CREATE INDEX tracks_played ON tracks(played DESC)")
        db.execSQL("CREATE INDEX membership_track ON membership(track)")
        createV2(db)
    }
    private fun createV2(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE favorites(track TEXT PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE analysis(track TEXT PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,fingerprint TEXT NOT NULL,energy REAL NOT NULL,tempo REAL NOT NULL,brightness REAL NOT NULL,confidence REAL NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion < 2) createV2(db) }

    fun sources(): List<LibrarySource> = readableDatabase.rawQuery("SELECT * FROM sources ORDER BY label COLLATE NOCASE", null).use { c ->
        buildList { while (c.moveToNext()) add(LibrarySource(c.text("uri"), c.text("label"), c.text("kind"), c.nullable("volume"), c.nullable("path"))) }
    }
    fun addSource(source: LibrarySource) {
        writableDatabase.insertWithOnConflict("sources", null, ContentValues().apply {
            put("uri", source.uri); put("label", source.label); put("kind", source.kind); put("volume", source.volume); put("path", source.path)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun removeSource(uri: String) = transaction {
        delete("sources", "uri=?", arrayOf(uri))
        execSQL("DELETE FROM tracks WHERE NOT EXISTS (SELECT 1 FROM membership WHERE membership.track=tracks.id)")
    }
    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM tracks", null).use { it.moveToFirst(); it.getInt(0) }
    fun track(id: String): LibraryTrack? = readableDatabase.query("tracks", null, "id=?", arrayOf(id), null, null, null).use { if (it.moveToFirst()) it.track() else null }

    /** Upserts keep history and enriched metadata for unchanged files. Never REPLACE a row: it would cascade membership deletion. */
    fun sourceSnapshot(source: String): Map<String, LibraryTrack> = readableDatabase.rawQuery(
        "SELECT tracks.* FROM tracks JOIN membership ON membership.track=tracks.id WHERE membership.source=?", arrayOf(source)).use { c ->
        buildMap { while (c.moveToNext()) { val row = c.track(); put(row.id, row) } }
    }
    fun upsert(source: String, tracks: List<LibraryTrack>, previousRows: Map<String, LibraryTrack>? = null): Int = transaction {
        var changed = 0
        val known = previousRows ?: if (tracks.size > 250) sourceSnapshot(source) else emptyMap()
        for (candidate in tracks) {
            val previous = known[candidate.id] ?: track(candidate.id)
            val sameFile = previous != null && previous.fingerprint == candidate.fingerprint
            val result = if (sameFile && previous!!.enriched && !candidate.enriched) previous.copy(uri = candidate.uri) else candidate.copy(
                addedAt = previous?.addedAt ?: candidate.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                lastPlayed = previous?.lastPlayed ?: 0,
            )
            if (result != previous) {
                val values = result.values()
                if (previous == null) insertOrThrow("tracks", null, values) else update("tracks", values, "id=?", arrayOf(result.id))
                changed++
            }
            if (candidate.id !in known) insertWithOnConflict("membership", null, ContentValues().apply { put("source", source); put("track", candidate.id) }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        changed
    }
    fun reconcile(source: String, seen: Set<String>, mediaOnly: Boolean = false) = transaction {
        // Only called after a complete successful scan, never after a failed or partial scan.
        val sql = "SELECT membership.track FROM membership JOIN tracks ON membership.track=tracks.id WHERE source=?" +
            if (mediaOnly) " AND tracks.uri LIKE 'content://media/%'" else ""
        val old = rawQuery(sql, arrayOf(source)).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
        old.filter { it !in seen }.forEach { delete("membership", "source=? AND track=?", arrayOf(source, it)) }
        execSQL("DELETE FROM tracks WHERE NOT EXISTS (SELECT 1 FROM membership WHERE membership.track=tracks.id)")
    }
    fun enrich(track: LibraryTrack) {
        val previous = this.track(track.id) ?: return
        if (previous.fingerprint != track.fingerprint) return
        writableDatabase.update("tracks", track.copy(addedAt = previous.addedAt, lastPlayed = previous.lastPlayed).values(), "id=?", arrayOf(track.id))
    }
    fun markPlayed(id: String) { writableDatabase.update("tracks", ContentValues().apply { put("played", System.currentTimeMillis()) }, "id=?", arrayOf(id)) }
    fun pending(limit: Int = 100): List<LibraryTrack> = rows("enriched=0", emptyArray(), "added DESC", limit)
    fun recent(limit: Int = 10) = rows(null, emptyArray(), "added DESC", limit)
    fun played(limit: Int = 8) = rows("played>0", emptyArray(), "played DESC", limit)
    fun favoriteIds(): Set<String> = readableDatabase.rawQuery("SELECT track FROM favorites", null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(0)) } }
    fun toggleFavorite(id: String) = transaction {
        if (delete("favorites", "track=?", arrayOf(id)) == 0 && track(id) != null)
            insertOrThrow("favorites", null, ContentValues().apply { put("track", id) })
    }
    fun clearHistory() { writableDatabase.execSQL("UPDATE tracks SET played=0") }
    fun browse(query: String, browse: LibraryBrowse, limit: Int): List<LibraryTrack> {
        val (filter, args) = filter(query, null, null)
        val extra = when(browse) { LibraryBrowse.HISTORY -> "played>0"; LibraryBrowse.FAVORITES -> "id IN (SELECT track FROM favorites)"; else -> "1=1" }
        return rows(listOfNotNull(filter, extra).joinToString(" AND "), args,
            if (browse == LibraryBrowse.HISTORY) "played DESC,id" else if (browse == LibraryBrowse.RECENT) "added DESC,id" else "title COLLATE NOCASE,id", limit)
    }
    fun analyses(): Map<String, SongFeatures> = readableDatabase.rawQuery("SELECT analysis.* FROM analysis JOIN tracks ON tracks.id=analysis.track WHERE analysis.fingerprint=CAST(tracks.modified AS TEXT)||':'||CAST(tracks.size AS TEXT)", null).use { c ->
        buildMap { while(c.moveToNext()) put(c.text("track"), SongFeatures(c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5))) }
    }
    fun saveAnalysis(track: LibraryTrack, features: SongFeatures) {
        if (this.track(track.id)?.fingerprint != track.fingerprint) return
        writableDatabase.insertWithOnConflict("analysis", null, ContentValues().apply {
            put("track", track.id); put("fingerprint", track.fingerprint); put("energy", features.energy); put("tempo", features.tempo)
            put("brightness", features.brightness); put("confidence", features.confidence)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun search(query: String, artist: String? = null, album: String? = null, limit: Int = 500): List<LibraryTrack> {
        val (where, args) = filter(query, artist, album)
        return rows(where, args, "title COLLATE NOCASE,id", limit)
    }
    fun groups(query: String, browse: LibraryBrowse, limit: Int = 500): List<LibraryGroup> {
        val (where, args) = filter(query, null, null)
        val columns = if (browse == LibraryBrowse.ARTISTS) "artist" else "album,artist"
        val sql = "SELECT *,COUNT(*) AS total FROM tracks" + (where?.let { " WHERE $it" } ?: "") + " GROUP BY $columns ORDER BY $columns COLLATE NOCASE LIMIT ?"
        return readableDatabase.rawQuery(sql, args + limit.toString()).use { c -> buildList {
            while (c.moveToNext()) add(LibraryGroup(if (browse == LibraryBrowse.ARTISTS) c.text("artist") else c.text("album"),
                if (browse == LibraryBrowse.ARTISTS) null else c.text("artist"), c.getInt(c.getColumnIndexOrThrow("total")), c.track()))
        } }
    }
    private fun filter(query: String, artist: String?, album: String?): Pair<String?, Array<String>> {
        val parts = mutableListOf<String>(); val args = mutableListOf<String>()
        LibraryText.terms(query).forEach { parts += "search LIKE ? ESCAPE '\\'"; args += LibraryText.likeTerm(it) }
        artist?.let { parts += "artist=?"; args += it }
        album?.let { parts += "album=?"; args += it }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" AND ") to args.toTypedArray()
    }
    private fun rows(where: String?, args: Array<String>, order: String, limit: Int): List<LibraryTrack> =
        readableDatabase.query("tracks", null, where, args, null, null, order, limit.coerceIn(1, 100_000).toString()).use { c ->
            buildList { while (c.moveToNext()) add(c.track()) }
        }
    private fun LibraryTrack.values() = ContentValues().apply {
        put("id", id); put("uri", uri); put("title", title); put("artist", artist); put("album", album); put("duration", durationMs)
        put("modified", modified); put("size", size); put("art", albumArt); put("added", addedAt); put("played", lastPlayed)
        put("enriched", if (enriched) 1 else 0); put("search", search)
    }
    private fun Cursor.text(name: String) = getString(getColumnIndexOrThrow(name)).orEmpty()
    private fun Cursor.nullable(name: String) = getString(getColumnIndexOrThrow(name))
    private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.track() = LibraryTrack(text("id"), text("uri"), text("title"), text("artist"), text("album"), long("duration"), long("modified"), long("size"), nullable("art"), long("added"), long("played"), long("enriched") != 0L)
    private inline fun <T> transaction(action: SQLiteDatabase.() -> T): T {
        val db = writableDatabase; db.beginTransaction()
        try { val result = db.action(); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
}
