package com.offlinep2p.feature.effects.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Direct-form-I biquad filter (AGENTS.md §11).
 *
 * All coefficients follow the RBJ audio-EQ cookbook formulas. State
 * (`z1`, `z2`) is preserved across calls to [process] so a chunked
 * stream sounds identical to a single-buffer render.
 */
class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    private var z1 = 0.0; private var z2 = 0.0

    fun reset() { z1 = 0.0; z2 = 0.0 }

    fun setLowPass(sampleRate: Int, cutoffHz: Double, q: Double = SQRT2_INV) {
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosw = cos(w0)
        val b0n = (1.0 - cosw) / 2.0
        val b1n = 1.0 - cosw
        val b2n = (1.0 - cosw) / 2.0
        val a0 = 1.0 + alpha
        val a1n = -2.0 * cosw
        val a2n = 1.0 - alpha
        assign(b0n, b1n, b2n, a0, a1n, a2n)
    }

    fun setHighPass(sampleRate: Int, cutoffHz: Double, q: Double = SQRT2_INV) {
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosw = cos(w0)
        val b0n = (1.0 + cosw) / 2.0
        val b1n = -(1.0 + cosw)
        val b2n = (1.0 + cosw) / 2.0
        val a0 = 1.0 + alpha
        val a1n = -2.0 * cosw
        val a2n = 1.0 - alpha
        assign(b0n, b1n, b2n, a0, a1n, a2n)
    }

    fun setBandPass(sampleRate: Int, centerHz: Double, q: Double = 1.0) {
        val w0 = 2.0 * PI * centerHz / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosw = cos(w0)
        val b0n = alpha
        val b1n = 0.0
        val b2n = -alpha
        val a0 = 1.0 + alpha
        val a1n = -2.0 * cosw
        val a2n = 1.0 - alpha
        assign(b0n, b1n, b2n, a0, a1n, a2n)
    }

    fun setLowShelf(sampleRate: Int, cutoffHz: Double, gainDb: Double, s: Double = 1.0) {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val cosw = cos(w0)
        val sinw = sin(w0)
        val alpha = sinw / 2.0 * sqrt((a + 1.0 / a) * (1.0 / s - 1.0) + 2.0)
        val twoSqrtAalpha = 2.0 * sqrt(a) * alpha
        val b0n = a * ((a + 1) - (a - 1) * cosw + twoSqrtAalpha)
        val b1n = 2.0 * a * ((a - 1) - (a + 1) * cosw)
        val b2n = a * ((a + 1) - (a - 1) * cosw - twoSqrtAalpha)
        val a0 = (a + 1) + (a - 1) * cosw + twoSqrtAalpha
        val a1n = -2.0 * ((a - 1) + (a + 1) * cosw)
        val a2n = (a + 1) + (a - 1) * cosw - twoSqrtAalpha
        assign(b0n, b1n, b2n, a0, a1n, a2n)
    }

    /** In-place process. Length of [buf] can be any value. */
    fun process(buf: FloatArray) {
        for (i in buf.indices) {
            val x = buf[i].toDouble()
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            buf[i] = y.toFloat()
        }
    }

    private fun assign(b0n: Double, b1n: Double, b2n: Double, a0: Double, a1n: Double, a2n: Double) {
        b0 = b0n / a0; b1 = b1n / a0; b2 = b2n / a0
        a1 = a1n / a0; a2 = a2n / a0
    }

    companion object { private const val SQRT2_INV = 0.7071067811865475 }
}
