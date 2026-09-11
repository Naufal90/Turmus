package com.offlinep2p.feature.effects

/**
 * Initial voice-effect presets (AGENTS.md §11).
 * The DSP layer stays independent from Compose — this enum is metadata
 * only. Actual signal processing is added in Phase 7.
 */
enum class VoicePreset(val displayName: String) {
    Normal("Normal"),
    Deep("Deep"),
    Robot("Robot"),
    Radio("Radio"),
    Telephone("Telephone"),
    Monster("Monster"),
    Echo("Echo"),
    Reverb("Reverb"),
    Chipmunk("Chipmunk"),
}

data class EffectParameters(
    val pitch: Float = 1.0f,
    val bass: Float = 0.0f,
    val treble: Float = 0.0f,
    val gain: Float = 1.0f,
    val reverb: Float = 0.0f,
    val echo: Float = 0.0f,
    val distortion: Float = 0.0f,
    val compression: Float = 0.0f,
)
