package com.kopylovis.tossling.protocol.room

import com.kopylovis.tossling.protocol.ClipMeta
import com.kopylovis.tossling.protocol.Member
import com.kopylovis.tossling.protocol.SyncJson
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
) {
    val isOwner: Boolean get() = owner.isNotEmpty() && owner == deviceId
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

class RoomCore(config: RoomConfig, members: Map<String, Member> = emptyMap()) {

    var config: RoomConfig = config
        private set

    var members: Map<String, Member> = members
        private set

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
        val age = nowMs / 1000 - event.time
        val memberId = memberId(meta)
        val from = meta.device.ifEmpty { "?" }
        val kind = meta.kind
        if (age >= STALE_SECONDS && kind !in LATE_KINDS) return Incoming.Skipped(id, "too old")
        if (kind == ClipMeta.FILE && age >= FILE_STALE_SECONDS) return Incoming.FileGone(id, from = from, name = meta.fileName ?: "file")
        when (kind) {
            ClipMeta.PING -> {
                remember(meta = meta, time = event.time, nowMs = nowMs)
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
        val isNew = remember(meta = meta, time = event.time, nowMs = nowMs)
        if (kind == ClipMeta.HELLO) return Incoming.Joined(id, memberId = memberId, from = from, isNew = isNew, invite = meta.invite)
        if (kind !in CONTENT_KINDS) return Incoming.Skipped(id, "kind $kind")
        return Incoming.Content(
            eventId = id,
            meta = meta,
            memberId = memberId,
            from = from,
            attachmentUrl = event.attachment?.url?.takeIf { it.isNotEmpty() },
            fresh = age < STALE_SECONDS,
            time = event.time,
        )
    }

    fun openAttachment(sealed: ByteArray): ByteArray = cipher.open(sealed = sealed)

    fun hello(to: List<String>? = null, renewed: Boolean = false, invite: String? = null): Outgoing =
        control(kind = ClipMeta.HELLO).copy(to = to, renewed = renewed.takeIf { it }, invite = invite).sealed()

    fun ping(): Outgoing = control(kind = ClipMeta.PING).sealed()

    fun bye(): Outgoing = control(kind = ClipMeta.BYE).sealed()

    fun text(text: String, to: List<String>? = null): Outgoing {
        val base = content(kind = ClipMeta.TEXT, mime = ClipMeta.TEXT_MIME, to = to)
        val bytes = text.encodeToByteArray()
        return if (bytes.size <= INLINE_LIMIT) base.copy(text = text).sealed() else base.sealed(body = cipher.seal(plain = bytes))
    }

    fun image(bytes: ByteArray, mime: String, to: List<String>? = null): Outgoing =
        content(kind = ClipMeta.IMAGE, mime = mime, to = to).sealed(body = cipher.seal(plain = bytes))

    fun file(name: String, size: Long, mime: String, to: List<String>? = null): Outgoing =
        content(kind = ClipMeta.FILE, mime = mime, to = to).copy(fileName = name, format = ClipCipher.STREAM_FORMAT, size = size).sealed()

    fun fileCipher(): ClipCipher = cipher

    fun revoke(id: String, room: String, key: String, token: String?): List<Outgoing> {
        require(id != config.owner) { "the creator of the room cannot be removed" }
        val others = members.filterKeys { it != id && it != config.deviceId && !it.startsWith(LEGACY_PREFIX) }
        val isLegacy = others.values.any { RoomKeys.decodeKey(it.pk) == null }
        val messages = mutableListOf(control(kind = ClipMeta.KICK).copy(to = listOf(id)).sealed())
        if (others.isNotEmpty()) {
            val sealed = RoomKeys.seal(secret = RoomSecret(key = key, room = room, token = token.takeUnless { isLegacy }), recipients = others.mapValues { it.value.pk })
            messages += control(kind = ClipMeta.REKEY).copy(
                to = others.keys.toList(),
                ephemeral = sealed.ephemeral,
                keys = sealed.keys,
                room = room.takeIf { isLegacy },
                key = key.takeIf { isLegacy },
            ).sealed()
        }
        return messages
    }

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

    private fun remember(meta: ClipMeta, time: Long, nowMs: Long): Boolean {
        val id = memberId(meta)
        val offered = meta.publicKey?.takeIf { RoomKeys.decodeKey(it) != null }.orEmpty()
        val renewed = meta.renewed == true && meta.kind == ClipMeta.HELLO && offered.isNotEmpty()
        val seen = minOf(nowMs, time * 1000)
        val known = members[id]
        val pk = if (known == null || known.pk.isEmpty() || renewed) offered else known.pk
        members = members + (id to Member(id = id, name = meta.device.ifEmpty { "?" }, source = meta.source, seen = seen, since = known?.since ?: seen, alias = known?.alias.orEmpty(), pk = pk))
        return known == null
    }

    private fun control(kind: String): ClipMeta =
        ClipMeta(kind = kind, source = config.source, device = config.deviceName, sender = config.deviceId, publicKey = identity?.publicText)

    private fun content(kind: String, mime: String, to: List<String>?): ClipMeta =
        ClipMeta(kind = kind, mime = mime, source = config.source, device = config.deviceName, sender = config.deviceId, to = to)

    private fun ClipMeta.sealed(body: ByteArray? = null): Outgoing =
        Outgoing(message = cipher.sealToText(plain = SyncJson.encodeToString(ClipMeta.serializer(), this).encodeToByteArray()), body = body)

    private fun decode(plain: ByteArray): ClipMeta = SyncJson.decodeFromString(ClipMeta.serializer(), plain.decodeToString())

    private fun memberId(meta: ClipMeta): String = meta.sender.ifEmpty { "$LEGACY_PREFIX${meta.source}-${meta.device}" }

    companion object {
        const val LEGACY_PREFIX = "legacy-"
        const val ONLINE_MS = 5 * 60_000L
        const val STALE_SECONDS = 15 * 60
        const val FILE_STALE_SECONDS = 3 * 60 * 60
        const val INLINE_LIMIT = 2_400
        const val ROOM_BYTES = 12
        const val TOKEN_LABEL = "tossling"
        private const val MESSAGE_EVENT = "message"
        private val LATE_KINDS = setOf(ClipMeta.BYE, ClipMeta.REKEY, ClipMeta.KICK, ClipMeta.FILE)
        private val CONTENT_KINDS = setOf(ClipMeta.TEXT, ClipMeta.IMAGE, ClipMeta.FILE)
        private val MEMBERS = MapSerializer(String.serializer(), Member.serializer())

        fun decodeMembers(text: String): Map<String, Member> = runCatching { SyncJson.decodeFromString(MEMBERS, text) }.getOrDefault(emptyMap())

        fun createRoom(prefix: String): String = prefix + Primitives.random(ROOM_BYTES).hex()

        fun createDeviceId(): String = Primitives.random(8).hex()

        fun createIdentity(): String = Base64.encode(DeviceIdentity.generate().privateKey)
    }
}
