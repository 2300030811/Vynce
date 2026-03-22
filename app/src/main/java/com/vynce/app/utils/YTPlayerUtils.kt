/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app.utils

import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.PlaybackException
import com.vynce.app.constants.AudioQuality
import com.vynce.app.utils.YTPlayerUtils.MAIN_CLIENT
import com.zionhuang.innertube.utils.PoTokenHelper
import com.vynce.app.utils.YTPlayerUtils.validateStatus
import com.vynce.app.utils.potoken.PoTokenGenerator
import com.vynce.app.utils.potoken.PoTokenResult
import com.zionhuang.innertube.NewPipeUtils
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.zionhuang.innertube.models.YouTubeClient.Companion.IOS
import com.zionhuang.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.zionhuang.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.zionhuang.innertube.models.response.PlayerResponse
import okhttp3.OkHttpClient

object YTPlayerUtils {

    private const val TAG = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    /**
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     *
     * [com.zionhuang.innertube.models.YouTubeClient.ANDROID_VR_NO_AUTH] Is temporally used as it is out only working client
     * [com.zionhuang.innertube.models.YouTubeClient.WEB_REMIX] should be preferred here because currently it is the only client which provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    private val MAIN_CLIENT: YouTubeClient = YouTubeClient.ANDROID_MUSIC

    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     */
    private val STREAM_FALLBACK_CLIENTS: List<YouTubeClient> = PoTokenHelper.CLIENT_PRIORITY


    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Long,
    )

    private val PlayerResponse.streamUrl: String?
        get() = streamingData?.adaptiveFormats
            ?.filter { it.isAudio }
            ?.maxByOrNull { it.bitrate ?: 0 }
            ?.let { format ->
                // Use direct URL first (works for IOS/ANDROID_MUSIC clients)
                format.url?.takeIf { it.startsWith("https://") }
                    ?: NewPipeUtils.getStreamUrl(format, videoDetails?.videoId ?: "")
                        .getOrNull()
                        ?.takeIf { it.startsWith("https://") }
            }

    suspend fun getStreamUrl(videoId: String, poToken: String? = null, visitorData: String? = null): Result<String> {
        val clients = listOf(
            YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            YouTubeClient.ANDROID_MUSIC,
            YouTubeClient.ANDROID,
            YouTubeClient.WEB_REMIX
        )
        if (visitorData != null) {
            YouTube.visitorData = visitorData
        }
        if (poToken != null) {
            YouTube.poToken = poToken
        }

        var lastError: Exception? = null
        for (client in clients) {
            try {
                val result = YouTube.player(videoId, client = client, webPlayerPot = poToken)
                    .getOrThrow()
                val url = result.streamUrl ?: continue
                return Result.success(url)
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("YTPlayerUtils", "Client ${client.clientName} failed: ${e.message}")
            }
        }
        return Result.failure(lastError ?: Exception("All clients failed for $videoId"))
    }

    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        poToken: String? = null,
        visitorData: String? = null,
    ): Result<PlaybackData> {
        return try {
            Log.d(TAG, "Playback info requested: $videoId")

            /**
             * This is required for some clients to get working streams however
             * it should not be forced for the [MAIN_CLIENT] because the response of the [MAIN_CLIENT]
             * is required even if the streams won't work from this client.
             * This is why it is allowed to be null.
             */
            val signatureTimestamp = getSignatureTimestampOrNull(videoId)

            val isLoggedIn = YouTube.cookie != null
            val sessionId = visitorData ?: if (isLoggedIn) {
                YouTube.dataSyncId
            } else {
                YouTube.visitorData
            }

            if (visitorData != null) {
                YouTube.visitorData = visitorData
            }
            if (poToken != null) {
                YouTube.poToken = poToken
            }

            Log.d(TAG, "[$videoId] signatureTimestamp: $signatureTimestamp, isLoggedIn: $isLoggedIn")

            val (webPlayerPot, webStreamingPot) = if (poToken != null) {
                Pair(poToken, null)
            } else {
                getWebClientPoTokenOrNull(videoId, sessionId)?.let {
                    Pair(it.playerRequestPoToken, it.streamingDataPoToken)
                } ?: Pair(null, null).also {
                    Log.w(TAG, "[$videoId] No po token")
                }
            }

            val mainPlayerResponse =
                YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp, webPlayerPot)
                    .getOrThrow()

            val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
            val videoDetails = mainPlayerResponse.videoDetails
            val playbackTracking = mainPlayerResponse.playbackTracking

            var format: PlayerResponse.StreamingData.Format? = null
            var streamUrl: String? = null
            var streamExpiresInSeconds: Int? = null

            var streamPlayerResponse: PlayerResponse? = null
            var finalClientIndex = -1
            var finalClient: YouTubeClient? = null
            for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
                // reset for each client
                format = null
                streamUrl = null
                streamExpiresInSeconds = null

                // decide which client to use for streams and load its player response
                val client: YouTubeClient
                if (clientIndex == -1) {
                    Log.d(TAG, "Trying client: ${MAIN_CLIENT.clientName}")
                    // try with streams from main client first
                    client = MAIN_CLIENT
                    streamPlayerResponse = mainPlayerResponse
                    finalClientIndex = clientIndex
                    finalClient = client
                } else {
                    Log.d(TAG, "Trying fallback client: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    // after main client use fallback clients
                    client = STREAM_FALLBACK_CLIENTS[clientIndex]

                    if (client.loginRequired && !isLoggedIn) {
                        // skip client if it requires login but user is not logged in
                        continue
                    }

                    streamPlayerResponse =
                        YouTube.player(videoId, playlistId, client, signatureTimestamp, webPlayerPot)
                            .getOrNull()
                    finalClientIndex = clientIndex
                    finalClient = client
                }

                Log.d(TAG, "[$videoId] stream client: ${client.clientName}, " +
                        "playabilityStatus: ${streamPlayerResponse?.playabilityStatus?.let {
                            it.status + (it.reason?.let { " - $it" } ?: "")
                        }}")

                // process current client response
                if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                    format =
                        findFormat(
                            streamPlayerResponse,
                            audioQuality,
                            connectivityManager,
                        ) ?: continue
                    streamUrl = findUrlOrNull(format, videoId) ?: continue
                    streamExpiresInSeconds =
                        streamPlayerResponse.streamingData?.expiresInSeconds

                    if (client.useWebPoTokens && webStreamingPot != null) {
                        streamUrl += "&pot=$webStreamingPot";
                    }

                    if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                        /** skip [validateStatus] for last client */
                        break
                    }
                    if (validateStatus(streamUrl!!)) {
                        // working stream found
                        Log.i(TAG, "[$videoId] [${client.clientName}] found working stream")
                        break
                    } else {
                        Log.w(TAG, "[$videoId] [${client.clientName}] got bad http status code")
                    }
                }
            }

            if (streamPlayerResponse == null) {
                return Result.failure(Exception("Bad stream player response"))
            }
            if (streamPlayerResponse.playabilityStatus.status != "OK") {
                throw PlaybackException(
                    streamPlayerResponse.playabilityStatus.reason,
                    null,
                    PlaybackException.ERROR_CODE_REMOTE_ERROR
                )
            }
            val finalFormat = format ?: return Result.failure(Exception("Could not find format"))
            val finalStreamUrl = streamUrl ?: return Result.failure(Exception("Could not find stream url"))

            // After the client loop, add:
            Log.d(TAG, "[$videoId] clientIndex=$finalClientIndex client=${finalClient?.clientName}")
            Log.d(TAG, "[$videoId] playabilityStatus=${streamPlayerResponse?.playabilityStatus?.status}")
            Log.d(TAG, "[$videoId] format.url=${finalFormat.url?.take(100)}")
            Log.d(TAG, "[$videoId] finalStreamUrl=${finalStreamUrl.take(100)}")

            val expireTime: Long = try {
                streamExpiresInSeconds?.toLong()
                    ?: finalStreamUrl
                        .let { Regex("expire=(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() }
                        ?.let { it - System.currentTimeMillis() / 1000 }
                    ?: 21600L
            } catch (e: Exception) {
                21600L
            }

            Log.d(TAG, "[$videoId] stream url: $finalStreamUrl")

            Result.success(
                PlaybackData(
                    audioConfig,
                    videoDetails,
                    playbackTracking,
                    finalFormat,
                    finalStreamUrl,
                    expireTime,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> =
        YouTube.player(videoId, playlistId, client = WEB_REMIX) // ANDROID_VR does not work with history

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        val formats = playerResponse.streamingData?.adaptiveFormats ?: return null
        return formats
            .filter { it.isAudio }
            .maxByOrNull {
                (it.bitrate ?: 0) * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
            } ?: formats.firstOrNull() // fallback to any format
    }

    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    private fun validateStatus(url: String): Boolean {
        // Skip HTTP validation — causes blocking and false negatives
        // ExoPlayer will handle actual playback errors via retry
        return url.startsWith("https://")
    }

    /**
     * Wrapper around the [NewPipeUtils.getSignatureTimestamp] function which reports exceptions
     */
    private fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onFailure {
                reportException(it)
            }
            .getOrNull()
    }

    /**
     * Wrapper around the [NewPipeUtils.getStreamUrl] function which reports exceptions
     */
    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        // WEB/WEB_REMIX clients use signatureCipher — NewPipe decodes it
        // IOS/ANDROID clients use format.url directly
        if (format.url == null && format.signatureCipher != null) {
            // Cipher-based URL — must use NewPipe
            return NewPipeUtils.getStreamUrl(format, videoId)
                .onFailure { Log.e(TAG, "[$videoId] NewPipe cipher decode failed: ${it.message}") }
                .getOrNull()
                ?.takeIf { it.startsWith("https://") }
        }

        // Direct URL — try NewPipe throttle decode first, fall back to direct
        val directUrl = format.url?.takeIf { it.startsWith("https://") } ?: return null

        // Append ipbits=0 to disable IP binding — critical for IOS client URLs
        val urlWithIpBits = if (directUrl.contains("googlevideo.com") && !directUrl.contains("ipbits=")) {
            "$directUrl&ipbits=0"
        } else {
            directUrl
        }

        Log.d(TAG, "[$videoId] URL with ipbits=0: ${urlWithIpBits.take(80)}")

        return try {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
            YoutubeJavaScriptPlayerManager
                .getUrlWithThrottlingParameterDeobfuscated(videoId, urlWithIpBits)
                .takeIf { it.startsWith("https://") } ?: urlWithIpBits
        } catch (e: Exception) {
            Log.w(TAG, "[$videoId] throttle decode failed: ${e.message}")
            urlWithIpBits
        }
    }

    private fun extractN(url: String): String {
        return Regex("[?&]n=([^&]+)").find(url)?.groupValues?.get(1) ?: "not_found"
    }

    /**
     * Wrapper around the [PoTokenGenerator.getWebClientPoToken] function which reports exceptions
     */
    private fun getWebClientPoTokenOrNull(videoId: String, sessionId: String?): PoTokenResult? {
        if (sessionId == null) {
            Log.d(TAG, "[$videoId] Session identifier is null")
            return null
        }
        try {
            return poTokenGenerator.getWebClientPoToken(videoId, sessionId)
        } catch (e: Exception) {
            reportException(e)
        }
        return null
    }
}
