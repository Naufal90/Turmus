package com.offlinep2p.core.protocol

import kotlinx.serialization.Serializable

/**
 * Concrete payload shapes for the common message types.
 * Kept as separate serializable classes so the transport code can decode
 * `message.payload` into the right type without needing polymorphic magic.
 */
@Serializable
data class HelloPayload(
    val deviceName: String,
    val appVersion: String,
    val protocolVersion: Int = Message.PROTOCOL_VERSION,
    /** Random per-session token — used later for auth (AGENTS.md §36). */
    val sessionToken: String,
)

@Serializable
data class DeviceInfoPayload(
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val osVersion: Int,
    val appVersion: String,
    /** Audio-effect availability observed on this device (AGENTS.md §10). */
    val aecAvailable: Boolean,
    val nsAvailable: Boolean,
    val agcAvailable: Boolean,
)

@Serializable
data class PairRequestPayload(val sessionToken: String)

@Serializable
data class PairAcceptPayload(val accepted: Boolean, val reason: String? = null)

@Serializable
data class DisconnectPayload(val reason: String)

// ---------------------------------------------------------------------
// Music sync payloads (AGENTS.md §15). Phase 9.
// ---------------------------------------------------------------------

@Serializable
data class MusicPlayPayload(
    val songId: String,
    val title: String,
    val durationMs: Long,
    val positionMs: Long,
    val hostTimestamp: Long,
    val isPlaying: Boolean,
)

@Serializable
data class MusicPausePayload(
    val positionMs: Long,
    val hostTimestamp: Long,
)

@Serializable
data class MusicSyncPayload(
    val songId: String,
    val positionMs: Long,
    val hostTimestamp: Long,
    val isPlaying: Boolean,
)

@Serializable
data class MusicSeekPayload(
    val songId: String,
    val positionMs: Long,
    val hostTimestamp: Long,
)

@Serializable
data class MusicStopPayload(val hostTimestamp: Long)

@Serializable
data class PingPayload(val nonce: Long)

@Serializable
data class PongPayload(val nonce: Long, val remoteTimestamp: Long)
