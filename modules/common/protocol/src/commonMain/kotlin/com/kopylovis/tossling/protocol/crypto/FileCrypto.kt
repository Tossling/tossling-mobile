package com.kopylovis.tossling.protocol.crypto

import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

object FileCrypto {

    fun seal(cipher: ClipCipher, from: String, to: String): Long {
        val size = SystemFileSystem.metadataOrNull(Path(from))?.size ?: error("no file at $from")
        SystemFileSystem.source(Path(from)).buffered().use { source ->
            SystemFileSystem.sink(Path(to)).buffered().use { sink -> cipher.sealStream(source = source, sink = sink, size = size) {} }
        }
        return ClipCipher.sealedSize(size)
    }

    fun open(cipher: ClipCipher, from: String, to: String, stream: Boolean): Long =
        SystemFileSystem.source(Path(from)).buffered().use { source ->
            SystemFileSystem.sink(Path(to)).buffered().use { sink ->
                if (stream) {
                    cipher.openStream(source = source, sink = sink) {}
                } else {
                    val plain = cipher.open(sealed = source.readByteArray())
                    sink.write(plain)
                    plain.size.toLong()
                }
            }
        }
}
