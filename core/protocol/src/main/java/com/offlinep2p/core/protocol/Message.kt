package com.offlinep2p.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Versioned control-message envelope (AGENTS.md §7).
 *
 * Binary audio data (voice frames) is NOT wrapped in this envelope — the
 * transport layer uses a raw binary channel for that. This envelope is
 * used only for control/state messages: HELLO, PAIR_REQUEST, PING/PONG,
 * MUSIC_*, VOICE_CONTROL, DEVICE_INFO, AUDIO_ROUTE_CHANGED, DISCONNECT.
 */
@Serializable
data class Message(
    val version: Int = PROTOCOL_VERSION,
    val type: MessageType,
    val timestamp: Long,
    val payload: JsonElement = JsonObject(emptyMap()),
) {
    companion object {
        const val PROTOCOL_VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "kind"
            encodeDefaults = true
        }

        fun encode(message: Message): String = json.encodeToString(serializer(), message)
        fun decode(raw: String): Message = json.decodeFromString(serializer(), raw)
    }
}

@Serializable
enum class MessageType {
    @SerialName("HELLO") HELLO,
    @SerialName("PAIR_REQUEST") PAIR_REQUEST,
    @SerialName("PAIR_ACCEPT") PAIR_ACCEPT,
    @SerialName("PING") PING,
    @SerialName("PONG") PONG,
    @SerialName("VOICE_CONTROL") VOICE_CONTROL,
    @SerialName("MUSIC_PLAY") MUSIC_PLAY,
    @SerialName("MUSIC_PAUSE") MUSIC_PAUSE,
    @SerialName("MUSIC_SEEK") MUSIC_SEEK,
    @SerialName("MUSIC_STOP") MUSIC_STOP,
    @SerialName("MUSIC_SYNC") MUSIC_SYNC,
    @SerialName("MUSIC_NEXT") MUSIC_NEXT,
    @SerialName("MUSIC_PREVIOUS") MUSIC_PREVIOUS,
    @SerialName("DEVICE_INFO") DEVICE_INFO,
    @SerialName("AUDIO_ROUTE_CHANGED") AUDIO_ROUTE_CHANGED,
    @SerialName("DISCONNECT") DISCONNECT,
}
