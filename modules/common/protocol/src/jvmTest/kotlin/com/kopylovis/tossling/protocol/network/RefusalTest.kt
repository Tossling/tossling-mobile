package com.kopylovis.tossling.protocol.network

import com.kopylovis.tossling.protocol.Endpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class RefusalTest {

    private val requests = CopyOnWriteArrayList<String>()

    private fun server(status: String, body: String = ""): ServerSocket {
        val socket = ServerSocket(0)
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                runCatching {
                    socket.accept().use { client ->
                        val input = client.getInputStream().bufferedReader()
                        val line = input.readLine().orEmpty()
                        var length = 0
                        while (true) {
                            val header = input.readLine().orEmpty()
                            if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(":").trim().toInt()
                        }
                        val sent = CharArray(length).also { input.read(it) }
                        requests.add(if (length > 0) "$line ${String(sent)}" else line)
                        client.getOutputStream().apply {
                            write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
                            flush()
                        }
                    }
                }
            }
        }
        return socket
    }

    @Test
    fun newTokensAreAskedForWithoutAnExpiry() = runBlocking {
        val socket = server(status = "200 OK", body = """{"token":"tk_new"}""")
        val token = NtfyClient().createToken(endpoint = Endpoint(server = "http://127.0.0.1:${socket.localPort}", token = "tk_room"), label = "tossling")
        socket.close()
        assertEquals("tk_new", token)
        assertEquals("""POST /v1/account/token HTTP/1.1 {"label":"tossling","expires":0}""", requests.single())
    }

    @Test
    fun aRefusedTokenIsNotSentAgainUntilThePauseEnds() = runBlocking {
        val socket = server(status = "401 Unauthorized", body = """{"code":40101,"http":401,"error":"unauthorized"}""")
        val client = NtfyClient()
        val endpoint = Endpoint(server = "http://127.0.0.1:${socket.localPort}", token = "tk_gone")
        val first = runCatching { client.poll(endpoint = endpoint, topic = "tossling-room", since = "all") }.exceptionOrNull() as NtfyException
        val second = runCatching { client.publish(endpoint = endpoint, topic = "tossling-room", message = "hi", body = null) }.exceptionOrNull() as NtfyException
        val third = runCatching { client.subscriptions(endpoint = endpoint) }.exceptionOrNull() as NtfyException
        socket.close()
        assertEquals(1, requests.size)
        assertEquals(listOf(401, 401, 401), listOf(first.code, second.code, third.code))
        assertEquals(listOf(false, true, true), listOf(first.isHeld, second.isHeld, third.isHeld))
        assertEquals(ServerRefusal.TOKEN, client.gate.refusal(endpoint = endpoint)?.kind)
        assertTrue(client.gate.pauseLeft(endpoint = endpoint) > 50_000L)
    }

    @Test
    fun healthChecksGoThroughWhileTheTokenIsHeldBack() = runBlocking {
        val socket = server(status = "401 Unauthorized")
        val client = NtfyClient()
        val endpoint = Endpoint(server = "http://127.0.0.1:${socket.localPort}", token = "tk_gone")
        runCatching { client.poll(endpoint = endpoint, topic = "tossling-room", since = "all") }
        client.isReachable(endpoint = endpoint)
        socket.close()
        assertEquals(2, requests.size)
    }
}
