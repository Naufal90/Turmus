package com.offlinep2p.feature.effects.dsp

/**
 * Ring-buffer delay line with linear interpolation (AGENTS.md §11).
 *
 * Used by Echo (single tap) and Reverb (Schroeder comb + allpass).
 * All state is preserved across [process] calls so a chunked stream
 * produces the same output as a single-shot render.
 */
class DelayLine(maxDelaySamples: Int) {

    private val buffer = FloatArray(maxDelaySamples + 1)
    private val size = buffer.size
    private var writeIdx = 0

    fun reset() {
        for (i in buffer.indices) buffer[i] = 0f
        writeIdx = 0
    }

    /** Read a sample [delaySamples] in the past (fractional allowed). */
    fun read(delaySamples: Float): Float {
        val delay = delaySamples.coerceIn(0f, (size - 2).toFloat())
        val readPos = (writeIdx - delay + size) % size
        val idx0 = readPos.toInt()
        val idx1 = (idx0 + 1) % size
        val frac = readPos - idx0
        return buffer[idx0] * (1f - frac) + buffer[idx1] * frac
    }

    fun write(sample: Float) {
        buffer[writeIdx] = sample
        writeIdx = (writeIdx + 1) % size
    }

    /** Convenience: process one sample with a single delay tap and feedback. */
    fun processEcho(x: Float, delaySamples: Float, feedback: Float, wet: Float): Float {
        val delayed = read(delaySamples)
        write(x + delayed * feedback)
        return x + delayed * wet
    }
}
