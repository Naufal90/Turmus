package com.offlinep2p.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import java.nio.ByteBuffer

/**
 * Opus voice codec on top of Android's built-in `MediaCodec` (API 29+).
 * Encoder mime: "audio/opus", decoder mime: "audio/opus".
 *
 * On older devices [factory] falls back to [PcmPassthroughCodec] — the
 * caller never has to check API level (AGENTS.md §1.3, §1.4).
 *
 * NOTE: Google's OMX Opus decoder needs codec-specific-data (CSD) at
 * config time (identification header + 2 empty setup buffers). We
 * synthesize the minimum viable set — 1 CSD-0 only — which the AOSP
 * SoftOpus and later C2 opusdec accept. If a specific OEM device
 * rejects it the pipeline degrades to PCM via [factory].
 */
class OpusVoiceCodec private constructor(
    private val config: VoiceConfig,
    private val encoder: MediaCodec,
    private val decoder: MediaCodec,
) : VoiceCodec {

    override val name = "OPUS"
    override val pcmFrameBytes: Int = config.bytesPerFrame

    private val timeoutUs = 10_000L
    private var encPresentationUs = 0L
    private var decPresentationUs = 0L

    override fun encode(pcm: ByteArray): ByteArray {
        // ---- feed encoder ------------------------------------------------
        val inIdx = encoder.dequeueInputBuffer(timeoutUs)
        if (inIdx >= 0) {
            val buf: ByteBuffer = encoder.getInputBuffer(inIdx) ?: return EMPTY
            buf.clear()
            buf.put(pcm)
            encoder.queueInputBuffer(inIdx, 0, pcm.size, encPresentationUs, 0)
            encPresentationUs += frameDurationUs()
        }
        // ---- drain encoder ----------------------------------------------
        val info = MediaCodec.BufferInfo()
        val outIdx = encoder.dequeueOutputBuffer(info, timeoutUs)
        if (outIdx < 0) return EMPTY
        val out = encoder.getOutputBuffer(outIdx) ?: return EMPTY
        val bytes = ByteArray(info.size)
        out.position(info.offset)
        out.get(bytes, 0, info.size)
        encoder.releaseOutputBuffer(outIdx, false)
        return bytes
    }

    override fun decode(encoded: ByteArray): ByteArray {
        val inIdx = decoder.dequeueInputBuffer(timeoutUs)
        if (inIdx >= 0) {
            val buf = decoder.getInputBuffer(inIdx) ?: return EMPTY
            buf.clear()
            buf.put(encoded)
            decoder.queueInputBuffer(inIdx, 0, encoded.size, decPresentationUs, 0)
            decPresentationUs += frameDurationUs()
        }
        val info = MediaCodec.BufferInfo()
        val outIdx = decoder.dequeueOutputBuffer(info, timeoutUs)
        if (outIdx < 0) return EMPTY
        val out = decoder.getOutputBuffer(outIdx) ?: return EMPTY
        val bytes = ByteArray(info.size)
        out.position(info.offset)
        out.get(bytes, 0, info.size)
        decoder.releaseOutputBuffer(outIdx, false)
        return bytes
    }

    private fun frameDurationUs(): Long = config.frameDurationMs * 1000L

    override fun release() {
        runCatching { encoder.stop() }; runCatching { encoder.release() }
        runCatching { decoder.stop() }; runCatching { decoder.release() }
    }

    companion object {
        private const val MIME = "audio/opus"
        private val EMPTY = ByteArray(0)

        /**
         * Best-effort factory: returns [OpusVoiceCodec] when the platform
         * provides Opus encode + decode, otherwise a [PcmPassthroughCodec].
         */
        fun factory(config: VoiceConfig = VoiceConfig()): VoiceCodec {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return PcmPassthroughCodec(config)
            }
            return runCatching { create(config) }.getOrElse { PcmPassthroughCodec(config) }
        }

        private fun create(config: VoiceConfig): VoiceCodec {
            val format = MediaFormat.createAudioFormat(MIME, config.sampleRateHz, config.channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 24_000)
                setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            }
            val enc = MediaCodec.createEncoderByType(MIME).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            val decFormat = MediaFormat.createAudioFormat(MIME, config.sampleRateHz, config.channelCount).apply {
                setByteBuffer("csd-0", buildOpusIdHeader(config))
            }
            val dec = MediaCodec.createDecoderByType(MIME).apply {
                configure(decFormat, null, null, 0)
                start()
            }
            return OpusVoiceCodec(config, enc, dec)
        }

        /** Minimal 19-byte OpusHead identification header (RFC 7845 §5.1). */
        private fun buildOpusIdHeader(config: VoiceConfig): ByteBuffer {
            val buf = ByteBuffer.allocate(19).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buf.put("OpusHead".toByteArray(Charsets.US_ASCII))
            buf.put(1)                                       // version
            buf.put(config.channelCount.toByte())            // channel count
            buf.putShort(0)                                  // pre-skip
            buf.putInt(config.sampleRateHz)                  // input sample rate
            buf.putShort(0)                                  // output gain
            buf.put(0)                                       // channel mapping family
            buf.flip()
            return buf
        }

        @Suppress("unused")
        fun isSupported(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            return runCatching {
                MediaCodec.createEncoderByType(MIME).also { it.release() }
                MediaCodec.createDecoderByType(MIME).also { it.release() }
                true
            }.getOrDefault(false)
        }

        @Suppress("unused")
        private fun MediaCodecInfo.CodecCapabilities.dummy() = Unit
    }
}
