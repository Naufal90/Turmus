package com.offlinep2p.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePacketTest {

    @Test
    fun `pack then unpack round-trips header and body`() {
        val body = ByteArray(160) { (it * 3).toByte() }
        val wire = VoicePacket.pack(sequence = 42, captureTsMs = 1_700_000_000_000L, body = body)
        val parsed = VoicePacket.unpack(wire)!!
        assertEquals(42, parsed.sequence)
        assertEquals(1_700_000_000_000L, parsed.captureTsMs)
        assertArrayEquals(body, parsed.body)
    }

    @Test
    fun `too small buffer returns null`() {
        assertNull(VoicePacket.unpack(ByteArray(5)))
    }

    @Test
    fun `header size is 12 bytes`() {
        val wire = VoicePacket.pack(0, 0, ByteArray(0))
        assertEquals(VoicePacket.HEADER_SIZE, wire.size)
    }

    @Test
    fun `empty body still parses`() {
        val wire = VoicePacket.pack(7, 100L, ByteArray(0))
        val p = VoicePacket.unpack(wire)!!
        assertEquals(7, p.sequence)
        assertEquals(0, p.body.size)
    }
}
