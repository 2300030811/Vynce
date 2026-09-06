package com.vynce.app.data.spotify

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SpotifyEmbedTrack(
    val name: String,
    val artist: String,
    val durationMs: Int,
    val coverUrl: String,
    val album: String,
    val uri: String
)

data class SpotifyEmbedPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val coverUrl: String,
    val tracks: List<SpotifyEmbedTrack>
)

object SpotifyPlaylistFetcher {
    private const val TAG = "SpotifyPlaylistFetcher"
    private const val PREFS_NAME = "spotify_curated_covers"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val playlistCache = LruCache<String, SpotifyEmbedPlaylist>(50)
    private val coverCache = LruCache<String, String>(200)

    fun getCachedCover(playlistId: String, context: Context? = null): String? {
        val inMem = synchronized(coverCache) { coverCache.get(playlistId) }
        if (!inMem.isNullOrBlank()) return inMem

        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val persisted = prefs.getString(playlistId, null)
                if (!persisted.isNullOrBlank()) {
                    synchronized(coverCache) { coverCache.put(playlistId, persisted) }
                    return persisted
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read cover from prefs", e)
            }
        }
        return null
    }

    fun saveCover(context: Context?, playlistId: String, coverUrl: String) {
        if (playlistId.isBlank() || coverUrl.isBlank()) return
        synchronized(coverCache) { coverCache.put(playlistId, coverUrl) }
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(playlistId, coverUrl).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist cover to prefs", e)
            }
        }
    }

    /**
     * Fetches public Spotify playlist metadata and tracks via Spotify's Embed endpoint.
     * Requires ZERO authentication or API keys.
     */
    suspend fun fetchPlaylist(playlistId: String, context: Context? = null): SpotifyEmbedPlaylist? = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext null

        synchronized(playlistCache) { playlistCache.get(playlistId) }?.let { return@withContext it }

        try {
            val url = "https://open.spotify.com/embed/playlist/$playlistId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to load Spotify embed: HTTP ${response.code}")
                return@withContext null
            }

            val html = response.body?.string().orEmpty()
            val scriptRegex = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">([^<]+)</script>")
            val match = scriptRegex.find(html)

            if (match == null) {
                Log.e(TAG, "Could not find __NEXT_DATA__ in Spotify embed HTML")
                return@withContext null
            }

            val jsonStr = match.groupValues[1]
            val root = JSONObject(jsonStr)
            val entity = root.optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("state")
                ?.optJSONObject("data")
                ?.optJSONObject("entity")

            if (entity == null) {
                Log.e(TAG, "Could not parse entity from Spotify JSON")
                return@withContext null
            }

            val name = entity.optString("name", entity.optString("title", "Curated Playlist"))
            val description = entity.optString("description", "")
            
            // Extract cover art URL
            var coverUrl = ""
            val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
            if (coverSources != null && coverSources.length() > 0) {
                coverUrl = coverSources.getJSONObject(0).optString("url", "")
            }
            if (coverUrl.isEmpty()) {
                val visualImages = entity.optJSONObject("visualIdentity")?.optJSONArray("image")
                if (visualImages != null && visualImages.length() > 0) {
                    coverUrl = visualImages.getJSONObject(0).optString("url", "")
                }
            }
            if (coverUrl.isEmpty()) {
                val images = entity.optJSONArray("images")
                if (images != null && images.length() > 0) {
                    coverUrl = images.getJSONObject(0).optString("url", "")
                }
            }
            if (coverUrl.isEmpty()) {
                val ogMatch = Regex("""<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""").find(html)
                    ?: Regex("""\"sources\":\s*\[\s*\{\s*\"url\":\s*\"(https://[^\"]+)\"""").find(html)
                if (ogMatch != null) {
                    coverUrl = ogMatch.groupValues[1]
                }
            }
            if (coverUrl.isNotEmpty()) {
                saveCover(context, playlistId, coverUrl)
            }

            val trackListArray = entity.optJSONArray("trackList")
            val tracks = mutableListOf<SpotifyEmbedTrack>()

            if (trackListArray != null) {
                for (i in 0 until trackListArray.length()) {
                    val t = trackListArray.getJSONObject(i)
                    val tName = t.optString("title", t.optString("name", ""))
                    val tArtist = t.optString("subtitle", "")
                    val duration = t.optInt("duration", 0)
                    val uri = t.optString("uri", "")

                    var tCover = ""
                    val tAlbum = t.optJSONObject("album")
                    val albumImages = tAlbum?.optJSONArray("images")
                    if (albumImages != null && albumImages.length() > 0) {
                        tCover = albumImages.getJSONObject(0).optString("url", "")
                    }
                    if (tCover.isEmpty()) {
                        val tImages = t.optJSONArray("images")
                        if (tImages != null && tImages.length() > 0) {
                            tCover = tImages.getJSONObject(0).optString("url", "")
                        }
                    }

                    if (tName.isNotBlank()) {
                        tracks.add(
                            SpotifyEmbedTrack(
                                name = tName,
                                artist = tArtist,
                                durationMs = duration,
                                coverUrl = tCover,
                                album = tAlbum?.optString("name", "") ?: "",
                                uri = uri
                            )
                        )
                    }
                }
            }

            val result = SpotifyEmbedPlaylist(
                id = playlistId,
                name = name,
                description = description,
                coverUrl = coverUrl,
                tracks = tracks
            )

            synchronized(playlistCache) { playlistCache.put(playlistId, result) }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Spotify playlist $playlistId", e)
            null
        }
    }

    /**
     * Parallel background hydration matching Vynce Web / AirBeats architecture.
     * Emits cached covers immediately, then resolves fresh dynamic covers across all playlists concurrently.
     */
    suspend fun hydrateCuratedPlaylists(
        playlists: List<SpotifyCuratedPlaylist>,
        context: Context,
        onCoverLoaded: (playlistId: String, coverUrl: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        // 1. Emit all cached covers immediately
        playlists.forEach { p ->
            val cached = getCachedCover(p.id, context)
            if (!cached.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    onCoverLoaded(p.id, cached)
                }
            }
        }

        // 2. Fetch fresh dynamic metadata in parallel (batch of 4)
        val dispatcher = Dispatchers.IO.limitedParallelism(4)
        val jobs = playlists.map { p ->
            async(dispatcher) {
                try {
                    val result = fetchPlaylist(p.id, context)
                    if (result != null && result.coverUrl.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            onCoverLoaded(p.id, result.coverUrl)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error hydrating cover for ${p.id}: ${e.message}")
                }
            }
        }
        jobs.awaitAll()
    }
}
