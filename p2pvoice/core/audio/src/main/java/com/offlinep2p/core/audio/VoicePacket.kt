package com.offlinep2p.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real-time voice packet header (AGENTS.md §9).
 *
 * Layout (little-endian, 12 bytes):
 *   [0..3]   uint32  sequenceNumber
 *   [4..11]  int64   captureTimestampMs (sender-wall-clock)
 *   [12..]   body    codec payload (Opus frame, or raw PCM in fallback mode)
 *
 * The header lets the receiver detect loss / reorder and drive a jitter
 * buffer without touching Nearby-specific APIs. Keep this format stable
 * once shipped — bumping it requires a protocol-version bump in
 * [com.offlinep2p.core.protocol.Message].
 */
object VoicePacket {

    const val HEADER_SIZE = 12

    fun pack(sequence: Int, captureTsMs: Long, body: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(HEADER_SIZE + body.size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(sequence)
        out.putLong(captureTsMs)
        out.put(body)
        return out.array()
    }

    fun unpack(raw: ByteArray): Parsed? {
        if (raw.size < HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val seq = buf.int
        val ts = buf.long
        val body = ByteArray(raw.size - HEADER_SIZE)
        buf.get(body)
        return Parsed(seq, ts, body)
    }

    data class Parsed(val sequence: Int, val captureTsMs: Long, val body: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Parsed) return false
            return sequence == other.sequence &&
                    captureTsMs == other.captureTsMs &&
                    body.contentEquals(other.body)
        }
        override fun hashCode(): Int {
            var r = sequence
            r = 31 * r + captureTsMs.hashCode()
            r = 31 * r + body.contentHashCode()
            return r
        }
    }
}
