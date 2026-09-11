package com.offlinep2p.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketTest {

    @Test
    fun `control round-trip preserves body`() {
        val body = "{\"type\":\"PING\"}".toByteArray()
        val wire = Packet.wrapControl(body)
        val parsed = Packet.parse(wire)!!
        assertTrue(parsed.isControl)
        assertArrayEquals(body, parsed.body)
    }

    @Test
    fun `audio round-trip preserves body`() {
        val frame = ByteArray(160) { it.toByte() }
        val wire = Packet.wrapAudio(frame)
        val parsed = Packet.parse(wire)!!
        assertTrue(parsed.isAudio)
        assertArrayEquals(frame, parsed.body)
        assertEquals(161, wire.size)
    }

    @Test
    fun `empty packet returns null`() {
        assertNull(Packet.parse(ByteArray(0)))
    }

    @Test
    fun `unknown kind returns null`() {
        assertNull(Packet.parse(byteArrayOf(99, 1, 2, 3)))
    }
}
