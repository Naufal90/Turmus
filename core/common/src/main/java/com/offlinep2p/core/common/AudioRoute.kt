package com.offlinep2p.core.common

/**
 * Enumerates the audio output routes required by AGENTS.md §18.
 * The UI (see §20) may only show routes that are actually available on
 * the device — the [AudioDevice] list is filtered to real hardware.
 */
enum class AudioRoute {
    Auto,
    PhoneSpeaker,
    Earpiece,
    BluetoothHeadset,
    BluetoothSpeaker,
    WiredHeadset,
}

/**
 * A concrete output the user can select. [displayName] must come from
 * the platform (Bluetooth device name, "Wired headset", etc.) — never a
 * hard-coded enum label (§20).
 */
data class AudioDevice(
    val id: Int,
    val displayName: String,
    val route: AudioRoute,
)
