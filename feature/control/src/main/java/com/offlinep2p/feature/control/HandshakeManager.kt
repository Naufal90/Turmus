package com.offlinep2p.feature.control

import com.offlinep2p.core.protocol.HelloPayload
import com.offlinep2p.core.protocol.Message
import com.offlinep2p.core.protocol.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the small post-connect handshake. Pure state machine — it does not
 * talk to the transport directly. Callers hand it received messages and
 * ask it what to send next; that way the same class is trivial to unit
 * test (see HandshakeManagerTest).
 */
class HandshakeManager(
    private val localIdentity: LocalIdentity,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class LocalIdentity(
        val deviceName: String,
        val appVersion: String,
        val sessionToken: String,
    )

    private val _phase = MutableStateFlow(SessionPhase.LinkUp)
    val phase: StateFlow<SessionPhase> = _phase.asStateFlow()

    private val _remote = MutableStateFlow<RemoteIdentity?>(null)
    val remote: StateFlow<RemoteIdentity?> = _remote.asStateFlow()

    /** Called once the transport reports Connected. Returns the HELLO to send. */
    fun buildHello(): Message {
        val payload = HelloPayload(
            deviceName = localIdentity.deviceName,
            appVersion = localIdentity.appVersion,
            protocolVersion = Message.PROTOCOL_VERSION,
            sessionToken = localIdentity.sessionToken,
        )
        _phase.value = if (_phase.value == SessionPhase.HelloReceived) {
            SessionPhase.Ready
        } else {
            SessionPhase.HelloSent
        }
        return ControlCodec.envelope(
            type = MessageType.HELLO,
            payload = ControlCodec.encodePayload(payload, HelloPayload.serializer()),
            nowMs = now(),
        )
    }

    /**
     * Handles an incoming HELLO from the peer. Returns a rejection reason
     * if the peer is incompatible (protocol version mismatch), null on OK.
     */
    fun onHelloReceived(message: Message): String? {
        if (message.type != MessageType.HELLO) return "not a HELLO"
        val hello = runCatching {
            ControlCodec.decodePayload(message.payload, HelloPayload.serializer())
        }.getOrNull() ?: return "malformed HELLO payload"

        if (hello.protocolVersion != Message.PROTOCOL_VERSION) {
            _phase.value = SessionPhase.Terminated
            return "incompatible protocol version ${hello.protocolVersion}"
        }

        _remote.value = RemoteIdentity(
            deviceName = hello.deviceName,
            appVersion = hello.appVersion,
            protocolVersion = hello.protocolVersion,
            sessionToken = hello.sessionToken,
        )
        _phase.value = when (_phase.value) {
            SessionPhase.HelloSent -> SessionPhase.Ready
            SessionPhase.LinkUp -> SessionPhase.HelloReceived
            else -> _phase.value
        }
        return null
    }

    fun terminate() {
        _phase.value = SessionPhase.Terminated
    }

    val isReady: Boolean
        get() = _phase.value == SessionPhase.Ready
}
