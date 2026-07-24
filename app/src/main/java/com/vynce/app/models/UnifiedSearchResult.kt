package com.vynce.app.models

import com.vynce.jiosaavn.SaavnSong
import com.vynce.jiosaavn.SaavnArtist
import com.vynce.jiosaavn.SaavnAlbum
import com.vynce.jiosaavn.SaavnPlaylist

data class UnifiedSearchResult(
    val query: String,
    val songs: List<SaavnSong>,
    val artists: List<SaavnArtist>,
    val albums: List<SaavnAlbum>,
    val playlists: List<SaavnPlaylist>,
    val topResult: TopResult?
)

sealed interface TopResult {
    data class Artist(val artist: SaavnArtist) : TopResult
    data class Song(val song: SaavnSong) : TopResult
    data class Album(val album: SaavnAlbum) : TopResult
    data class Playlist(val playlist: SaavnPlaylist) : TopResult
}
