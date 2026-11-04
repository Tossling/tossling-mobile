package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.crypto.ClipCipher
import com.kopylovis.tossling.protocol.network.NtfyEvent
import com.kopylovis.tossling.protocol.room.Incoming
import com.kopylovis.tossling.protocol.room.RoomConfig
import com.kopylovis.tossling.protocol.room.RoomCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SensitiveTest {

    @Test
    fun secretContentIsMarkedWithSens() {
        val meta = ClipMeta(kind = ClipMeta.TEXT, text = "hunter2", sensitive = true)
        val json = SyncJson.encodeToString(ClipMeta.serializer(), meta)
        assertTrue(json.contains("\"sens\":true"), json)
        assertEquals(true, SyncJson.decodeFromString(ClipMeta.serializer(), json).sensitive)
        val plain = SyncJson.encodeToString(ClipMeta.serializer(), ClipMeta(kind = ClipMeta.TEXT, text = "hello"))
        assertTrue(!plain.contains("sens"), plain)
    }

    @Test
    fun aReceiverSeesTheMark() {
        val key = ClipCipher.randomKey()
        val now = 1_800_000_000_000L
        val core = RoomCore(RoomConfig(server = "https://s", token = "t", room = "tossling-r", key = key, deviceId = "phone", deviceName = "iPhone"))
        val sealed = ClipCipher.fromBase64(key = key).sealToText(
            plain = """{"k":"text","m":"text/plain","src":"mac","n":"Studio","id":"mac","v":"hunter2","ts":$now,"mid":"0011223344556677","sens":true}""".encodeToByteArray(),
        )
        val content = assertIs<Incoming.Content>(core.open(NtfyEvent(id = "e1", time = now / 1000, event = "message", topic = "tossling-r", message = sealed), nowMs = now))
        assertEquals(true, content.meta.sensitive)
        assertNull(ClipMeta(kind = ClipMeta.TEXT).sensitive)
    }
}
