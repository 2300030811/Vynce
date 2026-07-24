package com.vynce.app.data.bandcamp

import android.util.Log
import com.vynce.app.extensions.decodeHtml
import com.vynce.jiosaavn.SaavnSong
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BcSearchRequest(
    val search_text: String,
    val search_filter: String = "t",
    val full_page: Boolean = false,
    val fan_id: Long? = null
)

@Serializable
data class BcResult(
    val type: String,
    val name: String,
    val url: String,
    val band_name: String? = null,
    val album_name: String? = null,
    val img: String? = null,
    val id: Long? = null
)

@Serializable
data class BcAuto(
    val results: List<BcResult>
)

@Serializable
data class BcSearchResponse(
    val auto: BcAuto
)

@Serializable
data class BcTrackFile(
    val `mp3-128`: String? = null
)

@Serializable
data class BcTrackInfo(
    val file: BcTrackFile? = null,
    val title: String? = null,
    val id: Long? = null,
    val track_id: Long? = null
)

@Serializable
data class BcTralbum(
    val current: BcTralbumCurrent? = null,
    val trackinfo: List<BcTrackInfo>? = null,
    val artist: String? = null,
    val album_title: String? = null
)

@Serializable
data class BcTralbumCurrent(
    val title: String? = null,
    val id: Long? = null
)

object Bandcamp {
    private const val TAG = "Bandcamp"
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

    suspend fun searchTracks(query: String): List<SaavnSong> = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.post("https://bandcamp.com/api/bcsearch_public_api/1/autocomplete_elastic") {
                header("User-Agent", BROWSER_USER_AGENT)
                contentType(ContentType.Application.Json)
                setBody(BcSearchRequest(search_text = query))
            }
            if (response.status.value !in 200..299) return@withContext emptyList()
            val bodyText = response.bodyAsText()
            val searchRes = json.decodeFromString<BcSearchResponse>(bodyText)
            
            searchRes.auto.results
                .filter { it.type == "track" }
                .map { result ->
                    val highResImage = result.img?.replace("_3.jpg", "_10.jpg")
                        ?.replace("_3.png", "_10.png") ?: ""
                    
                    SaavnSong(
                        id = "bandcamp:${result.url}",
                        name = result.name.decodeHtml(),
                        primaryArtists = result.band_name?.decodeHtml() ?: "Unknown Bandcamp Artist",
                        album = result.album_name?.decodeHtml() ?: "Bandcamp",
                        image = highResImage,
                        downloadUrl = "",
                        duration = "0",
                        year = "",
                        language = ""
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query: $query", e)
            emptyList()
        }
    }

    private val TRALBUM_REGEX = Regex("""data-tralbum="([^"]+)"""")

    suspend fun resolveStreamUrl(trackUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.get(trackUrl) {
                header("User-Agent", BROWSER_USER_AGENT)
            }
            if (response.status.value !in 200..299) return@withContext null
            val html = response.bodyAsText()
            
            val match = TRALBUM_REGEX.find(html) ?: return@withContext null
            val escapedJson = match.groupValues[1]
            val tralbumJson = escapedJson.decodeHtml()
            
            val tralbum = json.decodeFromString<BcTralbum>(tralbumJson)
            val streamUrl = tralbum.trackinfo?.firstOrNull()?.file?.`mp3-128`
            
            if (streamUrl != null) {
                if (streamUrl.startsWith("//")) "https:$streamUrl" else streamUrl
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve stream for track URL: $trackUrl", e)
            null
        }
    }
}
