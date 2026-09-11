package com.offlinep2p.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsNegotiatorTest {

    @Test
    fun `enables everything the local device supports when no overrides`() {
        val local = AudioEffectSupport(aec = true, ns = true, agc = true)
        val decision = EffectsNegotiator.decide(local = local, peer = null)
        assertTrue(decision.aec); assertTrue(decision.ns); assertTrue(decision.agc)
    }

    @Test
    fun `disables effects the local device does not support`() {
        val local = AudioEffectSupport(aec = false, ns = true, agc = false)
        val decision = EffectsNegotiator.decide(local = local, peer = null)
        assertFalse(decision.aec); assertTrue(decision.ns); assertFalse(decision.agc)
    }

    @Test
    fun `user override wins over device capability`() {
        val local = AudioEffectSupport(aec = true, ns = true, agc = true)
        val decision = EffectsNegotiator.decide(
            local = local,
            peer = null,
            overrides = EffectsNegotiator.UserOverrides(
                forceAecOff = true, forceNsOff = false, forceAgcOff = true,
            ),
        )
        assertFalse(decision.aec); assertTrue(decision.ns); assertFalse(decision.agc)
    }

    @Test
    fun `peer capability is accepted as input without crashing`() {
        val local = AudioEffectSupport(aec = true, ns = true, agc = true)
        val peer = AudioEffectSupport(aec = false, ns = false, agc = false)
        val decision = EffectsNegotiator.decide(local = local, peer = peer)
        // Phase 5 policy: always honour local caps regardless of peer.
        assertEquals(EffectSelection(true, true, true), decision)
    }

    @Test
    fun `defaultsFor mirrors support flags`() {
        val support = AudioEffectSupport(aec = true, ns = false, agc = true)
        val sel = EffectSelection.defaultsFor(support)
        assertEquals(EffectSelection(true, false, true), sel)
    }
}
