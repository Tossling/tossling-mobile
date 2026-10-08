package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.crypto.ClipCipher
import com.kopylovis.tossling.protocol.crypto.DeviceIdentity
import com.kopylovis.tossling.protocol.crypto.RoomKeys
import com.kopylovis.tossling.protocol.crypto.RoomSecret
import com.kopylovis.tossling.protocol.crypto.X25519
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SharedVectorsTest {

    private val root = Json.parseToJsonElement(VECTORS_JSON).jsonObject

    private fun vectors(name: String): List<Map<String, String>> =
        root.getValue(name).jsonArray.map { element -> element.jsonObject.mapValues { it.value.jsonPrimitive.content } }

    private fun Map<String, String>.bytes(name: String): ByteArray = Base64.decode(getValue(name))

    @Test
    fun envelope() {
        vectors("envelope").forEach { v ->
            val cipher = ClipCipher.fromBase64(key = v.getValue("key"))
            assertEquals(v.getValue("sealed"), Base64.encode(cipher.seal(plain = v.getValue("plain").encodeToByteArray(), nonce = v.bytes("nonce"))))
            assertEquals(v.getValue("plain"), cipher.openText(text = v.getValue("sealed")).decodeToString())
        }
    }

    @Test
    fun invite() {
        vectors("invite").forEach { v ->
            val code = v.getValue("code")
            assertEquals(v.getValue("normalized"), Invites.normalized(code = code))
            assertEquals(listOf(v.getValue("topic"), v.getValue("legacy_topic")), Invites.topics(code = code))
            assertEquals(v.getValue("key"), Base64.encode(Invites.key(code = code)))
            assertEquals(v.getValue("sealed"), Base64.encode(ClipCipher(v.bytes("key")).seal(plain = v.getValue("plain").encodeToByteArray(), nonce = v.bytes("nonce"))))
            assertEquals(SyncJson.decodeFromString(Invite.serializer(), v.getValue("plain")), Invites.open(message = v.getValue("sealed"), key = v.bytes("key")))
        }
    }

    @Test
    fun rekey() {
        vectors("rekey").forEach { v ->
            val recipient = DeviceIdentity(privateKey = v.bytes("recipient_private"))
            val secret = RoomSecret(key = v.getValue("key"), room = v.getValue("room"))
            val plain = """{"key":"${secret.key}","r":"${secret.room}"}""".encodeToByteArray()
            assertEquals(v.getValue("box"), RoomKeys.sealFor(ephemeral = v.bytes("ephemeral_private"), recipient = recipient.publicKey, id = v.getValue("id"), plain = plain, nonce = v.bytes("nonce")))
            assertEquals(v.getValue("ephemeral"), Base64.encode(X25519.publicKey(privateKey = v.bytes("ephemeral_private"))))
            assertEquals(secret, RoomKeys.open(identity = recipient, id = v.getValue("id"), ephemeral = v.getValue("ephemeral"), box = v.getValue("box")))
            assertNull(RoomKeys.open(identity = recipient, id = "someone-else", ephemeral = v.getValue("ephemeral"), box = v.getValue("box")))
        }
    }

    @Test
    fun tsy2() {
        vectors("tsy2").forEach { v ->
            val cipher = ClipCipher(v.bytes("key"))
            val plain = v.bytes("plain")
            val sealed = Buffer()
            cipher.sealStream(source = Buffer().apply { write(plain) }, sink = sealed, size = plain.size.toLong(), chunkSize = v.getValue("chunk").toInt(), prefix = v.bytes("prefix")) {}
            assertContentEquals(v.bytes("sealed"), sealed.readByteArray())
            val opened = Buffer()
            cipher.openStream(source = Buffer().apply { write(v.bytes("sealed")) }, sink = opened) {}
            assertContentEquals(plain, opened.readByteArray())
        }
    }

    @Test
    fun tsy2RejectsADroppedLastChunk() {
        val v = vectors("tsy2").first()
        val sealed = v.bytes("sealed")
        assertFailsWith<Exception> {
            ClipCipher(v.bytes("key")).openStream(source = Buffer().apply { write(sealed.copyOf(sealed.size - 16)) }, sink = Buffer()) {}
        }
    }

    @Test
    fun device() {
        vectors("device").forEach { v -> assertEquals(v.getValue("device_id"), deviceIdFrom(androidId = v.getValue("android_id"))) }
    }

    @Test
    fun rfc7748() {
        val alice = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bob = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        assertContentEquals(hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"), X25519.publicKey(privateKey = alice))
        assertContentEquals(hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"), X25519.agree(privateKey = alice, publicKey = X25519.publicKey(privateKey = bob)))
    }

    @Test
    fun serverAddresses() {
        assertEquals("https://tossling.example.com", normalizedServer(" https://Tossling.Example.com:443/ "))
        assertEquals("http://10.0.2.2:8090", normalizedServer("http://10.0.2.2:8090"))
        assertNull(normalizedServer("tossling.example.com"))
    }

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
