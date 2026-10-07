package com.kopylovis.tossling.sync.crypto

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class ClipCipher(key: ByteArray) {

    private val secret = SecretKeySpec(key, ALGORITHM)
    private val random = SecureRandom()

    fun seal(plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, nonce))
        return nonce + cipher.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > NONCE_SIZE + TAG_BITS / 8) { "sealed data is too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_SIZE))
        return cipher.doFinal(sealed, NONCE_SIZE, sealed.size - NONCE_SIZE)
    }

    fun sealToText(plain: ByteArray): String = Base64.getEncoder().encodeToString(seal(plain))

    fun openText(text: String): ByteArray = open(Base64.getDecoder().decode(text.trim()))

    fun sealStream(
        input: InputStream,
        output: OutputStream,
        size: Long,
        chunkSize: Int = CHUNK_SIZE,
        prefix: ByteArray = ByteArray(PREFIX_SIZE).also(random::nextBytes),
        onChunk: (Long) -> Unit,
    ) {
        require(prefix.size == PREFIX_SIZE) { "nonce prefix must be $PREFIX_SIZE bytes" }
        val header = ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).putInt(chunkSize).put(prefix).put(0).array()
        output.write(header)
        val chunks = size / chunkSize + 1
        var done = 0L
        for (index in 0 until chunks) {
            val last = index == chunks - 1
            val length = if (last) (size % chunkSize).toInt() else chunkSize
            val plain = input.readNBytes(length)
            if (plain.size != length) throw IOException("file changed while sending")
            output.write(chunkCipher(mode = Cipher.ENCRYPT_MODE, header = header, index = index, last = last).doFinal(plain))
            done += length
            onChunk(done)
        }
    }

    fun openStream(input: InputStream, output: OutputStream, onChunk: (Long) -> Unit): Long {
        val header = input.readNBytes(HEADER_SIZE)
        if (header.size != HEADER_SIZE || !header.copyOf(MAGIC.size).contentEquals(MAGIC)) throw IOException("not a Tossling stream")
        val chunkSize = ByteBuffer.wrap(header, MAGIC.size, Int.SIZE_BYTES).int
        if (chunkSize <= 0 || chunkSize > MAX_CHUNK_SIZE) throw IOException("bad chunk size")
        var done = 0L
        var index = 0L
        while (true) {
            val frame = input.readNBytes(chunkSize + TAG_SIZE)
            val last = frame.size < chunkSize + TAG_SIZE
            if (frame.size < TAG_SIZE) throw IOException("stream is cut short")
            val plain = chunkCipher(mode = Cipher.DECRYPT_MODE, header = header, index = index, last = last).doFinal(frame)
            output.write(plain)
            done += plain.size
            onChunk(done)
            if (last) return done
            index++
        }
    }

    private fun chunkCipher(mode: Int, header: ByteArray, index: Long, last: Boolean): Cipher {
        val nonce = ByteBuffer.allocate(NONCE_SIZE)
            .put(header, MAGIC.size + Int.SIZE_BYTES, PREFIX_SIZE)
            .putInt(index.toInt())
            .put(if (last) 1 else 0)
            .array()
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, secret, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
        }
    }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_SIZE = 12
        private const val TAG_BITS = 128
        private const val TAG_SIZE = TAG_BITS / 8
        private const val PREFIX_SIZE = 7
        private const val HEADER_SIZE = 16
        private const val MAX_CHUNK_SIZE = 16 shl 20
        private val MAGIC = "TSY2".toByteArray()
        const val CHUNK_SIZE = 1 shl 20
        const val KEY_SIZE = 32
        const val STREAM_FORMAT = 2

        fun sealedSize(size: Long): Long = HEADER_SIZE + size + TAG_SIZE * (size / CHUNK_SIZE + 1)

        fun fromBase64(key: String): ClipCipher {
            val bytes = Base64.getDecoder().decode(key)
            require(bytes.size == KEY_SIZE) { "key must be 32 bytes" }
            return ClipCipher(bytes)
        }
    }
}
