package com.offlinep2p.feature.music

/**
 * Role of the local peer in a shared music session (AGENTS.md §15).
 *
 * • [None]   — no shared session; player behaves as a solo device.
 * • [Host]   — the local device owns playback. UI actions (play,
 *              pause, seek) are executed locally AND sent to the peer.
 * • [Client] — the peer owns playback. UI actions are DISABLED except
 *              for local volume; playback state is driven by incoming
 *              MUSIC_* control messages, with drift correction.
 */
enum class MusicRole { None, Host, Client }
