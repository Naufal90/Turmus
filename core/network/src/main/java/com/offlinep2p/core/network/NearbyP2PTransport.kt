package com.offlinep2p.core.network

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.offlinep2p.core.common.LogEvents
import com.offlinep2p.core.common.logEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real implementation of [P2PTransport] on top of Google Nearby Connections
 * (AGENTS.md §5). Uses [Strategy.P2P_STAR] — one host, one client, direct
 * link, works completely offline.
 *
 * Wire path:
 *   startAdvertising() ──► ConnectionsClient.startAdvertising()
 *   startDiscovery()   ──► ConnectionsClient.startDiscovery()
 *   connect(peerId)    ──► ConnectionsClient.requestConnection()
 *   sendControl(...)   ──► Payload.Type.BYTES  (JSON envelope)
 *   sendAudio(...)     ──► Payload.Type.BYTES  (raw Opus frame — Phase 4)
 *
 * Control and audio are BOTH BYTES payloads today because Nearby's STREAM
 * type is one-directional per stream. Distinguishing the two channels is
 * the concern of a higher layer (packet header) — this class stays
 * transport-only, per AGENTS.md §5.
 */
class NearbyP2PTransport(
    private val context: Context,
    private val serviceId: String = DEFAULT_SERVICE_ID,
) : P2PTransport {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)

    private val _events = MutableSharedFlow<TransportEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<TransportEvent> = _events

    /** Discovered endpoints keyed by Nearby endpointId. */
    private val discovered = ConcurrentHashMap<String, String>()
    /** Endpoints that reached CONNECTION_ESTABLISHED. */
    private val connected = ConcurrentHashMap<String, String>()

    private val TAG = "NearbyP2P"

    // ---------------------------------------------------------------------
    // Nearby callbacks
    // ---------------------------------------------------------------------

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            // Higher layer decides control vs audio by inspecting the first
            // byte / packet header. This class does not peek.
            _events.tryEmit(TransportEvent.ControlReceived(endpointId, bytes))
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // BYTES payloads finish in one shot — no progress reporting needed.
        }
    }

    private val connectionLifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept. AGENTS.md §36 (pairing / auth token verification)
            // is layered on top via the HELLO/PAIR_REQUEST control messages.
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    val name = discovered[endpointId] ?: endpointId
                    connected[endpointId] = name
                    logEvent(TAG, LogEvents.P2P_CONNECTED, endpointId)
                    _events.tryEmit(TransportEvent.Connected(endpointId, name))
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED,
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    _events.tryEmit(TransportEvent.Error("Connection failed: ${result.status.statusMessage ?: "unknown"}"))
                }
                else -> {
                    _events.tryEmit(TransportEvent.Error("Connection status ${result.status.statusCode}"))
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            logEvent(TAG, LogEvents.P2P_DISCONNECTED, endpointId)
            _events.tryEmit(TransportEvent.Disconnected(endpointId))
        }
    }

    private val endpointDiscovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != serviceId) return
            discovered[endpointId] = info.endpointName
            _events.tryEmit(TransportEvent.PeerFound(endpointId, info.endpointName))
        }

        override fun onEndpointLost(endpointId: String) {
            discovered.remove(endpointId)
            _events.tryEmit(TransportEvent.PeerLost(endpointId))
        }
    }

    // ---------------------------------------------------------------------
    // P2PTransport API
    // ---------------------------------------------------------------------

    override suspend fun startAdvertising(displayName: String) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        suspendCancellableCoroutine<Unit> { cont ->
            client.startAdvertising(displayName, serviceId, connectionLifecycle, options)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override suspend fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        suspendCancellableCoroutine<Unit> { cont ->
            client.startDiscovery(serviceId, endpointDiscovery, options)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override suspend fun connect(peerId: String) {
        val displayName = discovered[peerId] ?: peerId
        suspendCancellableCoroutine<Unit> { cont ->
            client.requestConnection(displayName, peerId, connectionLifecycle)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override suspend fun disconnect(peerId: String) {
        client.disconnectFromEndpoint(peerId)
        connected.remove(peerId)
    }

    override suspend fun sendControl(peerId: String, payload: ByteArray) {
        if (!connected.containsKey(peerId)) {
            throw IllegalStateException("sendControl: peer $peerId not connected")
        }
        val nearbyPayload = Payload.fromBytes(payload)
        suspendCancellableCoroutine<Unit> { cont ->
            client.sendPayload(peerId, nearbyPayload)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override suspend fun sendAudio(peerId: String, frame: ByteArray) {
        // Same wire transport as control — differentiated by the first byte
        // of `frame` (packet-header work belongs to Phase 4). We deliberately
        // do NOT block/suspend on the send task here because voice frames
        // are fire-and-forget; the sender loop will observe backpressure via
        // the shared Flow if we ever need to add it.
        if (!connected.containsKey(peerId)) return
        client.sendPayload(peerId, Payload.fromBytes(frame))
    }

    override suspend fun shutdown() {
        client.stopAllEndpoints()
        discovered.clear()
        connected.clear()
        logEvent(TAG, LogEvents.P2P_DISCONNECTED, "shutdown()")
    }

    companion object {
        const val DEFAULT_SERVICE_ID = "com.offlinep2p.voice.nearby"
        private val STRATEGY = Strategy.P2P_STAR
    }
}
