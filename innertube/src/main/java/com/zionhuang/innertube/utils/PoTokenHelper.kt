package com.zionhuang.innertube.utils

import com.zionhuang.innertube.models.YouTubeClient

object PoTokenHelper {
    val CLIENT_PRIORITY = listOf(
        YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER, // non-IP-bound, no login needed
        YouTubeClient.WEB,                             // non-IP-bound, needs NewPipe cipher
        YouTubeClient.IOS,                             // IP-bound (fallback only)
        YouTubeClient.ANDROID_MUSIC,
        YouTubeClient.ANDROID,
        YouTubeClient.WEB_REMIX
    )
    fun isRetryableError(code: Int) = code == 403 || code == 429
}

