package com.vynce.app.utils.listentogether

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight in-app HTTP room coordinator that runs directly on the host device.
 * Enables zero-latency, local/Wi-Fi/hotspot Listen Together without requiring external cloud servers.
 */
object EmbeddedRoomServer {
    private const val TAG = "EmbeddedRoomServer"
    const val DEFAULT_PORT = 8945
    private const val GUEST_TIMEOUT_MS = 15_000L
    private const val MAX_REQUEST_BODY_BYTES = 65536 // 64 KB
    private const val MAX_HEADER_BYTES = 8192 // 8 KB

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var activeSession: ListenTogetherSession? = null
    private val participants = ConcurrentHashMap<String, ListenTogetherParticipant>()
    private val guestHeartbeats = ConcurrentHashMap<String, Long>()
    private val stateVersion = AtomicLong(1L)

    @Volatile
    var isRunning = false
        private set

    /**
     * Retrieves the primary local IPv4 address of this device (Wi-Fi, hotspot, or ethernet).
     */
    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var fallbackIp: String? = null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("127.")) continue
                        // Prioritize Wi-Fi / Hotspot ranges (192.168.x.x, 10.x.x.x, 172.x.x.x)
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                        if (fallbackIp == null) {
                            fallbackIp = ip
                        }
                    }
                }
            }
            fallbackIp
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP address", e)
            null
        }
    }

    /**
     * Converts a 4-octet IPv4 string (e.g. 192.168.1.45) into an 8-character uppercase hex code (e.g. C0A8012D).
     */
    fun ipToRoomCode(ip: String): String {
        return try {
            val parts = ip.trim().split('.').map { it.toInt() }
            if (parts.size == 4) {
                parts.joinToString("") { "%02X".format(it) }
            } else {
                generateRandomRoomCode()
            }
        } catch (e: Exception) {
            generateRandomRoomCode()
        }
    }

    /**
     * Decodes an 8-character uppercase hex code into a 4-octet IPv4 string.
     * If the input is already an IP or contains a port, cleans and returns it.
     */
    fun roomCodeToIp(code: String): String? {
        val clean = code.trim().uppercase()
        if (clean.contains(".")) return clean // Already IP or host
        if (clean.length == 8 && clean.all { it in "0123456789ABCDEF" }) {
            return try {
                val p0 = clean.substring(0, 2).toInt(16)
                val p1 = clean.substring(2, 4).toInt(16)
                val p2 = clean.substring(4, 6).toInt(16)
                val p3 = clean.substring(6, 8).toInt(16)
                "$p0.$p1.$p2.$p3"
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun generateRandomRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    /**
     * Starts the embedded room coordinator on the given port (default 8945).
     */
    @Synchronized
    fun start(context: Context? = null, session: ListenTogetherSession, port: Int = DEFAULT_PORT) {
        stop()

        activeSession = session
        participants.clear()
        guestHeartbeats.clear()
        session.participantList.forEach { participants[it.id] = it }
        stateVersion.set(session.stateVersion.coerceAtLeast(1L))

        if (context != null) {
            NsdRoomDiscovery.registerRoomService(context, session.code, session.hostName, port)
        }

        serverJob = scope.launch {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 50)
                serverSocket = socket
                isRunning = true
                Log.i(TAG, "EmbeddedRoomServer started on port $port for room ${session.code}")

                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        scope.launch {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (socket.isClosed) break
                        Log.w(TAG, "Error accepting client connection", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start EmbeddedRoomServer on port $port", e)
                isRunning = false
            }
        }
    }

    /**
     * Stops the embedded room server and clears local in-memory session state.
     */
    @Synchronized
    fun stop() {
        isRunning = false
        NsdRoomDiscovery.unregisterRoomService()
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        activeSession = null
        participants.clear()
        guestHeartbeats.clear()
        stateVersion.set(1L)
        Log.i(TAG, "EmbeddedRoomServer stopped")
    }

    /**
     * Direct local fast-path for updating playback state without network hops when hosting.
     */
    fun updateLocalState(state: ListenTogetherPlaybackState): ListenTogetherSession? {
        val current = activeSession ?: return null
        val newVersion = stateVersion.incrementAndGet()
        val updated = current.copy(
            state = state,
            stateVersion = newVersion,
            serverNow = System.currentTimeMillis()
        )
        activeSession = updated
        return updated
    }

    /**
     * Direct local fast-path for getting current session when hosting.
     */
    fun getLocalSession(): ListenTogetherSession? {
        pruneStaleGuests()
        val current = activeSession ?: return null
        return current.copy(
            participantList = participants.values.toList(),
            participants = participants.size,
            serverNow = System.currentTimeMillis()
        )
    }

    private fun pruneStaleGuests() {
        val now = System.currentTimeMillis()
        var changed = false
        for ((pId, lastSeen) in guestHeartbeats) {
            val participant = participants[pId]
            if (participant != null && !participant.isHost && now - lastSeen > GUEST_TIMEOUT_MS) {
                participants.remove(pId)
                guestHeartbeats.remove(pId)
                changed = true
                Log.i(TAG, "Pruned stale participant: ${participant.name} ($pId)")
            }
        }
        if (changed) {
            val current = activeSession
            if (current != null) {
                activeSession = current.copy(
                    participants = participants.size,
                    participantList = participants.values.toList()
                )
            }
        }
    }

    private fun errorJson(msg: String): String = "{\"error\":\"${msg.replace("\"", "\\\"")}\"}"

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 4000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val headerBytes = java.io.ByteArrayOutputStream()
            var prev = -1
            var prevPrev = -1
            var prevPrevPrev = -1
            var matchedDelimiter = false

            while (headerBytes.size() < MAX_HEADER_BYTES) {
                val b = input.read()
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
                sendResponse(output, 431, "Request Header Fields Too Large", errorJson("Headers exceed limit"))
                return
            }
            if (headerBytes.size() == 0) return

            val headerString = headerBytes.toString(Charsets.ISO_8859_1.name())
            val lines = headerString.lines()
            if (lines.isEmpty()) return

            val requestLine = lines[0].trim()
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1].substringBefore('?')

            var contentLength = 0
            var clientParticipantId: String? = null

            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    } else if (key == "x-participant-id") {
                        clientParticipantId = value
                    }
                }
            }

            if (contentLength > MAX_REQUEST_BODY_BYTES || contentLength < 0) {
                sendResponse(output, 413, "Payload Too Large", errorJson("Request body exceeds maximum allowed size of $MAX_REQUEST_BODY_BYTES bytes"))
                return
            }

            val body = if (contentLength > 0) {
                val buffer = ByteArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val count = input.read(buffer, readTotal, contentLength - readTotal)
                    if (count == -1) break
                    readTotal += count
                }
                if (readTotal < contentLength) {
                    sendResponse(output, 400, "Bad Request", errorJson("Premature end of request body"))
                    return
                }
                String(buffer, 0, readTotal, Charsets.UTF_8)
            } else {
                ""
            }

            if (clientParticipantId != null && participants.containsKey(clientParticipantId)) {
                guestHeartbeats[clientParticipantId] = System.currentTimeMillis()
            }

            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "")
                return
            }

            // GET health / session info
            if (method == "GET" && (path == "/" || path == "/health")) {
                sendResponse(output, 200, "OK", JSONObject().put("status", "ok").put("name", "Vynce Listen Together").toString())
                return
            }

            // POST create session: only permitted from local host/loopback; external LAN cannot create or replace rooms
            if (method == "POST" && (path == "/sessions" || path == "/sessions/")) {
                val isLoopback = socket.inetAddress?.isLoopbackAddress == true
                if (!isLoopback) {
                    sendResponse(output, 403, "Forbidden", errorJson("Room creation is only permitted locally by the host"))
                    return
                }
                if (activeSession != null) {
                    sendResponse(output, 403, "Forbidden", errorJson("A room is already active on this host"))
                    return
                }
                val reqJson = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                val name = reqJson.optString("name").ifBlank { "Vynce Host" }
                val stateJson = reqJson.optJSONObject("state")
                val state = stateJson?.let(ListenTogetherPlaybackState::fromJson)

                val hostIp = getLocalIpAddress() ?: "127.0.0.1"
                val roomCode = ipToRoomCode(hostIp)
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
                start(null, newSession)
                sendResponse(output, 200, "OK", sessionToJson(newSession).toString())
                return
            }

            // Structured room routes: /sessions/<roomCode>[/<action>]
            val segments = path.trim('/').split('/')
            if (segments.isNotEmpty() && segments[0].equals("sessions", ignoreCase = true)) {
                val requestedCode = segments.getOrNull(1)?.uppercase()
                val action = segments.getOrNull(2)?.lowercase()
                val current = activeSession

                if (requestedCode == null || current == null || !current.code.equals(requestedCode, ignoreCase = true)) {
                    sendResponse(output, 404, "Not Found", errorJson("Room not found or no longer active"))
                    return
                }

                when {
                    method == "GET" && action == null -> {
                        val session = getLocalSession()
                        if (session != null) {
                            sendResponse(output, 200, "OK", sessionToJson(session).toString())
                        } else {
                            sendResponse(output, 404, "Not Found", errorJson("Room not active"))
                        }
                    }

                    method == "POST" && action == "join" -> {
                        val reqJson = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                        val name = reqJson.optString("name").ifBlank { "Guest" }
                        val newParticipant = ListenTogetherParticipant(
                            id = "usr_${UUID.randomUUID().toString().take(8)}",
                            name = name,
                            isHost = false
                        )

                        participants[newParticipant.id] = newParticipant
                        guestHeartbeats[newParticipant.id] = System.currentTimeMillis()

                        val newVersion = stateVersion.incrementAndGet()
                        val updated = current.copy(
                            participants = participants.size,
                            participantList = participants.values.toList(),
                            stateVersion = newVersion,
                            serverNow = System.currentTimeMillis()
                        )
                        activeSession = updated

                        val guestSession = updated.copy(participantId = newParticipant.id)
                        sendResponse(output, 200, "OK", sessionToJson(guestSession).toString())
                    }

                    method == "POST" && action == "state" -> {
                        val isLoopback = socket.inetAddress?.isLoopbackAddress == true
                        val reqJson = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                        val callerId = clientParticipantId?.ifBlank { null } ?: reqJson.optString("participantId").ifBlank { null }
                        val isAuthorized = isLoopback && callerId != null && (
                            callerId == current.controllerId ||
                            callerId == current.participantId ||
                            participants[callerId]?.isHost == true
                        )
                        if (!isAuthorized) {
                            sendResponse(output, 403, "Forbidden", errorJson("Only the host or room controller may update playback state"))
                            return
                        }

                        val stateJson = reqJson.optJSONObject("state")
                        if (stateJson != null) {
                            val state = ListenTogetherPlaybackState.fromJson(stateJson)
                            val updated = updateLocalState(state)
                            if (updated != null) {
                                sendResponse(output, 200, "OK", sessionToJson(updated).toString())
                            } else {
                                sendResponse(output, 500, "Internal Error", errorJson("Failed to update state"))
                            }
                        } else {
                            sendResponse(output, 400, "Bad Request", errorJson("Missing state"))
                        }
                    }

                    method == "POST" && action == "leave" -> {
                        val reqJson = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                        val callerId = clientParticipantId?.ifBlank { null } ?: reqJson.optString("participantId").ifBlank { null }
                        if (callerId != null) {
                            val participant = participants[callerId]
                            // Only valid non-host guests can be removed via network leave request
                            if (participant != null && !participant.isHost && callerId != current.participantId) {
                                participants.remove(callerId)
                                guestHeartbeats.remove(callerId)
                                val updated = current.copy(
                                    participants = participants.size,
                                    participantList = participants.values.toList(),
                                    serverNow = System.currentTimeMillis()
                                )
                                activeSession = updated
                            }
                        }
                        sendResponse(output, 200, "OK", "{\"ok\":true}")
                    }

                    else -> {
                        sendResponse(output, 404, "Not Found", errorJson("Endpoint not found"))
                    }
                }
            } else {
                sendResponse(output, 404, "Not Found", errorJson("Endpoint not found"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling client request", e)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        body: String
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, X-Participant-Id\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        if (bodyBytes.isNotEmpty()) {
            output.write(bodyBytes)
        }
        output.flush()
    }

    private fun sessionToJson(session: ListenTogetherSession): JSONObject {
        return JSONObject().apply {
            put("code", session.code)
            put("participantId", session.participantId)
            put("joinUrl", session.joinUrl)
            put("participants", session.participants)
            put("participantList", JSONArray().apply {
                session.participantList.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("isHost", p.isHost)
                    })
                }
            })
            put("hostName", session.hostName)
            put("controllerId", session.controllerId)
            put("controllerName", session.controllerName)
            put("stateVersion", session.stateVersion)
            put("serverNow", session.serverNow)
            session.state?.let { put("state", it.toJson()) }
        }
    }
}
