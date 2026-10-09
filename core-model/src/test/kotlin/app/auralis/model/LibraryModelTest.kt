package app.auralis.model

import org.junit.Assert.*
import org.junit.Test

class LibraryModelTest {
    @Test fun fileIdentityMatchesAcrossSafAndMediaStoreVolumes() {
        assertEquals(LibraryText.id(LibraryText.storageKey("primary", "Music/प्यार.flac")),
            LibraryText.id(LibraryText.storageKey("external_primary", "/Music/प्यार.flac")))
        assertNotEquals(LibraryText.storageKey("abcd-efgh", "Music/song.mp3"), LibraryText.storageKey("primary", "Music/song.mp3"))
    }
    @Test fun folderMatchingDoesNotIncludeSiblingWithSimilarPrefix() {
        assertTrue(LibraryText.within("Music/Love/song.mp3", "Music/Love"))
        assertTrue(LibraryText.within("Music/Love/Hindi/song.mp3", "/Music/Love/"))
        assertFalse(LibraryText.within("Music/Lovely/song.mp3", "Music/Love"))
    }
    @Test fun searchNormalizationKeepsHindiAndEscapesSqlWildcards() {
        assertEquals(listOf("arijit", "प्यार"), LibraryText.terms("  ARIJIT  प्यार "))
        assertEquals("%100\\%\\_love%", LibraryText.likeTerm("100%_love"))
    }
    @Test fun hashesAreStableAndSafeForArtworkPaths() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", LibraryText.id("abc"))
        assertTrue(Regex("[a-f0-9]{64}").matches(LibraryText.id("content://music/audio/123")))
    }
}
