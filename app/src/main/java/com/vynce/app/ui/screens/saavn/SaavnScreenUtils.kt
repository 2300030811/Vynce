package com.vynce.app.ui.screens.saavn

import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.vynce.app.playback.PlayerConnection
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnSong

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

fun formatFollowerCount(count: String): String {
    val n = count.toLongOrNull() ?: return count
    return when {
        n >= 1_000_000 -> "${(n / 1_000_000)}M"
        n >= 1_000 -> "${(n / 1_000)}K"
        else -> n.toString()
    }
}

fun playAllSongs(songs: List<SaavnSong>, playerConnection: PlayerConnection?) {
    if (songs.isEmpty()) return
    with(JioSaavn) {
        val mediaItems = songs.mapNotNull { song ->
            song.streamUrl()?.let { url ->
                MediaItem.Builder()
                    .setMediaId("saavn:${song.id}")
                    .setUri(url)
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(song.name)
                        .setArtist(song.artistNames())
                        .setArtworkUri(song.thumbnailUrl()?.replace("http://","https://")?.toUri())
                        .build())
                    .build()
            }
        }
        playerConnection?.player?.apply {
            clearMediaItems()
            addMediaItems(mediaItems)
            prepare()
            playWhenReady = true
            play()
        }
    }
}
