package com.offlinep2p.feature.music

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.offlinep2p.core.common.LogEvents
import com.offlinep2p.core.common.logEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Media3/ExoPlayer wrapper for local music playback (AGENTS.md §14).
 *
 * Design:
 *   • Thin — no business logic, just [PlaybackState] out and command
 *     methods in. Phase 9 sync will layer on top of this.
 *   • Main-thread-only ExoPlayer API is honoured; commands from other
 *     threads are posted to the main Looper.
 *   • Every state change updates [state] with a fresh
 *     `positionUpdatedAtMs` so the sync extrapolator has a live anchor.
 *
 * Deliberately NOT a fake — plays the real file via ExoPlayer; the UI
 * never receives synthetic progress.
 */
class MusicPlayer(context: Context, private val now: () -> Long = System::currentTimeMillis) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val exo: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var currentItem: PlaylistItem? = null

    private val poller = object : Runnable {
        override fun run() {
            val item = currentItem
            if (item != null && exo.playbackState == Player.STATE_READY) {
                _state.value = _state.value.copy(
                    positionMs = exo.currentPosition.coerceAtLeast(0),
                    durationMs = exo.duration.coerceAtLeast(0),
                    isPlaying = exo.isPlaying,
                    positionUpdatedAtMs = now(),
                )
            }
            mainHandler.postDelayed(this, POSITION_POLL_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                positionMs = exo.currentPosition.coerceAtLeast(0),
                positionUpdatedAtMs = now(),
            )
            logEvent(TAG, if (isPlaying) "MUSIC_PLAY" else "MUSIC_PAUSE")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    positionMs = exo.duration.coerceAtLeast(0),
                    positionUpdatedAtMs = now(),
                )
                logEvent(TAG, "MUSIC_ENDED")
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            logEvent(TAG, LogEvents.CODEC_ERROR, "music: ${error.errorCodeName}")
        }
    }

    init {
        runOnMain {
            exo.addListener(listener)
            mainHandler.post(poller)
        }
    }

    // ---------------------------------------------------------------------
    // Public commands
    // ---------------------------------------------------------------------

    fun play(item: PlaylistItem) {
        runOnMain {
            currentItem = item
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(item.localUri)))
            exo.prepare()
            exo.playWhenReady = true
            _state.value = PlaybackState(
                currentItem = item,
                isPlaying = true,
                positionMs = 0L,
                durationMs = item.durationMs,
                positionUpdatedAtMs = now(),
            )
        }
    }

    fun resume() = runOnMain { exo.playWhenReady = true }
    fun pause()  = runOnMain { exo.playWhenReady = false }
    fun stop() {
        runOnMain {
            exo.stop()
            currentItem = null
            _state.value = PlaybackState()
        }
    }

    fun seekTo(positionMs: Long) {
        runOnMain {
            exo.seekTo(positionMs.coerceAtLeast(0))
            _state.value = _state.value.copy(
                positionMs = positionMs.coerceAtLeast(0),
                positionUpdatedAtMs = now(),
            )
        }
    }

    fun release() {
        runOnMain {
            mainHandler.removeCallbacks(poller)
            exo.removeListener(listener)
            exo.release()
        }
    }

    // ---------------------------------------------------------------------

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    companion object {
        private const val TAG = "MusicPlayer"
        /** How often to refresh position while playing. 250 ms is enough
         *  for a smooth progress bar and Phase 9 sync accuracy. */
        private const val POSITION_POLL_MS = 250L
    }
}
