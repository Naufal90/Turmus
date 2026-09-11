package com.offlinep2p.feature.control

import com.offlinep2p.core.protocol.HelloPayload
import com.offlinep2p.core.protocol.Message
import com.offlinep2p.core.protocol.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HandshakeManagerTest {

    private fun localA() = HandshakeManager.LocalIdentity("Alice", "0.3.0", "tok-A")

    private fun peerHello(name: String, token: String, version: Int = Message.PROTOCOL_VERSION): Message {
        val payload = HelloPayload(
            deviceName = name,
            appVersion = "0.3.0",
            protocolVersion = version,
            sessionToken = token,
        )
        return ControlCodec.envelope(
            MessageType.HELLO,
            ControlCodec.encodePayload(payload, HelloPayload.serializer()),
            nowMs = 0L,
        )
    }

    @Test
    fun `send then receive lands in Ready`() {
        val h = HandshakeManager(localA())
        h.buildHello() // LinkUp -> HelloSent
        val err = h.onHelloReceived(peerHello("Bob", "tok-B"))
        assertNull(err)
        assertEquals(SessionPhase.Ready, h.phase.value)
        val remote = h.remote.value
        assertNotNull(remote)
        assertEquals("Bob", remote!!.deviceName)
        assertEquals("tok-B", remote.sessionToken)
    }

    @Test
    fun `receive then send also lands in Ready`() {
        val h = HandshakeManager(localA())
        val err = h.onHelloReceived(peerHello("Bob", "tok-B"))
        assertNull(err)
        assertEquals(SessionPhase.HelloReceived, h.phase.value)
        h.buildHello()
        assertEquals(SessionPhase.Ready, h.phase.value)
    }

    @Test
    fun `protocol version mismatch terminates the session`() {
        val h = HandshakeManager(localA())
        h.buildHello()
        val err = h.onHelloReceived(peerHello("Bob", "tok-B", version = 99))
        assertNotNull(err)
        assertEquals(SessionPhase.Terminated, h.phase.value)
    }

    @Test
    fun `non-hello message is rejected`() {
        val h = HandshakeManager(localA())
        val ping = ControlCodec.envelope(
            MessageType.PING,
            ControlCodec.encodePayload(
                com.offlinep2p.core.protocol.PingPayload(1L),
                com.offlinep2p.core.protocol.PingPayload.serializer(),
            ),
            nowMs = 0L,
        )
        val err = h.onHelloReceived(ping)
        assertNotNull(err)
    }
}
