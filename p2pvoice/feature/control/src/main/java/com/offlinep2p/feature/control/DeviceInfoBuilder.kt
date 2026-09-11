package com.offlinep2p.feature.control

import android.os.Build
import com.offlinep2p.core.audio.AudioEffectSupport
import com.offlinep2p.core.protocol.DeviceInfoPayload
import com.offlinep2p.core.protocol.Message
import com.offlinep2p.core.protocol.MessageType

/**
 * Builds the DEVICE_INFO control message (AGENTS.md §7).
 * Peers use this to negotiate which audio-processing effects are safe to
 * assume on the remote side (e.g. skip client-side NS if the peer already
 * did it on capture).
 */
object DeviceInfoBuilder {

    fun build(
        deviceName: String,
        appVersion: String,
        effects: AudioEffectSupport,
        nowMs: Long,
    ): Message {
        val payload = DeviceInfoPayload(
            deviceName = deviceName,
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
            osVersion = Build.VERSION.SDK_INT,
            appVersion = appVersion,
            aecAvailable = effects.aec,
            nsAvailable = effects.ns,
            agcAvailable = effects.agc,
        )
        return ControlCodec.envelope(
            type = MessageType.DEVICE_INFO,
            payload = ControlCodec.encodePayload(payload, DeviceInfoPayload.serializer()),
            nowMs = nowMs,
        )
    }
}
