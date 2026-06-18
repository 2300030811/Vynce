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
    
    private data class CachedUrl(val url: String, val resolvedAt: Long)
    private val streamUrlCache = LruCache<String, CachedUrl>(50)

    /**
     * Resolves a Saavn ID to a stream URL.
     * This method blocks the calling thread, so it MUST NOT be called from the Main thread.
     * It is designed to be used within ExoPlayer's ResolvingDataSource resolver.
     */
    fun resolve(saavnId: String): String? {
        val cached = synchronized(streamUrlCache) {
            streamUrlCache.get(saavnId)
        }
        if (cached != null) {
            val age = android.os.SystemClock.elapsedRealtime() - cached.resolvedAt
            if (age < 2 * 60 * 60 * 1000L) { // 2 hours
                return cached.url
            } else {
                synchronized(streamUrlCache) {
                    streamUrlCache.remove(saavnId)
                }
            }
        }

        return try {
            // This is called from ExoPlayer's internal loading thread.
            // Using runBlocking is necessary to bridge the gap between ExoPlayer's 
            // synchronous API and JioSaavn's asynchronous API.
            val song = runBlocking(Dispatchers.IO) {
                JioSaavn.getSong(saavnId)
            }
            val streamUrl = with(JioSaavn) { song?.streamUrl() }
            if (streamUrl != null) {
                synchronized(streamUrlCache) {
                    streamUrlCache.put(saavnId, CachedUrl(streamUrl, android.os.SystemClock.elapsedRealtime()))
                }
            }
            streamUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve Saavn stream URL for $saavnId", e)
            null
        }
    }

    /**
     * Invalidates a cached stream URL for a given Saavn ID.
     */
    fun invalidate(saavnId: String) {
        synchronized(streamUrlCache) {
            streamUrlCache.remove(saavnId)
        }
    }
}
