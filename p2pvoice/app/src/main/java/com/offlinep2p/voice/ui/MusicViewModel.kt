package com.offlinep2p.voice.ui

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offlinep2p.feature.music.MusicLibrary
import com.offlinep2p.feature.music.MusicPlayer
import com.offlinep2p.feature.music.MusicRole
import com.offlinep2p.feature.music.MusicSyncEngine
import com.offlinep2p.feature.music.PlaybackState
import com.offlinep2p.feature.music.PlaylistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel dedicated to music (AGENTS.md §14-§15).
 *
 * Phase 9: exposes host/client role management via [becomeHost] /
 * [becomeClient] / [leaveShared]. The [MusicSyncEngine] itself is
 * created lazily by [attachSync] so a bare `MusicViewModel` still
 * builds and works when there is no active connection.
 */
class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val library = MusicLibrary(app.applicationContext)
    private val player = MusicPlayer(app.applicationContext)

    private val _items = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val items: StateFlow<List<PlaylistItem>> = _items.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val playback: StateFlow<PlaybackState> = player.state

    private var sync: MusicSyncEngine? = null
    private val _role = MutableStateFlow(MusicRole.None)
    val role: StateFlow<MusicRole> = _role.asStateFlow()

    init {
        if (hasReadAudioPermission()) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _scanning.value = true
            runCatching { _items.value = library.scan() }
            _scanning.value = false
        }
    }

    // ---------------------------------------------------------------------
    // Sync attachment — called by ConnectionViewModel once transport +
    // control channel exist. The engine is (re)installed on connect and
    // torn down on disconnect.
    // ---------------------------------------------------------------------

    fun attachSync(engine: MusicSyncEngine) {
        sync = engine
        viewModelScope.launch {
            engine.role.collect { _role.value = it }
        }
    }

    fun detachSync() {
        sync?.leave()
        sync = null
        _role.value = MusicRole.None
    }

    fun becomeHost() { sync?.becomeHost() }
    fun becomeClient() { sync?.becomeClient() }
    fun leaveShared() { sync?.leave() }

    fun lookup(songId: String): PlaylistItem? =
        _items.value.firstOrNull { it.contentId == songId }

    // ---------------------------------------------------------------------
    // Playback commands. Client role: DISABLED locally (playback is
    // driven by MUSIC_* messages).
    // ---------------------------------------------------------------------

    fun play(item: PlaylistItem) {
        if (_role.value == MusicRole.Client) return
        player.play(item)
    }
    fun pause() {
        if (_role.value == MusicRole.Client) return
        player.pause()
    }
    fun resume() {
        if (_role.value == MusicRole.Client) return
        player.resume()
    }
    fun stop() {
        if (_role.value == MusicRole.Client) return
        player.stop()
    }
    fun seekTo(ms: Long) {
        if (_role.value == MusicRole.Client) return
        player.seekTo(ms)
        if (_role.value == MusicRole.Host) sync?.hostSeekedTo(ms)
    }

    // Expose the player so ConnectionViewModel can create the sync engine.
    internal fun playerInstance(): MusicPlayer = player

    fun hasReadAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            android.Manifest.permission.READ_MEDIA_AUDIO
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(getApplication(), perm) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun readAudioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            android.Manifest.permission.READ_MEDIA_AUDIO
        else android.Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCleared() {
        sync?.leave()
        player.release()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MusicViewModel(app)
            }
        }
    }
}
