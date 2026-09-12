package com.offlinep2p.feature.voice

import android.media.AudioRecord
import android.media.AudioTrack
import com.offlinep2p.core.audio.AudioEffectSupport
import com.offlinep2p.core.audio.AudioIoFactory
import com.offlinep2p.core.audio.AudioRouter
import com.offlinep2p.core.audio.CaptureEffectsController
import com.offlinep2p.core.audio.EffectSelection
import com.offlinep2p.core.audio.EffectsNegotiator
import com.offlinep2p.core.audio.JitterBuffer
import com.offlinep2p.core.audio.OpusVoiceCodec
import com.offlinep2p.core.audio.VoiceCodec
import com.offlinep2p.core.audio.VoiceConfig
import com.offlinep2p.core.audio.VoicePacket
import com.offlinep2p.core.common.LogEvents
import com.offlinep2p.core.common.logEvent
import com.offlinep2p.core.network.P2PTransport
import com.offlinep2p.core.network.TransportEvent
import com.offlinep2p.core.protocol.Packet
import com.offlinep2p.feature.control.SessionPhase
import com.offlinep2p.feature.effects.VoiceEffectProcessor
import com.offlinep2p.feature.effects.VoicePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-duplex voice pipeline (AGENTS.md §8-§13, §18).
 *
 *   Capture path : AudioRecord ─► CaptureEffectsController (AEC/NS/AGC) ─►
 *                  VoiceEffectProcessor (Robot / Radio / Echo / …) ─►
 *                  VoiceCodec.encode ─► VoicePacket.pack ─►
 *                  Packet.wrapAudio ─► P2PTransport.sendAudio
 *
 *   Playout path : TransportEvent.ControlReceived[KIND_AUDIO] ─►
 *                  VoicePacket.unpack ─► JitterBuffer.push
 *                  timer @ 20 ms ─► JitterBuffer.pollExpected ─►
 *                  VoiceCodec.decode ─► AudioTrack.write
 *
 * The session refuses to start until [isSessionReady] returns true —
 * i.e. handshake has reached [SessionPhase.Ready] (AGENTS.md §36).
 *
 * Phase 5: capture-side effects lifecycle is managed here through
 * [CaptureEffectsController], with the enabled set decided by
 * [EffectsNegotiator] using local probe + peer DEVICE_INFO capability.
 *
 * Phase 6: [audioRouter] (optional) is switched into communication mode
 * on start() and back out on stop(). The router is optional so
 * `VoiceSession` still works in unit tests without a real
 * `AudioManager`.
 *
 * Phase 7: [voiceEffects] applies the selected [VoicePreset] on the
 * capture path BEFORE encoding, so both the local mic-in-your-headset
 * (via loopback echo, if any) and the peer hear the same effect.
 */
class VoiceSession(
    private val transport: P2PTransport,
    private val isSessionReady: () -> Boolean,
    private val currentPeerId: () -> String?,
    val config: VoiceConfig = VoiceConfig(),
    private val codecFactory: () -> VoiceCodec = { OpusVoiceCodec.factory(config) },
    private val now: () -> Long = System::currentTimeMillis,
    /** Local device probe — computed once, injected for testability. */
    private val localEffectSupport: AudioEffectSupport = AudioEffectSupport.probe(),
    /** Optional: manages MODE_IN_COMMUNICATION + route selection. */
    private val audioRouter: AudioRouter? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = AtomicBoolean(false)

    private var captureJob: Job? = null
    private var playoutJob: Job? = null
    private var receiveJob: Job? = null

    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var codec: VoiceCodec? = null
    private var effects: CaptureEffectsController? = null
    private val jitter = JitterBuffer()
    private var sequence: Int = 0

    /** Per-session voice-effect processor (Phase 7). Created lazily so
     *  the frame size matches [VoiceConfig] at runtime. */
    private val voiceEffects: VoiceEffectProcessor =
        VoiceEffectProcessor(
            sampleRate = config.sampleRateHz,
            frameSize = config.bytesPerFrame / 2, // 16-bit samples
        )

    private val _voicePreset = MutableStateFlow(VoicePreset.Normal)
    val voicePreset: StateFlow<VoicePreset> = _voicePreset.asStateFlow()

    /** Latest capability reported by the peer via DEVICE_INFO (Phase 3). */
    private var peerEffectSupport: AudioEffectSupport? = null

    /** User overrides — Phase 11 will bind these to Settings preferences. */
    private var userOverrides = EffectsNegotiator.UserOverrides()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _codecName = MutableStateFlow<String?>(null)
    val codecName: StateFlow<String?> = _codecName.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _activeEffects = MutableStateFlow(EffectSelection(false, false, false))
    val activeEffects: StateFlow<EffectSelection> = _activeEffects.asStateFlow()

    private val TAG = "VoiceSession"

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /** Feed peer-reported capability so the negotiator has both sides. */
    fun onPeerEffectSupport(peer: AudioEffectSupport?) {
        peerEffectSupport = peer
        if (running.get()) applyNegotiatedEffects()
    }

    /** User-level toggles (Phase 11 wires these to Settings UI). */
    fun setUserOverrides(overrides: EffectsNegotiator.UserOverrides) {
        userOverrides = overrides
        if (running.get()) applyNegotiatedEffects()
    }

    /** Switch the voice-effect preset. Applies immediately, even mid-call. */
    fun setVoicePreset(preset: VoicePreset) {
        voiceEffects.setPreset(preset)
        _voicePreset.value = preset
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        if (!isSessionReady()) {
            running.set(false)
            _lastError.value = "Handshake not ready — wait for peer HELLO"
            return
        }

        val c = runCatching { codecFactory() }.getOrElse {
            _lastError.value = "Codec init failed: ${it.message}"
            running.set(false); return
        }
        val r = runCatching { AudioIoFactory.createRecord(config) }.getOrElse {
            _lastError.value = "AudioRecord init failed: ${it.message}"
            c.release(); running.set(false); return
        }
        val t = runCatching { AudioIoFactory.createTrack(config) }.getOrElse {
            _lastError.value = "AudioTrack init failed: ${it.message}"
            r.release(); c.release(); running.set(false); return
        }

        codec = c; record = r; track = t
        _codecName.value = c.name
        _lastError.value = null
        sequence = 0
        jitter.reset()

        // Enter communication mode BEFORE starting AudioTrack so the OS
        // routes our USAGE_VOICE_COMMUNICATION stream correctly.
        audioRouter?.enterCommunicationMode()

        r.startRecording()
        t.play()

        // Effects can only be attached AFTER startRecording() — the audio
        // session id is not stable until the record is running.
        effects = CaptureEffectsController(r.audioSessionId)
        applyNegotiatedEffects()

        captureJob = scope.launch { captureLoop(r, c) }
        playoutJob = scope.launch { playoutLoop(t, c) }
        receiveJob = scope.launch { receiveLoop() }

        _active.value = true
        logEvent(
            TAG, LogEvents.VOICE_START,
            "codec=${c.name} fx=${_activeEffects.value} preset=${_voicePreset.value}"
        )
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        captureJob?.cancel(); captureJob = null
        playoutJob?.cancel(); playoutJob = null
        receiveJob?.cancel(); receiveJob = null

        runCatching { effects?.release() }; effects = null
        runCatching { record?.stop() }; runCatching { record?.release() }; record = null
        runCatching { track?.stop() }; runCatching { track?.release() }; track = null
        codec?.release(); codec = null

        // Leave communication mode LAST so the audio stack cleans up in
        // reverse order of setup.
        audioRouter?.leaveCommunicationMode()

        jitter.reset()
        _active.value = false
        _codecName.value = null
        _activeEffects.value = EffectSelection(false, false, false)
        logEvent(TAG, LogEvents.VOICE_STOP)
    }

    fun release() { stop(); scope.coroutineContext[Job]?.cancel() }

    // ---------------------------------------------------------------------
    // Negotiation
    // ---------------------------------------------------------------------

    private fun applyNegotiatedEffects() {
        val fx = effects ?: return
        val selection = EffectsNegotiator.decide(
            local = localEffectSupport,
            peer = peerEffectSupport,
            overrides = userOverrides,
        )
        fx.apply(selection)
        _activeEffects.value = fx.activeSelection()
        logEvent(TAG, LogEvents.AUDIO_ROUTE_CHANGED, "fx=${_activeEffects.value}")
    }

    // ---------------------------------------------------------------------
    // Loops
    // ---------------------------------------------------------------------

    private suspend fun captureLoop(rec: AudioRecord, codec: VoiceCodec) {
        val pcm = ByteArray(config.bytesPerFrame)
        while (scope.isActive && running.get()) {
            val read = rec.read(pcm, 0, pcm.size)
            if (read <= 0) continue

            // Phase 7: apply voice effect BEFORE encoding so the peer
            // hears the same processed signal.
            val processed = if (read == pcm.size) {
                voiceEffects.process(pcm)
            } else {
                pcm.copyOf(read)  // partial frame — skip effect this tick
            }

            val encodedResult = runCatching { codec.encode(processed) }
            if (encodedResult.isFailure) {
                logEvent(TAG, LogEvents.CODEC_ERROR, "encode: ${encodedResult.exceptionOrNull()?.message}")
                continue
            }
            val encoded = encodedResult.getOrNull() ?: continue
            if (encoded.isEmpty()) continue // codec still priming

            val voice = VoicePacket.pack(sequence++, now(), encoded)
            val wire = Packet.wrapAudio(voice)
            val peer = currentPeerId() ?: continue
            runCatching { transport.sendAudio(peer, wire) }
        }
    }

    /**
     * Consumes KIND_AUDIO packets from the transport bus and pushes them
     * into the jitter buffer. Control packets are ignored here — the
     * ControlChannel already handles them.
     */
    private suspend fun receiveLoop() {
        transport.events.collect { event ->
            if (event !is TransportEvent.ControlReceived) return@collect
            val parsed = Packet.parse(event.payload) ?: return@collect
            if (!parsed.isAudio) return@collect
            val vp = VoicePacket.unpack(parsed.body) ?: return@collect
            if (!jitter.push(vp.sequence, vp.body)) {
                logEvent(TAG, LogEvents.BUFFER_UNDERRUN, "dropped seq=${vp.sequence}")
            }
        }
    }

    private suspend fun playoutLoop(track: AudioTrack, codec: VoiceCodec) {
        val silence = ByteArray(config.bytesPerFrame)
        val frameNs = config.frameDurationMs * 1_000_000L
        var nextTickNs = System.nanoTime()

        while (scope.isActive && running.get()) {
            val encoded = jitter.pollExpected()
            val pcm = if (encoded == null) {
                silence   // concealment / priming
            } else {
                runCatching { codec.decode(encoded) }.getOrElse {
                    logEvent(TAG, LogEvents.CODEC_ERROR, "decode: ${it.message}")
                    silence
                }.let { if (it.isEmpty()) silence else it }
            }
            runCatching { track.write(pcm, 0, pcm.size) }

            nextTickNs += frameNs
            val sleep = nextTickNs - System.nanoTime()
            if (sleep > 0) {
                kotlinx.coroutines.delay(sleep / 1_000_000L)
            } else {
                // Fell behind — reset the pace so we don't spin.
                nextTickNs = System.nanoTime()
            }
        }
    }
}
