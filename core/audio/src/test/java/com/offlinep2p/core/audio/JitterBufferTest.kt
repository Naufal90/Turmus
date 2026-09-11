package com.offlinep2p.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterBufferTest {

    private fun payload(tag: Int) = byteArrayOf(tag.toByte())

    @Test
    fun `does not release until target depth reached`() {
        val jb = JitterBuffer(targetDepth = 3, maxDepth = 8)
        jb.push(0, payload(0))
        jb.push(1, payload(1))
        assertNull(jb.pollExpected())          // still priming
    }

    @Test
    fun `in-order playout works after priming`() {
        val jb = JitterBuffer(targetDepth = 3, maxDepth = 8)
        jb.push(0, payload(0)); jb.push(1, payload(1)); jb.push(2, payload(2))
        assertArrayEquals(payload(0), jb.pollExpected())
        assertArrayEquals(payload(1), jb.pollExpected())
        assertArrayEquals(payload(2), jb.pollExpected())
        assertNull(jb.pollExpected())          // gap: caller inserts silence
    }

    @Test
    fun `out-of-order arrival is reordered`() {
        val jb = JitterBuffer(targetDepth = 3, maxDepth = 8)
        jb.push(2, payload(2)); jb.push(0, payload(0)); jb.push(1, payload(1))
        assertArrayEquals(payload(0), jb.pollExpected())
        assertArrayEquals(payload(1), jb.pollExpected())
        assertArrayEquals(payload(2), jb.pollExpected())
    }

    @Test
    fun `duplicate is dropped`() {
        val jb = JitterBuffer(targetDepth = 3, maxDepth = 8)
        assertTrue(jb.push(0, payload(0)))
        assertFalse(jb.push(0, payload(0)))
    }

    @Test
    fun `missing sequence produces a concealment gap`() {
        val jb = JitterBuffer(targetDepth = 3, maxDepth = 8)
        jb.push(0, payload(0)); jb.push(1, payload(1)); jb.push(3, payload(3))
        assertArrayEquals(payload(0), jb.pollExpected())
        assertArrayEquals(payload(1), jb.pollExpected())
        assertNull(jb.pollExpected())          // seq 2 missing -> conceal
        assertArrayEquals(payload(3), jb.pollExpected())
    }

    @Test
    fun `overflow drops oldest`() {
        val jb = JitterBuffer(targetDepth = 2, maxDepth = 3)
        for (i in 0..4) jb.push(i, payload(i))
        // Depth capped: oldest were dropped; head should be seq 2
        assertNotNull(jb.pollExpected())        // primes on seq 2 (0 and 1 evicted)
    }

    @Test
    fun `late packet after playout is refused`() {
        val jb = JitterBuffer(targetDepth = 2, maxDepth = 8)
        jb.push(0, payload(0)); jb.push(1, payload(1))
        jb.pollExpected(); jb.pollExpected()
        // seq 0 is now stale
        assertFalse(jb.push(0, payload(0)))
    }
}
