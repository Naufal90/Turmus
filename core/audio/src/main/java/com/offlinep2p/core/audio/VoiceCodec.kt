package com.offlinep2p.core.audio

/**
 * Voice codec abstraction (AGENTS.md §5, §9).
 * The voice pipeline talks only to this interface; concrete codecs
 * (Opus, PCM, or a future libopus native binding) plug in without
 * changing the capture/playback loops.
 */
interface VoiceCodec {

    val name: String

    /** Bytes-per-frame consumed by [encode]; must equal [VoiceConfig.bytesPerFrame]. */
    val pcmFrameBytes: Int

    /** Encode ONE 20 ms PCM frame. Returns empty array if the codec is
     *  still priming (Opus needs a few frames before it emits output). */
    fun encode(pcm: ByteArray): ByteArray

    /** Decode ONE codec frame back to PCM. Returns empty on codec error;
     *  the caller substitutes silence — never crashes the pipeline. */
    fun decode(encoded: ByteArray): ByteArray

    fun release()
}
