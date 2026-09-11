package com.offlinep2p.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.offlinep2p.core.common.AudioDevice
import com.offlinep2p.core.common.AudioRoute
import com.offlinep2p.core.common.LogEvents
import com.offlinep2p.core.common.logEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Legacy `AudioRouter` for API 26-30 (AGENTS.md §18-§20).
 *
 * On this range there is no `setCommunicationDevice`. We steer routing
 * with the pre-Android-12 toggles:
 *
 *   • Speaker      → `isSpeakerphoneOn = true`
 *   • Earpiece     → `isSpeakerphoneOn = false` and no SCO / wired
 *   • Bluetooth    → `startBluetoothSco()` (SCO covers both HFP/HSP
 *                    headsets, which is the only Bluetooth path Android
 *                    exposes to voice-communication streams)
 *   • Wired        → the OS routes automatically when a wired headset is
 *                    plugged in — we just report it, and disable
 *                    speakerphone/SCO so the OS takes over
 *
 * A route that is not physically available is filtered out of [devices]
 * — never simulated.
 */
class LegacyAudioRouter(context: Context) : AudioRouter {

    private val am: AudioManager =
        context.applicationContext.getSystemService(AudioManager::class.java)

    private val _devices = MutableStateFlow<List<AudioDevice>>(emptyList())
    override val devices: StateFlow<List<AudioDevice>> = _devices.asStateFlow()

    private val _currentRoute = MutableStateFlow(AudioRoute.Auto)
    override val currentRoute: StateFlow<AudioRoute> = _currentRoute.asStateFlow()

    private var previousMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerOn: Boolean = false
    private var scoActive: Boolean = false

    private val TAG = "AudioRouter"

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refreshDevices()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refreshDevices()
    }

    init {
        am.registerAudioDeviceCallback(deviceCallback, null)
        refreshDevices()
    }

    override fun enterCommunicationMode() {
        previousMode = am.mode
        previousSpeakerOn = am.isSpeakerphoneOn
        am.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    override fun leaveCommunicationMode() {
        stopScoIfActive()
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = previousSpeakerOn
        am.mode = previousMode
        _currentRoute.value = AudioRoute.Auto
    }

    override fun select(route: AudioRoute): Boolean {
        val available = _devices.value.map { it.route }.toSet()

        when (route) {
            AudioRoute.Auto -> {
                stopScoIfActive()
                @Suppress("DEPRECATION") run { am.isSpeakerphoneOn = false }
                _currentRoute.value = AudioRoute.Auto
            }
            AudioRoute.PhoneSpeaker -> {
                if (AudioRoute.PhoneSpeaker !in available) return false
                stopScoIfActive()
                @Suppress("DEPRECATION") run { am.isSpeakerphoneOn = true }
                _currentRoute.value = AudioRoute.PhoneSpeaker
            }
            AudioRoute.Earpiece -> {
                if (AudioRoute.Earpiece !in available) return false
                stopScoIfActive()
                @Suppress("DEPRECATION") run { am.isSpeakerphoneOn = false }
                _currentRoute.value = AudioRoute.Earpiece
            }
            AudioRoute.BluetoothHeadset,
            AudioRoute.BluetoothSpeaker -> {
                if (route !in available) return false
                @Suppress("DEPRECATION") run { am.isSpeakerphoneOn = false }
                startScoIfPossible()
                _currentRoute.value = route
            }
            AudioRoute.WiredHeadset -> {
                if (AudioRoute.WiredHeadset !in available) return false
                // Let the OS handle wired routing; we just clear overrides.
                stopScoIfActive()
                @Suppress("DEPRECATION") run { am.isSpeakerphoneOn = false }
                _currentRoute.value = AudioRoute.WiredHeadset
            }
        }
        logEvent(TAG, LogEvents.AUDIO_ROUTE_CHANGED, "route=$route")
        return true
    }

    override fun release() {
        runCatching { am.unregisterAudioDeviceCallback(deviceCallback) }
        stopScoIfActive()
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = previousSpeakerOn
        am.mode = previousMode
    }

    // ---------------------------------------------------------------------

    private fun startScoIfPossible() {
        if (scoActive) return
        if (!am.isBluetoothScoAvailableOffCall) return
        @Suppress("DEPRECATION") run {
            am.startBluetoothSco()
            am.isBluetoothScoOn = true
        }
        scoActive = true
    }

    private fun stopScoIfActive() {
        if (!scoActive) return
        @Suppress("DEPRECATION") run {
            am.isBluetoothScoOn = false
            am.stopBluetoothSco()
        }
        scoActive = false
    }

    private fun refreshDevices() {
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val mapped = outputs.mapNotNull { AudioDeviceMapper.toAudioDevice(it) }
        val deduped = mapped
            .distinctBy { it.route.name + "|" + it.displayName }
            .sortedBy { it.route.ordinal }
        _devices.value = deduped

        // If the user selected a route that just vanished, drop to Auto.
        val cur = _currentRoute.value
        if (cur != AudioRoute.Auto && deduped.none { it.route == cur }) {
            stopScoIfActive()
            @Suppress("DEPRECATION") run { am.isSpeakerphoneOn = false }
            _currentRoute.value = AudioRoute.Auto
        }
    }
}
