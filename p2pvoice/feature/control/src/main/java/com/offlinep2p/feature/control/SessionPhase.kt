package com.offlinep2p.feature.control

/**
 * Post-transport handshake phase (AGENTS.md §7, §36).
 *
 * Once the [com.offlinep2p.core.network.P2PTransport] reports "Connected",
 * the session is at [LinkUp] — the socket is open, but we have not yet
 * exchanged app-level identity. The handshake progresses:
 *
 *   LinkUp -> HelloSent -> HelloReceived -> Ready
 *
 * Only [Ready] allows the voice/music layers to start streaming, per §36
 * ("no audio before pairing is confirmed").
 */
enum class SessionPhase {
    LinkUp,
    HelloSent,
    HelloReceived,
    Ready,
    Terminated,
}

data class RemoteIdentity(
    val deviceName: String,
    val appVersion: String,
    val protocolVersion: Int,
    val sessionToken: String,
)
