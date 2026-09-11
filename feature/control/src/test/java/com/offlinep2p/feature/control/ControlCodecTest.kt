package com.offlinep2p.feature.control

import com.offlinep2p.core.protocol.HelloPayload
import com.offlinep2p.core.protocol.Message
import com.offlinep2p.core.protocol.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCodecTest {

    @Test
    fun `encode then decode round-trips a HELLO message`() {
        val hello = HelloPayload(
            deviceName = "Pixel 8",
            appVersion = "0.2.0",
            sessionToken = "abc123",
        )
        val msg = ControlCodec.envelope(
            type = MessageType.HELLO,
            payload = ControlCodec.encodePayload(hello, HelloPayload.serializer()),
            nowMs = 1_700_000_000_000,
        )
        val wire = ControlCodec.encode(msg)
        val decoded = ControlCodec.decode(wire)
        assertNotNull(decoded)
        assertEquals(MessageType.HELLO, decoded!!.type)
        val redecoded = ControlCodec.decodePayload(decoded.payload, HelloPayload.serializer())
        assertEquals(hello, redecoded)
    }

    @Test
    fun `decode rejects audio packet`() {
        val audio = com.offlinep2p.core.protocol.Packet.wrapAudio(byteArrayOf(1, 2, 3))
        assertNull(ControlCodec.decode(audio))
    }

    @Test
    fun `decode rejects garbage`() {
        assertNull(ControlCodec.decode(byteArrayOf(1, 42, 42, 42)))
    }

    @Test
    fun `envelope carries the current protocol version`() {
        val m = ControlCodec.envelope(
            MessageType.PING,
            ControlCodec.encodePayload(
                com.offlinep2p.core.protocol.PingPayload(1L),
                com.offlinep2p.core.protocol.PingPayload.serializer(),
            ),
            nowMs = 0L,
        )
        assertTrue(m.version == Message.PROTOCOL_VERSION)
    }
}
