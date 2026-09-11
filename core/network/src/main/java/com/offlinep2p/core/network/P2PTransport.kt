package com.offlinep2p.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Transport abstraction (AGENTS.md §5).
 * The app must NOT depend directly on Nearby Connections; it depends on
 * this interface. A [NearbyP2PTransport] implementation is added in Phase 2,
 * and alternative transports (Wi-Fi Direct, BLE mesh, etc.) can be swapped
 * in later without touching feature code.
 */
interface P2PTransport {
    val events: Flow<TransportEvent>

    suspend fun startAdvertising(displayName: String)
    suspend fun startDiscovery()
    suspend fun connect(peerId: String)
    suspend fun disconnect(peerId: String)

    /** Send a control-plane (JSON-encoded) message. */
    suspend fun sendControl(peerId: String, payload: ByteArray)

    /** Send a real-time audio frame. Kept separate so the implementation
     *  can pick a low-latency binary channel (never wrap voice in JSON). */
    suspend fun sendAudio(peerId: String, frame: ByteArray)

    suspend fun shutdown()
}

sealed interface TransportEvent {
    data class PeerFound(val peerId: String, val name: String) : TransportEvent
    data class PeerLost(val peerId: String) : TransportEvent
    data class Connected(val peerId: String, val name: String) : TransportEvent
    data class Disconnected(val peerId: String) : TransportEvent
    data class ControlReceived(val peerId: String, val payload: ByteArray) : TransportEvent
    data class AudioReceived(val peerId: String, val frame: ByteArray) : TransportEvent
    data class Error(val reason: String) : TransportEvent
}
