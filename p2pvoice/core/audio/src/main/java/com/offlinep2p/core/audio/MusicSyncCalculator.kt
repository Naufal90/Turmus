package com.offlinep2p.core.audio

import kotlin.math.abs

/**
 * Deterministic music-sync math (AGENTS.md §15, §44).
 * Kept pure/side-effect-free so it can be unit tested without an emulator.
 */
class MusicSyncCalculator(
    /** Playback deviation below which no correction is applied. */
    val smallThresholdMs: Long = 30,
    /** Playback deviation above which we prefer a hard seek. */
    val largeThresholdMs: Long = 500,
) {
    enum class Action { None, GentleSpeedCorrection, Seek }

    /**
     * @param hostPositionMs host's current playback position
     * @param hostTimestampMs monotonic time on host when the sync was sent
     * @param nowTimestampMs client's monotonic time when the packet is processed
     * @param localPositionMs client's current playback position
     * @param oneWayLatencyMs estimated one-way network latency
     */
    fun decide(
        hostPositionMs: Long,
        hostTimestampMs: Long,
        nowTimestampMs: Long,
        localPositionMs: Long,
        oneWayLatencyMs: Long,
    ): Action {
        val elapsed = (nowTimestampMs - hostTimestampMs) + oneWayLatencyMs
        val expectedLocal = hostPositionMs + elapsed
        val diff = abs(expectedLocal - localPositionMs)
        return when {
            diff < smallThresholdMs -> Action.None
            diff < largeThresholdMs -> Action.GentleSpeedCorrection
            else -> Action.Seek
        }
    }
}
