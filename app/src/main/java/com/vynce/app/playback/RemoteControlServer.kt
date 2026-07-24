package com.vynce.app.playback

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.vynce.app.extensions.vynceMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@Serializable
data class RemoteQueueItem(val title: String, val artist: String, val isCurrent: Boolean)

@Serializable
data class RemoteStatus(
    val title: String,
    val artist: String,
    val album: String,
    val thumbnailUrl: String,
    val isPlaying: Boolean,
    val position: Long,
    val duration: Long,
    val index: Int,
    val queue: List<RemoteQueueItem>
)

class RemoteControlServer(private val service: MusicService) {
    private val TAG = "RemoteControlServer"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isRunning = false

    private var clientExecutor: ExecutorService? = null

    fun start(): Boolean {
        var listener: ServerSocket? = null
        var startupFailure: IOException? = null

        synchronized(this) {
            if (isRunning) return true
            try {
                listener = ServerSocket(PORT)
                serverSocket = listener
                clientExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_CONNECTIONS)
                isRunning = true
            } catch (exception: IOException) {
                startupFailure = exception
            }
        }

        startupFailure?.let { exception ->
            Log.e(TAG, "Unable to start remote control server", exception)
            showToast("Jam Mode couldn't start because port $PORT is already in use.")
            return false
        }

        val listeningSocket = checkNotNull(listener)
        val shareUrl = getShareUrl()
        Log.i(TAG, "Remote control server started on port $PORT")
        showToast("Jam Mode: $shareUrl")

        thread(name = "VynceRemoteServer") {
            while (isRunning) {
                val connection = try {
                    listeningSocket.accept()
                } catch (exception: IOException) {
                    if (isRunning) Log.e(TAG, "Remote control server socket error", exception)
                    break
                }

                val executor = synchronized(this) { clientExecutor }
                if (!isRunning || executor == null) {
                    connection.close()
                    break
                }

                try {
                    executor.execute { handleConnection(connection) }
                } catch (_: RejectedExecutionException) {
                    connection.close()
                }
            }
        }
        return true
    }

    fun stop() {
        val listener: ServerSocket?
        val executor: ExecutorService?
        synchronized(this) {
            isRunning = false
            listener = serverSocket
            serverSocket = null
            executor = clientExecutor
            clientExecutor = null
        }
        try {
            listener?.close()
            Log.i(TAG, "Remote control server stopped")
        } catch (exception: IOException) {
            Log.e(TAG, "Failed to stop remote control server", exception)
        }
        executor?.shutdownNow()
    }

    private fun handleConnection(socket: Socket) {
        try {
            socket.use { connection ->
                connection.soTimeout = SOCKET_READ_TIMEOUT_MS
                val output = connection.getOutputStream()
                val reader = BufferedReader(InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                if (requestLine.length > MAX_REQUEST_LINE_LENGTH) {
                    writeResponse(output, "400 Bad Request", "Request too large")
                    return
                }

                var headerCount = 0
                while (true) {
                    val header = reader.readLine() ?: return
                    if (header.isEmpty()) break
                    if (header.length > MAX_HEADER_LENGTH || ++headerCount > MAX_HEADERS) {
                        writeResponse(output, "400 Bad Request", "Request headers are too large")
                        return
                    }
                }

                val requestParts = requestLine.split(" ", limit = 3)
                if (requestParts.size != 3) {
                    writeResponse(output, "400 Bad Request", "Malformed request")
                    return
                }
                if (requestParts[0] != "GET" && requestParts[0] != "POST") {
                    writeResponse(output, "405 Method Not Allowed", "Only GET and POST requests are supported")
                    return
                }

                val pathWithQuery = requestParts[1]
                val path = pathWithQuery.substringBefore("?")
                val query = pathWithQuery.substringAfter("?", "")
                val parameters = parseQuery(query)
                if (path != "/mcp" && path != "/api/mcp" && parameters["token"] != sessionToken) {
                    writeResponse(output, "403 Forbidden", "This Jam Mode link is not authorized")
                    return
                }

                when {
                    path == "/mcp" || path == "/api/mcp" -> {
                        val mcpResponse = runOnMainThread {
                            val player = service.player
                            val currentItem = player.currentMediaItem
                            val metadata = currentItem?.vynceMetadata
                            val title = metadata?.title ?: currentItem?.mediaMetadata?.title?.toString() ?: "Unknown"
                            val artist = metadata?.artists?.joinToString { it.name } ?: currentItem?.mediaMetadata?.artist?.toString() ?: "Unknown"
                            """
                            {
                              "jsonrpc": "2.0",
                              "result": {
                                "protocolVersion": "2024-11-05",
                                "capabilities": { "tools": {} },
                                "player": {
                                  "status": "${if (player.isPlaying) "playing" else "paused"}",
                                  "title": "$title",
                                  "artist": "$artist",
                                  "position_ms": ${player.currentPosition},
                                  "duration_ms": ${player.duration.coerceAtLeast(0)}
                                }
                              }
                            }
                            """.trimIndent()
                        }
                        writeResponse(output, "200 OK", mcpResponse, "application/json; charset=utf-8")
                    }
                    path == "/" || path == "/index.html" -> {
                        writeResponse(output, "200 OK", HTML_CONTENT, "text/html; charset=utf-8")
                    }
                    path == "/api/status" -> {
                        val jsonText = runOnMainThread {
                            val player = service.player
                            val currentItem = player.currentMediaItem
                            val metadata = currentItem?.vynceMetadata
                            
                            val queueList = service.queueBoard.value.getCurrentQueue()?.getCurrentQueueShuffled() ?: emptyList()
                            val currentIndex = player.currentMediaItemIndex

                            val remoteQueue = queueList.mapIndexed { idx, song ->
                                RemoteQueueItem(
                                    title = song.title,
                                    artist = song.artists.joinToString { it.name },
                                    isCurrent = idx == currentIndex
                                )
                            }

                            val status = RemoteStatus(
                                title = metadata?.title ?: currentItem?.mediaMetadata?.title?.toString() ?: "No Song Playing",
                                artist = metadata?.artists?.joinToString { it.name } ?: currentItem?.mediaMetadata?.artist?.toString() ?: "",
                                album = metadata?.album?.title ?: currentItem?.mediaMetadata?.albumTitle?.toString() ?: "",
                                thumbnailUrl = metadata?.thumbnailUrl ?: currentItem?.mediaMetadata?.artworkUri?.toString() ?: "",
                                isPlaying = player.isPlaying,
                                position = player.currentPosition,
                                duration = player.duration.coerceAtLeast(0),
                                index = currentIndex,
                                queue = remoteQueue
                            )
                            Json.encodeToString(status)
                        }
                        writeResponse(output, "200 OK", jsonText, "application/json; charset=utf-8")
                    }
                    path == "/api/control" -> {
                        val commandApplied = runOnMainThread {
                            val player = service.player
                            when (parameters["cmd"]) {
                                "play" -> {
                                    player.play()
                                    true
                                }
                                "pause" -> {
                                    player.pause()
                                    true
                                }
                                "next" -> {
                                    player.seekToNext()
                                    true
                                }
                                "prev" -> {
                                    player.seekToPrevious()
                                    true
                                }
                                "seek" -> parameters["pos"]?.toLongOrNull()?.let { position ->
                                    val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                    player.seekTo(position.coerceIn(0, duration))
                                    true
                                } ?: false
                                else -> false
                            }
                        }
                        if (commandApplied) {
                            writeResponse(output, "200 OK", "{\"status\":\"ok\"}", "application/json; charset=utf-8")
                        } else {
                            writeResponse(output, "400 Bad Request", "{\"status\":\"invalid_command\"}", "application/json; charset=utf-8")
                        }
                    }
                    else -> {
                        writeResponse(output, "404 Not Found", "Not Found")
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            Unit
        } catch (exception: Exception) {
            Log.w(TAG, "Remote control request failed", exception)
        }
    }

    private fun parseQuery(query: String): Map<String, String> = query
        .split("&")
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            runCatching {
                URLDecoder.decode(part.substring(0, separator), "UTF-8") to
                    URLDecoder.decode(part.substring(separator + 1), "UTF-8")
            }.getOrNull()
        }
        .toMap()

    private fun writeResponse(
        output: OutputStream,
        status: String,
        content: String,
        contentType: String = "text/plain; charset=utf-8",
    ) {
        val body = content.toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(StandardCharsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(service, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun <T> runOnMainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()

        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        mainHandler.post {
            result = runCatching(block)
            latch.countDown()
        }
        if (!latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw SocketTimeoutException("Timed out waiting for player state")
        }
        return checkNotNull(result).getOrThrow()
    }

    companion object {
        const val PORT = 8080
        private const val MAX_CONCURRENT_CONNECTIONS = 4
        private const val MAX_HEADERS = 32
        private const val MAX_HEADER_LENGTH = 8_192
        private const val MAX_REQUEST_LINE_LENGTH = 8_192
        private const val SOCKET_READ_TIMEOUT_MS = 10_000
        private const val MAIN_THREAD_TIMEOUT_MS = 3_000L
        private const val TOKEN_BYTES = 24

        private val sessionToken: String by lazy {
            Base64.encodeToString(
                ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
        }

        fun getShareUrl(ipAddress: String? = getLocalIpAddress()): String =
            "http://${ipAddress ?: "localhost"}:$PORT/?token=$sessionToken"

        fun getLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                var fallbackAddress: String? = null
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) continue

                    val addresses = networkInterface.inetAddresses
                    var address: String? = null
                    while (addresses.hasMoreElements()) {
                        val candidate = addresses.nextElement()
                        if (!candidate.isLoopbackAddress && candidate is Inet4Address) {
                            address = candidate.hostAddress
                            break
                        }
                    }
                    if (address == null) continue

                    if (networkInterface.name.startsWith("wlan") || networkInterface.displayName.contains("wi-fi", ignoreCase = true)) {
                        return address
                    }
                    if (fallbackAddress == null) fallbackAddress = address
                }
                return fallbackAddress
            } catch (exception: Exception) {
                Log.w("RemoteControlServer", "Unable to determine a local IPv4 address", exception)
                return null
            }
        }
    }

    private val HTML_CONTENT = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="referrer" content="no-referrer">
                <title>Vynce Remote Control</title>
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;700&display=swap" rel="stylesheet">
                <style>
                    * {
                        box-sizing: border-box;
                        margin: 0;
                        padding: 0;
                    }
                    body {
                        font-family: 'Inter', sans-serif;
                        background: radial-gradient(circle at top, #182235, #0b0f19);
                        color: #ffffff;
                        min-height: 100vh;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        padding: 24px;
                    }
                    .container {
                        width: 100%;
                        max-width: 480px;
                        background: rgba(255, 255, 255, 0.03);
                        backdrop-filter: blur(20px);
                        -webkit-backdrop-filter: blur(20px);
                        border: 1px rgba(255, 255, 255, 0.08) solid;
                        border-radius: 24px;
                        padding: 24px;
                        box-shadow: 0 20px 40px rgba(0,0,0,0.4);
                        display: flex;
                        flex-direction: column;
                        gap: 20px;
                    }
                    .header {
                        text-align: center;
                        font-weight: 700;
                        font-size: 1.2rem;
                        letter-spacing: 1px;
                        color: #7983ff;
                        text-transform: uppercase;
                    }
                    .artwork-container {
                        width: 100%;
                        aspect-ratio: 1;
                        border-radius: 16px;
                        overflow: hidden;
                        box-shadow: 0 12px 24px rgba(0,0,0,0.5);
                        position: relative;
                        background: #131926;
                    }
                    .artwork {
                        width: 100%;
                        height: 100%;
                        object-fit: cover;
                        transition: transform 0.5s ease;
                    }
                    .artwork:hover {
                        transform: scale(1.03);
                    }
                    .track-info {
                        text-align: center;
                    }
                    .title {
                        font-size: 1.4rem;
                        font-weight: 700;
                        margin-bottom: 6px;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                    .artist {
                        font-size: 1rem;
                        color: rgba(255,255,255,0.6);
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                    .progress-container {
                        display: flex;
                        flex-direction: column;
                        gap: 8px;
                    }
                    .slider {
                        width: 100%;
                        -webkit-appearance: none;
                        height: 6px;
                        border-radius: 3px;
                        background: rgba(255,255,255,0.1);
                        outline: none;
                        cursor: pointer;
                    }
                    .slider::-webkit-slider-thumb {
                        -webkit-appearance: none;
                        width: 14px;
                        height: 14px;
                        border-radius: 50%;
                        background: #7983ff;
                        cursor: pointer;
                        box-shadow: 0 0 10px rgba(121,131,255,0.8);
                        transition: transform 0.1s;
                    }
                    .slider::-webkit-slider-thumb:hover {
                        transform: scale(1.2);
                    }
                    .time-info {
                        display: flex;
                        justify-content: space-between;
                        font-size: 0.8rem;
                        color: rgba(255,255,255,0.5);
                    }
                    .controls {
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        gap: 24px;
                        margin: 10px 0;
                    }
                    .btn {
                        background: none;
                        border: none;
                        color: #ffffff;
                        cursor: pointer;
                        transition: all 0.2s ease;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .btn:hover {
                        color: #7983ff;
                        transform: scale(1.1);
                    }
                    .btn:active {
                        transform: scale(0.95);
                    }
                    .btn-play-pause {
                        width: 64px;
                        height: 64px;
                        border-radius: 50%;
                        background: #7983ff;
                        color: #0b0f19;
                        box-shadow: 0 8px 16px rgba(121,131,255,0.3);
                    }
                    .btn-play-pause:hover {
                        color: #0b0f19;
                        background: #8b95ff;
                        box-shadow: 0 12px 20px rgba(121,131,255,0.5);
                    }
                    .btn-play-pause svg {
                        width: 28px;
                        height: 28px;
                        fill: currentColor;
                    }
                    .btn-nav svg {
                        width: 32px;
                        height: 32px;
                        fill: currentColor;
                    }
                    .queue-title {
                        font-size: 1.1rem;
                        font-weight: 700;
                        color: #7983ff;
                        margin-top: 10px;
                        border-bottom: 1px rgba(255,255,255,0.08) solid;
                        padding-bottom: 8px;
                    }
                    .queue-list {
                        max-height: 180px;
                        overflow-y: auto;
                        display: flex;
                        flex-direction: column;
                        gap: 8px;
                        padding-right: 4px;
                    }
                    .queue-list::-webkit-scrollbar {
                        width: 4px;
                    }
                    .queue-list::-webkit-scrollbar-thumb {
                        background: rgba(255,255,255,0.1);
                        border-radius: 2px;
                    }
                    .queue-item {
                        padding: 10px 12px;
                        border-radius: 8px;
                        background: rgba(255,255,255,0.02);
                        border: 1px solid transparent;
                        display: flex;
                        flex-direction: column;
                        font-size: 0.9rem;
                    }
                    .queue-item.active {
                        background: rgba(121,131,255,0.15);
                        border-color: rgba(121,131,255,0.3);
                    }
                    .queue-item-title {
                        font-weight: 500;
                    }
                    .queue-item-artist {
                        font-size: 0.8rem;
                        color: rgba(255,255,255,0.5);
                        margin-top: 2px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">Vynce Jam Mode</div>
                    <div class="artwork-container">
                        <img id="art" class="artwork" src="" alt="Album Art">
                    </div>
                    <div class="track-info">
                        <div id="title" class="title">Loading...</div>
                        <div id="artist" class="artist">Connecting to device...</div>
                    </div>
                    <div class="progress-container">
                        <input type="range" id="progress" class="slider" min="0" max="100" value="0">
                        <div class="time-info">
                            <span id="time-current">0:00</span>
                            <span id="time-total">0:00</span>
                        </div>
                    </div>
                    <div class="controls">
                        <button class="btn btn-nav" onclick="control('prev')">
                            <svg viewBox="0 0 24 24"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg>
                        </button>
                        <button id="play-pause" class="btn btn-play-pause" onclick="togglePlay()">
                            <svg id="play-icon" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
                        </button>
                        <button class="btn btn-nav" onclick="control('next')">
                            <svg viewBox="0 0 24 24"><path d="M6 18l8.5-6L6 6zm9-12h2v12h-2z"/></svg>
                        </button>
                    </div>
                    <div class="queue-title">Up Next</div>
                    <div id="queue" class="queue-list">
                        <!-- Queue items -->
                    </div>
                </div>

                <script>
                    let isPlaying = false;
                    let duration = 0;
                    let position = 0;
                    let isDragging = false;
                    const sessionToken = new URLSearchParams(window.location.search).get('token');

                    const titleEl = document.getElementById('title');
                    const artistEl = document.getElementById('artist');
                    const artEl = document.getElementById('art');
                    const progressEl = document.getElementById('progress');
                    const timeCurrentEl = document.getElementById('time-current');
                    const timeTotalEl = document.getElementById('time-total');
                    const playIconEl = document.getElementById('play-icon');
                    const queueEl = document.getElementById('queue');

                    function apiUrl(path) {
                        const separator = path.includes('?') ? '&' : '?';
                        return path + separator + 'token=' + encodeURIComponent(sessionToken || '');
                    }

                    function formatTime(ms) {
                        if (isNaN(ms) || ms < 0) return '0:00';
                        const sec = Math.floor(ms / 1000);
                        const mins = Math.floor(sec / 60);
                        const secs = sec % 60;
                        return mins + ':' + (secs < 10 ? '0' : '') + secs;
                    }

                    function updateStatus() {
                        fetch(apiUrl('/api/status'))
                            .then(r => r.json())
                            .then(data => {
                                isPlaying = data.isPlaying;
                                duration = data.duration;
                                if (!isDragging) {
                                    position = data.position;
                                    progressEl.max = duration;
                                    progressEl.value = position;
                                    timeCurrentEl.textContent = formatTime(position);
                                }
                                titleEl.textContent = data.title;
                                artistEl.textContent = data.artist || (data.album ? 'Album: ' + data.album : '');
                                timeTotalEl.textContent = formatTime(duration);
                                
                                artEl.src = data.thumbnailUrl || 'https://via.placeholder.com/400?text=Vynce';

                                // Play/pause icon
                                if (isPlaying) {
                                    playIconEl.innerHTML = '<path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>';
                                } else {
                                    playIconEl.innerHTML = '<path d="M8 5v14l11-7z"/>';
                                }

                                queueEl.replaceChildren(...data.queue.map(item => {
                                    const queueItem = document.createElement('div');
                                    queueItem.className = 'queue-item' + (item.isCurrent ? ' active' : '');
                                    const title = document.createElement('span');
                                    title.className = 'queue-item-title';
                                    title.textContent = item.title;
                                    const artist = document.createElement('span');
                                    artist.className = 'queue-item-artist';
                                    artist.textContent = item.artist;
                                    queueItem.append(title, artist);
                                    return queueItem;
                                }));
                            })
                            .catch(err => console.error("Status error", err));
                    }

                    function control(cmd) {
                        fetch(apiUrl('/api/control?cmd=' + encodeURIComponent(cmd)));
                    }

                    function togglePlay() {
                        control(isPlaying ? 'pause' : 'play');
                    }

                    progressEl.addEventListener('mousedown', () => isDragging = true);
                    progressEl.addEventListener('touchstart', () => isDragging = true);

                    progressEl.addEventListener('change', (e) => {
                        isDragging = false;
                        const targetPos = e.target.value;
                        fetch(apiUrl('/api/control?cmd=seek&pos=' + encodeURIComponent(targetPos)));
                    });

                    progressEl.addEventListener('input', (e) => {
                        timeCurrentEl.textContent = formatTime(e.target.value);
                    });

                    // Update status periodically
                    setInterval(updateStatus, 1000);
                    updateStatus();
                </script>
            </body>
            </html>
        """.trimIndent()
    }
