package com.offlinep2p.core.common

/**
 * Explicit connection state model (AGENTS.md §6, §28).
 * Do not scatter connection status across global variables — always
 * emit one of these sealed values through the [ConnectionCoordinator]'s state flow.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Discovering : ConnectionState
    data object Advertising : ConnectionState
    data class Connecting(val peerName: String) : ConnectionState
    data class Connected(val peerId: String, val peerName: String) : ConnectionState
    data object Reconnecting : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}
