package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.crypto.ClipCipher
import com.kopylovis.tossling.protocol.crypto.FileCrypto
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FileCryptoTest {

    @Test
    fun sealsAndOpensFilesOnDisk() {
        val cipher = ClipCipher.fromBase64(ClipCipher.randomKey())
        val dir = SystemTemporaryDirectory
        val tag = Random.nextLong().toString(16).removePrefix("-")
        val plain = Path(dir, "plain-$tag")
        val sealed = Path(dir, "sealed-$tag")
        val opened = Path(dir, "opened-$tag")
        val bytes = Random.nextBytes(ClipCipher.CHUNK_SIZE + 1234)
        SystemFileSystem.sink(plain).buffered().use { it.write(bytes) }
        assertEquals(ClipCipher.sealedSize(bytes.size.toLong()), FileCrypto.seal(cipher = cipher, from = plain.toString(), to = sealed.toString()))
        assertEquals(ClipCipher.sealedSize(bytes.size.toLong()), SystemFileSystem.metadataOrNull(sealed)?.size)
        assertEquals(bytes.size.toLong(), FileCrypto.open(cipher = cipher, from = sealed.toString(), to = opened.toString(), stream = true))
        assertContentEquals(bytes, SystemFileSystem.source(opened).buffered().use { it.readByteArray() })
        listOf(plain, sealed, opened).forEach { SystemFileSystem.delete(it, mustExist = false) }
    }
}
