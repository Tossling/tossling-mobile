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

    /** Length of the codes [newCode] makes (version 2). */
    const val CODE_LENGTH = 10

    /** Codes of this length come from older apps (version 1): they still join, but are never made. */
    const val V1_CODE_LENGTH = 8

    private const val TOPIC_LABEL = "tossy-invite-topic:"
    private const val KEY_SALT = "tossy-invite-v1"
    private const val ITERATIONS = 300_000
    private const val V2_SALT = "tossling-invite-v2"
    private const val V2_ITERATIONS = 600_000
    private const val V2_KEY_INFO = "tossling-invite-key"
    private const val V2_TOPIC_INFO = "tossling-invite-topic"
    private const val TOPIC_BYTES = 12

    // HKDF with an empty salt is HKDF with HashLen zero bytes (RFC 5869); spelled out because some providers refuse an
    // empty HMAC key.
    private val EMPTY_SALT = ByteArray(32)

    // A version 2 topic and key both come from one PBKDF2 run of 600,000 iterations; keeping the last seed lets
    // topic(), topics() and key() for the same code share it.
    private var lastSeed: Pair<String, ByteArray>? = null

    /** A new version 2 code, shown as `XXXXX-XXXXX`. */
    fun newCode(): String {
        val raw = Primitives.random(CODE_LENGTH).map { ALPHABET[(it.toInt() and 0xff) % ALPHABET.length] }.joinToString(separator = "")
        return "${raw.take(CODE_LENGTH / 2)}-${raw.drop(CODE_LENGTH / 2)}"
    }

    fun normalized(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    /** True for an 8-character code of an older app (version 1); anything else is treated as version 2. */
    fun isVersion1(code: String): Boolean = normalized(code).length == V1_CODE_LENGTH

    /** The invite topic under [prefix]; the version follows the code's length. */
    fun topic(code: String, prefix: String): String = prefix + "inv-" + topicHex(code)

    fun topics(code: String): List<String> {
        val hex = topicHex(code)
        return listOf(ROOM_PREFIX + "inv-" + hex, OLD_ROOM_PREFIX + "inv-" + hex)
    }

    /** The key that seals the invite; the version follows the code's length. Slow (PBKDF2): call off the main thread. */
    fun key(code: String): ByteArray =
        if (isVersion1(code)) keyV1(code) else Primitives.hkdf(ikm = seed(code), salt = EMPTY_SALT, info = V2_KEY_INFO.encodeToByteArray(), size = ClipCipher.KEY_SIZE)

    private fun keyV1(code: String): ByteArray =
        Primitives.pbkdf2(password = normalized(code).encodeToByteArray(), salt = KEY_SALT.encodeToByteArray(), iterations = ITERATIONS, size = ClipCipher.KEY_SIZE)

    internal fun seed(code: String): ByteArray {
        val clean = normalized(code)
        lastSeed?.takeIf { it.first == clean }?.let { return it.second.copyOf() }
        val seed = Primitives.pbkdf2(password = clean.encodeToByteArray(), salt = V2_SALT.encodeToByteArray(), iterations = V2_ITERATIONS, size = 32)
        lastSeed = clean to seed.copyOf()
        return seed
    }

    private fun topicHex(code: String): String =
        if (isVersion1(code)) {
            Primitives.sha256((TOPIC_LABEL + normalized(code)).encodeToByteArray()).copyOf(TOPIC_BYTES).hex()
        } else {
            Primitives.hkdf(ikm = seed(code), salt = EMPTY_SALT, info = V2_TOPIC_INFO.encodeToByteArray(), size = TOPIC_BYTES).hex()
        }

    fun seal(invite: Invite, key: ByteArray): String =
        ClipCipher(key).sealToText(plain = SyncJson.encodeToString(Invite.serializer(), invite).encodeToByteArray())

    fun open(message: String, key: ByteArray): Invite? =
        runCatching { SyncJson.decodeFromString(Invite.serializer(), ClipCipher(key).openText(text = message).decodeToString()) }.getOrNull()
}
