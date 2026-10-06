package com.kopylovis.tossling.sync.crypto

import com.kopylovis.tossling.sync.data.SyncJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class DeviceIdentity(val privateKey: ByteArray) {

    val publicKey: ByteArray = X25519.publicKey(privateKey = privateKey)

    val publicText: String get() = Base64.getEncoder().encodeToString(publicKey)

    companion object {
        fun generate(): DeviceIdentity = DeviceIdentity(privateKey = X25519.newPrivateKey())
    }
}

@Serializable
internal data class RoomSecret(
    @SerialName("key") val key: String,
    @SerialName("r") val room: String,
    @SerialName("t") val token: String? = null,
)

internal data class SealedRoom(
    val ephemeral: String,
    val keys: Map<String, String>,
)

internal object RoomKeys {

    private const val INFO = "tossy-rekey-v1"
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun seal(secret: RoomSecret, recipients: Map<String, String>): SealedRoom {
        val ephemeral = X25519.newPrivateKey()
        val plain = SyncJson.encodeToString(RoomSecret.serializer(), secret).toByteArray()
        val keys = recipients.mapNotNull { (id, publicKey) ->
            val recipient = decodeKey(publicKey) ?: return@mapNotNull null
            id to sealFor(ephemeral = ephemeral, recipient = recipient, id = id, plain = plain, nonce = ByteArray(NONCE_SIZE).also(random::nextBytes))
        }.toMap()
        return SealedRoom(ephemeral = encode(X25519.publicKey(privateKey = ephemeral)), keys = keys)
    }

    fun open(identity: DeviceIdentity, id: String, ephemeral: String, box: String): RoomSecret? = runCatching {
        val ephemeralKey = decodeKey(ephemeral) ?: return null
        val sealed = Base64.getDecoder().decode(box)
        val kek = kek(shared = X25519.agree(privateKey = identity.privateKey, publicKey = ephemeralKey), ephemeral = ephemeralKey, recipient = identity.publicKey)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_SIZE))
        cipher.updateAAD(id.toByteArray())
        val plain = cipher.doFinal(sealed, NONCE_SIZE, sealed.size - NONCE_SIZE)
        SyncJson.decodeFromString(RoomSecret.serializer(), plain.decodeToString())
    }.getOrNull()

    fun sealFor(ephemeral: ByteArray, recipient: ByteArray, id: String, plain: ByteArray, nonce: ByteArray): String {
        val kek = kek(shared = X25519.agree(privateKey = ephemeral, publicKey = recipient), ephemeral = X25519.publicKey(privateKey = ephemeral), recipient = recipient)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(id.toByteArray())
        return encode(nonce + cipher.doFinal(plain))
    }

    fun decodeKey(text: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(text) }.getOrNull()?.takeIf { it.size == X25519.KEY_SIZE }

    private fun kek(shared: ByteArray, ephemeral: ByteArray, recipient: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ephemeral + recipient, "HmacSHA256"))
        val prk = mac.doFinal(shared)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(INFO.toByteArray())
        mac.update(1)
        return mac.doFinal()
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
