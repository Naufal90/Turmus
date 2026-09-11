package com.offlinep2p.feature.music

/**
 * Public playback state for the UI + Phase 9 sync engine
 * (AGENTS.md §14, §15).
 *
 * Kept as an immutable data class so state transitions are unambiguous
 * and the sync layer can diff two snapshots to decide what to send.
 */
data class PlaybackState(
    val currentItem: PlaylistItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Local timestamp when the position was last updated. Used by the
     *  sync engine to extrapolate the peer's expected position. */
    val positionUpdatedAtMs: Long = 0L,
)
