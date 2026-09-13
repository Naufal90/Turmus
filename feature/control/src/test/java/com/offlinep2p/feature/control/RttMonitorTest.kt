package com.offlinep2p.feature.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RttMonitorTest {

    /** Controllable monotonic clock (nanoseconds). */
    private class FakeClock(var ns: Long = 0L) { fun ns(): Long = currentNs }

    @Test
    fun `pong matching our ping updates RTT`() {
        val clock = FakeClock()
        val rtt = RttMonitor(nowNs = clock::ns, alpha = 1.0) // pure last-sample
        val ping = rtt.buildPing(nowWallMs = 100)
        clock.currentNs += 50_000_000 // 50 ms later
        // Peer would generate a PONG; we simulate its bytes by asking the monitor to
        // build one from the ping payload (as if we were the peer).
        val pong = rtt.buildPong(ping, nowWallMs = 150)!!
        // Now feed it back — this is the round trip completing on our side.
        val elapsed = rtt.onPongReceived(pong)
        assertNotNull(elapsed)
        assertEquals(50L, elapsed)
        assertEquals(50L, rtt.rttMs.value)
    }

    @Test
    fun `EMA smooths successive samples`() {
        val clock = FakeClock()
        val rtt = RttMonitor(nowNs = clock::ns, alpha = 0.5)
        val p1 = rtt.buildPing(0); clock.currentNs += 20_000_000
        rtt.onPongReceived(rtt.buildPong(p1, 0)!!)
        val p2 = rtt.buildPing(0); clock.currentNs += 100_000_000
        rtt.onPongReceived(rtt.buildPong(p2, 0)!!)
        // First sample 20, second 100 -> EMA(0.5) = 60
        assertEquals(60L, rtt.rttMs.value)
    }

    @Test
    fun `unknown nonce is ignored`() {
        val clock = FakeClock()
        val rtt = RttMonitor(nowNs = clock::ns)
        val stray = ControlCodec.envelope(
            com.offlinep2p.core.protocol.MessageType.PONG,
            ControlCodec.encodePayload(
                com.offlinep2p.core.protocol.PongPayload(nonce = 9999L, remoteTimestamp = 0L),
                com.offlinep2p.core.protocol.PongPayload.serializer(),
            ),
            nowMs = 0L,
        )
        assertNull(rtt.onPongReceived(stray))
        assertNull(rtt.rttMs.value)
    }
}
