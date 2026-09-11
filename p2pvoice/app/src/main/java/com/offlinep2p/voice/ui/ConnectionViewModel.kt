package com.offlinep2p.voice.ui

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offlinep2p.core.audio.AudioEffectSupport
import com.offlinep2p.core.audio.AudioRouter
import com.offlinep2p.core.audio.AudioRouterFactory
import com.offlinep2p.core.audio.EffectsNegotiator
import com.offlinep2p.core.common.AudioRoute
import com.offlinep2p.core.common.ConnectionState
import com.offlinep2p.core.network.NearbyP2PTransport
import com.offlinep2p.feature.connection.ConnectionCoordinator
import com.offlinep2p.feature.connection.DiscoveredPeer
import com.offlinep2p.feature.control.ControlChannel
import com.offlinep2p.feature.control.HandshakeManager
import com.offlinep2p.feature.control.SessionPhase
import com.offlinep2p.feature.effects.VoicePreset
import com.offlinep2p.feature.music.MusicChannel
import com.offlinep2p.feature.music.MusicSyncEngine
import com.offlinep2p.feature.music.PlaylistItem
import com.offlinep2p.feature.voice.VoiceSession
import com.offlinep2p.voice.BuildConfig
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Owns transport + coordinator + control + voice + audio router +
 * music sync (AGENTS.md §23). Phase 9: constructs a [MusicSyncEngine]
 * and [MusicChannel] once we have all four (transport, control,
 * player, library lookup) available.
 *
 * The music view-model provides the [MusicPlayer] and the song lookup
 * via [MusicViewModel.attachSync] — kept out of this class so the
 * music feature can still be developed and tested standalone.
 */
class ConnectionViewModel(app: Application) : AndroidViewModel(app) {

    private val transport = NearbyP2PTransport(app.applicationContext)
    private val displayName: String = resolveDisplayName(app)
    private val sessionToken: String = UUID.randomUUID().toString()
    private val localEffectSupport: AudioEffectSupport = AudioEffectSupport.probe()

    val router: AudioRouter = AudioRouterFactory.create(app.applicationContext)

    val coordinator = ConnectionCoordinator(transport, displayName)

    val control: ControlChannel = ControlChannel(
        transport = transport,
        localIdentity = HandshakeManager.LocalIdentity(
            deviceName = displayName,
            appVersion = BuildConfig.VERSION_NAME,
            sessionToken = sessionToken,
        ),
        effectSupport = localEffectSupport,
    ).also { it.start() }

    val voice: VoiceSession = VoiceSession(
        transport = transport,
        isSessionReady = { control.phase.value == SessionPhase.Ready },
        currentPeerId = {
            (coordinator.state.value as? ConnectionState.Connected)?.peerId
        },
        localEffectSupport = localEffectSupport,
        audioRouter = router,
    )

    private var musicChannel: MusicChannel? = null

    init {
        viewModelScope.launch {
            control.remoteDeviceInfo.collect { info ->
                voice.onPeerEffectSupport(
                    info?.let {
                        AudioEffectSupport.fromPeer(
                            aec = it.aecAvailable,
                            ns = it.nsAvailable,
                            agc = it.agcAvailable,
                        )
                    }
                )
            }
        }
    }

    /**
     * Called from the Music screen once its ViewModel is available.
     * Sets up the MusicSyncEngine over the shared transport/control
     * and hands the engine back so the UI can observe role/linked.
     */
    fun attachMusic(
        playerLookup: () -> com.offlinep2p.feature.music.MusicPlayer,
        libraryLookup: (String) -> PlaylistItem?,
    ): MusicSyncEngine {
        musicChannel?.let { return it.engine }

        // Engine created FIRST — MusicChannel needs to reference it in
        // its `sendControl` lambda.
        lateinit var channel: MusicChannel
        val engine = MusicSyncEngine(
            player = playerLookup(),
            sendControl = { out -> channel.send(out) },
            oneWayLatencyMs = { (control.rttMs.value ?: 0L) / 2 },
        )
        channel = MusicChannel(
            transport = transport,
            engine = engine,
            currentPeerId = {
                (coordinator.state.value as? ConnectionState.Connected)?.peerId
            },
            libraryLookup = libraryLookup,
        ).also { it.start() }
        musicChannel = channel
        return engine
    }

    fun host() = coordinator.startAdvertising()
    fun join() = coordinator.startDiscovery()
    fun connect(peer: DiscoveredPeer) = coordinator.connectTo(peer)

    fun startVoice() = voice.start()
    fun stopVoice() = voice.stop()

    fun selectRoute(route: AudioRoute): Boolean = router.select(route)
    fun setVoicePreset(preset: VoicePreset) = voice.setVoicePreset(preset)

    fun setEffectOverrides(overrides: EffectsNegotiator.UserOverrides) =
        voice.setUserOverrides(overrides)

    fun disconnect() {
        voice.stop()
        musicChannel?.shutdown(); musicChannel = null
        control.shutdown("user requested")
        coordinator.disconnect()
    }

    override fun onCleared() {
        voice.release()
        musicChannel?.shutdown(); musicChannel = null
        control.shutdown("viewmodel cleared")
        coordinator.release()
        router.release()
        super.onCleared()
    }

    private fun resolveDisplayName(app: Application): String {
        val fromSettings = runCatching {
            Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        return fromSettings?.takeIf { it.isNotBlank() } ?: "Android device"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ConnectionViewModel(app)
            }
        }
    }
}
