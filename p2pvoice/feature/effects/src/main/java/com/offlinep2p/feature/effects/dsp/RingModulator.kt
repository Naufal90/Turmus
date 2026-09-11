package com.offlinep2p.feature.effects.dsp

import kotlin.math.PI
import kotlin.math.sin

/**
 * Ring modulator (AGENTS.md §11): multiplies the signal by a sine
 * carrier. Classic "Robot / Cylon / Dalek" effect. Phase is preserved
 * across calls so successive buffers do not click at their boundary.
 */
class RingModulator(private val sampleRate: Int) {
    private var phase = 0.0
    private var carrierHz = 30.0

    fun setCarrier(hz: Double) { carrierHz = hz }
    fun reset() { phase = 0.0 }

    fun process(buf: FloatArray) {
        val phaseInc = 2.0 * PI * carrierHz / sampleRate
        for (i in buf.indices) {
            buf[i] = (buf[i] * sin(phase)).toFloat()
            phase += phaseInc
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }
    }
}
