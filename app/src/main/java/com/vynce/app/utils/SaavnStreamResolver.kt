package com.vynce.app.utils

import android.util.Log
import android.util.LruCache
import androidx.media3.common.util.UnstableApi
import com.zionhuang.jiosaavn.JioSaavn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class SaavnStreamResolver @Inject constructor() {
    private val TAG = "SaavnStreamResolver"
    private val streamUrlCache = LruCache<String, String>(50)

    /**
     * Resolves a Saavn ID to a stream URL.
     * This method blocks the calling thread, so it MUST NOT be called from the Main thread.
     * It is designed to be used within ExoPlayer's ResolvingDataSource resolver.
     */
    fun resolve(saavnId: String): String? {
        val cachedUrl = streamUrlCache.get(saavnId)
        if (cachedUrl != null) return cachedUrl

        return try {
            // This is called from ExoPlayer's internal loading thread.
            // Using runBlocking is necessary to bridge the gap between ExoPlayer's 
            // synchronous API and JioSaavn's asynchronous API.
            val song = runBlocking(Dispatchers.IO) {
                JioSaavn.getSong(saavnId)
            }
            val streamUrl = with(JioSaavn) { song?.streamUrl() }
            if (streamUrl != null) {
                streamUrlCache.put(saavnId, streamUrl)
            }
            streamUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve Saavn stream URL for $saavnId", e)
            null
        }
    }
}
