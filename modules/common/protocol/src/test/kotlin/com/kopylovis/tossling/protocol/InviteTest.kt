package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.crypto.ClipCipher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class InviteTest {

    private val vectors = Json.parseToJsonElement(javaClass.classLoader!!.getResource("vectors.json")!!.readText()).jsonObject
        .getValue("invite").jsonArray.map { element -> element.jsonObject.mapValues { it.value.jsonPrimitive.content } }

    @Test
    fun matchesSharedVectors() {
        vectors.forEach { v ->
            val code = v.getValue("code")
            val key = Base64.getDecoder().decode(v.getValue("key"))
            assertEquals(v.getValue("normalized"), Invites.normalized(code = code))
            assertEquals(listOf(v.getValue("topic"), v.getValue("legacy_topic")), Invites.topics(code = code))
            assertEquals(v.getValue("key"), Base64.getEncoder().encodeToString(Invites.key(code = code)))
            val plain = v.getValue("plain").toByteArray()
            assertEquals(v.getValue("sealed"), Base64.getEncoder().encodeToString(ClipCipher(key).seal(plain = plain, nonce = Base64.getDecoder().decode(v.getValue("nonce")))))
            assertEquals(SyncJson.decodeFromString(Invite.serializer(), v.getValue("plain")), Invites.open(message = v.getValue("sealed"), key = key))
        }
    }

    @Test
    fun readsWhatTheMacSends() {
        val invite = Invites.open(message = vectors.first().getValue("sealed"), key = Base64.getDecoder().decode(vectors.first().getValue("key")))!!
        assertEquals("https://tossling.example.com", invite.server)
        assertEquals("tossling-0123456789abcdef01234567", invite.room)
        assertEquals("Studio", invite.name)
        assertEquals("a1b2c3d4e5f60718", invite.owner)
        assertTrue(invite.isExpired(now = 1_791_360_000_000))
        assertFalse(invite.isExpired(now = 1_791_359_999_000))
    }

    @Test
    fun anotherCodeDoesNotOpenIt() {
        assertNull(Invites.open(message = vectors.first().getValue("sealed"), key = Invites.key(code = "AAAA-BBBB")))
    }

    @Test
    fun roundTripsWithoutAnOwner() {
        val key = Invites.key(code = "7KQ2-M9XD")
        val invite = Invite(server = "https://s", token = "t", room = "tossling-r", key = "k", name = "Laptop", expires = 1.5)
        val sealed = Invites.seal(invite = invite, key = key)
        assertFalse(ClipCipher(key).openText(text = sealed).decodeToString().contains("\"o\""))
        assertEquals(invite, Invites.open(message = sealed, key = key))
    }

    @Test
    fun newCodesUseTheAlphabetInTwoHalves() {
        repeat(times = 50) {
            val code = Invites.newCode()
            assertTrue(code, Regex("^[${Invites.ALPHABET}]{4}-[${Invites.ALPHABET}]{4}$").matches(code))
        }
        assertEquals("7KQ2M9XD", Invites.normalized(code = " 7kq2-m9xd\n"))
    }
}
