package com.vynce.app.utils.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.Socket

class ListenTogetherNetworkingTest {

    @Test
    fun testPrivateIpv4Validation() {
        // RFC 1918 valid ranges
        assertTrue(ListenTogetherClient.isPrivateIpv4("192.168.1.1"))
        assertTrue(ListenTogetherClient.isPrivateIpv4("192.168.0.100"))
        assertTrue(ListenTogetherClient.isPrivateIpv4("10.0.0.1"))
        assertTrue(ListenTogetherClient.isPrivateIpv4("10.255.255.254"))
        assertTrue(ListenTogetherClient.isPrivateIpv4("172.16.0.1"))
        assertTrue(ListenTogetherClient.isPrivateIpv4("172.20.10.1"))
        assertTrue(ListenTogetherClient.isPrivateIpv4("172.31.255.254"))

        // Loopback & Link-local
        assertTrue(ListenTogetherClient.isPrivateIpv4("127.0.0.1"))
        assertTrue(ListenTogetherClient.isPrivateIpv4("169.254.1.5"))

        // Public IPs must be rejected
        assertFalse(ListenTogetherClient.isPrivateIpv4("8.8.8.8"))
        assertFalse(ListenTogetherClient.isPrivateIpv4("1.1.1.1"))
        assertFalse(ListenTogetherClient.isPrivateIpv4("142.250.190.46"))
        assertFalse(ListenTogetherClient.isPrivateIpv4("172.15.0.1")) // Below 172.16
        assertFalse(ListenTogetherClient.isPrivateIpv4("172.32.0.1")) // Above 172.31

        // Multicast, broadcast, zero
        assertFalse(ListenTogetherClient.isPrivateIpv4("224.0.0.1"))
        assertFalse(ListenTogetherClient.isPrivateIpv4("255.255.255.255"))
        assertFalse(ListenTogetherClient.isPrivateIpv4("0.0.0.0"))

        // Malformed strings & hostnames
        assertFalse(ListenTogetherClient.isPrivateIpv4("google.com"))
        assertFalse(ListenTogetherClient.isPrivateIpv4("evil.com"))
        assertFalse(ListenTogetherClient.isPrivateIpv4("192.168.1"))
        assertFalse(ListenTogetherClient.isPrivateIpv4("192.168.1.300"))
        assertFalse(ListenTogetherClient.isPrivateIpv4("010.0.0.1")) // Leading zero octet
    }

    @Test
    fun testEndpointValidationWithHexRoomCode() {
        // C0A8012D = 192.168.1.45
        val endpoint = ListenTogetherClient.validateEndpoint("C0A8012D")
        assertEquals("192.168.1.45", endpoint.ip)
        assertEquals(8945, endpoint.port)

        val baseUrl = ListenTogetherClient.resolveBaseUrl("C0A8012D")
        assertEquals("http://192.168.1.45:8945", baseUrl)
    }

    @Test
    fun testEndpointValidationWithRawIpAndUrl() {
        val endpoint1 = ListenTogetherClient.validateEndpoint("192.168.1.50")
        assertEquals("192.168.1.50", endpoint1.ip)
        assertEquals(8945, endpoint1.port)

        val endpoint2 = ListenTogetherClient.validateEndpoint("192.168.1.50:8945")
        assertEquals("192.168.1.50", endpoint2.ip)
        assertEquals(8945, endpoint2.port)

        val endpoint3 = ListenTogetherClient.validateEndpoint("http://192.168.1.50:8945/sessions/C0A8012D/join")
        assertEquals("192.168.1.50", endpoint3.ip)
        assertEquals(8945, endpoint3.port)
    }

    @Test
    fun testEndpointValidationRejectsPublicAndArbitraryHosts() {
        try {
            ListenTogetherClient.validateEndpoint("8.8.8.8")
            fail("Expected exception for public IP")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("private IPv4"))
        }

        try {
            ListenTogetherClient.validateEndpoint("evil.com")
            fail("Expected exception for arbitrary hostname")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("private IPv4"))
        }
    }

    @Test
    fun testEndpointValidationRejectsUnsupportedSchemesAndPorts() {
        try {
            ListenTogetherClient.validateEndpoint("https://192.168.1.50:8945")
            fail("Expected exception for https scheme")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unsupported scheme"))
        }

        try {
            ListenTogetherClient.validateEndpoint("ftp://192.168.1.50:8945")
            fail("Expected exception for ftp scheme")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unsupported scheme"))
        }

        try {
            ListenTogetherClient.validateEndpoint("192.168.1.50:80")
            fail("Expected exception for port 80")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("port 8945"))
        }

        try {
            ListenTogetherClient.validateEndpoint("192.168.1.50:22")
            fail("Expected exception for port 22")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("port 8945"))
        }
    }

    @Test
    fun testRoomCodeEncodingAndDecoding() {
        val ip = "192.168.43.1"
        val code = EmbeddedRoomServer.ipToRoomCode(ip)
        assertEquals("C0A82B01", code)

        val decodedIp = EmbeddedRoomServer.roomCodeToIp(code)
        assertEquals(ip, decodedIp)
    }

    @Test
    fun testEndpointValidationRejectsEmptyInput() {
        try {
            ListenTogetherClient.validateEndpoint("   ")
            fail("Expected exception for empty input")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun testLocalSessionAndStateHandling() {
        val host = ListenTogetherParticipant("usr_host1", "Host User", isHost = true)
        val session = ListenTogetherSession(
            code = "C0A8012D",
            participantId = host.id,
            joinUrl = "https://vynce.app/together/C0A8012D",
            participants = 1,
            participantList = listOf(host),
            hostName = host.name,
            controllerId = host.id,
            controllerName = host.name,
            stateVersion = 1L,
            serverNow = System.currentTimeMillis(),
            state = ListenTogetherPlaybackState(
                songId = "track123",
                title = "Test Song",
                artists = listOf("Test Artist"),
                thumbnailUrl = null,
                positionMs = 1500L,
                isPlaying = true
            )
        )

        EmbeddedRoomServer.start(null, session)
        var attempts = 0
        while (!EmbeddedRoomServer.isRunning && attempts++ < 30) {
            Thread.sleep(50)
        }
        assertTrue(EmbeddedRoomServer.isRunning)

        val local = EmbeddedRoomServer.getLocalSession()
        assertNotNull(local)
        assertEquals("C0A8012D", local!!.code)
        assertEquals(1, local.participants)
        assertEquals("Test Song", local.state?.title)

        val newState = ListenTogetherPlaybackState(
            songId = "track123",
            title = "Test Song",
            artists = listOf("Test Artist"),
            thumbnailUrl = null,
            positionMs = 5000L,
            isPlaying = true
        )
        val updated = EmbeddedRoomServer.updateLocalState(newState)
        assertNotNull(updated)
        assertEquals(2L, updated!!.stateVersion)
        assertEquals(5000L, updated.state?.positionMs)

        EmbeddedRoomServer.stop()
        assertFalse(EmbeddedRoomServer.isRunning)
    }

    @Test
    fun testReadHttpResponseSuccess() {
        val body = "{\"status\":\"ready\"}"
        val raw = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${body.length}\r\n\r\n$body"
        val response = ListenTogetherClient.readHttpResponse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
        assertEquals(200, response.statusCode)
        assertEquals(body, response.bodyText)
        assertEquals("application/json; charset=utf-8", response.headers["content-type"])
    }

    @Test
    fun testReadHttpResponseNoContent204() {
        val raw = "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        val response = ListenTogetherClient.readHttpResponse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
        assertEquals(204, response.statusCode)
        assertEquals("", response.bodyText)
    }

    @Test
    fun testReadHttpResponseEmptyBodyNoContentLength() {
        val raw = "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n"
        val response = ListenTogetherClient.readHttpResponse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
        assertEquals(200, response.statusCode)
        assertEquals("", response.bodyText)
    }

    @Test
    fun testReadHttpResponseRejectsChunkedTransferEncoding() {
        val raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nWiki\r\n0\r\n\r\n"
        try {
            ListenTogetherClient.readHttpResponse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
            fail("Expected exception for chunked transfer encoding")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Chunked transfer encoding is not supported"))
        }
    }

    @Test
    fun testReadHttpResponseDetectsPrematureTruncation() {
        val raw = "HTTP/1.1 200 OK\r\nContent-Length: 50\r\n\r\n{\"too_short\":true}"
        try {
            ListenTogetherClient.readHttpResponse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
            fail("Expected exception for truncated body")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Premature end of stream"))
        }
    }

    @Test
    fun testReadHttpResponseRejectsMalformedStatusLine() {
        try {
            ListenTogetherClient.readHttpResponse(ByteArrayInputStream("INVALID STATUS LINE\r\n\r\n".toByteArray(Charsets.UTF_8)))
            fail("Expected exception for non-HTTP status line")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Malformed HTTP status line"))
        }

        try {
            ListenTogetherClient.readHttpResponse(ByteArrayInputStream("HTTP/1.1 ABC OK\r\n\r\n".toByteArray(Charsets.UTF_8)))
            fail("Expected exception for non-numeric status code")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Invalid HTTP status code"))
        }
    }

    @Test
    fun testReadHttpResponseRejectsNegativeContentLength() {
        val raw = "HTTP/1.1 200 OK\r\nContent-Length: -5\r\n\r\n"
        try {
            ListenTogetherClient.readHttpResponse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
            fail("Expected exception for negative Content-Length")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("negative Content-Length"))
        }
    }

    @Test
    fun testReadHttpResponseRejectsOversizedHeaders() {
        val longHeader = "X-Padding: " + "A".repeat(8200) + "\r\n"
        val raw = "HTTP/1.1 200 OK\r\n$longHeader\r\n"
        try {
            ListenTogetherClient.readHttpResponse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
            fail("Expected exception for oversized headers")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("exceed 8192 bytes"))
        }
    }

    @Test
    fun testReadHttpResponseRejectsOversizedBody() {
        val raw = "HTTP/1.1 200 OK\r\nContent-Length: 200000\r\n\r\n"
        try {
            ListenTogetherClient.readHttpResponse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
            fail("Expected exception for oversized body")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("exceeds maximum allowed"))
        }
    }

    @Test
    fun testReadHttpResponseUtf8Handling() {
        val body = "{\"name\":\"Café München 🎵 日本語\"}"
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val raw = "HTTP/1.1 200 OK\r\nContent-Length: ${bodyBytes.size}\r\nContent-Type: application/json; charset=utf-8\r\n\r\n$body"
        val response = ListenTogetherClient.readHttpResponse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
        assertEquals(200, response.statusCode)
        assertEquals(body, response.bodyText)
    }

    private fun sendRawHttpRequest(port: Int, request: String): String {
        Thread.sleep(50)
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 4000
            socket.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val sb = StringBuilder()
            val buffer = CharArray(1024)
            var read: Int
            while (reader.read(buffer).also { read = it } != -1) {
                sb.append(buffer, 0, read)
            }
            return sb.toString()
        }
    }

    @Test
    fun testEmbeddedServerHandlesOptionsPreflight() {
        val host = ListenTogetherParticipant("usr_host1", "Host User", isHost = true)
        val session = ListenTogetherSession(
            code = "C0A8012D",
            participantId = host.id,
            joinUrl = "https://vynce.app/together/C0A8012D",
            participants = 1,
            participantList = listOf(host),
            hostName = host.name,
            controllerId = host.id,
            controllerName = host.name,
            stateVersion = 1L,
            serverNow = System.currentTimeMillis(),
            state = null
        )

        val testPort = 8961
        EmbeddedRoomServer.start(null, session, port = testPort)
        var attempts = 0
        while (!EmbeddedRoomServer.isRunning && attempts++ < 30) {
            Thread.sleep(50)
        }
        assertTrue(EmbeddedRoomServer.isRunning)

        try {
            val res = sendRawHttpRequest(testPort, "OPTIONS /sessions/C0A8012D/join HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\n\r\n")
            assertTrue(res.startsWith("HTTP/1.1 204 No Content"))
            assertTrue(res.contains("Access-Control-Allow-Origin: *"))
            assertTrue(res.contains("Access-Control-Allow-Methods: GET, POST, OPTIONS"))
        } finally {
            EmbeddedRoomServer.stop()
        }
    }

    @Test
    fun testEmbeddedServerRejectsMalformedPath() {
        val host = ListenTogetherParticipant("usr_host1", "Host User", isHost = true)
        val session = ListenTogetherSession(
            code = "C0A8012D",
            participantId = host.id,
            joinUrl = "https://vynce.app/together/C0A8012D",
            participants = 1,
            participantList = listOf(host),
            hostName = host.name,
            controllerId = host.id,
            controllerName = host.name,
            stateVersion = 1L,
            serverNow = System.currentTimeMillis(),
            state = null
        )

        val testPort = 8962
        EmbeddedRoomServer.start(null, session, port = testPort)
        var attempts = 0
        while (!EmbeddedRoomServer.isRunning && attempts++ < 30) {
            Thread.sleep(50)
        }
        assertTrue(EmbeddedRoomServer.isRunning)

        try {
            val res1 = sendRawHttpRequest(testPort, "GET /nonexistent HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\n\r\n")
            assertTrue(res1.startsWith("HTTP/1.1 404 Not Found"))

            val res2 = sendRawHttpRequest(testPort, "GET /sessions/FFFFFFFF HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\n\r\n")
            assertTrue(res2.startsWith("HTTP/1.1 404 Not Found"))
        } finally {
            EmbeddedRoomServer.stop()
        }
    }

    @Test
    fun testEmbeddedServerRejectsOversizedRequests() {
        val host = ListenTogetherParticipant("usr_host1", "Host User", isHost = true)
        val session = ListenTogetherSession(
            code = "C0A8012D",
            participantId = host.id,
            joinUrl = "https://vynce.app/together/C0A8012D",
            participants = 1,
            participantList = listOf(host),
            hostName = host.name,
            controllerId = host.id,
            controllerName = host.name,
            stateVersion = 1L,
            serverNow = System.currentTimeMillis(),
            state = null
        )

        val testPort = 8963
        EmbeddedRoomServer.start(null, session, port = testPort)
        var attempts = 0
        while (!EmbeddedRoomServer.isRunning && attempts++ < 30) {
            Thread.sleep(50)
        }
        assertTrue(EmbeddedRoomServer.isRunning)

        try {
            // Header > 8 KB -> 431
            val bigHeader = "X-Padding: " + "B".repeat(8500) + "\r\n"
            val resHeader = sendRawHttpRequest(testPort, "GET / HTTP/1.1\r\n$bigHeader\r\n")
            assertTrue(resHeader.startsWith("HTTP/1.1 431 Request Header Fields Too Large"))

            // Body > 64 KB -> 413
            val resBody = sendRawHttpRequest(testPort, "POST /sessions/C0A8012D/join HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nContent-Length: 70000\r\n\r\n")
            assertTrue(resBody.startsWith("HTTP/1.1 413 Payload Too Large"))
        } finally {
            EmbeddedRoomServer.stop()
        }
    }

    @Test
    fun testEmbeddedServerProtectsHostFromLeave() {
        val host = ListenTogetherParticipant("usr_host1", "Host User", isHost = true)
        val session = ListenTogetherSession(
            code = "C0A8012D",
            participantId = host.id,
            joinUrl = "https://vynce.app/together/C0A8012D",
            participants = 1,
            participantList = listOf(host),
            hostName = host.name,
            controllerId = host.id,
            controllerName = host.name,
            stateVersion = 1L,
            serverNow = System.currentTimeMillis(),
            state = null
        )

        val testPort = 8964
        EmbeddedRoomServer.start(null, session, port = testPort)
        var attempts = 0
        while (!EmbeddedRoomServer.isRunning && attempts++ < 30) {
            Thread.sleep(50)
        }
        assertTrue(EmbeddedRoomServer.isRunning)

        try {
            // Join a guest via HTTP
            val joinBody = "{\"name\":\"Guest User\"}"
            val joinRes = sendRawHttpRequest(
                testPort,
                "POST /sessions/C0A8012D/join HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nContent-Type: application/json\r\nContent-Length: ${joinBody.length}\r\n\r\n$joinBody"
            )
            assertTrue(joinRes.startsWith("HTTP/1.1 200 OK"))
            val localAfterJoin = EmbeddedRoomServer.getLocalSession()
            assertNotNull(localAfterJoin)
            assertEquals(2, localAfterJoin!!.participants)

            // Attempt to leave specifying the host's participantId -> Host must NOT be removed!
            val leaveHostReq = "{\"participantId\":\"usr_host1\"}"
            val leaveHostRes = sendRawHttpRequest(
                testPort,
                "POST /sessions/C0A8012D/leave HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nContent-Type: application/json\r\nContent-Length: ${leaveHostReq.length}\r\n\r\n$leaveHostReq"
            )
            assertTrue(leaveHostRes.startsWith("HTTP/1.1 200 OK"))
            val localAfterHostLeaveAttempt = EmbeddedRoomServer.getLocalSession()
            assertEquals(2, localAfterHostLeaveAttempt!!.participants)
            assertTrue(localAfterHostLeaveAttempt.participantList.any { it.id == "usr_host1" && it.isHost })
        } finally {
            EmbeddedRoomServer.stop()
        }
    }

    @Test
    fun testEmbeddedServerHandlesUtf8JsonWithJapaneseAccentsAndEmojiOverSocket() {
        val host = ListenTogetherParticipant("usr_host1", "Host User", isHost = true)
        val session = ListenTogetherSession(
            code = "C0A8012D",
            participantId = host.id,
            joinUrl = "https://vynce.app/together/C0A8012D",
            participants = 1,
            participantList = listOf(host),
            hostName = host.name,
            controllerId = host.id,
            controllerName = host.name,
            stateVersion = 1L,
            serverNow = System.currentTimeMillis(),
            state = null
        )

        val testPort = 8971
        EmbeddedRoomServer.start(null, session, port = testPort)
        var attempts = 0
        while (!EmbeddedRoomServer.isRunning && attempts++ < 30) {
            Thread.sleep(50)
        }
        assertTrue(EmbeddedRoomServer.isRunning)

        try {
            val unicodeName = "Café München 🎵 日本語 🚀"
            val joinJson = "{\"name\":\"$unicodeName\"}"
            val bodyBytes = joinJson.toByteArray(Charsets.UTF_8)
            val request = buildString {
                append("POST /sessions/C0A8012D/join HTTP/1.1\r\n")
                append("Host: 127.0.0.1:$testPort\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                append("\r\n")
                append(joinJson)
            }

            val response = sendRawHttpRequest(testPort, request)
            assertTrue("Expected 200 OK, got: $response", response.startsWith("HTTP/1.1 200 OK"))
            val localSession = EmbeddedRoomServer.getLocalSession()
            assertNotNull(localSession)
            assertEquals(2, localSession!!.participants)
            val guest = localSession.participantList.find { !it.isHost }
            assertNotNull(guest)
            assertEquals(unicodeName, guest!!.name)
        } finally {
            EmbeddedRoomServer.stop()
        }
    }

    @Test
    fun testEmbeddedServerHandlesEmptyAndPrematureRequestBodiesOverSocket() {
        val host = ListenTogetherParticipant("usr_host1", "Host User", isHost = true)
        val session = ListenTogetherSession(
            code = "C0A8012D",
            participantId = host.id,
            joinUrl = "https://vynce.app/together/C0A8012D",
            participants = 1,
            participantList = listOf(host),
            hostName = host.name,
            controllerId = host.id,
            controllerName = host.name,
            stateVersion = 1L,
            serverNow = System.currentTimeMillis(),
            state = null
        )

        val testPort = 8972
        EmbeddedRoomServer.start(null, session, port = testPort)
        var attempts = 0
        while (!EmbeddedRoomServer.isRunning && attempts++ < 30) {
            Thread.sleep(50)
        }
        assertTrue(EmbeddedRoomServer.isRunning)

        try {
            // 1. Empty body with Content-Length: 0
            val emptyBodyReq = "POST /sessions/C0A8012D/join HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nContent-Length: 0\r\n\r\n"
            val emptyRes = sendRawHttpRequest(testPort, emptyBodyReq)
            assertTrue("Expected 200 OK for empty body, got: $emptyRes", emptyRes.startsWith("HTTP/1.1 200 OK"))

            // 2. Prematurely terminated body (Content-Length says 50, but only 10 bytes written then output shutdown)
            val prematureReqHeader = "POST /sessions/C0A8012D/join HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nContent-Length: 50\r\n\r\n"
            val prematureRes = Socket("127.0.0.1", testPort).use { socket ->
                socket.soTimeout = 4000
                socket.getOutputStream().write(prematureReqHeader.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().write("1234567890".toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                socket.shutdownOutput() // Signal EOF to server
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val sb = StringBuilder()
                val buffer = CharArray(1024)
                var read: Int
                while (reader.read(buffer).also { read = it } != -1) {
                    sb.append(buffer, 0, read)
                }
                sb.toString()
            }
            assertTrue("Expected 400 Bad Request for premature body, got: $prematureRes", prematureRes.startsWith("HTTP/1.1 400 Bad Request"))
        } finally {
            EmbeddedRoomServer.stop()
        }
    }

    @Test
    fun testEmbeddedServerEnforces64KbLimitAgainstActualIncomingByteCount() {
        val host = ListenTogetherParticipant("usr_host1", "Host User", isHost = true)
        val session = ListenTogetherSession(
            code = "C0A8012D",
            participantId = host.id,
            joinUrl = "https://vynce.app/together/C0A8012D",
            participants = 1,
            participantList = listOf(host),
            hostName = host.name,
            controllerId = host.id,
            controllerName = host.name,
            stateVersion = 1L,
            serverNow = System.currentTimeMillis(),
            state = null
        )

        val testPort = 8973
        EmbeddedRoomServer.start(null, session, port = testPort)
        var attempts = 0
        while (!EmbeddedRoomServer.isRunning && attempts++ < 30) {
            Thread.sleep(50)
        }
        assertTrue(EmbeddedRoomServer.isRunning)

        try {
            // Content-Length 65537 bytes (1 byte over 64 KB limit of 65536)
            val oversizedReq = "POST /sessions/C0A8012D/join HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nContent-Length: 65537\r\n\r\n"
            val oversizedRes = sendRawHttpRequest(testPort, oversizedReq)
            assertTrue("Expected 413 Payload Too Large, got: $oversizedRes", oversizedRes.startsWith("HTTP/1.1 413 Payload Too Large"))

            // Negative Content-Length (-10)
            val negativeReq = "POST /sessions/C0A8012D/join HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nContent-Length: -10\r\n\r\n"
            val negativeRes = sendRawHttpRequest(testPort, negativeReq)
            assertTrue("Expected 413 Payload Too Large for negative Content-Length, got: $negativeRes", negativeRes.startsWith("HTTP/1.1 413 Payload Too Large"))
        } finally {
            EmbeddedRoomServer.stop()
        }
    }
}
