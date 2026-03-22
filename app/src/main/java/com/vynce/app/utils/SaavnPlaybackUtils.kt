package com.vynce.app.utils

import com.vynce.app.extensions.decodeHtml
import com.vynce.app.models.MediaMetadata
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.playback.queues.ListQueue
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnSong

/**
 * Convert a SaavnSong into the app's internal MediaMetadata format.
 */
fun SaavnSong.toSaavnMediaMetadata(): MediaMetadata {
    with(JioSaavn) {
        return MediaMetadata(
            id = "saavn:${this@toSaavnMediaMetadata.id}",
            title = this@toSaavnMediaMetadata.name.decodeHtml(),
            artists = this@toSaavnMediaMetadata.artistNames().split(", ").map { name ->
                MediaMetadata.Artist(
                    id = null,
                    name = name.trim().decodeHtml()
                )
            },
            duration = this@toSaavnMediaMetadata.duration.toIntOrNull() ?: -1,
            thumbnailUrl = this@toSaavnMediaMetadata.thumbnailUrl()?.replace("http://", "https://"),
            album = this@toSaavnMediaMetadata.album.takeIf { it.isNotEmpty() }?.let { albumName ->
                MediaMetadata.Album(
                    id = "saavn_album:${albumName.hashCode()}",
                    title = albumName.decodeHtml()
                )
            },
            genre = null
        )
    }
}

/**
 * Play a JioSaavn song through the proper QueueBoard pipeline.
 * This ensures the mini player and bottom sheet player appear correctly.
 */
fun playJioSaavnSong(song: SaavnSong, playerConnection: PlayerConnection?) {
    playerConnection ?: return
    val mediaMetadata = song.toSaavnMediaMetadata()
    playerConnection.playQueue(
        ListQueue(
            title = song.name,
            items = listOf(mediaMetadata)
        )
    )
}
