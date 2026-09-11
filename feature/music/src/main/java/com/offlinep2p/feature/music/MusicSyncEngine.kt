package com.offlinep2p.feature.music

import com.offlinep2p.core.audio.MusicSyncCalculator
import com.offlinep2p.core.common.LogEvents
import com.offlinep2p.core.common.logEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the host/client sync loop for shared music playback
 * (AGENTS.md §15).
 *
 *   HOST   : observes [MusicPlayer.state], sends MUSIC_PLAY /
 *            MUSIC_PAUSE / MUSIC_SEEK on transitions, and a
 *            MUSIC_SYNC heartbeat once a second so the client can
 *            reconcile drift.
 *
 *   CLIENT : receives control messages via [applyRemote*] methods,
 *            uses [MusicSyncCalculator] against the current local
 *            playback state, and executes the recommended correction
 *            (None / GentleSpeed / Seek).
 *
 * The engine does NOT hold ExoPlayer directly — it commands
 * [MusicPlayer] through the same [play/pause/resume/seekTo] contract
 * used by the UI, so nothing about the playback engine leaks here.
 *
 * Deliberately NOT a fake — every timestamp comes from an injected
 * clock, every RTT reading from the real [RttMonitor] via
 * [oneWayLatencyMs], and every correction actually calls
 * `MusicPlayer.seekTo(...)`.
 */
class MusicSyncEngine(
    private val player: MusicPlayer,
    /** Called by the engine when it wants to send a control message.
     *  The wiring layer serialises it into the protocol envelope and
     *  hands it to the transport. Returns true if actually sent. */
    private val sendControl: (SyncOutbound) -> Boolean,
    /** Live one-way latency estimate — half of the RTT reading. */
    private val oneWayLatencyMs: () -> Long = { 0L },
    private val calculator: MusicSyncCalculator = MusicSyncCalculator(),
    private val now: () -> Long = System::currentTimeMillis,
    private val heartbeatIntervalMs: Long = 1_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var heartbeatJob: Job? = null
    private var observerJob: Job? = null

    private val _role = MutableStateFlow(MusicRole.None)
    val role: StateFlow<MusicRole> = _role.asStateFlow()

    /** True when the client has recently received a MUSIC_SYNC. UI uses
     *  this to render "in sync" vs "waiting for host". */
    private val _linked = MutableStateFlow(false)
    val linked: StateFlow<Boolean> = _linked.asStateFlow()

    /** Latest correction the client applied — useful for the Voice/Music
     *  status card. Not used by the pipeline itself. */
    private val _lastCorrection = MutableStateFlow<MusicSyncCalculator.Action?>(null)
    val lastCorrection: StateFlow<MusicSyncCalculator.Action?> = _lastCorrection.asStateFlow()

    private var lastSentIsPlaying: Boolean? = null
    private var lastSentSongId: String? = null

    private val TAG = "MusicSync"

    // ---------------------------------------------------------------------
    // Role control
    // ---------------------------------------------------------------------

    fun becomeHost() {
        stopJobs()
        _role.value = MusicRole.Host
        _linked.value = true
        // Start heartbeat + transition observer.
        observerJob = scope.launch {
            player.state.collect { st -> onHostStateChanged(st) }
        }
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                sendHeartbeat()
            }
        }
        logEvent(TAG, LogEvents.VOICE_START, "role=Host")
    }

    fun becomeClient() {
        stopJobs()
        _role.value = MusicRole.Client
        _linked.value = false
        _lastCorrection.value = null
        // Client stops any locally-initiated playback; MUSIC_PLAY will
        // set it up when it arrives.
        player.stop()
    }

    fun leave() {
        stopJobs()
        _role.value = MusicRole.None
        _linked.value = false
        _lastCorrection.value = null
    }

    private fun stopJobs() {
        heartbeatJob?.cancel(); heartbeatJob = null
        observerJob?.cancel(); observerJob = null
        lastSentIsPlaying = null
        lastSentSongId = null
    }

    // ---------------------------------------------------------------------
    // HOST → outbound
    // ---------------------------------------------------------------------

    private fun onHostStateChanged(state: PlaybackState) {
        val item = state.currentItem ?: run {
            // Playback fully stopped. Emit a STOP transition once.
            if (lastSentSongId != null) {
                sendControl(SyncOutbound.Stop(now()))
                lastSentSongId = null
                lastSentIsPlaying = null
            }
            return
        }

        val songChanged = lastSentSongId != item.contentId
        val playChanged = lastSentIsPlaying != state.isPlaying

        if (songChanged) {
            sendControl(
                SyncOutbound.Play(
                    songId = item.contentId,
                    title = item.title,
                    durationMs = item.durationMs,
                    positionMs = state.positionMs,
                    hostTimestamp = now(),
                    isPlaying = state.isPlaying,
                )
            )
            lastSentSongId = item.contentId
            lastSentIsPlaying = state.isPlaying
        } else if (playChanged) {
            sendControl(
                if (state.isPlaying) SyncOutbound.Resume(state.positionMs, now())
                else SyncOutbound.Pause(state.positionMs, now())
            )
            lastSentIsPlaying = state.isPlaying
        }
    }

    private fun sendHeartbeat() {
        val st = player.state.value
        val item = st.currentItem ?: return
        sendControl(
            SyncOutbound.Sync(
                songId = item.contentId,
                positionMs = st.positionMs,
                hostTimestamp = now(),
                isPlaying = st.isPlaying,
            )
        )
    }

    /** Called by the UI wrapper when the local user seeked on the host. */
    fun hostSeekedTo(positionMs: Long) {
        val item = player.state.value.currentItem ?: return
        sendControl(
            SyncOutbound.Seek(
                songId = item.contentId,
                positionMs = positionMs,
                hostTimestamp = now(),
            )
        )
    }

    // ---------------------------------------------------------------------
    // CLIENT → inbound
    // ---------------------------------------------------------------------

    /**
     * The client received a MUSIC_PLAY message. The [libraryLookup]
     * is asked to resolve `songId` to a local [PlaylistItem]; if it
     * cannot, the client falls back to a silent "song unavailable"
     * state — never plays a synthetic file (§1.2).
     */
    fun applyRemotePlay(
        songId: String,
        libraryLookup: (String) -> PlaylistItem?,
        positionMs: Long,
        hostTimestamp: Long,
        isPlaying: Boolean,
    ) {
        if (_role.value != MusicRole.Client) return
        val local = libraryLookup(songId)
        if (local == null) {
            _lastCorrection.value = null
            _linked.value = true
            logEvent(TAG, LogEvents.CODEC_ERROR, "client missing song $songId")
            return
        }
        player.play(local)
        // Immediately seek to where the host is now + one-way latency.
        val extrapolated = extrapolatedHostPosition(positionMs, hostTimestamp, isPlaying)
        player.seekTo(extrapolated)
        if (!isPlaying) player.pause()
        _linked.value = true
        _lastCorrection.value = MusicSyncCalculator.Action.Seek
    }

    fun applyRemotePause(positionMs: Long) {
        if (_role.value != MusicRole.Client) return
        player.pause()
        player.seekTo(positionMs)
        _linked.value = true
    }

    fun applyRemoteResume(positionMs: Long, hostTimestamp: Long) {
        if (_role.value != MusicRole.Client) return
        val extrapolated = extrapolatedHostPosition(positionMs, hostTimestamp, isPlaying = true)
        player.seekTo(extrapolated)
        player.resume()
        _linked.value = true
    }

    fun applyRemoteSeek(positionMs: Long, hostTimestamp: Long, isPlaying: Boolean) {
        if (_role.value != MusicRole.Client) return
        val extrapolated = extrapolatedHostPosition(positionMs, hostTimestamp, isPlaying)
        player.seekTo(extrapolated)
        _linked.value = true
        _lastCorrection.value = MusicSyncCalculator.Action.Seek
    }

    fun applyRemoteStop() {
        if (_role.value != MusicRole.Client) return
        player.stop()
        _linked.value = true
    }

    /** Heartbeat: reconcile client's position against host's. */
    fun applyRemoteSync(
        songId: String,
        positionMs: Long,
        hostTimestamp: Long,
        isPlaying: Boolean,
    ) {
        if (_role.value != MusicRole.Client) return
        val local = player.state.value
        val currentSong = local.currentItem?.contentId
        if (currentSong != songId) {
            // Host is on a different track than the client — the next
            // MUSIC_PLAY will resolve it; do nothing here.
            _linked.value = true
            return
        }

        val action = calculator.decide(
            hostPositionMs = positionMs,
            hostTimestampMs = hostTimestamp,
            nowTimestampMs = now(),
            localPositionMs = local.positionMs,
            oneWayLatencyMs = oneWayLatencyMs(),
        )
        when (action) {
            MusicSyncCalculator.Action.None -> Unit
            MusicSyncCalculator.Action.GentleSpeedCorrection -> {
                // Media3's playbackParameters can nudge speed; for the
                // first landing we conservatively behave like Seek —
                // Phase 11 will add rate-based correction (setPlaybackParameters).
                val target = extrapolatedHostPosition(positionMs, hostTimestamp, isPlaying)
                player.seekTo(target)
            }
            MusicSyncCalculator.Action.Seek -> {
                val target = extrapolatedHostPosition(positionMs, hostTimestamp, isPlaying)
                player.seekTo(target)
            }
        }

        // Reconcile play/pause too, in case they diverged mid-stream.
        if (isPlaying && !local.isPlaying) player.resume()
        else if (!isPlaying && local.isPlaying) player.pause()

        _lastCorrection.value = action
        _linked.value = true
    }

    // ---------------------------------------------------------------------

    private fun extrapolatedHostPosition(
        hostPositionMs: Long,
        hostTimestampMs: Long,
        isPlaying: Boolean,
    ): Long {
        if (!isPlaying) return hostPositionMs
        val elapsed = (now() - hostTimestampMs) + oneWayLatencyMs()
        return (hostPositionMs + elapsed).coerceAtLeast(0)
    }
}

/**
 * Serialisable-friendly outbound message shapes. Kept in this file so
 * the file that owns the state machine also owns the wire contract —
 * making it obvious when a new sync command needs both sides updated.
 */
sealed interface SyncOutbound {
    data class Play(
        val songId: String,
        val title: String,
        val durationMs: Long,
        val positionMs: Long,
        val hostTimestamp: Long,
        val isPlaying: Boolean,
    ) : SyncOutbound
    data class Pause(val positionMs: Long, val hostTimestamp: Long) : SyncOutbound
    data class Resume(val positionMs: Long, val hostTimestamp: Long) : SyncOutbound
    data class Seek(val songId: String, val positionMs: Long, val hostTimestamp: Long) : SyncOutbound
    data class Sync(
        val songId: String,
        val positionMs: Long,
        val hostTimestamp: Long,
        val isPlaying: Boolean,
    ) : SyncOutbound
    data class Stop(val hostTimestamp: Long) : SyncOutbound
}
