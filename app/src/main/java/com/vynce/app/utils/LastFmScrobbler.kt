package com.vynce.app.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

class LastFmScrobbler(
    private val apiKey: String = "YOUR_LASTFM_API_KEY",
    private val apiSecret: String = "YOUR_LASTFM_API_SECRET"
) {
    private val client = OkHttpClient()
    private val BASE = "https://ws.audioscrobbler.com/2.0/"
    var sessionKey: String? = null

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun sign(params: Map<String, String>): String {
        val sorted = params.entries.sortedBy { it.key }
            .joinToString("") { "${it.key}${it.value}" }
        return md5(sorted + apiSecret)
    }

    suspend fun scrobble(artist: String, track: String, album: String?, timestamp: Long) {
        val sk = sessionKey ?: return
        val params = buildMap {
            put("method", "track.scrobble")
            put("artist", artist)
            put("track", track)
            album?.let { put("album", it) }
            put("timestamp", (timestamp / 1000).toString())
            put("api_key", apiKey)
            put("sk", sk)
        }
        val sig = sign(params)
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder().apply {
                    params.forEach { (k, v) -> add(k, v) }
                    add("api_sig", sig)
                    add("format", "json")
                }.build()
                val req = Request.Builder().url(BASE).post(body).build()
                client.newCall(req).execute().close()
                Log.d("LastFm", "Scrobbled: $artist - $track")
            } catch (e: Exception) {
                Log.w("LastFm", "Scrobble failed: ${e.message}")
            }
        }
    }

    suspend fun updateNowPlaying(artist: String, track: String, album: String?) {
        val sk = sessionKey ?: return
        val params = buildMap {
            put("method", "track.updateNowPlaying")
            put("artist", artist)
            put("track", track)
            album?.let { put("album", it) }
            put("api_key", apiKey)
            put("sk", sk)
        }
        val sig = sign(params)
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder().apply {
                    params.forEach { (k, v) -> add(k, v) }
                    add("api_sig", sig)
                    add("format", "json")
                }.build()
                val req = Request.Builder().url(BASE).post(body).build()
                client.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.w("LastFm", "NowPlaying failed: ${e.message}")
            }
        }
    }
}
