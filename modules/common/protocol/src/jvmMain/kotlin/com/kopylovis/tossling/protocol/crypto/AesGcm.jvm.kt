package com.kopylovis.tossling.protocol.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray?): ByteArray =
    cipher(mode = Cipher.ENCRYPT_MODE, key = key, nonce = nonce, aad = aad).doFinal(plain)

internal actual fun aesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray?): ByteArray =
    cipher(mode = Cipher.DECRYPT_MODE, key = key, nonce = nonce, aad = aad).doFinal(sealed)

private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray?): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        aad?.let(::updateAAD)
    }
