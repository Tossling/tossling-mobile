package com.kopylovis.tossling.sync.data

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.kopylovis.tossling.protocol.Pairing
import com.kopylovis.tossling.protocol.SyncJson
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable

@Serializable
data class SavedRooms(
    val deviceId: String,
    val identity: String,
    val name: String = "",
    val rooms: List<Pairing>,
) {
    val title: String get() = rooms.firstOrNull()?.macName?.ifEmpty { null } ?: "Mac"

    val host: String get() = rooms.firstOrNull()?.host.orEmpty()
}

internal fun savedRoomsBytes(saved: SavedRooms): ByteArray? {
    var rooms = saved.rooms.filter { it.isRoom }.map { it.copy(members = emptyList(), since = 0) }
    while (rooms.isNotEmpty()) {
        val bytes = SyncJson.encodeToString(SavedRooms.serializer(), saved.copy(rooms = rooms)).toByteArray()
        if (bytes.size <= MAX_BYTES) return bytes
        rooms = rooms.dropLast(1)
    }
    return null
}

internal fun savedRoomsOf(bytes: ByteArray): SavedRooms? =
    runCatching { SyncJson.decodeFromString(SavedRooms.serializer(), bytes.decodeToString()) }.getOrNull()?.takeIf { it.rooms.isNotEmpty() }

private const val MAX_BYTES = 4_000

internal class RoomBackup(private val context: Context) {

    suspend fun save(saved: SavedRooms) {
        val bytes = savedRoomsBytes(saved = saved) ?: return clear()
        runCatching {
            Blockstore.getClient(context).storeBytes(
                StoreBytesData.Builder().setBytes(bytes).setKey(KEY).setShouldBackupToCloud(false).build(),
            ).await()
        }.onFailure { Log.w(TAG, "could not keep the room for a reinstall", it) }
    }

    suspend fun load(): SavedRooms? = runCatching {
        Blockstore.getClient(context).retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(KEY)).build()).await()
            .blockstoreDataMap[KEY]?.bytes?.let(::savedRoomsOf)
    }.onFailure { Log.w(TAG, "could not read the kept room", it) }.getOrNull()

    suspend fun clear() {
        runCatching { Blockstore.getClient(context).deleteBytes(DeleteBytesRequest.Builder().setKeys(listOf(KEY)).build()).await() }
            .onFailure { Log.w(TAG, "could not forget the kept room", it) }
    }

    private companion object {
        private const val TAG = "RoomBackup"
        private const val KEY = "com.kopylovis.tossling.rooms"
    }
}
