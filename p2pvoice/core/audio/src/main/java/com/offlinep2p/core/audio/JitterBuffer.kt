package com.offlinep2p.core.audio

import java.util.PriorityQueue

/**
 * Small ordered playout buffer for voice packets (AGENTS.md §9, §44).
 *
 * Real-time voice packets arrive with jitter, occasional reorder, and
 * occasional loss. This buffer:
 *
 *   • orders packets by sequence
 *   • drops duplicates
 *   • drops packets that are already too late (sequence < nextExpected)
 *   • caps depth to [maxDepth] so latency does not grow without bound
 *     — under overflow, the OLDEST packet is dropped (AGENTS.md §44:
 *     prefer low latency, drop old data instead of accumulating)
 *
 * Concealment (silence substitution for lost frames) is handled by the
 * consumer via [pollExpected], which returns null on gap so the caller
 * can insert a silence frame of [VoiceConfig.bytesPerFrame].
 */
class JitterBuffer(
    val targetDepth: Int = 3,
    val maxDepth: Int = 8,
) {
    private data class Entry(val sequence: Int, val payload: ByteArray) : Comparable<Entry> {
        override fun compareTo(other: Entry): Int = sequence.compareTo(other.sequence)
    }

    private val heap = PriorityQueue<Entry>()
    private val seen = HashSet<Int>()
    private var nextExpected: Int = -1
    private var primed = false

    val size: Int get() = heap.size

    /** Push a packet. Returns true if it was accepted. */
    fun push(sequence: Int, payload: ByteArray): Boolean {
        if (!seen.add(sequence)) return false                    // duplicate
        if (nextExpected >= 0 && sequence < nextExpected) return false  // too late

        heap.offer(Entry(sequence, payload))
        while (heap.size > maxDepth) {
            heap.poll()?.also { seen.remove(it.sequence) }        // drop oldest
        }
        return true
    }

    /**
     * Non-blocking playout tick.
     *
     * Returns:
     *   • the next expected packet's payload, or
     *   • null when the buffer is priming (below [targetDepth]) OR the
     *     expected sequence is missing — the caller inserts silence.
     */
    fun pollExpected(): ByteArray? {
        if (!primed) {
            if (heap.size < targetDepth) return null
            nextExpected = heap.peek()!!.sequence
            primed = true
        }
        val head = heap.peek() ?: return null
        return when {
            head.sequence == nextExpected -> {
                heap.poll()
                seen.remove(head.sequence)
                nextExpected += 1
                head.payload
            }
            head.sequence < nextExpected -> {                 // stale, discard
                heap.poll(); seen.remove(head.sequence); pollExpected()
            }
            else -> {                                          // gap, conceal
                nextExpected += 1
                null
            }
        }
    }

    fun reset() {
        heap.clear(); seen.clear()
        nextExpected = -1; primed = false
    }
}
