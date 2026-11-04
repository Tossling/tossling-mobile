package com.kopylovis.tossling.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplaysTest {

    private val now = 1_800_000_000_000L

    @Test
    fun theAgeIsTheLargerOfTheEventTimeAndTs() {
        assertEquals(60_000L, Freshness.ageMs(eventTime = now / 1000 - 60, sentAt = null, nowMs = now))
        assertEquals(20 * 60_000L, Freshness.ageMs(eventTime = now / 1000 - 60, sentAt = now - 20 * 60_000, nowMs = now))
        assertEquals(60_000L, Freshness.ageMs(eventTime = now / 1000 - 60, sentAt = now - 1_000, nowMs = now))
        assertEquals(60_000L, Freshness.ageMs(eventTime = now / 1000 - 60, sentAt = now + Freshness.FUTURE_MS, nowMs = now))
        assertEquals(Long.MAX_VALUE, Freshness.ageMs(eventTime = now / 1000, sentAt = now + Freshness.FUTURE_MS + 1, nowMs = now))
        assertTrue(Freshness.isFresh(eventTime = now / 1000, sentAt = now - Freshness.STALE_MS + 1, nowMs = now))
        assertFalse(Freshness.isFresh(eventTime = now / 1000, sentAt = now - Freshness.STALE_MS, nowMs = now))
    }

    @Test
    fun handledIdsLastThreeHoursAndSurviveASave() {
        val handled = HandledMessages()
        handled.add(mid = "0011223344556677", nowMs = now)
        assertTrue(handled.contains(mid = "0011223344556677", nowMs = now + HandledMessages.WINDOW_MS - 1))
        assertFalse(handled.contains(mid = "0011223344556677", nowMs = now + HandledMessages.WINDOW_MS))
        assertFalse(handled.contains(mid = "8899aabbccddeeff", nowMs = now))
        val restored = HandledMessages.decode(text = handled.encode())
        assertTrue(restored.contains(mid = "0011223344556677", nowMs = now))
        restored.add(mid = "8899aabbccddeeff", nowMs = now + HandledMessages.WINDOW_MS)
        assertEquals(1, restored.size)
        restored.remove(mid = "8899aabbccddeeff")
        assertEquals(0, restored.size)
        assertEquals(0, HandledMessages.decode(text = "garbage,:5,a:b").size)
    }

    @Test
    fun handledIdsAreBounded() {
        val handled = HandledMessages()
        repeat(HandledMessages.LIMIT + 10) { handled.add(mid = "m$it", nowMs = now + it) }
        assertEquals(HandledMessages.LIMIT, handled.size)
        assertFalse(handled.contains(mid = "m0", nowMs = now + HandledMessages.LIMIT))
        assertTrue(handled.contains(mid = "m${HandledMessages.LIMIT + 9}", nowMs = now + HandledMessages.LIMIT))
        handled.add(mid = "a,b:c", nowMs = now)
        assertFalse(handled.contains(mid = "a,b:c", nowMs = now))
    }

    @Test
    fun stampedMetaGetsTheTimeAndAFreshId() {
        val meta = ClipMeta(kind = ClipMeta.TEXT)
        val a = meta.stamped(nowMs = now)
        val b = meta.stamped(nowMs = now)
        assertEquals(now, a.sentAt)
        assertTrue(a.messageId != b.messageId)
        assertEquals(16, a.messageId?.length)
    }
}
