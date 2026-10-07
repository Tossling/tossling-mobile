package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.crypto.ClipCipher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@Serializable
data class Invite(
    @SerialName("s") val server: String,
    @SerialName("t") val token: String = "",
    @SerialName("r") val room: String,
    @SerialName("k") val key: String,
    @SerialName("n") val name: String = "",
    @SerialName("exp") val expires: Double,
    @SerialName("o") val owner: String? = null,
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = expires * 1000 <= now
}

object Invites {

    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LIFETIME_SECONDS = 600
    const val OFFER_INTERVAL_MS = 15_000L

    private const val CODE_LENGTH = 8
    private const val TOPIC_LABEL = "tossy-invite-topic:"
    private const val KEY_SALT = "tossy-invite-v1"
    private const val ITERATIONS = 300_000
    private const val TOPIC_BYTES = 12
    private val random = SecureRandom()

    fun newCode(): String {
        val raw = (1..CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString(separator = "")
        return "${raw.take(CODE_LENGTH / 2)}-${raw.drop(CODE_LENGTH / 2)}"
    }

    fun normalized(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    fun topic(code: String, prefix: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest((TOPIC_LABEL + normalized(code)).toByteArray())
        return prefix + "inv-" + hash.take(TOPIC_BYTES).joinToString(separator = "") { "%02x".format(it) }
    }

    fun topics(code: String): List<String> = listOf(topic(code = code, prefix = ROOM_PREFIX), topic(code = code, prefix = OLD_ROOM_PREFIX))

    fun key(code: String): ByteArray {
        val spec = PBEKeySpec(normalized(code).toCharArray(), KEY_SALT.toByteArray(), ITERATIONS, ClipCipher.KEY_SIZE * Byte.SIZE_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(invite: Invite, key: ByteArray): String =
        ClipCipher(key).sealToText(plain = SyncJson.encodeToString(Invite.serializer(), invite).toByteArray())

    fun open(message: String, key: ByteArray): Invite? =
        runCatching { SyncJson.decodeFromString(Invite.serializer(), ClipCipher(key).openText(text = message).decodeToString()) }.getOrNull()
}
