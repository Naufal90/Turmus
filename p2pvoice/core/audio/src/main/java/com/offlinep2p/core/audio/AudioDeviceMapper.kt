package com.offlinep2p.core.audio

import android.media.AudioDeviceInfo
import com.offlinep2p.core.common.AudioDevice
import com.offlinep2p.core.common.AudioRoute

/**
 * Maps a platform [AudioDeviceInfo] to our internal [AudioDevice].
 * The mapping keeps the *real* device name (`productName`) — AGENTS.md
 * §20 explicitly forbids using enum labels like "Bluetooth Headset" if
 * the platform gave us the friendly name "Sony WH-1000XM4".
 *
 * Types we intentionally IGNORE:
 *   • telephony (TYPE_TELEPHONY)   — not applicable in an offline P2P call
 *   • USB accessories             — routed as a wired headset already
 *   • FM tuner / line-out         — not an output for voice
 * Anything unknown falls back to [AudioRoute.Auto] so the router still
 * surfaces it instead of silently dropping the device.
 */
object AudioDeviceMapper {

    fun toRoute(type: Int): AudioRoute = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.Earpiece
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> AudioRoute.PhoneSpeaker
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioRoute.BluetoothHeadset
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioRoute.BluetoothSpeaker
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET -> AudioRoute.WiredHeadset
        else -> AudioRoute.Auto
    }

    fun toAudioDevice(info: AudioDeviceInfo): AudioDevice? {
        // Skip inputs — this list is output-only.
        if (!info.isSink) return null
        val route = toRoute(info.type)
        val name = info.productName?.toString()?.takeIf { it.isNotBlank() }
            ?: defaultNameFor(route)
        return AudioDevice(id = info.id, displayName = name, route = route)
    }

    private fun defaultNameFor(route: AudioRoute): String = when (route) {
        AudioRoute.Earpiece -> "Earpiece"
        AudioRoute.PhoneSpeaker -> "Phone speaker"
        AudioRoute.BluetoothHeadset -> "Bluetooth headset"
        AudioRoute.BluetoothSpeaker -> "Bluetooth speaker"
        AudioRoute.WiredHeadset -> "Wired headset"
        AudioRoute.Auto -> "Automatic"
    }
}
