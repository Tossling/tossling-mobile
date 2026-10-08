package com.kopylovis.tossling.protocol.crypto

import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.InputStream
import java.io.OutputStream

fun ClipCipher.sealStream(
    input: InputStream,
    output: OutputStream,
    size: Long,
    chunkSize: Int = ClipCipher.CHUNK_SIZE,
    prefix: ByteArray? = null,
    onChunk: (Long) -> Unit,
) {
    val source = input.asSource().buffered()
    val sink = output.asSink().buffered()
    if (prefix == null) sealStream(source = source, sink = sink, size = size, chunkSize = chunkSize, onChunk = onChunk)
    else sealStream(source = source, sink = sink, size = size, chunkSize = chunkSize, prefix = prefix, onChunk = onChunk)
}

fun ClipCipher.openStream(input: InputStream, output: OutputStream, onChunk: (Long) -> Unit): Long =
    openStream(source = input.asSource().buffered(), sink = output.asSink().buffered(), onChunk = onChunk)
