package com.offlinep2p.core.audio

import android.content.Context
import android.os.Build

/**
 * Chooses the right [AudioRouter] implementation for the running
 * Android version (AGENTS.md §1.3 — never assume a single API).
 *
 * API 31+ gets [ModernAudioRouter] (setCommunicationDevice); everything
 * older gets [LegacyAudioRouter] (speakerphone / SCO toggles). Callers
 * see a single [AudioRouter] contract and never branch on Build.VERSION.
 */
object AudioRouterFactory {
    fun create(context: Context): AudioRouter =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ModernAudioRouter(context)
        } else {
            LegacyAudioRouter(context)
        }
}
