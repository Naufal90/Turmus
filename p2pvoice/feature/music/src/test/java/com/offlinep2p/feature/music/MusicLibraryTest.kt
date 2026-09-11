package com.offlinep2p.feature.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MusicLibraryTest {

    @Test
    fun `contentId is deterministic for the same inputs`() {
        val a = MusicLibrary.contentId("Song", "Artist", 240_000L, 5_000_000L)
        val b = MusicLibrary.contentId("Song", "Artist", 240_000L, 5_000_000L)
        assertEquals(a, b)
    }

    @Test
    fun `contentId ignores case in title and artist`() {
        val a = MusicLibrary.contentId("Song", "Artist", 240_000L, 5_000_000L)
        val b = MusicLibrary.contentId("SONG", "artist", 240_000L, 5_000_000L)
        assertEquals(a, b)
    }

    @Test
    fun `contentId differs when duration differs`() {
        val a = MusicLibrary.contentId("Song", "Artist", 240_000L, 5_000_000L)
        val b = MusicLibrary.contentId("Song", "Artist", 241_000L, 5_000_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `contentId differs when size differs`() {
        val a = MusicLibrary.contentId("Song", "Artist", 240_000L, 5_000_000L)
        val b = MusicLibrary.contentId("Song", "Artist", 240_000L, 5_100_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `contentId handles null artist`() {
        val a = MusicLibrary.contentId("Song", null, 240_000L, 5_000_000L)
        val b = MusicLibrary.contentId("Song", null, 240_000L, 5_000_000L)
        assertEquals(a, b)
        assertEquals(16, a.length)
    }

    @Test
    fun `contentId is 16 hex characters`() {
        val id = MusicLibrary.contentId("Any", "Any", 1L, 1L)
        assertEquals(16, id.length)
        assert(id.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
