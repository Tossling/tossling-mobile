package com.kopylovis.tossling.protocol

import com.kopylovis.tossling.protocol.crypto.ClipCipher
import com.kopylovis.tossling.protocol.crypto.Primitives
import com.kopylovis.tossling.protocol.crypto.hex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

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
    fun isExpired(now: Long = Clock.System.now().toEpochMilliseconds()): Boolean = expires * 1000 <= now
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

    fun newCode(): String {
        val raw = Primitives.random(CODE_LENGTH).map { ALPHABET[(it.toInt() and 0xff) % ALPHABET.length] }.joinToString(separator = "")
        return "${raw.take(CODE_LENGTH / 2)}-${raw.drop(CODE_LENGTH / 2)}"
    }

    fun normalized(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    fun topic(code: String, prefix: String): String {
        val hash = Primitives.sha256((TOPIC_LABEL + normalized(code)).encodeToByteArray())
        return prefix + "inv-" + hash.copyOf(TOPIC_BYTES).hex()
    }

    fun topics(code: String): List<String> = listOf(topic(code = code, prefix = ROOM_PREFIX), topic(code = code, prefix = OLD_ROOM_PREFIX))

    fun key(code: String): ByteArray =
        Primitives.pbkdf2(password = normalized(code).encodeToByteArray(), salt = KEY_SALT.encodeToByteArray(), iterations = ITERATIONS, size = ClipCipher.KEY_SIZE)

    fun seal(invite: Invite, key: ByteArray): String =
        ClipCipher(key).sealToText(plain = SyncJson.encodeToString(Invite.serializer(), invite).encodeToByteArray())

    fun open(message: String, key: ByteArray): Invite? =
        runCatching { SyncJson.decodeFromString(Invite.serializer(), ClipCipher(key).openText(text = message).decodeToString()) }.getOrNull()
}
