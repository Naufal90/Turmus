package com.offlinep2p.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicSyncCalculatorTest {
    private val calc = MusicSyncCalculator()

    @Test
    fun `no correction when local is close`() {
        val action = calc.decide(
            hostPositionMs = 10_000,
            hostTimestampMs = 1_000,
            nowTimestampMs = 1_040,
            localPositionMs = 10_050,
            oneWayLatencyMs = 15,
        )
        assertEquals(MusicSyncCalculator.Action.None, action)
    }

    @Test
    fun `gentle correction for moderate drift`() {
        val action = calc.decide(
            hostPositionMs = 10_000,
            hostTimestampMs = 1_000,
            nowTimestampMs = 1_040,
            localPositionMs = 10_100,
            oneWayLatencyMs = 15,
        )
        assertEquals(MusicSyncCalculator.Action.GentleSpeedCorrection, action)
    }

    @Test
    fun `hard seek for large drift`() {
        val action = calc.decide(
            hostPositionMs = 10_000,
            hostTimestampMs = 1_000,
            nowTimestampMs = 1_040,
            localPositionMs = 3_000,
            oneWayLatencyMs = 15,
        )
        assertEquals(MusicSyncCalculator.Action.Seek, action)
    }
}
