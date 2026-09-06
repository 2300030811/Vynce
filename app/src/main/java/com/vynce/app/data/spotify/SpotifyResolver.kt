package com.vynce.app.data.spotify

import android.util.LruCache
import com.vynce.jiosaavn.JioSaavn
import com.vynce.jiosaavn.SaavnSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SpotifyResolver {
    private val resolvedCache = LruCache<String, SaavnSong>(200)

    /**
     * Resolves a Spotify track (name + artists) to the highest-match 320kbps JioSaavn song.
     */
    suspend fun resolveToSaavn(
        trackName: String,
        artistName: String,
        durationSeconds: Int = 0
    ): SaavnSong? = withContext(Dispatchers.IO) {
        val cacheKey = "${trackName.trim().lowercase()}###${artistName.trim().lowercase()}"
        synchronized(resolvedCache) { resolvedCache.get(cacheKey) }?.let { return@withContext it }

        val cleanTitle = trackName
            .replace(Regex("\\(.*?\\)|\\[.*?\\]"), "") // remove (feat. ...) or [Remix] for initial broad search
            .trim()
        val query = "$cleanTitle $artistName".trim()

        try {
            val results = JioSaavn.searchSongs(query)
            if (results.isNotEmpty()) {
                // Find best matching candidate
                val best = results.maxByOrNull { candidate ->
                    var score = 0
                    if (candidate.name.contains(cleanTitle, ignoreCase = true)) score += 50
                    if (candidate.primaryArtists.contains(artistName, ignoreCase = true)) score += 30
                    if (durationSeconds > 0 && candidate.duration.toIntOrNull() != null) {
                        val diff = kotlin.math.abs(candidate.duration.toInt() - durationSeconds)
                        if (diff <= 5) score += 20
                        else if (diff <= 15) score += 10
                    }
                    score
                } ?: results.first()

                synchronized(resolvedCache) { resolvedCache.put(cacheKey, best) }
                return@withContext best
            }
        } catch (_: Exception) {
            // Fallback to broader query if needed
        }

        // Secondary fallback search on raw track title
        try {
            val fallbackResults = JioSaavn.searchSongs(trackName)
            val fallback = fallbackResults.firstOrNull()
            if (fallback != null) {
                synchronized(resolvedCache) { resolvedCache.put(cacheKey, fallback) }
                return@withContext fallback
            }
        } catch (_: Exception) {}

        null
    }
}
