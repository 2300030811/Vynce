package com.vynce.app.utils

import com.vynce.app.data.spotify.SpotifyPlaylistFetcher
import com.vynce.app.data.spotify.SpotifyResolver
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.PlaylistEntity
import com.vynce.app.db.entities.PlaylistSongMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

object SpotifyImporter {

    /**
     * Extracts a Spotify playlist ID from any URL or string.
     */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        val match = Regex("playlist[/:]+([a-zA-Z0-9]+)").find(trimmed)
        if (match != null) return match.groupValues[1]
        if (Regex("^[a-zA-Z0-9]{15,30}$").matches(trimmed)) return trimmed
        return null
    }

    /**
     * Imports a Spotify playlist into the local Vynce database.
     */
    suspend fun importPlaylist(
        urlOrId: String,
        database: MusicDatabase,
        onProgress: (Int, Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = extractPlaylistId(urlOrId)
                ?: throw IllegalArgumentException("Invalid Spotify Playlist link or ID")

            val playlist = SpotifyPlaylistFetcher.fetchPlaylist(playlistId)
                ?: throw IllegalStateException("Could not fetch playlist metadata from Spotify. Please check link.")

            val tracks = playlist.tracks
            if (tracks.isEmpty()) throw IllegalStateException("Spotify playlist has no tracks")

            val playlistName = playlist.name.ifBlank { "Imported Spotify Playlist" }
            val playlistEntity = PlaylistEntity(
                name = playlistName,
                bookmarkedAt = LocalDateTime.now(),
                thumbnailUrl = playlist.coverUrl.ifBlank { null }
            )
            database.query {
                insert(playlistEntity)
            }

            var resolvedCount = 0
            val total = tracks.size

            tracks.forEachIndexed { index, track ->
                onProgress(index + 1, total)
                val saavnSong = SpotifyResolver.resolveToSaavn(
                    trackName = track.name,
                    artistName = track.artist,
                    durationSeconds = track.durationMs / 1000
                )
                if (saavnSong != null) {
                    val mediaMetadata = saavnSong.toSaavnMediaMetadata()
                    database.query {
                        insert(mediaMetadata)
                        insert(
                            PlaylistSongMap(
                                playlistId = playlistEntity.id,
                                songId = mediaMetadata.id,
                                position = resolvedCount
                            )
                        )
                    }
                    resolvedCount++
                }
            }

            "$playlistName ($resolvedCount of $total tracks matched)"
        }
    }
}
