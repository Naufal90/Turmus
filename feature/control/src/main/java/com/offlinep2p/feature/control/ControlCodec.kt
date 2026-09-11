package com.offlinep2p.feature.control

import com.offlinep2p.core.protocol.Message
import com.offlinep2p.core.protocol.MessageType
import com.offlinep2p.core.protocol.Packet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Encodes a [Message] to a wire packet (JSON envelope + KIND_CONTROL header),
 * and decodes a KIND_CONTROL packet back to a [Message].
 *
 * Kept small and stateless — the codec must never own connection state,
 * because the same JSON envelope will be reused when a future transport
 * (Wi-Fi Direct, BLE) is added per AGENTS.md §5.
 */
object ControlCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(message: Message): ByteArray {
        val jsonBytes = Message.encode(message).toByteArray(Charsets.UTF_8)
        return Packet.wrapControl(jsonBytes)
    }

    /** Returns null if the wire bytes are not a well-formed CONTROL packet. */
    fun decode(raw: ByteArray): Message? {
        val parsed = Packet.parse(raw) ?: return null
        if (!parsed.isControl) return null
        val text = parsed.body.toString(Charsets.UTF_8)
        return runCatching { Message.decode(text) }.getOrNull()
    }

    /** Convenience: decode `message.payload` into a typed payload. */
    fun <T> decodePayload(payload: JsonElement, serializer: KSerializer<T>): T =
        json.decodeFromJsonElement(serializer, payload)

    fun <T> encodePayload(value: T, serializer: KSerializer<T>): JsonElement =
        json.encodeToJsonElement(serializer, value)

    fun envelope(type: MessageType, payload: JsonElement, nowMs: Long): Message =
        Message(type = type, timestamp = nowMs, payload = payload)
}
