package com.offlinep2p.core.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MessageTest {

    @Test
    fun `encode then decode round-trips`() {
        val original = Message(
            type = MessageType.PING,
            timestamp = 123456789L,
            payload = buildJsonObject { put("nonce", 42) }
        )
        val raw = Message.encode(original)
        val decoded = Message.decode(raw)

        assertEquals(original.version, decoded.version)
        assertEquals(original.type, decoded.type)
        assertEquals(original.timestamp, decoded.timestamp)
        assertNotNull(decoded.payload)
    }

    @Test
    fun `default protocol version is 1`() {
        val m = Message(type = MessageType.HELLO, timestamp = 0L)
        assertEquals(1, m.version)
    }

    @Test
    fun `music sync payload serializes deterministically`() {
        val payload = MusicSyncPayload(
            songId = "abc123",
            positionMs = 52340L,
            hostTimestamp = 999L,
            isPlaying = true,
        )
        val json = Json.encodeToString(MusicSyncPayload.serializer(), payload)
        val decoded = Json.decodeFromString(MusicSyncPayload.serializer(), json)
        assertEquals(payload, decoded)
    }
}
