package com.offlinep2p.feature.music

import com.offlinep2p.core.network.P2PTransport
import com.offlinep2p.core.network.TransportEvent
import com.offlinep2p.core.protocol.MessageType
import com.offlinep2p.core.protocol.Packet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the transport's KIND_CONTROL bytes to [MusicSyncEngine]
 * (AGENTS.md §15). Own responsibility split:
 *
 *   • MusicSyncEngine  — pure state machine, calls [sendControl]
 *     lambda to emit outbound.
 *   • MusicChannel     — wraps the engine, serialises outbound via
 *     [MusicSyncSerializer], forwards them to the transport, and
 *     dispatches inbound MUSIC_* messages back into the engine.
 *
 * The channel does NOT decode HELLO/PING/PONG/etc — that stays with
 * `ControlChannel` (Phase 3). It only reacts to MUSIC_* types.
 */
class MusicChannel(
    private val transport: P2PTransport,
    val engine: MusicSyncEngine,
    private val currentPeerId: () -> String?,
    private val libraryLookup: (String) -> PlaylistItem?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var eventJob: Job? = null

    fun start() {
        if (eventJob != null) return
        eventJob = scope.launch {
            transport.events.collect { onEvent(it) }
        }
    }

    fun shutdown() {
        eventJob?.cancel(); eventJob = null
        engine.leave()
    }

    // ---------------------------------------------------------------------
    // outbound — wired into engine via constructor lambda
    // ---------------------------------------------------------------------

    /** The engine gives us a [SyncOutbound]; we serialise + send. */
    fun send(outbound: SyncOutbound): Boolean {
        val peer = currentPeerId() ?: return false
        val msg = MusicSyncSerializer.toMessage(outbound, now())
        return runCatching {
            val wire = com.offlinep2p.feature.control.ControlCodec.encode(msg)
            // We are on a coroutine that may or may not be main-safe —
            // dispatch the actual network call to a background task.
            scope.launch { runCatching { transport.sendControl(peer, wire) } }
            true
        }.getOrDefault(false)
    }

    // ---------------------------------------------------------------------
    // inbound
    // ---------------------------------------------------------------------

    private fun onEvent(event: TransportEvent) {
        if (event !is TransportEvent.ControlReceived) return
        val parsed = Packet.parse(event.payload) ?: return
        if (!parsed.isControl) return
        val msg = com.offlinep2p.feature.control.ControlCodec.decode(event.payload) ?: return

        when (msg.type) {
            MessageType.MUSIC_PLAY -> {
                val p = MusicSyncSerializer.decodePlay(msg.payload) ?: return
                if (p.songId.isBlank()) {
                    // Was serialised as a Resume — treat accordingly.
                    engine.applyRemoteResume(p.positionMs, p.hostTimestamp)
                } else {
                    engine.applyRemotePlay(
                        songId = p.songId,
                        libraryLookup = libraryLookup,
                        positionMs = p.positionMs,
                        hostTimestamp = p.hostTimestamp,
                        isPlaying = p.isPlaying,
                    )
                }
            }
            MessageType.MUSIC_PAUSE -> {
                val p = MusicSyncSerializer.decodePause(msg.payload) ?: return
                engine.applyRemotePause(p.positionMs)
            }
            MessageType.MUSIC_SEEK -> {
                val p = MusicSyncSerializer.decodeSeek(msg.payload) ?: return
                engine.applyRemoteSeek(p.positionMs, p.hostTimestamp, isPlaying = true)
            }
            MessageType.MUSIC_SYNC -> {
                val p = MusicSyncSerializer.decodeSync(msg.payload) ?: return
                engine.applyRemoteSync(p.songId, p.positionMs, p.hostTimestamp, p.isPlaying)
            }
            MessageType.MUSIC_STOP -> engine.applyRemoteStop()
            else -> Unit
        }
    }
}
