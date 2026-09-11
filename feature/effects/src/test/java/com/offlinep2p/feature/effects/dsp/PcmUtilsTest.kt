package com.offlinep2p.feature.effects.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PcmUtilsTest {

    @Test
    fun `round-trip preserves samples within quantisation error`() {
        val src = FloatArray(160) { i -> kotlin.math.sin(i * 0.1).toFloat() * 0.8f }
        val bytes = PcmUtils.floatsToBytes(src)
        val back = PcmUtils.bytesToFloats(bytes)
        for (i in src.indices) {
            assertTrue("index $i: ${src[i]} vs ${back[i]}", abs(src[i] - back[i]) < 1e-3f)
        }
    }

    @Test
    fun `floatsToBytes clamps out-of-range`() {
        val src = floatArrayOf(2.0f, -2.0f, 0.5f)
        val bytes = PcmUtils.floatsToBytes(src)
        val back = PcmUtils.bytesToFloats(bytes)
        assertTrue(back[0] > 0.99f)
        assertTrue(back[1] < -0.99f)
        assertEquals(0.5f, back[2], 1e-3f)
    }

    @Test
    fun `softClip tames overshoot`() {
        val buf = floatArrayOf(3f, -3f, 0.5f, 0f)
        PcmUtils.softClip(buf)
        assertTrue(buf[0] < 1f); assertTrue(buf[0] > 0.9f)
        assertTrue(buf[1] > -1f); assertTrue(buf[1] < -0.9f)
        assertTrue(abs(buf[2] - kotlin.math.tanh(0.5).toFloat()) < 1e-4f)
        assertEquals(0f, buf[3], 1e-6f)
    }
}
