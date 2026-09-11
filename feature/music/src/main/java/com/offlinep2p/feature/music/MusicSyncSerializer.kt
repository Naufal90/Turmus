package com.offlinep2p.feature.music

import com.offlinep2p.core.protocol.Message
import com.offlinep2p.core.protocol.MessageType
import com.offlinep2p.core.protocol.MusicPausePayload
import com.offlinep2p.core.protocol.MusicPlayPayload
import com.offlinep2p.core.protocol.MusicSeekPayload
import com.offlinep2p.core.protocol.MusicStopPayload
import com.offlinep2p.core.protocol.MusicSyncPayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Bidirectional bridge between [SyncOutbound] / decoded inbound
 * shapes and the versioned [Message] envelope (AGENTS.md §7).
 *
 * Kept in `feature/music` so `core/protocol` stays payload-only and
 * doesn't grow imports of feature classes.
 */
object MusicSyncSerializer {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ------- outbound -------

    fun toMessage(o: SyncOutbound, nowMs: Long): Message = when (o) {
        is SyncOutbound.Play -> envelope(
            MessageType.MUSIC_PLAY,
            encode(MusicPlayPayload(
                songId = o.songId, title = o.title, durationMs = o.durationMs,
                positionMs = o.positionMs, hostTimestamp = o.hostTimestamp,
                isPlaying = o.isPlaying,
            ), MusicPlayPayload.serializer()),
            nowMs,
        )
        is SyncOutbound.Pause -> envelope(
            MessageType.MUSIC_PAUSE,
            encode(MusicPausePayload(o.positionMs, o.hostTimestamp),
                MusicPausePayload.serializer()),
            nowMs,
        )
        is SyncOutbound.Resume -> envelope(
            // Resume is modelled on the wire as a MUSIC_PLAY reissue
            // with isPlaying=true, so a client that missed the initial
            // MUSIC_PLAY still recovers on the next resume.
            MessageType.MUSIC_PLAY,
            encode(MusicPlayPayload(
                songId = "", title = "", durationMs = 0L,
                positionMs = o.positionMs, hostTimestamp = o.hostTimestamp,
                isPlaying = true,
            ), MusicPlayPayload.serializer()),
            nowMs,
        )
        is SyncOutbound.Seek -> envelope(
            MessageType.MUSIC_SEEK,
            encode(MusicSeekPayload(o.songId, o.positionMs, o.hostTimestamp),
                MusicSeekPayload.serializer()),
            nowMs,
        )
        is SyncOutbound.Sync -> envelope(
            MessageType.MUSIC_SYNC,
            encode(MusicSyncPayload(o.songId, o.positionMs, o.hostTimestamp, o.isPlaying),
                MusicSyncPayload.serializer()),
            nowMs,
        )
        is SyncOutbound.Stop -> envelope(
            MessageType.MUSIC_STOP,
            encode(MusicStopPayload(o.hostTimestamp),
                MusicStopPayload.serializer()),
            nowMs,
        )
    }

    // ------- inbound decoders -------

    fun decodePlay(payload: JsonElement): MusicPlayPayload? =
        runCatching { decode(payload, MusicPlayPayload.serializer()) }.getOrNull()

    fun decodePause(payload: JsonElement): MusicPausePayload? =
        runCatching { decode(payload, MusicPausePayload.serializer()) }.getOrNull()

    fun decodeSeek(payload: JsonElement): MusicSeekPayload? =
        runCatching { decode(payload, MusicSeekPayload.serializer()) }.getOrNull()

    fun decodeSync(payload: JsonElement): MusicSyncPayload? =
        runCatching { decode(payload, MusicSyncPayload.serializer()) }.getOrNull()

    fun decodeStop(payload: JsonElement): MusicStopPayload? =
        runCatching { decode(payload, MusicStopPayload.serializer()) }.getOrNull()

    // ------- helpers -------

    private fun <T> encode(v: T, ser: KSerializer<T>): JsonElement =
        json.encodeToJsonElement(ser, v)

    private fun <T> decode(el: JsonElement, ser: KSerializer<T>): T =
        json.decodeFromJsonElement(ser, el)

    private fun envelope(type: MessageType, payload: JsonElement, nowMs: Long): Message =
        Message(type = type, timestamp = nowMs, payload = payload)
}
