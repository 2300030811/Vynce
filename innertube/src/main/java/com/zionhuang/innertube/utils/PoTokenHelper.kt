package com.zionhuang.innertube.utils

import com.zionhuang.innertube.models.YouTubeClient

object PoTokenHelper {
    
    // Retry with fallback clients on 403
    val CLIENT_PRIORITY = listOf(
        YouTubeClient.ANDROID_MUSIC,
        YouTubeClient.ANDROID,
        YouTubeClient.WEB_REMIX
    )
    
    fun isRetryableError(code: Int): Boolean = code == 403 || code == 429
}
