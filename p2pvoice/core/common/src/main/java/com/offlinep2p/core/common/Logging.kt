package com.offlinep2p.core.common

import android.util.Log

/**
 * Structured logging tags (AGENTS.md §26). Use the [logEvent] helper
 * for state-transition events — never spam inside real-time audio loops.
 */
object LogEvents {
    const val VOICE_START = "VOICE_START"
    const val VOICE_STOP = "VOICE_STOP"
    const val AUDIO_ROUTE_CHANGED = "AUDIO_ROUTE_CHANGED"
    const val P2P_CONNECTED = "P2P_CONNECTED"
    const val P2P_DISCONNECTED = "P2P_DISCONNECTED"
    const val CODEC_ERROR = "CODEC_ERROR"
    const val BUFFER_UNDERRUN = "BUFFER_UNDERRUN"
}

fun logEvent(tag: String, event: String, detail: String? = null) {
    if (detail == null) Log.i(tag, event)
    else Log.i(tag, "$event $detail")
}
