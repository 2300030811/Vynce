package com.vynce.app.utils

import android.util.Log
import android.util.LruCache
import androidx.media3.common.util.UnstableApi
import com.vynce.app.data.soundcloud.SoundCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class SoundCloudStreamResolver @Inject constructor() {
    private val TAG = "SoundCloudStreamResolver"

    private data class CachedUrl(val url: String, val resolvedAt: Long)
    private val streamUrlCache = LruCache<String, CachedUrl>(50)

    /**
     * Resolves a SoundCloud track ID to a playable stream URL (typically HLS/m3u8).
     * This method blocks the calling thread, so it MUST NOT be called from the Main thread.
     * It is designed to be used within ExoPlayer's ResolvingDataSource resolver.
     */
    fun resolve(trackId: String): String? {
        val cached = synchronized(streamUrlCache) {
            streamUrlCache.get(trackId)
        }
        if (cached != null) {
            val age = android.os.SystemClock.elapsedRealtime() - cached.resolvedAt
            if (age < STREAM_CACHE_TTL_MS) {
                return cached.url
            } else {
                synchronized(streamUrlCache) {
                    streamUrlCache.remove(trackId)
                }
            }
        }

        return try {
            // This is called from ExoPlayer's internal loading thread.
            // Using runBlocking is necessary to bridge the gap between ExoPlayer's 
            // synchronous API and SoundCloud's asynchronous API.
            val streamUrl = runBlocking(Dispatchers.IO) {
                SoundCloud.resolveStreamUrl(trackId)
            }
            if (streamUrl != null) {
                synchronized(streamUrlCache) {
                    streamUrlCache.put(trackId, CachedUrl(streamUrl, android.os.SystemClock.elapsedRealtime()))
                }
            }
            streamUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve SoundCloud stream URL for $trackId", e)
            null
        }
    }

    /**
     * Invalidates a cached stream URL for a given track ID.
     */
    fun invalidate(trackId: String) {
        synchronized(streamUrlCache) {
            streamUrlCache.remove(trackId)
        }
    }

    private companion object {
        const val STREAM_CACHE_TTL_MS = 45 * 60 * 1_000L
    }
}
