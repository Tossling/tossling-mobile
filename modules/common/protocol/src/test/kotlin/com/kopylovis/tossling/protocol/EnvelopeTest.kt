package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.crypto.ClipCipher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class EnvelopeTest {

    private val vectors = Json.parseToJsonElement(javaClass.classLoader!!.getResource("vectors.json")!!.readText()).jsonObject
        .getValue("envelope").jsonArray.map { element -> element.jsonObject.mapValues { it.value.jsonPrimitive.content } }

    @Test
    fun matchesSharedVectors() {
        vectors.forEach { v ->
            val cipher = ClipCipher.fromBase64(key = v.getValue("key"))
            val plain = v.getValue("plain").toByteArray()
            assertEquals(v.getValue("sealed"), Base64.getEncoder().encodeToString(cipher.seal(plain = plain, nonce = Base64.getDecoder().decode(v.getValue("nonce")))))
            assertEquals(v.getValue("plain"), cipher.openText(text = v.getValue("sealed")).decodeToString())
        }
    }

    @Test
    fun readsTheMetaOfEveryKind() {
        val metas = vectors.map { v -> SyncJson.decodeFromString(ClipMeta.serializer(), v.getValue("plain")) }
        assertEquals(listOf(ClipMeta.TEXT, ClipMeta.FILE, ClipMeta.HELLO), metas.map { it.kind })
        assertEquals("Hello, Tossling", metas[0].text)
        assertEquals("mac", metas[0].source)
        assertEquals("report.pdf", metas[1].fileName)
        assertEquals(ClipCipher.STREAM_FORMAT, metas[1].format)
        assertEquals(1_048_577L, metas[1].size)
        assertEquals(listOf("a1b2c3d4e5f60718"), metas[1].to)
        assertEquals(true, metas[2].renewed)
        assertEquals("hSDwCYkwp1R0i33ctD73Wg2/Og0mOBr066SpjqqbTmo=", metas[2].publicKey)
    }
}
