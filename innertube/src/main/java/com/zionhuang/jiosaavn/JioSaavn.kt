package com.zionhuang.jiosaavn

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class SaavnSong(
    val id: String = "",
    val name: String = "",
    val primaryArtists: String = "",
    val album: String = "",
    val image: String = "",
    val downloadUrl: String = "",
    val duration: String = "0",
    val year: String = "",
    val language: String = "",
    val hasLyrics: String = "false"
)

@Serializable
data class SaavnAlbum(
    val id: String = "",
    val name: String = "",
    val artists: String = "",
    val image: String = "",
    val songCount: String = "0",
    val year: String = ""
)

@Serializable
data class SaavnArtist(
    val id: String = "",
    val name: String = "",
    val image: String = "",
    val followerCount: String = "0"
)

@Serializable
data class SaavnPlaylist(
    val id: String = "",
    val name: String = "",
    val followerCount: String = "0",
    val image: String = "",
    val songCount: String = "0"
)

object JioSaavn {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    // ... existing BASES and getJson ...

    suspend fun searchAlbums(query: String): List<SaavnAlbum> {
        val obj = getJson("/api/search/albums", mapOf("query" to query, "limit" to "10")) ?: return emptyList()
        return try {
            val results = obj["data"]?.jsonObject?.get("results")?.jsonArray ?: return emptyList()
            results.mapNotNull { el ->
                try {
                    val a = el.jsonObject
                    SaavnAlbum(
                        id = a["id"]?.jsonPrimitive?.content ?: "",
                        name = a["name"]?.jsonPrimitive?.content ?: "",
                        artists = a["artists"]?.jsonObject?.get("primary")?.jsonArray
                            ?.joinToString(", ") { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" } ?: "",
                        image = a["image"]?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                            ?.replace("http://","https://") ?: "",
                        songCount = a["songCount"]?.jsonPrimitive?.content ?: "0",
                        year = a["year"]?.jsonPrimitive?.content ?: ""
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun searchArtists(query: String): List<SaavnArtist> {
        val obj = getJson("/api/search/artists", mapOf("query" to query, "limit" to "10")) ?: return emptyList()
        return try {
            val results = obj["data"]?.jsonObject?.get("results")?.jsonArray ?: return emptyList()
            results.mapNotNull { el ->
                try {
                    val a = el.jsonObject
                    SaavnArtist(
                        id = a["id"]?.jsonPrimitive?.content ?: "",
                        name = a["name"]?.jsonPrimitive?.content ?: "",
                        image = a["image"]?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                            ?.replace("http://","https://") ?: "",
                        followerCount = a["followerCount"]?.jsonPrimitive?.content ?: "0"
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun searchPlaylists(query: String): List<SaavnPlaylist> {
        val obj = getJson("/api/search/playlists", mapOf("query" to query, "limit" to "10")) ?: return emptyList()
        return try {
            val results = obj["data"]?.jsonObject?.get("results")?.jsonArray ?: return emptyList()
            results.mapNotNull { el ->
                try {
                    val p = el.jsonObject
                    SaavnPlaylist(
                        id = p["id"]?.jsonPrimitive?.content ?: "",
                        name = p["name"]?.jsonPrimitive?.content ?: "",
                        image = p["image"]?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                            ?.replace("http://","https://") ?: "",
                        songCount = p["songCount"]?.jsonPrimitive?.content ?: "0",
                        followerCount = p["followerCount"]?.jsonPrimitive?.content ?: "0"
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getAlbumSongs(albumId: String): List<SaavnSong> {
        val obj = getJson("/api/albums", mapOf("id" to albumId)) ?: return emptyList()
        return try {
            obj["data"]?.jsonObject?.get("songs")?.jsonArray
                ?.mapNotNull { parseSong(it.jsonObject) } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getArtistSongs(artistId: String): List<SaavnSong> {
        val obj = getJson("/api/artists/$artistId/songs") ?: return emptyList()
        return try {
            obj["data"]?.jsonObject?.get("songs")?.jsonArray
                ?.mapNotNull { parseSong(it.jsonObject) } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }

    private val BASES = listOf(
        "https://my-repo-nine-phi.vercel.app",
        "https://jiosaavn-api-pink.vercel.app"
    )

    private suspend fun getJson(path: String, params: Map<String, String> = emptyMap()): JsonObject? {
        for (base in BASES) {
            try {
                val response: HttpResponse = client.get("$base$path") {
                    params.forEach { (k, v) -> parameter(k, v) }
                }
                val text = response.bodyAsText()
                println("JioSaavn: Response from $base$path (${text.length} chars): ${text.take(300)}")
                return json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                println("JioSaavn: Failed $base$path: ${e.message}")
            }
        }
        return null
    }

    private fun parseSong(song: JsonObject): SaavnSong {
        val finalDownloadUrl = song["downloadUrl"]?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: song["downloadUrl"]?.jsonArray?.lastOrNull()?.jsonObject?.get("link")?.jsonPrimitive?.content
            ?: ""
        println("JioSaavn: Download URL for ${song["name"]?.jsonPrimitive?.content}: $finalDownloadUrl")
        return SaavnSong(
            id = song["id"]?.jsonPrimitive?.content ?: "",
            name = song["name"]?.jsonPrimitive?.content ?: "",
            primaryArtists = song["primaryArtists"]?.jsonPrimitive?.content
                ?: song["artists"]?.jsonObject?.get("primary")?.jsonArray
                    ?.joinToString(", ") { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" }
                ?: "",
            album = song["album"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                ?: song["album"]?.jsonPrimitive?.content ?: "",
            image = (song["image"]?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                ?: song["image"]?.jsonArray?.lastOrNull()?.jsonObject?.get("link")?.jsonPrimitive?.content
                ?: song["image"]?.jsonPrimitive?.content ?: "").replace("http://", "https://"),
            downloadUrl = finalDownloadUrl,
            duration = song["duration"]?.jsonPrimitive?.content ?: "0",
            year = song["year"]?.jsonPrimitive?.content ?: "",
            language = song["language"]?.jsonPrimitive?.content ?: ""
        )
    }

    suspend fun searchSongs(query: String): List<SaavnSong> {
        val obj = getJson("/api/search/songs", mapOf("query" to query, "limit" to "20")) ?: return emptyList()
        return try {
            val results = obj["data"]?.jsonObject?.get("results")?.jsonArray ?: run {
                println("JioSaavn: No results array. Keys: ${obj.keys}")
                return emptyList()
            }
            results.mapNotNull { el ->
                try {
                    parseSong(el.jsonObject)
                } catch (e: Exception) {
                    println("JioSaavn: Failed parsing song: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            println("JioSaavn: Parse error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSong(id: String): SaavnSong? {
        val obj = getJson("/api/songs/$id") ?: return null
        val data = obj["data"]?.jsonArray ?: obj["data"]?.jsonObject?.get("results")?.jsonArray
        return data?.firstOrNull()?.jsonObject?.let {
            try {
                parseSong(it)
            } catch (e: Exception) {
                println("JioSaavn: Failed parsing getSong: ${e.message}")
                null
            }
        }
    }

    suspend fun getCharts(): List<SaavnSong> {
        // Use search with trending queries since /api/charts isn't supported on some endpoints
        return searchSongs("arijit singh hits 2024")
    }

    fun SaavnSong.streamUrl(): String? = downloadUrl.takeIf { it.startsWith("https://") }
    fun SaavnSong.thumbnailUrl(): String? = image.takeIf { it.isNotEmpty() }
    fun SaavnSong.artistNames(): String = primaryArtists
}
