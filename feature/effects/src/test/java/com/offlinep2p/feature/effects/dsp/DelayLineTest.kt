package com.offlinep2p.feature.effects.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayLineTest {

    @Test
    fun `impulse comes back after the specified delay`() {
        val d = DelayLine(1000)
        // Feed impulse then zeros
        d.write(1.0f)
        for (i in 0 until 99) d.write(0f)
        // The 100th sample back should be the impulse
        val v = d.read(100f)
        assertTrue("impulse at 100 samples back", v > 0.99f)
    }

    @Test
    fun `linear interpolation gives half between samples`() {
        val d = DelayLine(1000)
        d.write(1.0f); d.write(0.0f)
        // read half a sample behind writeIdx-1 (which is the 0) means we
        // interpolate between 0 (nearest, 0 samples back) and 1 (1 sample back)
        val v = d.read(0.5f)
        assertEquals(0.5f, v, 1e-3f)
    }

    @Test
    fun `processEcho produces a delayed copy plus original`() {
        val d = DelayLine(1000)
        // Pump 200 samples of an impulse train
        val out = FloatArray(200)
        for (i in 0 until 200) {
            val x = if (i == 0) 1f else 0f
            out[i] = d.processEcho(x, delaySamples = 100f, feedback = 0.5f, wet = 1.0f)
        }
        // Original impulse at index 0…
        assertTrue(out[0] > 0.99f)
        // …echo at index 100
        assertTrue("echo at index 100 (${out[100]})", out[100] > 0.4f)
    }
}
