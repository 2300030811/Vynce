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
fun playAllSongs(
    title: String,
    songs: List<SaavnSong>,
    playerConnection: PlayerConnection?,
    startIndex: Int = 0,
    shuffle: Boolean = false,
) {
    playerConnection ?: return
    val queueSongs = if (shuffle) songs.shuffled() else songs
    if (queueSongs.isEmpty()) return
    val metadataList = queueSongs.map { it.toSaavnMediaMetadata() }
    val queueStartIndex = if (shuffle) 0 else startIndex.coerceIn(metadataList.indices)
    playerConnection.playQueue(
        ListQueue(
            title = title.ifBlank { "JioSaavn" },
            items = metadataList,
            startIndex = queueStartIndex
        )
    )
}
