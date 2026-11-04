package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.crypto.Primitives
import com.kopylovis.tossling.protocol.crypto.hex
import kotlin.time.Clock

/**
 * The server, or anyone with a token, can publish an old envelope again. Every message carries when it was sealed
 * (`ts`) and an id of its own (`mid`), so a receiver can tell a replay from a new message.
 */
object Freshness {

    /** Content older than this is not put on the clipboard; a `re` hello older than this does not change a key. */
    const val STALE_MS = 15 * 60_000L

    /** A file is still fetched for this long, then it is gone from the server. */
    const val FILE_STALE_MS = 3 * 60 * 60_000L

    /** A `ts` further ahead than this counts as stale. */
    const val FUTURE_MS = 5 * 60_000L

    /**
     * The age of a message: the larger of now minus the event's time (ntfy, in seconds) and now minus `ts`, so a
     * replayed envelope stays old. A `ts` more than [FUTURE_MS] ahead gives [Long.MAX_VALUE]. Without `ts` (an older
     * sender) the event's time alone decides.
     */
    fun ageMs(eventTime: Long, sentAt: Long?, nowMs: Long): Long {
        val byEvent = nowMs - eventTime * 1000
        if (sentAt == null) return byEvent
        if (sentAt - nowMs > FUTURE_MS) return Long.MAX_VALUE
        return maxOf(byEvent, nowMs - sentAt)
    }

    fun isFresh(eventTime: Long, sentAt: Long?, nowMs: Long, limitMs: Long = STALE_MS): Boolean = ageMs(eventTime = eventTime, sentAt = sentAt, nowMs = nowMs) < limitMs
}

/** A new `mid`: 8 random bytes in lowercase hex. */
fun newMessageId(): String = Primitives.random(8).hex()

/** The meta as it goes out: sealed now, with an id of its own. */
fun ClipMeta.stamped(nowMs: Long = Clock.System.now().toEpochMilliseconds()): ClipMeta = copy(sentAt = nowMs, messageId = newMessageId())

/**
 * The `mid`s a device handled in the last [WINDOW_MS], at most [LIMIT] of them, oldest dropped first. Saved as text next
 * to the last event id.
 */
class HandledMessages(entries: Map<String, Long> = emptyMap()) {

    private val entries = LinkedHashMap<String, Long>(entries)

    val size: Int get() = entries.size

    fun contains(mid: String, nowMs: Long): Boolean = entries[mid]?.let { nowMs - it < WINDOW_MS } == true

    fun add(mid: String, nowMs: Long) {
        if (!isValid(mid)) return
        entries.remove(mid)
        entries[mid] = nowMs
        val expired = entries.filterValues { nowMs - it >= WINDOW_MS }.keys
        expired.forEach(entries::remove)
        while (entries.size > LIMIT) entries.remove(entries.keys.first())
    }

    fun remove(mid: String) {
        entries.remove(mid)
    }

    fun encode(): String = entries.entries.joinToString(separator = ",") { "${it.key}:${it.value}" }

    companion object {
        const val WINDOW_MS = 3 * 60 * 60_000L
        const val LIMIT = 1_000

        // A mid is 16 hex characters; anything that could break the saved text is not kept.
        private fun isValid(mid: String): Boolean = mid.length in 1..64 && mid.all { it.isLetterOrDigit() || it == '-' || it == '_' }

        fun decode(text: String): HandledMessages = HandledMessages(
            text.split(',').mapNotNull { entry ->
                val mid = entry.substringBefore(':', missingDelimiterValue = "")
                val time = entry.substringAfter(':', missingDelimiterValue = "").toLongOrNull()
                if (mid.isEmpty() || time == null) null else mid to time
            }.toMap(),
        )
    }
}
