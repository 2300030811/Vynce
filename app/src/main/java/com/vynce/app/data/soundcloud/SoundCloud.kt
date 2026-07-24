package com.vynce.app.data.soundcloud

import android.util.Log
import com.vynce.jiosaavn.SaavnSong
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ScUser(
    val id: Long,
    val username: String,
    val avatar_url: String? = null,
    val permalink_url: String? = null
)

@Serializable
data class ScFormat(
    val protocol: String,
    val mime_type: String? = null
)

@Serializable
data class ScTranscoding(
    val url: String,
    val preset: String? = null,
    val duration: Long? = null,
    val format: ScFormat,
    val quality: String? = null
)

@Serializable
data class ScMedia(
    val transcodings: List<ScTranscoding>
)

@Serializable
data class ScTrack(
    val id: Long,
    val title: String,
    val full_duration: Long,
    val artwork_url: String? = null,
    val permalink_url: String,
    val user: ScUser,
    val media: ScMedia
)

@Serializable
data class ScSearchResponse(
    val collection: List<ScTrack>
)

@Serializable
data class ScStreamUrlResponse(
    val url: String
)

object SoundCloud {
    private const val TAG = "SoundCloud"
    private const val SOUNDCLOUD_URL = "https://soundcloud.com"
    private const val SOUNDCLOUD_API_V2 = "https://api-v2.soundcloud.com"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val CLIENT_ID_REGEX = Regex("[{,]client_id:\"(\\w+)\"")
    private val SNDCDN_SCRIPT_URL_REGEX = Regex("https?://[^\\s\"]*sndcdn\\.com[^\\s\"]*\\.js")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 8_000 }
    }

    private var cachedClientId: String? = null
    private val clientIdMutex = Mutex()

    /**
     * Harvest client_id dynamically from SoundCloud's homepage & script bundles.
     */
    private suspend fun getClientId(forceRefresh: Boolean = false): String {
        if (!forceRefresh) {
            cachedClientId?.let { return it }
        }

        return clientIdMutex.withLock {
            // Check again inside lock to avoid duplicate requests
            if (!forceRefresh) {
                cachedClientId?.let { return it }
            }

            Log.d(TAG, "Harvesting SoundCloud client_id...")
            try {
                val homepageRes: HttpResponse = client.get(SOUNDCLOUD_URL) {
                    header("User-Agent", BROWSER_USER_AGENT)
                }
                if (homepageRes.status.value != 200) {
                    throw IllegalStateException("SoundCloud homepage returned status ${homepageRes.status}")
                }
                val html = homepageRes.bodyAsText()
                val matches = SNDCDN_SCRIPT_URL_REGEX.findAll(html)
                    .map { it.value }
                    .toList()

                if (matches.isEmpty()) {
                    throw IllegalStateException("No sndcdn script URLs found in SoundCloud homepage")
                }

                // Check the scripts in reverse order (typically the client ID is in the last scripts)
                for (scriptUrl in matches.reversed()) {
                    try {
                        val scriptRes: HttpResponse = client.get(scriptUrl) {
                            header("User-Agent", BROWSER_USER_AGENT)
                        }
                        if (scriptRes.status.value == 200) {
                            val scriptBody = scriptRes.bodyAsText()
                            val clientIdMatch = CLIENT_ID_REGEX.find(scriptBody)
                            val foundId = clientIdMatch?.groupValues?.get(1)
                            if (!foundId.isNullOrEmpty()) {
                                Log.i(TAG, "Successfully harvested SoundCloud client_id: $foundId")
                                cachedClientId = foundId
                                return@withLock foundId
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to inspect script URL: $scriptUrl", e)
                    }
                }
                throw IllegalStateException("Could not find client_id in any parsed script bundles")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to harvest SoundCloud client_id", e)
                throw e
            }
        }
    }

    /**
     * Search SoundCloud tracks. Returns mapped SaavnSong objects with "soundcloud:" prefixed IDs.
     */
    suspend fun searchTracks(query: String, limit: Int = 10): List<SaavnSong> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            var clientId = getClientId()
            var response = performSearchRequest(trimmed, limit, clientId)

            // Retry once if unauthorized/forbidden (client ID might be expired)
            if (response == null) {
                Log.w(TAG, "Search returned null or failed (401/403 likely). Refreshing client_id...")
                clientId = getClientId(forceRefresh = true)
                response = performSearchRequest(trimmed, limit, clientId)
            }

            response?.collection?.map { track ->
                val durationSec = (track.full_duration / 1000).toInt()
                val image = (track.artwork_url ?: track.user.avatar_url ?: "")
                    .replace("-large.jpg", "-t500x500.jpg")
                    .replace("-large.png", "-t500x500.png")

                SaavnSong(
                    id = "soundcloud:${track.id}",
                    name = track.title,
                    primaryArtists = track.user.username,
                    album = "SoundCloud",
                    image = image,
                    downloadUrl = "", // Dynamically resolved at playback time
                    duration = durationSec.toString(),
                    year = "",
                    language = ""
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "SoundCloud search failed for query: $query", e)
            emptyList()
        }
    }

    private suspend fun performSearchRequest(query: String, limit: Int, clientId: String): ScSearchResponse? {
        return try {
            val response: HttpResponse = client.get("$SOUNDCLOUD_API_V2/search/tracks") {
                parameter("q", query)
                parameter("limit", limit.toString())
                parameter("client_id", clientId)
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "application/json")
            }
            if (response.status.value in 400..403) {
                return null
            }
            val bodyText = response.bodyAsText()
            json.decodeFromString<ScSearchResponse>(bodyText)
        } catch (e: Exception) {
            Log.e(TAG, "Search HTTP request failed", e)
            null
        }
    }

    /**
     * Resolve a SoundCloud track's transcoding stream URL.
     *
     * Prefer progressive streams because they work with both Media3 playback and its progressive
     * download pipeline. HLS remains a fallback for tracks that do not expose a progressive URL.
     */
    suspend fun resolveStreamUrl(trackId: String): String? = withContext(Dispatchers.IO) {
        try {
            var clientId = getClientId()
            var trackDetails = fetchTrackDetails(trackId, clientId)

            if (trackDetails == null) {
                Log.w(TAG, "Track details fetch failed. Refreshing client_id...")
                clientId = getClientId(forceRefresh = true)
                trackDetails = fetchTrackDetails(trackId, clientId)
            }

            val transcodings = trackDetails?.media?.transcodings ?: return@withContext null
            val selectedTranscoding = transcodings.find { it.format.protocol.equals("progressive", ignoreCase = true) }
                ?: transcodings.find { it.format.protocol.equals("hls", ignoreCase = true) }
                ?: transcodings.firstOrNull()
                ?: return@withContext null

            var streamUrl = fetchStreamFromTranscoding(selectedTranscoding.url, clientId)
            if (streamUrl == null) {
                clientId = getClientId(forceRefresh = true)
                streamUrl = fetchStreamFromTranscoding(selectedTranscoding.url, clientId)
            }
            streamUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve stream URL for trackId: $trackId", e)
            null
        }
    }

    private suspend fun fetchTrackDetails(trackId: String, clientId: String): ScTrack? {
        return try {
            val response: HttpResponse = client.get("$SOUNDCLOUD_API_V2/tracks/$trackId") {
                parameter("client_id", clientId)
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "application/json")
            }
            if (response.status.value in 400..403) return null
            val bodyText = response.bodyAsText()
            json.decodeFromString<ScTrack>(bodyText)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchStreamFromTranscoding(transcodingUrl: String, clientId: String): String? {
        return try {
            val response: HttpResponse = client.get(transcodingUrl) {
                parameter("client_id", clientId)
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "application/json")
            }
            if (response.status.value in 400..403) return null
            val bodyText = response.bodyAsText()
            json.decodeFromString<ScStreamUrlResponse>(bodyText).url
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getRelatedTracks(trackId: String, limit: Int = 10): List<SaavnSong> = withContext(Dispatchers.IO) {
        try {
            var clientId = getClientId()
            var response = performRelatedRequest(trackId, limit, clientId)

            if (response == null) {
                clientId = getClientId(forceRefresh = true)
                response = performRelatedRequest(trackId, limit, clientId)
            }

            response?.collection?.map { track ->
                val durationSec = (track.full_duration / 1000).toInt()
                val image = (track.artwork_url ?: track.user.avatar_url ?: "")
                    .replace("-large.jpg", "-t500x500.jpg")
                    .replace("-large.png", "-t500x500.png")

                SaavnSong(
                    id = "soundcloud:${track.id}",
                    name = track.title,
                    primaryArtists = track.user.username,
                    album = "SoundCloud",
                    image = image,
                    downloadUrl = "",
                    duration = durationSec.toString(),
                    year = "",
                    language = ""
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "SoundCloud getRelatedTracks failed for trackId: $trackId", e)
            emptyList()
        }
    }

    private suspend fun performRelatedRequest(trackId: String, limit: Int, clientId: String): ScSearchResponse? {
        return try {
            val response: HttpResponse = client.get("$SOUNDCLOUD_API_V2/tracks/$trackId/related") {
                parameter("limit", limit.toString())
                parameter("client_id", clientId)
                header("User-Agent", BROWSER_USER_AGENT)
                header("Accept", "application/json")
            }
            if (response.status.value in 400..403) {
                return null
            }
            val bodyText = response.bodyAsText()
            json.decodeFromString<ScSearchResponse>(bodyText)
        } catch (e: Exception) {
            null
        }
    }
}
