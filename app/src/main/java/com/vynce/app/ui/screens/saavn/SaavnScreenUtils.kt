package com.vynce.app.ui.screens.saavn

import com.vynce.app.playback.PlayerConnection
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.utils.toSaavnMediaMetadata
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

/**
 * Play a list of JioSaavn songs through the proper QueueBoard pipeline.
 * Using playerConnection.playQueue() ensures MusicService/QueueBoard get correctly
 * notified, which prevents crashes from getCurrentQueue() returning null.
 */
fun playAllSongs(songs: List<SaavnSong>, playerConnection: PlayerConnection?) {
    if (songs.isEmpty()) return
    playerConnection ?: return
    val metadataList = songs.map { it.toSaavnMediaMetadata() }
    playerConnection.playQueue(
        ListQueue(
            title = songs.firstOrNull()?.name ?: "Playlist",
            items = metadataList
        )
    )
}
