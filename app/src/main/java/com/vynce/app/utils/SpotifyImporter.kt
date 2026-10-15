package com.vynce.app.utils

import com.vynce.app.data.spotify.SpotifyPlaylistFetcher
import com.vynce.app.data.spotify.SpotifyResolver
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.PlaylistEntity
import com.vynce.app.db.entities.PlaylistSongMap
import com.vynce.app.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

data class SpotifyImportResult(
    val playlistName: String,
    val resolvedCount: Int,
    val totalCount: Int
) {
    val isAllMatched: Boolean get() = resolvedCount == totalCount
    val skippedCount: Int get() = totalCount - resolvedCount

    override fun toString(): String =
        if (isAllMatched) "$playlistName ($totalCount tracks)"
        else "$playlistName ($resolvedCount of $totalCount tracks matched)"
}

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
    ): Result<SpotifyImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = extractPlaylistId(urlOrId)
                ?: throw IllegalArgumentException("Invalid Spotify Playlist link or ID")

            val playlist = SpotifyPlaylistFetcher.fetchPlaylist(playlistId)
                ?: throw IllegalStateException("Could not fetch playlist metadata from Spotify. Please check link.")

            val tracks = playlist.tracks
            if (tracks.isEmpty()) throw IllegalStateException("Spotify playlist has no tracks")

            val total = tracks.size
            val resolvedSongs = mutableListOf<MediaMetadata>()

            // Step 1: Network resolution outside any database transaction
            tracks.forEachIndexed { index, track ->
                onProgress(index + 1, total)
                val saavnSong = SpotifyResolver.resolveToSaavn(
                    trackName = track.name,
                    artistName = track.artist,
                    durationSeconds = track.durationMs / 1000
                )
                if (saavnSong != null) {
                    resolvedSongs.add(saavnSong.toSaavnMediaMetadata())
                }
            }

            if (resolvedSongs.isEmpty()) {
                throw IllegalStateException("None of the $total tracks could be matched from this playlist.")
            }

            val playlistName = playlist.name.ifBlank { "Imported Spotify Playlist" }
            val spotifyBrowseId = "spotify:$playlistId"

            // Step 2: Atomic persistence in a single Room transaction
            // Ensures parent playlist, songs, and mappings are committed in valid order, or rolled back on failure.
            database.runInTransaction {
                // Idempotently reuse existing playlist if previously imported, or create new
                val existingPlaylist = findPlaylistByBrowseId(spotifyBrowseId)
                val targetPlaylist = if (existingPlaylist != null) {
                    val updated = existingPlaylist.copy(
                        name = playlistName,
                        thumbnailUrl = playlist.coverUrl.ifBlank { existingPlaylist.thumbnailUrl }
                    )
                    update(updated)
                    clearPlaylist(updated.id)
                    updated
                } else {
                    val created = PlaylistEntity(
                        id = PlaylistEntity.generatePlaylistId(),
                        name = playlistName,
                        browseId = spotifyBrowseId,
                        bookmarkedAt = LocalDateTime.now(),
                        thumbnailUrl = playlist.coverUrl.ifBlank { null },
                        isEditable = true,
                        isLocal = true
                    )
                    insert(created)
                    created
                }

                // Insert all resolved songs (and their artist/album mappings)
                resolvedSongs.forEach { songMetadata ->
                    insert(songMetadata)
                }

                // Insert playlist-to-song mappings with sequential 0-indexed positions
                resolvedSongs.forEachIndexed { position, songMetadata ->
                    insert(
                        PlaylistSongMap(
                            playlistId = targetPlaylist.id,
                            songId = songMetadata.id,
                            position = position
                        )
                    )
                }
            }

            SpotifyImportResult(
                playlistName = playlistName,
                resolvedCount = resolvedSongs.size,
                totalCount = total
            )
        }
    }
}
