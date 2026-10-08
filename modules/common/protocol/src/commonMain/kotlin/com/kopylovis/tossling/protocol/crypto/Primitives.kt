package com.kopylovis.tossling.protocol.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.XDH
import dev.whyoleg.cryptography.random.CryptographyRandom

internal object Primitives {

    private val provider = CryptographyProvider.Default
    private val aes by lazy { provider.get(AES.GCM) }
    private val xdh by lazy { provider.get(XDH) }
    private val sha256 by lazy { provider.get(SHA256).hasher() }
    private val BASE_POINT = ByteArray(32).also { it[0] = 9 }

    fun random(size: Int): ByteArray = CryptographyRandom.nextBytes(size)

    fun sha256(data: ByteArray): ByteArray = sha256.hashBlocking(data)

    fun seal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray =
        aesGcmSeal(key = key, nonce = nonce, plain = plain, aad = aad)

    fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray? = null): ByteArray =
        aesGcmOpen(key = key, nonce = nonce, sealed = sealed, aad = aad)

    @OptIn(DelicateCryptographyApi::class)
    internal fun providerSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray?): ByteArray =
        aesKey(key).cipher().encryptWithIvBlocking(iv = nonce, plaintext = plain, associatedData = aad)

    @OptIn(DelicateCryptographyApi::class)
    internal fun providerOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray?): ByteArray =
        aesKey(key).cipher().decryptWithIvBlocking(iv = nonce, ciphertext = sealed, associatedData = aad)

    fun publicKey(privateKey: ByteArray): ByteArray = agree(privateKey = privateKey, publicKey = BASE_POINT)

    fun agree(privateKey: ByteArray, publicKey: ByteArray): ByteArray =
        xdhPrivate(privateKey).sharedSecretGenerator().generateSharedSecretToByteArrayBlocking(
            xdh.publicKeyDecoder(XDH.Curve.X25519).decodeFromByteArrayBlocking(XDH.PublicKey.Format.RAW, publicKey),
        )

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray =
        provider.get(HKDF).secretDerivation(digest = SHA256, outputSize = size.bytes, salt = salt, info = info).deriveSecretToByteArrayBlocking(ikm)

    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, size: Int): ByteArray =
        provider.get(PBKDF2).secretDerivation(digest = SHA256, iterations = iterations, outputSize = size.bytes, salt = salt).deriveSecretToByteArrayBlocking(password)

    private fun aesKey(key: ByteArray): AES.GCM.Key = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)

    private fun xdhPrivate(key: ByteArray): XDH.PrivateKey =
        xdh.privateKeyDecoder(XDH.Curve.X25519).decodeFromByteArrayBlocking(XDH.PrivateKey.Format.RAW, key)
}

internal expect fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray?): ByteArray

internal expect fun aesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray?): ByteArray

internal fun ByteArray.hex(): String = joinToString(separator = "") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
