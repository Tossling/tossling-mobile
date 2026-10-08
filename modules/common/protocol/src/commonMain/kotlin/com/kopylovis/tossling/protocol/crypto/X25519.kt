package com.kopylovis.tossling.protocol.crypto

object X25519 {

    const val KEY_SIZE = 32

    fun newPrivateKey(): ByteArray = Primitives.random(KEY_SIZE)

    fun publicKey(privateKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_SIZE) { "X25519 keys are 32 bytes" }
        return Primitives.publicKey(privateKey = privateKey)
    }

    fun agree(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_SIZE && publicKey.size == KEY_SIZE) { "X25519 keys are 32 bytes" }
        return Primitives.agree(privateKey = privateKey, publicKey = publicKey)
    }
}
