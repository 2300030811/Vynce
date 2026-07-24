package com.vynce.app.utils

import com.vynce.app.extensions.decodeHtml
import com.vynce.app.models.MediaMetadata
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.playback.queues.ListQueue
import com.vynce.jiosaavn.JioSaavn
import com.vynce.jiosaavn.SaavnSong

/**
 * Convert a Saavn image URL to high resolution HTTPS
 */
fun String.saavnHighResHttps(): String = 
    replace("http://", "https://").replace("150x150", "500x500")

/**
 * Convert a SaavnSong into the app's internal MediaMetadata format.
 */
fun SaavnSong.toSaavnMediaMetadata(): MediaMetadata {
    with(JioSaavn) {
        val finalId = if (this@toSaavnMediaMetadata.id.startsWith("soundcloud:") || this@toSaavnMediaMetadata.id.startsWith("bandcamp:")) {
            this@toSaavnMediaMetadata.id
        } else {
            "saavn:${this@toSaavnMediaMetadata.id}"
        }

        val albumMetadata = when {
            this@toSaavnMediaMetadata.id.startsWith("soundcloud:") -> {
                MediaMetadata.Album(
                    id = "soundcloud_album:soundcloud",
                    title = "SoundCloud"
                )
            }
            this@toSaavnMediaMetadata.id.startsWith("bandcamp:") -> {
                MediaMetadata.Album(
                    id = "bandcamp_album:bandcamp",
                    title = "Bandcamp"
                )
            }
            else -> {
                this@toSaavnMediaMetadata.album.takeIf { it.isNotEmpty() }?.let { albumName ->
                    MediaMetadata.Album(
                        id = "saavn_album:${albumName.hashCode()}",
                        title = albumName.decodeHtml()
                    )
                }
            }
        }

        return MediaMetadata(
            id = finalId,
            title = this@toSaavnMediaMetadata.name.decodeHtml()
                .replace(Regex("(?i)\\s*by\\s+.*"), "")
                .replace(Regex("\\s*\\([^)]*\\)"), "")
                .replace(Regex("\\s*\\[[^]]*\\]"), "")
                .trim(),
            artists = this@toSaavnMediaMetadata.artistNames().split(", ").map { name ->
                MediaMetadata.Artist(
                    id = null,
                    name = name.trim().decodeHtml()
                )
            },
            duration = this@toSaavnMediaMetadata.duration.toIntOrNull() ?: -1,
            thumbnailUrl = this@toSaavnMediaMetadata.thumbnailUrl()?.replace("http://", "https://"),
            album = albumMetadata,
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
