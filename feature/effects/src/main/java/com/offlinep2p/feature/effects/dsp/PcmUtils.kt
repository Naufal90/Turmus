package com.offlinep2p.feature.effects.dsp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PCM 16-bit LE ↔ FloatArray conversion helpers (AGENTS.md §11).
 *
 * The DSP graph operates on `FloatArray` in the range [-1.0, 1.0] so
 * every stage stays numerically stable when chained. Callers convert
 * once at the boundary — never inside a hot loop.
 */
object PcmUtils {

    fun bytesToFloats(pcm: ByteArray, out: FloatArray = FloatArray(pcm.size / 2)): FloatArray {
        val buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val n = out.size
        for (i in 0 until n) {
            out[i] = buf.short.toFloat() / 32768f
        }
        return out
    }

    fun floatsToBytes(samples: FloatArray, out: ByteArray = ByteArray(samples.size * 2)): ByteArray {
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        for (i in samples.indices) {
            val clamped = samples[i].coerceIn(-1f, 1f)
            buf.putShort((clamped * 32767f).toInt().toShort())
        }
        return out
    }

    /** Soft clip via tanh — smoother than hard-clip when a stage overshoots. */
    fun softClip(samples: FloatArray) {
        for (i in samples.indices) {
            val x = samples[i]
            samples[i] = kotlin.math.tanh(x.toDouble()).toFloat()
        }
    }
}
