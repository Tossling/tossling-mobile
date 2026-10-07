package com.kopylovis.tossling.protocol.network

import com.kopylovis.tossling.protocol.Endpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class StreamTest {

    private fun server(lines: List<String>): ServerSocket {
        val socket = ServerSocket(0)
        thread(isDaemon = true) {
            runCatching {
                socket.accept().use { client ->
                    val input = client.getInputStream().bufferedReader()
                    while (input.readLine().orEmpty().isNotEmpty()) Unit
                    val out = client.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/x-ndjson\r\n\r\n".toByteArray())
                    lines.forEach { line -> out.write("$line\n".toByteArray()) }
                    out.flush()
                    Thread.sleep(60_000)
                }
            }
        }
        return socket
    }

    @Test
    fun deliversEventsAsTheyCome() = runBlocking {
        val socket = server(lines = listOf("""{"id":"a","event":"open","topic":"t"}""", """{"id":"b","event":"message","topic":"t","message":"hi"}"""))
        val events = CopyOnWriteArrayList<NtfyEvent>()
        val job = launch(Dispatchers.IO) {
            runCatching { NtfyClient().stream(endpoint = Endpoint(server = "http://127.0.0.1:${socket.localPort}"), topics = listOf("t"), onOpen = {}, onEvent = { events.add(it) }) }
        }
        withTimeout(5_000) { while (events.size < 2) delay(20) }
        job.cancelAndJoin()
        socket.close()
        assertEquals(listOf("open", "message"), events.map { it.event })
    }

    @Test
    fun cancellingStopsAHangingStreamAtOnce() = runBlocking {
        val socket = server(lines = emptyList())
        var opened = false
        val job = launch(Dispatchers.IO) {
            runCatching { NtfyClient().stream(endpoint = Endpoint(server = "http://127.0.0.1:${socket.localPort}"), topics = listOf("t"), onOpen = { opened = true }, onEvent = {}) }
        }
        withTimeout(5_000) { while (!opened) delay(20) }
        val started = System.currentTimeMillis()
        withTimeout(3_000) { job.cancelAndJoin() }
        socket.close()
        assertTrue(System.currentTimeMillis() - started < 3_000)
    }
}
