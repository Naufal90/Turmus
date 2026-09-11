package com.offlinep2p.feature.connection

/**
 * A peer the local device has discovered but is not yet connected to.
 * Held in a StateFlow so the UI can render the list live.
 */
data class DiscoveredPeer(
    val peerId: String,
    val displayName: String,
)
