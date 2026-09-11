package com.offlinep2p.feature.music

/**
 * Local music item scanned from MediaStore.
 *
 * [contentId] is a deterministic content-derived id (AGENTS.md §43) so
 * host and client can refer to the same track without relying on the
 * filename alone — two files with the same name but different bytes get
 * different ids.
 *
 * [localUri] is a `content://` MediaStore URI on the DEVICE that owns
 * the file. It is NOT sent to the peer — Phase 9 will send only
 * [contentId] + [title] + [durationMs] and let each side resolve to
 * whatever local URI it has for the same track.
 */
data class PlaylistItem(
    val contentId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val localUri: String,
)
