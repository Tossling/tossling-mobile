package com.kopylovis.tossling.sync.network

import com.kopylovis.tossling.sync.data.Endpoint
import com.kopylovis.tossling.sync.data.SyncJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
internal data class NtfyEvent(
    val id: String = "",
    val time: Long = 0,
    val event: String = "",
    val topic: String = "",
    val title: String? = null,
    val message: String? = null,
    val priority: Int? = null,
    val tags: List<String>? = null,
    val click: String? = null,
    val icon: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    val attachment: NtfyAttachment? = null,
)

@Serializable
internal data class NtfyAttachment(
    val url: String = "",
    val type: String? = null,
    val size: Long? = null,
)

@Serializable
internal data class NtfyToken(
    val token: String = "",
)

@Serializable
internal data class NtfySubscription(
    @SerialName("base_url") val baseUrl: String = "",
    val topic: String = "",
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
internal data class NtfyAccount(
    val subscriptions: List<NtfySubscription> = emptyList(),
)

@Serializable
internal data class NtfyTokenRequest(
    val label: String,
)

@Serializable
internal data class TossyHealth(
    val server: String = "",
    val push: Boolean? = null,
)

@Serializable
internal data class TossyProjectRequest(
    val topic: String,
    val name: String,
)

@Serializable
internal data class TossyProject(
    val topic: String = "",
    val name: String = "",
    val publisher: String? = null,
    val token: String? = null,
    val example: String? = null,
)

class NtfyException(val code: Int, message: String) : Exception(message)

internal class NtfyClient {

    suspend fun publish(endpoint: Endpoint, topic: String, message: String, body: ByteArray?) = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/$topic")
        connection.requestMethod = if (body == null) "POST" else "PUT"
        connection.doOutput = true
        connection.setRequestProperty("X-Priority", "4")
        val payload = if (body == null) {
            message.toByteArray()
        } else {
            connection.setRequestProperty("X-Message", message)
            connection.setRequestProperty("X-Filename", "clip.bin")
            body
        }
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }
        connection.ensureOk()
        connection.disconnect()
    }

    suspend fun publishStream(endpoint: Endpoint, topic: String, message: String, length: Long, write: (OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/$topic")
        connection.requestMethod = "PUT"
        connection.doOutput = true
        connection.readTimeout = STREAM_TIMEOUT_MS
        connection.setRequestProperty("X-Priority", "4")
        connection.setRequestProperty("X-Message", message)
        connection.setRequestProperty("X-Filename", "clip.bin")
        connection.setFixedLengthStreamingMode(length)
        try {
            connection.outputStream.use(write)
            connection.ensureOk()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadTo(endpoint: Endpoint, url: String, read: (InputStream) -> Unit) = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = url)
        connection.readTimeout = STREAM_TIMEOUT_MS
        try {
            connection.ensureOk()
            connection.inputStream.buffered(STREAM_BUFFER).use(read)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun poll(endpoint: Endpoint, topic: String, since: String): List<NtfyEvent> = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(since, Charsets.UTF_8.name())
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/$topic/json?poll=1&since=$query")
        connection.ensureOk()
        val lines = connection.inputStream.bufferedReader().use { it.readLines() }
        connection.disconnect()
        lines.filter { it.isNotBlank() }.map { SyncJson.decodeFromString(NtfyEvent.serializer(), it) }
    }

    suspend fun download(endpoint: Endpoint, url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = url)
        connection.readTimeout = DOWNLOAD_TIMEOUT_MS
        connection.ensureOk()
        val bytes = connection.inputStream.use { it.readBytes() }
        connection.disconnect()
        bytes
    }

    suspend fun createToken(endpoint: Endpoint, label: String): String = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/v1/account/token")
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        val body = SyncJson.encodeToString(NtfyTokenRequest.serializer(), NtfyTokenRequest(label = label)).toByteArray()
        connection.setFixedLengthStreamingMode(body.size)
        try {
            connection.outputStream.use { it.write(body) }
            connection.ensureOk()
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            SyncJson.decodeFromString(NtfyToken.serializer(), text).token.takeIf { it.isNotEmpty() } ?: throw NtfyException(code = 0, message = "no token in the answer")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun deleteToken(endpoint: Endpoint, token: String) = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/v1/account/token")
        connection.requestMethod = "DELETE"
        connection.setRequestProperty("X-Token", token)
        try {
            connection.ensureOk()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun subscriptions(endpoint: Endpoint): List<NtfySubscription> = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/v1/account")
        try {
            connection.ensureOk()
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            SyncJson.decodeFromString(NtfyAccount.serializer(), text).subscriptions
        } finally {
            connection.disconnect()
        }
    }

    suspend fun saveSubscription(endpoint: Endpoint, topic: String, name: String, isNew: Boolean) = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/v1/account/subscription")
        connection.requestMethod = if (isNew) "POST" else "PATCH"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        val body = SyncJson.encodeToString(NtfySubscription.serializer(), NtfySubscription(baseUrl = endpoint.server, topic = topic, displayName = name)).toByteArray()
        connection.setFixedLengthStreamingMode(body.size)
        try {
            connection.outputStream.use { it.write(body) }
            connection.ensureOk()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun removeSubscription(endpoint: Endpoint, topic: String) = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/v1/account/subscription")
        connection.requestMethod = "DELETE"
        connection.setRequestProperty("X-BaseURL", endpoint.server)
        connection.setRequestProperty("X-Topic", topic)
        try {
            connection.ensureOk()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun stream(endpoint: Endpoint, topics: List<String>, onOpen: () -> Unit, onEvent: (NtfyEvent) -> Unit) = coroutineScope {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/${topics.joinToString(separator = ",")}/json")
        connection.readTimeout = LIVE_TIMEOUT_MS
        val closer = launch(Dispatchers.IO) {
            try {
                awaitCancellation()
            } finally {
                connection.disconnect()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                connection.ensureOk()
                onOpen()
                connection.inputStream.bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() }.forEach { line -> onEvent(SyncJson.decodeFromString(NtfyEvent.serializer(), line)) }
                }
            }
        } finally {
            closer.cancel()
            connection.disconnect()
        }
    }

    suspend fun tossyHealth(endpoint: Endpoint): TossyHealth? = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/v1/tossy/health")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            SyncJson.decodeFromString(TossyHealth.serializer(), connection.inputStream.bufferedReader().use { it.readText() })
                .takeIf { it.server == TOSSY_SERVER }
        } catch (error: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    suspend fun isTossyServer(endpoint: Endpoint): Boolean = tossyHealth(endpoint = endpoint) != null

    suspend fun isReachable(endpoint: Endpoint): Boolean = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/v1/health")
        try {
            connection.responseCode in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_INTERNAL_ERROR
        } catch (error: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    suspend fun createProject(endpoint: Endpoint, topic: String, name: String): TossyProject = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/v1/tossy/projects")
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        val body = SyncJson.encodeToString(TossyProjectRequest.serializer(), TossyProjectRequest(topic = topic, name = name)).toByteArray()
        connection.setFixedLengthStreamingMode(body.size)
        try {
            connection.outputStream.use { it.write(body) }
            connection.ensureOk()
            SyncJson.decodeFromString(TossyProject.serializer(), connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    suspend fun deleteProject(endpoint: Endpoint, topic: String) = withContext(Dispatchers.IO) {
        val connection = open(endpoint = endpoint, url = "${endpoint.server}/v1/tossy/projects/$topic")
        connection.requestMethod = "DELETE"
        try {
            connection.ensureOk()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun check(endpoint: Endpoint, topic: String) {
        poll(endpoint = endpoint, topic = topic, since = "1s")
    }

    private fun open(endpoint: Endpoint, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            if (endpoint.token.isNotEmpty()) setRequestProperty("Authorization", "Bearer ${endpoint.token}")
        }

    private fun HttpURLConnection.ensureOk() {
        val code = responseCode
        if (code in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE) return
        val text = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().trim()
        disconnect()
        throw NtfyException(code = code, message = "HTTP $code $text".trim())
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val DOWNLOAD_TIMEOUT_MS = 120_000
        private const val STREAM_TIMEOUT_MS = 300_000
        private const val LIVE_TIMEOUT_MS = 90_000
        private const val TOSSY_SERVER = "tossy-server"
        private const val STREAM_BUFFER = 1 shl 16
    }
}
