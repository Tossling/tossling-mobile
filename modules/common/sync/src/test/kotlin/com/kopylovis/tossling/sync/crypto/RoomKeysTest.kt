package com.kopylovis.tossling.sync.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

class RoomKeysTest {

    @Test
    fun matchesRfc7748() {
        val alice = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bob = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        assertArrayEquals(hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"), X25519.publicKey(privateKey = alice))
        assertArrayEquals(hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"), X25519.publicKey(privateKey = bob))
        assertArrayEquals(hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"), X25519.agree(privateKey = alice, publicKey = X25519.publicKey(privateKey = bob)))
    }

    @Test
    fun opensOnlyForItsRecipient() {
        val phone = DeviceIdentity.generate()
        val mac = DeviceIdentity.generate()
        val removed = DeviceIdentity.generate()
        val secret = RoomSecret(room = "tossy-abc", key = "c2VjcmV0")
        val sealed = RoomKeys.seal(secret = secret, recipients = mapOf("phone" to phone.publicText, "mac" to mac.publicText))
        assertEquals(secret, RoomKeys.open(identity = phone, id = "phone", ephemeral = sealed.ephemeral, box = sealed.keys.getValue("phone")))
        assertEquals(secret, RoomKeys.open(identity = mac, id = "mac", ephemeral = sealed.ephemeral, box = sealed.keys.getValue("mac")))
        assertNull(RoomKeys.open(identity = removed, id = "phone", ephemeral = sealed.ephemeral, box = sealed.keys.getValue("phone")))
        assertNull(RoomKeys.open(identity = phone, id = "mac", ephemeral = sealed.ephemeral, box = sealed.keys.getValue("phone")))
    }

    @Test
    fun skipsBrokenRecipientKeys() {
        val sealed = RoomKeys.seal(secret = RoomSecret(room = "r", key = "k"), recipients = mapOf("bad" to "nope", "short" to "AAAA"))
        assertEquals(emptyMap<String, String>(), sealed.keys)
    }

    @Test
    fun matchesSharedVectors() {
        val text = javaClass.classLoader!!.getResource("vectors.json")!!.readText()
        val vectors = Json.parseToJsonElement(text).jsonObject.getValue("rekey").jsonArray
        vectors.forEach { element ->
            val v = element.jsonObject.mapValues { it.value.jsonPrimitive.content }
            val decode = { name: String -> Base64.getDecoder().decode(v.getValue(name)) }
            val recipient = DeviceIdentity(privateKey = decode("recipient_private"))
            val secret = RoomSecret(key = v.getValue("key"), room = v.getValue("room"))
            val plain = """{"key":"${secret.key}","r":"${secret.room}"}""".toByteArray()
            val box = RoomKeys.sealFor(ephemeral = decode("ephemeral_private"), recipient = recipient.publicKey, id = v.getValue("id"), plain = plain, nonce = decode("nonce"))
            assertEquals(v.getValue("box"), box)
            assertEquals(v.getValue("ephemeral"), Base64.getEncoder().encodeToString(X25519.publicKey(privateKey = decode("ephemeral_private"))))
            assertEquals(secret, RoomKeys.open(identity = recipient, id = v.getValue("id"), ephemeral = v.getValue("ephemeral"), box = v.getValue("box")))
        }
    }

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
