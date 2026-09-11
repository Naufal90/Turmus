package com.offlinep2p.feature.connection

import com.offlinep2p.core.common.ConnectionState
import com.offlinep2p.core.common.LogEvents
import com.offlinep2p.core.common.logEvent
import com.offlinep2p.core.network.P2PTransport
import com.offlinep2p.core.network.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI-facing connection coordinator (AGENTS.md §6, §28).
 *
 * Phase 2:
 *  - Wires a real [P2PTransport] into a strongly-typed [ConnectionState]
 *    flow that the UI collects.
 *  - Consumes [TransportEvent]s and maintains a live [peers] list.
 *  - Never simulates a peer or state transition — every state change is
 *    triggered either by a user action or by a real transport event.
 *
 * The transport itself is injectable so tests can substitute a fake, and
 * so Phase 1 (no transport) keeps working when [transport] is null.
 */
class ConnectionCoordinator(
    private val transport: P2PTransport? = null,
    private val localDisplayName: String = "Android device",
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var eventJob: Job? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()

    private val TAG = "ConnCoord"

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    fun startAdvertising() {
        if (transport == null) {
            // Phase 1 fallback: no transport wired yet.
            _state.value = ConnectionState.Advertising
            return
        }
        subscribeToTransportEvents()
        _state.value = ConnectionState.Advertising
        scope.launch {
            runCatching { transport.startAdvertising(localDisplayName) }
                .onFailure { _state.value = ConnectionState.Failed(it.message ?: "Advertise failed") }
        }
    }

    fun startDiscovery() {
        if (transport == null) {
            _state.value = ConnectionState.Discovering
            return
        }
        subscribeToTransportEvents()
        _state.value = ConnectionState.Discovering
        _peers.value = emptyList()
        scope.launch {
            runCatching { transport.startDiscovery() }
                .onFailure { _state.value = ConnectionState.Failed(it.message ?: "Discovery failed") }
        }
    }

    fun connectTo(peer: DiscoveredPeer) {
        val t = transport ?: return
        _state.value = ConnectionState.Connecting(peer.displayName)
        scope.launch {
            runCatching { t.connect(peer.peerId) }
                .onFailure { _state.value = ConnectionState.Failed(it.message ?: "Connect failed") }
        }
    }

    fun disconnect() {
        val previousState = _state.value
        _state.value = ConnectionState.Disconnected
        _peers.value = emptyList()

        val t = transport ?: return
        scope.launch {
            if (previousState is ConnectionState.Connected) {
                runCatching { t.disconnect(previousState.peerId) }
            }
            runCatching { t.shutdown() }
        }
    }

    fun release() {
        eventJob?.cancel()
        eventJob = null
        val t = transport ?: return
        scope.launch { runCatching { t.shutdown() } }
    }

    // ---------------------------------------------------------------------
    // Event pump
    // ---------------------------------------------------------------------

    private fun subscribeToTransportEvents() {
        if (eventJob != null || transport == null) return
        eventJob = scope.launch {
            transport.events.collect { event -> handle(event) }
        }
    }

    private fun handle(event: TransportEvent) {
        when (event) {
            is TransportEvent.PeerFound -> {
                _peers.update { current ->
                    if (current.any { it.peerId == event.peerId }) current
                    else current + DiscoveredPeer(event.peerId, event.name)
                }
            }
            is TransportEvent.PeerLost -> {
                _peers.update { current -> current.filterNot { it.peerId == event.peerId } }
            }
            is TransportEvent.Connected -> {
                _peers.value = emptyList()
                _state.value = ConnectionState.Connected(event.peerId, event.name)
                logEvent(TAG, LogEvents.P2P_CONNECTED, event.peerId)
            }
            is TransportEvent.Disconnected -> {
                _state.value = ConnectionState.Disconnected
                logEvent(TAG, LogEvents.P2P_DISCONNECTED, event.peerId)
            }
            is TransportEvent.Error -> {
                _state.value = ConnectionState.Failed(event.reason)
            }
            is TransportEvent.ControlReceived,
            is TransportEvent.AudioReceived -> {
                // Handled by higher layers (Phase 3+).
            }
        }
    }
}

/** Small helper to keep MutableStateFlow updates atomic without importing everywhere. */
private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    while (true) {
        val prev = value
        val next = transform(prev)
        if (compareAndSet(prev, next)) return
    }
}
