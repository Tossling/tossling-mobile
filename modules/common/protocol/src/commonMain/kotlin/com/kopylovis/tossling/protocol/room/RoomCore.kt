package com.kopylovis.tossling.protocol.room

import com.kopylovis.tossling.protocol.ClipMeta
import com.kopylovis.tossling.protocol.Freshness
import com.kopylovis.tossling.protocol.HandledMessages
import com.kopylovis.tossling.protocol.Member
import com.kopylovis.tossling.protocol.SyncJson
import com.kopylovis.tossling.protocol.stamped
import com.kopylovis.tossling.protocol.crypto.ClipCipher
import com.kopylovis.tossling.protocol.crypto.DeviceIdentity
import com.kopylovis.tossling.protocol.crypto.Primitives
import com.kopylovis.tossling.protocol.crypto.RoomKeys
import com.kopylovis.tossling.protocol.crypto.RoomSecret
import com.kopylovis.tossling.protocol.crypto.hex
import com.kopylovis.tossling.protocol.network.NtfyEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.io.encoding.Base64
import kotlin.time.Clock

@Serializable
data class RoomConfig(
    val server: String,
    val token: String,
    val room: String,
    val key: String,
    val deviceId: String,
    val deviceName: String,
    val owner: String = "",
    val identity: String = "",
    val source: String = "ios",
    val own: String = "",
) {
    val isOwner: Boolean get() = owner.isNotEmpty() && owner == deviceId

    val authToken: String get() = own.ifEmpty { token }
}

data class Outgoing(
    val message: String,
    val body: ByteArray?,
)

sealed interface Incoming {

    val eventId: String

    data class Content(
        override val eventId: String,
        val meta: ClipMeta,
        val memberId: String,
        val from: String,
        val attachmentUrl: String?,
        val fresh: Boolean,
        val time: Long,
    ) : Incoming

    data class Joined(override val eventId: String, val memberId: String, val from: String, val isNew: Boolean, val invite: String?) : Incoming

    data class Pinged(override val eventId: String, val memberId: String, val reply: Outgoing?) : Incoming

    data class Left(override val eventId: String, val memberId: String, val from: String) : Incoming

    data class Rekeyed(override val eventId: String, val from: String, val config: RoomConfig) : Incoming

    data class RekeyMissed(override val eventId: String, val from: String) : Incoming

    data class Kicked(override val eventId: String, val from: String, val ignored: Boolean) : Incoming

    data class FileGone(override val eventId: String, val from: String, val name: String) : Incoming

    data class Skipped(override val eventId: String, val reason: String) : Incoming
}

/**
 * [handled] is what [encodeHandled] gave before: the `mid`s handled in the last 3 hours, so a replayed message is
 * skipped after a restart too.
 */
class RoomCore(config: RoomConfig, members: Map<String, Member> = emptyMap(), handled: String = "") {

    var config: RoomConfig = config
        private set

    var members: Map<String, Member> = members
        private set

    private val handled = HandledMessages.decode(text = handled)

    /** Milliseconds now; tests replace it to seal messages at another time. */
    internal var clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    private val cipher: ClipCipher get() = ClipCipher.fromBase64(key = config.key)

    private val identity: DeviceIdentity?
        get() = config.identity.takeIf { it.isNotEmpty() }?.let { runCatching { DeviceIdentity(privateKey = Base64.decode(it)) }.getOrNull() }

    val knowsOthers: Boolean get() = members.keys.any { it != config.deviceId && !it.startsWith(LEGACY_PREFIX) }

    fun others(nowMs: Long): List<Member> =
        members.values.filter { it.id != config.deviceId }.sortedWith(compareByDescending<Member> { nowMs - it.seen < ONLINE_MS }.thenBy { it.name.lowercase() })

    fun isOnline(member: Member, nowMs: Long): Boolean = nowMs - member.seen < ONLINE_MS

    fun rename(name: String) {
        config = config.copy(deviceName = name)
    }

    fun open(event: NtfyEvent, nowMs: Long): Incoming {
        val id = event.id
        if (event.event != MESSAGE_EVENT) return Incoming.Skipped(id, "not a message")
        if (event.topic.isNotEmpty() && event.topic != config.room) return Incoming.Skipped(id, "another topic")
        val meta = runCatching { decode(cipher.openText(text = event.message.orEmpty())) }.getOrNull()
            ?: return Incoming.Skipped(id, "does not open with this room key")
        if (meta.sender.isNotEmpty() && meta.sender == config.deviceId) return Incoming.Skipped(id, "sent by this device")
        if (meta.to?.contains(config.deviceId) == false) return Incoming.Skipped(id, "for other devices")
        val mid = meta.messageId
        if (mid != null) {
            if (handled.contains(mid = mid, nowMs = nowMs)) return Incoming.Skipped(id, "message $mid was handled already")
            handled.add(mid = mid, nowMs = nowMs)
        }
        val age = nowMs / 1000 - event.time
        val contentAge = Freshness.ageMs(eventTime = event.time, sentAt = meta.sentAt, nowMs = nowMs)
        val memberId = memberId(meta)
        val from = meta.device.ifEmpty { "?" }
        val kind = meta.kind
        if (age >= STALE_SECONDS && kind !in LATE_KINDS) return Incoming.Skipped(id, "too old")
        if (kind in CONTENT_KINDS && kind != ClipMeta.FILE && contentAge >= Freshness.STALE_MS) return Incoming.Skipped(id, "too old")
        if (kind == ClipMeta.FILE && contentAge >= Freshness.FILE_STALE_MS) return Incoming.FileGone(id, from = from, name = meta.fileName ?: "file")
        when (kind) {
            ClipMeta.PING -> {
                remember(meta = meta, time = event.time, nowMs = nowMs, fresh = contentAge < Freshness.STALE_MS)
                val reply = meta.sender.takeIf { it.isNotEmpty() }?.let { hello(to = listOf(it)) }
                return Incoming.Pinged(id, memberId = memberId, reply = reply)
            }

            ClipMeta.BYE -> {
                members = members - memberId
                return Incoming.Left(id, memberId = memberId, from = from)
            }

            ClipMeta.REKEY -> return rekey(eventId = id, meta = meta, from = from)
            ClipMeta.KICK -> return Incoming.Kicked(id, from = from, ignored = config.isOwner)
        }
        val isNew = remember(meta = meta, time = event.time, nowMs = nowMs, fresh = contentAge < Freshness.STALE_MS)
        if (kind == ClipMeta.HELLO) return Incoming.Joined(id, memberId = memberId, from = from, isNew = isNew, invite = meta.invite)
        if (kind !in CONTENT_KINDS) return Incoming.Skipped(id, "kind $kind")
        return Incoming.Content(
            eventId = id,
            meta = meta,
            memberId = memberId,
            from = from,
            attachmentUrl = event.attachment?.url?.takeIf { it.isNotEmpty() },
            fresh = contentAge < Freshness.STALE_MS,
            time = event.time,
        )
    }

    /**
     * Forgets that [content] was handled, for an item that could not be fetched for now: when its event comes again
     * after a reconnect, it is not skipped as a replay.
     */
    fun retryLater(content: Incoming.Content) {
        content.meta.messageId?.let(handled::remove)
    }

    /** The `mid`s handled lately, to save next to the last event id and pass back to the constructor. */
    fun encodeHandled(): String = handled.encode()

    @Throws(Exception::class)
    fun openAttachment(sealed: ByteArray): ByteArray = cipher.open(sealed = sealed)

    fun hello(to: List<String>? = null, renewed: Boolean = false, invite: String? = null): Outgoing =
        control(kind = ClipMeta.HELLO).copy(to = to, renewed = renewed.takeIf { it }, invite = invite).sealed()

    fun ping(): Outgoing = control(kind = ClipMeta.PING).sealed()

    fun bye(): Outgoing = control(kind = ClipMeta.BYE).sealed()

    fun text(text: String, to: List<String>? = null): Outgoing {
        val base = content(kind = ClipMeta.TEXT, mime = ClipMeta.TEXT_MIME, to = to)
        val bytes = text.encodeToByteArray()
        if (bytes.size <= INLINE_LIMIT) {
            val inline = base.copy(text = text).sealed()
            if (inline.message.length <= MESSAGE_LIMIT) return inline
        }
        return base.sealed(body = cipher.seal(plain = bytes))
    }

    fun image(bytes: ByteArray, mime: String, to: List<String>? = null): Outgoing =
        content(kind = ClipMeta.IMAGE, mime = mime, to = to).sealed(body = cipher.seal(plain = bytes))

    fun file(name: String, size: Long, mime: String, to: List<String>? = null): Outgoing =
        content(kind = ClipMeta.FILE, mime = mime, to = to).copy(fileName = name, format = ClipCipher.STREAM_FORMAT, size = size).sealed()

    fun fileCipher(): ClipCipher = cipher

    fun canRenewToken(id: String): Boolean = withoutKey(id = id).isEmpty()

    /**
     * The devices that would stay in the room after removing [id] but have no key of their own yet: the removal is
     * refused until they say hello with one, or the new room key would have to go out under the old one.
     */
    fun withoutKey(id: String): List<Member> = Removal.withoutKey(members = members.values, removed = id, self = config.deviceId)

    /** The devices that would stay in the room after removing [id] and whose key changed: the user is warned first. */
    fun keyChangedAmong(id: String): List<Member> = Removal.keyChanged(members = members.values, removed = id, self = config.deviceId)

    /** The user accepted the member's current key: the "key changed" mark goes away. */
    fun acceptKey(id: String) {
        val member = members[id] ?: return
        members = members + (id to member.copy(keyChanged = 0))
    }

    /**
     * The `kick` and `rekey` that move everyone but [id] to [room]. Throws [MissingKeys] when a remaining device has no
     * key: check [withoutKey] before issuing a new token. The user should have confirmed [keyChangedAmong] already.
     */
    @Throws(Exception::class)
    fun revoke(id: String, room: String, key: String, token: String?): List<Outgoing> {
        require(id != config.owner) { "the creator of the room cannot be removed" }
        val missing = withoutKey(id = id)
        if (missing.isNotEmpty()) throw MissingKeys(members = missing)
        val others = remainingAfter(id = id)
        val messages = mutableListOf(control(kind = ClipMeta.KICK).copy(to = listOf(id)).sealed())
        if (others.isNotEmpty()) {
            val sealed = RoomKeys.seal(secret = RoomSecret(key = key, room = room, token = token), recipients = others.mapValues { it.value.pk })
            messages += control(kind = ClipMeta.REKEY).copy(to = others.keys.toList(), ephemeral = sealed.ephemeral, keys = sealed.keys).sealed()
        }
        return messages
    }

    private fun remainingAfter(id: String): Map<String, Member> = Removal.remaining(members = members.values, removed = id, self = config.deviceId).associateBy { it.id }

    fun switchRoom(room: String, key: String, token: String?, keep: Set<String>) {
        config = config.copy(room = room, key = key, token = token?.takeIf { it.isNotEmpty() } ?: config.token)
        members = members.filterKeys { it in keep || it == config.deviceId }
    }

    fun encodeMembers(): String = SyncJson.encodeToString(MEMBERS, members)

    private fun rekey(eventId: String, meta: ClipMeta, from: String): Incoming {
        val box = meta.keys?.get(config.deviceId)
        val ephemeral = meta.ephemeral
        val identity = identity
        val opened = if (box != null && ephemeral != null && identity != null) RoomKeys.open(identity = identity, id = config.deviceId, ephemeral = ephemeral, box = box) else null
        val room = opened?.room ?: meta.room
        val key = opened?.key ?: meta.key
        if (room == null || key == null || runCatching { ClipCipher.fromBase64(key = key) }.isFailure) return Incoming.RekeyMissed(eventId, from = from)
        switchRoom(room = room, key = key, token = opened?.token, keep = meta.to.orEmpty().toSet() + memberId(meta))
        return Incoming.Rekeyed(eventId, from = from, config = config)
    }

    private fun remember(meta: ClipMeta, time: Long, nowMs: Long, fresh: Boolean): Boolean {
        val id = memberId(meta)
        val offered = meta.publicKey?.takeIf { RoomKeys.decodeKey(it) != null }.orEmpty()
        // A replayed `re` hello must not bring back an old key: only a fresh one (by `ts` too) replaces it.
        val renewed = meta.renewed == true && meta.kind == ClipMeta.HELLO && offered.isNotEmpty() && fresh
        val seen = minOf(nowMs, time * 1000)
        val known = members[id]
        val pk = if (known == null || known.pk.isEmpty() || renewed) offered else known.pk
        val changed = known != null && known.pk.isNotEmpty() && pk != known.pk
        val keyChanged = if (changed) nowMs else known?.keyChanged ?: 0
        members = members + (id to Member(id = id, name = meta.device.ifEmpty { "?" }, source = meta.source, seen = seen, since = known?.since ?: seen, alias = known?.alias.orEmpty(), pk = pk, keyChanged = keyChanged))
        return known == null
    }

    private fun control(kind: String): ClipMeta =
        ClipMeta(kind = kind, source = config.source, device = config.deviceName, sender = config.deviceId, publicKey = identity?.publicText)

    private fun content(kind: String, mime: String, to: List<String>?): ClipMeta =
        ClipMeta(kind = kind, mime = mime, source = config.source, device = config.deviceName, sender = config.deviceId, to = to)

    private fun ClipMeta.sealed(body: ByteArray? = null): Outgoing =
        Outgoing(message = cipher.sealToText(plain = SyncJson.encodeToString(ClipMeta.serializer(), stamped(nowMs = clock())).encodeToByteArray()), body = body)

    private fun decode(plain: ByteArray): ClipMeta = SyncJson.decodeFromString(ClipMeta.serializer(), plain.decodeToString())

    private fun memberId(meta: ClipMeta): String = meta.sender.ifEmpty { "$LEGACY_PREFIX${meta.source}-${meta.device}" }

    companion object {
        const val LEGACY_PREFIX = "legacy-"
        const val ONLINE_MS = 5 * 60_000L
        const val STALE_SECONDS = 15 * 60
        const val FILE_STALE_SECONDS = 3 * 60 * 60
        const val INLINE_LIMIT = 2_400

        // ntfy keeps a body of up to 4,096 bytes as the message and turns a longer one into an attachment that no
        // receiver can open. Escaping, encryption and Base64 can take 2,400 bytes of text past that, so the sealed
        // envelope decides.
        const val MESSAGE_LIMIT = 4_096
        const val ROOM_BYTES = 12
        const val TOKEN_LABEL = "tossling"
        private const val MESSAGE_EVENT = "message"
        private val LATE_KINDS = setOf(ClipMeta.BYE, ClipMeta.REKEY, ClipMeta.KICK, ClipMeta.FILE)
        private val CONTENT_KINDS = setOf(ClipMeta.TEXT, ClipMeta.IMAGE, ClipMeta.FILE)
        private val MEMBERS = MapSerializer(String.serializer(), Member.serializer())

        fun encodeConfig(config: RoomConfig): String = SyncJson.encodeToString(RoomConfig.serializer(), config)

        fun decodeConfig(text: String): RoomConfig? = runCatching { SyncJson.decodeFromString(RoomConfig.serializer(), text) }.getOrNull()

        fun encodeMembers(members: Map<String, Member>): String = SyncJson.encodeToString(MEMBERS, members)

        fun decodeMembers(text: String): Map<String, Member> = runCatching { SyncJson.decodeFromString(MEMBERS, text) }.getOrDefault(emptyMap())

        fun createRoom(prefix: String): String = prefix + Primitives.random(ROOM_BYTES).hex()

        fun createDeviceId(): String = Primitives.random(8).hex()

        fun createIdentity(): String = Base64.encode(DeviceIdentity.generate().privateKey)
    }
}

/** The removal is refused: these remaining devices have no key of their own yet and have to be opened or updated. */
class MissingKeys(val members: List<Member>) : Exception("no key yet: ${members.joinToString(separator = ", ") { it.title }}")

/**
 * The checks before removing a device, on the devices that stay: every member except this one, the removed one and
 * old devices without an id (they cannot be addressed and never get a sealed key).
 */
object Removal {

    fun remaining(members: Collection<Member>, removed: String, self: String): List<Member> =
        members.filter { it.id != removed && it.id != self && !it.id.startsWith(RoomCore.LEGACY_PREFIX) }

    /** Remaining devices without a usable key: the removal is refused and names them. */
    fun withoutKey(members: Collection<Member>, removed: String, self: String): List<Member> =
        remaining(members = members, removed = removed, self = self).filter { RoomKeys.decodeKey(it.pk) == null }.sortedBy { it.title.lowercase() }

    /** Remaining devices marked "key changed": the user is warned, naming them, and goes on only after confirming. */
    fun keyChanged(members: Collection<Member>, removed: String, self: String): List<Member> =
        remaining(members = members, removed = removed, self = self).filter { it.keyChanged > 0 }.sortedBy { it.title.lowercase() }
}
