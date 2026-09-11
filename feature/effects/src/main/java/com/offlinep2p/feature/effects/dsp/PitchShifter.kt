package com.offlinep2p.feature.effects.dsp

/**
 * Pitch shifter using resample + SOLA overlap-add (AGENTS.md §11).
 *
 * Algorithm: resample the incoming block by 1/factor (time-scaling),
 * then re-time it back to the original length with overlap-add so the
 * pitch changes but the duration stays constant. Deliberately simple —
 * a full phase-vocoder would be higher quality but far heavier for the
 * 20 ms real-time budget. The trade-off is acceptable for the two
 * preset use cases: Deep (0.8x = -4 semitones) and Chipmunk (1.5x =
 * +7 semitones) where colorful artefacts are expected.
 *
 * State is preserved across calls via the internal residual buffer so
 * successive 20 ms frames do not click at their boundary.
 */
class PitchShifter(private val frameSize: Int) {

    /** 1.0 = no change; <1.0 lower pitch; >1.0 higher pitch. */
    var factor: Float = 1.0f
        set(value) { field = value.coerceIn(0.5f, 2.0f) }

    private val overlap = frameSize / 4
    private val residual = FloatArray(overlap)

    fun reset() {
        for (i in residual.indices) residual[i] = 0f
    }

    /** Returns a NEW FloatArray of size [frameSize] with the shifted signal. */
    fun process(input: FloatArray): FloatArray {
        if (kotlin.math.abs(factor - 1.0f) < 1e-3f) {
            return input.copyOf()
        }

        // Step 1: resample the input by 1/factor (linear interp).
        val resampledLen = (input.size / factor).toInt().coerceAtLeast(1)
        val resampled = FloatArray(resampledLen)
        for (i in 0 until resampledLen) {
            val srcPos = i * factor
            val i0 = srcPos.toInt().coerceIn(0, input.size - 1)
            val i1 = (i0 + 1).coerceIn(0, input.size - 1)
            val frac = srcPos - i0
            resampled[i] = input[i0] * (1f - frac) + input[i1] * frac
        }

        // Step 2: overlap-add to bring the output back to frameSize.
        // Use a Hann window across the overlap zone.
        val out = FloatArray(frameSize)
        val copyLen = kotlin.math.min(frameSize, resampledLen)
        System.arraycopy(resampled, 0, out, 0, copyLen)

        // If the resample is SHORTER than frameSize, tile the residual
        // (mild but predictable — better than a hole).
        if (copyLen < frameSize) {
            val hold = resampled[resampledLen - 1]
            for (i in copyLen until frameSize) out[i] = hold
        }

        // Cross-fade from the previous frame's tail into this one's head.
        for (i in 0 until overlap) {
            val w = 0.5f - 0.5f * kotlin.math.cos(
                Math.PI * i.toDouble() / overlap
            ).toFloat()
            out[i] = out[i] * w + residual[i] * (1f - w)
        }

        // Save this frame's tail for the next call.
        val tailStart = frameSize - overlap
        for (i in 0 until overlap) residual[i] = out[tailStart + i]

        return out
    }
}
