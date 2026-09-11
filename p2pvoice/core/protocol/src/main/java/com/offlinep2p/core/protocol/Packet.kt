package com.offlinep2p.core.protocol

import java.nio.ByteBuffer

/**
 * Wire-level packet framing (AGENTS.md §7).
 *
 * Nearby Connections gives us ONE bytes channel. We multiplex control
 * messages and real-time audio frames over it with a 1-byte type header:
 *
 *   [0]     : PacketKind (1 = CONTROL, 2 = AUDIO)
 *   [1..]   : payload bytes (JSON-encoded Message for CONTROL, raw Opus
 *             frame for AUDIO)
 *
 * Keeping this framing in a dedicated file — instead of hard-coding the
 * magic byte inside the transport — means the transport layer stays
 * transport-only (§5) and any future transport (Wi-Fi Direct, BLE) can
 * reuse the exact same envelope.
 */
object Packet {
    const val KIND_CONTROL: Byte = 1
    const val KIND_AUDIO: Byte = 2

    fun wrapControl(json: ByteArray): ByteArray = wrap(KIND_CONTROL, json)
    fun wrapAudio(frame: ByteArray): ByteArray = wrap(KIND_AUDIO, frame)

    private fun wrap(kind: Byte, body: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(1 + body.size)
        buf.put(kind)
        buf.put(body)
        return buf.array()
    }

    /** Returns null if the packet is malformed (empty or unknown kind). */
    fun parse(raw: ByteArray): Parsed? {
        if (raw.isEmpty()) return null
        val kind = raw[0]
        if (kind != KIND_CONTROL && kind != KIND_AUDIO) return null
        val body = raw.copyOfRange(1, raw.size)
        return Parsed(kind, body)
    }

    data class Parsed(val kind: Byte, val body: ByteArray) {
        val isControl: Boolean get() = kind == KIND_CONTROL
        val isAudio: Boolean get() = kind == KIND_AUDIO

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Parsed) return false
            return kind == other.kind && body.contentEquals(other.body)
        }
        override fun hashCode(): Int = 31 * kind.toInt() + body.contentHashCode()
    }
}
