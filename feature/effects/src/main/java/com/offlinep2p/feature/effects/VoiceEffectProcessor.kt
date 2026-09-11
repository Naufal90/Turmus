package com.offlinep2p.feature.effects

import com.offlinep2p.feature.effects.dsp.Biquad
import com.offlinep2p.feature.effects.dsp.DelayLine
import com.offlinep2p.feature.effects.dsp.PcmUtils
import com.offlinep2p.feature.effects.dsp.PitchShifter
import com.offlinep2p.feature.effects.dsp.RingModulator
import java.util.concurrent.atomic.AtomicReference

/**
 * Real-time voice effect engine (AGENTS.md §11-§13).
 *
 * Operates on ONE 20 ms PCM 16-bit LE frame at a time. The preset can
 * be swapped at any moment via [setPreset]; the DSP state is reset on
 * change so the previous preset's tail never bleeds into the next.
 *
 * The engine is intentionally allocation-light in the hot path — the
 * float scratch buffer is reused across calls. Concurrency is handled
 * with a single [AtomicReference] so the audio thread never blocks on
 * the UI thread's preset change.
 */
class VoiceEffectProcessor(
    private val sampleRate: Int,
    private val frameSize: Int,
) {
    private val current = AtomicReference(VoicePreset.Normal)

    // DSP primitives — created once and reused; state is reset on preset swap.
    private val hp = Biquad()
    private val lp = Biquad()
    private val bp = Biquad()
    private val shelf = Biquad()
    private val ring = RingModulator(sampleRate)
    private val pitch = PitchShifter(frameSize)
    private val echoDelay = DelayLine((sampleRate * 0.400f).toInt())  // up to 400 ms
    private val reverbComb1 = DelayLine((sampleRate * 0.030f).toInt())
    private val reverbComb2 = DelayLine((sampleRate * 0.037f).toInt())
    private val reverbAllpass = DelayLine((sampleRate * 0.005f).toInt())

    // Reusable float scratch buffer — avoids per-frame allocation.
    private val scratch = FloatArray(frameSize)

    init { configurePreset(current.get()) }

    fun currentPreset(): VoicePreset = current.get()

    fun setPreset(preset: VoicePreset) {
        if (current.getAndSet(preset) == preset) return
        resetAll()
        configurePreset(preset)
    }

    /**
     * Process a 20 ms PCM 16-bit LE frame in place.
     * Returns the SAME [inputPcm] reference — the caller may hand it
     * straight to the encoder.
     */
    fun process(inputPcm: ByteArray): ByteArray {
        val expectedSamples = frameSize
        val actualSamples = inputPcm.size / 2
        if (actualSamples != expectedSamples) {
            // Frame size mismatch: pass through untouched so we never
            // corrupt the caller's buffer. Voice pipeline logs this.
            return inputPcm
        }

        val preset = current.get()
        if (preset == VoicePreset.Normal) return inputPcm

        val buf = PcmUtils.bytesToFloats(inputPcm, scratch)
        val out = applyPreset(preset, buf)
        PcmUtils.softClip(out)
        PcmUtils.floatsToBytes(out, inputPcm)
        return inputPcm
    }

    // ---------------------------------------------------------------------
    // Preset composition — one branch per VoicePreset value.
    // ---------------------------------------------------------------------

    private fun applyPreset(preset: VoicePreset, buf: FloatArray): FloatArray = when (preset) {
        VoicePreset.Normal -> buf
        VoicePreset.Deep -> {
            pitch.factor = 0.8f            // ≈ -4 semitones
            val shifted = pitch.process(buf)
            shelf.process(shifted)          // slight bass boost
            shifted
        }
        VoicePreset.Chipmunk -> {
            pitch.factor = 1.5f            // ≈ +7 semitones
            pitch.process(buf)
        }
        VoicePreset.Robot -> {
            ring.setCarrier(70.0)
            ring.process(buf); buf
        }
        VoicePreset.Monster -> {
            pitch.factor = 0.65f
            val shifted = pitch.process(buf)
            ring.setCarrier(25.0)
            ring.process(shifted)
            shifted
        }
        VoicePreset.Radio -> {
            hp.process(buf)                // 300 Hz HP + 3 kHz LP
            lp.process(buf)
            // Light distortion via gain + tanh (soft-clip applied later)
            for (i in buf.indices) buf[i] = buf[i] * 2.2f
            buf
        }
        VoicePreset.Telephone -> {
            bp.process(buf)                // 300-3400 Hz band-pass
            buf
        }
        VoicePreset.Echo -> {
            val delaySamples = sampleRate * 0.25f      // 250 ms
            for (i in buf.indices) {
                buf[i] = echoDelay.processEcho(
                    x = buf[i],
                    delaySamples = delaySamples,
                    feedback = 0.45f,
                    wet = 0.55f,
                )
            }
            buf
        }
        VoicePreset.Reverb -> {
            // Very small Schroeder: two combs in parallel + one allpass.
            val out = FloatArray(buf.size)
            val c1Delay = sampleRate * 0.023f
            val c2Delay = sampleRate * 0.031f
            val apDelay = sampleRate * 0.004f
            for (i in buf.indices) {
                val x = buf[i]
                val c1 = reverbComb1.processEcho(x, c1Delay, feedback = 0.7f, wet = 1.0f) - x
                val c2 = reverbComb2.processEcho(x, c2Delay, feedback = 0.65f, wet = 1.0f) - x
                val combSum = (c1 + c2) * 0.5f
                // Simple allpass: y[n] = -g*x[n] + x[n-M] + g*y[n-M]
                val delayed = reverbAllpass.read(apDelay)
                val allpassOut = -0.5f * combSum + delayed
                reverbAllpass.write(combSum + 0.5f * allpassOut)
                out[i] = x + allpassOut * 0.35f
            }
            out
        }
    }

    private fun configurePreset(preset: VoicePreset) {
        when (preset) {
            VoicePreset.Deep -> shelf.setLowShelf(sampleRate, 200.0, gainDb = 4.0)
            VoicePreset.Radio -> {
                hp.setHighPass(sampleRate, 300.0)
                lp.setLowPass(sampleRate, 3000.0)
            }
            VoicePreset.Telephone -> bp.setBandPass(sampleRate, centerHz = 1850.0, q = 1.4)
            else -> Unit
        }
    }

    private fun resetAll() {
        hp.reset(); lp.reset(); bp.reset(); shelf.reset()
        ring.reset(); pitch.reset()
        echoDelay.reset()
        reverbComb1.reset(); reverbComb2.reset(); reverbAllpass.reset()
    }
}
