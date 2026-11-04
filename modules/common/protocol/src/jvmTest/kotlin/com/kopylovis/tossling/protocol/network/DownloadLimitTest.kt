package com.kopylovis.tossling.protocol.network

import com.kopylovis.tossling.protocol.AttachmentTooLarge
import com.kopylovis.tossling.protocol.Endpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class DownloadLimitTest {

    private fun server(body: ByteArray, withLength: Boolean): ServerSocket {
        val socket = ServerSocket(0)
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                runCatching {
                    socket.accept().use { client ->
                        val input = client.getInputStream().bufferedReader()
                        while (input.readLine().orEmpty().isNotEmpty()) Unit
                        val length = if (withLength) "Content-Length: ${body.size}\r\n" else ""
                        client.getOutputStream().apply { write("HTTP/1.1 200 OK\r\n${length}Connection: close\r\n\r\n".toByteArray()); write(body) }
                    }
                }
            }
        }
        return socket
    }

    @Test
    fun anAttachmentOverTheLimitIsRefused() = runBlocking {
        for (withLength in listOf(true, false)) {
            val socket = server(body = ByteArray(100_000), withLength = withLength)
            val endpoint = Endpoint(server = "http://127.0.0.1:${socket.localPort}", token = "tk_room")
            val url = "${endpoint.server}/file/a.bin"
            val error = runCatching { NtfyClient().download(endpoint = endpoint, url = url, maxBytes = 50_000) }.exceptionOrNull()
            assertTrue("with Content-Length $withLength: $error", error is AttachmentTooLarge)
            assertEquals(100_000, NtfyClient().download(endpoint = endpoint, url = url, maxBytes = 100_000).size)
            socket.close()
        }
    }
}
