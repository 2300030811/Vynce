package com.vynce.app.utils

import android.util.Log
import android.util.LruCache
import androidx.media3.common.util.UnstableApi
import com.vynce.app.data.bandcamp.Bandcamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class BandcampStreamResolver @Inject constructor() {
    private val TAG = "BandcampStreamResolver"

    private data class CachedUrl(val url: String, val resolvedAt: Long)
    private val streamUrlCache = LruCache<String, CachedUrl>(50)

    /**
     * Resolves a Bandcamp track page URL to a playable stream URL.
     * This method blocks the calling thread, so it MUST NOT be called from the Main thread.
     * It is designed to be used within ExoPlayer's ResolvingDataSource resolver.
     */
    fun resolve(trackUrl: String): String? {
        val cached = synchronized(streamUrlCache) {
            streamUrlCache.get(trackUrl)
        }
        if (cached != null) {
            val age = android.os.SystemClock.elapsedRealtime() - cached.resolvedAt
            if (age < 2 * 60 * 60 * 1000L) { // 2 hours
                return cached.url
            } else {
                synchronized(streamUrlCache) {
                    streamUrlCache.remove(trackUrl)
                }
            }
        }

        return try {
            val streamUrl = runBlocking(Dispatchers.IO) {
                Bandcamp.resolveStreamUrl(trackUrl)
            }
            if (streamUrl != null) {
                synchronized(streamUrlCache) {
                    streamUrlCache.put(trackUrl, CachedUrl(streamUrl, android.os.SystemClock.elapsedRealtime()))
                }
            }
            streamUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve Bandcamp stream URL for $trackUrl", e)
            null
        }
    }

    /**
     * Invalidates a cached stream URL for a given track URL.
     */
    fun invalidate(trackUrl: String) {
        synchronized(streamUrlCache) {
            streamUrlCache.remove(trackUrl)
        }
    }
}
