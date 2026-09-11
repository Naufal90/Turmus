package com.offlinep2p.core.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.offlinep2p.core.common.LogEvents
import com.offlinep2p.core.common.logEvent

/**
 * Owns the lifecycle of AEC / NS / AGC for ONE capture session
 * (AGENTS.md §10).
 *
 * Design goals:
 *   • Never crash if an effect is missing on this device.
 *   • Enable/disable each effect independently at runtime, so the
 *     control channel can negotiate with the peer (skip NS locally
 *     when the peer's mic already denoises — avoids double-processing).
 *   • Release ALL effects when the capture session ends, even if
 *     enabling one of them failed halfway.
 *
 * Deliberately NOT a fake — every effect object here is a real
 * `AudioEffect`. The class simply refuses to create ones the device
 * did not report as available.
 */
class CaptureEffectsController(private val sessionId: Int) {

    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    private val TAG = "CaptureFX"

    /** Configure all three effects in one shot. Idempotent. */
    fun apply(desired: EffectSelection) {
        setAec(desired.aec)
        setNs(desired.ns)
        setAgc(desired.agc)
    }

    fun setAec(on: Boolean) {
        if (on) {
            if (!AcousticEchoCanceler.isAvailable()) return
            if (aec == null) aec = runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()
            aec?.let { setEnabledSafely(it, true, "AEC") }
        } else {
            aec?.let { setEnabledSafely(it, false, "AEC") }
        }
    }

    fun setNs(on: Boolean) {
        if (on) {
            if (!NoiseSuppressor.isAvailable()) return
            if (ns == null) ns = runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()
            ns?.let { setEnabledSafely(it, true, "NS") }
        } else {
            ns?.let { setEnabledSafely(it, false, "NS") }
        }
    }

    fun setAgc(on: Boolean) {
        if (on) {
            if (!AutomaticGainControl.isAvailable()) return
            if (agc == null) agc = runCatching { AutomaticGainControl.create(sessionId) }.getOrNull()
            agc?.let { setEnabledSafely(it, true, "AGC") }
        } else {
            agc?.let { setEnabledSafely(it, false, "AGC") }
        }
    }

    /** Report what the controller is actually enabling right now. */
    fun activeSelection(): EffectSelection = EffectSelection(
        aec = aec?.enabled == true,
        ns  = ns?.enabled  == true,
        agc = agc?.enabled == true,
    )

    fun release() {
        runCatching { aec?.release() }; aec = null
        runCatching { ns?.release()  }; ns  = null
        runCatching { agc?.release() }; agc = null
    }

    private fun setEnabledSafely(fx: AudioEffect, enabled: Boolean, tag: String) {
        val rc = runCatching { fx.setEnabled(enabled) }.getOrNull()
        if (rc != AudioEffect.SUCCESS) {
            logEvent(TAG, LogEvents.CODEC_ERROR, "$tag setEnabled($enabled) rc=$rc")
        }
    }
}

/** What the local capture pipeline SHOULD do — either from user prefs or
 *  from negotiation with the peer. Missing effects are silently ignored. */
data class EffectSelection(
    val aec: Boolean,
    val ns: Boolean,
    val agc: Boolean,
) {
    companion object {
        /** Everything the device supports (baseline). */
        fun defaultsFor(support: AudioEffectSupport) = EffectSelection(
            aec = support.aec,
            ns  = support.ns,
            agc = support.agc,
        )
    }
}
