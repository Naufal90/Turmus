package com.offlinep2p.feature.control

import com.offlinep2p.core.protocol.Message
import com.offlinep2p.core.protocol.MessageType
import com.offlinep2p.core.protocol.PingPayload
import com.offlinep2p.core.protocol.PongPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Real RTT measurement over the control channel (AGENTS.md §26, §44).
 *
 * The sender records `nowNs()` when it emits a PING, keyed by the nonce.
 * When the PONG returns, elapsed nanoseconds are converted to millis and
 * fed into an exponential moving average so the reading is stable but
 * still responsive to route changes.
 *
 * NOT a simulation — the timestamps come from [nowNs], which callers wire
 * to `System.nanoTime()`; tests inject a fake clock.
 */
class RttMonitor(
    private val nowNs: () -> Long = System::nanoTime,
    /** Smoothing factor for the EMA (0..1). Larger = more reactive. */
    private val alpha: Double = 0.3,
) {
    private val outstanding = ConcurrentHashMap<Long, Long>()
    private val nonceGen = AtomicLong(1)

    private val _rttMs = MutableStateFlow<Long?>(null)
    val rttMs: StateFlow<Long?> = _rttMs.asStateFlow()

    /** Builds a PING message; the returned nonce is auto-registered. */
    fun buildPing(nowWallMs: Long): Message {
        val nonce = nonceGen.getAndIncrement()
        outstanding[nonce] = nowNs()
        val payload = ControlCodec.encodePayload(PingPayload(nonce), PingPayload.serializer())
        return ControlCodec.envelope(MessageType.PING, payload, nowWallMs)
    }

    /** Builds the PONG that should be sent in reply to [incoming]. */
    fun buildPong(incoming: Message, nowWallMs: Long): Message? {
        if (incoming.type != MessageType.PING) return null
        val ping = runCatching {
            ControlCodec.decodePayload(incoming.payload, PingPayload.serializer())
        }.getOrNull() ?: return null
        val pong = PongPayload(nonce = ping.nonce, remoteTimestamp = nowWallMs)
        return ControlCodec.envelope(
            MessageType.PONG,
            ControlCodec.encodePayload(pong, PongPayload.serializer()),
            nowWallMs,
        )
    }

    /** Consumes a PONG; updates [rttMs] if the nonce was ours. */
    fun onPongReceived(message: Message): Long? {
        if (message.type != MessageType.PONG) return null
        val pong = runCatching {
            ControlCodec.decodePayload(message.payload, PongPayload.serializer())
        }.getOrNull() ?: return null

        val startNs = outstanding.remove(pong.nonce) ?: return null
        val elapsedMs = (nowNs() - startNs) / 1_000_000L
        val prev = _rttMs.value
        val next = if (prev == null) elapsedMs
        else (alpha * elapsedMs + (1 - alpha) * prev).toLong()
        _rttMs.value = next
        return elapsedMs
    }

    /** Drops outstanding entries older than [olderThanMs]. */
    fun sweep(olderThanMs: Long) {
        val cutoff = nowNs() - olderThanMs * 1_000_000L
        outstanding.entries.removeIf { it.value < cutoff }
    }
}
