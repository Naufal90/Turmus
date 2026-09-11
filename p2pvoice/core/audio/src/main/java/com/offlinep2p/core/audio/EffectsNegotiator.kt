package com.offlinep2p.core.audio

/**
 * Decides which capture-side effects the LOCAL device should enable,
 * given (a) what the local device supports and (b) what the peer
 * reports via DEVICE_INFO (AGENTS.md §10).
 *
 * Rules (pure — no Android imports so it is trivially testable):
 *
 *   • AEC lives on the CAPTURE side (it needs the local reference
 *     signal), so it is only ever enabled locally. Peer's AEC state
 *     is not relevant to us.
 *
 *   • NS and AGC processed on the sender ARE audible to the peer
 *     because we send the processed signal. If the peer already
 *     applies NS/AGC on THEIR playout side… that would be double-
 *     processing on their headset. But since both sides send audio,
 *     the sender is the correct place to apply NS/AGC. Therefore:
 *       - We enable NS/AGC locally whenever we have them (they act on
 *         OUR outgoing stream — this is not double-processing).
 *       - If the peer LACKS NS but we have it, we still enable ours
 *         (harmless to peer).
 *       - The one case we DISABLE ours is when the user has manually
 *         overridden the toggle (Phase 11 settings hook).
 *
 * The negotiator is therefore mostly about honouring device capability
 * + user override; the "peer-side" input is kept in the signature so
 * Phase 11 can add stricter policies without changing callers.
 */
object EffectsNegotiator {

    data class UserOverrides(
        val forceAecOff: Boolean = false,
        val forceNsOff: Boolean = false,
        val forceAgcOff: Boolean = false,
    )

    fun decide(
        local: AudioEffectSupport,
        peer: AudioEffectSupport?,
        overrides: UserOverrides = UserOverrides(),
    ): EffectSelection {
        // `peer` is intentionally read here so a future stricter policy
        // (e.g. "peer says its mic already applies NS at 30 dB, so drop
        // ours by 10 dB") has a place to plug in — but for Phase 5 the
        // decision is: turn on whatever we have, unless the user says no.
        @Suppress("UNUSED_VARIABLE") val peerInfo = peer

        return EffectSelection(
            aec = local.aec && !overrides.forceAecOff,
            ns  = local.ns  && !overrides.forceNsOff,
            agc = local.agc && !overrides.forceAgcOff,
        )
    }
}
