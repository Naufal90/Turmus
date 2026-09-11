package com.offlinep2p.feature.music

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Reads local audio files from MediaStore (AGENTS.md §14).
 *
 * Everything here is `withContext(Dispatchers.IO)` — the caller may
 * invoke [scan] from any coroutine and get back a fully materialised
 * list of [PlaylistItem]. There is no observation of MediaStore
 * changes at this layer: the UI triggers a re-scan when the user pulls
 * to refresh, or when the READ_MEDIA_AUDIO permission is first granted.
 *
 * Deliberately NOT a fake — every field comes from the platform. If a
 * device holds zero audio files the returned list is empty; callers
 * render an empty-state message instead of populating with mock rows.
 */
class MusicLibrary(private val context: Context) {

    @SuppressLint("InlinedApi")
    suspend fun scan(): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
        )
        // Filter: is-music AND at least 5 s long (skips notifications, ringtones)
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val out = mutableListOf<PlaylistItem>()
        context.contentResolver.query(uri, projection, selection, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: continue
                val artist = c.getString(artistCol)
                val album = c.getString(albumCol)
                val durationMs = c.getLong(durationCol)
                val sizeBytes = c.getLong(sizeCol)
                val localUri = ContentUris.withAppendedId(uri, id).toString()

                out += PlaylistItem(
                    contentId = contentId(title, artist, durationMs, sizeBytes),
                    title = title,
                    artist = artist?.takeIf { it.isNotBlank() && it != "<unknown>" },
                    album = album?.takeIf { it.isNotBlank() && it != "<unknown>" },
                    durationMs = durationMs,
                    localUri = localUri,
                )
            }
        }
        out
    }

    companion object {
        /**
         * Deterministic id derived from stable content-adjacent fields.
         * Two files with the same title/artist/duration/size get the
         * same id on both peers — that is what Phase 9 sync needs.
         *
         * NOT a cryptographic guarantee — just enough entropy to avoid
         * cross-track collisions in a personal library.
         */
        fun contentId(title: String, artist: String?, durationMs: Long, sizeBytes: Long): String {
            val src = "${title.lowercase()}|${artist?.lowercase() ?: ""}|$durationMs|$sizeBytes"
            val md = MessageDigest.getInstance("SHA-1")
            val hash = md.digest(src.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(2 * hash.size)
            for (b in hash) sb.append("%02x".format(b.toInt() and 0xff))
            return sb.substring(0, 16) // 64-bit id is plenty
        }
    }
}
