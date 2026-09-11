package com.offlinep2p.feature.control

import com.offlinep2p.core.audio.AudioEffectSupport
import com.offlinep2p.core.common.LogEvents
import com.offlinep2p.core.common.logEvent
import com.offlinep2p.core.network.P2PTransport
import com.offlinep2p.core.network.TransportEvent
import com.offlinep2p.core.protocol.DeviceInfoPayload
import com.offlinep2p.core.protocol.DisconnectPayload
import com.offlinep2p.core.protocol.Message
import com.offlinep2p.core.protocol.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ties the control-plane pieces together (AGENTS.md §7):
 *   - forwards KIND_CONTROL packets from the transport into [ControlCodec.decode]
 *   - drives [HandshakeManager] (HELLO exchange)
 *   - runs [RttMonitor] on a fixed heartbeat schedule
 *   - responds to inbound PING with a PONG
 *   - handles peer-initiated DISCONNECT cleanly
 *
 * Voice/music layers never read raw transport events — they observe
 * [phase]/[remote]/[rttMs] from this class. That keeps the transport
 * abstraction intact (§5).
 */
class ControlChannel(
    private val transport: P2PTransport,
    private val localIdentity: HandshakeManager.LocalIdentity,
    private val effectSupport: AudioEffectSupport = AudioEffectSupport(false, false, false),
    private val heartbeatIntervalMs: Long = 2_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handshake = HandshakeManager(localIdentity, now)
    private val rtt = RttMonitor()

    val phase: StateFlow<SessionPhase> = handshake.phase
    val remote: StateFlow<RemoteIdentity?> = handshake.remote
    val rttMs: StateFlow<Long?> = rtt.rttMs

    private val _remoteDeviceInfo = MutableStateFlow<DeviceInfoPayload?>(null)
    val remoteDeviceInfo: StateFlow<DeviceInfoPayload?> = _remoteDeviceInfo.asStateFlow()

    private var connectedPeerId: String? = null
    private var eventJob: Job? = null
    private var heartbeatJob: Job? = null

    private val TAG = "ControlChannel"

    /** Start listening for transport events. Idempotent. */
    fun start() {
        if (eventJob != null) return
        eventJob = scope.launch {
            transport.events.collect { onTransportEvent(it) }
        }
    }

    /** Stop everything and send a peer-visible DISCONNECT. */
    fun shutdown(reason: String = "user requested") {
        val peer = connectedPeerId
        if (peer != null && handshake.isReady) {
            scope.launch {
                runCatching {
                    val msg = ControlCodec.envelope(
                        MessageType.DISCONNECT,
                        ControlCodec.encodePayload(
                            DisconnectPayload(reason),
                            DisconnectPayload.serializer(),
                        ),
                        now(),
                    )
                    transport.sendControl(peer, ControlCodec.encode(msg))
                }
            }
        }
        heartbeatJob?.cancel(); heartbeatJob = null
        eventJob?.cancel(); eventJob = null
        handshake.terminate()
        connectedPeerId = null
    }

    // ---------------------------------------------------------------------

    private suspend fun onTransportEvent(event: TransportEvent) {
        when (event) {
            is TransportEvent.Connected -> {
                connectedPeerId = event.peerId
                sendHello()
                sendDeviceInfo()
                startHeartbeat()
            }
            is TransportEvent.Disconnected -> {
                heartbeatJob?.cancel(); heartbeatJob = null
                handshake.terminate()
                connectedPeerId = null
            }
            is TransportEvent.ControlReceived -> {
                val msg = ControlCodec.decode(event.payload) ?: return
                dispatch(event.peerId, msg)
            }
            else -> Unit
        }
    }

    private suspend fun dispatch(peerId: String, msg: Message) {
        when (msg.type) {
            MessageType.HELLO -> {
                val err = handshake.onHelloReceived(msg)
                if (err != null) {
                    logEvent(TAG, "HELLO_REJECTED", err)
                    shutdown(err)
                }
            }
            MessageType.DEVICE_INFO -> {
                _remoteDeviceInfo.value = runCatching {
                    ControlCodec.decodePayload(msg.payload, DeviceInfoPayload.serializer())
                }.getOrNull()
            }
            MessageType.PING -> {
                val pong = rtt.buildPong(msg, now()) ?: return
                transport.sendControl(peerId, ControlCodec.encode(pong))
            }
            MessageType.PONG -> {
                rtt.onPongReceived(msg)
            }
            MessageType.DISCONNECT -> {
                val reason = runCatching {
                    ControlCodec.decodePayload(msg.payload, DisconnectPayload.serializer()).reason
                }.getOrNull() ?: "peer disconnected"
                logEvent(TAG, LogEvents.P2P_DISCONNECTED, reason)
                shutdown(reason)
            }
            else -> Unit
        }
    }

    private suspend fun sendHello() {
        val peer = connectedPeerId ?: return
        val hello = handshake.buildHello()
        runCatching { transport.sendControl(peer, ControlCodec.encode(hello)) }
    }

    private suspend fun sendDeviceInfo() {
        val peer = connectedPeerId ?: return
        val info = DeviceInfoBuilder.build(
            deviceName = localIdentity.deviceName,
            appVersion = localIdentity.appVersion,
            effects = effectSupport,
            nowMs = now(),
        )
        runCatching { transport.sendControl(peer, ControlCodec.encode(info)) }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                val peer = connectedPeerId ?: return@launch
                if (!handshake.isReady) continue
                val ping = rtt.buildPing(now())
                runCatching { transport.sendControl(peer, ControlCodec.encode(ping)) }
                rtt.sweep(olderThanMs = heartbeatIntervalMs * 5)
            }
        }
    }
}
