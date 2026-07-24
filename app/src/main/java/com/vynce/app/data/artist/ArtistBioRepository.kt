package com.vynce.app.data.artist

import android.util.Log
import android.util.LruCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

@Serializable
data class WikiSearchQuery(
    val search: List<WikiSearchResult>
)

@Serializable
data class WikiSearchResponse(
    val query: WikiSearchQuery? = null
)

@Serializable
data class WikiSearchResult(
    val title: String
)

@Serializable
data class WikiSummaryThumbnail(
    val source: String
)

@Serializable
data class WikiSummaryResponse(
    val title: String,
    val extract: String? = null,
    val thumbnail: WikiSummaryThumbnail? = null
)

data class ArtistBio(
    val bio: String,
    val imageUrl: String? = null
)

object ArtistBioRepository {
    private const val TAG = "ArtistBioRepository"
    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
        }
        install(ContentNegotiation) {
            json(json)
        }
    }
    private val bioCache = LruCache<String, ArtistBio>(100)

    suspend fun getArtistBio(artistName: String): ArtistBio? = withContext(Dispatchers.IO) {
        val cacheKey = artistName.trim().lowercase()
        if (cacheKey.isBlank()) return@withContext null
        synchronized(bioCache) { bioCache.get(cacheKey) }?.let { return@withContext it }

        try {
            // Step 1: Search Wikipedia for the best matching page title
            val searchResponse: HttpResponse = client.get("https://en.wikipedia.org/w/api.php") {
                parameter("action", "query")
                parameter("format", "json")
                parameter("list", "search")
                parameter("srsearch", artistName)
                parameter("srlimit", "1")
                header("User-Agent", BROWSER_USER_AGENT)
            }
            if (searchResponse.status.value !in 200..299) return@withContext null
            val searchBody = searchResponse.bodyAsText()
            val searchData = json.decodeFromString<WikiSearchResponse>(searchBody)
            val title = searchData.query?.search?.firstOrNull()?.title ?: return@withContext null

            // Step 2: Fetch summary extract and image for the retrieved page title
            val encodedTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
            val summaryResponse: HttpResponse = client.get("https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle") {
                header("User-Agent", BROWSER_USER_AGENT)
            }
            if (summaryResponse.status.value !in 200..299) return@withContext null
            val summaryBody = summaryResponse.bodyAsText()
            val summaryData = json.decodeFromString<WikiSummaryResponse>(summaryBody)

            ArtistBio(
                bio = summaryData.extract ?: "",
                imageUrl = summaryData.thumbnail?.source
            ).also { bio ->
                synchronized(bioCache) { bioCache.put(cacheKey, bio) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get artist bio for: $artistName", e)
            null
        }
    }
}
