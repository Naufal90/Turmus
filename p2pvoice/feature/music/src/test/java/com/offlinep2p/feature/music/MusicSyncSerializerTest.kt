package com.offlinep2p.feature.music

import com.offlinep2p.core.protocol.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MusicSyncSerializerTest {

    @Test
    fun `Play outbound round-trips through envelope`() {
        val out = SyncOutbound.Play(
            songId = "abc", title = "Track", durationMs = 180_000,
            positionMs = 42_000, hostTimestamp = 1_700_000_000_000L, isPlaying = true,
        )
        val msg = MusicSyncSerializer.toMessage(out, nowMs = 1_700_000_000_000L)
        assertEquals(MessageType.MUSIC_PLAY, msg.type)
        val decoded = MusicSyncSerializer.decodePlay(msg.payload)
        assertNotNull(decoded)
        assertEquals("abc", decoded!!.songId)
        assertEquals(42_000, decoded.positionMs)
        assertEquals(true, decoded.isPlaying)
    }

    @Test
    fun `Resume is wire-encoded as a MUSIC_PLAY with blank songId`() {
        val out = SyncOutbound.Resume(positionMs = 12_345, hostTimestamp = 99L)
        val msg = MusicSyncSerializer.toMessage(out, nowMs = 100L)
        assertEquals(MessageType.MUSIC_PLAY, msg.type)
        val decoded = MusicSyncSerializer.decodePlay(msg.payload)!!
        assertEquals("", decoded.songId)
        assertEquals(true, decoded.isPlaying)
    }

    @Test
    fun `Sync payload round-trips`() {
        val out = SyncOutbound.Sync("abc", 12_000L, 5_000L, isPlaying = true)
        val msg = MusicSyncSerializer.toMessage(out, nowMs = 6_000L)
        assertEquals(MessageType.MUSIC_SYNC, msg.type)
        val decoded = MusicSyncSerializer.decodeSync(msg.payload)!!
        assertEquals("abc", decoded.songId)
        assertEquals(12_000L, decoded.positionMs)
    }

    @Test
    fun `Seek and Stop have their own message types`() {
        val seek = MusicSyncSerializer.toMessage(
            SyncOutbound.Seek("abc", 4_000L, 1_000L), nowMs = 1_000L
        )
        val stop = MusicSyncSerializer.toMessage(
            SyncOutbound.Stop(1_000L), nowMs = 1_000L
        )
        assertEquals(MessageType.MUSIC_SEEK, seek.type)
        assertEquals(MessageType.MUSIC_STOP, stop.type)
    }
}
