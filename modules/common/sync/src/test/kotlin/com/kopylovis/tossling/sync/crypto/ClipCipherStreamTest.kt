package com.kopylovis.tossling.sync.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Random

class ClipCipherStreamTest {

    private val cipher = ClipCipher(ByteArray(ClipCipher.KEY_SIZE) { it.toByte() })
    private val chunk = ClipCipher.CHUNK_SIZE

    @Test
    fun matchesSharedVectors() {
        val text = javaClass.classLoader!!.getResource("vectors.json")!!.readText()
        val vectors = Json.parseToJsonElement(text).jsonObject.getValue("tsy2").jsonArray
        val decode = Base64.getDecoder()
        vectors.forEach { element ->
            val v = element.jsonObject.mapValues { it.value.jsonPrimitive.content }
            val vectorCipher = ClipCipher(decode.decode(v.getValue("key")))
            val plain = decode.decode(v.getValue("plain"))
            val sealed = ByteArrayOutputStream()
            vectorCipher.sealStream(input = ByteArrayInputStream(plain), output = sealed, size = plain.size.toLong(), chunkSize = v.getValue("chunk").toInt(), prefix = decode.decode(v.getValue("prefix"))) {}
            assertArrayEquals("seal ${plain.size}", decode.decode(v.getValue("sealed")), sealed.toByteArray())
            val opened = ByteArrayOutputStream()
            vectorCipher.openStream(input = ByteArrayInputStream(decode.decode(v.getValue("sealed"))), output = opened) {}
            assertArrayEquals("open ${plain.size}", plain, opened.toByteArray())
        }
    }

    @Test
    fun roundTripsAcrossChunkBoundaries() {
        listOf(0, 1, chunk - 1, chunk, chunk + 1, 3 * chunk + 5).forEach { size ->
            val plain = randomBytes(size = size)
            val sealed = seal(plain = plain)
            assertEquals("sealed size for $size", ClipCipher.sealedSize(size.toLong()), sealed.size.toLong())
            assertArrayEquals("round trip for $size", plain, open(sealed = sealed))
        }
    }

    @Test
    fun reportsProgressUpToTheFullSize() {
        val plain = randomBytes(size = 2 * chunk + 10)
        val seen = mutableListOf<Long>()
        cipher.sealStream(input = ByteArrayInputStream(plain), output = ByteArrayOutputStream(), size = plain.size.toLong()) { seen += it }
        assertEquals(listOf(chunk.toLong(), 2L * chunk, plain.size.toLong()), seen)
    }

    @Test
    fun rejectsADroppedFinalChunk() {
        val sealed = seal(plain = randomBytes(size = 2 * chunk))
        val withoutLast = sealed.copyOf(sealed.size - TAG_SIZE)
        assertThrows(Exception::class.java) { open(sealed = withoutLast) }
    }

    @Test
    fun rejectsAStreamCutMidChunk() {
        val sealed = seal(plain = randomBytes(size = 2 * chunk + 100))
        assertThrows(Exception::class.java) { open(sealed = sealed.copyOf(sealed.size - 50)) }
    }

    @Test
    fun rejectsTamperedBytes() {
        val sealed = seal(plain = randomBytes(size = chunk + 100))
        sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { open(sealed = sealed) }
    }

    @Test
    fun rejectsAnotherKey() {
        val sealed = seal(plain = randomBytes(size = 1000))
        val other = ClipCipher(ByteArray(ClipCipher.KEY_SIZE) { 7 })
        assertThrows(Exception::class.java) { other.openStream(input = ByteArrayInputStream(sealed), output = ByteArrayOutputStream()) { } }
    }

    @Test
    fun opensAStreamSealedByAnotherImplementation() {
        val sealed = VECTOR.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val expected = ByteArray(50) { (it * 7 % 256).toByte() }
        assertArrayEquals(expected, open(sealed = sealed))
    }

    private fun seal(plain: ByteArray): ByteArray =
        ByteArrayOutputStream().also { cipher.sealStream(input = ByteArrayInputStream(plain), output = it, size = plain.size.toLong()) { } }.toByteArray()

    private fun open(sealed: ByteArray): ByteArray =
        ByteArrayOutputStream().also { cipher.openStream(input = ByteArrayInputStream(sealed), output = it) { } }.toByteArray()

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(Random(size.toLong())::nextBytes)

    private companion object {
        private const val TAG_SIZE = 16
        private const val VECTOR = "54535932000000100908070605040300df24655b4b22d20db07099d3382893a453a63dc9efe03f573f3dda7eb9f1564d89f94bdeb06ec08309a97a4fa373d5e2f6ebf9b69f09b712a377036ae4541accdb8cbd55f1813dc7c32b9aa27098ba0bee1528386c2fa7bd17d2099f88e3f781af69a2f5bdaa0e7c9eeea4ed76d415f0f42c"
    }
}
