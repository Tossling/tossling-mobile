package com.kopylovis.tossling.protocol.network

import com.kopylovis.tossling.protocol.Endpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ServerRefusal { TOKEN, BUSY }

data class Refusal(
    val kind: ServerRefusal,
    val failures: Int,
    val until: Long,
)

fun serverRefusal(code: Int, isAccount: Boolean = false): ServerRefusal? =
    when {
        code == HTTP_UNAUTHORIZED -> ServerRefusal.TOKEN
        code == HTTP_FORBIDDEN && isAccount -> ServerRefusal.TOKEN
        code == HTTP_TOO_MANY_REQUESTS -> ServerRefusal.BUSY
        else -> null
    }

fun refusalPauseMs(failures: Int): Long =
    (FIRST_PAUSE_MS shl (failures - 1).coerceIn(0, MAX_DOUBLINGS)).coerceAtMost(LONGEST_PAUSE_MS)

class RefusalGate(private val now: () -> Long) {

    private val tokenRefusals = MutableStateFlow<Map<Endpoint, Refusal>>(emptyMap())
    private val serverRefusals = MutableStateFlow<Map<String, Refusal>>(emptyMap())

    val tokens: StateFlow<Map<Endpoint, Refusal>> = tokenRefusals.asStateFlow()
    val servers: StateFlow<Map<String, Refusal>> = serverRefusals.asStateFlow()

    fun refusal(endpoint: Endpoint): Refusal? = tokenRefusals.value[endpoint] ?: serverRefusals.value[endpoint.server]

    fun held(endpoint: Endpoint): Refusal? = refusal(endpoint)?.takeIf { now() < it.until }

    fun pauseLeft(endpoint: Endpoint): Long = held(endpoint)?.let { it.until - now() } ?: 0L

    fun refused(endpoint: Endpoint, kind: ServerRefusal) {
        when (kind) {
            ServerRefusal.TOKEN -> tokenRefusals.update { map -> map + (endpoint to next(previous = map[endpoint], kind = kind)) }
            ServerRefusal.BUSY -> serverRefusals.update { map -> map + (endpoint.server to next(previous = map[endpoint.server], kind = kind)) }
        }
    }

    fun passed(endpoint: Endpoint) {
        if (endpoint in tokenRefusals.value) tokenRefusals.update { it - endpoint }
        if (endpoint.server in serverRefusals.value) serverRefusals.update { it - endpoint.server }
    }

    private fun next(previous: Refusal?, kind: ServerRefusal): Refusal {
        val time = now()
        if (previous != null && time < previous.until) return previous
        val failures = (previous?.failures ?: 0) + 1
        return Refusal(kind = kind, failures = failures, until = time + refusalPauseMs(failures = failures))
    }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val FIRST_PAUSE_MS = 60_000L
private const val LONGEST_PAUSE_MS = 30 * 60_000L
private const val MAX_DOUBLINGS = 5
