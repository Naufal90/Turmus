package com.offlinep2p.core.audio

/**
 * Voice capture/playback configuration (AGENTS.md §8).
 * Values may be tuned after real-device profiling.
 */
data class VoiceConfig(
    val sampleRateHz: Int = 16_000,
    val channelCount: Int = 1,
    val bitsPerSample: Int = 16,
    val frameDurationMs: Int = 20,
) {
    /** Bytes per PCM frame at this configuration. */
    val bytesPerFrame: Int
        get() = (sampleRateHz * frameDurationMs / 1000) * channelCount * (bitsPerSample / 8)
}
