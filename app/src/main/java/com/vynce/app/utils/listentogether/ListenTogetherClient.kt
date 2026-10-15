package com.vynce.app.utils.listentogether

import android.content.Context
import android.util.Log
import com.vynce.app.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ListenTogetherClient {
    private const val TAG = "ListenTogetherClient"
    const val DEFAULT_PORT = 8945

    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 4000
    private const val MAX_HEADER_BYTES = 8192
    private const val MAX_RESPONSE_BODY_BYTES = 131072 // 128 KB
    private const val MAX_REQUEST_BODY_BYTES = 65536 // 64 KB

    // Maintained for backward compatibility; local LAN peer socket transport bypasses external proxies.
    var proxy: Proxy? = null

    // Cache the resolved host base URL for joined rooms (e.g. "http://192.168.1.45:8945")
    private val roomEndpoints = ConcurrentHashMap<String, String>()

    data class ValidatedEndpoint(
        val ip: String,
        val port: Int,
    )

    /**
     * Checks whether an IPv4 address belongs to an RFC 1918 private subnet, loopback, or link-local range.
     * Public addresses, multicast, broadcast, and invalid formats are rejected.
     */
    fun isPrivateIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            if (part.length > 1 && part.startsWith('0')) return false
            part.toIntOrNull() ?: return false
        }
        if (octets.any { it !in 0..255 }) return false
        val (p0, p1, _, _) = octets

        // RFC 1918: 10.0.0.0/8
        if (p0 == 10) return true
        // RFC 1918: 172.16.0.0/12
        if (p0 == 172 && p1 in 16..31) return true
        // RFC 1918: 192.168.0.0/16
        if (p0 == 192 && p1 == 168) return true
        // Loopback: 127.0.0.0/8
        if (p0 == 127) return true
        // Link-Local: 169.254.0.0/16
        if (p0 == 169 && p1 == 254) return true

        return false
    }

    /**
     * Validates and parses a room code, raw IP, or full URL into a safe private peer endpoint.
     * Enforces scheme, port (8945), and IPv4 private network boundaries.
     */
    fun validateEndpoint(rawInput: String): ValidatedEndpoint {
        var clean = rawInput.trim()
        if (clean.contains("://")) {
            if (!clean.startsWith("http://", ignoreCase = true)) {
                throw IllegalArgumentException("Unsupported scheme. Listen Together requires local HTTP.")
            }
            clean = clean.substring(7)
        }
        clean = clean.substringBefore('/').substringBefore('?').substringBefore('#').trim()
        if (clean.isBlank()) {
            throw IllegalArgumentException("Peer address cannot be empty")
        }

        val hostPart: String
        val port: Int
        if (clean.contains(':')) {
            val parts = clean.split(':')
            if (parts.size != 2) {
                throw IllegalArgumentException("Malformed host:port format: $clean")
            }
            hostPart = parts[0].trim()
            val parsedPort = parts[1].trim().toIntOrNull()
                ?: throw IllegalArgumentException("Invalid port: ${parts[1]}")
            if (parsedPort != DEFAULT_PORT) {
                throw IllegalArgumentException("Invalid port $parsedPort: Listen Together operates exclusively on port $DEFAULT_PORT")
            }
            port = parsedPort
        } else {
            hostPart = clean
            port = DEFAULT_PORT
        }

        val resolvedIp = EmbeddedRoomServer.roomCodeToIp(hostPart) ?: hostPart
        if (!isPrivateIpv4(resolvedIp)) {
            throw IllegalArgumentException("Invalid peer host '$hostPart'. Only local private IPv4 addresses are permitted.")
        }

        return ValidatedEndpoint(resolvedIp, port)
    }

    /**
     * Resolves the target base HTTP URL for a given room code.
     * Validates that the destination is a safe local private IPv4 peer.
     */
    fun resolveBaseUrl(code: String): String {
        val clean = code.trim()
        val cached = roomEndpoints[clean.uppercase()]
        if (!cached.isNullOrBlank()) return cached

        val endpoint = validateEndpoint(clean)
        val url = "http://${endpoint.ip}:${endpoint.port}"
        roomEndpoints[clean.uppercase()] = url
        return url
    }

    suspend fun createSession(
        context: Context? = null,
        displayName: String,
        state: ListenTogetherPlaybackState,
    ): ListenTogetherSession = withContext(Dispatchers.IO) {
        val name = displayName.ifBlank { "Vynce Host" }
        val localIp = EmbeddedRoomServer.getLocalIpAddress() ?: "127.0.0.1"
        val roomCode = EmbeddedRoomServer.ipToRoomCode(localIp)

        val hostParticipant = ListenTogetherParticipant(
            id = "usr_${UUID.randomUUID().toString().take(8)}",
            name = name,
            isHost = true
        )

        val newSession = ListenTogetherSession(
            code = roomCode,
            participantId = hostParticipant.id,
            joinUrl = "https://vynce.app/together/$roomCode",
            participants = 1,
            participantList = listOf(hostParticipant),
            hostName = name,
            controllerId = hostParticipant.id,
            controllerName = name,
            stateVersion = 1L,
            serverNow = System.currentTimeMillis(),
            state = state
        )

        // Start the embedded coordinator on device
        EmbeddedRoomServer.start(context, newSession)
        roomEndpoints[roomCode.uppercase()] = "http://127.0.0.1:$DEFAULT_PORT"
        Log.i(TAG, "Created room $roomCode hosted at $localIp:$DEFAULT_PORT")

        newSession
    }

    suspend fun joinSession(
        code: String,
        displayName: String,
    ): ListenTogetherSession = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()

        // If self-hosting this room, return local session directly
        if (EmbeddedRoomServer.isRunning) {
            val localSession = EmbeddedRoomServer.getLocalSession()
            if (localSession != null && (cleanCode == localSession.code || cleanCode == "127.0.0.1")) {
                return@withContext localSession
            }
        }

        val baseUrl = resolveBaseUrl(code)
        roomEndpoints[cleanCode] = baseUrl

        val url = "$baseUrl/sessions/$cleanCode/join"
        Log.i(TAG, "Joining room $cleanCode via $url")

        post(
            url,
            JSONObject().put("name", displayName.ifBlank { "Guest" })
        ) { body ->
            ListenTogetherSession.fromJson(body)
        }
    }

    suspend fun getSession(
        code: String,
        participantId: String? = null,
    ): ListenTogetherSession = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()

        // Direct in-memory fast path if this device is the host
        if (EmbeddedRoomServer.isRunning) {
            val localSession = EmbeddedRoomServer.getLocalSession()
            if (localSession != null) {
                return@withContext localSession
            }
        }

        val baseUrl = resolveBaseUrl(code)
        val url = "$baseUrl/sessions/$cleanCode"

        get(url, participantId) { body ->
            ListenTogetherSession.fromJson(body)
        }
    }

    suspend fun updateState(
        code: String,
        participantId: String,
        state: ListenTogetherPlaybackState,
    ): ListenTogetherSession = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()

        // Direct in-memory fast path if this device is the host
        if (EmbeddedRoomServer.isRunning) {
            val updated = EmbeddedRoomServer.updateLocalState(state)
            if (updated != null) {
                return@withContext updated
            }
        }

        val baseUrl = resolveBaseUrl(code)
        val url = "$baseUrl/sessions/$cleanCode/state"

        post(
            url,
            JSONObject()
                .put("participantId", participantId)
                .put("state", state.toJson()),
            participantId
        ) { body ->
            ListenTogetherSession.fromJson(body)
        }
    }

    suspend fun leaveSession(
        code: String,
        participantId: String,
    ) = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()

        // If this device was the host, shut down embedded server
        if (EmbeddedRoomServer.isRunning) {
            EmbeddedRoomServer.stop()
        } else {
            val baseUrl = resolveBaseUrl(code)
            val url = "$baseUrl/sessions/$cleanCode/leave"
            runCatching {
                post(
                    url,
                    JSONObject().put("participantId", participantId)
                ) {}
            }
        }
        roomEndpoints.remove(cleanCode)
    }

    private suspend fun <T> get(
        url: String,
        participantId: String? = null,
        parser: (JSONObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        executeSocketRequest("GET", url, null, participantId, parser)
    }

    private suspend fun <T> post(
        url: String,
        body: JSONObject,
        participantId: String? = null,
        parser: (JSONObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        executeSocketRequest("POST", url, body.toString(), participantId, parser)
    }

    internal data class RawHttpResponse(
        val statusCode: Int,
        val headers: Map<String, String>,
        val bodyText: String,
    )

    private fun <T> executeSocketRequest(
        method: String,
        url: String,
        body: String?,
        participantId: String?,
        parser: (JSONObject) -> T,
    ): T {
        // Validate endpoint address, scheme, and port
        val endpoint = validateEndpoint(url)
        val path = extractPath(url)

        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        if (bodyBytes != null && bodyBytes.size > MAX_REQUEST_BODY_BYTES) {
            throw IllegalArgumentException("Request body exceeds limit of $MAX_REQUEST_BODY_BYTES bytes")
        }

        // ponytail: Dedicated bounded socket transport for local P2P HTTP avoids platform cleartext block while preserving global network security
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = READ_TIMEOUT_MS
            socket.connect(InetSocketAddress(endpoint.ip, endpoint.port), CONNECT_TIMEOUT_MS)

            val outputStream = socket.getOutputStream()
            val requestHeader = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: ${endpoint.ip}:${endpoint.port}\r\n")
                append("User-Agent: Vynce-ListenTogether\r\n")
                append("Accept: application/json\r\n")
                append("Connection: close\r\n")
                if (!participantId.isNullOrBlank()) {
                    append("X-Participant-Id: $participantId\r\n")
                }
                if (bodyBytes != null) {
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                }
                append("\r\n")
            }

            outputStream.write(requestHeader.toByteArray(Charsets.UTF_8))
            if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                outputStream.write(bodyBytes)
            }
            outputStream.flush()

            val response = readHttpResponse(socket.getInputStream())
            if (response.statusCode !in 200..299) {
                throw IllegalStateException(errorMessage(response.bodyText, response.statusCode, url))
            }

            val json = if (response.bodyText.isNotBlank()) JSONObject(response.bodyText) else JSONObject()
            return parser(json)
        }
    }

    private fun extractPath(url: String): String {
        var clean = url.trim()
        if (clean.contains("://")) {
            clean = clean.substringAfter("://")
        }
        val slashIndex = clean.indexOf('/')
        return if (slashIndex >= 0) clean.substring(slashIndex) else "/"
    }

    internal fun readHttpResponse(inputStream: InputStream): RawHttpResponse {
        val headerBytes = ByteArrayOutputStream()
        var prev = -1
        var prevPrev = -1
        var prevPrevPrev = -1
        var matchedDelimiter = false

        while (headerBytes.size() < MAX_HEADER_BYTES) {
            val b = inputStream.read()
            if (b == -1) break
            headerBytes.write(b)
            if (b == '\n'.code) {
                if (prev == '\r'.code && prevPrev == '\n'.code && prevPrevPrev == '\r'.code) {
                    matchedDelimiter = true
                    break
                } else if (prev == '\n'.code) {
                    matchedDelimiter = true
                    break
                }
            }
            prevPrevPrev = prevPrev
            prevPrev = prev
            prev = b
        }

        if (!matchedDelimiter && headerBytes.size() >= MAX_HEADER_BYTES) {
            throw IOException("HTTP response headers exceed $MAX_HEADER_BYTES bytes limit")
        }
        if (headerBytes.size() == 0) {
            throw IOException("Empty response from peer host")
        }

        val headerString = headerBytes.toString(Charsets.ISO_8859_1.name())
        val lines = headerString.lines()
        if (lines.isEmpty()) throw IOException("Malformed HTTP response")

        val statusLine = lines[0].trim()
        val statusParts = statusLine.split(" ", limit = 3)
        if (statusParts.size < 2 || !statusParts[0].startsWith("HTTP/", ignoreCase = true)) {
            throw IOException("Malformed HTTP status line: $statusLine")
        }
        val statusCode = statusParts[1].toIntOrNull() ?: throw IOException("Invalid HTTP status code: $statusLine")

        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) {
                val key = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                headers[key] = value
            }
        }

        val transferEncoding = headers["transfer-encoding"]?.lowercase()
        if (transferEncoding != null && transferEncoding.contains("chunked")) {
            throw IOException("Chunked transfer encoding is not supported for local peer communication")
        }

        val contentLength = headers["content-length"]?.toIntOrNull()
        val bodyBytes: ByteArray
        if (contentLength != null) {
            if (contentLength > MAX_RESPONSE_BODY_BYTES) {
                throw IOException("Response body length ($contentLength) exceeds maximum allowed ($MAX_RESPONSE_BODY_BYTES)")
            }
            if (contentLength < 0) {
                throw IOException("Invalid negative Content-Length")
            }
            val buffer = ByteArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val count = inputStream.read(buffer, totalRead, contentLength - totalRead)
                if (count == -1) break
                totalRead += count
            }
            if (totalRead < contentLength) {
                throw IOException("Premature end of stream: expected $contentLength bytes, received $totalRead")
            }
            bodyBytes = buffer
        } else {
            val bodyOut = ByteArrayOutputStream()
            val temp = ByteArray(4096)
            while (true) {
                val count = inputStream.read(temp)
                if (count == -1) break
                if (bodyOut.size() + count > MAX_RESPONSE_BODY_BYTES) {
                    throw IOException("Response body exceeds maximum allowed ($MAX_RESPONSE_BODY_BYTES)")
                }
                bodyOut.write(temp, 0, count)
            }
            bodyBytes = bodyOut.toByteArray()
        }

        val bodyText = String(bodyBytes, Charsets.UTF_8)
        return RawHttpResponse(statusCode, headers, bodyText)
    }

    private fun errorMessage(
        text: String,
        code: Int,
        url: String = "",
    ): String =
        runCatching {
            JSONObject(text).optString("error")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: when (code) {
            403 -> "Action not permitted in this room"
            404 -> "Room not found or no longer active"
            405 -> "Server method not supported"
            413 -> "Payload too large for room coordinator"
            503 -> "Room host temporarily unavailable"
            else -> "Room connection failed ($code)"
        }
}

data class ListenTogetherSession(
    val code: String,
    val participantId: String,
    val joinUrl: String,
    val participants: Int,
    val participantList: List<ListenTogetherParticipant>,
    val hostName: String,
    val controllerId: String,
    val controllerName: String,
    val stateVersion: Long,
    val serverNow: Long,
    val state: ListenTogetherPlaybackState?,
) {
    companion object {
        fun fromJson(json: JSONObject): ListenTogetherSession =
            ListenTogetherSession(
                code = json.optString("code"),
                participantId = json.optString("participantId"),
                joinUrl = json.optString("joinUrl"),
                participants = json.optInt("participants", 1),
                participantList =
                    json.optJSONArray("participantList")?.let { array ->
                        (0 until array.length()).mapNotNull { index ->
                            array.optJSONObject(index)?.let(ListenTogetherParticipant::fromJson)
                        }
                    }.orEmpty(),
                hostName = json.optString("hostName").ifBlank { "Vynce Listener" },
                controllerId = json.optString("controllerId"),
                controllerName = json.optString("controllerName").ifBlank { "Vynce Listener" },
                stateVersion = json.optLong("stateVersion", 0L),
                serverNow = json.optLong("serverNow", System.currentTimeMillis()),
                state = json.optJSONObject("state")?.let(ListenTogetherPlaybackState::fromJson),
            )
    }
}

data class ListenTogetherParticipant(
    val id: String,
    val name: String,
    val isHost: Boolean,
) {
    companion object {
        fun fromJson(json: JSONObject) =
            ListenTogetherParticipant(
                id = json.optString("id"),
                name = json.optString("name").ifBlank { "Vynce Listener" },
                isHost = json.optBoolean("isHost"),
            )
    }
}

data class ListenTogetherPlaybackState(
    val songId: String,
    val title: String,
    val artists: List<String>,
    val thumbnailUrl: String?,
    val positionMs: Long,
    val isPlaying: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toMediaMetadata() =
        MediaMetadata(
            id = songId,
            title = title,
            artists = artists.map { MediaMetadata.Artist(id = null, name = it) },
            duration = -1,
            thumbnailUrl = thumbnailUrl,
            genre = emptyList()
        )

    fun toJson() =
        JSONObject()
            .put("songId", songId)
            .put("title", title)
            .put("artists", JSONArray(artists))
            .put("thumbnailUrl", thumbnailUrl)
            .put("positionMs", positionMs)
            .put("isPlaying", isPlaying)
            .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): ListenTogetherPlaybackState =
            ListenTogetherPlaybackState(
                songId = json.optString("songId"),
                title = json.optString("title"),
                artists =
                    json.optJSONArray("artists")?.let { array ->
                        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty(),
                thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                positionMs = json.optLong("positionMs"),
                isPlaying = json.optBoolean("isPlaying"),
                updatedAt = json.optLong("updatedAt"),
            )
    }
}
