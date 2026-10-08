package com.kopylovis.tossling.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val SyncJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class Pairing(
    @SerialName("s") val server: String,
    @SerialName("t") val token: String = "",
    @SerialName("in") val inTopic: String = "",
    @SerialName("out") val outTopic: String = "",
    @SerialName("k") val key: String,
    @SerialName("n") val macName: String = "",
    @SerialName("v") val version: Int = 1,
    @SerialName("r") val room: String = "",
    @SerialName("id") val macId: String = "",
    @SerialName("pk") val macKey: String = "",
    @SerialName("o") val owner: String = "",
    val members: List<Member> = emptyList(),
    val since: Long = 0,
) {
    val id: String get() = inTopic

    val isRoom: Boolean get() = room.isNotEmpty()

    val ownerId: String get() = owner.ifEmpty { macId }

    val host: String get() = server.substringAfter("://").substringBefore("/")

    val endpoint: Endpoint get() = Endpoint(server = server, token = token)
}

@Serializable
data class Endpoint(
    val server: String,
    val token: String = "",
) {
    val host: String get() = server.substringAfter("://").substringBefore("/")
}

@Serializable
data class Member(
    val id: String,
    val name: String,
    val source: String,
    val seen: Long,
    val since: Long = seen,
    val alias: String = "",
    val pk: String = "",
) {
    val isComputer: Boolean get() = source in COMPUTER_SOURCES

    val title: String get() = alias.ifEmpty { name }
}

enum class DeviceKind { COMPUTER, PHONE }

data class RoomDevice(
    val id: String,
    val pairingId: String,
    val name: String,
    val kind: DeviceKind,
    val isSelf: Boolean,
    val isOnline: Boolean,
    val seen: Long,
    val since: Long,
    val host: String,
    val isRoom: Boolean,
    val isOwner: Boolean = false,
    val ownName: String = name,
    val hasAlias: Boolean = false,
) {
    val shownOwnName: String? get() = ownName.takeIf { hasAlias }
}

const val ROOM_PREFIX = "tossling-"
const val OLD_ROOM_PREFIX = "tossy-"

fun isRoomTopic(topic: String): Boolean = topic.startsWith(ROOM_PREFIX) || topic.startsWith(OLD_ROOM_PREFIX)

fun pairingHost(raw: String): String =
    runCatching { SyncJson.decodeFromString(Pairing.serializer(), raw.trim()).host }.getOrDefault("")

fun localDeviceNames(names: String, devices: List<RoomDevice>): String {
    val aliases = devices.filter { it.hasAlias }.associate { it.ownName to it.name }
    if (aliases.isEmpty() || names.isEmpty()) return names
    return aliases[names] ?: names.split(", ").joinToString(separator = ", ") { aliases[it] ?: it }
}

data class RoomSwitch(
    val from: String,
    val to: String,
)

data class MacDevice(
    val id: String,
    val pairingId: String,
    val name: String,
    val host: String,
    val link: LinkStatus,
)

@Serializable
data class ClipMeta(
    @SerialName("k") val kind: String,
    @SerialName("m") val mime: String = TEXT_MIME,
    @SerialName("src") val source: String = "android",
    @SerialName("n") val device: String = "",
    @SerialName("v") val text: String? = null,
    @SerialName("id") val sender: String = "",
    @SerialName("to") val to: List<String>? = null,
    @SerialName("r") val room: String? = null,
    @SerialName("key") val key: String? = null,
    @SerialName("f") val fileName: String? = null,
    @SerialName("x") val format: Int? = null,
    @SerialName("s") val size: Long? = null,
    @SerialName("pk") val publicKey: String? = null,
    @SerialName("e") val ephemeral: String? = null,
    @SerialName("keys") val keys: Map<String, String>? = null,
    @SerialName("re") val renewed: Boolean? = null,
    @SerialName("inv") val invite: String? = null,
) {
    companion object {
        const val TEXT = "text"
        const val IMAGE = "image"
        const val FILE = "file"
        const val HELLO = "hello"
        const val MOVE = "move"
        const val PING = "ping"
        const val BYE = "bye"
        const val REKEY = "rekey"
        const val KICK = "kick"
        const val TEXT_MIME = "text/plain"
    }
}

@Serializable
enum class ClipKind { TEXT, IMAGE, FILE }

@Serializable
data class ClipItem(
    val id: String,
    val incoming: Boolean,
    val kind: ClipKind,
    val text: String? = null,
    val file: String? = null,
    val mime: String? = null,
    val name: String? = null,
    val size: Long = 0,
    val macId: String? = null,
    val device: String = "",
    val toAll: Boolean = false,
    val time: Long,
    val isPinned: Boolean = false,
)

enum class PairingProblem { NOT_TOSSLING, TOKEN, NETWORK }

class PairingException(val problem: PairingProblem, val server: String = "", cause: Throwable? = null) : Exception(problem.name, cause)

data class LinkStatus(
    val isOnline: Boolean = true,
    val lastContact: Long = 0,
)

data class Transfer(
    val name: String,
    val incoming: Boolean,
    val device: String,
    val done: Long,
    val total: Long,
) {
    val percent: Int get() = if (total <= 0) 0 else (done * 100 / total).toInt().coerceIn(0, 100)
}

val COMPUTER_SOURCES = setOf("mac", "windows", "linux")
