package com.kopylovis.tossling.protocol.crypto

import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

object X25519 {

    const val KEY_SIZE = 32

    private const val ALGORITHM = "XDH"
    private val PUBLIC_PREFIX = bytes("302a300506032b656e032100")
    private val PRIVATE_PREFIX = bytes("302e020100300506032b656e04220420")
    private val BASE_POINT = ByteArray(KEY_SIZE).also { it[0] = 9 }
    private val random = SecureRandom()

    fun newPrivateKey(): ByteArray = ByteArray(KEY_SIZE).also(random::nextBytes)

    fun publicKey(privateKey: ByteArray): ByteArray = agree(privateKey = privateKey, publicKey = BASE_POINT)

    fun agree(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_SIZE && publicKey.size == KEY_SIZE) { "X25519 keys are 32 bytes" }
        val factory = KeyFactory.getInstance(ALGORITHM)
        val private = factory.generatePrivate(PKCS8EncodedKeySpec(PRIVATE_PREFIX + privateKey))
        val public = factory.generatePublic(X509EncodedKeySpec(PUBLIC_PREFIX + publicKey))
        return KeyAgreement.getInstance(ALGORITHM).run {
            init(private)
            doPhase(public, true)
            generateSecret()
        }
    }

    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
