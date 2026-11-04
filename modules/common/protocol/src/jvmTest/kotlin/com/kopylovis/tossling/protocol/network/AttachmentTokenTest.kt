package com.kopylovis.tossling.protocol.network

import com.kopylovis.tossling.protocol.Endpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class AttachmentTokenTest {

    private val authorizations = CopyOnWriteArrayList<String>()

    private fun server(status: String): ServerSocket {
        val socket = ServerSocket(0)
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                runCatching {
                    socket.accept().use { client ->
                        val input = client.getInputStream().bufferedReader()
                        input.readLine()
                        var authorization = "none"
                        while (true) {
                            val header = input.readLine().orEmpty()
                            if (header.isEmpty()) break
                            if (header.startsWith("Authorization:", ignoreCase = true)) authorization = header.substringAfter(":").trim()
                        }
                        authorizations += authorization
                        client.getOutputStream().write("HTTP/1.1 $status\r\nContent-Length: 4\r\nConnection: close\r\n\r\nfile".toByteArray())
                    }
                }
            }
        }
        return socket
    }

    @Test
    fun theTokenGoesOnlyToTheRoomsOwnServer() = runBlocking {
        val own = server("200 OK")
        val other = server("200 OK")
        val client = NtfyClient()
        val endpoint = Endpoint(server = "http://127.0.0.1:${own.localPort}", token = "tk_room")
        client.download(endpoint = endpoint, url = "http://127.0.0.1:${own.localPort}/file/a.bin")
        client.download(endpoint = endpoint, url = "http://127.0.0.1:${other.localPort}/file/b.bin")
        client.downloadTo(endpoint = endpoint, url = "http://127.0.0.1:${other.localPort}/file/c.bin") { it.readBytes() }
        assertEquals(listOf("Bearer tk_room", "none", "none"), authorizations.toList())
        own.close()
        other.close()
    }

    @Test
    fun aRefusalFromAnotherHostDoesNotHoldTheServerBack() = runBlocking {
        val own = server("200 OK")
        val other = server("401 Unauthorized")
        val client = NtfyClient()
        val endpoint = Endpoint(server = "http://127.0.0.1:${own.localPort}", token = "tk_room")
        runCatching { client.download(endpoint = endpoint, url = "http://127.0.0.1:${other.localPort}/file/a.bin") }
        assertEquals("file", client.download(endpoint = endpoint, url = "http://127.0.0.1:${own.localPort}/file/b.bin").decodeToString())
        own.close()
        other.close()
    }

    @Test
    fun sameOrigin() {
        assertTrue(NtfyClient.isSameOrigin(server = "https://Tossling.example.com", url = "https://tossling.example.com:443/file/a"))
        assertTrue(NtfyClient.isSameOrigin(server = "http://10.0.0.2:8090", url = "http://10.0.0.2:8090/file/a"))
        assertFalse(NtfyClient.isSameOrigin(server = "https://tossling.example.com", url = "http://tossling.example.com/file/a"))
        assertFalse(NtfyClient.isSameOrigin(server = "https://tossling.example.com", url = "https://tossling.example.com.evil.net/file/a"))
        assertFalse(NtfyClient.isSameOrigin(server = "https://tossling.example.com", url = "https://tossling.example.com:8443/file/a"))
        assertFalse(NtfyClient.isSameOrigin(server = "https://tossling.example.com", url = "not a url"))
    }
}
