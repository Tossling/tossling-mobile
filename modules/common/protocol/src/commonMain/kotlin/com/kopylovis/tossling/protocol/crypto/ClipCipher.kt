package com.kopylovis.tossling.protocol.crypto

import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlin.io.encoding.Base64

class ClipCipher(private val key: ByteArray) {

    init {
        require(key.size == KEY_SIZE) { "key must be $KEY_SIZE bytes" }
    }

    fun seal(plain: ByteArray, nonce: ByteArray = Primitives.random(NONCE_SIZE)): ByteArray {
        require(nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes" }
        return nonce + Primitives.seal(key = key, nonce = nonce, plain = plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > NONCE_SIZE + TAG_SIZE) { "sealed data is too short" }
        return Primitives.open(key = key, nonce = sealed.copyOfRange(0, NONCE_SIZE), sealed = sealed.copyOfRange(NONCE_SIZE, sealed.size))
    }

    fun sealToText(plain: ByteArray): String = Base64.encode(seal(plain))

    fun openText(text: String): ByteArray = open(Base64.decode(text.trim()))

    fun sealStream(
        source: Source,
        sink: Sink,
        size: Long,
        chunkSize: Int = CHUNK_SIZE,
        prefix: ByteArray = Primitives.random(PREFIX_SIZE),
        onChunk: (Long) -> Unit,
    ) {
        require(prefix.size == PREFIX_SIZE) { "nonce prefix must be $PREFIX_SIZE bytes" }
        val header = MAGIC + chunkSize.bigEndian() + prefix + byteArrayOf(0)
        sink.write(header)
        val chunks = size / chunkSize + 1
        var done = 0L
        for (index in 0 until chunks) {
            val last = index == chunks - 1
            val length = if (last) (size % chunkSize).toInt() else chunkSize
            val plain = source.readUpTo(length)
            if (plain.size != length) throw IOException("file changed while sending")
            sink.write(Primitives.seal(key = key, nonce = chunkNonce(header = header, index = index, last = last), plain = plain, aad = header))
            done += length
            onChunk(done)
        }
        sink.flush()
    }

    fun openStream(source: Source, sink: Sink, onChunk: (Long) -> Unit): Long {
        val header = source.readUpTo(HEADER_SIZE)
        if (header.size != HEADER_SIZE || !header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw IOException("not a Tossling stream")
        val chunkSize = header.int(MAGIC.size)
        if (chunkSize <= 0 || chunkSize > MAX_CHUNK_SIZE) throw IOException("bad chunk size")
        var done = 0L
        var index = 0L
        while (true) {
            val frame = source.readUpTo(chunkSize + TAG_SIZE)
            val last = frame.size < chunkSize + TAG_SIZE
            if (frame.size < TAG_SIZE) throw IOException("stream is cut short")
            val plain = Primitives.open(key = key, nonce = chunkNonce(header = header, index = index, last = last), sealed = frame, aad = header)
            sink.write(plain)
            done += plain.size
            onChunk(done)
            if (last) {
                sink.flush()
                return done
            }
            index++
        }
    }

    private fun chunkNonce(header: ByteArray, index: Long, last: Boolean): ByteArray =
        header.copyOfRange(MAGIC.size + Int.SIZE_BYTES, MAGIC.size + Int.SIZE_BYTES + PREFIX_SIZE) + index.toInt().bigEndian() + byteArrayOf(if (last) 1 else 0)

    companion object {
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private const val PREFIX_SIZE = 7
        private const val HEADER_SIZE = 16
        private const val MAX_CHUNK_SIZE = 16 shl 20
        private val MAGIC = "TSY2".encodeToByteArray()
        const val CHUNK_SIZE = 1 shl 20
        const val KEY_SIZE = 32
        const val STREAM_FORMAT = 2

        fun sealedSize(size: Long): Long = HEADER_SIZE + size + TAG_SIZE * (size / CHUNK_SIZE + 1)

        fun fromBase64(key: String): ClipCipher = ClipCipher(Base64.decode(key))

        fun randomKey(): String = Base64.encode(Primitives.random(KEY_SIZE))
    }
}

private fun Int.bigEndian(): ByteArray = byteArrayOf((this ushr 24).toByte(), (this ushr 16).toByte(), (this ushr 8).toByte(), toByte())

private fun ByteArray.int(offset: Int): Int =
    ((this[offset].toInt() and 0xff) shl 24) or ((this[offset + 1].toInt() and 0xff) shl 16) or ((this[offset + 2].toInt() and 0xff) shl 8) or (this[offset + 3].toInt() and 0xff)

private fun Source.readUpTo(count: Int): ByteArray {
    val buffer = ByteArray(count)
    var read = 0
    while (read < count) {
        val got = readAtMostTo(buffer, startIndex = read, endIndex = count)
        if (got == -1) break
        read += got
    }
    return if (read == count) buffer else buffer.copyOf(read)
}
