package com.offlinep2p.core.audio

import com.offlinep2p.core.common.AudioDevice
import com.offlinep2p.core.common.AudioRoute
import kotlinx.coroutines.flow.StateFlow

/**
 * Audio output routing abstraction (AGENTS.md §18-§20).
 *
 * The concrete implementation is chosen by [AudioRouterFactory] based
 * on the running Android version:
 *
 *   • API 31+  → `AudioManager.setCommunicationDevice(...)`
 *   • API 26-30 → `setSpeakerphoneOn` / `startBluetoothSco` / `isWiredHeadsetOn`
 *
 * Both share this contract. Neither ever simulates a route — a route
 * that is not physically available is filtered out of [devices].
 */
interface AudioRouter {

    /** Live list of routes the user may actually select right now. */
    val devices: StateFlow<List<AudioDevice>>

    /** The route currently in effect, or `Auto` if the system picks. */
    val currentRoute: StateFlow<AudioRoute>

    /** Enter the "in-call" audio mode so `USAGE_VOICE_COMMUNICATION`
     *  streams reach the earpiece / SCO instead of `STREAM_MUSIC`. */
    fun enterCommunicationMode()

    /** Restore the audio mode the caller had before enter*(). */
    fun leaveCommunicationMode()

    /** Ask the platform to route to [route]. Returns true if a change
     *  was actually requested — false if [route] is unavailable. */
    fun select(route: AudioRoute): Boolean

    /** Stop tracking device plug/unplug events. Idempotent. */
    fun release()
}
