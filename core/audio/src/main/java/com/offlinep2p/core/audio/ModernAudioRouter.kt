package com.offlinep2p.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.offlinep2p.core.common.AudioDevice
import com.offlinep2p.core.common.AudioRoute
import com.offlinep2p.core.common.LogEvents
import com.offlinep2p.core.common.logEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * `AudioRouter` implementation for API 31+ (AGENTS.md §18-§20).
 *
 * Uses `AudioManager.setCommunicationDevice(...)` — the modern,
 * non-deprecated way to route voice-communication audio. Also keeps a
 * live device list via `AudioDeviceCallback` so plug/unplug events
 * (headset, Bluetooth pairing) show up in the UI immediately.
 *
 * The router itself never generates audio — it only tells the platform
 * where our `USAGE_VOICE_COMMUNICATION` stream should land.
 */
@RequiresApi(Build.VERSION_CODES.S)
class ModernAudioRouter(context: Context) : AudioRouter {

    private val am: AudioManager =
        context.applicationContext.getSystemService(AudioManager::class.java)

    private val _devices = MutableStateFlow<List<AudioDevice>>(emptyList())
    override val devices: StateFlow<List<AudioDevice>> = _devices.asStateFlow()

    private val _currentRoute = MutableStateFlow(AudioRoute.Auto)
    override val currentRoute: StateFlow<AudioRoute> = _currentRoute.asStateFlow()

    private var previousMode: Int = AudioManager.MODE_NORMAL

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
        am.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    override fun leaveCommunicationMode() {
        runCatching { am.clearCommunicationDevice() }
        am.mode = previousMode
        _currentRoute.value = AudioRoute.Auto
    }

    override fun select(route: AudioRoute): Boolean {
        if (route == AudioRoute.Auto) {
            am.clearCommunicationDevice()
            _currentRoute.value = AudioRoute.Auto
            logEvent(TAG, LogEvents.AUDIO_ROUTE_CHANGED, "route=Auto")
            return true
        }

        val target = findDevice(route) ?: return false
        val ok = runCatching { am.setCommunicationDevice(target) }.getOrDefault(false)
        if (ok) {
            _currentRoute.value = route
            logEvent(TAG, LogEvents.AUDIO_ROUTE_CHANGED, "route=$route id=${target.id}")
        }
        return ok
    }

    override fun release() {
        runCatching { am.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching { am.clearCommunicationDevice() }
        am.mode = previousMode
    }

    // ---------------------------------------------------------------------

    private fun refreshDevices() {
        val available = am.availableCommunicationDevices
        val mapped = available.mapNotNull { AudioDeviceMapper.toAudioDevice(it) }
        // Dedup by (route, displayName) — a device may appear twice with
        // different `AudioDeviceInfo.id`s (e.g. A2DP + SCO for one headset).
        val deduped = mapped
            .distinctBy { it.route.name + "|" + it.displayName }
            .sortedBy { it.route.ordinal }
        _devices.value = deduped

        // If the user selected a route that just vanished, drop to Auto.
        val cur = _currentRoute.value
        if (cur != AudioRoute.Auto && deduped.none { it.route == cur }) {
            am.clearCommunicationDevice()
            _currentRoute.value = AudioRoute.Auto
        }
    }

    private fun findDevice(route: AudioRoute): AudioDeviceInfo? =
        am.availableCommunicationDevices.firstOrNull {
            AudioDeviceMapper.toRoute(it.type) == route
        }
}
