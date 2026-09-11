package com.offlinep2p.core.audio

/**
 * No-op codec: sends raw PCM over the wire (AGENTS.md §1.2 — a real
 * fallback, not a fake). Used on devices where the MediaCodec Opus
 * encoder is unavailable (API 26–28, or manufacturer omissions).
 *
 * PCM at 16 kHz mono 16-bit is ~256 kbps — heavy for Bluetooth-class
 * links, but the link is Wi-Fi Direct via Nearby, which sustains this
 * easily. Callers see the same [VoiceCodec] contract as with Opus.
 */
class PcmPassthroughCodec(config: VoiceConfig = VoiceConfig()) : VoiceCodec {
    override val name = "PCM16"
    override val pcmFrameBytes: Int = config.bytesPerFrame
    override fun encode(pcm: ByteArray): ByteArray = pcm.copyOf()
    override fun decode(encoded: ByteArray): ByteArray = encoded.copyOf()
    override fun release() {}
}
