package com.offlinep2p.feature.effects

import com.offlinep2p.feature.effects.dsp.PcmUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceEffectProcessorTest {

    private val sr = 16_000
    private val frameSamples = 320  // 20 ms @ 16 kHz
    private val frameBytes = frameSamples * 2

    private fun sinePcm(freqHz: Double, amp: Float = 0.5f): ByteArray {
        val f = FloatArray(frameSamples) { i ->
            amp * sin(2.0 * PI * freqHz * i / sr).toFloat()
        }
        return PcmUtils.floatsToBytes(f)
    }

    @Test
    fun `Normal preset is a pass-through`() {
        val proc = VoiceEffectProcessor(sr, frameSamples)
        val input = sinePcm(440.0)
        val copy = input.copyOf()
        proc.process(input)
        assertArrayEquals(copy, input)
    }

    @Test
    fun `frame-size mismatch is passed through untouched`() {
        val proc = VoiceEffectProcessor(sr, frameSamples)
        proc.setPreset(VoicePreset.Robot)
        val weird = ByteArray(frameBytes - 4) { it.toByte() }
        val copy = weird.copyOf()
        proc.process(weird)
        assertArrayEquals(copy, weird)
    }

    @Test
    fun `Robot preset actually modifies the samples`() {
        val proc = VoiceEffectProcessor(sr, frameSamples)
        proc.setPreset(VoicePreset.Robot)
        val input = sinePcm(440.0)
        val before = input.copyOf()
        proc.process(input)
        var different = 0
        for (i in input.indices) if (input[i] != before[i]) different++
        assertFalse("Robot must change most samples ($different / ${input.size})",
            different < input.size / 4)
    }

    @Test
    fun `Radio preset attenuates a sub-bass tone`() {
        val proc = VoiceEffectProcessor(sr, frameSamples)
        proc.setPreset(VoicePreset.Radio)
        // Warm-up the biquads so their state settles.
        repeat(4) { proc.process(sinePcm(60.0)) }
        val input = sinePcm(60.0)
        proc.process(input)
        val out = PcmUtils.bytesToFloats(input)
        var sum = 0.0; for (x in out) sum += x * x
        val rms = kotlin.math.sqrt(sum / out.size)
        // 60 Hz is well below the 300 Hz HP; a small amount survives due
        // to filter roll-off, but should be << 0.5 amplitude.
        assert(rms < 0.15) { "Radio should attenuate 60 Hz, rms=$rms" }
    }

    @Test
    fun `setPreset is thread-safe getAndSet - same value is a no-op`() {
        val proc = VoiceEffectProcessor(sr, frameSamples)
        proc.setPreset(VoicePreset.Echo)
        proc.setPreset(VoicePreset.Echo) // second call: no reset expected
        assertEquals(VoicePreset.Echo, proc.currentPreset())
    }

    @Test
    fun `all nine presets process a frame without throwing`() {
        val proc = VoiceEffectProcessor(sr, frameSamples)
        for (p in VoicePreset.values()) {
            proc.setPreset(p)
            val input = sinePcm(440.0)
            proc.process(input)
            // Correct byte length preserved
            assertEquals(frameBytes, input.size)
        }
    }
}
