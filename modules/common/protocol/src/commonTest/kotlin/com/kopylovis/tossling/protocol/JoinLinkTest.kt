package com.kopylovis.tossling.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JoinLinkTest {

    private val code = """{"id":"bb101dea281887e1","k":"qDKfjH+sx7Ro2YYguao3QuY4SPjE5H0lV0aI7AhoOyY=","n":"Demo computer","o":"bb101dea281887e1","pk":"x","r":"tossling-3367939294282173262a2ba1","s":"https://demo.example.com","t":"tk_x","v":2}"""

    @Test
    fun roundTrip() {
        val link = joinLink(code = code)
        assertEquals(code, joinLinkCode(link = link))
        assertEquals("Demo computer", pairingName(raw = code))
    }

    @Test
    fun paddedCodeIsAccepted() {
        val padded = "tossling://join?code=" + kotlin.io.encoding.Base64.UrlSafe.encode(code.encodeToByteArray())
        assertEquals(code, joinLinkCode(link = padded))
    }

    @Test
    fun foreignLinksAreRejected() {
        assertNull(joinLinkCode(link = "https://example.com/?code=abc"))
        assertNull(joinLinkCode(link = "tossling://join?code=bm90IGpzb24"))
        assertNull(joinLinkCode(link = "tossling://join"))
    }

    @Test
    fun demoAnswer() {
        val answer = """{"code":${kotlinx.serialization.json.JsonPrimitive(code)},"link":"tossling://join?code=x","expires":1}"""
        assertEquals(code, demoRoomCode(answer = answer))
        assertNull(demoRoomCode(answer = """{"error":"Too many demo rooms"}"""))
        assertNull(demoRoomCode(answer = "<html>"))
    }
}
