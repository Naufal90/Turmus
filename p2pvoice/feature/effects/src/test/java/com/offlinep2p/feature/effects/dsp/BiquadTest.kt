package com.offlinep2p.feature.effects.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class BiquadTest {

    private fun rms(buf: FloatArray): Float {
        var sum = 0.0
        for (x in buf) sum += x * x
        return kotlin.math.sqrt(sum / buf.size).toFloat()
    }

    private fun sine(freqHz: Double, sampleRate: Int, len: Int): FloatArray {
        val buf = FloatArray(len)
        for (i in 0 until len) buf[i] = sin(2.0 * PI * freqHz * i / sampleRate).toFloat()
        return buf
    }

    @Test
    fun `low-pass attenuates high frequency more than low frequency`() {
        val sr = 16_000
        val lp = Biquad().also { it.setLowPass(sr, cutoffHz = 500.0) }

        val low = sine(100.0, sr, 4000); lp.reset(); lp.process(low)
        val high = sine(3000.0, sr, 4000); lp.reset(); lp.process(high)

        assertTrue("low kept, high attenuated", rms(low) > rms(high) * 4)
    }

    @Test
    fun `high-pass attenuates low frequency more than high frequency`() {
        val sr = 16_000
        val hp = Biquad().also { it.setHighPass(sr, cutoffHz = 1000.0) }

        val low = sine(100.0, sr, 4000); hp.reset(); hp.process(low)
        val high = sine(3000.0, sr, 4000); hp.reset(); hp.process(high)

        assertTrue("high kept, low attenuated", rms(high) > rms(low) * 4)
    }

    @Test
    fun `band-pass attenuates far-band frequencies`() {
        val sr = 16_000
        val bp = Biquad().also { it.setBandPass(sr, centerHz = 1500.0, q = 1.4) }

        val inBand = sine(1500.0, sr, 4000); bp.reset(); bp.process(inBand)
        val below = sine(100.0, sr, 4000); bp.reset(); bp.process(below)
        val above = sine(7000.0, sr, 4000); bp.reset(); bp.process(above)

        assertTrue(rms(inBand) > rms(below) * 3)
        assertTrue(rms(inBand) > rms(above) * 3)
    }
}
