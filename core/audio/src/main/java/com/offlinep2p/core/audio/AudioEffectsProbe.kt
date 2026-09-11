package com.offlinep2p.core.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

/**
 * Detects whether the running device supports optional audio-processing
 * effects (AGENTS.md §10). Never crash because an effect is unavailable —
 * callers use these flags to decide whether to enable/skip each stage.
 *
 * Since Phase 5 the peer's DEVICE_INFO also decodes into this same
 * structure, so [EffectsNegotiator] can reason about local and remote
 * capabilities uniformly.
 */
data class AudioEffectSupport(
    val aec: Boolean,
    val ns: Boolean,
    val agc: Boolean,
) {
    val anyAvailable: Boolean get() = aec || ns || agc

    companion object {
        fun probe(): AudioEffectSupport = AudioEffectSupport(
            aec = AcousticEchoCanceler.isAvailable(),
            ns = NoiseSuppressor.isAvailable(),
            agc = AutomaticGainControl.isAvailable(),
        )

        /** Build an [AudioEffectSupport] from a peer DEVICE_INFO payload. */
        fun fromPeer(aec: Boolean, ns: Boolean, agc: Boolean): AudioEffectSupport =
            AudioEffectSupport(aec = aec, ns = ns, agc = agc)
    }
}
