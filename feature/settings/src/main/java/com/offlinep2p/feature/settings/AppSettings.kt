package com.offlinep2p.feature.settings

import com.offlinep2p.core.common.AudioRoute

/**
 * Session-level user preferences. Persistence is added in Phase 11.
 */
data class AppSettings(
    val preferredRoute: AudioRoute = AudioRoute.Auto,
    val voiceVolume: Float = 1.0f,
    val musicVolume: Float = 0.6f,
    val masterVolume: Float = 1.0f,
)
