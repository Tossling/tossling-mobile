package com.kopylovis.tossling.protocol.crypto

import com.kopylovis.tossling.protocol.SyncJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

class DeviceIdentity(val privateKey: ByteArray) {

    val publicKey: ByteArray = X25519.publicKey(privateKey = privateKey)

    val publicText: String get() = Base64.encode(publicKey)

    companion object {
        fun generate(): DeviceIdentity = DeviceIdentity(privateKey = X25519.newPrivateKey())
    }
}

@Serializable
data class RoomSecret(
    @SerialName("key") val key: String,
    @SerialName("r") val room: String,
    @SerialName("t") val token: String? = null,
)

data class SealedRoom(
    val ephemeral: String,
    val keys: Map<String, String>,
)

object RoomKeys {

    private const val INFO = "tossy-rekey-v1"
    private const val NONCE_SIZE = 12

    fun seal(secret: RoomSecret, recipients: Map<String, String>): SealedRoom {
        val ephemeral = X25519.newPrivateKey()
        val plain = SyncJson.encodeToString(RoomSecret.serializer(), secret).encodeToByteArray()
        val keys = recipients.mapNotNull { (id, publicKey) ->
            val recipient = decodeKey(publicKey) ?: return@mapNotNull null
            id to sealFor(ephemeral = ephemeral, recipient = recipient, id = id, plain = plain, nonce = Primitives.random(NONCE_SIZE))
        }.toMap()
        return SealedRoom(ephemeral = encode(X25519.publicKey(privateKey = ephemeral)), keys = keys)
    }

    fun open(identity: DeviceIdentity, id: String, ephemeral: String, box: String): RoomSecret? = runCatching {
        val ephemeralKey = decodeKey(ephemeral) ?: return null
        val sealed = Base64.decode(box)
        val kek = kek(shared = X25519.agree(privateKey = identity.privateKey, publicKey = ephemeralKey), ephemeral = ephemeralKey, recipient = identity.publicKey)
        val plain = Primitives.open(key = kek, nonce = sealed.copyOfRange(0, NONCE_SIZE), sealed = sealed.copyOfRange(NONCE_SIZE, sealed.size), aad = id.encodeToByteArray())
        SyncJson.decodeFromString(RoomSecret.serializer(), plain.decodeToString())
    }.getOrNull()

    fun sealFor(ephemeral: ByteArray, recipient: ByteArray, id: String, plain: ByteArray, nonce: ByteArray): String {
        val kek = kek(shared = X25519.agree(privateKey = ephemeral, publicKey = recipient), ephemeral = X25519.publicKey(privateKey = ephemeral), recipient = recipient)
        return encode(nonce + Primitives.seal(key = kek, nonce = nonce, plain = plain, aad = id.encodeToByteArray()))
    }

    fun decodeKey(text: String): ByteArray? =
        runCatching { Base64.decode(text) }.getOrNull()?.takeIf { it.size == X25519.KEY_SIZE }

    private fun kek(shared: ByteArray, ephemeral: ByteArray, recipient: ByteArray): ByteArray =
        Primitives.hkdf(ikm = shared, salt = ephemeral + recipient, info = INFO.encodeToByteArray(), size = 32)

    private fun encode(bytes: ByteArray): String = Base64.encode(bytes)
}
