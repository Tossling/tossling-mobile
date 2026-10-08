package com.kopylovis.tossling.protocol.room

import com.kopylovis.tossling.protocol.Member
import com.kopylovis.tossling.protocol.Pairing
import com.kopylovis.tossling.protocol.SyncJson
import com.kopylovis.tossling.protocol.crypto.ClipCipher
import com.kopylovis.tossling.protocol.crypto.RoomKeys
import com.kopylovis.tossling.protocol.normalizedServer

data class PairedRoom(
    val config: RoomConfig,
    val members: Map<String, Member>,
    val computer: String,
)

object PairingCode {

    fun read(raw: String, deviceId: String, deviceName: String, identity: String, source: String = "ios", nowMs: Long): PairedRoom? {
        val pairing = runCatching { SyncJson.decodeFromString(Pairing.serializer(), raw.trim()) }.getOrNull() ?: return null
        if (!pairing.isRoom || pairing.version < 2) return null
        val server = normalizedServer(pairing.server) ?: return null
        if (runCatching { ClipCipher.fromBase64(key = pairing.key) }.isFailure) return null
        val config = RoomConfig(
            server = server,
            token = pairing.token,
            room = pairing.room,
            key = pairing.key,
            deviceId = deviceId,
            deviceName = deviceName,
            owner = pairing.ownerId,
            identity = identity,
            source = source,
        )
        val members = pairing.macId.takeIf { it.isNotEmpty() }?.let { id ->
            val pk = pairing.macKey.takeIf { RoomKeys.decodeKey(it) != null }.orEmpty()
            mapOf(id to Member(id = id, name = pairing.macName.ifEmpty { "?" }, source = "mac", seen = nowMs, pk = pk))
        }.orEmpty()
        return PairedRoom(config = config, members = members, computer = pairing.macName)
    }
}
