package com.vynce.app.models

import com.zionhuang.jiosaavn.SaavnSong
import com.zionhuang.jiosaavn.SaavnArtist
import com.zionhuang.jiosaavn.SaavnAlbum
import com.zionhuang.jiosaavn.SaavnPlaylist

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
