package com.kopylovis.tossling.protocol.crypto

internal actual fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray?): ByteArray =
    Primitives.providerSeal(key = key, nonce = nonce, plain = plain, aad = aad)

internal actual fun aesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray?): ByteArray =
    Primitives.providerOpen(key = key, nonce = nonce, sealed = sealed, aad = aad)
